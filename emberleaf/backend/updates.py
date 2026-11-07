"""Emberleaf's own files (launcher, client mod, shader pack) and how new versions reach every friend.

Every file carries its version in the name (emberleaf-client-1.0.1.jar, Emberleaf-Glut-1.0.0.zip). The launcher
uses the newest copy it has: the one bundled with Emberleaf or one downloaded as an update.

New versions are GitHub releases (made by tools/release.ps1). Every launcher looks at the newest release shortly
after it starts and every 30 minutes:
- client mod and shader: newer files go to DATA_DIR/updates/<kind> and are used from the next game start
- the launcher itself (Emberleaf.exe): the new exe is downloaded next to the running one, the player gets a
  message and restarts right away or later (the next start installs it)
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import threading
import time
from pathlib import Path

import requests

from . import accounts, storage
from .storage import UserError
from .version import APP_VERSION, GITHUB_REPO, GITHUB_URL, OWNER_NAME, USER_AGENT

APP_ROOT = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
BUNDLED = {
    "mod": APP_ROOT / "client-mod",
    "resourcepack": APP_ROOT / "packs" / "resourcepacks",
    "shaderpack": APP_ROOT / "packs" / "shaderpacks",
}
PATTERNS = {"mod": "emberleaf-client-*.jar", "resourcepack": "Emberleaf-*.zip", "shaderpack": "Emberleaf-*.zip"}
TARGET_DIRS = {"mod": "mods", "resourcepack": "resourcepacks", "shaderpack": "shaderpacks"}
UPDATES_DIR = storage.DATA_DIR / "updates"
# built on this PC from packs downloaded from their official pages (packassembler.py): used locally, never uploaded
GENERATED_DIR = storage.DATA_DIR / "generated"
STATE_FILE = UPDATES_DIR / "state.json"
NAME_RE = re.compile(r"^(?P<family>[A-Za-z0-9._+-]+?)-(?P<version>\d{1,4}(?:\.\d{1,4}){0,3})\.(?:jar|zip)$")
CHECK_EVERY = 30 * 60
# renamed families: the old files are replaced by the new family (also in options.txt)
LEGACY = {"Emberleaf-Texturen": ("Emberleaf-64x",)}

RELEASES_API = f"https://api.github.com/repos/{GITHUB_REPO}/releases/latest"
MANIFEST_NAME = "emberleaf-release.json"  # lists every file of a release with size and SHA-256
GITHUB_HEADERS = {"User-Agent": USER_AGENT, "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
# The shared exe replaces itself; started from the source (pythonw main.py) only mod and shader are updated.
FROZEN = bool(getattr(sys, "frozen", False))
EXE = Path(sys.executable).resolve() if FROZEN else None
PENDING_EXE = EXE.with_name(f"{EXE.stem}.update.exe") if FROZEN else None
OLD_EXE = EXE.with_name(f"{EXE.stem}.old.exe") if FROZEN else None

_lock = threading.Lock()
_status = {"state": "idle", "message": "", "progress": 0.0}


def parse(name):
    m = NAME_RE.match(name)
    if not m:
        return None
    return m["family"], tuple(int(x) for x in m["version"].split("."))


def _candidates(kind):
    out = []
    folders = {"bundled": BUNDLED[kind], "download": UPDATES_DIR / kind, "generated": GENERATED_DIR / kind}
    for source, folder in folders.items():
        if not folder.is_dir():
            continue
        for file in folder.glob(PATTERNS[kind]):
            info = parse(file.name)
            if info and file.is_file():
                out.append({"kind": kind, "name": file.name, "family": info[0], "version": info[1], "path": file, "source": source})
    return out


def best_files():
    """The newest copy of every Emberleaf file family, e.g. {"mod": [..], "resourcepack": [..]}."""
    result = {}
    for kind in BUNDLED:
        newest = {}
        for c in _candidates(kind):
            have = newest.get(c["family"])
            # same version: a downloaded copy wins, it is what the owner published last
            if have is None or c["version"] > have["version"] or (c["version"] == have["version"] and c["source"] == "download"):
                newest[c["family"]] = c
        result[kind] = sorted(newest.values(), key=lambda c: c["name"])
    return result


def version_text(version):
    return ".".join(str(x) for x in version)


def local_summary():
    return {kind: [{"name": c["name"], "version": version_text(c["version"]), "source": c["source"]} for c in files]
            for kind, files in best_files().items()}


# ---------- in die Profile legen ----------

def _options_resourcepacks(profile_dir, wanted, marker):
    """Turns Emberleaf resource packs on in options.txt the first time, and swaps in new versions later."""
    options = profile_dir / "options.txt"
    lines = options.read_text("utf-8", errors="replace").splitlines() if options.is_file() else []
    index = next((i for i, l in enumerate(lines) if l.startswith("resourcePacks:")), None)
    try:
        packs = json.loads(lines[index].split(":", 1)[1]) if index is not None else ["vanilla"]
    except (ValueError, IndexError):
        packs = ["vanilla"]
    if not isinstance(packs, list):
        packs = ["vanilla"]
    changed = False
    for name in wanted:
        family = parse(name)[0]
        entry = f"file/{name}"
        olds = (family, *LEGACY.get(family, ()))
        old = [p for p in packs if isinstance(p, str) and p != entry and parse(p[5:]) and parse(p[5:])[0] in olds]
        if old:  # an older version was on: use the new one in the same place
            packs[packs.index(old[0])] = entry
            packs = [p for p in packs if p not in old[1:]]
            changed = True
        elif entry not in packs and family not in marker.setdefault("resourcepacks", []):
            packs.append(entry)  # first time: on top of vanilla
            changed = True
        if family not in marker["resourcepacks"]:
            marker["resourcepacks"].append(family)
            changed = True
    if changed:
        line = "resourcePacks:" + json.dumps(packs, separators=(",", ":"))
        if index is None:
            lines.append(line)
        else:
            lines[index] = line
        options.write_text("\n".join(lines) + "\n", "utf-8")
    return changed


def _iris_shader(profile_dir, name, marker):
    """Selects the Emberleaf shader in Iris the first time, and keeps it on the newest version."""
    config = profile_dir / "config" / "iris.properties"
    lines = config.read_text("utf-8", errors="replace").splitlines() if config.is_file() else []
    values = dict(l.split("=", 1) for l in lines if "=" in l and not l.startswith("#"))
    family = parse(name)[0]
    current = values.get("shaderPack", "")
    first_time = family not in marker.setdefault("shaderpacks", [])
    old_version = current.startswith(f"{family}-") and current != name
    if not first_time and not old_version:
        return False
    values["shaderPack"] = name
    if first_time:
        values["enableShaders"] = "true"
        marker["shaderpacks"].append(family)
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text("\n".join(f"{k}={v}" for k, v in values.items()) + "\n", "utf-8")
    return True


def sync_profile(profile, mc_version):
    """Puts the newest Emberleaf files into a Fabric profile of the version the client mod is built for."""
    if profile["loader"] != "fabric" or profile["mc_version"] != mc_version:
        return
    folder = storage.profile_dir(profile["id"])
    marker_file = folder / ".emberleaf" / "content.json"
    marker = storage.read_json(marker_file, {})
    for kind, files in best_files().items():
        target_dir = folder / TARGET_DIRS[kind]
        target_dir.mkdir(parents=True, exist_ok=True)
        for c in files:
            for old in target_dir.glob("*"):
                info = parse(old.name)
                if info and info[0] in (c["family"], *LEGACY.get(c["family"], ())) and old.name != c["name"]:
                    old.unlink(missing_ok=True)
            target = target_dir / c["name"]
            data = c["path"].read_bytes()
            if not target.exists() or target.stat().st_size != len(data) or target.read_bytes() != data:
                target.write_bytes(data)
        if kind == "resourcepack" and files:
            _options_resourcepacks(folder, [c["name"] for c in files], marker)
        if kind == "shaderpack" and files:
            _iris_shader(folder, files[0]["name"], marker)
    storage.write_json(marker_file, marker)


# ---------- Updates von GitHub ----------

_state_lock = threading.Lock()


def _version_tuple(text):
    m = re.fullmatch(r"v?(\d{1,4}(?:\.\d{1,4}){0,3})", str(text or "").strip())
    return tuple(int(x) for x in m[1].split(".")) if m else None


APP_TUPLE = _version_tuple(APP_VERSION)


def _read_state():
    with _state_lock:
        return storage.read_json(STATE_FILE, {})


def _merge_state(changes, drop=()):
    """Writes some keys of state.json without losing what another thread wrote meanwhile."""
    with _state_lock:
        state = storage.read_json(STATE_FILE, {})
        state.update(changes)
        for key in drop:
            state.pop(key, None)
        storage.write_json(STATE_FILE, state)
        return state


def _sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _get(url, **kwargs):
    try:
        return requests.get(url, timeout=20, **kwargs)
    except requests.RequestException as e:
        raise UserError("GitHub ist gerade nicht erreichbar. Emberleaf versucht es später nochmal.") from e


def _latest_release(state):
    """The newest GitHub release as {"tag", "version", "notes", "url", "published", "assets"}, None without one."""
    headers = dict(GITHUB_HEADERS)
    if state.get("etag") and state.get("github"):
        headers["If-None-Match"] = state["etag"]  # unchanged answers don't count against GitHub's limit
    r = _get(RELEASES_API, headers=headers)
    if r.status_code == 304:
        return state["github"]
    if r.status_code == 404:
        state.update(etag=None, github=None)
        return None
    if r.status_code in (403, 429):
        raise UserError("GitHub bremst gerade (zu viele Anfragen). Emberleaf versucht es später nochmal.")
    if r.status_code != 200:
        raise UserError(f"GitHub hat mit Fehler {r.status_code} geantwortet.")
    data = r.json()
    version = _version_tuple(data.get("tag_name"))
    release = {
        "tag": str(data.get("tag_name") or ""),
        "version": ".".join(map(str, version)) if version else "",
        "notes": str(data.get("body") or "")[:4000],
        "url": str(data.get("html_url") or f"{GITHUB_URL}/releases"),
        "published": data.get("published_at"),
        "assets": {a["name"]: {"url": a["browser_download_url"], "size": int(a.get("size") or 0)}
                   for a in data.get("assets", []) if a.get("name") and a.get("browser_download_url")},
    }
    state.update(etag=r.headers.get("ETag"), github=release)
    return release


def _manifest(release, state):
    """The file list of a release (emberleaf-release.json), cached per release."""
    cached = state.get("manifest")
    if cached and cached.get("tag") == release["tag"]:
        return cached
    asset = release["assets"].get(MANIFEST_NAME)
    if not asset:
        return None
    r = _get(asset["url"], headers={"User-Agent": USER_AGENT})
    if r.status_code != 200:
        raise UserError("Die Update-Liste auf GitHub konnte nicht geladen werden.")
    try:
        data = r.json()
    except ValueError as e:
        raise UserError("Die Update-Liste auf GitHub ist kaputt.") from e
    files = [f for f in data.get("files", []) if isinstance(f, dict) and f.get("name") in release["assets"]
             and re.fullmatch(r"[0-9a-f]{64}", str(f.get("sha256", "")))]
    manifest = {"tag": release["tag"], "version": str(data.get("version") or release["version"]),
                "notes": str(data.get("notes") or release["notes"])[:4000], "files": files}
    state["manifest"] = manifest
    return manifest


def _fetch(url, target, sha256, size, label):
    """Downloads one file next to its target, checks size and SHA-256, then moves it in place."""
    target.parent.mkdir(parents=True, exist_ok=True)
    tmp = target.with_name(target.name + ".part")
    digest = hashlib.sha256()
    done = 0
    try:
        with requests.get(url, headers={"User-Agent": USER_AGENT}, stream=True, timeout=30) as r:
            if r.status_code != 200:
                raise UserError(f"{label} konnte nicht von GitHub geladen werden (Fehler {r.status_code}).")
            with tmp.open("wb") as out:
                for block in r.iter_content(256 * 1024):
                    out.write(block)
                    digest.update(block)
                    done += len(block)
                    if size:
                        _status["progress"] = min(done / size, 1.0)
    except requests.RequestException as e:
        tmp.unlink(missing_ok=True)
        raise UserError("GitHub ist gerade nicht erreichbar. Emberleaf versucht es später nochmal.") from e
    except UserError:
        tmp.unlink(missing_ok=True)
        raise
    if digest.hexdigest() != sha256 or (size and done != size):
        tmp.unlink(missing_ok=True)
        raise UserError(f"{label} kam kaputt an und wurde verworfen.")
    os.replace(tmp, target)


def _update_files(manifest, release):
    """Downloads client mod and shader files that are newer than the ones this launcher has."""
    have = {c["family"]: c["version"] for files in best_files().values() for c in files}
    updated = []
    for file in manifest["files"]:
        info = parse(file["name"])
        kind = file.get("kind")
        if not info or kind not in ("mod", "shaderpack") or info[1] <= have.get(info[0], ()):
            continue
        _status.update(state="downloading", message=f"Lädt {file['name']} …", progress=0.0)
        target_dir = UPDATES_DIR / kind
        _fetch(release["assets"][file["name"]]["url"], target_dir / file["name"], file["sha256"],
               int(file.get("size") or 0), file["name"])
        for old in target_dir.glob(PATTERNS[kind]):
            old_info = parse(old.name)
            if old_info and old_info[0] == info[0] and old.name != file["name"]:
                old.unlink(missing_ok=True)
        updated.append(file["name"])
    return updated


def _pending(state, verify=False):
    """The downloaded launcher update, if it is complete and newer than this launcher."""
    pending = state.get("pending")
    if not FROZEN or not isinstance(pending, dict) or not PENDING_EXE.is_file():
        return None
    if (_version_tuple(pending.get("version")) or ()) <= APP_TUPLE:
        return None
    if PENDING_EXE.stat().st_size != pending.get("size"):
        return None
    if verify and _sha256(PENDING_EXE) != pending.get("sha256"):
        return None
    return pending


def _update_launcher(manifest, release, state):
    """Downloads a newer Emberleaf.exe next to the running one (only the shared exe updates itself)."""
    if not FROZEN or (_version_tuple(manifest["version"]) or ()) <= APP_TUPLE:
        return None
    exe = next((f for f in manifest["files"] if f.get("kind") == "launcher"), None)
    if not exe:
        return None
    pending = _pending(state)
    if pending and pending.get("sha256") == exe["sha256"]:
        return pending
    _status.update(state="downloading", message=f"Lädt Emberleaf {manifest['version']} …", progress=0.0)
    try:
        _fetch(release["assets"][exe["name"]]["url"], PENDING_EXE, exe["sha256"], int(exe.get("size") or 0),
               f"Emberleaf {manifest['version']}")
    except OSError as e:
        raise UserError(f"Emberleaf darf in seinem Ordner nichts speichern. Lade die neue Version auf {GITHUB_URL}/releases.") from e
    return {"version": manifest["version"], "notes": manifest["notes"], "sha256": exe["sha256"],
            "size": PENDING_EXE.stat().st_size, "url": release["url"]}


def _is_owner():
    try:
        account = accounts.public_account()
    except Exception:  # noqa: BLE001 - unreadable accounts file: just no badge
        return False
    return bool(account and account["name"].lower() == OWNER_NAME.lower())


def check(force=False):
    """Looks at the newest GitHub release and downloads everything that is newer than what we have."""
    state = _read_state()
    if not force and time.time() - (state.get("checked") or 0) < 60:
        return {"state": "ok", **status()}
    if not _lock.acquire(blocking=False):
        return {"state": "busy", **status()}
    try:
        _status.update(state="checking", message="Sucht nach Updates …", progress=0.0)
        changes = {"owner": _is_owner()}
        _merge_state(changes)  # the mod reads "owner" for the founder badge
        release = _latest_release(state)
        updated, pending = [], None
        if release and _manifest(release, state):
            manifest = state["manifest"]
            updated = _update_files(manifest, release)
            pending = _update_launcher(manifest, release, state)
            if updated and not pending:
                changes["news"] = {"kind": "files", "version": manifest["version"], "notes": manifest["notes"],
                                   "files": updated, "shown": False, "at": time.time()}
        changes.update(etag=state.get("etag"), github=state.get("github"), manifest=state.get("manifest"),
                       pending=pending, checked=time.time())
        _merge_state(changes)
        _status.update(state="idle", message="", progress=1.0)
        return {"state": "ok", "new": updated, **status()}
    except UserError as e:
        _merge_state({"etag": state.get("etag"), "github": state.get("github"), "checked": time.time()})
        _status.update(state="error", message=str(e))
        raise
    finally:
        _lock.release()


def status():
    state = _read_state()
    github = state.get("github") or {}
    latest = (state.get("manifest") or {}).get("version") or github.get("version")
    pending = _pending(state)
    news = state.get("news")
    return {
        "status": dict(_status), "local": local_summary(), "owner": bool(state.get("owner")),
        "app_version": APP_VERSION, "latest": latest, "release_url": github.get("url") or f"{GITHUB_URL}/releases",
        "newer": bool(latest and (_version_tuple(latest) or ()) > APP_TUPLE),
        "pending": {k: pending.get(k) for k in ("version", "notes")} if pending else None,
        "self_update": FROZEN, "checked": state.get("checked"), "repo": GITHUB_URL,
        "news": news if isinstance(news, dict) and not news.get("shown") else None,
    }


def mark_seen():
    """The player saw the "Emberleaf was updated" message: don't show it again."""
    state = _read_state()
    if isinstance(state.get("news"), dict):
        news = dict(state["news"], shown=True)
        _merge_state({"news": news})
    return status()


