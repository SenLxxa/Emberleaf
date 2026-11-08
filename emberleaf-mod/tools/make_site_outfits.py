"""Builds the outfits the website screenshots are taken with, from the launcher's cosmetics catalog.

    emberleaf\\.venv\\Scripts\\python.exe emberleaf-mod\\tools\\make_site_outfits.py

Writes build/test-assets/outfits/<look>.json in the same format the launcher hands to the client mod
(outfit.json: {"version", "items": {category: {"id", "name", "model": <converted Blockbench model>}}}).
The game test WebsiteShotsTest wears them (run with EMBERLEAF_TEST_ONLY=website).
"""
import json
import os
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "emberleaf-mod" / "build" / "test-assets" / "outfits"
# never touch the real launcher data: the backend creates its data folder on import
os.environ["EMBERLEAF_DATA_DIR"] = tempfile.mkdtemp(prefix="emberleaf-outfits-")
sys.path.insert(0, str(ROOT / "emberleaf"))
from backend import cosmetics  # noqa: E402

LEAF_AURA = {"id": "aura-leaves", "name": "Blätterwirbel", "aura": {"mode": "swirl", "sprite": "leaf"}}
LOOKS = {
    "eternal": {"head": "eternal-schein", "wings": "eternal-fluegel", "cape": "eternal-licht", "body": "eternal-schwert", "pet": "eternal-geist"},
    "herbst": {"head": "beanie", "wings": "leafwings", "cape": "phoenixschwinge", "body": "backpack", "pet": "pet-katze", "aura": LEAF_AURA},
    "drache": {"head": "foxears", "wings": "dragonwings", "cape": "sturmbrecher", "pet": "pet-drache"},
    "frost": {"head": "bandana", "cape": "frostkrone", "pet": "pet-qualle"},
    "prisma": {"cape": "prismenherz", "pet": "pet-runenwolf"},
    "leere": {"cape": "leerenriss", "pet": "pet-phoenix"},
}


def main():
    catalog = {c["id"]: c for c in cosmetics.builtin_catalog()}
    OUT.mkdir(parents=True, exist_ok=True)
    for look, parts in LOOKS.items():
        items = {}
        for category, item in parts.items():
            if isinstance(item, dict):
                items[category] = item
                continue
            entry = catalog[item]
            items[category] = {"id": entry["id"], "name": entry["name"], "model": cosmetics.model(entry["id"])}
        data = {"version": 1, "updated": 0, "items": items}
        path = OUT / f"{look}.json"
        path.write_text(json.dumps(data, separators=(",", ":")), "utf-8")
        print(f"{look}: {', '.join(items)} ({path.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
