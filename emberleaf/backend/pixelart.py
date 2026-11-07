"""Emberleaf 16x pixel art: every texture drawn from code with small hand-picked palettes.

Style (mixed from the packs the player likes, nothing copied): crisp 16x pixel art close to the vanilla feel,
calm cozy colours with a hue shift (cool shadows, warm lights), clear shapes with a light edge at the top left
and a dark edge at the bottom right, bushy leaves, chunky ores, readable planks and bricks.
Arrays are uint8 RGBA (16, 16, 4); animated textures stack frames vertically.
"""
import math

import numpy as np

from .cozyart import pnoise

S = 16


def hx(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.int32)


def pal(*cols):
    return np.array([hx(c) for c in cols], np.int32)


def rgba(idx, palette, alpha=None):
    out = np.zeros((*idx.shape, 4), np.uint8)
    out[..., :3] = palette[np.clip(idx, 0, len(palette) - 1)]
    out[..., 3] = 255 if alpha is None else alpha
    return out


def grid():
    yy, xx = np.mgrid[0:S, 0:S]
    return yy, xx


def quant(t, n):
    """0..1 -> palette index 0..n-1 (clusters, not noise)."""
    return np.clip((np.clip(t, 0, 0.9999) * n).astype(np.int32), 0, n - 1)


def smooth_field(rng, scales=((2, 1.0), (4, 0.55), (8, 0.25))):
    f = sum(pnoise(rng, s, size=S) * w for s, w in scales)
    f -= f.min()
    return f / max(f.max(), 1e-6)


def tidy(idx, passes=1):
    """Pixel-art cleanup: a lonely pixel takes the colour most of its neighbours have."""
    for _ in range(passes):
        n = [np.roll(np.roll(idx, dy, 0), dx, 1) for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1))]
        stack = np.stack(n)
        same = (stack == idx).sum(0)
        majority = np.median(stack, 0).astype(np.int32)
        idx = np.where(same == 0, majority, idx)
    return idx


def wrap_neighbours(mask):
    up = np.roll(mask, 1, 0)
    left = np.roll(mask, 1, 1)
    down = np.roll(mask, -1, 0)
    right = np.roll(mask, -1, 1)
    return up, left, down, right


def voronoi16(rng, n, size=S):
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    pts = rng.random((n, 2)) * size
    best = np.full((size, size), 1e9, np.float32)
    second = np.full((size, size), 1e9, np.float32)
    ids = np.zeros((size, size), np.int32)
    for i, (px, py) in enumerate(pts):
        dx = np.abs(xx + 0.5 - px)
        dy = np.abs(yy + 0.5 - py)
        dx = np.minimum(dx, size - dx)
        dy = np.minimum(dy, size - dy)
        d = np.sqrt(dx * dx + dy * dy)
        closer = d < best
        second = np.where(closer, best, np.minimum(second, d))
        ids = np.where(closer, i, ids)
        best = np.where(closer, d, best)
    return best, second, ids


# ---------- Materialien ----------

def blotchy(seed, palette, scales=((2, 1.0), (4, 0.6), (8, 0.3)), speckle=0.0, speck_pal=None):
    """Calm stone/earth: soft clusters in a few colours (Ashen-like), optional speckles."""
    rng = np.random.default_rng(seed)
    f = smooth_field(rng, scales)
    idx = tidy(quant(f, len(palette)))
    out = rgba(idx, palette)
    if speckle:
        sp = (pnoise(rng, 14, size=S) > 1 - speckle)
        sp &= ~np.roll(sp, 1, 1)  # single pixels, never blobs
        sp_pal = speck_pal if speck_pal is not None else palette
        tone = (rng.random((S, S)) * len(sp_pal)).astype(int)
        out[..., :3] = np.where(sp[..., None], sp_pal[tone], out[..., :3])
    return out


def cobbled(seed, stones, mortar, n=7, moss=None):
    """Rounded stones with a lit top-left edge and a dark bottom-right edge."""
    rng = np.random.default_rng(seed)
    d, d2, ids = voronoi16(rng, n)
    gap = (d2 - d) < 0.9
    tone = (rng.random(n) * (len(stones) - 2)).astype(int)[ids] + 1
    up, left, down, right = wrap_neighbours(gap)
    idx = tone.copy()
    idx = np.where(up | left, np.minimum(idx + 1, len(stones) - 1), idx)
    idx = np.where((down | right) & ~(up | left), np.maximum(idx - 1, 0), idx)
    out = rgba(idx, stones)
    mtone = (pnoise(rng, 6, size=S) > 0.5).astype(int)
    out[..., :3] = np.where(gap[..., None], mortar[mtone], out[..., :3])
    if moss is not None:
        m = (smooth_field(rng) > 0.62) | (gap & (pnoise(rng, 5, size=S) > 0.55))
        mi = quant(pnoise(rng, 10, size=S), len(moss))
        out[..., :3] = np.where(m[..., None], moss[mi], out[..., :3])
    return out


