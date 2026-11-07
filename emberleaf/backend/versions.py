"""Minecraft versions available for each mod loader, cached for a few minutes."""
import re
import threading
import time

import requests
from minecraft_launcher_lib import mod_loader, utils

from .storage import UserError

NEOFORGE_MAVEN = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"
RELEASE_RE = re.compile(r"\d+(\.\d+)+")
CACHE_SECONDS = 600

_cache = {}
_lock = threading.Lock()


def _version_key(version):
    return tuple(int(part) for part in version.split("."))


def neoforge_minecraft_version(neoforge_version):
    """Maps a NeoForge version to its Minecraft version: 21.1.77 -> 1.21.1, 26.3.0.101 -> 26.3.

    minecraft-launcher-lib still assumes the old scheme and turns 26.3 into "1.26.3".
    """
    if not re.fullmatch(r"\d+\.\d+\.\d+(\.\d+)?", neoforge_version):
        return None  # betas and other pre-releases
    parts = [int(p) for p in neoforge_version.split(".")]
    if parts[0] >= 26:
        patch = parts[2] if len(parts) == 4 else 0
        return f"{parts[0]}.{parts[1]}.{patch}" if patch else f"{parts[0]}.{parts[1]}"
    return f"1.{parts[0]}.{parts[1]}" if parts[1] else f"1.{parts[0]}"


def _neoforge_releases():
    with _lock:
        hit = _cache.get("neoforge-releases")
    if hit and time.time() - hit[0] < CACHE_SECONDS:
        return hit[1]
    releases = requests.get(NEOFORGE_MAVEN, timeout=15).json()["versions"]
    with _lock:
        _cache["neoforge-releases"] = (time.time(), releases)
    return releases


def neoforge_loader_versions(minecraft_version):
    """NeoForge builds for a Minecraft version, newest first."""
    try:
        releases = _neoforge_releases()
    except (requests.RequestException, ValueError, KeyError) as e:
        raise UserError("NeoForge-Versionen konnten nicht geladen werden. Bist du online?") from e
    return [v for v in reversed(releases) if neoforge_minecraft_version(v) == minecraft_version]


def _fetch(loader):
    if loader == "vanilla":
        return [v["id"] for v in utils.get_version_list() if v["type"] == "release"]
    if loader == "neoforge":
        found = {neoforge_minecraft_version(v) for v in _neoforge_releases()} - {None}
    else:
        found = set(mod_loader.get_mod_loader(loader).get_minecraft_versions(True))
    return sorted((v for v in found if RELEASE_RE.fullmatch(v)), key=_version_key, reverse=True)


def minecraft_versions(loader):
    with _lock:
        hit = _cache.get(loader)
    if hit and time.time() - hit[0] < CACHE_SECONDS:
        return hit[1]
    try:
        found = _fetch(loader)
    except (requests.RequestException, ValueError, KeyError) as e:
        raise UserError("Die Versionen konnten nicht geladen werden. Bist du online?") from e
    with _lock:
        _cache[loader] = (time.time(), found)
    return found
