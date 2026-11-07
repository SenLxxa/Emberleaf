"""Builds the floating menu islands shared by the launcher and the in-game main menu.

Four scenes: autumn (Oberwelt), nether, end and cave. Writes island-<scene>.json (blocks, props,
fox, friend spots, small islands) and one 32x cozy texture atlas for all of them.
Run:  python tools/make_island.py   (needs Pillow + numpy)
"""
import json
import math
import shutil
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
MOD_OUT = ROOT / "src/client/resources/assets/emberleaf"
UI_OUT = ROOT.parent / "emberleaf/ui/assets/island"
TILE = 32
SCENES = ("autumn", "nether", "end", "cave")
TILES = [
    "grass_top", "grass_side", "dirt", "stone", "stone_dark", "leaves_orange", "leaves_red", "leaves_yellow",
    "log_side", "log_top", "moss_stone", "path", "flower_red", "flower_yellow", "mushroom", "tall_grass",
    "netherrack", "crimson_top", "crimson_side", "crimson_stem", "wart", "shroomlight", "lava", "blackstone",
    "magma", "crimson_roots", "end_stone", "purpur", "chorus", "chorus_flower", "obsidian", "crystal",
    "end_rod", "deepslate", "amethyst", "moss_block", "glow_moss", "dripstone", "glow_berries", "torch",
    "amethyst_bud", "moss_side",
]
GLOW = ["shroomlight", "lava", "magma", "crystal", "end_rod", "amethyst", "glow_moss", "glow_berries", "torch", "amethyst_bud", "chorus_flower"]
# block type -> [top, side, bottom]
BLOCKS = {
    "grass": ["grass_top", "grass_side", "dirt"], "dirt": ["dirt"] * 3, "stone": ["stone"] * 3, "stone_dark": ["stone_dark"] * 3,
    "moss": ["moss_stone", "moss_stone", "stone"], "path": ["path", "grass_side", "dirt"], "log": ["log_top", "log_side", "log_top"],
    "leaves_orange": ["leaves_orange"] * 3, "leaves_red": ["leaves_red"] * 3, "leaves_yellow": ["leaves_yellow"] * 3,
    "netherrack": ["netherrack"] * 3, "crimson": ["crimson_top", "crimson_side", "netherrack"], "crimson_stem": ["crimson_stem"] * 3,
    "wart": ["wart"] * 3, "shroomlight": ["shroomlight"] * 3, "lava": ["lava"] * 3, "blackstone": ["blackstone"] * 3, "magma": ["magma"] * 3,
    "end_stone": ["end_stone"] * 3, "purpur": ["purpur"] * 3, "chorus": ["chorus"] * 3, "chorus_flower": ["chorus_flower"] * 3,
    "obsidian": ["obsidian"] * 3, "crystal": ["crystal"] * 3,
    "deepslate": ["deepslate"] * 3, "amethyst": ["amethyst"] * 3, "moss_block": ["moss_block", "moss_side", "deepslate"],
    "glow_moss": ["glow_moss", "moss_side", "deepslate"], "dripstone": ["dripstone"] * 3,
}
PROPS = ["flower_red", "flower_yellow", "mushroom", "tall_grass", "crimson_roots", "end_rod", "glow_berries", "torch", "amethyst_bud"]

THEMES = {
    "autumn": dict(top="grass", path="path", sub="dirt", deep="stone", deeper="stone_dark", accent="moss",
                   trees="autumn", props=[("tall_grass", 0.16), ("flower_red", 0.025), ("flower_yellow", 0.025), ("mushroom", 0.02)]),
    "nether": dict(top="crimson", path="blackstone", sub="netherrack", deep="netherrack", deeper="blackstone", accent="magma",
                   trees="fungus", props=[("crimson_roots", 0.16)]),
    "end": dict(top="end_stone", path="purpur", sub="end_stone", deep="end_stone", deeper="obsidian", accent="obsidian",
                trees="chorus", props=[("end_rod", 0.07)]),
    "cave": dict(top="moss_block", path="deepslate", sub="deepslate", deep="deepslate", deeper="deepslate", accent="amethyst",
                 trees="crystal", props=[("glow_berries", 0.07), ("amethyst_bud", 0.06), ("tall_grass", 0.06)]),
}


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], np.float32)


