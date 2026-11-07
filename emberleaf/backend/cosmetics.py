"""Equipped cosmetics and the player's own creations (Blockbench models and capes)."""
import json
import re
import sys
import threading
import time
import uuid
from pathlib import Path

from . import bbmodel, storage
from .storage import UserError

COSMETICS_FILE = storage.DATA_DIR / "cosmetics.json"
MODELS_DIR = storage.DATA_DIR / "models"  # imported Blockbench models, already converted
_APP_ROOT = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
BUILTIN_MODELS = _APP_ROOT / "ui" / "assets" / "models"  # the HD starter set (.bbmodel)
MODEL_CATEGORIES = ("head", "body", "wings", "cape", "pet")
_model_cache = {}
# Fully resolved outfit (voxels, cape frames, aura settings …) for the in-game client mod
OUTFIT_FILE = storage.DATA_DIR / "outfit.json"
MAX_OUTFIT_CHARS = 8_000_000
CATEGORIES = ("head", "body", "wings", "aura", "pet", "minime", "emote", "cape")
VOXEL_CATEGORIES = ("head", "body", "wings", "pet")
ID_RE = re.compile(r"[a-z0-9_\-]{1,40}")
COLOR_RE = re.compile(r"#[0-9a-f]{6}")
MAX_VOXELS = 3000
MAX_CAPE_BYTES = 3_000_000

_lock = threading.Lock()


def _load():
    data = storage.read_json(COSMETICS_FILE, {})
    return {"equipped": data.get("equipped", {}), "custom": data.get("custom", [])}


def get():
    with _lock:
        data = _load()
    data["models"] = builtin_catalog()
    return data


def equip(category, item_id):
    if category not in CATEGORIES:
        raise UserError("Unbekannte Kategorie.")
    if item_id is not None and not ID_RE.fullmatch(str(item_id)):
        raise UserError("Unbekanntes Cosmetic.")
    with _lock:
        data = _load()
        if item_id is None:
            data["equipped"].pop(category, None)
        else:
            data["equipped"][category] = item_id
        storage.write_json(COSMETICS_FILE, data)
        return data["equipped"]


def builtin_catalog():
    return storage.read_json(BUILTIN_MODELS / "catalog.json", [])


def model(item_id):
    """The converted Blockbench model of a starter-set item or an imported one."""
    item_id = str(item_id)
    if not ID_RE.fullmatch(item_id):
        raise UserError("Unbekanntes Cosmetic.")
    if item_id in _model_cache:
        return _model_cache[item_id]
    entry = next((c for c in builtin_catalog() if c["id"] == item_id), None)
    if entry is not None:
        data = bbmodel.parse((BUILTIN_MODELS / f"{entry['file']}.bbmodel").read_text(encoding="utf-8"))
    else:
        data = storage.read_json(MODELS_DIR / f"{item_id}.json", None)
        if data is None:
            raise UserError("Dieses Cosmetic gibt es nicht mehr.")
    _model_cache[item_id] = data
    return data


def import_model(name, category, text):
    """Takes a .bbmodel file from Blockbench and adds it to the player's own cosmetics."""
    if category not in MODEL_CATEGORIES:
        raise UserError("Wähle, wo das Modell sitzen soll (Kopf, Rücken, Flügel, Cape oder Pet).")
    data = bbmodel.parse(str(text))
    item_id = "bb-" + uuid.uuid4().hex[:10]
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    storage.write_json(MODELS_DIR / f"{item_id}.json", data)
    item = {"id": item_id, "category": category, "name": str(name or "").strip()[:30] or "Mein Modell",
            "model": True, "updated": int(time.time())}
    with _lock:
        cos = _load()
        cos["custom"].append(item)
        storage.write_json(COSMETICS_FILE, cos)
    return item


def export_outfit(outfit):
    """Stores what the player wears so the client mod can draw it in the game."""
    if not isinstance(outfit, dict) or any(k not in CATEGORIES for k in outfit):
        raise UserError("Ungültiges Outfit.")
    for category, item in outfit.items():
        if not isinstance(item, dict) or not ID_RE.fullmatch(str(item.get("id", ""))):
            raise UserError("Ungültiges Outfit.")
        if item.get("model") is True:
            item["model"] = model(item["id"])  # the game gets the whole converted model
        if "voxels" in item:
            item["voxels"] = _clean_voxels(item["voxels"])
        if category == "cape" and not all(str(f).startswith("data:image/png;base64,") for f in item.get("frames", [])):
            raise UserError("Ungültiges Cape.")
    data = {"version": 1, "updated": int(time.time()), "items": outfit}
    if len(json.dumps(data)) > MAX_OUTFIT_CHARS:
        raise UserError("Dein Outfit ist zu groß für das Spiel.")
    storage.write_json(OUTFIT_FILE, data)


def _clean_voxels(voxels):
    if not isinstance(voxels, list) or not voxels:
        raise UserError("Dein Modell ist noch leer. Setz ein paar Blöcke!")
    if len(voxels) > MAX_VOXELS:
        raise UserError(f"Maximal {MAX_VOXELS} Blöcke pro Cosmetic.")
    clean, seen = [], set()
    for v in voxels:
        try:
            x, y, z, color = int(v[0]), int(v[1]), int(v[2]), str(v[3]).lower()
        except (TypeError, ValueError, IndexError) as e:
            raise UserError("Das Modell ist beschädigt.") from e
        if not all(-40 <= c <= 40 for c in (x, y, z)) or not COLOR_RE.fullmatch(color):
            raise UserError("Das Modell ist beschädigt.")
        if (x, y, z) not in seen:
            seen.add((x, y, z))
            clean.append([x, y, z, color])
    return clean


def save_item(item):
    category = item.get("category")
    if category != "cape":
        raise UserError("Eigene Modelle kommen jetzt aus Blockbench: Lade eine .bbmodel-Datei hoch.")
    name = str(item.get("name", "")).strip()[:30] or "Mein Cosmetic"
    clean = {"category": category, "name": name, "updated": int(time.time())}
    if category == "cape":
        image = str(item.get("image", ""))
        if not image.startswith("data:image/png;base64,") or len(image) > MAX_CAPE_BYTES:
            raise UserError("Capes brauchen ein PNG-Bild (maximal ca. 2 MB).")
        clean["image"] = image
        clean["frames"] = max(1, min(64, int(item.get("frames", 1))))
        clean["fps"] = max(1, min(30, int(item.get("fps", 8))))
    else:
        clean["voxels"] = _clean_voxels(item.get("voxels"))

    with _lock:
        data = _load()
        item_id = item.get("id")
        existing = next((c for c in data["custom"] if c["id"] == item_id), None)
        if existing:
            existing.update(clean)
            saved = existing
        else:
            saved = {"id": "my-" + uuid.uuid4().hex[:10], **clean}
            data["custom"].append(saved)
        storage.write_json(COSMETICS_FILE, data)
        return saved


def delete_item(item_id):
    with _lock:
        data = _load()
        item = next((c for c in data["custom"] if c["id"] == item_id), None)
        if item is None:
            raise UserError("Dieses Cosmetic gibt es nicht mehr.")
        data["custom"].remove(item)
        if data["equipped"].get(item["category"]) == item_id:
            data["equipped"].pop(item["category"])
        storage.write_json(COSMETICS_FILE, data)
        if item.get("model"):
            (MODELS_DIR / f"{item_id}.json").unlink(missing_ok=True)
            _model_cache.pop(item_id, None)
    return get()
