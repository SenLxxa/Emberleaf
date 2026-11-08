"""Talks to the Emberleaf friends server ("Lagerfeuer") from the launcher.

The login is shared with the client mod through friends-session.json: whoever logs in first
(the mod in-game, or the launcher with a Microsoft account) writes it, the other one reuses it.
"""
import base64
import re
import secrets
import threading
import time

import requests

from . import accounts, storage
from .storage import UserError
from .version import USER_AGENT

CONFIG_FILE = storage.DATA_DIR / "friends.json"
SESSION_FILE = storage.DATA_DIR / "friends-session.json"
JOIN_URL = "https://sessionserver.mojang.com/session/minecraft/join"
HEADERS = {"User-Agent": USER_AGENT}

_lock = threading.Lock()


def server_url():
    url = str(storage.get_settings().get("friends_server", "")).strip().rstrip("/")
    return url if storage.FRIENDS_SERVER_RE.fullmatch(url) else ""


def write_config():
    """Tells the client mod which friends server to use (empty = friends turned off)."""
    storage.write_json(CONFIG_FILE, {"server": server_url()})


def forget_session():
    SESSION_FILE.unlink(missing_ok=True)


def _session():
    """The saved friends login, if it belongs to this server and the current account."""
    data = storage.read_json(SESSION_FILE, None)
    if not isinstance(data, dict) or data.get("server") != server_url():
        return None
    if data.get("expires", 0) < time.time() + 3600:
        return None
    account = accounts.public_account()
    if account is not None and account["id"].replace("-", "") != data.get("uuid"):
        return None
    return data


def _login():
    """Proves the Minecraft account to the friends server, like joining an online-mode server."""
    url = server_url()
    acc = accounts.session()  # raises a friendly UserError when nobody is logged in
    server_id = secrets.token_hex(16)
    try:
        r = requests.post(JOIN_URL, json={
            "accessToken": acc["access_token"], "selectedProfile": acc["id"], "serverId": server_id,
        }, headers=HEADERS, timeout=15)
        if r.status_code not in (200, 204):
            raise UserError("Mojang hat die Anmeldung fürs Lagerfeuer abgelehnt. Melde dich im Launcher neu an.")
        r = requests.post(f"{url}/v1/auth", json={"name": acc["name"], "serverId": server_id}, headers=HEADERS, timeout=15)
    except requests.RequestException as e:
        raise UserError("Das Lagerfeuer ist gerade nicht erreichbar.") from e
    if r.status_code != 200:
        raise UserError(_error(r, "Die Anmeldung am Lagerfeuer hat nicht geklappt."))
    data = r.json()
    session = {"server": url, "token": data["token"], "uuid": data["uuid"], "name": data["name"], "expires": data["expires"]}
    storage.write_json(SESSION_FILE, session)
    return session


def _error(response, fallback):
    try:
        return response.json().get("error") or fallback
    except ValueError:
        return fallback


def _call(method, path, payload=None, data=None, content_type="application/json"):
    if not server_url():
        raise UserError("Trag zuerst in den Einstellungen die Adresse deines Lagerfeuers ein.")
    with _lock:
        session = _session() or _login()
    headers = {**HEADERS, "Authorization": f"Bearer {session['token']}"}
    if data is not None:
        headers["Content-Type"] = content_type
    try:
        r = requests.request(method, f"{session['server']}{path}", json=payload, data=data, headers=headers, timeout=15)
    except requests.RequestException as e:
        raise UserError("Das Lagerfeuer ist gerade nicht erreichbar.") from e
    if r.status_code == 401:
        forget_session()
        raise UserError("Deine Lagerfeuer-Anmeldung ist abgelaufen. Versuch es gleich nochmal.")
    if r.status_code >= 300:
        raise UserError(_error(r, "Das Lagerfeuer hat einen Fehler gemeldet."))
    return r.json()


def overview():
    """Everything the friends page needs, or a hint why it cannot show friends yet."""
    if not server_url():
        return {"state": "no_server"}
    if _session() is None and accounts.public_account() is None:
        return {"state": "no_login"}
    data = _call("GET", "/v1/friends")  # {me, friends, incoming, outgoing}
    data["state"] = "ok"
    return data


def add(name):
    name = str(name or "").strip()
    if not storage.USERNAME_RE.fullmatch(name):
        raise UserError("Gib einen gültigen Minecraft-Namen ein.")
    return _call("POST", "/v1/friends/add", {"name": name})


def accept(uuid):
    return _call("POST", "/v1/friends/accept", {"uuid": str(uuid)})


def remove(uuid):
    return _call("POST", "/v1/friends/remove", {"uuid": str(uuid)})


# ---------- Chat, Party und Handy (wie in der Handy-App und im Spiel) ----------

