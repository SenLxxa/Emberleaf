"""Baut worker.js (die eine Datei, die man bei Cloudflare einfügt) aus src/worker.js und der Handy-App in app/.

    python build.py

Die App-Dateien landen als Text (bzw. Bilder als Base64) im Worker, er liefert sie unter /app/ aus.
"""
import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app"
TYPES = {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".webmanifest": "application/manifest+json; charset=utf-8",
    ".png": "image/png",
}
MARKER = "/*@APP_FILES@*/ {}"
HEADER = "// GEBAUT AUS src/worker.js UND app/ MIT build.py – Änderungen bitte dort machen.\n"


def app_files():
    files = {}
    for path in sorted(APP.rglob("*")):
        if not path.is_file() or path.suffix not in TYPES:
            continue
        name = path.relative_to(APP).as_posix()
        if path.suffix == ".png":
            files[name] = {"type": TYPES[".png"], "base64": base64.b64encode(path.read_bytes()).decode("ascii")}
        else:
            files[name] = {"type": TYPES[path.suffix], "text": path.read_text("utf-8")}
    return files


def main():
    source = (ROOT / "src" / "worker.js").read_text("utf-8")
    if source.count(MARKER) != 1:
        raise SystemExit("Platzhalter für die App fehlt in src/worker.js")
    files = app_files()
    blob = json.dumps(files, ensure_ascii=False, separators=(",", ":"))
    (ROOT / "worker.js").write_text(HEADER + source.replace(MARKER, blob), "utf-8", newline="\n")
    size = (ROOT / "worker.js").stat().st_size
    print(f"worker.js gebaut: {len(files)} App-Dateien, {size / 1024:.0f} KB")


if __name__ == "__main__":
    main()