def start_background():
    def loop():
        time.sleep(8)  # let the window open first
        while True:
            try:
                check(force=True)
            except Exception:  # noqa: BLE001 - offline or no release yet: try again later
                pass
            time.sleep(CHECK_EVERY)

    threading.Thread(target=loop, daemon=True, name="emberleaf-updates").start()


# ---------- den neuen Launcher einsetzen ----------

def _swap():
    """Puts the downloaded exe in place of the running one (Windows allows renaming a running exe)."""
    old = OLD_EXE
    try:
        old.unlink(missing_ok=True)
    except OSError:  # an older copy is still running: use a fresh name, startup() cleans up later
        old = EXE.with_name(f"{EXE.stem}.old-{int(time.time())}.exe")
    os.replace(EXE, old)
    try:
        os.replace(PENDING_EXE, EXE)
    except OSError:
        os.replace(old, EXE)
        raise


def _restart():
    """Starts the (new) Emberleaf.exe as its own program; it waits until this one has closed."""
    env = {k: v for k, v in os.environ.items() if not k.upper().startswith("_PYI") and k.upper() != "_MEIPASS2"}
    env["PYINSTALLER_RESET_ENVIRONMENT"] = "1"  # unpack itself again instead of reusing our temp folder
    flags = subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP
    subprocess.Popen([str(EXE), "--after-update", str(os.getpid())], cwd=str(EXE.parent), env=env,
                     creationflags=flags, close_fds=True)


