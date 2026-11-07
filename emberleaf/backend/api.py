"""Everything the UI may call. Each public method is one /api/<name> endpoint."""
import os
import threading
import time

import json
import re
import sys
from datetime import date, timedelta
from pathlib import Path

from . import accounts, cosmetics, friends, game, modrinth, skins, storage, updates, versions
from .storage import UserError
from .version import APP_LABEL

APP_VERSION = APP_LABEL
_APP_ROOT = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
# installiert: <Emberleaf>/lagerfeuer, beim Entwickeln: der Quellordner daneben
LAGERFEUER_DIRS = (_APP_ROOT / "lagerfeuer", _APP_ROOT.parent / "emberleaf-server")
SERVER_RE = re.compile(r"[A-Za-z0-9.\-]{1,253}(:\d{1,5})?")
EXPOSED = {
    "state", "versions", "skin", "skin_library", "skin_add", "skin_save_current", "skin_remove", "skin_apply", "save_settings", "profiles", "create_profile", "delete_profile",
    "content", "search", "install", "remove", "add_local", "open_folder", "window_action",
    "login", "logout", "play", "game_status", "stop_game",
    "cosmetics", "equip", "save_cosmetic", "delete_cosmetic", "export_outfit", "model", "import_model",
    "friends", "friend_add", "friend_accept", "friend_remove", "update_status", "update_check", "update_install",
    "update_seen", "open_github",
    "island_atlas", "playtime",
    "chats", "chat_messages", "chat_send", "chat_react", "chat_read", "chat_delete", "chat_group", "chat_group_add", "chat_group_leave",
    "chat_group_rename", "chat_game", "chat_move", "chat_image", "party", "party_action", "pair_phone",
}


def _playtime():
    """What the client mod counted (playtime.json in the Emberleaf data folder): total, today, last 7 days."""
    data = storage.read_json(storage.DATA_DIR / "playtime.json", {})
    if not isinstance(data, dict):
        data = {}
    days = data.get("days") if isinstance(data.get("days"), dict) else {}
    today = date.today()
    week_start = (today - timedelta(days=6)).isoformat()

    def seconds(value):
        return int(value) if isinstance(value, (int, float)) else 0

    return {
        "total": seconds(data.get("total")),
        "today": seconds(days.get(today.isoformat())),
        "week": sum(seconds(v) for k, v in days.items() if k >= week_start),
        "sessions": seconds(data.get("sessions")),
        "since": data.get("since"),
    }


