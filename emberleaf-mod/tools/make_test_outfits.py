"""Writes test outfits from the HD starter set for the in-game screenshot tests (src/gametest/resources)."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT.parent / "emberleaf"))
from backend import bbmodel  # noqa: E402

MODELS = ROOT.parent / "emberleaf/ui/assets/models"
OUT = ROOT / "src/gametest/resources/emberleaf-test"
OUTFITS = {
    "outfit-a": {"head": "beanie", "wings": "leaf_wings", "cape": "ember_cape", "pet": "fox_pet"},
    "outfit-b": {"head": "fox_ears", "wings": "dragon_wings", "body": "backpack"},
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, slots in OUTFITS.items():
        items = {}
        for slot, file in slots.items():
            model = bbmodel.parse((MODELS / f"{file}.bbmodel").read_text(encoding="utf-8"))
            items[slot] = {"id": file.replace("_", ""), "name": file, "model": model}
        items["emote"] = {"id": "emote-cheer"}
        (OUT / f"{name}.json").write_text(json.dumps({"version": 1, "items": items}), encoding="utf-8")
        print(name, ", ".join(slots.values()))


if __name__ == "__main__":
    main()
