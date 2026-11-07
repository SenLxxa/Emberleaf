"""Local HTTP bridge: serves the UI folder and forwards POST /api/<name> to Api.

Only answers on 127.0.0.1, checks the Host header (against DNS rebinding) and
requires a per-start token on every API call, so other websites can't drive it.
"""
import json
import mimetypes
import secrets
import sys
import threading
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit

from .api import EXPOSED, Api
from .storage import UserError

# In the packaged .exe, PyInstaller unpacks the ui folder next to the code in sys._MEIPASS.
_APP_ROOT = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
UI_DIR = (_APP_ROOT / "ui").resolve()
mimetypes.add_type("font/woff2", ".woff2")
mimetypes.add_type("text/javascript", ".js")


class Bridge:
    def __init__(self, port=0):
        self.api = Api()
        self.token = secrets.token_urlsafe(24)
        self.httpd = ThreadingHTTPServer(("127.0.0.1", port), _make_handler(self))
        self.port = self.httpd.server_address[1]
        self.url = f"http://127.0.0.1:{self.port}/?t={self.token}"

    def start(self):
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()
        return self


def _make_handler(bridge):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def _host_ok(self):
            return self.headers.get("Host") in (f"127.0.0.1:{bridge.port}", f"localhost:{bridge.port}")

        def _send(self, status, body, content_type):
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def _json(self, status, payload):
            self._send(status, json.dumps(payload).encode(), "application/json; charset=utf-8")

        def do_GET(self):
            if not self._host_ok():
                return self._send(403, b"forbidden", "text/plain")
            rel = unquote(urlsplit(self.path).path).lstrip("/") or "index.html"
            target = (UI_DIR / rel).resolve()
            if not target.is_relative_to(UI_DIR) or not target.is_file():
                return self._send(404, b"not found", "text/plain")
            content_type = mimetypes.guess_type(target.name)[0] or "application/octet-stream"
            if content_type.startswith("text/") or content_type.endswith("javascript"):
                content_type += "; charset=utf-8"
            self._send(200, target.read_bytes(), content_type)

        def do_POST(self):
            token = self.headers.get("X-Emberleaf-Token", "")
            if not self._host_ok() or not secrets.compare_digest(token, bridge.token):
                return self._json(403, {"ok": False, "error": "Nicht erlaubt."})
            name = urlsplit(self.path).path.removeprefix("/api/")
            if name not in EXPOSED:
                return self._json(404, {"ok": False, "error": "Unbekannte Aktion."})
            try:
                length = int(self.headers.get("Content-Length") or 0)
                body = json.loads(self.rfile.read(length) or b"{}")
                if not isinstance(body, dict):
                    raise ValueError
            except ValueError:
                return self._json(400, {"ok": False, "error": "Ungültige Anfrage."})
            try:
                data = getattr(bridge.api, name)(**body)
                self._json(200, {"ok": True, "data": data})
            except UserError as e:
                self._json(200, {"ok": False, "error": str(e)})
            except Exception as e:  # noqa: BLE001 - every failure must reach the UI as a message
                traceback.print_exc()
                self._json(500, {"ok": False, "error": f"Unerwarteter Fehler: {e}"})

    return Handler