class Api:
    def __init__(self):
        self.window = None
        self._maximized = False
        try:
            friends.write_config()  # the client mod reads the Lagerfeuer address from friends.json
        except OSError:
            pass
        updates.start_background()

    def attach_window(self, window):
        self.window = window
        window.events.maximized += lambda *_: setattr(self, "_maximized", True)
        window.events.restored += lambda *_: setattr(self, "_maximized", False)

    def state(self):
        return {
            "version": APP_VERSION,
            "settings": storage.get_settings(),
            "profiles": storage.list_profiles(),
            "loaders": storage.LOADERS,
            "icons": storage.ICONS,
            "account": accounts.public_account(),
            "offline_play": accounts.offline_allowed(),
            "cosmetics": cosmetics.get(),
            "game": game.status(),
            "desktop": self.window is not None,
            "playtime": _playtime(),
        }

    def playtime(self):
        return _playtime()

    def island_atlas(self):
        """The start page island with the real block textures of the game (None: keep the bundled drawing)."""
        from . import islandtextures

        try:
            return {"url": islandtextures.atlas_url()}
        except Exception:  # noqa: BLE001 - broken or half-written files: the bundled island still works
            return {"url": None}

    # ---------- Profile & Inhalte ----------

    def versions(self, loader):
        if loader not in storage.LOADERS:
            raise UserError("Unbekannter Mod-Loader.")
        return versions.minecraft_versions(loader)

    def skin(self, name):
        return skins.get_skin(name)

    def skin_library(self):
        return skins.library()

    def skin_add(self, name, model, data):
        return skins.add_to_library(name, model, data)

    def skin_save_current(self):
        name = (accounts.public_account() or {}).get("name") or storage.get_settings()["username"]
        current = skins.get_skin(name)
        if not current.get("skin"):
            raise UserError("Für diesen Namen gibt es keinen Skin.")
        return skins.add_to_library(f"{current['name']} (gespeichert)", current["model"], current["skin"])

    def skin_remove(self, skin_id):
        return skins.remove_from_library(skin_id)

    def skin_apply(self, skin_id):
        if accounts.public_account() is None:
            raise UserError("Zum Skin-Wechseln musst du mit Microsoft angemeldet sein.")
        return skins.apply(skin_id, accounts.session())

    def save_settings(self, **patch):
        settings = storage.save_settings(patch)
        if "friends_server" in patch:
            friends.write_config()
        return settings

    def profiles(self):
        return storage.list_profiles()

    def create_profile(self, name, loader, mc_version, icon, voicechat=True):
        if loader not in storage.LOADERS or mc_version not in versions.minecraft_versions(loader):
            raise UserError("Diese Version gibt es für den gewählten Loader nicht.")
        profile = storage.create_profile(name, loader, mc_version, icon)
        # Fabric API comes first: almost every Fabric mod needs it, and mods like Voice Chat bundle
        # older parts of it that crash on new Minecraft versions unless the current one is present.
        wanted = (["fabric-api"] if loader == "fabric" else []) + (["simple-voice-chat"] if voicechat and loader != "vanilla" else [])
        installed, warnings = [], []
        for slug in wanted:
            try:
                result = modrinth.install(profile, slug, "mod")
                installed += result["installed"]
                warnings += result["warnings"]
            except UserError as e:
                warnings.append(f"{slug}: {e}")
        storage.save_settings({"last_profile": profile["id"]})
        return {"profile": profile, "installed": installed, "warnings": warnings}

    def delete_profile(self, profile_id):
        storage.delete_profile(profile_id)
        return storage.list_profiles()

    def content(self, profile_id):
        return storage.read_content(storage.get_profile(profile_id)["id"])

    def search(self, profile_id, project_type, query="", offset=0):
        return modrinth.search(query, project_type, storage.get_profile(profile_id), offset)

    def install(self, profile_id, project_id, project_type):
        return modrinth.install(storage.get_profile(profile_id), project_id, project_type)

    def add_local(self, profile_id, project_type, filename, data):
        return modrinth.add_local(storage.get_profile(profile_id), project_type, filename, data)

    def remove(self, profile_id, project_id):
        return modrinth.remove(storage.get_profile(profile_id), project_id)

    def open_folder(self, profile_id=None, target=None):
        if target == "models":  # Blockbench templates of the starter set
            os.startfile(cosmetics.BUILTIN_MODELS)
            return
        if target == "lagerfeuer":
            folder = next((d for d in LAGERFEUER_DIRS if (d / "ANLEITUNG.md").is_file()), None)
            if folder is None:
                raise UserError("Die Lagerfeuer-Anleitung fehlt in dieser Emberleaf-Installation.")
            os.startfile(folder)
            return
        folder = storage.profile_dir(storage.get_profile(profile_id)["id"]) if profile_id else storage.DATA_DIR
        folder.mkdir(parents=True, exist_ok=True)
        os.startfile(folder)

    # ---------- Konto & Spielen ----------

    def login(self):
        if self.window is None:
            raise UserError("Der Login funktioniert nur im Emberleaf-Fenster.")
        import webview

        url, state, verifier = accounts.login_url()
        found = {}
        done = threading.Event()
        popup = webview.create_window("Mit Microsoft anmelden", url, width=500, height=680)
        popup.events.closed += lambda *_: done.set()

        def watch():
            while not done.is_set():
                try:
                    current = popup.get_current_url() or ""
                except Exception:  # noqa: BLE001 - window not ready yet or already gone
                    current = ""
                if current.startswith(accounts.REDIRECT_URI):
                    found["url"] = current
                    popup.destroy()
                    break
                time.sleep(0.25)
            done.set()

        threading.Thread(target=watch, daemon=True).start()
        done.wait(600)
        if "url" not in found:
            raise UserError("Die Anmeldung wurde abgebrochen.")
        return accounts.finish_login(found["url"], state, verifier)

    def logout(self):
        accounts.logout()
        friends.forget_session()
        return accounts.public_account()

    def play(self, profile_id, server=None):
        profile = storage.get_profile(profile_id)
        storage.save_settings({"last_profile": profile_id})
        get_account = accounts.session
        if accounts.public_account() is None:
            name = storage.get_settings()["username"]
            if not (accounts.offline_allowed() and name):
                raise UserError("Bitte melde dich zuerst mit deinem Microsoft-Konto an.")
            get_account = lambda: accounts.offline_account(name)  # noqa: E731 - offline until Mojang approves the app
        if server is not None and not SERVER_RE.fullmatch(str(server)):
            raise UserError("Diese Serveradresse sieht nicht richtig aus.")

        def started():
            if self.window is not None and storage.get_settings()["minimize_on_play"]:
                self.window.minimize()

        game.run(profile, get_account, on_started=started, join_server=server)
        return game.status()

    def game_status(self):
        return game.status()

    def stop_game(self):
        game.stop()
        return game.status()

    # ---------- Cosmetics ----------

    def cosmetics(self):
        return cosmetics.get()

    def equip(self, category, item_id=None):
        return cosmetics.equip(category, item_id)

    def save_cosmetic(self, item):
        return cosmetics.save_item(item)

    def delete_cosmetic(self, item_id):
        return cosmetics.delete_item(item_id)

    def model(self, item_id):
        return cosmetics.model(item_id)

    def import_model(self, name, category, text):
        return cosmetics.import_model(name, category, text)

    def export_outfit(self, outfit):
        cosmetics.export_outfit(outfit)
        friends.upload_outfit_async(json.dumps(outfit, separators=(",", ":")))

    # ---------- Freunde (Lagerfeuer) ----------

    def friends(self):
        return friends.overview()

    def friend_add(self, name):
        return friends.add(name)

    def friend_accept(self, uuid):
        return friends.accept(uuid)

    def friend_remove(self, uuid):
        return friends.remove(uuid)

    # ---------- Chat, Party, Handy ----------

    def chats(self):
        return friends.chats()

    def chat_messages(self, chat, limit=50):
        return friends.chat_messages(chat, limit)

    def chat_send(self, chat=None, to=None, text="", kind="text", data=None, reply=None):
        return friends.chat_send(chat, to, text, kind, data, reply)

    def chat_react(self, message, emoji):
        return friends.chat_react(message, emoji)

    def chat_read(self, chat, message):
        return friends.chat_read(chat, message)

    def chat_delete(self, message):
        return friends.chat_delete(message)

    def chat_group(self, name, members):
        return friends.chat_group(name, members)

    def chat_group_add(self, chat, uuid):
        return friends.chat_group_add(chat, uuid)

    def chat_group_leave(self, chat):
        return friends.chat_group_leave(chat)

    def chat_group_rename(self, chat, name):
        return friends.chat_group_rename(chat, name)

    def chat_game(self, chat, game):
        return friends.chat_game(chat, game)

    def chat_move(self, message, cell):
        return friends.chat_move(message, cell)

    def chat_image(self, data_url):
        result = friends.chat_image(data_url)
        result["url"] = friends.image_url(result["id"])
        return result

    def party(self):
        return friends.party()

    def party_action(self, action, party=None, uuid=None):
        return friends.party_action(action, party, uuid)

    def pair_phone(self):
        return friends.pair_phone()

    # ---------- Client-Updates ----------

    def update_status(self):
        return updates.status()

    def update_check(self):
        return updates.check(force=True)

    def update_install(self):
        """ "Jetzt neu starten": swaps in the downloaded Emberleaf, starts it and closes this window."""
        if game.status().get("state") in ("installing", "running"):
            raise UserError("Minecraft läuft gerade. Spiel ruhig weiter: Das Update wird beim nächsten Start von Emberleaf installiert.")
        updates.install_now()
        if self.window is not None:
            threading.Timer(0.4, self.window.destroy).start()  # let the answer reach the window first
        else:
            threading.Timer(0.4, os._exit, args=(0,)).start()
        return {"restarting": True}

    def update_seen(self):
        return updates.mark_seen()

    def open_github(self):
        import webbrowser

        from .version import GITHUB_URL
        webbrowser.open(f"{GITHUB_URL}/releases")

    # ---------- Fenster ----------

    def window_action(self, action):
        if self.window is None:
            return
        if action == "minimize":
            self.window.minimize()
        elif action == "maximize":
            if self._maximized:
                self.window.restore()
            else:
                self.window.maximize()
        elif action == "close":
            self.window.destroy()
