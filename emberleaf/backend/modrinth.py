"""Search Modrinth and install mods, resource packs and shaders into a profile."""
import hashlib
import json
import os
import threading
from pathlib import Path

import requests

from . import storage
from .storage import UserError
from .version import USER_AGENT

API = "https://api.modrinth.com/v2"
HEADERS = {"User-Agent": f"{USER_AGENT} (private hobby launcher)"}
FOLDERS = {"mod": "mods", "resourcepack": "resourcepacks", "shader": "shaderpacks"}
SHADER_LOADERS = ("fabric", "neoforge")  # Iris only exists for these
LOADER_NAMES = {"vanilla": "Vanilla", "fabric": "Fabric", "forge": "Forge", "neoforge": "NeoForge"}

_profile_locks = {}
_locks_guard = threading.Lock()


def _lock_for(profile_id):
    with _locks_guard:
        return _profile_locks.setdefault(profile_id, threading.Lock())


def _get(path, **params):
    try:
        r = requests.get(API + path, params=params, headers=HEADERS, timeout=20)
        r.raise_for_status()
        return r.json()
    except (requests.RequestException, ValueError) as e:
        raise UserError("Modrinth ist gerade nicht erreichbar.") from e


def _check_type(project_type, profile):
    if project_type not in FOLDERS:
        raise UserError("Unbekannte Inhaltsart.")
    if project_type == "mod" and profile["loader"] == "vanilla":
        raise UserError("Vanilla-Profile können keine Mods laden.")
    if project_type == "shader" and profile["loader"] not in SHADER_LOADERS:
        raise UserError("Shader brauchen Iris, und das gibt es nur für Fabric und NeoForge.")


def search(query, project_type, profile, offset=0):
    _check_type(project_type, profile)
    facets = [[f"project_type:{project_type}"], [f"versions:{profile['mc_version']}"]]
    if project_type == "mod":
        facets.append([f"categories:{profile['loader']}"])
    query = str(query).strip()
    data = _get(
        "/search",
        query=query,
        facets=json.dumps(facets),
        limit=20,
        offset=int(offset),
        index="relevance" if query else "downloads",
    )
    installed = {c["project_id"] for c in storage.read_content(profile["id"])}
    hits = [
        {
            "id": h["project_id"],
            "title": h["title"],
            "author": h["author"],
            "description": h["description"],
            "icon": h.get("icon_url") or "",
            "downloads": h["downloads"],
            "installed": h["project_id"] in installed,
        }
        for h in data["hits"]
    ]
    return {"hits": hits, "total": data["total_hits"]}


def _pick_version(project_id, project_type, profile):
    params = {"game_versions": json.dumps([profile["mc_version"]])}
    if project_type == "mod":
        params["loaders"] = json.dumps([profile["loader"]])
    versions = _get(f"/project/{project_id}/version", **params)
    releases = [v for v in versions if v["version_type"] == "release"]
    return (releases or versions or [None])[0]


def _download(file, dest):
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_name(dest.name + ".part")
    sha1 = hashlib.sha1()
    try:
        with requests.get(file["url"], headers=HEADERS, stream=True, timeout=60) as r:
            r.raise_for_status()
            with open(tmp, "wb") as out:
                for chunk in r.iter_content(1 << 16):
                    out.write(chunk)
                    sha1.update(chunk)
    except requests.RequestException as e:
        tmp.unlink(missing_ok=True)
        raise UserError("Der Download ist fehlgeschlagen.") from e
    if sha1.hexdigest() != file["hashes"].get("sha1"):
        tmp.unlink(missing_ok=True)
        raise UserError("Der Download war beschädigt. Bitte versuch es nochmal.")
    os.replace(tmp, dest)


def install(profile, project_id, project_type):
    """Installs a project plus its required dependencies; shaders also pull in Iris."""
    _check_type(project_type, profile)
    pid = profile["id"]
    with _lock_for(pid):
        content = storage.read_content(pid)
        have = {c["project_id"] for c in content}
        queue = [(project_id, project_type, True)]
        if project_type == "shader":
            queue.append(("iris", "mod", False))
        installed, warnings = [], []

        while queue:
            ref, ptype, is_root = queue.pop(0)
            project = _get(f"/project/{ref}")
            if project["id"] in have:
                continue
            version = _pick_version(project["id"], ptype, profile)
            if version is None:
                msg = (f"„{project['title']}“ gibt es nicht für "
                       f"{LOADER_NAMES[profile['loader']]} {profile['mc_version']}.")
                if is_root:
                    raise UserError(msg)
                warnings.append(msg)
                continue

            file = next((f for f in version["files"] if f.get("primary")), version["files"][0])
            filename = Path(file["filename"]).name
            _download(file, storage.profile_dir(pid) / FOLDERS[ptype] / filename)
            content.append({
                "project_id": project["id"],
                "slug": project["slug"],
                "type": ptype,
                "title": project["title"],
                "icon": project.get("icon_url") or "",
                "version": version["version_number"],
                "filename": filename,
            })
            have.add(project["id"])
            installed.append(project["title"])
            storage.write_content(pid, content)  # after every file, so a failed dependency keeps the rest

            for dep in version.get("dependencies", []):
                if dep.get("dependency_type") == "required" and dep.get("project_id"):
                    queue.append((dep["project_id"], "mod", False))

    return {"installed": installed, "warnings": warnings}


LOCAL_EXT = {"mod": (".jar",), "resourcepack": (".zip",), "shader": (".zip",)}
MAX_LOCAL_BYTES = 150 * 1024 * 1024


def add_local(profile, project_type, filename, data_b64):
    """Adds the player's own file (a mod .jar, resourcepack or shader .zip) to a profile."""
    import base64
    import hashlib

    _check_type(project_type, profile)
    name = Path(str(filename)).name
    if not name.lower().endswith(LOCAL_EXT[project_type]) or len(name) > 120 or name.startswith("."):
        raise UserError(f"Erlaubt sind {', '.join(LOCAL_EXT[project_type])}-Dateien.")
    try:
        data = base64.b64decode(str(data_b64), validate=True)
    except ValueError as e:
        raise UserError("Die Datei konnte nicht gelesen werden.") from e
    if len(data) > MAX_LOCAL_BYTES:
        raise UserError("Die Datei ist zu groß (maximal 150 MB).")
    if not data.startswith(b"PK"):
        raise UserError("Das ist keine gültige .jar- oder .zip-Datei.")
    pid = profile["id"]
    with _lock_for(pid):
        folder = storage.profile_dir(pid) / FOLDERS[project_type]
        folder.mkdir(parents=True, exist_ok=True)
        (folder / name).write_bytes(data)
        content = [c for c in storage.read_content(pid) if not (c.get("local") and c["filename"] == name)]
        content.append({
            "project_id": "local-" + hashlib.sha1(name.encode()).hexdigest()[:12],
            "slug": "", "type": project_type, "title": Path(name).stem, "icon": "",
            "version": "eigene Datei", "filename": name, "local": True,
        })
        storage.write_content(pid, content)
    return {"installed": [Path(name).stem], "warnings": []}


def remove(profile, project_id):
    pid = profile["id"]
    with _lock_for(pid):
        content = storage.read_content(pid)
        entry = next((c for c in content if c["project_id"] == project_id), None)
        if entry is None:
            raise UserError("Das ist nicht (mehr) installiert.")
        (storage.profile_dir(pid) / FOLDERS[entry["type"]] / Path(entry["filename"]).name).unlink(missing_ok=True)
        content.remove(entry)
        storage.write_content(pid, content)
        return content
