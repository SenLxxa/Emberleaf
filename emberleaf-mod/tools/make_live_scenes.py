"""Builds the live main-menu scenes (cave, nether, overworld, end) for the Emberleaf client.

Each scene is a small world of real Minecraft blocks seen from a fixed camera. The game draws them with its own
block models and textures (so the active texture packs show up in the menu); this tool only decides which block
goes where and bakes the light per face corner:
  ambient (sky openness x corner shadows), sun (with real shadows) and coloured block light from lava,
  torches, glowstone … (with occlusion). The game adds perspective, flicker, fog, bloom, light shafts and particles.

Writes assets/emberleaf/live/<scene>.bin + .json
Run: python tools/make_live_scenes.py   (numpy)

.bin (little endian): int32 count, then per block: int16 x, y, z, uint16 palette index, uint8 face mask (+x -x +y -y +z -z),
uint8 flags (1 = has a centre light), then 4 corners x 5 bytes per open face, then 5 bytes centre light.
Light bytes: ambient, sun (0..1 -> 0..255), block light r, g, b (0..2 -> 0..255).
"""
import json
import math
import struct
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
OUT_DATA = ROOT / "src/client/resources/assets/emberleaf/live"

# scene block kinds -> real block states
STATES = {
    "stone": "minecraft:stone", "deepslate": "minecraft:deepslate[axis=y]", "tuff": "minecraft:tuff", "andesite": "minecraft:andesite",
    "cobblestone": "minecraft:cobblestone", "mossy": "minecraft:mossy_cobblestone", "dirt": "minecraft:dirt", "coarse": "minecraft:coarse_dirt",
    "gravel": "minecraft:gravel", "sand": "minecraft:sand", "clay": "minecraft:clay", "grass": "minecraft:grass_block[snowy=false]",
    "path": "minecraft:dirt_path", "snow": "minecraft:snow_block", "ice": "minecraft:packed_ice",
    "netherrack": "minecraft:netherrack", "nether_bricks": "minecraft:nether_bricks", "soul_sand": "minecraft:soul_sand",
    "blackstone": "minecraft:blackstone", "basalt": "minecraft:basalt[axis=y]", "crimson": "minecraft:crimson_nylium",
    "crimson_stem": "minecraft:crimson_stem[axis=y]", "wart": "minecraft:nether_wart_block",
    "end_stone": "minecraft:end_stone", "end_bricks": "minecraft:end_stone_bricks", "obsidian": "minecraft:obsidian",
    "purpur": "minecraft:purpur_block", "bedrock": "minecraft:bedrock",
    "oak_log": "minecraft:oak_log[axis=y]", "birch_log": "minecraft:birch_log[axis=y]", "spruce_log": "minecraft:spruce_log[axis=y]",
    "oak_planks": "minecraft:oak_planks", "spruce_planks": "minecraft:spruce_planks", "stone_bricks": "minecraft:stone_bricks",
    "bricks": "minecraft:bricks", "water": "minecraft:water[level=0]",
    "oak_leaves": "minecraft:oak_leaves[persistent=true]", "birch_leaves": "minecraft:birch_leaves[persistent=true]",
    "coal": "minecraft:coal_ore", "iron": "minecraft:iron_ore", "copper": "minecraft:copper_ore", "gold": "minecraft:gold_ore",
    "redstone": "minecraft:redstone_ore[lit=false]", "lapis": "minecraft:lapis_ore", "diamond": "minecraft:diamond_ore",
    "emerald": "minecraft:emerald_ore", "deep_diamond": "minecraft:deepslate_diamond_ore", "deep_gold": "minecraft:deepslate_gold_ore",
    "deep_redstone": "minecraft:deepslate_redstone_ore[lit=false]", "nether_gold": "minecraft:nether_gold_ore",
    "quartz_ore": "minecraft:nether_quartz_ore",
    "lava": "minecraft:lava[level=0]", "glowstone": "minecraft:glowstone", "shroomlight": "minecraft:shroomlight",
    "magma": "minecraft:magma_block", "amethyst": "minecraft:amethyst_block", "chorus_flower": "minecraft:chorus_flower[age=2]",
}
# small things standing in the scene (not full blocks)
PROPS = {
    "dripstone": "minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]",
    "dripstone_up": "minecraft:pointed_dripstone[vertical_direction=up,thickness=tip]",
    "glow_berries": "minecraft:cave_vines[berries=true]",
    "torch": "minecraft:torch", "wall_torch": "minecraft:wall_torch[facing={}]",
    "amethyst_cluster": "minecraft:amethyst_cluster[facing={}]",
    "crimson_roots": "minecraft:crimson_roots", "crimson_fungus": "minecraft:crimson_fungus",
    "lantern": "minecraft:lantern[hanging=false]", "campfire": "minecraft:campfire[lit=true,facing=south]",
    "grass": "minecraft:short_grass", "poppy": "minecraft:poppy", "dandelion": "minecraft:dandelion", "cornflower": "minecraft:cornflower",
    "end_rod": "minecraft:end_rod[facing=up]",
}
EMIT = {"lava": (1.0, 0.42, 0.1), "glowstone": (1.0, 0.78, 0.4), "shroomlight": (1.0, 0.6, 0.25), "magma": (0.9, 0.3, 0.08),
        "crystal": (1.0, 0.5, 0.85), "chorus_flower": (0.7, 0.5, 0.95)}
