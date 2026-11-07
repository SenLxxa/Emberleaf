"""Emberleaf's own 64x art: seamless cozy block materials and one enchanted book per enchantment.

Everything here is drawn from code (noise, Voronoi cells, shapes), nothing is copied from Minecraft.
All block textures tile seamlessly because every noise is periodic (FFT synthesis / wrapped distances).
Arrays are float32 RGBA in 0..1, shape (64, 64, 4) or (frames*64, 64, 4) for animations.
"""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 64


# ---------- Grundlagen ----------

def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], np.float32)


def pnoise(rng, scale, size=S, aniso=(1.0, 1.0)):
    """Seamless noise: white noise low-passed in the frequency domain (FFT is periodic by nature)."""
    white = rng.standard_normal((size, size)).astype(np.float32)
    fy = np.fft.fftfreq(size)[:, None] * aniso[0]
    fx = np.fft.fftfreq(size)[None, :] * aniso[1]
    f = np.sqrt(fx * fx + fy * fy) * size
    sigma = max(scale, 0.5)  # roughly how many features fit across the tile
    spec = np.fft.fft2(white) * np.exp(-(f / sigma) ** 2)
    out = np.real(np.fft.ifft2(spec))
    out -= out.min()
    return out / max(out.max(), 1e-6)


def fbm(rng, octaves=((4, 1.0), (8, 0.5), (16, 0.25), (32, 0.12)), aniso=(1.0, 1.0)):
    out = sum(pnoise(rng, s, aniso=aniso) * a for s, a in octaves)
    out -= out.min()
    return out / max(out.max(), 1e-6)


def ramp(t, cols):
    cols = np.array([hexc(c) if isinstance(c, str) else c for c in cols], np.float32)
    t = np.clip(t, 0, 1)
    n = len(cols) - 1
    idx = np.minimum((t * n).astype(int), n - 1)
    k = (t * n - idx)[..., None]
    return cols[idx] + (cols[idx + 1] - cols[idx]) * k


def rgba(rgb, alpha=None):
    a = np.ones(rgb.shape[:2], np.float32) if alpha is None else alpha
    return np.concatenate([np.clip(rgb, 0, 1), a[..., None]], -1).astype(np.float32)


def grid(size=S):
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    return yy, xx


def voronoi(rng, n, size=S, jitter=1.0):
    """Wrapped Voronoi: distance to nearest/second nearest point and the cell id per pixel."""
    yy, xx = grid(size)
    pts = rng.random((n, 2)) * size
    best = np.full((size, size), 1e9, np.float32)
    second = np.full((size, size), 1e9, np.float32)
    ids = np.zeros((size, size), np.int32)
    for i, (px, py) in enumerate(pts):
        dx = np.abs(xx - px)
        dy = np.abs(yy - py)
        dx = np.minimum(dx, size - dx)
        dy = np.minimum(dy, size - dy)
        d = np.sqrt(dx * dx + dy * dy)
        closer = d < best
        second = np.where(closer, best, np.minimum(second, d))
        ids = np.where(closer, i, ids)
        best = np.where(closer, d, best)
    return best, second, ids


def shade(height, strength=1.0):
    """Light from the top left on a periodic height field: returns a brightness factor around 1."""
    gx = np.roll(height, -1, 1) - np.roll(height, 1, 1)
    gy = np.roll(height, -1, 0) - np.roll(height, 1, 0)
    return 1 + (-gx - gy) * strength


def soft(arr, passes=1):
    """Periodic 3x3 blur."""
    for _ in range(passes):
        arr = sum(np.roll(np.roll(arr, dy, 0), dx, 1) for dy in (-1, 0, 1) for dx in (-1, 0, 1)) / 9
    return arr


# ---------- Materialien ----------

def earth(seed, cols, pebbles=None, grain=0.35, pebble_n=14):
    rng = np.random.default_rng(seed)
    n = fbm(rng, ((3, 1.0), (6, 0.55), (12, 0.25), (24, 0.1)))
    fine = pnoise(rng, 26)
    col = ramp(n * (1 - grain * 0.7) + fine * grain * 0.7, cols)
    if pebbles:
        d, d2, ids = voronoi(rng, pebble_n)
        size = rng.random(pebble_n)[ids] * 2.0 + 1.8
        stone = d < size
        pcol = ramp(fine * 0.4 + rng.random(pebble_n)[ids] * 0.6, pebbles)
        lit = 0.9 + np.clip((size - d) / size, 0, 1) * 0.2
        col = np.where(stone[..., None], pcol * lit[..., None], col)
        col = np.where(((d >= size) & (d < size + 1))[..., None], col * 0.8, col)
    col *= shade(soft(n, 1), 1.3)[..., None]
    return rgba(col)


def stone(seed, cols, cracks=0.0, strata=0.0):
    rng = np.random.default_rng(seed)
    n = fbm(rng, ((2, 1.0), (5, 0.6), (10, 0.3), (20, 0.12)))
    fine = pnoise(rng, 24)
    col = ramp(n * 0.8 + fine * 0.2, cols)
    if strata:
        yy, _ = grid()
        col *= (1 + np.sin(yy / S * math.tau * 3 + n * 5) * strata)[..., None]
    if cracks:
        d, d2, _ = voronoi(rng, 7)
        edge = np.clip(1 - (d2 - d) / 1.6, 0, 1) * (pnoise(rng, 6) > 0.45)
        col *= (1 - edge * cracks)[..., None]
    col *= shade(soft(n, 1), 1.2)[..., None]
    return rgba(col)