CHAT_RE = re.compile(r"[dgp][0-9a-f]{16,64}")
UUID_RE = re.compile(r"[0-9a-f]{32}")
DATA_URL_RE = re.compile(r"data:(image/(?:png|jpeg|webp|gif));base64,([A-Za-z0-9+/=]+)")
PARTY_ACTIONS = {"create", "invite", "join", "decline", "leave", "kick"}
MAX_IMAGE_BYTES = 450 * 1024


def _uuid(value):
    value = str(value or "").replace("-", "")
    if not UUID_RE.fullmatch(value):
        raise UserError("Unbekannter Spieler.")
    return value


def _chat(value):
    value = str(value or "")
    if not CHAT_RE.fullmatch(value):
        raise UserError("Unbekannter Chat.")
    return value


def chats():
    return _call("GET", "/v1/chats")


def chat_messages(chat, limit=50):
    return _call("GET", f"/v1/chat/messages?chat={_chat(chat)}&limit={max(1, min(100, int(limit)))}")


def chat_send(chat=None, to=None, text="", kind="text", data=None, reply=None):
    payload = {"text": str(text or ""), "kind": kind}
    if to:
        payload["to"] = _uuid(to)
    else:
        payload["chat"] = _chat(chat)
    if data is not None:
        payload["data"] = data
    if reply:
        payload["reply"] = int(reply)
    return _call("POST", "/v1/chat/send", payload)


def chat_react(message, emoji):
    return _call("POST", "/v1/chat/react", {"message": int(message), "emoji": str(emoji)})


def chat_read(chat, message):
    return _call("POST", "/v1/chat/read", {"chat": _chat(chat), "message": int(message)})


def chat_delete(message):
    return _call("POST", "/v1/chat/delete", {"message": int(message)})


def chat_group(name, members):
    return _call("POST", "/v1/chat/group", {"name": str(name or ""), "members": [_uuid(m) for m in members or []]})


def chat_group_add(chat, uuid):
    return _call("POST", "/v1/chat/group/add", {"chat": _chat(chat), "uuid": _uuid(uuid)})


def chat_group_leave(chat):
    return _call("POST", "/v1/chat/group/leave", {"chat": _chat(chat)})


def chat_group_rename(chat, name):
    return _call("POST", "/v1/chat/group/rename", {"chat": _chat(chat), "name": str(name or "")})


def chat_game(chat, game):
    if game not in ("ttt", "c4"):
        raise UserError("Unbekanntes Spiel.")
    return _call("POST", "/v1/chat/game", {"chat": _chat(chat), "game": game})


def chat_move(message, cell):
    return _call("POST", "/v1/chat/game/move", {"message": int(message), "cell": int(cell)})


def chat_image(data_url):
    """Uploads a picture (data URL from the file picker, already made small in the UI) and returns its id."""
    match = DATA_URL_RE.fullmatch(str(data_url or ""))
    if not match:
        raise UserError("Das ist kein Bild (PNG, JPEG, WebP oder GIF).")
    raw = base64.b64decode(match.group(2))
    if len(raw) > MAX_IMAGE_BYTES:
        raise UserError("Das Bild ist zu groß (höchstens 450 KB).")
    return _call("PUT", "/v1/image", data=raw, content_type=match.group(1))


def party():
    return _call("GET", "/v1/party")


def party_action(action, party=None, uuid=None):
    if action not in PARTY_ACTIONS:
        raise UserError("Unbekannte Party-Aktion.")
    payload = {}
    if party:
        payload["party"] = str(party)
    if uuid:
        payload["uuid"] = _uuid(uuid)
    return _call("POST", f"/v1/party/{action}", payload)


def pair_phone():
    """A one-time code (and QR code) that logs the phone app in with this account."""
    from . import qrcode

    data = _call("POST", "/v1/pair/start", {})
    data["qr"] = qrcode.svg_data_url(data["url"])
    data["app"] = f"{server_url()}/app/"
    return data


def website_url(site_url, path="/"):
    """A page of the Emberleaf website, already logged in with this account when possible (one-time code in the
    #fragment, which never reaches a server log). Without a Lagerfeuer login, or with another friends server,
    just the page."""
    page = site_url.rstrip("/") + path
    if server_url() != site_url.rstrip("/") or (_session() is None and accounts.public_account() is None):
        return page
    try:
        code = _call("POST", "/v1/pair/start", {})["code"]
    except (UserError, KeyError):
        return page
    return f"{page}#login={code}"


def image_url(image_id):
    return f"{server_url()}/v1/image/{image_id}"


def upload_outfit_async(items_json):
    """Shares the new outfit with friends right away (silently skipped when not set up)."""
    if not server_url() or _session() is None:
        return

    def work():
        try:
            _call("PUT", "/v1/outfit", data=items_json.encode("utf-8"))
        except UserError:
            pass  # beim nächsten Spielstart lädt der Mod es ohnehin hoch

    threading.Thread(target=work, daemon=True).start()
