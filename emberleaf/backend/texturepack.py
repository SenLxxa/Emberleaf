"""Builds the "Emberleaf Texturen" resource pack on this PC (16x pixel art).

- ~235 blocks are Emberleaf's own pixel art (pixelblocks.py): stones, earth, woods, ores, wool, concrete …
- leaves get an extra outer layer (bushy, like big soft canopies), lanterns a real 3D model,
  sun and moon are glowing cubes, common blocks get random variants so the world does not repeat
- every enchanted book gets its own book (cover colour by type, emblem by enchantment, level pips)
- the dark Emberleaf hotbar and bars
- everything else keeps its vanilla shape but gets the calm Emberleaf colours (cool shadows, warm lights)

The pack is generated from the player's own copy of the game, so no Minecraft files are ever shipped or shared.
Run directly for testing:  python -m backend.texturepack <client.jar> <out.zip>
"""
import copy
import io
import json
import re
import sys
import time
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

from . import pixelart, pixelblocks, storage

GENERATOR_VERSION = "2.0.0"
PACK_FAMILY = "Emberleaf-Texturen"
LEGACY_FAMILIES = ("Emberleaf-64x",)
PACK_FORMAT = (97, 1)  # Minecraft 26.3
INCLUDE = ("block", "item", "entity", "particle", "painting", "mob_effect", "map", "environment", "misc")
SKIP_FILES = {"environment/clouds.png", "misc/enchanted_glint_armor.png", "misc/enchanted_glint_item.png"}
SKIP_PREFIX = ("trims/", "gui/", "font/", "colormap/", "effect/", "palettes/")
HUD_DARK = ("gui/sprites/hud/hotbar.png", "gui/sprites/hud/hotbar_selection.png", "gui/sprites/hud/hotbar_offhand_left.png",
            "gui/sprites/hud/hotbar_offhand_right.png", "gui/sprites/hud/experience_bar_background.png")
TINTED_BLOCK = re.compile(
    r"^(grass_block_top|grass_block_side_overlay|short_grass|tall_grass_(top|bottom)|fern|large_fern_(top|bottom)|"
    r"(oak|jungle|acacia|dark_oak|mangrove|birch|spruce)_leaves|vine|water_(still|flow|overlay)|lily_pad|sugar_cane|"
    r"(attached_)?(pumpkin|melon)_stem|redstone_dust_(dot|line0|line1|overlay)|bush|leaf_litter|pink_petals_stem|"
    r"wildflowers_stem|potted_.*)$")
APP_ROOT = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
GENERATED_DIR = storage.DATA_DIR / "generated" / "resourcepack"