def bricks(seed, faces, mortar, rows=4, per_row=2, offset=True, moss=None, cracked=False, chiseled=False):
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    rh = S // rows
    bw = S // per_row
    row = yy // rh
    off = (row % 2) * (bw // 2) if offset else 0
    lx = (xx + off) % bw
    ly = yy % rh
    bid = row * 7 + ((xx + off) % S) // bw
    tone = ((np.sin(bid * 12.9898 + seed) * 43758.5453) % 1 * (len(faces) - 2)).astype(int) + 1
    f = pnoise(rng, 9, size=S)
    idx = np.clip(tone + (f > 0.72).astype(int) - (f < 0.22).astype(int), 1, len(faces) - 2)
    idx = np.where(ly == 0, len(faces) - 1, idx)  # lit top edge
    idx = np.where(lx == 0, np.minimum(idx + 1, len(faces) - 1), idx)
    idx = np.where(ly == rh - 2, np.maximum(idx - 1, 0), idx)
    out = rgba(idx, faces)
    gap = (ly == rh - 1) | (lx == bw - 1)
    out[..., :3] = np.where(gap[..., None], mortar[(pnoise(rng, 5, size=S) > 0.5).astype(int)], out[..., :3])
    if cracked:
        crack = np.zeros((S, S), bool)
        x, y = int(rng.integers(3, 12)), 0
        while y < S:
            crack[y, x % S] = True
            y += 1
            x += int(rng.integers(-1, 2))
        x, y = int(rng.integers(3, 12)), int(rng.integers(6, 10))
        for _ in range(7):
            crack[y % S, x % S] = True
            x += 1
            y += int(rng.integers(-1, 2))
        out[..., :3] = np.where(crack[..., None], mortar[0], out[..., :3])
    if chiseled:
        ring = (np.maximum(np.abs(xx - 7.5), np.abs(yy - 7.5)) > 4.5) & (np.maximum(np.abs(xx - 7.5), np.abs(yy - 7.5)) < 5.6)
        out[..., :3] = np.where(ring[..., None], mortar[1], out[..., :3])
    if moss is not None:
        m = (smooth_field(rng) > 0.64) | (gap & (pnoise(rng, 6, size=S) > 0.5))
        out[..., :3] = np.where(m[..., None], moss[quant(pnoise(rng, 10, size=S), len(moss))], out[..., :3])
    return out


def planks(seed, wood, boards=4):
    """Four boards with a lit top row, a dark seam, calm grain and one butt joint each."""
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    bh = S // boards
    b = yy // bh
    ly = yy % bh
    grain = pnoise(rng, 5, size=S, aniso=(0.3, 4.0))
    base = 2 + ((np.sin(b * 3.7 + seed) * 43758.5453) % 1 > 0.5).astype(int) - 1
    idx = base + (grain > 0.68).astype(int) - (grain < 0.25).astype(int)
    idx = np.clip(idx, 1, len(wood) - 2)
    idx = np.where(ly == 0, len(wood) - 1, idx)
    idx = np.where(ly == bh - 1, 0, idx)
    joint = np.array([(i * 7 + seed * 3 + 5) % S for i in range(boards)])[b]
    idx = np.where((xx == joint) & (ly != bh - 1), 0, idx)
    idx = np.where((xx == (joint + 1) % S) & (ly > 0) & (ly < bh - 1), np.minimum(idx + 1, len(wood) - 1), idx)
    return rgba(idx, wood)


def bark(seed, palette, marks=None):
    """Vertical bark plates with deep dark cracks."""
    rng = np.random.default_rng(seed)
    f = pnoise(rng, 6, size=S, aniso=(2.4, 0.15)) * 0.7 + pnoise(rng, 3, size=S) * 0.3
    idx = quant(f, len(palette))
    crack = pnoise(rng, 5, size=S, aniso=(3.0, 0.12)) > 0.66
    idx = np.where(crack, 0, np.maximum(idx, 1))
    up, left, down, right = wrap_neighbours(crack)
    idx = np.where(right & ~crack, np.minimum(idx + 1, len(palette) - 1), idx)  # lit edge left of a crack
    out = rgba(tidy(idx), palette)
    if marks is not None:  # birch: dark horizontal marks
        m = pnoise(rng, 7, size=S, aniso=(0.2, 2.6)) > 0.78
        out[..., :3] = np.where(m[..., None], marks[(pnoise(rng, 9, size=S) > 0.5).astype(int)], out[..., :3])
    return out


def log_top(seed, wood, rim):
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    r = np.maximum(np.abs(xx - 7.5), np.abs(yy - 7.5)) * 0.65 + np.hypot(xx - 7.5, yy - 7.5) * 0.35
    rings = ((r + pnoise(rng, 4, size=S) * 1.2) // 1.6).astype(int) % 2
    idx = 2 + rings
    idx = np.where(r < 1.3, 1, idx)
    out = rgba(np.clip(idx, 0, len(wood) - 1), wood)
    edge = np.maximum(np.abs(xx - 7.5), np.abs(yy - 7.5)) > 6
    rim_idx = quant(pnoise(rng, 8, size=S), len(rim))
    out[..., :3] = np.where(edge[..., None], rim[rim_idx], out[..., :3])
    inner = (np.maximum(np.abs(xx - 7.5), np.abs(yy - 7.5)) > 5) & ~edge
    out[..., :3] = np.where(inner[..., None], wood[1], out[..., :3])
    return out


def leaves(seed, palette, holes=0.12, blossoms=None, size=S):
    """Bigger leaf clusters with soft shading (light centre, slightly darker rim) and a few see-through gaps."""
    rng = np.random.default_rng(seed)
    d, d2, ids = voronoi16(rng, max(6, int(size * size / 15)), size)
    tone = (rng.random(ids.max() + 1) * 2).astype(int)[ids] + 1
    edge = (d2 - d) < 0.75
    idx = tone.copy()
    idx = np.where(d < 1.3, np.minimum(tone + 1, len(palette) - 1), idx)
    idx = np.where(edge, np.maximum(tone - 1, 0), idx)
    alpha = np.full((size, size), 255, np.uint8)
    gap = (pnoise(rng, size * 0.35, size=size) > 1 - holes) & edge
    alpha[gap] = 0
    out = rgba(tidy(idx), palette, alpha)
    if blossoms is not None:
        bl = (pnoise(rng, size * 0.4, size=size) > 0.78) & (alpha > 0)
        bcol = blossoms[np.where(d < 1.3, len(blossoms) - 1, 1)]
        out[..., :3] = np.where(bl[..., None], bcol, out[..., :3])
    return out

def fluff(seed, palette, blossoms=None, size=24):
    """The extra outer leaf layer (24x24 so the pixels stay as big as the block's): leaves fill
    the middle, the border breaks up into single leaves so the bush silhouette is ragged."""
    out = leaves(seed + 900, palette, holes=0.2, blossoms=blossoms, size=size)
    rng = np.random.default_rng(seed + 901)
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    c = (size - 1) / 2
    edge = np.maximum(np.abs(xx - c), np.abs(yy - c)) / c
    keep = edge + (pnoise(rng, 7, size=size) - 0.5) * 0.45 < 0.9
    out[..., 3] = np.where(keep, out[..., 3], 0)
    return out


def ore(base, seed, colors, gem=False, clusters=3):
    """Chunky ore nuggets (or gems) with a dark outline and a bright corner."""
    rng = np.random.default_rng(seed)
    out = base.copy()
    shapes_blob = [[(0, 0), (1, 0), (0, 1), (1, 1)], [(0, 0), (1, 0), (2, 0), (0, 1), (1, 1)], [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)],
                   [(0, 0), (1, 0), (1, 1), (2, 1)]]
    shapes_gem = [[(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)], [(0, 0), (1, 0), (0, 1), (1, 1)], [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2), (1, 3)]]
    taken = np.zeros((S, S), bool)
    placed = 0
    tries = 0
    while placed < clusters * 2 + 1 and tries < 200:
        tries += 1
        shape = (shapes_gem if gem else shapes_blob)[int(rng.integers(0, 3 if gem else 4))]
        x0, y0 = int(rng.integers(1, S - 4)), int(rng.integers(1, S - 4))
        cells = [(x0 + dx, y0 + dy) for dx, dy in shape]
        ring = {(x + ox, y + oy) for x, y in cells for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1))} - set(cells)
        if any(taken[y, x] for x, y in cells) or any(taken[y % S, x % S] for x, y in ring):
            continue
        for x, y in ring:
            out[y % S, x % S, :3] = (out[y % S, x % S, :3] * 0.55).astype(np.uint8)
        top = min(y for _, y in cells)
        left = min(x for x, _ in cells)
        for x, y in cells:
            c = 1
            if y == top and x == left or (gem and y == top):
                c = 2
            elif y > top + 1 or x > left + 1:
                c = 0
            out[y, x, :3] = colors[c]
            taken[y, x] = True
        for x, y in ring:
            taken[y % S, x % S] = True
        placed += 1
    return out


