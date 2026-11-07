"""Installs Minecraft with the profile's mod loader (plus Java) and starts the game."""
import collections
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

import requests
from minecraft_launcher_lib import command, install, mod_loader, runtime
from minecraft_launcher_lib.exceptions import UnsupportedVersion, VersionNotFound
# The public mod_loader wrapper rejects NeoForge for 26.x (wrong version mapping), so we drive its installer directly.
from minecraft_launcher_lib.mod_loader._neoforge import Neoforge

from . import packassembler, storage, updates, versions
from .storage import UserError
from .version import APP_VERSION

MC_DIR = storage.DATA_DIR / "minecraft"
INSTALLED_FILE = MC_DIR / "emberleaf-installed.json"
LAUNCHER_NAME = "Emberleaf"
# The Emberleaf client mod ships with the launcher and is copied into matching Fabric profiles on start.
CLIENT_MOD_DIR = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent)) / "client-mod"
CLIENT_MOD_MC = "26.3"
STATUS_TEXT = {
    "Download": "Lädt Dateien herunter",
    "Install java runtime": "Installiert Java",
    "Installation complete": "Installation fertig",
    "Running installer": "Richtet den Mod-Loader ein",
}

_lock = threading.Lock()
_job = {"state": "idle", "profile_id": None, "status": "", "progress": 0, "max": 0, "error": "", "log": []}
_log = collections.deque(maxlen=120)
_process = None


def status():
    with _lock:
        job = dict(_job)
    job["log"] = list(_log)[-25:]
    return job


def _update(**changes):
    with _lock:
        _job.update(changes)


def _set_status(text):
    for prefix, german in STATUS_TEXT.items():
        if text.startswith(prefix):
            text = german + text[len(prefix):]
            break
    _update(status=text)


CALLBACK = {
    "setStatus": _set_status,
    "setProgress": lambda value: _update(progress=int(value)),
    "setMax": lambda value: _update(max=int(value)),
}


def java_for(mc_version):
    info = runtime.get_version_runtime_information(mc_version, MC_DIR)
    if info:
        path = runtime.get_executable_path(info["name"], MC_DIR)
        if path:
            return path
    raise UserError("Java für diese Minecraft-Version wurde nicht gefunden.")


def _installed():
    return storage.read_json(INSTALLED_FILE, {})


def _mark_installed(version_id):
    data = _installed()
    data[version_id] = int(time.time())
    storage.write_json(INSTALLED_FILE, data)


def install_profile(profile):
    """Makes sure the game files for a profile exist and returns the version id to launch."""
    mc, loader = profile["mc_version"], profile["loader"]
    _update(status="Prüft Minecraft " + mc, progress=0, max=0)
    if mc not in _installed():
        install.install_minecraft_version(mc, MC_DIR, callback=CALLBACK)
        _mark_installed(mc)
    if loader == "vanilla":
        return mc

    java = java_for(mc)
    if loader == "neoforge":
        builds = versions.neoforge_loader_versions(mc)
        if not builds:
            raise UserError(f"Für Minecraft {mc} gibt es noch kein NeoForge.")
        version_id = f"neoforge-{builds[0]}"
        if version_id not in _installed():
            _update(status="Richtet NeoForge ein", progress=0, max=0)
            Neoforge().install(mc, str(MC_DIR), CALLBACK, java, builds[0])
            install.install_minecraft_version(version_id, MC_DIR, callback=CALLBACK)
            _mark_installed(version_id)
        return version_id

    loader_api = mod_loader.get_mod_loader(loader)
    loader_version = loader_api.get_latest_loader_version(mc)
    version_id = loader_api.get_installed_version(mc, loader_version)
    if version_id not in _installed():
        _update(status=f"Richtet {loader_api.get_name()} ein", progress=0, max=0)
        version_id = loader_api.install(mc, MC_DIR, loader_version=loader_version, callback=CALLBACK, java=java)
        _mark_installed(version_id)
    return version_id


def sync_client_mod(profile):
    """Puts the newest Emberleaf client mod, resource pack and shader pack into Fabric profiles of the supported version."""
    if profile["loader"] == "fabric" and profile["mc_version"] == CLIENT_MOD_MC:
        if not (packassembler.GENERATED_DIR / packassembler.pack_name()).is_file():
            _update(status="Lädt die Texturenpakete (nur beim ersten Mal)", progress=0, max=100)
            try:
                indexes = sorted((MC_DIR / "assets" / "indexes").glob("*.json"))
                packassembler.ensure(_pack_progress, asset_indexes=indexes)
            except Exception as e:  # noqa: BLE001 - the game still starts, just without the pack (next start tries again)
                _log.append(f"Emberleaf-Texturenpaket konnte nicht gebaut werden: {e}")
        _ensure_pack_mods(profile)
    updates.sync_profile(profile, CLIENT_MOD_MC)