CATEGORY = {  # cover dark, cover light
    "weapon": ("#7a221d", "#a8352a"), "armor": ("#1f3f6e", "#2f5d9e"), "tool": ("#25562a", "#3a7d3f"),
    "bow": ("#6e4712", "#a0691e"), "trident": ("#11564f", "#1d8378"), "fishing": ("#16506a", "#247aa0"),
    "crossbow": ("#532e14", "#7d4a22"), "mace": ("#3a3e48", "#5a606e"), "curse": ("#2e1538", "#4c2460"),
    "other": ("#6a4c14", "#9c7322"),
}
ENCHANT_STYLE = {
    "sharpness": ("weapon", "sword"), "smite": ("weapon", "sun"), "bane_of_arthropods": ("weapon", "spider"),
    "knockback": ("weapon", "burst"), "fire_aspect": ("weapon", "flame"), "looting": ("weapon", "gem"),
    "sweeping_edge": ("weapon", "arc"), "efficiency": ("tool", "pickaxe"), "silk_touch": ("tool", "feather"),
    "fortune": ("tool", "clover"), "unbreaking": ("other", "anvil"), "mending": ("other", "heart"),
    "protection": ("armor", "shield"), "fire_protection": ("armor", "shield_flame"), "blast_protection": ("armor", "shield_burst"),
    "projectile_protection": ("armor", "shield_arrow"), "thorns": ("armor", "thorns"), "respiration": ("armor", "bubbles"),
    "aqua_affinity": ("armor", "wave"), "feather_falling": ("armor", "feather"), "depth_strider": ("armor", "wave"),
    "frost_walker": ("armor", "snowflake"), "soul_speed": ("armor", "soul"), "swift_sneak": ("armor", "steps"),
    "power": ("bow", "bow"), "punch": ("bow", "burst"), "flame": ("bow", "flame"), "infinity": ("bow", "infinity"),
    "luck_of_the_sea": ("fishing", "clover"), "lure": ("fishing", "hook"), "loyalty": ("trident", "heart"),
    "impaling": ("trident", "trident"), "riptide": ("trident", "spiral"), "channeling": ("trident", "bolt"),
    "multishot": ("crossbow", "arrows"), "quick_charge": ("crossbow", "bolt"), "piercing": ("crossbow", "arrow"),
    "density": ("mace", "anvil"), "breach": ("mace", "shield_burst"), "wind_burst": ("mace", "spiral"),
    "lunge": ("weapon", "arrow"), "binding_curse": ("curse", "chain"), "vanishing_curse": ("curse", "ghost"),
}
_WARM_GLASS = pixelart.pal("#a8551a", "#e8902c", "#ffc35a", "#fff2b0")
_WARM_FLAME = pixelart.pal("#ff9a2e", "#fff0a8")
LANTERNS = {  # model name -> (frame palette, glass palette, flame palette)
    "lantern": (pixelart.pal("#2a2420", "#45403a", "#6b645b"), _WARM_GLASS, _WARM_FLAME),
    "soul_lantern": (pixelart.pal("#22262a", "#3b4248", "#5d666e"), pixelart.pal("#1c6f80", "#2fb2c8", "#7ee6f2", "#e0fbff"),
                     pixelart.pal("#3fd0e0", "#d8fbff")),
    "copper_lantern": (pixelart.pal("#6e3420", "#a6552f", "#d07a47"), _WARM_GLASS, _WARM_FLAME),
    "exposed_copper_lantern": (pixelart.pal("#6a4a38", "#9c6c4e", "#bf8c68"), _WARM_GLASS, _WARM_FLAME),
    "weathered_copper_lantern": (pixelart.pal("#3f5c4a", "#5c8a6c", "#7fae8c"), _WARM_GLASS, _WARM_FLAME),
    "oxidized_copper_lantern": (pixelart.pal("#2f6455", "#4a9580", "#6fc0a7"), _WARM_GLASS, _WARM_FLAME),
}


# ---------- Farbabgleich für alles, was nicht neu gemalt ist ----------

def _grade(arr, grey):
    """Calm, cohesive colours without blurring a single pixel: a touch less saturation, shadows lean cool,
    lights lean warm, blacks lifted a little (the soft look of the packs the player picked)."""
    a = arr.astype(np.float32) / 255
    rgb = a[..., :3]
    lum = rgb @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    if not grey:
        rgb = lum[..., None] + (rgb - lum[..., None]) * 0.9
        w = np.clip((lum - 0.2) / 0.6, 0, 1)[..., None]
        rgb = rgb * (np.array([0.95, 0.96, 1.05]) * (1 - w) + np.array([1.05, 1.0, 0.92]) * w)
    rgb = rgb * 0.94 + 0.035
    a[..., :3] = np.clip(rgb, 0, 1)
    return (a * 255 + 0.5).astype(np.uint8)


def _dark_hud(arr):
    """Hotbar & bars in a dark wood-and-gold look (Emberleaf HUD)."""
    a = arr.astype(np.float32)
    lum = (a[..., :3] @ np.array([0.2126, 0.7152, 0.0722])) / 255
    ramp = np.array([[18, 13, 11], [34, 26, 21], [52, 40, 33], [74, 58, 47], [120, 92, 60], [214, 170, 92]], np.float32)
    idx = np.clip(lum * (len(ramp) - 1), 0, len(ramp) - 1)
    lo = np.floor(idx).astype(int)
    hi = np.minimum(lo + 1, len(ramp) - 1)
    k = (idx - lo)[..., None]
    a[..., :3] = ramp[lo] * (1 - k) + ramp[hi] * k
    return np.clip(a + 0.5, 0, 255).astype(np.uint8)


def _is_grey(arr):
    m = arr[..., 3] > 16
    if m.sum() < 4:
        return False
    rgb = arr[..., :3][m].astype(np.float32) / 255
    return float((rgb.max(-1) - rgb.min(-1)).mean()) < 0.045


def _png(arr):
    buf = io.BytesIO()
    Image.fromarray(np.asarray(arr, np.uint8), "RGBA").save(buf, "PNG", optimize=True)
    return buf.getvalue()