def sand(seed, palette, ripples=True):
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    f = smooth_field(rng, ((3, 1.0), (6, 0.4)))
    if ripples:
        f = f * 0.5 + (np.sin((yy + pnoise(rng, 3, size=S) * 4) / S * math.tau * 3) * 0.5 + 0.5) * 0.5
    idx = tidy(quant(f, len(palette) - 1)) + 1
    speck = (pnoise(rng, 16, size=S) > 0.86) & ~np.roll(pnoise(rng, 16, size=S) > 0.86, 1, 1)
    idx = np.where(speck, 0, idx)
    return rgba(idx, palette)


def gravel(seed, palette, dark):
    return cobbled(seed, palette, dark, n=14)


def grass_top(seed):
    """Grey (the biome tints it): soft tufts with darker blade tips."""
    rng = np.random.default_rng(seed)
    g = pal("#5e5e5e", "#727272", "#868686", "#9a9a9a", "#ababab")
    f = smooth_field(rng, ((3, 1.0), (6, 0.5)))
    idx = quant(f, 4) + 1
    blades = pnoise(rng, 12, size=S) > 0.7
    idx = np.where(blades, idx - 1, idx)
    tips = (pnoise(rng, 12, size=S) > 0.85) & ~blades
    idx = np.where(tips, np.minimum(idx + 1, 4), idx)
    return rgba(tidy(np.clip(idx, 0, 4)), g)


