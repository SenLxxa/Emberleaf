"""Paints the HD art for the Emberleaf main menu: an autumn forest at dusk with a campfire.

Everything is generated from code with fixed seeds (our own art, reproducible). Layers are 4x the
480x270 "view units" the game code works in, and are drawn with smooth (linear) filtering.
Run:  python tools/make_menu_art.py   (needs Pillow, numpy, fonttools + brotli for the font)
"""
import io
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/client/resources/assets/emberleaf/textures/gui/menu"
FONT_OUT = ROOT / "src/client/resources/assets/emberleaf/font"
NUNITO = ROOT.parent / "emberleaf/ui/fonts/nunito.woff2"

S = 4                      # texture pixels per view unit
VIEW_W, VIEW_H = 480 * S, 270 * S
W, H = 544 * S, 270 * S    # layers are wider than the view for mouse parallax
MARGIN = (W - VIEW_W) // 2
GROUND_Y = 232 * S
FIRE_X = 318 * S           # in layer coordinates


# ---------- Helfer ----------

def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], np.float32)


def canvas(w=W, h=H):
    return np.zeros((h, w, 4), np.float32)


def over(dst, src, x=0, y=0):
    """Alpha-composites src (straight alpha) onto dst at (x, y), clipped to dst."""
    h, w = src.shape[:2]
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(dst.shape[1], x + w), min(dst.shape[0], y + h)
    if x0 >= x1 or y0 >= y1:
        return
    s = src[y0 - y:y1 - y, x0 - x:x1 - x]
    d = dst[y0:y1, x0:x1]
    sa = s[..., 3:4]
    da = d[..., 3:4]
    oa = sa + da * (1 - sa)
    rgb = (s[..., :3] * sa + d[..., :3] * da * (1 - sa)) / np.maximum(oa, 1e-6)
    d[..., :3] = rgb
    d[..., 3:4] = oa


def _box(ch, r, axis):
    if r < 1:
        return ch
    pad = [(0, 0), (0, 0)]
    pad[axis] = (r + 1, r)
    c = np.cumsum(np.pad(ch, pad, mode="edge"), axis=axis, dtype=np.float64)
    n = ch.shape[axis]
    hi = np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis)
    lo = np.take(c, np.arange(0, n), axis=axis)
    return ((hi - lo) / (2 * r + 1)).astype(np.float32)


def gauss(ch, sigma):
    """Gaussian blur of a float image via three box blurs per axis."""
    r = max(0, int(round((math.sqrt(4 * sigma * sigma + 1) - 1) / 2)))
    for _ in range(3):
        ch = _box(_box(ch, r, 0), r, 1)
    return ch


def blur(arr, radius):
    """Gaussian blur with premultiplied alpha (no dark fringes)."""
    if radius <= 0:
        return arr
    a = arr[..., 3]
    out = np.empty_like(arr)
    chans = [arr[..., i] * a for i in range(3)] + [a]
    res = [gauss(c.astype(np.float32), radius) for c in chans]
    na = np.maximum(res[3], 1e-6)
    for i in range(3):
        out[..., i] = res[i] / na
    out[..., 3] = res[3]
    return out


