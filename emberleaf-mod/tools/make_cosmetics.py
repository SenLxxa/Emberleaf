"""Builds Emberleaf's HD starter cosmetics as real Blockbench files (.bbmodel).

Every model follows the Emberleaf convention: the player stands with the feet at y=0 and looks
north (-z); body 12..24, head 24..32 (pixels). Textures are painted at 2 texels per model pixel.
Open the files in Blockbench ("Generic Model") to use them as templates.
Run:  python tools/make_cosmetics.py   (needs Pillow + numpy)
"""
import base64
import io
import json
import math
import uuid
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT.parent / "emberleaf/ui/assets/models"
D = 2  # texels per model pixel
FACES = ("north", "east", "south", "west", "up", "down")


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def noise(w, h, seed, cell=3):
    rng = np.random.default_rng(seed)
    g = rng.random((h // cell + 2, w // cell + 2)).astype(np.float32)
    up = Image.fromarray((g * 255).astype(np.uint8)).resize(((w // cell + 2) * cell, (h // cell + 2) * cell), Image.BICUBIC)
    return np.asarray(up, np.float32)[:h, :w] / 255


def gradient_img(w, h, top, bottom, seed=0, grain=0.12):
    t = np.linspace(0, 1, h, dtype=np.float32)[:, None] * np.ones((1, w), np.float32)
    n = noise(w, h, seed) - 0.5
    a, b = np.array(hexc(top), np.float32), np.array(hexc(bottom), np.float32)
    col = a + (b - a) * t[..., None]
    col = col * (1 + n[..., None] * grain * 2)
    return Image.fromarray(np.clip(col, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


# ---------- Maler für einzelne Flächen (w, h in Texeln) ----------

def knit(base, dark, stripe=None):
    def paint(w, h, face, seed):
        img = gradient_img(w, h, base, base, seed, 0.05)
        d = ImageDraw.Draw(img)
        dk = hexc(dark)
        for x in range(0, w, 3):  # knitted ribs
            d.line([(x, 0), (x, h)], fill=dk + (110,), width=1)
        for y in range(1, h, 3):
            for x in range(0, w, 3):
                d.point((x + 1, y), fill=tuple(min(255, c + 30) for c in hexc(base)) + (255,))
        if stripe and face not in ("up", "down"):
            sy = h // 2
            d.rectangle([0, sy - 2, w, sy + 1], fill=hexc(stripe) + (255,))
        return img
    return paint


def solid(base, dark=None, grain=0.1):
    def paint(w, h, face, seed):
        return gradient_img(w, h, base, dark or base, seed, grain)
    return paint


def leaf(base, tip, vein):
    def paint(w, h, face, seed):
        img = gradient_img(w, h, base, tip, seed, 0.08)
        if face in ("north", "south"):
            d = ImageDraw.Draw(img)
            v = hexc(vein) + (200,)
            d.line([(w * 0.1, h * 0.5), (w * 0.95, h * 0.5)], fill=v, width=max(1, h // 14))
            for k in range(1, 5):
                x = w * k / 5
                d.line([(x, h * 0.5), (x + w * 0.12, h * 0.15)], fill=v, width=1)
                d.line([(x, h * 0.5), (x + w * 0.12, h * 0.85)], fill=v, width=1)
        return img
    return paint


def fabric(base, dark, trim=None, embers=False):
    def paint(w, h, face, seed):
        img = gradient_img(w, h, base, dark, seed, 0.1)
        d = ImageDraw.Draw(img)
        for y in range(0, h, 2):  # woven texture
            d.line([(0, y), (w, y)], fill=hexc(dark) + (40,))
        if trim and face in ("north", "south"):
            t = hexc(trim) + (255,)
            d.rectangle([0, 0, w - 1, 1], fill=t)
            d.rectangle([0, h - 2, w - 1, h - 1], fill=t)
            d.rectangle([0, 0, 1, h - 1], fill=t)
            d.rectangle([w - 2, 0, w - 1, h - 1], fill=t)
        if embers and face == "south":
            rng = np.random.default_rng(seed)
            for _ in range(18):
                x, y = rng.integers(3, w - 3), rng.integers(4, h - 3)
                d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(255, 180, 80, 255))
        return img
    return paint


def fur(base, light, dark):
    def paint(w, h, face, seed):
        img = gradient_img(w, h, light if face == "up" else base, dark if face == "down" else base, seed, 0.18)
        d = ImageDraw.Draw(img)
        rng = np.random.default_rng(seed)
        for _ in range(w * h // 6):
            x, y = rng.integers(0, w), rng.integers(0, h)
            d.line([(x, y), (x + rng.integers(-1, 2), y + 2)], fill=hexc(rng.choice([light, dark])) + (120,))
        return img
    return paint


def membrane(base, edge):
    def paint(w, h, face, seed):
        img = gradient_img(w, h, base, edge, seed, 0.1)
        d = ImageDraw.Draw(img)
        if face in ("north", "south"):
            for k in range(1, 4):
                x = w * k / 4
                d.line([(x, 0), (x * 0.6, h)], fill=hexc(edge) + (160,), width=1)
        return img
    return paint


# ---------- Modell-Baukasten ----------

class Model:
    def __init__(self, name, size=256):
        self.name = name
        self.size = size
        self.tex = Image.new("RGBA", (size, size))
        self.elements = []
        self.groups = {}
        self.outliner = []
        self.cursor = [0, 0, 0]  # shelf packer: x, y, row height
        self.seed = 1

    def group(self, name, origin, parent=None, rotation=(0, 0, 0)):
        g = {"name": name, "origin": list(origin), "rotation": list(rotation), "uuid": str(uuid.uuid4()), "export": True,
             "isOpen": True, "visibility": True, "children": []}
        self.groups[name] = g
        (self.groups[parent]["children"] if parent else self.outliner).append(g)
        return name

    def _place(self, w, h):
        x, y, row = self.cursor
        if x + w > self.size:
            x, y, row = 0, y + row, 0
        if y + h > self.size:
            raise ValueError(f"{self.name}: texture full")
        self.cursor = [x + w, y, max(row, h)]
        return x, y

    def cube(self, group, frm, to, paint, origin=None, rotation=(0, 0, 0), name="cube", inflate=0):
        fx, fy, fz = frm
        tx, ty, tz = to
        sx, sy, sz = tx - fx, ty - fy, tz - fz
        sizes = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}
        faces = {}
        for face in FACES:
            w, h = max(1, round(sizes[face][0] * D)), max(1, round(sizes[face][1] * D))
            x, y = self._place(w, h)
            self.seed += 1
            self.tex.paste(paint(w, h, face, self.seed), (x, y))
            faces[face] = {"uv": [x, y, x + w, y + h], "texture": 0}
        el = {"name": name, "type": "cube", "uuid": str(uuid.uuid4()), "from": list(frm), "to": list(to),
              "origin": list(origin or [(fx + tx) / 2, (fy + ty) / 2, (fz + tz) / 2]), "rotation": list(rotation),
              "inflate": inflate, "box_uv": False, "faces": faces, "visibility": True}
        self.elements.append(el)
        self.groups[group]["children"].append(el["uuid"])

    def save(self, filename, animations=()):
        buf = io.BytesIO()
        self.tex.save(buf, "PNG")
        data = {
            "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
            "name": self.name,
            "resolution": {"width": self.size, "height": self.size},
            "elements": self.elements,
            "outliner": self.outliner,
            "textures": [{"name": f"{filename}.png", "uuid": str(uuid.uuid4()), "width": self.size, "height": self.size,
                          "uv_width": self.size, "uv_height": self.size,
                          "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}],
            "animations": list(animations),
        }
        (OUT / f"{filename}.bbmodel").write_text(json.dumps(data, indent=1), encoding="utf-8")
        return data


# ---------- Die Sammlung ----------

def beanie():
    m = Model("Strickmütze")
    m.group("hat", [0, 24, 0])
    wool, dark = "#d9682c", "#9a3e1c"
    m.cube("hat", [-4.6, 29, -4.6], [4.6, 31.6, 4.6], knit("#f2c14e", "#b8862a"), name="band")
    m.cube("hat", [-4.3, 31.6, -4.3], [4.3, 34.2, 4.3], knit(wool, dark, stripe="#f4e3c3"), name="crown")
    m.cube("hat", [-3.4, 34.2, -3.4], [3.4, 35.6, 3.4], knit(wool, dark), name="top")
    m.group("pompom", [0, 35.6, 0], "hat")
    m.cube("pompom", [-1.8, 35.4, -1.8], [1.8, 38.6, 1.8], fur("#f4e3c3", "#ffffff", "#d8c8a8"), name="pompom")
    m.save("beanie")


def bandana():
    m = Model("Bandana")
    m.group("bandana", [0, 24, 0])
    red = fabric("#c4372c", "#8a2018")
    m.cube("bandana", [-4.4, 30, -4.4], [4.4, 32.2, 4.4], red, name="band")
    m.cube("bandana", [-4.2, 32.2, -4.2], [4.2, 32.8, 4.2], red, name="top")
    m.group("knot", [0, 31, 4.4], "bandana")
    m.cube("knot", [-1, 30.2, 4.3], [1, 32, 5.3], red, name="knot")
    m.cube("knot", [-0.6, 27.5, 4.6], [0.4, 30.5, 5.0], red, name="tail1", rotation=(18, 0, 10))
    m.cube("knot", [0.0, 27.8, 4.6], [1.0, 30.8, 5.0], red, name="tail2", rotation=(14, 0, -14))
    m.save("bandana")


def fox_ears():
    m = Model("Fuchsohren")
    m.group("head", [0, 24, 0])
    orange = fur("#e0782e", "#f29a44", "#b85a1e")
    cream = fur("#f4e3c3", "#ffffff", "#d8c8a8")
    for side, sx in (("ear_left", -1), ("ear_right", 1)):
        m.group(side, [sx * 2.6, 32, 0], "head")
        m.cube(side, [sx * 2.6 - 1.6, 32, -0.8], [sx * 2.6 + 1.6, 34.5, 0.8], orange, name="base")
        m.cube(side, [sx * 2.6 - 1.1, 34.5, -0.6], [sx * 2.6 + 1.1, 36.2, 0.6], orange, name="mid")
        m.cube(side, [sx * 2.6 - 0.5, 36.2, -0.4], [sx * 2.6 + 0.5, 37.2, 0.4], fur("#2a1a14", "#3a2a20", "#1a100a"), name="tip")
        m.cube(side, [sx * 2.6 - 1.0, 32.4, -1.0], [sx * 2.6 + 1.0, 35, -0.8], cream, name="inner")
    m.save("fox_ears")


def leaf_wings():
    m = Model("Herbstblatt-Flügel")
    m.group("wings", [0, 21, 2])
    colors = [("#f29a44", "#c4472c", "#8a2a18"), ("#f2c14e", "#e0702e", "#9a5a1a"), ("#e0603e", "#9a2a1e", "#6a1a10")]
    for side, sx in (("wing_left", -1), ("wing_right", 1)):
        m.group(side, [sx * 1.5, 21, 2.5], "wings")
        for i, (a, b, v) in enumerate(colors):
            length = 13 - i * 2.2
            angle = 28 - i * 26
            m.cube(side, [sx * 1.5 if sx > 0 else -1.5 - length, 20.5 - i * 0.3, 2.4 + i * 0.15],
                   [1.5 + length if sx > 0 else -1.5, 24.5 - i * 0.3, 2.9 + i * 0.15],
                   leaf(a, b, v), origin=[sx * 1.5, 22.5, 2.6], rotation=(0, 0, sx * angle), name=f"leaf{i}")
    m.save("leaf_wings")


def dragon_wings():
    m = Model("Drachenflügel")
    m.group("wings", [0, 21, 2])
    bone = solid("#4a2a3a", "#2a1420")
    skin = membrane("#7a2a3a", "#3a1020")
    for side, sx in (("wing_left", -1), ("wing_right", 1)):
        m.group(side, [sx * 1.5, 22, 2.5], "wings")
        s = 1 if sx > 0 else -1
        def span(a, b):
            return [min(a, b), max(a, b)]
        x0, x1 = span(s * 1.5, s * 15)
        m.cube(side, [x0, 23, 2.3], [x1, 24.2, 3.3], bone, name="arm", origin=[s * 1.5, 23.5, 2.8], rotation=(0, 0, s * 12))
        x0, x1 = span(s * 2, s * 15)
        m.cube(side, [x0, 14, 2.6], [x1, 23.5, 3.0], skin, name="membrane", origin=[s * 1.5, 23.5, 2.8], rotation=(0, 0, s * 12))
        for k, (ln, rot) in enumerate(((11, 35), (9, 60))):
            x0, x1 = span(s * 2, s * (2 + ln))
            m.cube(side, [x0, 22.8, 2.4], [x1, 23.6, 3.2], bone, name=f"finger{k}", origin=[s * 2, 23.2, 2.8], rotation=(0, 0, -s * rot))
    m.save("dragon_wings")


def ember_cape():
    m = Model("Glut-Cape")
    m.group("cape", [0, 24, 2.2])
    m.cube("cape", [-5, 7.5, 2.2], [5, 24, 3.2], fabric("#5a1a22", "#2e0c12", trim="#e8a23a", embers=True), name="cloth")
    m.group("cape_glow", [0, 24, 2.2], "cape")
    m.cube("cape_glow", [-3, 12, 3.25], [3, 18, 3.35], solid("#ff9a3c", "#ffd36b", 0.2), name="emblem")
    m.save("ember_cape")


def backpack():
    m = Model("Wanderrucksack")
    m.group("pack", [0, 18, 2])
    canvas = fabric("#7a5a34", "#4e3820")
    m.cube("pack", [-3.6, 13, 2], [3.6, 22, 6], canvas, name="bag")
    m.cube("pack", [-3.8, 19, 1.9], [3.8, 22.6, 6.3], fabric("#5a3e22", "#3a2614"), name="flap")
    m.cube("pack", [-2.5, 14, 6], [2.5, 18, 7.2], canvas, name="pocket")
    m.cube("pack", [-4.5, 22.6, 2.6], [4.5, 25, 5.4], fabric("#3a6a5a", "#22443a"), name="bedroll")
    for sx in (-2.6, 2.6):
        m.cube("pack", [sx - 0.6, 12.5, -2.3], [sx + 0.6, 23.5, -2.0], solid("#3a2614", "#2a1a0e"), name="strap")
    m.save("backpack")


def fox_pet():
    m = Model("Fuchs-Pet")
    m.group("pet", [0, 0, 0])
    orange = fur("#e0782e", "#f29a44", "#b85a1e")
    cream = fur("#f4e3c3", "#ffffff", "#d8c8a8")
    dark = fur("#2a1a14", "#3a2a20", "#1a100a")
    m.group("body", [0, 5, 0], "pet")
    m.cube("body", [-2.5, 3.5, -4.5], [2.5, 7.5, 4.5], orange, name="torso")
    m.cube("body", [-2.0, 3.3, -4.0], [2.0, 4.5, 2.5], cream, name="belly")
    m.group("head", [0, 7, -4.5], "body")
    m.cube("head", [-3, 5.5, -9], [3, 10.5, -4], orange, name="skull")
    m.cube("head", [-1.5, 5.5, -11], [1.5, 8, -9], cream, name="snout")
    m.cube("head", [-0.6, 7.2, -11.4], [0.6, 8.2, -11], dark, name="nose")
    for sx in (-1, 1):
        m.group(f"ear_{'left' if sx < 0 else 'right'}", [sx * 1.8, 10.5, -6.5], "head")
        m.cube(f"ear_{'left' if sx < 0 else 'right'}", [sx * 1.8 - 1, 10.5, -7.3], [sx * 1.8 + 1, 13, -5.8], orange, name="ear")
    m.group("tail", [0, 6.5, 4.5], "body")
    m.cube("tail", [-1.8, 4.5, 4.5], [1.8, 8, 11], orange, name="tail", origin=[0, 6.5, 4.5], rotation=(-20, 0, 0))
    m.cube("tail", [-1.9, 4.4, 10], [1.9, 8.1, 12.5], cream, name="tip", origin=[0, 6.5, 4.5], rotation=(-20, 0, 0))
    for name, x, z in (("leg_front_left", -1.5, -3), ("leg_front_right", 1.5, -3), ("leg_back_left", -1.5, 3), ("leg_back_right", 1.5, 3)):
        m.group(name, [x, 4, z], "body")
        m.cube(name, [x - 0.9, 0, z - 0.9], [x + 0.9, 4, z + 0.9], orange, name="leg")
        m.cube(name, [x - 0.95, 0, z - 0.95], [x + 0.95, 1.2, z + 0.95], dark, name="paw")
    m.save("fox_pet")


CATALOG = [
    {"id": "beanie", "name": "Strickmütze", "category": "head", "file": "beanie"},
    {"id": "bandana", "name": "Bandana", "category": "head", "file": "bandana"},
    {"id": "foxears", "name": "Fuchsohren", "category": "head", "file": "fox_ears"},
    {"id": "leafwings", "name": "Herbstblatt-Flügel", "category": "wings", "file": "leaf_wings"},
    {"id": "dragonwings", "name": "Drachenflügel", "category": "wings", "file": "dragon_wings"},
    {"id": "embercape", "name": "Glut-Cape", "category": "cape", "file": "ember_cape"},
    {"id": "backpack", "name": "Wanderrucksack", "category": "body", "file": "backpack"},
    {"id": "foxpet", "name": "Fuchs", "category": "pet", "file": "fox_pet"},
]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for fn in (beanie, bandana, fox_ears, leaf_wings, dragon_wings, ember_cape, backpack, fox_pet):
        fn()
    (OUT / "catalog.json").write_text(json.dumps(CATALOG, ensure_ascii=False, indent=1), encoding="utf-8")
    for p in sorted(OUT.glob("*.bbmodel")):
        print(f"{p.name}: {p.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
