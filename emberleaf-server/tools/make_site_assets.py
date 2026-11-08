"""Builds the images and fonts of the Emberleaf website (emberleaf-server/site/) from the project's own art.

    emberleaf\\.venv\\Scripts\\python.exe emberleaf-server\\tools\\make_site_assets.py

- the maple leaf of the logo as a vector (same formula as emberleaf-mod/tools/make_menu_art.py) for the zoom
- the painted Emberleaf logo of the main menu
- game screenshots from the mod's game tests (emberleaf-mod/build/run/clientGameTest/screenshots) as WebP
- the launcher's fonts (Nunito, Pixelify Sans)
"""
import math
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SITE = ROOT / "emberleaf-server" / "site"
IMG = SITE / "img"
SHOTS_SRC = ROOT / "emberleaf-mod" / "build" / "run" / "clientGameTest" / "screenshots"
LOGO_SRC = ROOT / "emberleaf-mod" / "src" / "client" / "resources" / "assets" / "emberleaf" / "textures" / "gui" / "menu" / "logo.png"
FONTS_SRC = ROOT / "emberleaf" / "ui"
# screenshot file in the game tests -> name on the website
SHOTS = {
    "0049_features-title": "menu",
    "0003_emberleaf-menu-island": "menu-island",
    "0021_emberleaf-friend-cosmetics": "cosmetics",
    "0026_emberleaf-emote-cheer": "emote",
    "0076_features-lagerfeuer-chat": "chat",
    "0077_features-lagerfeuer-friends": "friends",
    "0047_shader-sunset": "shader",
    "0039_pack-world-2": "world",
    "0071_features-emote-wheel": "emote-wheel",
    "0072_features-settings": "settings",
    "0050_features-tools": "tools",
    "0017_skins-ingame": "skins",
    "0073_features-new-hud": "hud",
}


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


def main():
    IMG.mkdir(parents=True, exist_ok=True)
    (IMG / "shots").mkdir(exist_ok=True)
    (SITE / "fonts").mkdir(exist_ok=True)
    leaf_svgs()

    logo = Image.open(LOGO_SRC).convert("RGBA")
    logo.save(IMG / "logo.webp", "WEBP", quality=90, method=6)

    for src, name in SHOTS.items():
        shot = Image.open(SHOTS_SRC / f"{src}.png").convert("RGB")
        shot.save(IMG / "shots" / f"{name}.webp", "WEBP", quality=82, method=6)

    for font in ("nunito.woff2", "pixelify-sans.woff2"):
        shutil.copyfile(FONTS_SRC / "fonts" / font if (FONTS_SRC / "fonts" / font).is_file() else FONTS_SRC / font,
                        SITE / "fonts" / font)

    total = sum(p.stat().st_size for p in SITE.rglob("*") if p.is_file() and p.suffix in (".webp", ".svg", ".woff2"))
    print(f"Webseiten-Bilder fertig: {total / 1024:.0f} KB")


if __name__ == "__main__":
    main()