# ---------- Modelle ----------

def _leaves_model(vanilla_model, name):
    """Core cube + a bigger outer shell with a ragged 24x24 leaf layer: bushy canopies."""
    tinted = vanilla_model.get("parent", "").endswith("block/leaves")
    tex = vanilla_model.get("textures", {}).get("all", f"minecraft:block/{name}")
    tint = {"tintindex": 0} if tinted else {}
    sides = ("down", "up", "north", "south", "west", "east")
    core = {d: {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": d, **tint} for d in sides}
    shell = {d: {"uv": [0, 0, 16, 16], "texture": "#fluff", **tint} for d in sides}
    return {"parent": "minecraft:block/block", "textures": {"particle": tex, "all": tex, "fluff": f"emberleaf:block/fluff/{name}"},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": core},
                         {"from": [-4, -4, -4], "to": [20, 20, 20], "faces": shell}]}


def _box(frm, to, uv_side, uv_top=None, tex="#lantern"):
    faces = {d: {"uv": uv_side, "texture": tex} for d in ("north", "south", "west", "east")}
    faces["up"] = {"uv": uv_top or uv_side, "texture": tex}
    faces["down"] = {"uv": uv_top or uv_side, "texture": tex}
    return {"from": frm, "to": to, "faces": faces}


def _lantern_model(hanging):
    """3D lantern: base plate, four corner posts, glowing glass, flame, stepped roof and a ring (or chain)."""
    y = 3 if hanging else 0
    els = [
        _box([4, y, 4], [12, y + 1, 12], [0, 12, 16, 13], [0, 12, 8, 16]),            # base plate
        _box([5, y + 1, 5], [11, y + 8, 11], [8, 0, 14, 7], [4, 8, 8, 12]),          # glass box
        _box([7, y + 2, 7], [9, y + 5, 9], [0, 8, 2, 11], [0, 8, 2, 10]),            # flame
        _box([4, y + 8, 4], [12, y + 9, 12], [0, 0, 8, 1], [8, 8, 16, 12]),          # roof steps
        _box([5, y + 9, 5], [11, y + 10, 11], [0, 1, 6, 2], [8, 8, 14, 12]),
        _box([6, y + 10, 6], [10, y + 11, 10], [0, 2, 4, 3], [8, 8, 12, 12]),
    ]
    for cx, cz in ((4, 4), (11, 4), (4, 11), (11, 11)):  # corner posts
        els.append(_box([cx, y + 1, cz], [cx + 1, y + 8, cz + 1], [0, 0, 1, 7], [0, 0, 1, 1]))
    for rot in (45, -45):  # ring / chain as two crossed planes
        els.append({"from": [7, y + 11, 8], "to": [9, 16 if hanging else y + 13, 8],
                    "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": rot},
                    "faces": {"north": {"uv": [1, 0, 2, 5], "texture": "#lantern"}, "south": {"uv": [1, 0, 2, 5], "texture": "#lantern"}}})
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": {"particle": "#lantern"}, "elements": els}


# ---------- Bücher ----------

def _enchantments(z):
    out = {}
    for name in z.namelist():
        m = re.match(r"data/minecraft/enchantment/([a-z0-9_]+)\.json$", name)
        if m:
            try:
                out[m[1]] = int(json.loads(z.read(name)).get("max_level", 1))
            except (ValueError, TypeError):
                out[m[1]] = 1
    return out


def _books(enchantments):
    files = {}
    cases = []
    for ench, max_level in sorted(enchantments.items()):
        cat, emblem = ENCHANT_STYLE.get(ench, ("other", "burst"))
        for level in range(1, max(1, max_level) + 1):
            key = f"{ench}_{level}"
            files[f"assets/emberleaf/textures/item/book/{key}.png"] = _png(pixelart.book16(CATEGORY[cat], emblem, level, max_level))
            files[f"assets/emberleaf/models/item/book/{key}.json"] = json.dumps(
                {"parent": "minecraft:item/generated", "textures": {"layer0": f"emberleaf:item/book/{key}"}}).encode()
            cases.append({"when": {f"minecraft:{ench}": level},
                          "model": {"type": "minecraft:model", "model": f"emberleaf:item/book/{key}"}})
    files["assets/minecraft/items/enchanted_book.json"] = json.dumps({"model": {
        "type": "minecraft:select", "property": "minecraft:component", "component": "minecraft:stored_enchantments",
        "cases": cases, "fallback": {"type": "minecraft:model", "model": "minecraft:item/enchanted_book"}}}, indent=1).encode()
    return files


