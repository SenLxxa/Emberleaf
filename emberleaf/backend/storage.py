"""Persistent launcher data: settings, profiles and the content manifest of each profile."""
import json
import os
import re
import shutil
import threading
import time
import uuid
from pathlib import Path

from .version import LAGERFEUER_URL

_BASE_DIR = Path(
    os.environ.get("EMBERLEAF_DATA_DIR")
    or Path(os.environ.get("APPDATA", Path.home())) / ".emberleaf"
)
_BASE_DIR.mkdir(parents=True, exist_ok=True)
# resolve() turns redirected folders (e.g. AppData inside packaged apps) into their real location.
# Fabric compares classpath paths with real file locations; a mismatch loads Mixin twice and crashes.
DATA_DIR = _BASE_DIR.resolve()
INSTANCES_DIR = DATA_DIR / "instances"
CACHE_DIR = DATA_DIR / "cache"
SETTINGS_FILE = DATA_DIR / "settings.json"
PROFILES_FILE = DATA_DIR / "profiles.json"
CONTENT_FILE = "emberleaf-content.json"

LOADERS = ("vanilla", "fabric", "forge", "neoforge")
ICONS = ("🍁", "🍂", "🌲", "🏕️", "⚔️", "🏡", "🍄", "🔥")
USERNAME_RE = re.compile(r"[A-Za-z0-9_]{1,16}")
PROFILE_ID_RE = re.compile(r"[0-9a-f]{12}")
DEFAULT_SETTINGS = {
    "username": "",
    "ram_mb": 4096,
    "curseforge_key": "",
    "azure_client_id": "",
    "last_profile": "",
    "minimize_on_play": True,
    "friends_server": "",
    "menu_scene": "auto",
    "restore_after_play": True,
    "game_width": 0,
    "game_height": 0,
    "fullscreen": False,
    "jvm_args": "",
}
MENU_SCENES = ("auto", "autumn", "nether", "end", "cave")
JVM_ARG_RE = re.compile(r"-[A-Za-z0-9:_.=+\-/%,]+")
FRIENDS_SERVER_RE = re.compile(r"(https://[A-Za-z0-9.\-]+(:\d+)?|http://(127\.0\.0\.1|localhost)(:\d+)?)")

_lock = threading.RLock()


class UserError(Exception):
    """An error whose message can be shown to the player as-is."""


def read_json(path, default):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return default


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    os.replace(tmp, path)


def get_settings():
    with _lock:
        settings = dict(DEFAULT_SETTINGS)
        settings.update(read_json(SETTINGS_FILE, {}))
        if not str(settings.get("friends_server") or "").strip():
            settings["friends_server"] = LAGERFEUER_URL  # everyone meets at the same Lagerfeuer by default
        return settings


def save_settings(patch):
    with _lock:
        settings = get_settings()
        for key, value in patch.items():
            if key not in DEFAULT_SETTINGS:
                continue
            if key == "username":
                value = str(value).strip()
                if value and not USERNAME_RE.fullmatch(value):
                    raise UserError("Minecraft-Namen haben 1–16 Zeichen: Buchstaben, Zahlen oder _.")
            elif key == "ram_mb":
                value = int(value)
                if not 1024 <= value <= 32768:
                    raise UserError("Der Arbeitsspeicher muss zwischen 1 und 32 GB liegen.")
            elif key in ("minimize_on_play", "restore_after_play", "fullscreen"):
                value = bool(value)
            elif key in ("game_width", "game_height"):
                value = int(value or 0)
                if value and not 320 <= value <= 7680:
                    raise UserError("Die Fenstergröße muss zwischen 320 und 7680 Pixeln liegen (leer = Minecraft-Standard).")
            elif key == "jvm_args":
                value = " ".join(str(value or "").split())
                if len(value) > 400:
                    raise UserError("Das sind zu viele Java-Argumente.")
                for token in value.split():
                    if not JVM_ARG_RE.fullmatch(token):
                        raise UserError(f"„{token}“ ist kein gültiges Java-Argument (sie fangen mit - an).")
                    if token.startswith(("-Xmx", "-Xms")):
                        raise UserError("Den Arbeitsspeicher stellst du mit dem Regler ein, nicht bei den Java-Argumenten.")
            elif key == "menu_scene":
                value = str(value) if str(value) in MENU_SCENES else "auto"
            elif key == "friends_server":
                value = str(value).strip().rstrip("/")
                if value and not FRIENDS_SERVER_RE.fullmatch(value):
                    raise UserError("Die Lagerfeuer-Adresse muss mit https:// anfangen, z. B. https://lagerfeuer.dein-name.workers.dev")
            else:
                value = str(value).strip()
            settings[key] = value
        stored = dict(settings)
        if LAGERFEUER_URL and stored.get("friends_server") == LAGERFEUER_URL:
            stored["friends_server"] = ""  # the built-in Lagerfeuer is not written down: a newer Emberleaf may bring another address
        write_json(SETTINGS_FILE, stored)
        return settings


def profile_dir(profile_id):
    if not PROFILE_ID_RE.fullmatch(profile_id or ""):
        raise UserError("Ungültige Profil-ID.")
    return INSTANCES_DIR / profile_id


def read_content(profile_id):
    return read_json(profile_dir(profile_id) / CONTENT_FILE, [])


def write_content(profile_id, content):
    write_json(profile_dir(profile_id) / CONTENT_FILE, content)


def list_profiles():
    with _lock:
        profiles = read_json(PROFILES_FILE, [])
    for profile in profiles:
        content = read_content(profile["id"])
        profile["content_count"] = len(content)
        profile["voicechat"] = any(c.get("slug") == "simple-voice-chat" for c in content)
    return profiles


def get_profile(profile_id):
    for profile in list_profiles():
        if profile["id"] == profile_id:
            return profile
    raise UserError("Dieses Profil gibt es nicht mehr.")


def create_profile(name, loader, mc_version, icon):
    name = str(name).strip()[:40]
    if not name:
        raise UserError("Gib deinem Profil einen Namen.")
    if loader not in LOADERS:
        raise UserError("Unbekannter Mod-Loader.")
    if not re.fullmatch(r"[\w.\-]{1,32}", str(mc_version)):
        raise UserError("Ungültige Minecraft-Version.")
    profile = {
        "id": uuid.uuid4().hex[:12],
        "name": name,
        "loader": loader,
        "mc_version": str(mc_version),
        "icon": icon if icon in ICONS else ICONS[0],
        "created": int(time.time()),
    }
    with _lock:
        profiles = read_json(PROFILES_FILE, [])
        profiles.append(profile)
        write_json(PROFILES_FILE, profiles)
    profile_dir(profile["id"]).mkdir(parents=True, exist_ok=True)
    return profile


def delete_profile(profile_id):
    folder = profile_dir(profile_id)
    with _lock:
        profiles = read_json(PROFILES_FILE, [])
        remaining = [p for p in profiles if p["id"] != profile_id]
        if len(remaining) == len(profiles):
            raise UserError("Dieses Profil gibt es nicht mehr.")
        write_json(PROFILES_FILE, remaining)
    shutil.rmtree(folder, ignore_errors=True)
