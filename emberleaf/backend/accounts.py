"""Microsoft accounts for starting Minecraft.

The tokens are stored encrypted with Windows DPAPI, so only this Windows user
on this PC can read them.
"""
import ctypes
import hashlib
import json
import sys
import threading
import time
import uuid
from ctypes import wintypes

import requests
from minecraft_launcher_lib import microsoft_account
from minecraft_launcher_lib.exceptions import AccountNotOwnMinecraft, AzureAppNotPermitted, InvalidRefreshToken

from . import storage
from .storage import UserError

REDIRECT_URI = "https://login.microsoftonline.com/common/oauth2/nativeclient"
# Emberleafs eigene Azure-App (öffentliche ID, kein Geheimnis). In den Einstellungen lässt sich eine andere eintragen.
CLIENT_ID = "c921157f-fcc0-47b6-9eb0-ec3eaa8c6d76"
ACCOUNTS_FILE = storage.DATA_DIR / "accounts.dat"
TOKEN_LIFETIME = 20 * 3600  # Minecraft tokens last 24h; refresh a bit early

_lock = threading.Lock()


class _Blob(ctypes.Structure):
    _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_char))]


def _dpapi(data, protect):
    buf = ctypes.create_string_buffer(data, len(data))
    blob_in = _Blob(len(data), ctypes.cast(buf, ctypes.POINTER(ctypes.c_char)))
    blob_out = _Blob()
    crypt32 = ctypes.windll.crypt32
    fn = crypt32.CryptProtectData if protect else crypt32.CryptUnprotectData
    if not fn(ctypes.byref(blob_in), None, None, None, None, 0, ctypes.byref(blob_out)):
        raise OSError("DPAPI failed")
    try:
        return ctypes.string_at(blob_out.pbData, blob_out.cbData)
    finally:
        ctypes.windll.kernel32.LocalFree(blob_out.pbData)


def _load():
    try:
        return json.loads(_dpapi(ACCOUNTS_FILE.read_bytes(), protect=False))
    except (OSError, ValueError):
        return {"active": None, "accounts": {}}


def _save(data):
    ACCOUNTS_FILE.parent.mkdir(parents=True, exist_ok=True)
    tmp = ACCOUNTS_FILE.with_suffix(".tmp")
    tmp.write_bytes(_dpapi(json.dumps(data).encode(), protect=True))
    tmp.replace(ACCOUNTS_FILE)


def public_account():
    """The active account without any tokens, for the UI."""
    with _lock:
        data = _load()
    acc = data["accounts"].get(data["active"] or "")
    return {"id": acc["id"], "name": acc["name"]} if acc else None


def _client_id():
    return storage.get_settings()["azure_client_id"] or CLIENT_ID


def _store(result):
    account = {
        "id": result["id"],
        "name": result["name"],
        "access_token": result["access_token"],
        "refresh_token": result["refresh_token"],
        "expires": int(time.time()) + TOKEN_LIFETIME,
    }
    with _lock:
        data = _load()
        data["accounts"][account["id"]] = account
        data["active"] = account["id"]
        _save(data)
    storage.save_settings({"username": account["name"]})
    return {"id": account["id"], "name": account["name"]}


def _friendly(e):
    if isinstance(e, AzureAppNotPermitted):
        return UserError("Mojang hat deine Azure-App noch nicht freigeschaltet. Sobald die Freigabe da ist, klappt der Login.")
    if isinstance(e, AccountNotOwnMinecraft):
        return UserError("Dieses Microsoft-Konto besitzt kein Minecraft: Java Edition.")
    if isinstance(e, InvalidRefreshToken):
        return UserError("Deine Anmeldung ist abgelaufen. Bitte melde dich neu an.")
    if isinstance(e, KeyError):
        # Microsoft hat statt eines Tokens einen Fehler geschickt: meist falsche Client-ID oder Umleitungs-URI
        return UserError("Microsoft hat die Anmeldung abgelehnt. Prüf in Azure die Client-ID, die Umleitungs-URI "
                         "und dass „Persönliche Microsoft-Konten“ erlaubt sind.")
    return UserError("Die Anmeldung bei Microsoft hat nicht geklappt. Bist du online?")


def login_url():
    url, state, verifier = microsoft_account.get_secure_login_data(_client_id(), REDIRECT_URI)
    return url, state, verifier


def finish_login(redirect_url, state, verifier):
    try:
        code = microsoft_account.parse_auth_code_url(redirect_url, state)
    except (AssertionError, KeyError, ValueError) as e:
        raise UserError("Die Anmeldung wurde abgebrochen.") from e
    try:
        result = microsoft_account.complete_login(_client_id(), None, REDIRECT_URI, code, verifier)
        _check_ownership(result["access_token"])
    except (AzureAppNotPermitted, AccountNotOwnMinecraft, requests.RequestException, KeyError) as e:
        raise _friendly(e) from e
    return _store(result)


def _check_ownership(access_token):
    """Mojang requires launchers to verify the game license, not just the profile."""
    store = microsoft_account.get_store_information(access_token)
    owned = {item.get("name") for item in store.get("items", [])}
    if not owned & {"product_minecraft", "game_minecraft"}:
        raise AccountNotOwnMinecraft()


def logout():
    with _lock:
        data = _load()
        data["accounts"].pop(data["active"] or "", None)
        data["active"] = next(iter(data["accounts"]), None)
        _save(data)


def offline_allowed():
    """Until Mojang approves Emberleaf's Azure app the Microsoft login cannot work. The launcher run from source
    (pythonw main.py on the developer's own PC) may then start offline; the shared exe always needs a real login."""
    return not getattr(sys, "frozen", False)


def offline_account(name):
    """An offline player (singleplayer and offline-mode servers only) with the same UUID vanilla gives offline names."""
    digest = bytearray(hashlib.md5(f"OfflinePlayer:{name}".encode()).digest())
    digest[6] = digest[6] & 0x0F | 0x30
    digest[8] = digest[8] & 0x3F | 0x80
    return {"id": uuid.UUID(bytes=bytes(digest)).hex, "name": name, "access_token": "0", "offline": True}


def session():
    """A logged-in account with a fresh access token, refreshed when needed."""
    with _lock:
        data = _load()
    acc = data["accounts"].get(data["active"] or "")
    if not acc:
        raise UserError("Bitte melde dich zuerst mit deinem Microsoft-Konto an.")
    if acc["expires"] > time.time():
        return acc
    try:
        result = microsoft_account.complete_refresh(_client_id(), None, REDIRECT_URI, acc["refresh_token"])
    except (AzureAppNotPermitted, AccountNotOwnMinecraft, InvalidRefreshToken, requests.RequestException, KeyError) as e:
        raise _friendly(e) from e
    _store(result)
    with _lock:
        return _load()["accounts"][result["id"]]