def _pack_progress(stage, done, total, what):
    """Downloading the packs is most of the work (0-90 %), stacking them the rest."""
    if stage == "download":
        _update(status=f"Lädt Texturenpakete: {what}", progress=int(done * 90 / max(total, 1)), max=100)
    else:
        _update(status=f"Baut dein Emberleaf-Texturenpaket: {what}", progress=90 + int(done * 10 / max(total, 1)), max=100)


# old marker files of earlier launcher versions (only Iris back then)
_LEGACY_MOD_MARKERS = {"iris.json": ("iris",)}


def _ensure_pack_mods(profile):
    """The texture pack and the shader need a few mods (Iris + Sodium, EMF, ETF, Continuity). Each is installed once;
    if the player removes one later, it stays gone."""
    from . import modrinth

    marker_dir = storage.profile_dir(profile["id"]) / ".emberleaf"
    marker_file = marker_dir / "pack-mods.json"
    marker = storage.read_json(marker_file, {})
    done = set(marker.get("done", []))
    for legacy, mods in _LEGACY_MOD_MARKERS.items():
        if storage.read_json(marker_dir / legacy, {}).get("done"):
            done.update(mods)
    missing = [m for m in packassembler.REQUIRED_MODS if m not in done]
    if not missing:
        return
    slugs = {c.get("slug") for c in storage.read_content(profile["id"])}
    for i, slug in enumerate(missing):
        if slug not in slugs:
            _update(status=f"Installiert Mods für Texturen und Shader ({i + 1}/{len(missing)})", progress=i, max=len(missing))
            try:
                modrinth.install(profile, slug, "mod")
            except Exception as e:  # noqa: BLE001 - offline: try again next start
                _log.append(f"{slug} konnte nicht installiert werden: {e}")
                continue
        done.add(slug)
    storage.write_json(marker_file, {"done": sorted(done), "at": int(time.time())})


def build_command(profile, version_id, account, join_server=None):
    settings = storage.get_settings()
    options = {
        "username": account["name"],
        "uuid": account["id"],
        "token": account["access_token"],
        "executablePath": java_for(profile["mc_version"]),
        # emberleaf.dataDir tells the client mod where the equipped cosmetics (outfit.json) live
        "jvmArguments": [f"-Xmx{settings['ram_mb']}M", "-Xms512M", f"-Demberleaf.dataDir={storage.DATA_DIR}"],
        "launcherName": LAUNCHER_NAME,
        "launcherVersion": APP_VERSION,
        "gameDirectory": str(storage.profile_dir(profile["id"])),
    }
    if join_server:
        options["quickPlayMultiplayer"] = join_server  # "Mitspielen": joins the friend's server right away
    return command.get_minecraft_command(version_id, MC_DIR, options)


def _watch(proc, log_file):
    with open(log_file, "w", encoding="utf-8") as out:
        for line in proc.stdout:
            _log.append(line.rstrip())
            out.write(line)
    code = proc.wait()
    if code == 0:
        _update(state="idle", status="Minecraft wurde beendet.")
    else:
        _update(state="error", error=f"Minecraft ist abgestürzt (Code {code}). Das Protokoll liegt im Profil-Ordner unter logs.")


def run(profile, get_account, on_started=None, join_server=None):
    """Installs and starts the game in a background thread. get_account is called right before starting."""
    global _process
    with _lock:
        if _job["state"] in ("installing", "running"):
            raise UserError("Minecraft wird gerade schon vorbereitet oder läuft.")
        _job.update(state="installing", profile_id=profile["id"], status="Startet …", progress=0, max=0, error="")
    _log.clear()

    def work():
        global _process
        try:
            account = get_account()
            version_id = install_profile(profile)
            sync_client_mod(profile)
            _update(status="Startet Minecraft …", progress=0, max=0)
            game_dir = storage.profile_dir(profile["id"])
            (game_dir / "logs").mkdir(parents=True, exist_ok=True)
            proc = subprocess.Popen(
                build_command(profile, version_id, account, join_server),
                cwd=game_dir,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                creationflags=subprocess.CREATE_NO_WINDOW,
            )
            _process = proc
            _update(state="running", status="Minecraft läuft")
            if on_started:
                on_started()
            _watch(proc, game_dir / "logs" / "emberleaf-start.log")
        except UserError as e:
            _update(state="error", error=str(e))
        except (UnsupportedVersion, VersionNotFound):
            _update(state="error", error="Diese Version wird vom Mod-Loader nicht unterstützt.")
        except requests.RequestException:
            _update(state="error", error="Download fehlgeschlagen. Bist du online?")
        except subprocess.CalledProcessError:
            _update(state="error", error="Der Mod-Loader konnte nicht installiert werden.")
        except Exception as e:  # noqa: BLE001 - show every failure in the UI instead of dying silently
            _update(state="error", error=f"Unerwarteter Fehler: {e}")
        finally:
            _process = None

    threading.Thread(target=work, daemon=True).start()


def stop():
    proc = _process
    if proc and proc.poll() is None:
        proc.terminate()