GLOW_FACES = {"lava", "glowstone", "shroomlight", "magma"}  # drawn full bright
SOFT_GLOW = {"amethyst": 0.45, "chorus_flower": 0.55}  # partly self-lit
LEAVES = {"oak_leaves", "birch_leaves"}
FLUIDS = {"water", "lava"}
THIN = {"chorus", "chorus_flower"}  # not full blocks: every side the camera sees is drawn
SEE_THROUGH = LEAVES | FLUIDS | THIN  # neighbours that do not hide a face
PROP_EMIT = {"torch": (1.0, 0.62, 0.3), "wall_torch": (1.0, 0.62, 0.3), "end_rod": (0.95, 0.85, 1.0), "glow_berries": (1.0, 0.6, 0.25),
             "campfire": (1.0, 0.55, 0.22), "lantern": (1.0, 0.66, 0.32), "amethyst_cluster": (0.65, 0.4, 0.95)}
PROP_GLOW = {"torch", "wall_torch", "end_rod", "glow_berries", "campfire", "lantern"}

DIRS = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
SHADE = [0.8, 0.8, 1.0, 0.55, 0.9, 0.7]
# corners in counter-clockwise order seen from outside (the client uses the same order)
CORNERS = [
    [(1, 0, 1), (1, 0, 0), (1, 1, 0), (1, 1, 1)],
    [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)],
    [(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)],
    [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)],
    [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)],
    [(1, 0, 0), (0, 0, 0), (0, 1, 0), (1, 1, 0)],
]
AO = [1.0, 0.78, 0.62, 0.48]
FACING = {(1, 0, 0): "east", (-1, 0, 0): "west", (0, 1, 0): "up", (0, 0, 1): "south", (0, 0, -1): "north"}


def prop_base(kind):
    return kind.split(":", 1)[0]


def prop_state(kind):
    base, _, arg = kind.partition(":")
    return PROPS[base].format(arg) if arg else PROPS[base]


# ---------- Rauschen ----------

def noise3(shape, cell, seed):
    rng = np.random.default_rng(seed)
    gs = [s // cell + 2 for s in shape]
    g = rng.random(gs).astype(np.float32)
    idx = [np.arange(s) / cell for s in shape]
    i0 = [np.floor(a).astype(int) for a in idx]
    f = [a - np.floor(a) for a in idx]
    f = [x * x * (3 - 2 * x) for x in f]
    X, Y, Z = np.meshgrid(i0[0], i0[1], i0[2], indexing="ij")
    FX, FY, FZ = np.meshgrid(f[0], f[1], f[2], indexing="ij")

    def at(dx, dy, dz):
        return g[X + dx, Y + dy, Z + dz]

    c00 = at(0, 0, 0) * (1 - FX) + at(1, 0, 0) * FX
    c10 = at(0, 1, 0) * (1 - FX) + at(1, 1, 0) * FX
    c01 = at(0, 0, 1) * (1 - FX) + at(1, 0, 1) * FX
    c11 = at(0, 1, 1) * (1 - FX) + at(1, 1, 1) * FX
    c0 = c00 * (1 - FY) + c10 * FY
    c1 = c01 * (1 - FY) + c11 * FY
    return c0 * (1 - FZ) + c1 * FZ


_TABLES = {}


def noise2(x, z, scale, seed):
    """Cheap smooth 2D value noise at arbitrary float coordinates."""
    seed = abs(int(seed))
    if seed not in _TABLES:
        _TABLES[seed] = np.random.default_rng(seed).random((64, 64)).astype(np.float32)
    table = _TABLES[seed]
    xs, zs = np.asarray(x, np.float32) / scale, np.asarray(z, np.float32) / scale
    x0, z0 = np.floor(xs).astype(int), np.floor(zs).astype(int)
    fx, fz = xs - x0, zs - z0
    fx, fz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)

    def t(a, b):
        return table[a % 64, b % 64]

    return (t(x0, z0) * (1 - fx) + t(x0 + 1, z0) * fx) * (1 - fz) + (t(x0, z0 + 1) * (1 - fx) + t(x0 + 1, z0 + 1) * fx) * fz


# ---------- Szene ----------

class Scene:
    def __init__(self, name, lo, hi):
        self.name = name
        self.lo = np.array(lo)
        self.hi = np.array(hi)
        self.blocks = {}
        self.props = []  # (x, y, z, kind, hanging)
        self.lights = []  # (x, y, z, r, g, b, radius, strength)
        self.emitters = []
        self.figures = []
        self.crystals = []  # end crystals: x, y, z of the model's foot
        self.meta = {}
        self.sun = None
        self.ambient_open = True
        self.tints = {}

    def set(self, x, y, z, kind):
        if kind is None:
            self.blocks.pop((x, y, z), None)
        else:
            self.blocks[(x, y, z)] = kind

    def get(self, x, y, z):
        return self.blocks.get((x, y, z))

    def solid(self, x, y, z):
        k = self.blocks.get((x, y, z))
        return k is not None and k != "water"

    def floor(self, x, z):
        """Highest solid block below the first open cell, scanning up from the bottom (cave floors)."""
        for y in range(int(self.lo[1]) + 1, int(self.hi[1])):
            if not self.solid(x, y, z):
                return y - 1 if self.solid(x, y - 1, z) else None
        return None

    def top(self, x, z, y_from=60, y_to=-20):
        for y in range(y_from, y_to, -1):
            if self.solid(x, y, z):
                return y
        return None


