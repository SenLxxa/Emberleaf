"""Puts the website's big pictures (emberleaf-server/media/) into Cloudflare KV, only the ones that changed.

    python tools/upload_media.py --remote                      (the real website, deploy.ps1 calls this)
    python tools/upload_media.py --local --persist-to <dir>     (wrangler dev --local with the same --persist-to)

The worker serves them under /m/<name>. Every file carries its checksum as KV metadata; that is how changed files
are found (KV list returns the metadata, no file has to be read).
"""
import argparse
import base64
import hashlib
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

SERVER = Path(__file__).resolve().parents[1]
MEDIA = SERVER / "media"
TOOLS = Path.home() / "Emberleaf-Tools"
WRANGLER = TOOLS / "wrangler" / "node_modules" / ".bin" / "wrangler.cmd"
TYPES = {".webp": "image/webp", ".png": "image/png", ".jpg": "image/jpeg"}


def wrangler(args, where):
    env = dict(os.environ, PATH=f"{TOOLS / 'node'};{os.environ['PATH']}", WRANGLER_SEND_METRICS="false", NO_COLOR="1")
    out = subprocess.run([str(WRANGLER), *args, "--binding", "FILES", *where], cwd=SERVER, env=env,
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    if out.returncode != 0:
        raise SystemExit(f"wrangler {' '.join(args[:3])} ist fehlgeschlagen:\n{out.stderr[-1200:]}")
    return out.stdout


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--remote", action="store_true")
    group.add_argument("--local", action="store_true")
    parser.add_argument("--persist-to")
    args = parser.parse_args()
    where = ["--remote"] if args.remote else ["--local", *(["--persist-to", args.persist_to] if args.persist_to else [])]

    listed = json.loads(wrangler(["kv", "key", "list", "--prefix", "media/"], where) or "[]")
    have = {k["name"]: (k.get("metadata") or {}).get("hash") for k in listed}
    batch = []
    for path in sorted(MEDIA.iterdir()):
        if path.suffix not in TYPES:
            continue
        data = path.read_bytes()
        digest = hashlib.sha256(data).hexdigest()[:16]
        key = f"media/{path.name}"
        if have.get(key) == digest:
            continue
        batch.append({"key": key, "value": base64.b64encode(data).decode("ascii"), "base64": True,
                      "metadata": {"type": TYPES[path.suffix], "hash": digest}})
    if not batch:
        print("Bilder: alle schon aktuell")
        return
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        json.dump(batch, f)
    try:
        wrangler(["kv", "bulk", "put", f.name], where)
    finally:
        os.unlink(f.name)
    print(f"Bilder hochgeladen: {len(batch)} ({', '.join(b['key'][6:] for b in batch)})")


if __name__ == "__main__":
    sys.exit(main())