def grass_side(seed, dirt_tex):
    out = dirt_tex.copy()
    rng = np.random.default_rng(seed)
    g = pal("#59792f", "#6d8f38", "#82a443")
    _, xx = grid()
    depth = 3 + (pnoise(rng, 5, size=S)[0] * 3).astype(int)
    for x in range(S):
        for y in range(depth[x] + 1):
            out[y, x, :3] = g[2 if y == 0 else 1 if y < depth[x] else 0]
    return out


def grass_overlay(seed):
    rng = np.random.default_rng(seed)
    g = pal("#7a7a7a", "#939393", "#adadad")
    out = np.zeros((S, S, 4), np.uint8)
    depth = 3 + (pnoise(rng, 5, size=S)[0] * 3).astype(int)
    for x in range(S):
        for y in range(depth[x] + 1):
            out[y, x, :3] = g[2 if y == 0 else 1 if y < depth[x] else 0]
            out[y, x, 3] = 255
        if x % 3 == 1 and depth[x] < S - 2:  # a few blades hanging lower
            out[depth[x] + 1, x, :3] = g[0]
            out[depth[x] + 1, x, 3] = 255
    return out


def liquid(seed, palette, frames=32, alpha=255):
    rng = np.random.default_rng(seed)
    a = pnoise(rng, 3, size=S)
    b = pnoise(rng, 5, size=S)
    out = []
    for f in range(frames):
        t = f / frames
        v = np.roll(a, int(round(t * S)), 0) * 0.6 + np.roll(b, int(round(t * S * 2)) % S, 1) * 0.4
        wave = np.abs(np.sin((v + t) * math.tau * 1.5))
        idx = tidy(quant(v * 0.6 + wave * 0.4, len(palette)))
        out.append(rgba(idx, palette, alpha))
    return np.concatenate(out, 0)