def vnoise(rng, size, cell):
    g = rng.random((size // cell + 2, size // cell + 2)).astype(np.float32)
    img = Image.fromarray((g * 255).astype(np.uint8)).resize(((size // cell + 2) * cell,) * 2, Image.BICUBIC)
    return np.asarray(img, np.float32)[:size, :size] / 255


def fbm(rng, size, cells=(16, 8, 4, 2)):
    out = np.zeros((size, size), np.float32)
    total = 0
    for i, c in enumerate(cells):
        amp = 0.5 ** i
        out += vnoise(rng, size, c) * amp
        total += amp
    return out / total


def ramp(t, cols):
    t = np.clip(t, 0, 1)[..., None]
    n = len(cols) - 1
    idx = np.minimum((t[..., 0] * n).astype(int), n - 1)
    k = t * n - idx[..., None]
    a = np.array(cols, np.float32)[idx]
    b = np.array(cols, np.float32)[idx + 1]
    return a + (b - a) * k


def cobbles(rng, s, xx, yy, cells=5, soft=9):
    pts = rng.random((cells, 2)) * s
    allp = np.concatenate([pts + [dx, dy] for dx in (-s, 0, s) for dy in (-s, 0, s)])
    d = np.min(np.stack([np.hypot(xx - px, yy - py) for px, py in allp]), 0)
    return 1 - np.clip(d / soft, 0, 1) * 0.35


def side_band(rng, top_cols, base_cols, n, fine, yy, depth=9):
    s = TILE
    base = ramp(n * 0.7 + fine * 0.3, [hexc(c) for c in base_cols])
    edge = depth + (vnoise(rng, s, 3)[0] * 6).astype(int)
    top = ramp(fine, [hexc(c) for c in top_cols])
    return np.where((yy < edge[None, :])[..., None], top, base)


def tile(name, rng):
    s = TILE
    yy, xx = np.mgrid[0:s, 0:s].astype(np.float32)
    n = fbm(rng, s)
    fine = vnoise(rng, s, 2)
    alpha = np.ones((s, s), np.float32)
    col = None
    if name == "grass_top":
        col = ramp(n * 0.8 + fine * 0.3, [hexc("#5f7a2e"), hexc("#7f9a38"), hexc("#a5b24a"), hexc("#c9c26a")])
        for _ in range(7):
            cx, cy = rng.integers(2, s - 2, 2)
            col[max(0, cy - 1):cy + 1, max(0, cx - 1):cx + 2] = hexc(rng.choice(["#d9682c", "#e8a23a", "#c4472c"]))
    elif name == "grass_side":
        col = side_band(rng, ["#6f8a33", "#a5b24a"], ["#5a3b25", "#7a5234", "#94683f"], n, fine, yy)
    elif name == "dirt":
        col = ramp(n * 0.7 + fine * 0.3, [hexc("#4e3420"), hexc("#6e4a2f"), hexc("#8c6440")])
    elif name == "path":
        col = ramp(n * 0.6 + fine * 0.4, [hexc("#8a6a44"), hexc("#a98458"), hexc("#c4a070")])
    elif name in ("stone", "stone_dark", "moss_stone", "blackstone", "end_stone", "deepslate"):
        base = {"stone": ["#6e6a73", "#8e8a92", "#aaa6ad"], "stone_dark": ["#3e3a48", "#55505e", "#6a6474"],
                "moss_stone": ["#6e6a73", "#8e8a92", "#aaa6ad"], "blackstone": ["#1e1a22", "#2e2832", "#463c48"],
                "end_stone": ["#cfc890", "#e2dca6", "#f2ecc0"], "deepslate": ["#2a2a32", "#3c3c46", "#52525e"]}[name]
        col = ramp(n * 0.6 + fine * 0.4, [hexc(c) for c in base]) * cobbles(rng, s, xx, yy)[..., None]
        if name == "moss_stone":
            moss = (fbm(rng, s, (8, 4, 2)) > 0.55)[..., None]
            col = np.where(moss, ramp(fine, [hexc("#5d7a2c"), hexc("#86a040")]), col)
        if name == "deepslate":
            col = col * (0.9 + 0.1 * np.sin(yy * 1.6 + n * 4))[..., None]
    elif name.startswith("leaves"):
        base = {"leaves_orange": ["#9a3e1c", "#d0692c", "#f29a44", "#ffc673"], "leaves_red": ["#7a2418", "#b8382a", "#e0603e", "#f49a6a"],
                "leaves_yellow": ["#8a6a1a", "#d0a032", "#f2cc5a", "#fff0a0"]}[name]
        col = ramp((vnoise(rng, s, 3) * 0.6 + fine * 0.4) * 0.9 + (1 - yy / s) * 0.25, [hexc(c) for c in base])
    elif name in ("log_side", "crimson_stem"):
        cols = ["#3a2616", "#5a3c24", "#7a5434"] if name == "log_side" else ["#3a1424", "#5e1e36", "#8a2e4a"]
        stripes = np.sin(xx * 1.3 + vnoise(rng, s, 4) * 5) * 0.5 + 0.5
        col = ramp(stripes * 0.6 + fine * 0.4, [hexc(c) for c in cols])
    elif name == "log_top":
        r = np.hypot(xx - s / 2 + 0.5, yy - s / 2 + 0.5)
        col = ramp((np.sin(r * 1.4) * 0.5 + 0.5) * 0.6 + fine * 0.3, [hexc("#9a7448"), hexc("#c49a62"), hexc("#ddb87e")])
        col = np.where((r > s / 2 - 3)[..., None], hexc("#4a3020"), col)
    elif name == "netherrack":
        col = ramp(n * 0.6 + fine * 0.4, [hexc("#4a1414"), hexc("#6e2020"), hexc("#8e3a30")]) * cobbles(rng, s, xx, yy, 7, 6)[..., None]
    elif name == "crimson_top":
        col = ramp(n * 0.7 + fine * 0.4, [hexc("#7a1418"), hexc("#a82024"), hexc("#d84040")])
    elif name == "crimson_side":
        col = side_band(rng, ["#8a181c", "#c83034"], ["#4a1414", "#6e2020", "#8e3a30"], n, fine, yy, 7)
    elif name == "wart":
        col = ramp(vnoise(rng, s, 3) * 0.7 + fine * 0.3, [hexc("#5a0a10"), hexc("#8a1a1e"), hexc("#b8302e")])
    elif name == "shroomlight":
        col = ramp(vnoise(rng, s, 4) * 0.7 + fine * 0.3, [hexc("#e07a2a"), hexc("#ffb048"), hexc("#ffe08a")])
    elif name == "lava":
        flow = np.sin(xx * 0.35 + n * 6) * 0.5 + 0.5
        col = ramp(flow * 0.7 + fine * 0.3, [hexc("#c8320c"), hexc("#ff7a1a"), hexc("#ffd060")])
    elif name == "magma":
        cracks = np.abs(np.sin(xx * 0.5 + n * 7) * np.sin(yy * 0.5 + fine * 5))
        col = np.where((cracks < 0.12)[..., None], ramp(fine, [hexc("#ff8a1a"), hexc("#ffd060")]),
                       ramp(n, [hexc("#3a1410"), hexc("#5a2416")]))
    elif name == "purpur":
        brick = ((xx.astype(int) % 8 == 0) | (yy.astype(int) % 8 == 0))[..., None]
        col = np.where(brick, hexc("#6a4a8a"), ramp(n * 0.6 + fine * 0.4, [hexc("#9a70b8"), hexc("#b48ad0"), hexc("#c8a2e0")]))
    elif name == "chorus":
        col = ramp(vnoise(rng, s, 3) * 0.7 + fine * 0.3, [hexc("#4a3458"), hexc("#7a5a8a"), hexc("#9a7aa8")])
    elif name == "chorus_flower":
        col = ramp(fine, [hexc("#b48ad0"), hexc("#e8d0f8")])
    elif name == "obsidian":
        col = ramp(n * 0.7 + fine * 0.3, [hexc("#0e0814"), hexc("#1e1430"), hexc("#3a2a58")])
        col = np.where((fine > 0.86)[..., None], hexc("#7a5ab8"), col)
    elif name in ("crystal", "amethyst"):
        facets = np.abs(np.sin((xx + yy) * 0.4 + n * 3)) * 0.6 + np.abs(np.sin((xx - yy) * 0.3)) * 0.4
        cols = ["#c86aa8", "#f0a0d8", "#fff0fa"] if name == "crystal" else ["#5a3a9a", "#8a5ad0", "#d0a8ff"]
        col = ramp(facets, [hexc(c) for c in cols])
    elif name in ("moss_block", "glow_moss"):
        col = ramp(vnoise(rng, s, 3) * 0.6 + fine * 0.4, [hexc("#3e6a22"), hexc("#5e8a2e"), hexc("#86aa40")])
        if name == "glow_moss":
            dots = (fine > 0.8)[..., None]
            col = np.where(dots, hexc("#d8ff8a"), col)
    elif name == "moss_side":
        col = side_band(rng, ["#4e7a28", "#7a9a38"], ["#2a2a32", "#3c3c46", "#52525e"], n, fine, yy, 8)
    elif name == "dripstone":
        col = ramp((np.sin(xx * 0.9 + n * 3) * 0.5 + 0.5) * 0.6 + fine * 0.4, [hexc("#6a5040"), hexc("#8a6a54"), hexc("#a88a70")])
    if col is not None:
        col = np.clip(col, 0, 1)
        rgba = np.concatenate([col, alpha[..., None]], -1)
        return Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")
    return prop_tile(name, rng)


def prop_tile(name, rng):
    s = TILE
    img = Image.new("RGBA", (s, s))
    d = ImageDraw.Draw(img)
    if name in ("tall_grass", "crimson_roots"):
        cols = ["#7f9a38", "#a5b24a", "#c9c26a", "#6f8a33"] if name == "tall_grass" else ["#b82030", "#d83a40", "#8a1420"]
        for _ in range(9):
            x0 = int(rng.integers(3, s - 3))
            h = int(rng.integers(12, 28))
            c = tuple(int(v * 255) for v in hexc(rng.choice(cols)))
            d.line([(x0, s - 1), (x0 + int(rng.integers(-4, 5)), s - h)], fill=c + (255,), width=2)
    elif name == "mushroom":
        d.rectangle([13, 18, 18, 31], fill=(236, 226, 204, 255))
        d.pieslice([5, 6, 26, 26], 180, 360, fill=(196, 55, 44, 255))
        for px, py in ((10, 11), (17, 9), (21, 13)):
            d.ellipse([px - 2, py - 2, px + 2, py + 2], fill=(250, 240, 220, 255))
    elif name in ("flower_red", "flower_yellow"):
        petal = (210, 60, 48) if name == "flower_red" else (240, 196, 60)
        d.line([(16, 31), (16, 14)], fill=(90, 130, 50, 255), width=2)
        d.ellipse([10, 16, 15, 20], fill=(100, 140, 56, 255))
        for a in range(5):
            ang = a / 5 * math.tau
            cx, cy = 16 + math.cos(ang) * 5, 10 + math.sin(ang) * 5
            d.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=petal + (255,))
        d.ellipse([13, 7, 19, 13], fill=(250, 220, 120, 255))
    elif name == "end_rod":
        d.rectangle([14, 4, 17, 31], fill=(250, 246, 236, 255))
        d.rectangle([11, 28, 20, 31], fill=(200, 190, 210, 255))
    elif name == "glow_berries":
        d.line([(16, 0), (15, 31)], fill=(70, 110, 40, 255), width=2)
        for py in (8, 16, 24):
            d.ellipse([16, py, 22, py + 6], fill=(255, 170, 60, 255))
            d.ellipse([18, py + 1, 20, py + 3], fill=(255, 230, 160, 255))
    elif name == "torch":
        d.rectangle([14, 12, 17, 31], fill=(110, 76, 44, 255))
        d.ellipse([12, 4, 19, 14], fill=(255, 170, 60, 255))
        d.ellipse([14, 6, 17, 11], fill=(255, 240, 180, 255))
    elif name == "amethyst_bud":
        for x0, h in ((9, 18), (16, 26), (22, 14)):
            d.polygon([(x0 - 3, 31), (x0, 31 - h), (x0 + 3, 31)], fill=(170, 120, 240, 255))
            d.line([(x0, 31 - h), (x0, 31)], fill=(230, 200, 255, 255), width=1)
    return img


def atlas():
    rng = np.random.default_rng(11)
    cols = 8
    img = Image.new("RGBA", (TILE * cols, TILE * math.ceil(len(TILES) / cols)))
    for i, name in enumerate(TILES):
        img.paste(tile(name, rng), ((i % cols) * TILE, (i // cols) * TILE))
    return img, cols


# ---------- Inseln ----------

def island(rng, radius, theme, main_island, tree_count):
    t = THEMES[theme]
    blocks, props = {}, []
    r_noise = [rng.uniform(-1.2, 1.2) for _ in range(12)]

    def edge(theta):
        k = theta / math.tau * 12
        i = int(k) % 12
        f = k - int(k)
        return radius + r_noise[i] * (1 - f) + r_noise[(i + 1) % 12] * f

    rad = int(radius + 3)
    top = {}
    for x in range(-rad, rad + 1):
        for z in range(-rad, rad + 1):
            r = math.hypot(x, z)
            e = edge(math.atan2(z, x) % math.tau)
            if r > e:
                continue
            h = int(round(max(0, 1.6 - math.hypot(x + 3, z + 3) / 3))) if main_island else 0
            depth = int((e - r) * 0.85 + rng.uniform(0, 1.4)) + 1
            if r < 2.0:
                depth += int(1 + rng.uniform(0, 2.5))
            top[(x, z)] = h
            for y in range(-depth, h + 1):
                if y == h:
                    kind = t["path"] if main_island and x == 1 and z >= 0 else t["top"]
                elif y >= h - 2:
                    kind = t["sub"]
                elif y < -depth + 2:
                    kind = t["deeper"]
                else:
                    kind = t["accent"] if rng.random() < 0.1 else t["deep"]
                blocks[(x, y, z)] = kind
            if theme == "cave" and rng.random() < 0.25:
                for k in range(1, int(rng.integers(2, 5))):  # dripstone hanging below the island
                    blocks[(x, -depth - k, z)] = "dripstone"

    def free(x, z):
        return (x, top[(x, z)] + 1, z) not in blocks

    def back_half(x, z):
        # the menu camera looks from the front right: keep tall things in the back half so you stay visible
        return 0.616 * x + 0.788 * z < -1.5

    spots = list(top)
    rng.shuffle(spots)
    placed = []
    for x, z in spots:
        if len(placed) >= tree_count:
            break
        if math.hypot(x, z) > radius - 2.5 or (main_island and not back_half(x, z)):
            continue
        if any(math.hypot(x - px, z - pz) < 4.5 for px, pz in placed):
            continue
        placed.append((x, z))
        grow(blocks, rng, t["trees"], x, top[(x, z)] + 1, z)

    if theme == "nether" and main_island:  # a little lava pool
        for (x, z), h in top.items():
            if math.hypot(x - 3, z + 1) < 1.7 and free(x, z):
                blocks[(x, h, z)] = "lava"
    if theme == "end" and main_island:  # obsidian pillar with a glowing crystal
        px, pz = next(((x, z) for x, z in sorted(top, key=lambda p: (p[0] - 3) ** 2 + (p[1] + 4) ** 2) if free(x, z)), (3, -4))
        for y in range(top[(px, pz)] + 1, top[(px, pz)] + 6):
            for dx in (0, 1):
                for dz in (0, 1):
                    blocks[(px + dx, y, pz + dz)] = "obsidian"
        cy = top[(px, pz)] + 7
        for dx, dy, dz in ((0, 0, 0), (1, 0, 0), (0, 0, 1), (1, 0, 1), (0, 1, 0), (1, 1, 1)):
            blocks[(px + dx, cy + dy, pz + dz)] = "crystal"
    if theme == "cave":  # glowing moss patches
        for (x, z), h in top.items():
            if blocks.get((x, h, z)) == "moss_block" and rng.random() < 0.18:
                blocks[(x, h, z)] = "glow_moss"

    for (x, z), h in top.items():
        if not free(x, z) or blocks.get((x, h, z)) in ("lava", t["path"]):
            continue
        if main_island and abs(x - 1) <= 2 and 0 <= z <= 4:
            continue  # room for you and your friends
        roll = rng.random()
        acc = 0
        for prop, chance in t["props"]:
            acc += chance
            if roll < acc:
                props.append([x, h + 1, z, prop])
                break
    if theme == "cave" and main_island:
        for x, z in ((3, 3), (-2, 4)):
            if (x, z) in top and free(x, z):
                props.append([x, top[(x, z)] + 1, z, "torch"])
    return blocks, props, top


def grow(blocks, rng, kind, x, base, z):
    def put(p, block, replace=False):
        if replace or p not in blocks:
            blocks[p] = block

    if kind == "autumn":
        height = int(rng.integers(4, 7))
        leaves = rng.choice(["leaves_orange", "leaves_red", "leaves_yellow"])
        for y in range(base, base + height):
            put((x, y, z), "log", True)
        cr = rng.uniform(2.1, 2.9)
        cy = base + height - 1
        for dx in range(-3, 4):
            for dy in range(-2, 4):
                for dz in range(-3, 4):
                    if math.sqrt(dx * dx + (dy * 1.25) ** 2 + dz * dz) <= cr + rng.uniform(-0.4, 0.3):
                        put((x + dx, cy + dy, z + dz), leaves)
    elif kind == "fungus":
        height = int(rng.integers(4, 7))
        for y in range(base, base + height):
            put((x, y, z), "crimson_stem", True)
        cy = base + height
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                for dy in range(-1, 2):
                    d = math.hypot(dx, dz)
                    if d <= 2.8 - dy * 0.9 and not (dy == -1 and d < 1.6):
                        put((x + dx, cy + dy, z + dz), "shroomlight" if dy == -1 and rng.random() < 0.3 else "wart")
    elif kind == "chorus":
        def branch(px, py, pz, length, depth):
            for k in range(length):
                put((px, py + k, pz), "chorus", True)
            top_y = py + length
            if depth < 2:
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if rng.random() < 0.45:
                        put((px + dx, top_y - 1, pz + dz), "chorus")
                        branch(px + dx, top_y, pz + dz, int(rng.integers(1, 3)), depth + 1)
            put((px, top_y, pz), "chorus_flower")
        branch(x, base, z, int(rng.integers(3, 6)), 0)
    elif kind == "crystal":
        height = int(rng.integers(2, 5))
        for k in range(height):
            put((x + (k // 2) * (1 if rng.random() < 0.5 else 0), base + k, z), "amethyst", True)
        for dx, dz in ((1, 0), (0, 1), (-1, 0)):
            if rng.random() < 0.6:
                put((x + dx, base, z + dz), "amethyst")


def friend_spots(blocks, top, player):
    px, _, pz = player
    out = []
    for x, z in sorted(top, key=lambda p: (p[0] - px) ** 2 + (p[1] - pz) ** 2):
        if (x, z) == (px, pz) or (x, top[(x, z)] + 1, z) in blocks:
            continue
        if 0.616 * x + 0.788 * z < 0 or any(abs(x - a) + abs(z - c) < 2 for a, _, c in out):
            continue
        out.append([x, top[(x, z)] + 1, z])
        if len(out) == 4:
            break
    return out


def main():
    img, cols = atlas()
    (MOD_OUT / "island").mkdir(parents=True, exist_ok=True)
    (MOD_OUT / "textures/island").mkdir(parents=True, exist_ok=True)
    UI_OUT.mkdir(parents=True, exist_ok=True)
    for old in list((MOD_OUT / "island").glob("*.json")) + list(UI_OUT.glob("*.json")):
        old.unlink()
    for i, scene in enumerate(SCENES):
        rng = np.random.default_rng(1611 + i)
        blocks, props, top = island(rng, 7.2, scene, True, 3)
        player = [1, top[(1, 2)] + 1, 2]
        props = [p for p in props if (p[0], p[2]) != (player[0], player[2])]
        fox = None
        if scene == "autumn":
            spot = next(((x, z) for x, z in sorted(top, key=lambda p: (p[0] + 4) ** 2 + (p[1] - 1) ** 2)
                         if (x, top[(x, z)] + 1, z) not in blocks), (-4, 1))
            props = [p for p in props if (p[0], p[2]) != spot]
            fox = [spot[0], top[spot] + 1, spot[1]]
        friends = friend_spots(blocks, top, player)
        props = [p for p in props if [p[0], p[1], p[2]] not in friends]
        small = []
        for offset, rad, n in (((-17, 4, -13), 2.8, 1), ((16, -3, -18), 3.4, 1), ((22, 8, -5), 2.0, 0), ((-10, -6, -23), 2.3, 1)):
            b, p, _ = island(rng, rad, scene, False, n)
            small.append({"offset": list(offset), "blocks": [[x, y, z, k] for (x, y, z), k in b.items()], "props": p})
        data = {
            "version": 2, "scene": scene,
            "atlas": {"tile": TILE, "columns": cols, "tiles": TILES, "glow": GLOW},
            "blocks_types": BLOCKS, "props_types": PROPS,
            "blocks": [[x, y, z, k] for (x, y, z), k in sorted(blocks.items())],
            "props": props, "player": player, "fox": fox, "friends": friends, "small_islands": small,
        }
        text = json.dumps(data, separators=(",", ":"))
        (MOD_OUT / f"island/{scene}.json").write_text(text, encoding="utf-8")
        shutil.copy(MOD_OUT / f"island/{scene}.json", UI_OUT / f"{scene}.json")
        print(f"{scene}: {len(blocks)} blocks, {len(props)} props, {len(friends)} friend spots, {len(text) // 1024} KB")
    img.save(MOD_OUT / "textures/island/atlas.png")
    (MOD_OUT / "textures/island/atlas.png.mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n', encoding="utf-8")
    shutil.copy(MOD_OUT / "textures/island/atlas.png", UI_OUT / "atlas.png")


if __name__ == "__main__":
    main()