# ---------- Varianten (gegen Wiederholung) ----------

def _variant_blockstate(vanilla, name, count):
    data = copy.deepcopy(vanilla)
    variants = data.get("variants", {})
    if list(variants) != [""]:
        return None
    entries = variants[""] if isinstance(variants[""], list) else [variants[""]]
    out = []
    for e in entries:
        out.append(e)
        for v in range(1, count + 1):
            alt = dict(e)
            alt["model"] = f"emberleaf:block/variants/{name}_{v}"
            out.append(alt)
    data["variants"][""] = out
    return data


def _pack_icon():
    logo = APP_ROOT / "ui" / "assets" / "logo.png"
    if logo.is_file():
        img = Image.open(logo).convert("RGBA").resize((128, 128), Image.LANCZOS)
    else:
        img = Image.fromarray(pixelart.book16(CATEGORY["other"], "heart", 1, 1)).resize((128, 128), Image.NEAREST)
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return buf.getvalue()


def pack_name():
    return f"{PACK_FAMILY}-{GENERATOR_VERSION}.zip"


def generate(jar_path, out_zip, progress=None):
    """Builds the pack from the game's client jar. progress(done, total) is called now and then."""
    started = time.time()
    out_zip = Path(out_zip)
    tmp = out_zip.with_suffix(".part")
    out_zip.parent.mkdir(parents=True, exist_ok=True)
    stats = {"pixelart": 0, "graded": 0, "books": 0, "leaves": 0, "lanterns": 0, "variants": 0, "skipped": 0}
    with zipfile.ZipFile(jar_path) as z, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as out:
        names = set(z.namelist())

        def read_json(name):
            try:
                return json.loads(z.read(name))
            except (KeyError, ValueError):
                return None

        todo = []
        for name in sorted(names):
            m = re.match(r"assets/minecraft/textures/(.+\.png)$", name)
            if not m:
                continue
            rel = m[1]
            kind = rel.split("/", 1)[0]
            if rel in HUD_DARK:
                todo.append((name, rel, "hud"))
            elif kind not in INCLUDE or rel in SKIP_FILES or rel.startswith(SKIP_PREFIX) or "celestial/" in rel:
                stats["skipped"] += 1
            else:
                todo.append((name, rel, kind))
        total = len(todo)
        for i, (name, rel, kind) in enumerate(todo):
            if progress and i % 60 == 0:
                progress(i, total)
            meta_name = name + ".mcmeta"
            meta = read_json(meta_name) if meta_name in names else None
            base = rel[:-4].split("/")[-1]
            if kind == "block" and base in pixelblocks.TEXTURES:
                art = pixelblocks.TEXTURES[base]()
                out.writestr(name, _png(art))
                new_meta = dict(meta or {})
                if art.shape[0] > art.shape[1]:
                    new_meta["animation"] = {"frametime": pixelblocks.ANIMATED.get(base, 2)}
                else:
                    new_meta.pop("animation", None)
                if new_meta:
                    out.writestr(meta_name, json.dumps(new_meta))
                stats["pixelart"] += 1
                continue
            try:
                arr = np.asarray(Image.open(io.BytesIO(z.read(name))).convert("RGBA"), np.uint8)
            except OSError:
                stats["skipped"] += 1
                continue
            if kind == "hud":
                out.writestr(name, _png(_dark_hud(arr)))
            else:
                grey = (kind == "block" and bool(TINTED_BLOCK.match(base))) or (kind != "block" and _is_grey(arr))
                out.writestr(name, _png(_grade(arr, grey)))
            if meta is not None:
                out.writestr(meta_name, z.read(meta_name))
            stats["graded"] += 1

        # sun and moon: glowing cubes
        out.writestr("assets/minecraft/textures/environment/celestial/sun.png", _png(pixelart.cube_sky(
            [np.array([200, 110, 40]), np.array([186, 84, 24]), np.array([236, 146, 52]), np.array([255, 224, 140])])))
        phases = {"full_moon": (0, False), "waning_gibbous": (1, False), "third_quarter": (2, False), "waning_crescent": (3, False),
                  "new_moon": (4, False), "waxing_crescent": (3, True), "first_quarter": (2, True), "waxing_gibbous": (1, True)}
        for phase, (p, mirror) in phases.items():
            img = pixelart.moon_cube(p)
            out.writestr(f"assets/minecraft/textures/environment/celestial/moon/{phase}.png", _png(np.fliplr(img) if mirror else img))

        # bushy leaves
        for leaf in pixelblocks.LEAVES:
            model_name = f"assets/minecraft/models/block/{leaf}.json"
            vanilla = read_json(model_name)
            if vanilla is None:
                continue
            out.writestr(model_name, json.dumps(_leaves_model(vanilla, leaf)))
            out.writestr(f"assets/emberleaf/textures/block/fluff/{leaf}.png", _png(pixelblocks.fluff(leaf)))
            stats["leaves"] += 1

        # 3D lanterns
        out.writestr("assets/emberleaf/models/block/lantern3d.json", json.dumps(_lantern_model(False)))
        out.writestr("assets/emberleaf/models/block/lantern3d_hanging.json", json.dumps(_lantern_model(True)))
        for lantern, (frame, glass_col, flame) in LANTERNS.items():
            if f"assets/minecraft/models/block/{lantern}.json" not in names:
                continue
            out.writestr(f"assets/emberleaf/textures/block/lantern/{lantern}.png", _png(pixelart.lantern_tex(frame, glass_col, flame)))
            for suffix, parent in (("", "lantern3d"), ("_hanging", "lantern3d_hanging")):
                out.writestr(f"assets/minecraft/models/block/{lantern}{suffix}.json", json.dumps(
                    {"parent": f"emberleaf:block/{parent}", "textures": {"lantern": f"emberleaf:block/lantern/{lantern}"}}))
            stats["lanterns"] += 1

        # random variants
        for block, (count, gen) in pixelblocks.VARIANTS.items():
            state_name = f"assets/minecraft/blockstates/{block}.json"
            vanilla = read_json(state_name)
            state = _variant_blockstate(vanilla, block, count) if vanilla else None
            if state is None:
                continue
            for v in range(1, count + 1):
                out.writestr(f"assets/emberleaf/textures/block/variants/{block}_{v}.png", _png(gen(v)))
                out.writestr(f"assets/emberleaf/models/block/variants/{block}_{v}.json", json.dumps(
                    {"parent": "minecraft:block/cube_all", "textures": {"all": f"emberleaf:block/variants/{block}_{v}"}}))
            out.writestr(state_name, json.dumps(state))
            stats["variants"] += count

        books = _books(_enchantments(z))
        for path, data in books.items():
            out.writestr(path, data)
        stats["books"] = sum(1 for p in books if p.endswith(".png"))
        out.writestr("assets/emberleaf/emberleaf_pack.json", json.dumps({"dark_hud": True}))
        out.writestr("pack.mcmeta", json.dumps({"pack": {
            "description": "§6Emberleaf Texturen§r – gemütliche Pixelkunst",
            "min_format": [PACK_FORMAT[0], 0], "max_format": list(PACK_FORMAT)}}, ensure_ascii=False, indent=1))
        out.writestr("pack.png", _pack_icon())
        out.writestr("emberleaf.json", json.dumps({"generator": GENERATOR_VERSION, "built": int(time.time()), **stats}))
    tmp.replace(out_zip)
    if progress:
        progress(total, total)
    stats["seconds"] = round(time.time() - started, 1)
    stats["bytes"] = out_zip.stat().st_size
    return stats


def ensure(mc_dir, mc_version, progress=None):
    """Builds the pack once per generator version from the installed game. Returns its path or None."""
    target = GENERATED_DIR / pack_name()
    if target.is_file():
        return target
    jar = Path(mc_dir) / "versions" / mc_version / f"{mc_version}.jar"
    if not jar.is_file():
        return None
    GENERATED_DIR.mkdir(parents=True, exist_ok=True)
    for family in (PACK_FAMILY, *LEGACY_FAMILIES):
        for old in GENERATED_DIR.glob(f"{family}-*"):
            old.unlink(missing_ok=True)
    generate(jar, target, progress)
    return target


if __name__ == "__main__":
    def show(done, total):
        print(f"\r{done}/{total}", end="", flush=True)

    result = generate(sys.argv[1], sys.argv[2], show)
    print("\n", json.dumps(result))