def cobble(seed, cols, mortar, n_stones=11, moss=None):
    rng = np.random.default_rng(seed)
    d, d2, ids = voronoi(rng, n_stones)
    edge = d2 - d
    fine = pnoise(rng, 32)
    tone = rng.random(n_stones)[ids]
    height = np.clip(edge / 4.0, 0, 1) ** 0.6
    col = ramp(tone * 0.55 + fine * 0.3 + height * 0.15, cols)
    col *= shade(soft(height, 1), 1.6)[..., None]
    gap = edge < 1.1
    col = np.where(gap[..., None], ramp(fine, mortar), col)
    if moss:
        m = (fbm(rng) + (1 - height) * 0.35) > 0.62
        col = np.where(m[..., None], ramp(pnoise(rng, 30), moss), col)
    return rgba(col)


def bricks(seed, cols, mortar, rows=4, per_row=2, gap=2, chipped=0.0, moss=None, cracks=False):
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    rh = S // rows
    row = (yy // rh).astype(int)
    bw = S / per_row
    off = (row % 2) * bw / 2
    bx = ((xx + off) % S) // bw
    bid = row * 10 + bx.astype(int)
    tone = (np.sin(bid * 12.9898) * 43758.5453) % 1
    fine = pnoise(rng, 34)
    n = fbm(rng)
    lx = (xx + off) % bw
    ly = yy % rh
    inner = np.minimum(np.minimum(lx, bw - 1 - lx), np.minimum(ly, rh - 1 - ly))
    col = ramp(tone * 0.45 + fine * 0.35 + n * 0.2, cols)
    bevel = np.where(lx < gap + 1.5, 1.10, 1.0) * np.where(ly < gap + 1.5, 1.08, 1.0) \
        * np.where(lx > bw - gap - 2.5, 0.88, 1.0) * np.where(ly > rh - gap - 2.5, 0.86, 1.0)
    col *= bevel[..., None]
    if chipped:
        col *= (1 - (pnoise(rng, 20) > 1 - chipped) * 0.18)[..., None]
    if cracks:
        d, d2, _ = voronoi(rng, 5)
        col *= (1 - np.clip(1 - (d2 - d) / 1.2, 0, 1) * (n > 0.5) * 0.45)[..., None]
    col = np.where((inner < gap / 2)[..., None] | (lx < gap)[..., None] | (ly < gap)[..., None], ramp(fine, mortar), col)
    if moss:
        m = (fbm(rng) > 0.63) | ((ly < gap + 2) & (pnoise(rng, 16) > 0.55))
        col = np.where(m[..., None], ramp(pnoise(rng, 30), moss), col)
    return rgba(col)


def planks(seed, cols, boards=4, nails=True):
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    bh = S // boards
    b = (yy // bh).astype(int)
    grain = fbm(rng, ((2, 1.0), (6, 0.4), (24, 0.3)), aniso=(0.25, 4.0))
    knots_d, _, _ = voronoi(rng, 3)
    knots = np.exp(-(knots_d / 2.2) ** 2) * (rng.random() > 0.3)
    tone = ((np.sin(b * 7.31 + seed) * 43758.5453) % 1) * 0.3
    t = grain * 0.6 + tone + (np.sin(xx * 0.15 + grain * 9 + b) * 0.5 + 0.5) * 0.15 - knots * 0.5
    col = ramp(t, cols)
    ly = yy % bh
    col *= np.where(ly < 1, 1.16, np.where(ly > bh - 2, 0.72, 1.0))[..., None]
    # board ends: every board has one seam at its own position
    seam = ((xx + (b * 23 + seed * 7) % S) % S) < 1
    col *= np.where(seam, 0.7, 1.0)[..., None]
    if nails:
        for i in range(boards):
            for nx in ((i * 23 + seed * 7 + 3) % S, (i * 23 + seed * 7 - 4) % S):
                cy = i * bh + bh // 2
                col[cy - 1:cy + 1, nx:nx + 2] = col[cy - 1:cy + 1, nx:nx + 2] * 0.45 + hexc("#2a1c12") * 0.3
    return rgba(col)


def log_side(seed, cols, stripes=0.0):
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    ridges = fbm(rng, ((2, 1.0), (4, 0.6), (8, 0.3)), aniso=(2.4, 0.09))
    deep = (pnoise(rng, 4, aniso=(2.6, 0.12)) > 0.64)
    t = ridges * 0.8 + pnoise(rng, 20, aniso=(1.5, 0.5)) * 0.2
    col = ramp(t, cols)
    col *= np.where(deep, 0.74, 1.0)[..., None]
    col *= shade(soft(ridges, 1), 1.6)[..., None]
    if stripes:  # birch: light bark with dark marks
        marks = (pnoise(rng, 9, aniso=(0.4, 2.5)) > 1 - stripes)
        col = np.where(marks[..., None], hexc("#2e2a26") * (0.8 + 0.4 * pnoise(rng, 30))[..., None], col)
    return rgba(col)


def log_top(seed, wood, bark):
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    cx = cy = S / 2 - 0.5
    warp = pnoise(rng, 6) * 3
    r = np.hypot(xx - cx, yy - cy) + warp
    rings = np.sin(r * 1.05) * 0.5 + 0.5
    col = ramp(rings * 0.55 + pnoise(rng, 36) * 0.25 + (r / S) * 0.3, wood)
    edge = np.maximum(np.abs(xx - cx), np.abs(yy - cy))
    rim = edge > S / 2 - 6
    col = np.where(rim[..., None], ramp(pnoise(rng, 20), bark), col)
    col *= np.where((edge > S / 2 - 7) & (edge <= S / 2 - 6), 0.6, 1.0)[..., None]
    return rgba(col)


def leaves(seed, cols, density=0.78, tinted=True, blossoms=None):
    """Cutout leaves: soft leaf blobs with holes, lit from above."""
    rng = np.random.default_rng(seed)
    d, d2, ids = voronoi(rng, 46)
    leaf = d < (rng.random(46)[ids] * 2.0 + 3.0)
    n = fbm(rng)
    alpha = (leaf | (n > 1 - density)) & ~((pnoise(rng, 14) > 0.8) & (n < 0.55))
    height = np.clip(1 - d / 5, 0, 1)
    t = height * 0.5 + n * 0.3 + pnoise(rng, 40) * 0.2
    col = ramp(t, cols)
    col *= shade(soft(height), 0.9)[..., None]
    if blossoms:
        bl = (pnoise(rng, 26) > 0.8) & alpha
        col = np.where(bl[..., None], ramp(pnoise(rng, 40), blossoms), col)
    if tinted:  # keep grey so biome tint still colours it
        col = col.mean(-1, keepdims=True).repeat(3, -1)
    return rgba(col, alpha.astype(np.float32))


def grass_top(seed):
    rng = np.random.default_rng(seed)
    n = fbm(rng)
    blades = pnoise(rng, 26)
    tufts = pnoise(rng, 7)
    t = n * 0.4 + tufts * 0.38 + blades * 0.22
    lum = 0.5 + t * 0.24
    lum *= shade(soft(n * 0.5 + tufts * 0.5, 1), 0.7)
    return rgba(np.repeat(lum[..., None], 3, -1))


def grass_side(seed, dirt_cols):
    """Dirt only; the grass fringe comes from the tinted overlay."""
    base = earth(seed, dirt_cols, pebbles=["#7a6a5a", "#9a8a78"], pebble_n=8)
    rng = np.random.default_rng(seed + 5)
    yy, xx = grid()
    edge = 14 + (pnoise(rng, 10)[0] * 12).astype(int)
    fringe = yy < edge[None, :]
    base[..., :3] = np.where(fringe[..., None], ramp(pnoise(rng, 30), ["#5e7a32", "#7e9a3e", "#9ab44c"]), base[..., :3])
    return base


def grass_overlay(seed):
    rng = np.random.default_rng(seed + 5)
    yy, xx = grid()
    edge = 14 + (pnoise(rng, 10)[0] * 12).astype(int)
    drips = pnoise(rng, 5, aniso=(2.0, 0.3)) > 0.7
    alpha = (yy < edge[None, :]) | ((yy < edge[None, :] + 6) & drips)
    lum = 0.55 + pnoise(rng, 36) * 0.3 - (yy / 30) * 0.12
    return rgba(np.repeat(lum[..., None], 3, -1), alpha.astype(np.float32))


def sand(seed, cols, ripples=True):
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    n = fbm(rng)
    rip = np.sin((yy + pnoise(rng, 5) * 10) / S * math.tau * 5) * 0.5 + 0.5 if ripples else 0
    speck = pnoise(rng, 60)
    col = ramp(n * 0.4 + rip * 0.25 + speck * 0.35, cols)
    col = np.where((speck > 0.88)[..., None], col * 0.82, col)
    col *= shade(n * 0.5 + rip * 0.5, 0.9)[..., None]
    return rgba(col)


def sandstone(seed, cols, kind):
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    n = fbm(rng)
    layers = np.sin((yy + n * 6) / S * math.tau * 4) * 0.5 + 0.5
    if kind == "top":
        col = ramp(n * 0.5 + pnoise(rng, 50) * 0.5, cols)
    else:
        col = ramp(layers * 0.5 + n * 0.25 + pnoise(rng, 50) * 0.25, cols)
        if kind == "side":
            band = (yy < 10) | (yy > S - 12)
            col = np.where(band[..., None], col * 1.07, col * 0.97)
            col *= np.where((yy >= 10) & (yy < 12), 0.72, 1.0)[..., None]
    return rgba(col)


def ore(base, seed, cols, shape="blob", count=4):
    """Clusters of chunky ore nodules (or gems) with a dark rim and lit facets on a stone base."""
    rng = np.random.default_rng(seed)
    out = base.copy()
    yy, xx = grid()
    lumpy = pnoise(rng, 10)
    nodules = []
    for _ in range(count):
        cx, cy = rng.random(2) * S
        for _ in range(int(rng.integers(3, 6))):
            nodules.append((cx + rng.normal(0, 4.5), cy + rng.normal(0, 4.5), rng.uniform(2.6, 4.6), rng.random() * math.pi))
    for cx, cy, size, ang in nodules:
        dx = (xx - cx + S / 2) % S - S / 2
        dy = (yy - cy + S / 2) % S - S / 2
        if shape == "crystal":
            u = dx * math.cos(ang) + dy * math.sin(ang)
            v = -dx * math.sin(ang) + dy * math.cos(ang)
            r = (np.abs(u) / (size * 0.75) + np.abs(v) / (size * 1.25))
            light = np.clip(0.5 + np.sign(u) * 0.25 + np.sign(v) * -0.15 + (-dx - dy) / (size * 4), 0, 1)
        else:
            r = np.hypot(dx, dy) / size + (lumpy - 0.5) * 0.35
            light = np.clip(0.6 + (-dx - dy) / (size * 2.2), 0, 1)
        inside = r < 1
        rim = (r >= 1) & (r < 1 + 1.4 / size)
        col = ramp(light * 0.75 + np.clip(1 - r, 0, 1) * 0.25, cols)
        out[..., :3] = np.where(rim[..., None], out[..., :3] * 0.62, out[..., :3])
        out[..., :3] = np.where(inside[..., None], col, out[..., :3])
        glint = inside & (dx < -size * 0.15) & (dx > -size * 0.55) & (dy < -size * 0.15) & (dy > -size * 0.55)
        out[..., :3] = np.where(glint[..., None], np.minimum(col * 1.35 + 0.08, 1), out[..., :3])
    return out


def liquid(seed, cols, frames=32, grey=False, glow=False):
    """Animated seamless liquid: noise drifting in a loop."""
    rng = np.random.default_rng(seed)
    a = fbm(rng, ((2, 1.0), (4, 0.5), (8, 0.25)))
    b = fbm(rng, ((2, 1.0), (5, 0.5), (9, 0.2)))
    out = []
    for f in range(frames):
        t = f / frames
        sa = np.roll(np.roll(a, int(t * S), 0), int(t * S / 2), 1)
        sb = np.roll(b, -int(t * S), 1)
        v = (sa * 0.6 + sb * 0.4)
        caustic = np.abs(np.sin(v * math.tau * 2)) ** 3
        if grey:
            lum = 0.62 + v * 0.18 + caustic * 0.16
            col = np.repeat(lum[..., None], 3, -1)
            out.append(rgba(col, np.full((S, S), 0.82, np.float32)))
        else:
            col = ramp(v * 0.7 + caustic * 0.3, cols)
            if glow:
                col = np.where((caustic > 0.75)[..., None], np.minimum(col * 1.25, 1), col)
            out.append(rgba(col))
    return np.concatenate(out, 0)


def glass(seed, frame_cols):
    yy, xx = grid()
    edge = np.minimum(np.minimum(xx, S - 1 - xx), np.minimum(yy, S - 1 - yy))
    frame = edge < 3
    rgb = np.zeros((S, S, 3), np.float32)
    rgb[:] = hexc("#e8f4f8")
    alpha = np.zeros((S, S), np.float32)
    rgb = np.where(frame[..., None], ramp(edge / 3, frame_cols), rgb)
    alpha = np.where(frame, 1.0, alpha)
    streak = ((xx - yy) % 64 > 8) & ((xx - yy) % 64 < 12) & (xx > 10) & (xx < 30) & (yy > 6) & (yy < 30)
    streak |= ((xx - yy) % 64 > 14) & ((xx - yy) % 64 < 15) & (xx > 12) & (xx < 34) & (yy > 8) & (yy < 34)
    alpha = np.where(streak & ~frame, 0.5, alpha)
    return rgba(rgb, alpha)


# block name -> generator (the rest of Minecraft is restyled from the game's own textures)
WOODS = {
    "oak": (["#7a5532", "#9a6e40", "#b8874f", "#cfa064"], ["#3a2818", "#57402a", "#6e5236", "#86684a"]),
    "spruce": (["#4e3420", "#664429", "#7e5634", "#946944"], ["#2a1c12", "#3e2a1a", "#523826", "#664a32"]),
    "birch": (["#c2a874", "#d4bc88", "#e2cfa0", "#efe0b8"], ["#d8d2c4", "#e8e2d6", "#f4f0e6", "#ffffff"]),
    "jungle": (["#7a4e30", "#966240", "#ae7650", "#c48c60"], ["#3a2a14", "#52401e", "#6a542a", "#7e6838"]),
    "acacia": (["#8a3e1e", "#a85028", "#c26434", "#d87a44"], ["#4a4038", "#625650", "#7a6e66", "#8e847a"]),
    "dark_oak": (["#2e1c0e", "#3e2814", "#50361e", "#62442a"], ["#1e140c", "#2c1e12", "#3c2a1a", "#4c3824"]),
    "mangrove": (["#5e1e1a", "#782a24", "#923a30", "#a84a3e"], ["#3a2a22", "#4e3a30", "#62483c", "#765a4a"]),
    "cherry": (["#c88a84", "#dca29a", "#ecb8ae", "#f6ccc2"], ["#2e1a22", "#3e2430", "#52323e", "#64404c"]),
    "pale_oak": (["#c8bcb0", "#d8cec4", "#e6ded6", "#f2ece6"], ["#4a443e", "#5e5852", "#726c66", "#86807a"]),
}


def _register():
    gens = {}
    dirt_cols = ["#4a3020", "#5e3e28", "#765034", "#8e6442"]
    gens["dirt"] = lambda: earth(1, dirt_cols, pebbles=["#6e5e50", "#8e7e6e"], pebble_n=10)
    gens["coarse_dirt"] = lambda: earth(2, ["#3e281a", "#56382a", "#6e4c36", "#866044"], pebbles=["#7a7068", "#9a9088"], pebble_n=22)
    gens["rooted_dirt"] = lambda: earth(3, dirt_cols, pebbles=["#b89870", "#d0b088"], pebble_n=6)
    gens["podzol_top"] = lambda: earth(4, ["#3e2614", "#5a3a1c", "#7a5228", "#966a34"], pebbles=["#8a5a2a", "#b07a3a"], pebble_n=26)
    gens["mud"] = lambda: earth(5, ["#2e2a2a", "#3c3634", "#4a4240", "#58504c"], grain=0.2)
    gens["grass_block_top"] = lambda: grass_top(6)
    gens["grass_block_side"] = lambda: grass_side(1, dirt_cols)
    gens["grass_block_side_overlay"] = lambda: grass_overlay(1)
    gens["stone"] = lambda: stone(7, ["#5e5a62", "#747078", "#8a868e", "#a29ea6"], cracks=0.25)
    gens["smooth_stone"] = lambda: stone(8, ["#7a767e", "#8a868e", "#9a96a0"])
    gens["andesite"] = lambda: stone(9, ["#5e5e60", "#76767a", "#8e8e92", "#a8a8ac"])
    gens["diorite"] = lambda: stone(10, ["#a8a4a2", "#c4c0be", "#dcd8d6", "#f2f0ee"])
    gens["granite"] = lambda: stone(11, ["#7a4a3e", "#986050", "#b07a66", "#c8957e"])
    gens["calcite"] = lambda: stone(12, ["#d4d2cc", "#e2e0dc", "#eeece8", "#fafaf8"])
    gens["tuff"] = lambda: stone(13, ["#4a4a42", "#5e5e54", "#727266", "#86867a"], strata=0.06)
    gens["deepslate"] = lambda: stone(14, ["#26262e", "#34343e", "#44444e", "#56565e"], strata=0.08)
    gens["deepslate_top"] = lambda: stone(15, ["#26262e", "#34343e", "#44444e", "#56565e"])
    gens["netherrack"] = lambda: stone(16, ["#4a1616", "#621e1e", "#7a2a28", "#923a34"], cracks=0.4)
    gens["end_stone"] = lambda: stone(17, ["#bab486", "#d0ca9a", "#e2dcb0", "#f0ecc6"], cracks=0.2)
    gens["obsidian"] = lambda: stone(18, ["#0c0812", "#180f24", "#26183a", "#3a2858"], cracks=0.3)
    gens["bedrock"] = lambda: cobble(19, ["#1a1a1c", "#2e2e32", "#48484e", "#5e5e64"], ["#0a0a0c", "#141416"], n_stones=16)
    gens["cobblestone"] = lambda: cobble(20, ["#5a5660", "#726e78", "#8c8892", "#a6a2ac"], ["#2a2830", "#3a3840"])
    gens["mossy_cobblestone"] = lambda: cobble(20, ["#5a5660", "#726e78", "#8c8892", "#a6a2ac"], ["#2a2830", "#3a3840"],
                                               moss=["#3e5e22", "#5a7e2e", "#7a9a3c"])
    gens["cobbled_deepslate"] = lambda: cobble(21, ["#24242c", "#32323c", "#42424c", "#52525c"], ["#121216", "#1a1a20"], n_stones=14)
    gens["stone_bricks"] = lambda: bricks(22, ["#5e5a64", "#726e78", "#86828c", "#9a96a0"], ["#2e2c34", "#3e3c44"], rows=4, per_row=2)
    gens["mossy_stone_bricks"] = lambda: bricks(22, ["#5e5a64", "#726e78", "#86828c", "#9a96a0"], ["#2e2c34", "#3e3c44"], rows=4,
                                                per_row=2, moss=["#3e5e22", "#5a7e2e", "#7a9a3c"])
    gens["cracked_stone_bricks"] = lambda: bricks(22, ["#5e5a64", "#726e78", "#86828c", "#9a96a0"], ["#2e2c34", "#3e3c44"], rows=4,
                                                  per_row=2, cracks=True, chipped=0.25)
    gens["bricks"] = lambda: bricks(23, ["#7e3626", "#984430", "#b0563c", "#c46a4c"], ["#b8aca0", "#d0c6ba"], rows=8, per_row=4)
    gens["deepslate_bricks"] = lambda: bricks(24, ["#2a2a32", "#383840", "#46464e", "#54545c"], ["#141418", "#1c1c22"], rows=8, per_row=4)
    gens["nether_bricks"] = lambda: bricks(25, ["#2a1014", "#3a161a", "#4a1e22", "#5a282c"], ["#140608", "#1c0a0c"], rows=8, per_row=4)
    gens["mud_bricks"] = lambda: bricks(26, ["#7a5e48", "#8e705a", "#a2846c", "#b4967e"], ["#5a4434", "#6a5242"], rows=8, per_row=4)
    gens["sand"] = lambda: sand(27, ["#c8b07a", "#dcc48e", "#ead6a2", "#f6e6ba"])
    gens["red_sand"] = lambda: sand(28, ["#9a4a1e", "#b25a26", "#c66c32", "#d8803e"])
    gens["gravel"] = lambda: cobble(29, ["#56524e", "#6e6a66", "#88847e", "#a29e98"], ["#2e2a28", "#3a3634"], n_stones=30)
    gens["clay"] = lambda: stone(30, ["#8a8ea0", "#9ca0b0", "#aeb2c0", "#c0c4d0"])
    gens["snow"] = lambda: sand(31, ["#dfe8f0", "#eaf0f6", "#f4f8fc", "#ffffff"], ripples=False)
    gens["sandstone_top"] = lambda: sandstone(32, ["#d2bc84", "#e0cc96", "#ecdaa8", "#f6e8bc"], "top")
    gens["sandstone"] = lambda: sandstone(32, ["#c8b07a", "#d8c28c", "#e6d29e", "#f2e2b2"], "side")
    gens["sandstone_bottom"] = lambda: sandstone(32, ["#c8b07a", "#d8c28c", "#e6d29e", "#f2e2b2"], "bottom")
    gens["red_sandstone_top"] = lambda: sandstone(33, ["#a24e20", "#b45c28", "#c46c32", "#d47e3e"], "top")
    gens["red_sandstone"] = lambda: sandstone(33, ["#9a4a1e", "#ac5826", "#bc6830", "#cc7a3c"], "side")
    gens["red_sandstone_bottom"] = lambda: sandstone(33, ["#9a4a1e", "#ac5826", "#bc6830", "#cc7a3c"], "bottom")
    gens["glass"] = lambda: glass(34, ["#8a9aa4", "#c8d8e0", "#eef6fa"])
    gens["water_still"] = lambda: liquid(35, None, grey=True)
    gens["water_flow"] = lambda: liquid(36, None, grey=True)
    gens["lava_still"] = lambda: liquid(37, ["#a8260a", "#d84a10", "#ff7a1a", "#ffb43a", "#ffe27a"], glow=True)
    gens["lava_flow"] = lambda: liquid(38, ["#a8260a", "#d84a10", "#ff7a1a", "#ffb43a", "#ffe27a"], glow=True)
    for wood, (plank_cols, bark_cols) in WOODS.items():
        seed = sum(map(ord, wood))
        gens[f"{wood}_planks"] = (lambda c=plank_cols, s=seed: planks(s, c))
        gens[f"{wood}_log"] = (lambda c=bark_cols, s=seed, w=wood: log_side(s, c, stripes=0.16 if w == "birch" else 0.0))
        gens[f"{wood}_log_top"] = (lambda c=plank_cols, b=bark_cols, s=seed: log_top(s, c, b))
        gens[f"stripped_{wood}_log"] = (lambda c=plank_cols, s=seed: log_side(s + 1, c))
        gens[f"stripped_{wood}_log_top"] = (lambda c=plank_cols, s=seed: log_top(s, c, c))
    leaf = {"oak": 40, "jungle": 41, "acacia": 42, "dark_oak": 43, "mangrove": 44, "birch": 45, "spruce": 46}
    for name, seed in leaf.items():
        gens[f"{name}_leaves"] = (lambda s=seed: leaves(s, ["#3a3a3a", "#5a5a5a", "#7a7a7a", "#9a9a9a"]))
    gens["cherry_leaves"] = lambda: leaves(47, ["#d47a9a", "#e894b0", "#f6b0c8", "#ffd0e0"], tinted=False)
    gens["azalea_leaves"] = lambda: leaves(48, ["#3e5e22", "#5a7e2e", "#7a9a3c", "#94b44e"], tinted=False)
    gens["flowering_azalea_leaves"] = lambda: leaves(48, ["#3e5e22", "#5a7e2e", "#7a9a3c", "#94b44e"], tinted=False,
                                                     blossoms=["#c45aa8", "#e07ac4", "#f4a0dc"])
    gens["pale_oak_leaves"] = lambda: leaves(49, ["#6a7068", "#828a80", "#9aa298", "#b4bab0"], tinted=False)
    stone_base = gens["stone"]
    deep_base = gens["deepslate"]
    ores = {
        "coal": (["#1a1a1e", "#2e2e34", "#46464e"], "blob"), "iron": (["#a8745a", "#d0a084", "#ecc8b0"], "blob"),
        "copper": (["#a84e2e", "#d4743e", "#7ac0a0"], "blob"), "gold": (["#b8860a", "#f2c12e", "#fff08a"], "blob"),
        "redstone": (["#8a0a0a", "#e0201a", "#ff7a5a"], "blob"), "lapis": (["#1a2e8a", "#2e52c8", "#7a9aff"], "crystal"),
        "diamond": (["#1e9a9a", "#4ae0d4", "#c8fff8"], "crystal"), "emerald": (["#0e7a3a", "#22c45e", "#a8ffc0"], "crystal"),
    }
    for i, (name, (cols, shape)) in enumerate(ores.items()):
        gens[f"{name}_ore"] = (lambda c=cols, sh=shape, s=60 + i: ore(stone_base(), s, c, sh))
        gens[f"deepslate_{name}_ore"] = (lambda c=cols, sh=shape, s=70 + i: ore(deep_base(), s, c, sh))
    nether = gens["netherrack"]
    gens["nether_gold_ore"] = lambda: ore(nether(), 80, ["#b8860a", "#f2c12e", "#fff08a"], "blob", count=9)
    gens["nether_quartz_ore"] = lambda: ore(nether(), 81, ["#c8bcb4", "#ece4de", "#ffffff"], "crystal", count=7)
    return gens


HANDCRAFTED = _register()
ANIMATED = {"water_still": 2, "water_flow": 2, "lava_still": 3, "lava_flow": 3}  # frametime


# ---------- Verzauberte Bücher ----------

CATEGORY = {
    "weapon": ("#9a2a24", "#c8402e"), "armor": ("#24508a", "#3a72c0"), "tool": ("#2e6a32", "#46944a"),
    "bow": ("#8a5a14", "#c4862a"), "trident": ("#14706a", "#22a096"), "fishing": ("#1a6a8a", "#2e96c0"),
    "crossbow": ("#6a3a1a", "#9a5a2a"), "mace": ("#4a4e5a", "#6e7484"), "curse": ("#3a1a4a", "#5e2a74"),
    "other": ("#7a5a1a", "#b48a2a"),
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
ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]


def _emblem(d, kind, c, gold, k):
    """Draws one emblem centred at (c, c) on a 4x supersampled canvas; k = scale."""
    def poly(points, fill):
        d.polygon([(c + x * k, c + y * k) for x, y in points], fill=fill)

    def circ(x, y, r, fill):
        d.ellipse([c + (x - r) * k, c + (y - r) * k, c + (x + r) * k, c + (y + r) * k], fill=fill)

    def line(points, w, fill):
        d.line([(c + x * k, c + y * k) for x, y in points], fill=fill, width=max(1, int(w * k)), joint="curve")

    white = (255, 246, 226, 255)
    dark = (40, 24, 18, 255)
    if kind == "sword":
        poly([(-1.2, 7), (-1.2, -7), (0, -9.5), (1.2, -7), (1.2, 7)], white)
        poly([(-4.5, 5.5), (4.5, 5.5), (4.5, 7), (-4.5, 7)], gold)
        poly([(-0.9, 7), (0.9, 7), (0.9, 10.5), (-0.9, 10.5)], dark)
    elif kind == "sun":
        for i in range(8):
            a = i / 8 * math.tau
            line([(math.cos(a) * 4, math.sin(a) * 4), (math.cos(a) * 8.5, math.sin(a) * 8.5)], 1.6, gold)
        circ(0, 0, 4.2, white)
    elif kind == "spider":
        for side in (-1, 1):
            for j, y in enumerate((-2.5, -0.5, 1.5, 3.5)):
                line([(side * 2, y * 0.6), (side * 6, y - 2), (side * 8, y + 2.5)], 1.0, white)
        circ(0, 2, 3.4, white)
        circ(0, -2.5, 2.2, white)
        circ(-0.8, -2.8, 0.5, (220, 40, 40, 255))
        circ(0.8, -2.8, 0.5, (220, 40, 40, 255))
    elif kind == "burst":
        pts = []
        for i in range(16):
            a = i / 16 * math.tau
            r = 8.5 if i % 2 == 0 else 3.5
            pts.append((math.cos(a) * r, math.sin(a) * r))
        poly(pts, white)
        circ(0, 0, 2.2, gold)
    elif kind in ("flame", "soul"):
        outer = (255, 140, 40, 255) if kind == "flame" else (90, 200, 230, 255)
        inner = (255, 226, 120, 255) if kind == "flame" else (200, 250, 255, 255)
        poly([(0, -9), (5, -2), (5.5, 3), (2.5, 7), (-2.5, 7), (-5.5, 3), (-4, -1), (-2, 1), (-1.5, -4)], outer)
        poly([(0, -3), (2.8, 1.5), (2.4, 5), (-2.4, 5), (-2.6, 2)], inner)
    elif kind == "gem":
        poly([(-6, -2), (-3, -6), (3, -6), (6, -2), (0, 8)], (90, 220, 210, 255))
        poly([(-6, -2), (6, -2), (0, 8)], (60, 170, 170, 255))
        poly([(-3, -6), (0, -2), (3, -6)], (200, 255, 248, 255))
    elif kind == "arc":
        d.arc([c - 8 * k, c - 8 * k, c + 8 * k, c + 8 * k], 200, 340, fill=white, width=int(2.4 * k))
        d.arc([c - 5 * k, c - 4 * k, c + 5 * k, c + 6 * k], 200, 340, fill=gold, width=int(1.6 * k))
        poly([(-1, 3), (1, 3), (1, 9), (-1, 9)], white)
    elif kind == "pickaxe":
        d.arc([c - 8 * k, c - 7 * k, c + 8 * k, c + 5 * k], 200, 340, fill=white, width=int(2.6 * k))
        line([(0, -5), (0, 9)], 2.0, (150, 100, 60, 255))
        for i in range(3):
            line([(-9 + i * 0.5, 3 + i * 2.2), (-5 + i * 0.5, 3 + i * 2.2)], 1.0, gold)
    elif kind == "feather":
        poly([(5, -9), (7, -6), (2, 3), (-5, 9), (-6, 8), (0, 0), (2, -6)], white)
        line([(6, -8), (-6, 9)], 0.9, (190, 170, 140, 255))
    elif kind == "clover":
        for dx, dy in ((0, -3.5), (3.5, 0), (0, 3.5), (-3.5, 0)):
            circ(dx, dy, 3.4, (110, 210, 90, 255))
        circ(0, 0, 1.6, (170, 240, 140, 255))
        line([(1, 3), (4, 9)], 1.4, (80, 150, 60, 255))
    elif kind == "anvil":
        poly([(-8, -5), (8, -5), (6, -1), (3, -1), (3, 3), (6, 6), (-6, 6), (-3, 3), (-3, -1), (-6, -1)], (200, 205, 215, 255))
        poly([(-8, -5), (8, -5), (7.5, -4), (-7.5, -4)], white)
    elif kind == "heart":
        circ(-3, -2, 4, (230, 70, 90, 255))
        circ(3, -2, 4, (230, 70, 90, 255))
        poly([(-6.8, -0.5), (6.8, -0.5), (0, 8)], (230, 70, 90, 255))
        poly([(-0.8, -4), (0.8, -4), (0.8, 2), (-0.8, 2)], white)
        poly([(-3, -1.8), (3, -1.8), (3, -0.2), (-3, -0.2)], white)
    elif kind.startswith("shield"):
        poly([(-7, -8), (7, -8), (7, 0), (0, 9), (-7, 0)], (200, 210, 230, 255))
        poly([(-5, -6), (5, -6), (5, -0.5), (0, 6.5), (-5, -0.5)], (90, 130, 200, 255))
        if kind == "shield_flame":
            _emblem(d, "flame", c, gold, k * 0.5)
        elif kind == "shield_burst":
            _emblem(d, "burst", c, gold, k * 0.5)
        elif kind == "shield_arrow":
            _emblem(d, "arrow", c, gold, k * 0.5)
        else:
            circ(0, -1, 2, gold)
    elif kind == "thorns":
        line([(-8, 6), (-3, 0), (3, -1), (8, -7)], 1.8, (110, 170, 70, 255))
        for x, y, s in ((-5, 3, 1), (0, -0.5, -1), (5, -4, 1)):
            poly([(x - 1, y), (x + 1, y), (x, y - 3.5 * s)], white)
    elif kind == "bubbles":
        for x, y, r in ((-3, 3, 3.2), (3, -1, 2.4), (-1, -5, 1.8), (4, 5, 1.4)):
            circ(x, y, r, (150, 220, 250, 255))
            circ(x - r * 0.35, y - r * 0.35, r * 0.35, white)
    elif kind == "wave":
        for i, y in enumerate((-4, 1, 6)):
            d.arc([c - 8 * k, c + (y - 3) * k, c, c + (y + 3) * k], 180, 360, fill=(150, 220, 250, 255), width=int(1.6 * k))
            d.arc([c, c + (y - 3) * k, c + 8 * k, c + (y + 3) * k], 0, 180, fill=(150, 220, 250, 255), width=int(1.6 * k))
    elif kind == "snowflake":
        for i in range(3):
            a = i / 3 * math.pi
            line([(math.cos(a) * -8, math.sin(a) * -8), (math.cos(a) * 8, math.sin(a) * 8)], 1.6, (210, 240, 255, 255))
        circ(0, 0, 2, white)
    elif kind == "steps":
        for x, y in ((-4, 3), (3, -3)):
            circ(x, y, 2.8, white)
            circ(x + 0.5, y + 4.2, 1.4, white)
    elif kind == "bow":
        d.arc([c - 9 * k, c - 9 * k, c + 5 * k, c + 9 * k], 280, 80, fill=(170, 120, 70, 255), width=int(2.2 * k))
        line([(3.6, -8.8), (3.6, 8.8)], 0.8, white)
        line([(-7, 0), (7, 0)], 1.2, white)
        poly([(7, -2), (10, 0), (7, 2)], white)
    elif kind == "infinity":
        d.arc([c - 9 * k, c - 4 * k, c, c + 4 * k], 0, 360, fill=white, width=int(1.8 * k))
        d.arc([c, c - 4 * k, c + 9 * k, c + 4 * k], 0, 360, fill=white, width=int(1.8 * k))
    elif kind == "hook":
        line([(2, -9), (2, 3)], 1.6, (200, 205, 215, 255))
        d.arc([c - 5 * k, c - 1 * k, c + 3 * k, c + 7 * k], 0, 180, fill=(200, 205, 215, 255), width=int(1.6 * k))
        poly([(-5, 3), (-6.5, 0), (-3.5, 1)], (200, 205, 215, 255))
        circ(2, -9, 1.4, gold)
    elif kind == "trident":
        line([(0, -6), (0, 10)], 1.6, (90, 200, 190, 255))
        for x in (-4, 0, 4):
            poly([(x - 1.2, -4), (x + 1.2, -4), (x, -9)], (150, 240, 230, 255))
        line([(-4, -4), (4, -4)], 1.4, (90, 200, 190, 255))
    elif kind == "spiral":
        pts = [(math.cos(t * 0.5) * t * 0.55, math.sin(t * 0.5) * t * 0.55) for t in np.linspace(0, 16, 40)]
        line(pts, 1.6, (180, 240, 250, 255))
    elif kind == "bolt":
        poly([(2, -9), (-4, 1), (0, 1), (-2, 9), (5, -2), (1, -2), (4, -9)], (255, 226, 90, 255))
    elif kind == "arrows":
        for dy in (-4, 0, 4):
            line([(-7, dy + 2), (5, dy - 2)], 1.0, white)
            poly([(4, dy - 3.6), (8, dy - 3.2), (5.6, dy - 0.4)], white)
    elif kind == "arrow":
        line([(-8, 6), (5, -5)], 1.4, white)
        poly([(3, -7.5), (8.5, -8.5), (7.5, -3)], white)
        poly([(-8, 6), (-9, 3), (-6, 4)], gold)
    elif kind == "chain":
        for i, (x, y) in enumerate(((-5, -4), (0, 0), (5, 4))):
            d.ellipse([c + (x - 3.2) * k, c + (y - 2.2) * k, c + (x + 3.2) * k, c + (y + 2.2) * k], outline=(170, 170, 185, 255),
                      width=int(1.4 * k))
    elif kind == "ghost":
        poly([(-6, 8), (-6, -2), (-4, -7), (0, -9), (4, -7), (6, -2), (6, 8), (3, 6), (0, 8), (-3, 6)], (220, 220, 240, 190))
        circ(-2.2, -2, 1.2, dark)
        circ(2.2, -2, 1.2, dark)
    else:
        _emblem(d, "burst", c, gold, k)


def enchanted_book(enchant, level, max_level):
    """A 64x64 book: coloured cover, gold corners, an emblem for the enchantment and the level."""
    cat, emblem = ENCHANT_STYLE.get(enchant, ("other", "burst"))
    dark, light = (tuple(int(v * 255) for v in hexc(h)) for h in CATEGORY[cat])
    gold = (242, 193, 78, 255)
    up = 4
    W = S * up
    img = Image.new("RGBA", (W, W))
    d = ImageDraw.Draw(img)
    u = lambda v: v * up  # noqa: E731
    # pages (cream) behind the cover, then the cover with a lit top edge
    d.rounded_rectangle([u(14), u(9), u(55), u(59)], radius=u(3), fill=(236, 222, 190, 255))
    for y in range(12, 58, 3):
        d.line([(u(50), u(y)), (u(54), u(y))], fill=(200, 184, 150, 255), width=up)
    d.rounded_rectangle([u(8), u(5), u(50), u(56)], radius=u(4), fill=dark + (255,))
    d.rounded_rectangle([u(11), u(6), u(50), u(54)], radius=u(4), fill=light + (255,))
    d.rectangle([u(8), u(5), u(13), u(56)], fill=tuple(max(0, v - 30) for v in dark) + (255,))
    for y in (13, 46):  # spine bands
        d.rectangle([u(8), u(y), u(13), u(y + 2)], fill=gold)
    for x, y in ((47, 6), (47, 51)):  # corners
        d.polygon([(u(x), u(y)), (u(x + 3), u(y)), (u(x + 3), u(y + 3))] if y < 20 else
                  [(u(x + 3), u(y)), (u(x + 3), u(y + 3)), (u(x), u(y + 3))], fill=gold)
    # emblem plate
    d.ellipse([u(17), u(14), u(45), u(42)], fill=tuple(max(0, v - 25) for v in dark) + (255,))
    d.ellipse([u(18), u(15), u(44), u(41)], outline=gold, width=u(1))
    _emblem(d, emblem, u(31), gold, u(1.15))
    # level as small gold pips (I..V) or a roman numeral for higher levels
    if max_level > 1:
        if level <= 5:
            total = level * 5 - 1
            x0 = 31 - total / 2
            for i in range(level):
                d.rounded_rectangle([u(x0 + i * 5), u(46), u(x0 + i * 5 + 3), u(50)], radius=u(1), fill=gold)
        else:
            d.text((u(24), u(43)), ROMAN[min(level, 10)], fill=gold)
    img = img.resize((S, S), Image.LANCZOS)
    # soft dark outline so the book reads well in the inventory
    a = np.asarray(img, np.float32) / 255
    mask = a[..., 3]
    grow = np.maximum.reduce([np.roll(np.roll(mask, dy, 0), dx, 1) for dy in (-1, 0, 1) for dx in (-1, 0, 1)])
    outline = np.clip(grow - mask, 0, 1)
    out = a.copy()
    out[..., :3] = a[..., :3] * mask[..., None] + hexc("#24140c") * (1 - mask[..., None])
    out[..., 3] = np.maximum(mask, outline * 0.85)
    return out


def to_image(arr):
    return Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")
