"""The launcher's floating island with the real block textures of the game.

The island (ui/assets/island/<scene>.json) names its textures by tile ("grass_top", "log_side" …). On this PC the
launcher fills those tiles with the textures Minecraft actually shows: the Emberleaf texture pack if it was built,
else the game's own textures from the installed client jar. Nothing of it is shipped; without either, the island keeps
its bundled drawing (ui/assets/island/atlas.png).
"""
import base64
import hashlib
import io
import json
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

from . import packassembler, storage

APP_ROOT = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
BUNDLED_ATLAS = APP_ROOT / "ui" / "assets" / "island" / "atlas.png"
ISLAND_JSON = APP_ROOT / "ui" / "assets" / "island" / "autumn.json"
CACHE_DIR = storage.DATA_DIR / "generated" / "island"
MC_VERSION = "26.3"
ATLAS_VERSION = 2  # bump when the way tiles are made changes: the cached atlas is rebuilt
GRASS = "#9db653"  # the island's warm autumn grass (also used in the in-game menu)

# tile -> texture (assets/minecraft/textures/<path>.png), optional colour, optional grass overlay
TILES = {
    "grass_top": ("block/grass_block_top", GRASS),
    "grass_side": ("block/grass_block_side", None, "block/grass_block_side_overlay"),
    "dirt": ("block/dirt",), "stone": ("block/stone",), "stone_dark": ("block/deepslate",),
    "leaves_orange": ("block/oak_leaves", "#e3862e"), "leaves_red": ("block/oak_leaves", "#c9472d"),
    "leaves_yellow": ("block/oak_leaves", "#e4b53a"),
    "log_side": ("block/oak_log",), "log_top": ("block/oak_log_top",), "moss_stone": ("block/mossy_cobblestone",),
    "path": ("block/dirt_path_top",), "flower_red": ("block/poppy",), "flower_yellow": ("block/dandelion",),
    "mushroom": ("block/red_mushroom",), "tall_grass": ("block/short_grass", GRASS),
    "netherrack": ("block/netherrack",), "crimson_top": ("block/crimson_nylium",), "crimson_side": ("block/crimson_nylium_side",),
    "crimson_stem": ("block/crimson_stem",), "wart": ("block/nether_wart_block",), "shroomlight": ("block/shroomlight",),
    "lava": ("block/lava_still",), "blackstone": ("block/blackstone",), "magma": ("block/magma",),
    "crimson_roots": ("block/crimson_roots",), "end_stone": ("block/end_stone",), "purpur": ("block/purpur_block",),
    "chorus": ("block/chorus_plant",), "chorus_flower": ("block/chorus_flower",), "obsidian": ("block/obsidian",),
    "crystal": ("entity/end_crystal/end_crystal",), "end_rod": ("block/end_rod",), "deepslate": ("block/deepslate",),
    "amethyst": ("block/amethyst_block",), "moss_block": ("block/moss_block",), "glow_moss": ("block/moss_block",),
    "dripstone": ("block/dripstone_block",), "glow_berries": ("block/flowering_azalea_side",), "torch": ("block/torch",),
    "amethyst_bud": ("block/medium_amethyst_bud",), "moss_side": ("block/moss_block",),
}


def _sources():
    """Texture sources, best first: the Emberleaf texture pack, then the game itself."""
    out = []
    pack = packassembler.GENERATED_DIR / packassembler.pack_name()
    if pack.is_file():
        out.append(pack)
    jar = storage.DATA_DIR / "minecraft" / "versions" / MC_VERSION / f"{MC_VERSION}.jar"
    if jar.is_file():
        out.append(jar)
    return out


def _read(zips, path):
    for z in zips:
        try:
            return z.read(f"assets/minecraft/textures/{path}.png"), z
        except KeyError:
            continue
    return None, None


def _square(img):
    """Animated textures are a strip of frames: keep the first one."""
    w, h = img.size
    return img.crop((0, 0, w, w)) if h > w else img


