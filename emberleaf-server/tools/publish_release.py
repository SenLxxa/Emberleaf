"""Publishes an Emberleaf version on the website directly from this PC (the same API as the /upload page).

    emberleaf\\.venv\\Scripts\\python.exe emberleaf-server\\tools\\publish_release.py <folder> --version 0.0.8 [--notes notes.txt]

<folder> holds Emberleaf.exe, emberleaf-client-<version>.jar and Emberleaf-Glut-*.zip (e.g. emberleaf\\dist\\release-x.y.z).
Instead of a browser login it gives the owner (UPDATE_OWNER in wrangler.toml) a one-hour login straight in the
database via wrangler (needs the Cloudflare login on this PC) and deletes it again at the end.
"""
import argparse
import base64
import hashlib
import json
import os
import re
import secrets
import subprocess
import sys
import time
from pathlib import Path

import requests

SERVER = Path(__file__).resolve().parents[1]
TOOLS = Path.home() / "Emberleaf-Tools"
WRANGLER = TOOLS / "wrangler" / "node_modules" / ".bin" / "wrangler.cmd"
KINDS = (("launcher", re.compile(r"Emberleaf\.exe")), ("mod", re.compile(r"emberleaf-client-[\d.]+\.jar")),
         ("shaderpack", re.compile(r"Emberleaf-Glut-[\d.]+\.zip")))


def wrangler_sql(sql):
    env = dict(os.environ, PATH=f"{TOOLS / 'node'};{os.environ['PATH']}", WRANGLER_SEND_METRICS="false", NO_COLOR="1")
    out = subprocess.run([str(WRANGLER), "d1", "execute", "lagerfeuer", "--remote", "--json", "--command", sql],
                         cwd=SERVER, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if out.returncode != 0:
        raise SystemExit(f"wrangler d1 execute ist fehlgeschlagen:\n{out.stderr[-800:]}")
    return json.loads(out.stdout)[0]["results"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("folder")
    parser.add_argument("--version", required=True)
    parser.add_argument("--notes", default="")
    args = parser.parse_args()
    folder = Path(args.folder)
    toml = (SERVER / "wrangler.toml").read_text("utf-8")
    owner = re.search(r'UPDATE_OWNER = "([0-9a-f]{32})"', toml)[1]
    site = re.search(r'SITE_URL = "([^"]+)"', (SERVER.parent / "emberleaf" / "backend" / "version.py").read_text("utf-8"))[1]
    notes = Path(args.notes).read_text("utf-8").strip() if args.notes else ""

    files = []
    for kind, pattern in KINDS:
        for path in sorted(folder.iterdir()):
            if pattern.fullmatch(path.name):
                data = path.read_bytes()
                files.append({"name": path.name, "kind": kind, "size": len(data), "sha256": hashlib.sha256(data).hexdigest(), "path": path})
    if not any(f["kind"] == "launcher" for f in files):
        raise SystemExit(f"Emberleaf.exe fehlt in {folder}")
    for f in files:
        print(f"{f['name']:<30} {f['size'] / 1048576:7.1f} MB  {f['kind']}")

    token = base64.urlsafe_b64encode(secrets.token_bytes(32)).rstrip(b"=").decode()
    token_hash = hashlib.sha256(token.encode()).hexdigest()
    now = int(time.time())
    # the owner may not have logged in anywhere yet: the player row is what a login would create
    wrangler_sql(f"INSERT INTO players (uuid, name, created, last_seen) VALUES ('{owner}', 'SenLxca', {now}, {now}) ON CONFLICT(uuid) DO NOTHING; "
                 f"INSERT INTO sessions (token_hash, uuid, expires) VALUES ('{token_hash}', '{owner}', {now + 3600})")
    headers = {"Authorization": f"Bearer {token}", "User-Agent": "emberleaf-publish"}
    try:
        info = requests.get(f"{site}/v1/update", headers=headers, timeout=30).json()
        if not info.get("owner"):
            raise SystemExit("Der Server erkennt die Besitzer-Anmeldung nicht.")
        r = requests.post(f"{site}/v1/update/begin", headers=headers, timeout=30, json={
            "version": args.version, "notes": notes,
            "files": [{k: f[k] for k in ("name", "kind", "size", "sha256")} for f in files]})
        if r.status_code != 200:
            raise SystemExit(f"Entwurf abgelehnt: {r.text}")
        draft = r.json()
        part_bytes = draft["partBytes"]
        for f in files:
            data = f["path"].read_bytes()
            for part in range(max(1, -(-len(data) // part_bytes))):
                chunk = data[part * part_bytes:(part + 1) * part_bytes]
                for attempt in range(4):
                    r = requests.put(f"{site}/v1/update/part", params={"release": draft["id"], "file": f["name"], "part": part},
                                     headers=headers, data=chunk, timeout=120)
                    if r.status_code == 200:
                        break
                    time.sleep(2 * (attempt + 1))
                else:
                    raise SystemExit(f"Stück {part} von {f['name']} ging nicht hoch: {r.text}")
                print(f"  {f['name']} Stück {part + 1} hochgeladen")
        r = requests.post(f"{site}/v1/update/publish", headers=headers, timeout=30, json={"id": draft["id"]})
        if r.status_code != 200:
            raise SystemExit(f"Veröffentlichen abgelehnt: {r.text}")
        print(f"Emberleaf {args.version} ist auf {site} veröffentlicht.")
    finally:
        wrangler_sql(f"DELETE FROM sessions WHERE token_hash = '{token_hash}'")


if __name__ == "__main__":
    sys.exit(main())
