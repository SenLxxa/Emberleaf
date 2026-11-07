"""Draws the Emberleaf logo (pixel-art maple leaf with embers) as PNG for the UI and ICO for the .exe.

Usage (from the emberleaf folder): python tools/make_icon.py
"""
from pathlib import Path

from PIL import Image

N = 20  # pixel grid of the leaf
LEAF = [  # maple leaf outline in 0..1 coordinates, y pointing down
    (0.50, 0.02), (0.58, 0.20), (0.67, 0.13), (0.65, 0.33), (0.82, 0.24), (0.77, 0.38), (0.97, 0.37),
    (0.85, 0.50), (0.91, 0.56), (0.70, 0.63), (0.75, 0.74), (0.56, 0.67), (0.53, 0.82),
    (0.47, 0.82), (0.44, 0.67), (0.25, 0.74), (0.30, 0.63), (0.09, 0.56), (0.15, 0.50), (0.03, 0.37),
    (0.23, 0.38), (0.18, 0.24), (0.35, 0.33), (0.33, 0.13), (0.42, 0.20),
]
EMBERS = [(17, 1, "#ffd36b"), (18, 3, "#ffb347"), (15, 0, "#ff8a3d"), (2, 2, "#ffb347")]


def inside(x, y, poly):
    hit = False
    for i in range(len(poly)):
        (x1, y1), (x2, y2) = poly[i], poly[i - 1]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            hit = not hit
    return hit


def hex_rgba(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def draw():
    grid = [[inside((x + 0.5) / N, (y + 0.5) / N, LEAF) for x in range(N)] for y in range(N)]
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            if not grid[y][x]:
                continue
            edge = any(
                not (0 <= x + dx < N and 0 <= y + dy < N and grid[y + dy][x + dx])
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            )
            # warm gradient: yellow top-right to deep red bottom-left
            t = min(1, max(0, (y / N) * 0.8 + (1 - x / N) * 0.5 - 0.15))
            if edge:
                color = "#8a2a14"
            elif x == N // 2 or (abs(x - N // 2) == abs(y - N * 0.62) and y < N * 0.62):
                color = "#c9502a"  # Blattadern
            elif t < 0.33:
                color = "#ffc04d"
            elif t < 0.66:
                color = "#f2813a"
            else:
                color = "#d9462b"
            img.putpixel((x, y), hex_rgba(color))
    for y in range(int(N * 0.82), N):  # Stiel
        img.putpixel((N // 2, y), hex_rgba("#5b3a22"))
    for x, y, c in EMBERS:
        img.putpixel((x, y), hex_rgba(c))
    return img


def main():
    root = Path(__file__).resolve().parent.parent
    leaf = draw()
    big = leaf.resize((N * 16, N * 16), Image.NEAREST)
    canvas = Image.new("RGBA", (N * 16 + 32, N * 16 + 32), (0, 0, 0, 0))
    canvas.paste(big, (16, 16))
    (root / "ui" / "assets").mkdir(parents=True, exist_ok=True)
    leaf.resize((N * 8, N * 8), Image.NEAREST).save(root / "ui" / "assets" / "logo.png")
    canvas.save(root / "emberleaf.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("logo + icon written")


if __name__ == "__main__":
    main()