def install():
    """Installs the downloaded launcher and starts it. False when there is no complete update."""
    pending = _pending(_read_state(), verify=True)
    if not pending:
        return False
    try:
        _swap()
    except OSError as e:
        raise UserError("Emberleaf konnte sich nicht selbst ersetzen (ist der Ordner schreibgeschützt?). "
                        f"Lade die neue Version auf {GITHUB_URL}/releases.") from e
    _merge_state({"news": {"kind": "launcher", "version": pending["version"], "notes": pending.get("notes", ""),
                           "shown": False, "at": time.time()}}, drop=("pending",))
    _restart()
    return True


def install_now():
    """For the "Jetzt neu starten" button. The caller closes the window afterwards."""
    if not FROZEN:
        raise UserError("Du startest Emberleaf aus dem Quellcode. Hol dir die neue Version mit tools/install.ps1.")
    if not _lock.acquire(blocking=False):
        raise UserError("Das Update lädt noch, einen Moment …")
    try:
        if not install():
            raise UserError("Das Update ist noch nicht fertig geladen. Emberleaf versucht es gleich nochmal.")
    finally:
        _lock.release()


def _remove_old_exes():
    for old in EXE.parent.glob(f"{EXE.stem}.old*.exe"):
        try:
            old.unlink()
        except OSError:
            pass  # still closing


def startup():
    """Runs before the window opens: cleans up after an update and installs a waiting one.
    True means a new Emberleaf was started and this one should quit right away."""
    if not FROZEN:
        return False
    _remove_old_exes()

    def later():  # the old exe may still be closing when we start
        for _ in range(10):
            time.sleep(3)
            _remove_old_exes()

    threading.Thread(target=later, daemon=True).start()
    try:
        return install()
    except (UserError, OSError):
        return False  # the window shows the update message again