def glass(frame):
    yy, xx = grid()
    out = np.zeros((S, S, 4), np.uint8)
    border = (xx == 0) | (yy == 0) | (xx == S - 1) | (yy == S - 1)
    out[border] = np.append(frame[1], 255)
    out[(xx == 0) | (yy == 0)] = np.append(frame[2], 255)
    out[(xx == S - 1) | (yy == S - 1)] = np.append(frame[0], 255)
    for x, y in ((3, 3), (4, 4), (5, 5), (4, 3), (10, 11), (11, 12), (3, 4)):
        out[y, x] = np.append(frame[2], 200)
    return out


def flat(seed, color, noise=0.06, grain=False):
    """Concrete, wool base, terracotta: one colour, gentle variation."""
    rng = np.random.default_rng(seed)
    c = hx(color)
    p = np.array([np.clip(c * (1 - noise), 0, 255), c, np.clip(c * (1 + noise) + 4, 0, 255)], np.int32)
    f = smooth_field(rng, ((3, 1.0), (7, 0.4)))
    idx = tidy(quant(f, 3))
    if grain:
        g = pnoise(rng, 16, size=S) > 0.72
        idx = np.where(g, np.where(rng.random((S, S)) > 0.5, 0, 2), idx)
    return rgba(idx, p)


