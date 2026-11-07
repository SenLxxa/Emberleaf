"""Icons der Lagerfeuer-Handy-App: das Emberleaf-Blatt auf warmem, dunklem Grund.

Einmal ausführen (braucht Pillow, z. B. aus der Launcher-Umgebung):
    ..\\emberleaf\\.venv\\Scripts\\python.exe tools\\make_icons.py
Kopiert außerdem die Abzeichen-Bilder des Launchers in die App.
"""
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
LEAF = ROOT.parent / "emberleaf" / "emberleaf.ico"
BADGES = ROOT.parent / "emberleaf" / "ui" / "assets" / "badges"
OUT = ROOT / "app"


def leaf_image():
    ico = Image.open(LEAF)
    ico.size = (256, 256)
    leaf = ico.convert("RGBA")
    return leaf.crop(leaf.getbbox())


def icon(size, leaf_share, rounded=False):
    base = Image.new("RGBA", (size, size), (44, 29, 21, 255))
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    r = size * 0.42
    ImageDraw.Draw(glow).ellipse([size / 2 - r, size * 0.52 - r, size / 2 + r, size * 0.52 + r], fill=(255, 170, 90, 150))
    base = Image.alpha_composite(base, glow.filter(ImageFilter.GaussianBlur(size * 0.12)))
    leaf = leaf_image()
    # pixel art stays sharp: scale by a whole factor where possible
    target = size * leaf_share
    factor = target / max(leaf.size)
    w, h = max(1, round(leaf.width * factor)), max(1, round(leaf.height * factor))
    leaf = leaf.resize((w, h), Image.NEAREST)
    base.alpha_composite(leaf, ((size - w) // 2, (size - h) // 2))
    if rounded:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=round(size * 0.22), fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(base, (0, 0), mask)
        return out
    return base


def main():
    OUT.mkdir(exist_ok=True)
    # full-bleed squares: Android cuts them into its own shape ("maskable"), the leaf stays inside the safe zone
    icon(192, 0.58).save(OUT / "icon-192.png", optimize=True)
    icon(512, 0.58).save(OUT / "icon-512.png", optimize=True)
    icon(180, 0.62).save(OUT / "apple-touch-icon.png", optimize=True)
    icon(64, 0.74, rounded=True).save(OUT / "favicon.png", optimize=True)
    (OUT / "badges").mkdir(exist_ok=True)
    for png in sorted(BADGES.glob("*.png")):
        shutil.copyfile(png, OUT / "badges" / png.name)
    print("Icons und Abzeichen geschrieben nach", OUT)


if __name__ == "__main__":
    main()
