"""Baut worker.js (die eine Datei, die zu Cloudflare geht) aus src/worker.js, der Webseite in site/ und der Handy-App in app/.

    python build.py

Die Dateien landen als Text (bzw. Bilder und Schriften als Base64) im Worker:
die Webseite unter / und /s/, die Handy-App unter /app/.
"""
import base64
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TYPES = {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".webmanifest": "application/manifest+json; charset=utf-8",
    ".svg": "image/svg+xml",
    ".png": "image/png",
    ".webp": "image/webp",
    ".woff2": "font/woff2",
}
BINARY = {".png", ".webp", ".woff2"}
BUNDLES = {"/*@APP_FILES@*/ {}": ROOT / "app", "/*@SITE_FILES@*/ {}": ROOT / "site"}
# große Bilder kommen nicht in den Worker, sondern nach KV (tools/upload_media.py); Verweise /m/<name> bekommen ?v=
MEDIA = ROOT / "media"
MEDIA_REF = re.compile(r"/m/([a-z0-9-]+\.(?:webp|png|jpg))")


def media_versions():
    return {p.name: hashlib.sha256(p.read_bytes()).hexdigest()[:10] for p in MEDIA.glob("*") if p.is_file()} if MEDIA.is_dir() else {}


def with_media_versions(text, versions, name):
    def repl(m):
        if m[1] not in versions:
            raise SystemExit(f"{name}: Bild /m/{m[1]} gibt es nicht in media/")
        return f"/m/{m[1]}?v={versions[m[1]]}"
    return MEDIA_REF.sub(repl, text)
HEADER = "// GEBAUT AUS src/worker.js, site/ UND app/ MIT build.py – Änderungen bitte dort machen.\n"
LIMIT = 3 * 1024 * 1024  # Cloudflare (kostenlos): höchstens 3 MB Worker


def bundle(folder, versions):
    files = {}
    for path in sorted(folder.rglob("*")):
        if not path.is_file() or path.suffix not in TYPES:
            continue
        name = path.relative_to(folder).as_posix()
        data = path.read_bytes()
        if path.suffix in (".html", ".css", ".js"):
            data = with_media_versions(data.decode("utf-8"), versions, name).encode("utf-8")
        # ETag: der Browser fragt kurz nach und lädt nur neu, wenn sich die Datei geändert hat
        entry = {"type": TYPES[path.suffix], "etag": hashlib.sha256(data).hexdigest()[:20]}
        if path.suffix in BINARY:
            entry["base64"] = base64.b64encode(data).decode("ascii")
        else:
            entry["text"] = data.decode("utf-8")
        files[name] = entry
    return files


def main():
    source = (ROOT / "src" / "worker.js").read_text("utf-8")
    versions = media_versions()
    counts = []
    for marker, folder in BUNDLES.items():
        if source.count(marker) != 1:
            raise SystemExit(f"Platzhalter {marker} fehlt in src/worker.js")
        files = bundle(folder, versions)
        counts.append(f"{len(files)} aus {folder.name}/")
        source = source.replace(marker, json.dumps(files, ensure_ascii=False, separators=(",", ":")))
    out = ROOT / "worker.js"
    out.write_text(HEADER + source, "utf-8", newline="\n")
    size = out.stat().st_size
    print(f"worker.js gebaut: {', '.join(counts)}, {size / 1024:.0f} KB")
    if size > LIMIT:
        raise SystemExit("worker.js ist größer als 3 MB – das nimmt Cloudflare (kostenlos) nicht an.")


if __name__ == "__main__":
    main()