def lava_lights(s, every=3, radius=12.0, strength=1.6):
    seen = set()
    for (x, y, z), k in s.blocks.items():
        if k == "lava" and s.get(x, y + 1, z) is None:
            key = (x // every, z // every)
            if key in seen:
                continue
            seen.add(key)
            s.lights.append((x + 0.5, y + 1.3, z + 0.5, *EMIT["lava"], radius, strength))


# ---------- Höhle ----------

def build_cave():
    s = Scene("cave", (-24, -4, -66), (24, 26, 10))
    nx, ny, nz = s.hi - s.lo
    n = noise3((nx, ny, nz), 6, 11)
    n2 = noise3((nx, ny, nz), 3, 12)
    for ix in range(nx):
        x = ix + s.lo[0]
        for iz in range(nz):
            z = iz + s.lo[2]
            xc = 3.5 * math.sin(z * 0.07)
            yc = 8 + 1.5 * math.sin(z * 0.05 + 1)
            rx = 11 + 3 * math.sin(z * 0.11 + 2)
            ry = 8.5 + 2 * math.sin(z * 0.09)
            floor = yc - ry * 0.55 + (noise2(x, z, 5, 3) - 0.5) * 3
            for iy in range(ny):
                y = iy + s.lo[1]
                e = ((x - xc) / rx) ** 2 + ((y - yc) / ry) ** 2 + (n[ix, iy, iz] - 0.5) * 0.7 + (n2[ix, iy, iz] - 0.5) * 0.25
                hole = abs(x - (-2)) <= 2.5 and abs(z + 34) <= 2.5 and y > yc  # opening in the roof with daylight
                open_ = (e < 1 and y > floor) or hole
                if not open_:
                    kind = "deepslate" if y < 2 else "stone"
                    if n2[ix, iy, iz] > 0.72:
                        kind = "tuff"
                    if n[ix, iy, iz] > 0.74 and y >= 2:
                        kind = "andesite"
                    s.set(int(x), int(y), int(z), kind)
    rng = np.random.default_rng(4)
    # lava river and pools on the floor
    for z in range(int(s.lo[2]) + 2, -5):
        rc = 3.5 * math.sin(z * 0.07) + 2.4 * math.sin(z * 0.21)
        width = 1.3 + (1.8 if -24 < z < -16 or -50 < z < -42 else 0) + 0.6 * math.sin(z * 0.3)
        for x in range(int(rc - width - 1), int(rc + width + 2)):
            if abs(x - rc) > width:
                continue
            y = s.floor(x, z)
            if y is not None and s.get(x, y + 1, z) is None:
                s.set(x, y, z, "lava")
                s.set(x, y - 1, z, "magma" if rng.random() < 0.3 else "deepslate")
    # ores on the walls, an amethyst pocket on the right
    for (x, y, z), k in list(s.blocks.items()):
        if k not in ("stone", "deepslate", "tuff", "andesite"):
            continue
        exposed = any(s.get(x + dx, y + dy, z + dz) is None for dx, dy, dz in DIRS)
        if not exposed:
            continue
        if math.dist((x, y, z), (11, 8, -17)) < 4.2:
            s.set(x, y, z, "amethyst")
            continue
        r = rng.random()
        deep = k == "deepslate"
        if r < 0.03:
            s.set(x, y, z, "coal")
        elif r < 0.05:
            s.set(x, y, z, "iron")
        elif r < 0.06:
            s.set(x, y, z, "copper")
        elif r < 0.066:
            s.set(x, y, z, "deep_gold" if deep else "gold")
        elif r < 0.072:
            s.set(x, y, z, "deep_redstone" if deep else "redstone")
        elif r < 0.076:
            s.set(x, y, z, "deep_diamond" if deep else "diamond")
        elif r < 0.079:
            s.set(x, y, z, "lapis")
    for (x, y, z), k in list(s.blocks.items()):
        if k == "amethyst":
            for dx, dy, dz in DIRS:
                if s.get(x + dx, y + dy, z + dz) is None and dy >= 0 and rng.random() < 0.45:
                    s.props.append((x + dx, y + dy, z + dz, "amethyst_cluster:" + FACING[(dx, dy, dz)], False))
    s.lights.append((9.5, 8.5, -16.5, 0.6, 0.35, 0.95, 9, 1.1))
    # dripstone from the ceiling, stalagmites, glow berries near the roof opening
    for (x, y, z), k in list(s.blocks.items()):
        if k in ("stone", "tuff", "andesite") and s.get(x, y - 1, z) is None and s.get(x, y - 2, z) is None:
            r = rng.random()
            if r < 0.09:
                s.props.append((x, y - 1, z, "dripstone", True))
            elif r < 0.11 and abs(z + 34) < 8:
                s.props.append((x, y - 1, z, "glow_berries", True))
        if k in ("stone", "deepslate", "tuff") and s.get(x, y + 1, z) is None and rng.random() < 0.025:
            s.props.append((x, y + 1, z, "dripstone_up", False))
    # torches on both walls, every few blocks
    for z in range(2, -58, -7):
        for side in (-1, 1):
            for step in range(4, 20):
                x = side * step
                y0 = s.floor(x - side, z)
                if y0 is None:
                    continue
                if s.solid(x, y0 + 3, z) and not s.solid(x - side, y0 + 3, z):
                    s.props.append((x - side, y0 + 3, z, "wall_torch:" + ("west" if side == 1 else "east"), False))
                    break
    lava_lights(s)
    s.lights.append((-2, 22, -34, 0.75, 0.85, 1.0, 22, 1.0))  # daylight through the roof
    for (x, y, z, kind, hang) in s.props:
        base = prop_base(kind)
        if base in PROP_EMIT and base != "amethyst_cluster":
            s.lights.append((x + 0.5, y + 0.6, z + 0.5, *PROP_EMIT[base], 10, 1.1))
    s.ambient_open = False
    floor_y = s.floor(5, -1)
    s.figures = [[5, floor_y + 1, -1], [7, (s.floor(7, -4) or floor_y) + 1, -4], [3, (s.floor(3, -5) or floor_y) + 1, -5]]
    s.meta = {"camera": {"start": [-1, 15, 9], "end": [1.0, 10.5, 6.0], "target": [-1.5, 5.0, -32], "fov": 72},
              "ambient": [0.17, 0.14, 0.15], "fog": [0.035, 0.025, 0.03], "fogStart": 28, "fogEnd": 72,
              "rays": [{"from": [-2, 24, -34], "to": [-2, 4, -34], "width": 5, "color": [0.75, 0.85, 1.0]}],
              "emitters": [{"type": "embers", "area": [-14, 0, -60, 14, 6, -4], "count": 70},
                           {"type": "dust", "area": [-10, 2, -30, 10, 16, 6], "count": 60},
                           {"type": "drip", "area": [-10, 12, -40, 10, 18, -2], "count": 12}]}
    return s


# ---------- Nether ----------

def build_nether():
    s = Scene("nether", (-36, -3, -82), (36, 34, 10))
    rng = np.random.default_rng(21)
    for x in range(-34, 35):
        for z in range(-80, 9):
            # lava sea
            s.set(x, -1, z, "netherrack")
            s.set(x, 0, z, "lava")
            left = -14 + noise2(x, z, 9, 1) * 6
            right = 12 + noise2(x, z, 9, 2) * 6
            hl = int(10 + noise2(x, z, 7, 3) * 9) if x < left else 0
            hr = int(9 + noise2(x, z, 6, 4) * 6) if x > right else 0
            h = max(hl, hr)
            for y in range(0, h + 1 if h > 0 else 0):
                s.set(x, y, z, "netherrack" if rng.random() > 0.04 else ("nether_gold" if rng.random() < 0.5 else "quartz_ore"))
            if hr and h > 0:
                s.set(x, h, z, "crimson")
            if hl and h > 0 and noise2(x, z, 5, 9) > 0.62:
                s.set(x, h, z, "soul_sand")
    # basalt pillars in the lava
    for (cx, cz, hh) in ((-6, -46, 14), (7, -58, 18), (-2, -66, 11)):
        for y in range(0, hh):
            for dx in (0, 1):
                for dz in (0, 1):
                    if not (y > hh - 3 and (dx + dz) == 2):
                        s.set(cx + dx, y, cz + dz, "basalt")
    # fortress bridge
    for x in range(-15, 15):
        for z in range(-36, -32):
            s.set(x, 11, z, "nether_bricks")
        if x % 2 == 0:
            s.set(x, 12, -36, "nether_bricks")
            s.set(x, 12, -33, "nether_bricks")
    for px_ in (-8, 3):
        for y in range(1, 11):
            for dx in (0, 1):
                s.set(px_ + dx, y, -35, "nether_bricks")
                s.set(px_ + dx, y, -34, "nether_bricks")
    # crimson forest on the right cliff
    for _ in range(9):
        x, z = int(rng.integers(15, 30)), int(rng.integers(-60, -6))
        y = s.top(x, z, 30, 0)
        if y is None or s.get(x, y, z) != "crimson":
            continue
        h = int(rng.integers(4, 7))
        for k in range(1, h + 1):
            s.set(x, y + k, z, "crimson_stem")
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for dy in range(0, 2):
                    if abs(dx) + abs(dz) + dy < 4 and s.get(x + dx, y + h + dy, z + dz) is None:
                        s.set(x + dx, y + h + dy, z + dz, "shroomlight" if rng.random() < 0.08 else "wart")
    for (x, y, z), k in list(s.blocks.items()):
        if k == "crimson" and s.get(x, y + 1, z) is None and rng.random() < 0.18:
            s.props.append((x, y + 1, z, "crimson_roots" if rng.random() < 0.7 else "crimson_fungus", False))
    # ceiling with glowstone at the back
    for x in range(-34, 35):
        for z in range(-80, -48):
            h = int(28 + noise2(x, z, 6, 7) * 5)
            for y in range(h, 34):
                s.set(x, y, z, "netherrack")
            if noise2(x, z, 4, 8) > 0.66:
                for d in range(1, 3):
                    s.set(x, h - d, z, "glowstone")
    # a ledge to stand on near the camera
    for x in range(5, 12):
        for z in range(-10, -3):
            for y in range(0, 11):
                if s.get(x, y, z) in (None, "lava"):
                    s.set(x, y, z, "netherrack")
            s.set(x, 11, z, "crimson")
            s.set(x, 12, z, None)
    for (x, y, z), k in s.blocks.items():
        if k == "glowstone" and s.get(x, y - 1, z) is None and (x + z) % 4 == 0:
            s.lights.append((x + 0.5, y - 0.5, z + 0.5, *EMIT["glowstone"], 9, 1.0))
        if k == "shroomlight":
            s.lights.append((x + 0.5, y + 0.5, z + 0.5, *EMIT["shroomlight"], 7, 0.9))
    lava_lights(s, every=9, radius=14, strength=1.1)
    s.ambient_open = False
    s.figures = [[7, 12, -6], [9, 12, -8], [6, 12, -9]]
    s.meta = {"camera": {"start": [-3, 21, 12], "end": [-1, 15.5, 7], "target": [3, 4, -34], "fov": 70},
              "ambient": [0.42, 0.19, 0.13], "fog": [0.30, 0.08, 0.05], "fogStart": 45, "fogEnd": 115,
              "rays": [],
              "emitters": [{"type": "embers", "area": [-30, 0, -78, 30, 4, 0], "count": 90},
                           {"type": "ash", "area": [-16, 4, -40, 16, 28, 8], "count": 70}]}
    return s


# ---------- Oberwelt ----------

def build_overworld():
    s = Scene("overworld", (-52, -4, -100), (52, 40, 14))
    rng = np.random.default_rng(31)
    river = lambda z: 5 * math.sin(z * 0.045) + 2  # noqa: E731
    heights = {}
    for x in range(-50, 51):
        for z in range(-98, 13):
            d = abs(x - river(z))
            h = 3 + noise2(x, z, 11, 1) * 4 + min(d, 14) * 0.25
            if z < -60:
                h += (-60 - z) * 0.55 * (0.6 + noise2(x, z, 9, 2) * 0.8)  # mountains at the back
            if abs(x) > 30:
                h += (abs(x) - 30) * 0.45
            h = int(h)
            if d < 3.2 and z > -70:
                h = min(h, 1)
            elif d < 6.5 and z > -70:
                h = min(h, 2)  # flat beach instead of steps
            heights[(x, z)] = h
            for y in range(-3, h + 1):
                kind = "stone" if y < h - 3 else "dirt"
                if y == h:
                    kind = "grass"
                    if h > 22:
                        kind = "snow"
                    elif h > 16:
                        kind = "stone"
                    elif d < 4.2 and z > -70:
                        kind = "sand" if rng.random() > 0.3 else "gravel"
                s.set(x, y, z, kind)
            if d < 3.2 and z > -70:
                for y in range(h + 1, 3):
                    s.set(x, y, z, "water")
    cabin = (14, -16)
    hc = heights[cabin]
    # cozy cabin
    for x in range(cabin[0] - 3, cabin[0] + 4):
        for z in range(cabin[1] - 3, cabin[1] + 3):
            for y in range(hc, hc + 1):
                s.set(x, y, z, "cobblestone")
            for y in range(hc + 1, hc + 5):
                edge_x = x in (cabin[0] - 3, cabin[0] + 3)
                edge_z = z in (cabin[1] - 3, cabin[1] + 2)
                if edge_x and edge_z:
                    s.set(x, y, z, "spruce_log")
                elif edge_x or edge_z:
                    s.set(x, y, z, "spruce_planks")
            for k in range(0, 4):
                if cabin[0] - 4 + k <= x <= cabin[0] + 4 - k:
                    s.set(x, hc + 5 + k, z, "bricks" if k < 3 else "spruce_planks")
    for y in (hc + 1, hc + 2):
        s.set(cabin[0], y, cabin[1] + 2, None)  # door
    lantern = (cabin[0] + 1, cabin[1] + 3)
    s.props.append((lantern[0], heights[lantern] + 1, lantern[1], "lantern", False))  # on the ground next to the door
    s.set(cabin[0] - 1, hc + 3, cabin[1] + 2, "glowstone")  # warm window
    camp = (8, -9)
    s.props.append((camp[0], heights[camp] + 1, camp[1], "campfire", False))
    # trees
    planted = 0
    while planted < 26:
        x, z = int(rng.integers(-40, 41)), int(rng.integers(-62, 4))
        h = heights.get((x, z))
        if h is None or h < 2 or abs(x - river(z)) < 5 or math.dist((x, z), cabin) < 7 or math.dist((x, z), camp) < 4:
            continue
        if math.dist((x, z), (2, 4)) < 7 or math.dist((x, z), lantern) < 3:
            continue
        birch = rng.random() < 0.35
        log, leaf = ("birch_log", "birch_leaves") if birch else ("oak_log", "oak_leaves")
        th = int(rng.integers(4, 7))
        for k in range(1, th + 1):
            s.set(x, h + k, z, log)
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for dy in range(-1, 3):
                    r = abs(dx) + abs(dz) + max(0, dy) * 1.2
                    if r <= 3.3 - (0.8 if dy == 2 else 0) and s.get(x + dx, h + th + dy, z + dz) is None:
                        s.set(x + dx, h + th + dy, z + dz, leaf)
        planted += 1
    for (x, z), h in heights.items():
        if s.get(x, h, z) == "grass" and s.get(x, h + 1, z) is None and (x, z) != lantern:
            r = rng.random()
            if r < 0.16:
                s.props.append((x, h + 1, z, "grass", False))
            elif r < 0.2:
                s.props.append((x, h + 1, z, ["poppy", "dandelion", "cornflower"][int(rng.integers(0, 3))], False))
    s.sun = (-0.62, 0.36, -0.7)
    for (x, y, z, kind, hang) in s.props:
        base = prop_base(kind)
        if base in PROP_EMIT:
            s.lights.append((x + 0.5, y + 0.6, z + 0.5, *PROP_EMIT[base], 11, 1.2))
    s.lights.append((cabin[0] - 1.5, hc + 3.5, cabin[1] + 2.5, 1.0, 0.7, 0.35, 7, 0.9))
    s.figures = [[9, heights[(9, -4)] + 1, -4], [11, heights[(11, -6)] + 1, -6], [8, heights[(8, -7)] + 1, -7]]
    s.tints = {"grass": "#79c05a", "foliage": "#59ae30", "water": "#3f76e4"}
    s.meta = {"camera": {"start": [-4, 19, 16], "end": [-2, 11.5, 10], "target": [5, 4, -34], "fov": 68},
              "ambient": [0.42, 0.46, 0.56], "fog": [0.82, 0.78, 0.8], "fogStart": 50, "fogEnd": 118, "sky": True,
              "rays": [],
              "emitters": [{"type": "fireflies", "area": [-20, 2, -40, 24, 10, 6], "count": 45},
                           {"type": "leaves", "area": [-30, 6, -50, 30, 16, 6], "count": 26},
                           {"type": "smoke", "at": [camp[0] + 0.5, heights[camp] + 1.5, camp[1] + 0.5], "count": 18}]}
    return s


# ---------- End ----------

def build_end():
    s = Scene("end", (-48, -30, -96), (48, 40, 20))
    rng = np.random.default_rng(41)

    def island(cx, cy, cz, r, depth):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            for z in range(int(cz - r - 2), int(cz + r + 3)):
                d = math.hypot(x - cx, z - cz) / r + (noise2(x, z, 4, int(cx * 7 + cz)) - 0.5) * 0.3
                if d > 1:
                    continue
                top = int(cy + (1 - d) * 1.5 + noise2(x, z, 5, 3) * 1.2)
                bottom = int(cy - (1 - d) ** 0.7 * depth)
                for y in range(bottom, top + 1):
                    s.set(x, y, z, "end_stone")

    island(0, 0, -26, 17, 14)
    for (cx, cy, cz, r) in ((-30, 8, -40, 6), (28, -4, -50, 7), (34, 12, -82, 9), (-22, -10, -70, 5), (16, 18, -64, 4)):
        island(cx, cy, cz, r, r * 1.2)
    # obsidian pillars, each with an end crystal on bedrock (like in the real End)
    for (cx, cz, hh) in ((-8, -34, 16), (7, -38, 21), (-1, -48, 13), (12, -26, 11)):
        base = s.top(cx, cz, 10, -20) or 0
        for y in range(base, base + hh):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    s.set(cx + dx, y, cz + dz, "obsidian")
        s.set(cx, base + hh, cz, "bedrock")
        s.crystals.append([cx + 0.5, base + hh + 1, cz + 0.5])
        s.lights.append((cx + 0.5, base + hh + 1.5, cz + 0.5, *EMIT["crystal"], 13, 1.2))
    # chorus plants
    for _ in range(9):
        x, z = int(rng.integers(-14, 15)), int(rng.integers(-40, -12))
        y = s.top(x, z, 10, -20)
        if y is None:
            continue
        h = int(rng.integers(3, 6))
        for k in range(1, h + 1):
            s.set(x, y + k, z, "chorus")
        side = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
        s.set(x + side[0], y + h - 1, z + side[1], "chorus")
        s.set(x + side[0], y + h, z + side[1], "chorus_flower")
        s.set(x, y + h + 1, z, "chorus_flower")
    # purpur tower on the far island
    for y in range(13, 30):
        for x in range(31, 38):
            for z in range(-85, -78):
                edge = x in (31, 37) or z in (-85, -79)
                if edge and not (y % 5 == 2 and (x == 34 or z == -82)):
                    s.set(x, y, z, "purpur")
    for x in range(30, 39):
        for z in range(-86, -77):
            s.set(x, 30, z, "end_bricks")
    # end rods along the path to the pillars
    for k, (x, z) in enumerate(((-3, -10), (2, -12), (-4, -18), (3, -20), (-2, -24))):
        y = s.top(x, z, 10, -20)
        if y is not None:
            s.props.append((x, y + 1, z, "end_rod", False))
            s.lights.append((x + 0.5, y + 1.6, z + 0.5, *PROP_EMIT["end_rod"], 8, 0.9))
    for (x, y, z), k in s.blocks.items():
        if k == "chorus_flower" and rng.random() < 0.5:
            s.lights.append((x + 0.5, y + 0.5, z + 0.5, *EMIT["chorus_flower"], 6, 0.7))
    s.ambient_open = True
    top = s.top(-2, -12, 10, -20) or 1
    s.figures = [[-2, top + 1, -12], [0, (s.top(0, -15, 10, -20) or top) + 1, -15], [-4, (s.top(-4, -15, 10, -20) or top) + 1, -15]]
    s.meta = {"camera": {"start": [-8, 15, 18], "end": [-5, 9.5, 8], "target": [2, 3, -30], "fov": 70},
              "ambient": [0.30, 0.26, 0.42], "fog": [0.08, 0.05, 0.13], "fogStart": 45, "fogEnd": 115,
              "rays": [],
              "emitters": [{"type": "sparkles", "area": [-24, -4, -60, 24, 22, 8], "count": 70}]}
    return s


# ---------- Blöcke auswählen und Licht backen ----------

def block_state(s, x, y, z, k):
    if k == "chorus":  # the plant connects to its neighbours (and to end stone below)
        def joins(dx, dy, dz, ground=False):
            nb = s.get(x + dx, y + dy, z + dz)
            return nb in ("chorus", "chorus_flower") or (ground and nb == "end_stone")
        sides = {"up": joins(0, 1, 0), "down": joins(0, -1, 0, True), "north": joins(0, 0, -1), "south": joins(0, 0, 1),
                 "east": joins(1, 0, 0), "west": joins(-1, 0, 0)}
        return "minecraft:chorus_plant[" + ",".join(f"{k2}={str(v).lower()}" for k2, v in sides.items()) + "]"
    return STATES[k]


def collect(s):
    """Blocks the camera can see (with their open faces) and the points whose light gets baked."""
    cam_points = [np.array(s.meta["camera"]["start"], np.float32), np.array(s.meta["camera"]["end"], np.float32)]
    entries = []  # x, y, z, state, face mask, centre sample (-1 = none), first face sample
    pos, nrm, ao, kinds = [], [], [], []

    def visible(center, normal):
        return any(np.dot(np.array(normal), c - center) > 0 for c in cam_points)

    for (x, y, z), k in sorted(s.blocks.items()):
        mask = 0
        first = len(pos)
        for f, (dx, dy, dz) in enumerate(DIRS):
            nb = s.get(x + dx, y + dy, z + dz)
            if k not in THIN:
                if nb is not None and nb not in SEE_THROUGH:
                    continue
                if (k in LEAVES and nb in LEAVES) or (k in FLUIDS and nb == k):
                    continue
            center = np.array([x + 0.5 + dx * 0.5, y + 0.5 + dy * 0.5, z + 0.5 + dz * 0.5], np.float32)
            if not visible(center, (dx, dy, dz)):
                continue
            mask |= 1 << f
            for (cx, cy, cz) in CORNERS[f]:
                sx, sy, sz = (1 if cx else -1), (1 if cy else -1), (1 if cz else -1)
                if dx:
                    occ = sum(s.solid(*p) for p in ((x + dx, y + sy, z), (x + dx, y, z + sz), (x + dx, y + sy, z + sz)))
                elif dy:
                    occ = sum(s.solid(*p) for p in ((x + sx, y + dy, z), (x, y + dy, z + sz), (x + sx, y + dy, z + sz)))
                else:
                    occ = sum(s.solid(*p) for p in ((x + sx, y, z + dz), (x, y + sy, z + dz), (x + sx, y + sy, z + dz)))
                pos.append((x + cx, y + cy, z + cz))
                nrm.append((dx, dy, dz))
                ao.append(AO[occ] * SHADE[f])
                kinds.append(1 if k in GLOW_FACES else 4 if k in SOFT_GLOW else 0)
        if mask == 0:
            continue
        center = -1
        if k in LEAVES or k in THIN:  # model parts inside the block (bushy leaves, thin plants) get the light of its middle
            center = len(pos)
            pos.append((x + 0.5, y + 0.5, z + 0.5))
            nrm.append((0, 1, 0))
            ao.append(0.9)
            kinds.append(4 if k in SOFT_GLOW else 3)
        entries.append((x, y, z, block_state(s, x, y, z, k), mask, center, first))
    for (x, y, z, kind, hanging) in s.props:
        center = len(pos)
        pos.append((x + 0.5, y + 0.45, z + 0.5))
        nrm.append((0, 1, 0))
        ao.append(1.0)
        kinds.append(1 if prop_base(kind) in PROP_GLOW else 3)
        entries.append((x, y, z, prop_state(kind), 0, center, -1))
    return entries, np.array(pos, np.float32), np.array(nrm, np.float32), np.array(ao, np.float32), np.array(kinds, np.int8)


def bake(s, pos, normals, ao, kinds):
    n = len(pos)
    lo = s.lo - 2
    shape = tuple((s.hi - s.lo + 4).tolist())
    occ = np.zeros(shape, bool)
    for (x, y, z), k in s.blocks.items():
        if k != "water":
            occ[x - lo[0], y - lo[1], z - lo[2]] = True

    def blocked(points):
        idx = np.floor(points).astype(int) - lo
        inside = np.all((idx >= 0) & (idx < np.array(shape)), -1)
        res = np.zeros(len(points), bool)
        ii = idx[inside]
        res[inside] = occ[ii[:, 0], ii[:, 1], ii[:, 2]]
        return res

    start = pos + normals * 0.35
    # ambient: how open is the sky above (for the overworld/end), constant dim light in closed scenes
    if s.ambient_open:
        openness = np.ones(n, np.float32)
        for step in range(1, 26):
            openness = np.where(blocked(start + np.array([0, step, 0], np.float32)), openness * 0.55, openness)
        amb = ao * (0.45 + 0.55 * openness)
    else:
        amb = ao.copy()
    sun = np.zeros(n, np.float32)
    if s.sun is not None:
        d = np.array(s.sun, np.float32)
        d /= np.linalg.norm(d)
        ndl = np.clip(normals @ d, 0, 1)
        ndl = np.where(kinds == 3, 0.8, ndl)
        vis = np.ones(n, np.float32)
        for step in range(1, 60):
            vis = np.where(blocked(start + d * step * 0.9), 0.0, vis)
        sun = ndl * vis * np.where(kinds == 0, ao, 1.0)
    block = np.zeros((n, 3), np.float32)
    for (lx, ly, lz, r, g, b, radius, strength) in s.lights:
        lp = np.array([lx, ly, lz], np.float32)
        delta = lp - start
        dist = np.linalg.norm(delta, axis=1)
        near = dist < radius
        if not near.any():
            continue
        idx = np.nonzero(near)[0]
        dd = delta[idx] / np.maximum(dist[idx, None], 1e-4)
        ndl = np.clip(np.sum(dd * normals[idx], 1) * 0.6 + 0.4, 0, 1)
        ndl = np.where(kinds[idx] == 3, 1.0, ndl)
        vis = np.ones(len(idx), np.float32)
        steps = 14
        for k in range(1, steps):
            t = k / steps
            p = start[idx] + delta[idx] * t
            hit = blocked(p) & (np.linalg.norm(lp - p, axis=1) > 0.9)
            vis = np.where(hit, vis * 0.15, vis)
        att = np.clip(1 - dist[idx] / radius, 0, 1) ** 1.6 * strength
        contrib = (att * ndl * vis * ao[idx])[:, None] * np.array([r, g, b], np.float32)
        block[idx] += contrib
    glow = kinds == 1
    block[glow] = 1.0
    amb[glow] = 0
    sun[glow] = 0
    soft = kinds == 4
    block[soft] = np.maximum(block[soft], 0.45)
    return amb, sun, block


def light_visibility(s):
    """1 if the light can be seen from the camera's resting point (for the bloom glow), else 0."""
    eye = np.array(s.meta["camera"]["end"], np.float32)
    out = []
    for light in s.lights:
        lp = np.array(light[:3], np.float32)
        d = lp - eye
        dist = float(np.linalg.norm(d))
        visible = 1
        for k in range(1, max(2, int(dist * 2))):
            p = eye + d * (k / max(2, int(dist * 2)))
            if np.linalg.norm(lp - p) < 1.0:
                break
            if s.solid(*[int(math.floor(c)) for c in p]):
                visible = 0
                break
        out.append([*light, visible])
    return out


def write(s, entries, amb, sun, block):
    light = np.stack([amb * 255, sun * 255, block[:, 0] * 127.5, block[:, 1] * 127.5, block[:, 2] * 127.5], 1)
    light = np.clip(np.round(light), 0, 255).astype(np.uint8)
    palette, index = [], {}
    out = bytearray(struct.pack("<i", len(entries)))
    for (x, y, z, state, mask, center, first) in entries:
        if state not in index:
            index[state] = len(palette)
            palette.append(state)
        out += struct.pack("<hhhHBB", x, y, z, index[state], mask, 1 if center >= 0 else 0)
        n = bin(mask).count("1") * 4
        if n:
            out += light[first:first + n].tobytes()
        if center >= 0:
            out += light[center].tobytes()
    OUT_DATA.mkdir(parents=True, exist_ok=True)
    (OUT_DATA / f"{s.name}.bin").write_bytes(bytes(out))
    lights = [[round(v, 2) for v in l] for l in light_visibility(s)]
    meta = {**s.meta, "format": 3, "palette": palette, "tints": s.tints, "blocks": len(entries), "figures": s.figures,
            "crystals": s.crystals, "lights": lights}
    (OUT_DATA / f"{s.name}.json").write_text(json.dumps(meta), "utf-8")
    return len(palette), len(out)


def main():
    for build in (build_cave, build_nether, build_overworld, build_end):
        s = build()
        entries, pos, nrm, ao, kinds = collect(s)
        amb, sun, block = bake(s, pos, nrm, ao, kinds)
        states, size = write(s, entries, amb, sun, block)
        print(f"{s.name}: {len(s.blocks)} blocks, {len(entries)} drawn, {states} block states, {len(s.lights)} lights, {size // 1024} KB")


if __name__ == "__main__":
    main()