def _tinted(img, colour):
    a = np.asarray(img.convert("RGBA"), np.float32)
    rgb = np.array([int(colour[i:i + 2], 16) for i in (1, 3, 5)], np.float32) / 255
    a[..., :3] *= rgb
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def _tile(zips, name, size):
    spec = TILES.get(name)
    if not spec:
        return None
    data, source = _read(zips, spec[0])
    if data is None:
        return None
    img = _square(Image.open(io.BytesIO(data)).convert("RGBA"))
    if spec[0] == "entity/end_crystal/end_crystal":
        w = img.size[0] / 64  # the glowing core of the crystal (front face of its inner cube)
        img = img.crop((int(40 * w), int(8 * w), int(48 * w), int(16 * w)))
    elif spec[0].endswith("_leaves") and source.filename.endswith(packassembler.pack_name()) and img.size[0] >= 32:
        n = img.size[0] // 2  # Better Leaves textures: the leaf sits in the middle, the bushy edge around it
        img = img.crop((n // 2, n // 2, n // 2 + n, n // 2 + n))
    if len(spec) > 1 and spec[1]:
        img = _tinted(img, spec[1])
    if spec[0].endswith("_leaves"):
        # the launcher draws leaves as closed blocks: the gaps become deep shadow, like leaves on "fast" graphics
        a = np.asarray(img, np.float32)
        solid = a[..., 3] > 127
        shadow = (a[solid][:, :3].mean(0) if solid.any() else np.array([60, 90, 40], np.float32)) * 0.62
        a[~solid, :3] = shadow
        a[..., 3] = 255
        img = Image.fromarray(a.astype(np.uint8), "RGBA")
    if len(spec) > 2:
        over, _ = _read(zips, spec[2])
        if over is not None:
            top = _tinted(_square(Image.open(io.BytesIO(over)).convert("RGBA")).resize(img.size, Image.NEAREST), GRASS)
            img = Image.alpha_composite(img, top)
    return img.resize((size, size), Image.NEAREST)


def atlas_png():
    """PNG bytes of the island atlas with real textures, or None (no game files yet: keep the bundled one)."""
    sources = _sources()
    if not sources or not ISLAND_JSON.is_file() or not BUNDLED_ATLAS.is_file():
        return None
    stamp = [f"v{ATLAS_VERSION}"] + [f"{p.name}:{p.stat().st_size}:{int(p.stat().st_mtime)}" for p in sources]
    key = hashlib.sha1("|".join(stamp).encode()).hexdigest()[:16]
    cached = CACHE_DIR / f"atlas-{key}.png"
    if cached.is_file():
        return cached.read_bytes()
    layout = json.loads(ISLAND_JSON.read_text("utf-8"))["atlas"]
    tiles, cols, old_tile = layout["tiles"], layout["columns"], layout["tile"]
    bundled = Image.open(BUNDLED_ATLAS).convert("RGBA")
    zips = [zipfile.ZipFile(p) for p in sources]
    try:
        size = 16
        for name in tiles:  # tile size = the largest texture (64 with Faithful 64x)
            spec = TILES.get(name)
            data, _ = _read(zips, spec[0]) if spec else (None, None)
            if data is not None:
                size = max(size, min(128, Image.open(io.BytesIO(data)).size[0]))
        rows = -(-len(tiles) // cols)
        out = Image.new("RGBA", (cols * size, rows * size))
        for i, name in enumerate(tiles):
            img = _tile(zips, name, size)
            if img is None:  # keep Emberleaf's own drawing for anything the game has no texture for
                c, r = i % cols, i // cols
                img = bundled.crop((c * old_tile, r * old_tile, (c + 1) * old_tile, (r + 1) * old_tile)).resize((size, size), Image.NEAREST)
            out.paste(img, ((i % cols) * size, (i // cols) * size))
    finally:
        for z in zips:
            z.close()
    buf = io.BytesIO()
    out.save(buf, "PNG", optimize=True)
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    for old in CACHE_DIR.glob("atlas-*.png"):
        old.unlink(missing_ok=True)
    cached.write_bytes(buf.getvalue())
    return buf.getvalue()


def atlas_url():
    png = atlas_png()
    return None if png is None else "data:image/png;base64," + base64.b64encode(png).decode("ascii")