def save(arr, name, size=None):
    img = Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")
    if size:
        img = img.resize(size, Image.LANCZOS)
    img.save(OUT / f"{name}.png", optimize=True)
    # smooth filtering in game instead of nearest-neighbour pixels
    (OUT / f"{name}.png.mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n', encoding="utf-8")
    return img


def mask(w, h, draw_fn, ss=3):
    """Anti-aliased coverage mask from ImageDraw calls (drawn supersampled)."""
    img = Image.new("L", (w * ss, h * ss), 0)
    draw_fn(ImageDraw.Draw(img), ss)
    return np.asarray(img.resize((w, h), Image.LANCZOS), np.float32) / 255


def noise(w, h, rng, scale, octaves=3):
    """Smooth value noise in 0..1 (bilinear upscaled random grids)."""
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        cell = max(1, int(scale / (2 ** o)))
        gw, gh = w // cell + 2, h // cell + 2
        grid = Image.fromarray((rng.random((gh, gw)) * 255).astype(np.uint8), "L")
        up = np.asarray(grid.resize((gw * cell, gh * cell), Image.BICUBIC), np.float32)[:h, :w] / 255
        out += up * amp
        total += amp
        amp *= 0.5
    return out / total


def ramp(t, stops):
    """Maps t (0..1 array) through colour stops [(pos, rgb), ...]."""
    t = np.clip(t, 0, 1)[..., None]
    out = np.zeros(t.shape[:-1] + (3,), np.float32)
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        k = np.clip((t - p0) / max(1e-6, p1 - p0), 0, 1)
        inside = (t >= p0) & (t <= p1)
        out = np.where(inside, c0 + (c1 - c0) * k, out)
    out = np.where(t < stops[0][0], stops[0][1], out)
    out = np.where(t > stops[-1][0], stops[-1][1], out)
    return out


def smooth_line(values, sigma):
    k = np.exp(-np.arange(-3 * sigma, 3 * sigma + 1) ** 2 / (2 * sigma * sigma))
    k /= k.sum()
    pad = np.pad(values, (len(k) // 2, len(k) // 2), mode="edge")
    return np.convolve(pad, k, mode="valid")


# ---------- Wolken ----------

def clouds():
    rng = np.random.default_rng(5)
    r = random.Random(5)
    h = H // 2
    layer = canvas(W, h)
    for _ in range(8):
        cx, cy = r.uniform(0, W), r.uniform(50, 250)
        cw, ch = r.uniform(220, 480), r.uniform(34, 70)
        bw, bh = int(cw + 200), int(ch * 2.6 + 160)

        def puffs(d, ss, cw=cw, ch=ch, bw=bw, bh=bh):
            rr = random.Random(int(cx * 7 + cy))
            for _ in range(16):
                px = bw / 2 + rr.uniform(-cw / 2, cw / 2)
                py = bh * 0.62 + rr.uniform(-ch * 0.6, ch * 0.15)
                pr = rr.uniform(ch * 0.45, ch * 1.05) * (1 - abs(px - bw / 2) / cw * 0.6)
                d.ellipse([(px - pr * 1.25) * ss, (py - pr) * ss, (px + pr * 1.25) * ss, (py + pr * 0.8) * ss], fill=255)
            d.rectangle([(bw / 2 - cw * 0.55) * ss, bh * 0.62 * ss, (bw / 2 + cw * 0.55) * ss, (bh * 0.62 + ch * 0.25) * ss], fill=255)

        m = mask(bw, bh, puffs, ss=1)
        m = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(9)), np.float32) / 255
        yy = np.linspace(0, 1, bh, dtype=np.float32)[:, None] * np.ones((1, bw), np.float32)
        col = ramp(yy + (noise(bw, bh, rng, 40) - 0.5) * 0.25, [(0.2, hexc("#ffffff")), (0.62, hexc("#f2e6ee")), (0.9, hexc("#c9b6cc"))])
        sprite = np.concatenate([col, (m * 0.62)[..., None]], axis=2)
        over(layer, sprite, int(cx - bw / 2), int(cy - bh / 2))
    save(layer, "clouds")


# ---------- Berge ----------

def mountains(name, base, peaks, seed, cols, snow, blur_r, fog_col, wide=False):
    r = random.Random(seed)
    rng = np.random.default_rng(seed)
    x = np.arange(W, dtype=np.float32)
    line = np.zeros(W, np.float32)
    tops = []
    for _ in range(10 if not wide else 7):
        px, ph = r.uniform(-100, W + 100), r.uniform(0.45, 1.0) * peaks
        pw = r.uniform(260, 520) * (1.7 if wide else 1)
        tops.append((px, ph, pw))
        shape = np.clip(1 - np.abs(x - px) / pw, 0, 1)
        shape = shape ** 1.25 if not wide else (np.cos((1 - shape) * math.pi) * 0.5 + 0.5) * (shape > 0)
        line = np.maximum(line, ph * shape)
    rough = (noise(W, 1, rng, 30, 4)[0] - 0.5) * (40 if not wide else 14)
    line = base - smooth_line(line, 4) - rough
    deriv = np.gradient(smooth_line(line, 22))

    yy = np.arange(H, dtype=np.float32)[:, None]
    depth = yy - line[None, :]
    inside = np.clip(depth + 0.5, 0, 1)  # anti-aliased ridge edge
    body, lit, dark = (hexc(c) for c in cols)
    # sunny faces lean to the left (light from the left), with streaky rock texture
    sun = np.clip(-deriv * 2.5, -1, 1)[None, :] * np.ones((H, 1), np.float32)
    streak = gauss(noise(W, H, rng, 18, 3), 1)
    shade = np.clip(sun * np.exp(-np.maximum(depth, 0) / 260) + (streak - 0.5) * 0.5, -1, 1)
    col = np.where(shade[..., None] > 0, body + (lit - body) * shade[..., None], body + (dark - body) * (-shade[..., None]))
    if snow:
        cap = np.clip((22 + 18 * streak - depth) / 6, 0, 1) * np.clip((base - peaks * 0.5 - line[None, :]) / 40, 0, 1)
        snow_col = np.where(shade[..., None] > -0.2, hexc("#f6eef2"), hexc("#b9b0d0"))
        col = col + (snow_col - col) * cap[..., None]
    # Dunst nach unten
    fog = np.clip((depth - 40) / 380, 0, 1) ** 1.4 * 0.75
    col = col + (hexc(fog_col) - col) * fog[..., None]
    arr = np.concatenate([col, inside[..., None]], axis=2).astype(np.float32)
    save(blur(arr, blur_r), name)
    return line


# ---------- BÃ¤ume ----------

LEAF = ["#c4472c", "#e0702e", "#e6a23a", "#b8362a", "#d98b32", "#cf5a2a"]
_disc_cache = {}


def disc(radius, color, seed, light=1.0, haze=None):
    """A sphere-shaded clump of leaves with a ragged, leafy edge."""
    key = (radius, color, seed % 6, light, haze)
    if key in _disc_cache:
        return _disc_cache[key]
    rng = np.random.default_rng(seed)
    size = radius * 2 + 4
    c = size / 2
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    dx, dy = (xx - c) / radius, (yy - c) / radius
    d = np.sqrt(dx * dx + dy * dy)
    dz = np.sqrt(np.clip(1 - d * d, 0, 1))
    lx, ly, lz = -0.5, -0.72, 0.48
    leafy = noise(size, size, rng, max(2, radius / 7), 3)
    inten = dx * lx + dy * ly + dz * lz + (leafy - 0.5) * 0.7
    base = hexc(color)
    stops = [(0.0, base * np.array([0.34, 0.30, 0.42], np.float32)), (0.35, base * 0.62), (0.62, base * 0.95),
             (0.86, np.minimum(1, base * 1.18 + 0.05)), (1.0, np.minimum(1, base * 1.3 + 0.12))]
    col = ramp((inten + 0.55) / 1.4 * light, stops)
    if haze is not None:
        col = col + (hexc(haze[0]) - col) * haze[1]
    edge = 1 - d - (leafy - 0.5) * 0.28
    alpha = np.clip(edge * radius / 2.2, 0, 1)
    sprite = np.concatenate([col, alpha[..., None]], axis=2).astype(np.float32)
    _disc_cache[key] = sprite
    return sprite


def trunk(layer, cx, ground, top, width, haze=None):
    tw0, tw1 = width, width * 0.55
    h = ground - top
    w = int(width * 3)

    def shape(d, ss):
        pts = [((w / 2 - tw0 / 2 - width * 0.4) * ss, h * ss), ((w / 2 - tw1 / 2) * ss, 0), ((w / 2 + tw1 / 2) * ss, 0),
               ((w / 2 + tw0 / 2 + width * 0.4) * ss, h * ss)]
        d.polygon(pts, fill=255)

    m = mask(w, int(h), shape, ss=2)
    xx = np.linspace(0, 1, w, dtype=np.float32)[None, :] * np.ones((int(h), 1), np.float32)
    bark = ramp(xx + (noise(w, int(h), np.random.default_rng(abs(int(cx)) + 7), 6, 2) - 0.5) * 0.4,
                [(0.25, hexc("#1e130c")), (0.5, hexc("#3b2618")), (0.72, hexc("#5a3c26"))])
    if haze is not None:
        bark = bark + (hexc(haze[0]) - bark) * haze[1]
    over(layer, np.concatenate([bark, m[..., None]], axis=2), int(cx - w / 2), int(top))


def tree(layer, cx, ground, height, radius, color, seed, light=1.0, haze=None):
    r = random.Random(seed)
    crown_y = ground - height + radius
    trunk(layer, cx, ground, crown_y, max(6, radius // 5), haze)
    blobs = []
    for i in range(r.randint(18, 26)):
        a = r.uniform(0, math.tau)
        dist = r.uniform(0, 0.85) ** 0.7
        bx = cx + math.cos(a) * radius * dist * 1.1
        by = crown_y + math.sin(a) * radius * dist * 0.9
        br = int(radius * r.uniform(0.3, 0.52))
        blobs.append((by, bx, br))
    blobs.sort()  # top clumps first, lower ones overlap them like real crowns
    for k, (by, bx, br) in enumerate(blobs):
        tint = color if r.random() < 0.8 else r.choice(LEAF)
        sprite = disc(br, tint, seed * 31 + k, light, haze)
        over(layer, sprite, int(bx - sprite.shape[1] / 2), int(by - sprite.shape[0] / 2))


def forest(name, count, ground, heights, radii, light, seed, haze, blur_r, edges=False):
    layer = canvas()
    r = random.Random(seed)
    xs = []
    for _ in range(count):
        if edges:
            xs.append(r.choice([r.uniform(-100, 640), r.uniform(1640, W + 100)]))
        else:
            xs.append(r.uniform(-60, W + 60))
    for i, x in enumerate(sorted(xs, key=lambda _: r.random())):
        tree(layer, int(x), ground + r.randint(-8, 10), r.randint(*heights), r.randint(*radii), r.choice(LEAF), seed * 100 + i, light, haze)
    save(blur(layer, blur_r), name)


# ---------- Boden ----------

def ground():
    ss = 2
    w, h = W * ss, H * ss
    rng = np.random.default_rng(77)
    r = random.Random(77)
    xs = np.arange(W, dtype=np.float32)
    wave = GROUND_Y - 26 + smooth_line((noise(W, 1, rng, 120, 3)[0] - 0.5) * 30, 10)
    yy = np.arange(H, dtype=np.float32)[:, None]
    depth = yy - wave[None, :]
    t = np.clip(depth / (H - GROUND_Y + 40), 0, 1)
    grass_noise = noise(W, H, rng, 10, 3)
    col = ramp(t + (grass_noise - 0.5) * 0.35, [(0.0, hexc("#9a9142")), (0.3, hexc("#76762f")), (0.7, hexc("#525a24")), (1.0, hexc("#353c17"))])
    # golden light from the fire on the meadow
    fx = FIRE_X
    dist = np.sqrt(((xs[None, :] - fx) / 520) ** 2 + ((yy - GROUND_Y) / 160) ** 2)
    col = col + (hexc("#c99a45") - col) * (np.clip(1 - dist, 0, 1) ** 2 * 0.45)[..., None]
    alpha = np.clip(depth + 0.5, 0, 1)
    base = np.concatenate([col, alpha[..., None]], axis=2)
    img = Image.fromarray((np.clip(base, 0, 1) * 255).astype(np.uint8), "RGBA").resize((w, h), Image.BICUBIC)
    d = ImageDraw.Draw(img, "RGBA")

    # Trampelpfad
    path = Image.new("L", (w, h), 0)
    pd = ImageDraw.Draw(path)
    pts = []
    for k in range(41):
        s = k / 40
        y = (GROUND_Y - 6 + s * (H - GROUND_Y + 10)) * ss
        cx = (fx - 120 - s * 260) * ss
        half = (20 + s * 150) * ss
        pts.append((cx - half, y))
        pts.insert(0, (cx + half, y))
    pd.polygon(pts, fill=255)
    path = path.filter(ImageFilter.GaussianBlur(10 * ss))
    path_col = Image.new("RGBA", (w, h), (122, 92, 60, 255))
    img = Image.composite(path_col, img, path.point(lambda v: int(v * 0.85)))
    d = ImageDraw.Draw(img, "RGBA")

    # Grashalme
    for _ in range(26000):
        x = r.uniform(0, W)
        top = wave[int(min(W - 1, max(0, x)))]
        y = r.uniform(top, H)
        depth_k = (y - top) / (H - top)
        length = (4 + depth_k * 22) * r.uniform(0.6, 1.3)
        lean = r.uniform(-0.5, 0.5) * length
        g = r.choice([(150, 146, 66), (120, 124, 52), (96, 108, 40), (178, 160, 78), (84, 96, 34)])
        shade_k = 0.75 + depth_k * 0.1 + r.uniform(-0.1, 0.15)
        c = tuple(int(v * shade_k) for v in g) + (230,)
        d.line([(x * ss, y * ss), ((x + lean) * ss, (y - length) * ss)], fill=c, width=max(1, int((1 + depth_k * 2.5) * ss / 2)))
    # Laub am Boden
    for _ in range(2600):
        x = r.uniform(0, W)
        top = wave[int(min(W - 1, max(0, x)))]
        y = r.uniform(top + 2, H)
        near_tree = x < 700 or x > 1600
        if not near_tree and r.random() < 0.55:
            continue
        s = (3 + (y - top) / (H - top) * 9) * r.uniform(0.7, 1.3)
        a = r.uniform(0, math.pi)
        c = hexc(r.choice(LEAF)) * r.uniform(0.75, 1.15)
        fill = tuple(int(min(255, v * 255)) for v in c) + (240,)
        pts = [(x + math.cos(a + k * math.pi / 2) * s * (1 if k % 2 == 0 else 0.5), y + math.sin(a + k * math.pi / 2) * s * 0.55 * (1 if k % 2 == 0 else 0.5)) for k in range(4)]
        d.polygon([(px * ss, py * ss) for px, py in pts], fill=fill)

    # Steinring
    for i in range(13):
        a = math.pi * (i / 12) * 1.0 + math.pi
        sx = fx + math.cos(a) * 64
        sy = GROUND_Y + 6 + math.sin(a) * -14
        rw, rh = r.uniform(11, 15), r.uniform(8, 10)
        d.ellipse([(sx - rw) * ss, (sy - rh) * ss, (sx + rw) * ss, (sy + rh) * ss], fill=(98, 90, 82, 255))
        d.ellipse([(sx - rw * 0.75) * ss, (sy - rh * 0.95) * ss, (sx + rw * 0.55) * ss, (sy + rh * 0.2) * ss], fill=(150, 140, 126, 255))
        d.ellipse([(sx - rw * 0.4) * ss, (sy - rh * 0.8) * ss, (sx + rw * 0.1) * ss, (sy - rh * 0.3) * ss], fill=(190, 180, 164, 200))
    for i in range(13):
        a = math.pi * (i / 12)
        sx = fx + math.cos(a) * 64
        sy = GROUND_Y + 6 + math.sin(a) * 14
        rw, rh = r.uniform(12, 16), r.uniform(9, 11)
        d.ellipse([(sx - rw) * ss, (sy - rh) * ss, (sx + rw) * ss, (sy + rh) * ss], fill=(88, 80, 74, 255))
        d.ellipse([(sx - rw * 0.8) * ss, (sy - rh) * ss, (sx + rw * 0.5) * ss, (sy + rh * 0.1) * ss], fill=(160, 150, 134, 255))
    # Holzscheite im Feuer
    for dxs, ang in ((-1, -0.42), (1, 0.42), (0, 0.05)):
        ln, th = 70, 11
        cx, cy = fx + dxs * 14, GROUND_Y - 4
        ca, sa = math.cos(ang), math.sin(ang)
        corners = [(-ln / 2, -th), (ln / 2, -th), (ln / 2, th), (-ln / 2, th)]
        pts = [((cx + px * ca - py * sa) * ss, (cy + px * sa + py * ca) * ss) for px, py in corners]
        d.polygon(pts, fill=(70, 44, 26, 255))
        d.line(pts[:2], fill=(120, 80, 48, 255), width=3 * ss)
        ex, ey = cx + (ln / 2) * ca, cy + (ln / 2) * sa
        d.ellipse([(ex - 7) * ss, (ey - 10) * ss, (ex + 7) * ss, (ey + 10) * ss], fill=(204, 160, 104, 255))
        d.ellipse([(ex - 3) * ss, (ey - 5) * ss, (ex + 3) * ss, (ey + 5) * ss], fill=(160, 112, 66, 255))
    # Sitzstamm links vom Feuer
    lx0, lx1, ly = fx - 330, fx - 160, GROUND_Y - 6
    d.rounded_rectangle([lx0 * ss, (ly - 22) * ss, lx1 * ss, (ly + 8) * ss], radius=14 * ss, fill=(78, 50, 30, 255))
    d.rounded_rectangle([lx0 * ss, (ly - 22) * ss, lx1 * ss, (ly - 12) * ss], radius=8 * ss, fill=(112, 76, 46, 255))
    for k in range(9):
        bx = lx0 + 18 + k * 17
        d.line([(bx * ss, (ly - 16) * ss), ((bx + 10) * ss, (ly + 4) * ss)], fill=(54, 34, 20, 255), width=2 * ss)
    d.ellipse([(lx1 - 12) * ss, (ly - 22) * ss, (lx1 + 12) * ss, (ly + 8) * ss], fill=(206, 164, 108, 255))
    d.ellipse([(lx1 - 6) * ss, (ly - 13) * ss, (lx1 + 6) * ss, (ly - 1) * ss], fill=(166, 120, 72, 255))
    # Pilze
    for mx in (fx - 470, fx + 280, fx + 600, fx - 860):
        my = wave[int(mx)] + 18
        d.rectangle([(mx - 3) * ss, (my - 12) * ss, (mx + 3) * ss, my * ss], fill=(236, 226, 204, 255))
        d.pieslice([(mx - 13) * ss, (my - 24) * ss, (mx + 13) * ss, (my - 4) * ss], 180, 360, fill=(196, 55, 44, 255))
        for sx2, sy2 in ((-6, -16), (4, -19), (7, -13)):
            d.ellipse([(mx + sx2 - 2) * ss, (my + sy2 - 2) * ss, (mx + sx2 + 2) * ss, (my + sy2 + 2) * ss], fill=(250, 240, 220, 255))
    img = img.resize((W, H), Image.LANCZOS)
    img.save(OUT / "ground.png", optimize=True)
    (OUT / "ground.png.mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n', encoding="utf-8")


# ---------- Sprites ----------

def fire():
    fw, fh, frames, ss = 80, 112, 8, 4
    sheet = Image.new("RGBA", (fw * frames, fh))
    for f in range(frames):
        r = random.Random(900 + f)
        layer = Image.new("RGBA", (fw * ss, fh * ss))
        for scale_k, col in ((1.0, (232, 84, 42)), (0.74, (255, 150, 52)), (0.48, (255, 214, 110)), (0.26, (255, 246, 210))):
            tongue = Image.new("L", (fw * ss, fh * ss), 0)
            d = ImageDraw.Draw(tongue)
            for k in range(5):
                base_x = fw / 2 + (k - 2) * 7 * scale_k
                height = (fh - 14) * scale_k * r.uniform(0.62, 1.0) * (1.0 if k == 2 else 0.8)
                width = 15 * scale_k * r.uniform(0.8, 1.15)
                phase = f / frames * math.tau + k
                left, right = [], []
                for i in range(25):
                    s = i / 24
                    y = fh - 6 - s * height
                    half = width * (1 - s) ** 0.9 * min(1, s * 5 + 0.25)
                    cx = base_x + math.sin(phase + s * 3.2) * s * 7
                    left.append(((cx - half) * ss, y * ss))
                    right.append(((cx + half) * ss, y * ss))
                d.polygon(left + right[::-1], fill=255)
            tongue = tongue.filter(ImageFilter.GaussianBlur(2.2 * ss))
            solid = Image.new("RGBA", tongue.size, col + (255,))
            layer = Image.composite(solid, layer, tongue)
            layer.putalpha(Image.eval(Image.merge("L", [layer.getchannel("A")]).getchannel(0), lambda v: v))
        frame = layer.resize((fw, fh), Image.LANCZOS)
        sheet.alpha_composite(frame, (f * fw, 0))
    sheet.save(OUT / "fire.png", optimize=True)
    (OUT / "fire.png.mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n', encoding="utf-8")


def glow(size=256):
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    d = np.sqrt((xx + 0.5 - size / 2) ** 2 + (yy + 0.5 - size / 2) ** 2) / (size / 2)
    a = np.clip(1 - d, 0, 1) ** 2.2
    save(np.concatenate([np.ones((size, size, 3), np.float32), a[..., None]], axis=2), "glow")


def leaves(cell=64):
    colors = ["#e0702e", "#c4472c", "#e6a23a", "#b8362a"]
    sheet = Image.new("RGBA", (cell * 4, cell * 4))
    for si in range(4):
        for ci, c in enumerate(colors):
            img = Image.new("RGBA", (cell * 4, cell * 4))
            d = ImageDraw.Draw(img)
            k = cell * 4
            cx, cy = k / 2, k / 2
            base = tuple(int(v * 255) for v in hexc(c))
            if si == 0:  # Ahornblatt
                pts = []
                for i in range(181):
                    a = i / 180 * math.tau
                    lobes = 0.62 + 0.38 * abs(math.cos(a * 2.5)) ** 0.6
                    rr = k * 0.42 * lobes * (0.82 + 0.18 * math.cos(a * 15) ** 2)
                    pts.append((cx + math.sin(a) * rr, cy - math.cos(a) * rr * 0.95))
            elif si == 1:  # ovales Blatt
                pts = [(cx + math.sin(i / 90 * math.pi) * k * 0.2, cy - k * 0.42 + i / 90 * k * 0.84) for i in range(91)]
                pts += [(cx - math.sin(i / 90 * math.pi) * k * 0.2, cy + k * 0.42 - i / 90 * k * 0.84) for i in range(91)]
            elif si == 2:  # Eichenblatt
                pts = []
                for i in range(181):
                    s = i / 180
                    y = cy - k * 0.4 + s * k * 0.8
                    w = k * 0.2 * math.sin(s * math.pi) * (0.75 + 0.25 * abs(math.sin(s * math.pi * 4)))
                    pts.append((cx + w, y))
                pts += [(cx - (p[0] - cx), p[1]) for p in reversed(pts)]
            else:  # Birkenblatt
                pts = [(cx + math.sin(i / 90 * math.pi) * k * 0.28 * (1 - i / 140), cy - k * 0.38 + i / 90 * k * 0.76) for i in range(91)]
                pts += [(cx - (p[0] - cx), p[1]) for p in reversed(pts)]
            d.polygon(pts, fill=base + (255,))
            light = tuple(min(255, int(v * 1.25 + 20)) for v in base)
            dark = tuple(int(v * 0.6) for v in base)
            d.line([(cx, cy - k * 0.36), (cx, cy + k * 0.45)], fill=dark + (255,), width=k // 40)
            for side in (-1, 1):
                for j in range(3):
                    y0 = cy - k * 0.15 + j * k * 0.13
                    d.line([(cx, y0), (cx + side * k * 0.16, y0 - k * 0.1)], fill=dark + (200,), width=k // 70)
            d.polygon([(cx, cy - k * 0.36), (cx - k * 0.05, cy - k * 0.05), (cx, cy)], fill=light + (120,))
            sheet.alpha_composite(img.resize((cell, cell), Image.LANCZOS), (si * cell, ci * cell))
    sheet.save(OUT / "leaves.png", optimize=True)
    (OUT / "leaves.png.mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n', encoding="utf-8")


def moon(size=176):
    rng = np.random.default_rng(3)
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    c = size / 2
    d = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / (c - 2)
    n = noise(size, size, rng, 18, 4)
    base = ramp(n, [(0.2, hexc("#cfc4a8")), (0.55, hexc("#efe6cc")), (0.9, hexc("#fff8e6"))])
    limb = np.clip(1 - d ** 6 * 0.35, 0, 1)
    col = base * limb[..., None]
    a = np.clip((1 - d) * c / 1.5, 0, 1)
    save(np.concatenate([col, a[..., None]], axis=2), "moon")


# ---------- Logo & KnÃ¶pfe ----------

def nunito(weight):
    from fontTools.ttLib import TTFont
    from fontTools.varLib import instancer

    font = TTFont(str(NUNITO))
    static = instancer.instantiateVariableFont(font, {"wght": weight})
    buf = io.BytesIO()
    static.flavor = None
    static.save(buf)
    return buf.getvalue(), font


def leaf_icon(size):
    k = size * 3
    img = Image.new("RGBA", (k, k))
    d = ImageDraw.Draw(img)
    cx, cy = k / 2, k * 0.47
    pts = []
    for i in range(361):
        a = i / 360 * math.tau
        lobes = 0.6 + 0.4 * abs(math.cos(a * 2.5)) ** 0.55
        rr = k * 0.4 * lobes * (0.86 + 0.14 * math.cos(a * 15) ** 2)
        pts.append((cx + math.sin(a) * rr, cy - math.cos(a) * rr * 0.95))
    m = Image.new("L", (k, k), 0)
    ImageDraw.Draw(m).polygon(pts, fill=255)
    grad = np.zeros((k, k, 3), np.float32)
    t = np.linspace(0, 1, k, dtype=np.float32)[:, None] * np.ones((1, k), np.float32)
    grad[:] = ramp(t, [(0.1, hexc("#ffd36e")), (0.5, hexc("#ff8a34")), (0.95, hexc("#c4372c"))])
    gimg = Image.fromarray((grad * 255).astype(np.uint8), "RGB").convert("RGBA")
    img = Image.composite(gimg, img, m)
    d = ImageDraw.Draw(img)
    vein = (150, 52, 24, 210)
    d.line([(cx, cy + k * 0.36), (cx, cy - k * 0.3)], fill=vein, width=k // 45)
    for a in (-0.9, 0.9, -2.0, 2.0):
        d.line([(cx, cy + k * 0.05), (cx + math.sin(a) * k * 0.3, cy - math.cos(a) * k * 0.3)], fill=vein, width=k // 60)
    d.line([(cx, cy + k * 0.36), (cx + k * 0.02, cy + k * 0.5)], fill=(110, 60, 30, 255), width=k // 30)
    return img.resize((size, size), Image.LANCZOS)


def logo(bold_ttf):
    size = 150
    font = ImageFont.truetype(io.BytesIO(bold_ttf), size)
    text = "Emberleaf"
    bbox = font.getbbox(text, stroke_width=7)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    icon = 190
    pad = 34
    w, h = icon + 18 + tw + pad * 2, max(th, icon) + pad * 2
    ox, oy = pad + icon + 18 - bbox[0], (h - th) // 2 - bbox[1]

    def text_mask(stroke=0):
        m = Image.new("L", (w, h), 0)
        ImageDraw.Draw(m).text((ox, oy), text, font=font, fill=255, stroke_width=stroke, stroke_fill=255)
        return m

    fill_m, outline_m = text_mask(0), text_mask(7)
    out = Image.new("RGBA", (w, h))
    # weicher Schatten und Glut-Schein
    shadow = Image.new("RGBA", (w, h), (20, 8, 4, 0))
    shadow.putalpha(outline_m.filter(ImageFilter.GaussianBlur(10)).point(lambda v: int(v * 0.8)))
    out.alpha_composite(shadow, (6, 9))
    glow_l = Image.new("RGBA", (w, h), (255, 130, 40, 0))
    glow_l.putalpha(outline_m.filter(ImageFilter.GaussianBlur(22)).point(lambda v: int(v * 0.45)))
    out.alpha_composite(glow_l)
    edge = Image.new("RGBA", (w, h), (52, 20, 8, 255))
    out = Image.composite(edge, out, outline_m)
    t = np.clip((np.arange(h, dtype=np.float32)[:, None] - (oy + bbox[1])) / th, 0, 1) * np.ones((1, w), np.float32)
    grad = ramp(t, [(0.0, hexc("#fff2c0")), (0.35, hexc("#ffc45a")), (0.7, hexc("#ff8a34")), (1.0, hexc("#d2451f"))])
    gimg = Image.fromarray((grad * 255).astype(np.uint8), "RGB").convert("RGBA")
    out = Image.composite(gimg, out, fill_m)
    # Glanzkante oben
    shine = text_mask(0).filter(ImageFilter.GaussianBlur(1))
    shine_np = np.asarray(shine, np.float32) / 255
    below = np.roll(shine_np, 5, axis=0)
    edge_shine = np.clip(shine_np - below, 0, 1) * 0.55
    sh = Image.new("RGBA", (w, h), (255, 252, 236, 0))
    sh.putalpha(Image.fromarray((edge_shine * 255).astype(np.uint8)))
    out.alpha_composite(sh)
    li = leaf_icon(icon)
    lshadow = Image.new("RGBA", (icon, icon), (20, 8, 4, 0))
    lshadow.putalpha(li.getchannel("A").filter(ImageFilter.GaussianBlur(8)).point(lambda v: int(v * 0.7)))
    out.alpha_composite(lshadow, (pad + 5, (h - icon) // 2 + 8))
    out.alpha_composite(li, (pad, (h - icon) // 2))
    out.save(OUT / "logo.png", optimize=True)
    (OUT / "logo.png.mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n', encoding="utf-8")
    print(f"logo: {w}x{h}")


def panel(name, top, bottom, border, highlight, ember=False, size=96, radius=22):
    ss = 4
    k = size * ss
    m = Image.new("L", (k, k), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, k - 1, k - 1], radius=radius * ss, fill=255)
    inner = Image.new("L", (k, k), 0)
    bw = 3 * ss
    ImageDraw.Draw(inner).rounded_rectangle([bw, bw, k - 1 - bw, k - 1 - bw], radius=(radius - 3) * ss, fill=255)
    t = np.linspace(0, 1, k, dtype=np.float32)[:, None] * np.ones((1, k), np.float32)
    col = ramp(t, [(0.0, hexc(top[0])), (1.0, hexc(bottom[0]))])
    a_fill = top[1] + (bottom[1] - top[1]) * t
    if ember:
        xx = np.linspace(0, 1, k, dtype=np.float32)[None, :] * np.ones((k, 1), np.float32)
        warm = np.clip(1 - xx * 1.6, 0, 1) ** 2
        col = col + (hexc("#b8501c") - col) * (warm * 0.5)[..., None]
    fill = np.concatenate([col, a_fill[..., None]], axis=2)
    img = Image.fromarray((np.clip(fill, 0, 1) * 255).astype(np.uint8), "RGBA")
    border_img = Image.new("RGBA", (k, k), tuple(int(v * 255) for v in hexc(border[0])) + (int(border[1] * 255),))
    out = Image.new("RGBA", (k, k))
    out = Image.composite(border_img, out, m)
    out = Image.composite(img, out, inner)
    hl = Image.new("L", (k, k), 0)
    ImageDraw.Draw(hl).rounded_rectangle([bw, bw, k - 1 - bw, bw + 2 * ss + radius * ss], radius=(radius - 3) * ss, fill=255)
    hl_np = np.asarray(hl, np.float32) / 255 * np.clip(1 - t * k / (radius * ss + 2 * ss), 0, 1) * highlight
    hl_img = Image.new("RGBA", (k, k), (255, 240, 220, 0))
    hl_img.putalpha(Image.fromarray((hl_np * 255).astype(np.uint8)))
    out.alpha_composite(hl_img)
    out = out.resize((size, size), Image.LANCZOS)
    out.save(OUT / f"{name}.png", optimize=True)
    (OUT / f"{name}.png.mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n', encoding="utf-8")


def white_round(size=96, radius=22):
    """A white rounded rectangle; drawn nine-sliced and tinted it gives pills, switches and badges in any colour."""
    ss = 4
    k = size * ss
    m = Image.new("L", (k, k), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, k - 1, k - 1], radius=radius * ss, fill=255)
    out = Image.new("RGBA", (k, k), (255, 255, 255, 0))
    out.putalpha(m)
    out = out.resize((size, size), Image.LANCZOS)
    OUT.mkdir(parents=True, exist_ok=True)
    out.save(OUT / "round.png", optimize=True)
    (OUT / "round.png.mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n', encoding="utf-8")


def fonts():
    FONT_OUT.mkdir(parents=True, exist_ok=True)
    bold, src = nunito(800)
    medium, _ = nunito(650)
    (FONT_OUT / "nunito-menu.ttf").write_bytes(medium)
    (FONT_OUT / "nunito-title.ttf").write_bytes(bold)
    name = src["name"]
    info = [name.getDebugName(i) for i in (0, 13, 14)]
    (FONT_OUT / "nunito-license.txt").write_text(
        "Nunito font, converted to TTF for the Emberleaf main menu.\n\n" + "\n\n".join(x for x in info if x) + "\n", encoding="utf-8")
    (FONT_OUT / "menu.json").write_text(
        '{"providers": [{"type": "ttf", "file": "emberleaf:nunito-menu.ttf", "size": 9.5, "oversample": 5.0, "shift": [0.0, 0.5]}]}\n',
        encoding="utf-8")
    (FONT_OUT / "title.json").write_text(
        '{"providers": [{"type": "ttf", "file": "emberleaf:nunito-title.ttf", "size": 11.0, "oversample": 5.0, "shift": [0.0, 0.0]}]}\n',
        encoding="utf-8")
    return bold


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.png"):
        old.unlink()
    bold = fonts()
    # the menu background is now the 3D island (tools/make_island.py); only sky pieces are painted here
    clouds()
    glow()
    leaves()
    moon()
    logo(bold)
    panel("button", ("#2e2119", 0.88), ("#1a120d", 0.9), ("#0b0806", 0.92), 0.16)
    panel("button_hover", ("#5a3a22", 0.95), ("#2c1b10", 0.95), ("#1a0c06", 0.95), 0.22, ember=True)
    panel("panel", ("#24190f", 0.80), ("#160f0a", 0.86), ("#0b0806", 0.85), 0.12)
    white_round()
    for p in sorted(OUT.glob("*.png")):
        with Image.open(p) as im:
            print(f"{p.name}: {im.width}x{im.height}, {p.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