def wool(seed, color):
    c = hx(color)
    p = np.array([np.clip(c * 0.82, 0, 255), np.clip(c * 0.92, 0, 255), c, np.clip(c * 1.08 + 6, 0, 255)], np.int32)
    yy, xx = grid()
    weave = ((xx // 2 + yy // 2) % 2)
    rng = np.random.default_rng(seed)
    idx = 1 + weave + (pnoise(rng, 6, size=S) > 0.72).astype(int) - (pnoise(rng, 6, size=S) < 0.2).astype(int)
    idx = np.where((xx % 2 == 1) & (yy % 2 == 1), idx - 1, idx)
    return rgba(np.clip(idx, 0, 3), p)


def mineral_block(seed, palette, inset=True):
    """Solid metal/gem blocks: bevelled tile with a sheen line."""
    yy, xx = grid()
    idx = np.full((S, S), 2)
    idx = np.where((yy == 0) | (xx == 0), 4, idx)
    idx = np.where((yy == S - 1) | (xx == S - 1), 0, idx)
    if inset:
        inner = (np.maximum(np.abs(xx - 7.5), np.abs(yy - 7.5)) < 5.6) & (np.maximum(np.abs(xx - 7.5), np.abs(yy - 7.5)) > 4.4)
        idx = np.where(inner & ((yy < 8) & (xx < 8) | (yy < xx)), 1, idx)
        idx = np.where(inner & ~((yy < 8) & (xx < 8) | (yy < xx)), 3, idx)
    sheen = ((xx + yy) == 6) | ((xx + yy) == 7)
    idx = np.where(sheen & (idx == 2), 3, idx)
    return rgba(idx, palette)


def bookshelf(seed, wood):
    rng = np.random.default_rng(seed)
    out = planks(seed, wood)
    covers = pal("#7a2e2a", "#2f4f7a", "#3f6b36", "#8a6a2a", "#5a3a6a", "#2a5f5f", "#9a4a2a")
    for shelf_y in (1, 9):
        x = 1
        while x < 15:
            w = 1 if rng.random() < 0.7 else 2
            h = int(rng.integers(4, 7))
            c = covers[int(rng.integers(0, len(covers)))]
            for dx in range(w):
                if x + dx >= 15:
                    break
                for dy in range(h):
                    y = shelf_y + 5 - dy
                    shade = 1.15 if dx == 0 else 0.85
                    out[y, x + dx, :3] = np.clip(c * shade, 0, 255)
                out[shelf_y + 5 - h + 1, x + dx, :3] = np.clip(c * 1.3 + 20, 0, 255)
            x += w + (1 if rng.random() < 0.25 else 0)
        out[shelf_y + 6, 1:15, :3] = wood[0]
    return out


def lantern_tex(frame, glass_col, flame):
    """16x16 sheet for the 3D lantern model: frame metal, glowing glass panes, flame, cap and ring."""
    t = np.zeros((S, S, 4), np.uint8)

    def put(x0, y0, x1, y1, col):
        t[y0:y1, x0:x1, :3] = col
        t[y0:y1, x0:x1, 3] = 255

    put(0, 0, 8, 8, frame[1])              # metal (frame bars, cap)
    put(0, 0, 8, 1, frame[2])
    put(0, 7, 8, 8, frame[0])
    put(8, 0, 16, 8, glass_col[1])         # glass pane 6x7 with a lit inner glow
    put(9, 1, 15, 7, glass_col[2])
    put(11, 3, 13, 6, glass_col[3])
    t[0:8, 8, :3] = frame[0]
    t[0:8, 15, :3] = frame[0]
    put(0, 8, 4, 12, flame[0])             # flame
    put(1, 8, 3, 11, flame[1])
    put(4, 8, 8, 12, frame[0])             # dark (inside, ring)
    put(8, 8, 16, 12, frame[2])            # roof top
    t[8, 8:16, :3] = np.clip(frame[2] * 1.2, 0, 255)
    put(0, 12, 16, 16, frame[1])           # base plate
    return t


def _inside(poly, x, y):
    sign = 0
    for i in range(len(poly)):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % len(poly)]
        cross = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
        if abs(cross) < 1e-9:
            continue
        s = 1 if cross > 0 else -1
        if sign == 0:
            sign = s
        elif s != sign:
            return False
    return True


def cube_sky(colors, size=32, glow=True):
    """An isometric pixel cube for the sun / moon (additive blending: black = invisible).
    colors: [glow, right face, left face, top face]"""
    img = np.zeros((size, size, 4), np.uint8)
    c = size / 2
    r = size * 0.3
    w = r * 0.866
    yy, xx = np.mgrid[0:size, 0:size]
    if glow:
        d = np.hypot(xx - c + 0.5, yy - c + 0.5)
        g = np.clip(1 - d / (size * 0.5), 0, 1) ** 2 * 0.3
        for i in range(3):
            img[..., i] = (colors[0][i] * g).astype(np.uint8)
    top = [(c, c - r), (c + w, c - r / 2), (c, c), (c - w, c - r / 2)]
    left = [(c - w, c - r / 2), (c, c), (c, c + r), (c - w, c + r / 2)]
    right = [(c, c), (c + w, c - r / 2), (c + w, c + r / 2), (c, c + r)]
    for y in range(size):
        for x in range(size):
            px, py = x + 0.5, y + 0.5
            if _inside(top, px, py):
                img[y, x, :3] = colors[3]
            elif _inside(left, px, py):
                img[y, x, :3] = colors[2]
            elif _inside(right, px, py):
                img[y, x, :3] = colors[1]
    # dark edges between the faces so the cube reads as a cube
    edge = colors[1] // 2
    for y in range(size):
        for x in range(size):
            px, py = x + 0.5, y + 0.5
            on_face = _inside(top, px, py) or _inside(left, px, py) or _inside(right, px, py)
            if on_face and (abs(px - c) < 0.6 and py > c - 0.2 or abs(py - (c - r / 2 + abs(px - c) * 0.577)) < 0.55 and abs(px - c) <= w):
                img[y, x, :3] = edge
    img[..., 3] = 255
    return img

def moon_cube(phase, size=32):
    """Moon cube with craters; phase 0 = full ... 4 = new, lit part shrinks from the right."""
    base = cube_sky([np.array([120, 140, 190]), np.array([150, 160, 190]), np.array([185, 192, 215]), np.array([225, 230, 245])], size, glow=True)
    rng = np.random.default_rng(3)
    yy, xx = np.mgrid[0:size, 0:size]
    lit = base[..., :3].sum(-1) > 300
    for _ in range(7):
        cx, cy = rng.integers(10, 22, 2)
        crater = (((xx - cx) ** 2 + (yy - cy) ** 2) <= 2.5) & lit
        base[crater, :3] = (base[crater, :3] * 0.78).astype(np.uint8)
    if phase:
        frac = [0, 0.25, 0.5, 0.75, 1.0][min(phase, 4)]
        cut = size * 0.25 + (1 - frac) * size * 0.5
        shadow = (xx > cut) if phase < 4 else np.ones_like(lit)
        base[shadow & lit, :3] = (base[shadow & lit, :3] * 0.18).astype(np.uint8)
    return base


# ---------- Verzauberte Bücher (16x) ----------

EMBLEMS = {  # 5x5, "#" = emblem colour, "+" = highlight
    "sword": ["....+", "...#.", "#.#..", ".#...", "#.#.."],
    "sun": ["#.#.#", ".###.", "##+##", ".###.", "#.#.#"],
    "spider": ["#...#", ".#+#.", "##+##", ".###.", "#...#"],
    "burst": ["#.#.#", ".#+#.", "#+++#", ".#+#.", "#.#.#"],
    "flame": ["..#..", ".##..", ".#+#.", "#+++#", ".###."],
    "soul": ["..#..", ".#+..", ".#+#.", "#+++#", ".###."],
    "gem": [".###.", "#+++#", "#####", ".###.", "..#.."],
    "arc": [".###.", "#...#", "#....", ".....", "..#.."],
    "pickaxe": [".###.", "#.#.#", "..#..", "..#..", "..#.."],
    "feather": ["...##", "..#+#", ".#+#.", "#+#..", "#...."],
    "clover": [".#.#.", "##+##", ".#+#.", "##.##", "..#.."],
    "anvil": ["#####", ".###.", "..#..", ".###.", "#####"],
    "heart": [".#.#.", "#+#+#", "#+++#", ".#+#.", "..#.."],
    "shield": ["#####", "#+++#", "#+++#", ".#+#.", "..#.."],
    "shield_flame": ["#####", "#.#.#", "#+#+#", ".#+#.", "..#.."],
    "shield_burst": ["#####", "#+.+#", "#.+.#", ".#+#.", "..#.."],
    "shield_arrow": ["#####", "#..+#", "#.+.#", ".+.#.", "..#.."],
    "thorns": ["#.#.#", ".###.", "#.#.#", ".###.", "#.#.#"],
    "bubbles": ["...#.", "..#+#", "#..#.", "+#...", "#...."],
    "wave": [".#...", "#.#.#", "...#.", ".#...", "#.#.#"],
    "snowflake": ["#.#.#", ".#+#.", "#+++#", ".#+#.", "#.#.#"],
    "steps": ["##...", "##...", ".....", "...##", "...##"],
    "bow": ["##+..", "#.#..", "#..#.", "#.#..", "##+.."],
    "infinity": [".....", "##.##", "#+#+#", "##.##", "....."],
    "hook": ["..#..", "..#..", "..#..", "#.#..", ".#..."],
    "trident": ["#.#.#", "#.#.#", "#####", "..#..", "..#.."],
    "spiral": [".###.", "#...#", "#.#.#", "#.##.", ".#..."],
    "bolt": ["...#.", "..#..", ".###.", "..#..", ".#..."],
    "arrows": ["#..#.", ".#..#", "#..#.", ".#..#", "#..#."],
    "arrow": ["..###", "...##", "..#.#", ".#...", "#...."],
    "chain": ["##...", "#.#..", ".###.", "..#.#", "...##"],
    "ghost": [".###.", "#+#+#", "#####", "#####", "#.#.#"],
}


def book16(category_cols, emblem, level, max_level, gold=(242, 193, 78)):
    dark, light = (hx(c) for c in category_cols)
    darker = np.clip(dark * 0.7, 0, 255)
    page = hx("#efe2c2")
    page_d = hx("#cbb890")
    out = np.zeros((S, S, 4), np.uint8)

    def px(x, y, c):
        out[y, x, :3] = c
        out[y, x, 3] = 255

    for y in range(2, 15):  # pages on the right
        for x in (13, 14):
            px(x, y, page if (y % 2 or x == 13) else page_d)
    for y in range(1, 15):
        for x in range(2, 13):
            px(x, y, light)
    for y in range(1, 15):
        px(2, y, darker)
        px(3, y, dark)
    for x in range(2, 13):
        px(x, 1, np.clip(light * 1.2 + 10, 0, 255))
        px(x, 14, darker)
    for x in range(4, 13):
        px(x, 13, dark)
    for y in (3, 12):  # spine bands
        px(2, y, gold)
        px(3, y, gold)
    px(12, 1, gold)
    px(12, 14, gold)
    # emblem plate 7x7 with the 5x5 emblem
    for y in range(3, 10):
        for x in range(5, 12):
            if (x, y) in ((5, 3), (11, 3), (5, 9), (11, 9)):
                continue
            px(x, y, dark)
    pattern = EMBLEMS.get(emblem, EMBLEMS["burst"])
    for y, row in enumerate(pattern):
        for x, ch in enumerate(row):
            if ch == "#":
                px(6 + x, 4 + y, gold)
            elif ch == "+":
                px(6 + x, 4 + y, (255, 246, 220))
    # level pips under the plate
    if max_level > 1:
        n = min(level, 5)
        x0 = 8 - (n - 1)
        for i in range(n):
            px(x0 + i * 2, 11, gold)
    return out
