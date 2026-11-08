"""Builds the pictures and fonts of the Emberleaf website from the project's own art and game screenshots.

    emberleaf\\.venv\\Scripts\\python.exe emberleaf-server\\tools\\make_site_assets.py

Small things go into the worker itself (emberleaf-server/site/): the maple leaf of the logo as a vector (same formula
as emberleaf-mod/tools/make_menu_art.py) for the zoom, the painted logo, the launcher's fonts.

Big pictures go to emberleaf-server/media/ and from there into Cloudflare KV (tools/upload_media.py, deploy.ps1 does it),
the worker serves them under /m/<name>. They come from the game test WebsiteShotsTest (1920x1080, texture pack and Glut
shader): run it with EMBERLEAF_TEST_ONLY=website, it writes build/run/clientGameTest/screenshots/site-*.png. The test
folder is wiped on every game test run, so media/ is the copy that counts: pictures whose source is gone are kept.
"""
import math
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SITE = ROOT / "emberleaf-server" / "site"
IMG = SITE / "img"
MEDIA = ROOT / "emberleaf-server" / "media"
SHOTS_SRC = ROOT / "emberleaf-mod" / "build" / "run" / "clientGameTest" / "screenshots"
LOGO_SRC = ROOT / "emberleaf-mod" / "src" / "client" / "resources" / "assets" / "emberleaf" / "textures" / "gui" / "menu" / "logo.png"
FONTS_SRC = ROOT / "emberleaf" / "ui"
# backgrounds over the whole screen
BACKGROUNDS = {"site-bg-sunset": "bg-sunset", "site-bg-morning": "bg-morning", "site-bg-night": "bg-night"}
# main menu scenes (with the friends list), shown as cards
MENUS = {f"site-menu-{s}": f"menu-{s}" for s in ("island", "cave", "nether", "end", "overworld")}
LOOKS = ("eternal", "herbst", "drache", "frost", "prisma", "leere")


def leaf_points(cx=50.0, cy=47.0, size=100.0, steps=360):
    """Outline of the Emberleaf maple leaf in a size x size box (polar formula of the menu art)."""
    pts = []
    for i in range(steps):
        a = i / steps * math.tau
        lobes = 0.6 + 0.4 * abs(math.cos(a * 2.5)) ** 0.55
        rr = size * 0.4 * lobes * (0.86 + 0.14 * math.cos(a * 15) ** 2)
        pts.append((cx + math.sin(a) * rr, cy - math.cos(a) * rr * 0.95))
    return pts


def path_d(pts):
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"


def leaf_svgs():
    d = path_d(leaf_points())
    veins = [((50, 83), (50, 17))]
    for a in (-0.9, 0.9, -2.0, 2.0):
        veins.append(((50, 52), (50 + math.sin(a) * 30, 52 - math.cos(a) * 30)))
    vein_paths = "".join(f'<path d="M{x1:.1f},{y1:.1f} L{x2:.1f},{y2:.1f}"/>' for (x1, y1), (x2, y2) in veins)
    leaf = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 110">
<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
<stop offset="0.1" stop-color="#ffd36e"/><stop offset="0.5" stop-color="#ff8a34"/><stop offset="0.95" stop-color="#c4372c"/>
</linearGradient></defs>
<path d="M50,83 L52,99" stroke="#6e3c1e" stroke-width="3.2" stroke-linecap="round"/>
<path d="{d}" fill="url(#g)"/>
<g stroke="#962f18" stroke-opacity="0.8" stroke-width="1.6" stroke-linecap="round" fill="none">{vein_paths}</g>
</svg>
"""
    (IMG / "leaf.svg").write_text(leaf, "utf-8")
    # only the shape: the website cuts the leaf-shaped window for the zoom out of it
    vein_d = " ".join(f"M{x1:.1f},{y1:.1f} L{x2:.1f},{y2:.1f}" for (x1, y1), (x2, y2) in veins)
    (SITE / "leaf-path.js").write_text(
        "// GEBAUT VON tools/make_site_assets.py – das Ahornblatt aus dem Emberleaf-Logo (Box 100 x 100, Mitte 50/47)\n"
        f'export const LEAF = "{d}";\nexport const VEINS = "{vein_d}";\n', "utf-8")


def shot(name):
    path = SHOTS_SRC / f"{name}.png"
    return Image.open(path).convert("RGB") if path.is_file() else None


def media_from_shots():
    made = []
    for src, name in BACKGROUNDS.items():
        im = shot(src)
        if im:
            im.save(MEDIA / f"{name}.webp", "WEBP", quality=72, method=6)
            made.append(name)
    for src, name in MENUS.items():
        im = shot(src)
        if im:
            im.resize((1280, 720), Image.LANCZOS).save(MEDIA / f"{name}.webp", "WEBP", quality=80, method=6)
            made.append(name)
    for look in LOOKS:
        im = shot(f"site-look-{look}-back")
        if im:
            # the player stands in the middle: a square around them, wings, cape and pet included
            im.crop((600, 300, 1320, 1020)).resize((600, 600), Image.LANCZOS).save(MEDIA / f"look-{look}.webp", "WEBP", quality=80, method=6)
            made.append(f"look-{look}")
    return made


def main():
    IMG.mkdir(parents=True, exist_ok=True)
    MEDIA.mkdir(parents=True, exist_ok=True)
    (SITE / "fonts").mkdir(exist_ok=True)
    leaf_svgs()
    Image.open(LOGO_SRC).convert("RGBA").save(IMG / "logo.webp", "WEBP", quality=90, method=6)
    for font in ("nunito.woff2", "pixelify-sans.woff2"):
        shutil.copyfile(FONTS_SRC / "fonts" / font if (FONTS_SRC / "fonts" / font).is_file() else FONTS_SRC / font, SITE / "fonts" / font)

    # the 854x480 screenshots of the first website now live in media/ too
    old = IMG / "shots"
    if old.is_dir():
        for f in old.glob("*.webp"):
            shutil.copyfile(f, MEDIA / f.name)
        shutil.rmtree(old)

    made = media_from_shots()
    print("neu aus dem Spiel:", ", ".join(made) or "nichts (keine site-*.png im Testordner, media/ bleibt wie es ist)")
    total = sum(p.stat().st_size for p in MEDIA.glob("*.webp"))
    print(f"media/: {len(list(MEDIA.glob('*.webp')))} Bilder, {total / 1024:.0f} KB")


if __name__ == "__main__":
    main()
