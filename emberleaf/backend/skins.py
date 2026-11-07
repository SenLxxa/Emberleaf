"""Looks up a player's skin and cape at Mojang and hands them to the UI as data URLs."""
import base64
import json
import time

import requests

from . import storage
from .storage import USERNAME_RE, UserError

PROFILE_LOOKUP = "https://api.minecraftservices.com/minecraft/profile/lookup/name/{}"
SESSION_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/{}"
CACHE_SECONDS = 3600


def _data_url(url):
    r = requests.get(url, timeout=15)
    r.raise_for_status()
    return "data:image/png;base64," + base64.b64encode(r.content).decode()


def _lookup(name):
    r = requests.get(PROFILE_LOOKUP.format(name), timeout=15)
    if r.status_code in (204, 404):
        raise UserError(f"Den Minecraft-Namen „{name}“ gibt es nicht.")
    r.raise_for_status()
    profile = r.json()

    s = requests.get(SESSION_PROFILE.format(profile["id"]), timeout=15)
    s.raise_for_status()
    prop = next(p for p in s.json()["properties"] if p["name"] == "textures")
    textures = json.loads(base64.b64decode(prop["value"]))["textures"]
    skin = textures.get("SKIN", {})
    cape = textures.get("CAPE")
    return {
        "name": profile["name"],
        "uuid": profile["id"],
        "skin": _data_url(skin["url"]) if skin.get("url") else None,
        "model": "slim" if skin.get("metadata", {}).get("model") == "slim" else "default",
        "cape": _data_url(cape["url"]) if cape else None,
    }


LIBRARY_DIR = storage.DATA_DIR / "skins"
LIBRARY_FILE = LIBRARY_DIR / "library.json"  # the client mod reads this too (in-game skin switcher)
SKIN_UPLOAD = "https://api.minecraftservices.com/minecraft/profile/skins"
PNG_PREFIX = "data:image/png;base64,"


def _png_size(data):
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise UserError("Das ist kein PNG-Bild.")
    return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")


def library():
    """Saved skins with their images, newest first."""
    out = []
    for entry in storage.read_json(LIBRARY_FILE, []):
        file = LIBRARY_DIR / f"{entry['id']}.png"
        if file.is_file():
            out.append({**entry, "skin": PNG_PREFIX + base64.b64encode(file.read_bytes()).decode()})
    return out


def add_to_library(name, model, data_url):
    if not str(data_url).startswith(PNG_PREFIX):
        raise UserError("Skins sind PNG-Bilder (64×64 oder 64×32 Pixel).")
    data = base64.b64decode(str(data_url)[len(PNG_PREFIX):])
    if _png_size(data) not in ((64, 64), (64, 32)):
        raise UserError("Ein Skin muss 64×64 (oder alt 64×32) Pixel groß sein.")
    import uuid as _uuid

    entry = {"id": _uuid.uuid4().hex[:12], "name": str(name or "").strip()[:30] or "Mein Skin",
             "model": "slim" if model == "slim" else "default", "added": int(time.time())}
    LIBRARY_DIR.mkdir(parents=True, exist_ok=True)
    (LIBRARY_DIR / f"{entry['id']}.png").write_bytes(data)
    entries = storage.read_json(LIBRARY_FILE, [])
    entries.insert(0, entry)
    storage.write_json(LIBRARY_FILE, entries)
    return library()


def remove_from_library(skin_id):
    entries = [e for e in storage.read_json(LIBRARY_FILE, []) if e["id"] != skin_id]
    storage.write_json(LIBRARY_FILE, entries)
    (LIBRARY_DIR / f"{skin_id}.png").unlink(missing_ok=True)
    return library()


def apply(skin_id, account):
    """Uploads a saved skin to the Minecraft account (like the official launcher does)."""
    entry = next((e for e in storage.read_json(LIBRARY_FILE, []) if e["id"] == skin_id), None)
    file = LIBRARY_DIR / f"{skin_id}.png"
    if entry is None or not file.is_file():
        raise UserError("Diesen Skin gibt es nicht mehr.")
    try:
        r = requests.post(SKIN_UPLOAD, headers={"Authorization": f"Bearer {account['access_token']}"},
                          data={"variant": "slim" if entry["model"] == "slim" else "classic"},
                          files={"file": ("skin.png", file.read_bytes(), "image/png")}, timeout=30)
    except requests.RequestException as e:
        raise UserError("Mojang ist gerade nicht erreichbar.") from e
    if r.status_code == 401:
        raise UserError("Deine Anmeldung ist abgelaufen. Bitte melde dich neu an.")
    if r.status_code >= 300:
        raise UserError("Mojang hat den Skin abgelehnt.")
    (storage.CACHE_DIR / "skins" / f"{account['name'].lower()}.json").unlink(missing_ok=True)
    return entry


def get_skin(name):
    if not USERNAME_RE.fullmatch(name or ""):
        raise UserError("Ungültiger Minecraft-Name.")
    cache_file = storage.CACHE_DIR / "skins" / f"{name.lower()}.json"
    cached = storage.read_json(cache_file, None)
    if cached and time.time() - cached.get("fetched", 0) < CACHE_SECONDS:
        return cached
    try:
        result = _lookup(name)
    except (requests.RequestException, ValueError, KeyError, StopIteration) as e:
        if cached:
            return cached  # offline: the last known skin is better than none
        raise UserError("Dein Skin konnte nicht geladen werden. Bist du online?") from e
    result["fetched"] = int(time.time())
    storage.write_json(cache_file, result)
    return result
