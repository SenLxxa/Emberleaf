"""Draws Emberleaf's player badges (16x16 pixel icons) for the game font and the launcher/app.

Writes assets/emberleaf/textures/font/badges.png + assets/emberleaf/font/badges.json (glyphs U+E100..)
and emberleaf/ui/assets/badges/<id>.png (64x64) for the launcher and the phone app.
Run: python tools/make_badges.py   (Pillow)
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
FONT_PNG = ROOT / "src/client/resources/assets/emberleaf/textures/font/badges.png"
FONT_JSON = ROOT / "src/client/resources/assets/emberleaf/font/badges.json"
UI_DIR = ROOT.parent / "emberleaf/ui/assets/badges"


def hourglass(frame, frame_dark, sand):
    return ([
        "................",
        "...FFFFFFFFFF...",
        "...fFFFFFFFFf...",
        "....F......F....",
        "....FSSSSSSF....",
        ".....FSSSSF.....",
        "......FSSF......",
        ".......FF.......",
        "......F..F......",
        ".....F.SS.F.....",
        "....F.SSSS.F....",
        "....FSSSSSSF....",
        "...fFFFFFFFFf...",
        "...FFFFFFFFFF...",
        "................",
        "................",
    ], {"F": frame, "f": frame_dark, "S": sand})


ICONS = {
    "founder": ([
        "................",
        "................",
        "..y....yy....y..",
        ".yWy..yWWy..yWy.",
        ".yYy..yYYy..yYy.",
        ".yYYy.yYYy.yYYy.",
        ".yYYYyYYYYyYYYy.",
        ".yYYYYYYYYYYYYy.",
        ".yYRYYYBBYYYRYy.",
        ".yYYYYYYYYYYYYy.",
        ".yyyyyyyyyyyyyy.",
        ".yWWWWWWWWWWWWy.",
        ".yYYYYYYYYYYYYy.",
        ".yyyyyyyyyyyyyy.",
        "................",
        "................",
    ], {"y": "#8a5a10", "Y": "#ffcc33", "W": "#fff2a8", "R": "#e0303a", "B": "#3a8fe0"}),
    "alpha": ([
        "................",
        ".......o........",
        "......oOo.......",
        "..o..oOOOo..o...",
        "..oOooOOOooOo...",
        "...oOOOOOOOOo...",
        ".oooOOOwwOOOooo.",
        ".oOOOOwOOwOOOOo.",
        "..oOOOwOOwOOOo..",
        "...oOOOwwwOOo...",
        "....oOOOOOOOo...",
        ".....ooOOOoo....",
        ".......bb.......",
        ".......bb.......",
        "................",
        "................",
    ], {"o": "#9a3a10", "O": "#f07a28", "w": "#fff4dc", "b": "#5a3420"}),
    "h500": hourglass("#2bb3c4", "#16707c", "#b8f6ff"),
    "h100": hourglass("#d8a018", "#7a5208", "#ffe070"),
    "h50": hourglass("#a8b2bc", "#5c646c", "#f0ead8"),
    "h10": hourglass("#a8602c", "#5a3014", "#f0b070"),
    "friend": ([
        "................",
        ".......r........",
        "......rYr.......",
        ".....rYYYr......",
        ".....rYWYr......",
        "....rYYWYYr.....",
        "....rYWWWYr.....",
        ".....rYWYr......",
        "...LLLLLLLLLL...",
        "..LllLLLLLLllL..",
        "...LLLLLLLLLL...",
        "..LLLLlllLLLLL..",
        "...LLLLLLLLLL...",
        "................",
        "................",
        "................",
    ], {"r": "#e0502a", "Y": "#ffb22e", "W": "#fff0a0", "L": "#8a5a32", "l": "#4a2c18"}),
    "creator": ([
        ".............hh.",
        "............hhh.",
        "...........hhh..",
        "..........hhh...",
        ".........hhh....",
        "........hhh.....",
        ".......mmm......",
        "......mmm.......",
        ".....ppp........",
        "....pPPp........",
        "...pPPPp........",
        "..pPPPp.........",
        "..pPPp..........",
        ".pPPp...........",
        ".ppp............",
        "................",
    ], {"h": "#a8703c", "m": "#c8ccd4", "p": "#a83a8a", "P": "#ff6ad0"}),
    "explorer": ([
        "................",
        ".....kkkkkk.....",
        "...kkggggggkk...",
        "..kggggggggggk..",
        "..kgggggRggggk..",
        ".kgggggRRgggggk.",
        ".kggggRRRgggggk.",
        ".kgggggWWggggk..",
        ".kgggggBBBgggk..",
        ".kgggggBBgggggk.",
        "..kggggBggggk...",
        "..kggggggggggk..",
        "...kkggggggkk...",
        ".....kkkkkk.....",
        "................",
        "................",
    ], {"k": "#5a3a20", "g": "#f2e6c8", "R": "#e03a2a", "B": "#5a6a80", "W": "#ffffff"}),
    "nightowl": ([
        "................",
        "......mmmm......",
        "....mmMMm.......",
        "...mMMm.....s...",
        "..mMMm.....sSs..",
        "..mMm.......s...",
        ".mMMm...........",
        ".mMMm...........",
        ".mMMm...........",
        ".mMMMm..........",
        "..mMMMm.....mm..",
        "..mMMMMmmmmmMm..",
        "...mMMMMMMMMm...",
        "....mmMMMMmm....",
        "......mmmm......",
        "................",
    ], {"m": "#4a5a9a", "M": "#d8e0ff", "s": "#c8b040", "S": "#fff4a0"}),
}
ORDER = ["founder", "alpha", "h500", "h100", "h50", "h10", "friend", "creator", "explorer", "nightowl"]

# Chat reactions of the Lagerfeuer (glyphs U+E120.. in the same font): heart, laugh, like, fire, wow, sad
FACE = [
    "................",
    ".....oooooo.....",
    "...ooyyyyyyoo...",
    "..oyyyyyyyyyyo..",
    ".oyyyyyyyyyyyyo.",
    ".oyyyyyyyyyyyyo.",
    "oyyyyyyyyyyyyyyo",
    "oyyyyyyyyyyyyyyo",
    "oyyyyyyyyyyyyyyo",
    "oyyyyyyyyyyyyyyo",
    ".oyyyyyyyyyyyyo.",
    ".oyyyyyyyyyyyyo.",
    "..oyyyyyyyyyyo..",
    "...ooyyyyyyoo...",
    ".....oooooo.....",
    "................",
]
FACE_COLORS = {"o": "#9a6a10", "y": "#ffd23a", "k": "#4a3008", "r": "#c8302a", "t": "#5ab8ff", "w": "#ffffff"}


def face(marks):
    rows = [list(r) for r in FACE]
    for ch, points in marks.items():
        for x, y in points:
            rows[y][x] = ch
    return ["".join(r) for r in rows], FACE_COLORS


REACTION_ICONS = {
    "heart": ([
        "................",
        "................",
        "...RRR....RRR...",
        "..RrrrR..RrrrR..",
        ".RrWWrrRRrrrrrR.",
        ".RrWrrrrrrrrrrR.",
        ".RrrrrrrrrrrrrR.",
        ".RrrrrrrrrrrrrR.",
        "..RrrrrrrrrrrR..",
        "...RrrrrrrrrR...",
        "....RrrrrrrR....",
        ".....RrrrrR.....",
        "......RrrR......",
        ".......RR.......",
        "................",
        "................",
    ], {"R": "#8a1020", "r": "#ff4a5a", "W": "#ffd0d8"}),
    "laugh": face({
        "k": [(4, 6), (5, 5), (6, 6), (9, 6), (10, 5), (11, 6), (4, 9), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (11, 9),
              (4, 10), (11, 10), (5, 11), (10, 11), (6, 12), (7, 12), (8, 12), (9, 12)],
        "r": [(5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10), (6, 11), (7, 11), (8, 11), (9, 11)],
        "t": [(2, 7), (2, 8), (1, 9), (13, 7), (13, 8), (14, 9)],
    }),
    "like": ([
        "................",
        ".......kk.......",
        "......kYYk......",
        "......kYYk......",
        ".....kYYYk......",
        ".....kYYYkkkkk..",
        "kkkk.kYYYYYYYYk.",
        "kBBk.kYYYYkkkk..",
        "kBBk.kYYYYYYYYk.",
        "kBBk.kYYYYkkkk..",
        "kBBk.kYYYYYYYYk.",
        "kBBk.kYYYYkkkk..",
        "kBBk..kYYYYYYk..",
        "kkkk...kkkkkk...",
        "................",
        "................",
    ], {"k": "#8a5a10", "Y": "#ffcc4a", "B": "#3a7ad0"}),
    "fire": ([
        "................",
        ".......o........",
        "......oO........",
        ".....oOOo..o....",
        "....oOOOOooO....",
        "...oOOOyOOOOo...",
        "...oOOyyOOOOo...",
        "..oOOyyyyOOOOo..",
        "..oOyyyyyyOOOo..",
        "..oOyyWWyyyOOo..",
        "..oOyyWWWyyOOo..",
        "...oOyWWWyyOo...",
        "....ooOyyOOo....",
        "......oooo......",
        "................",
        "................",
    ], {"o": "#b8380c", "O": "#ff7a22", "y": "#ffc83a", "W": "#fff6c0"}),
    "wow": face({
        "k": [(5, 5), (5, 6), (10, 5), (10, 6), (4, 3), (5, 3), (10, 3), (11, 3), (7, 9), (8, 9), (6, 10), (9, 10), (6, 11), (9, 11), (7, 12), (8, 12)],
        "r": [(7, 10), (8, 10), (7, 11), (8, 11)],
    }),
    "sad": face({
        "k": [(5, 6), (5, 7), (10, 6), (10, 7), (6, 4), (5, 4), (9, 4), (10, 4), (6, 10), (7, 10), (8, 10), (9, 10), (5, 11), (10, 11)],
        "t": [(10, 8), (10, 9), (11, 9)],
    }),
}
REACTION_ORDER = ["heart", "laugh", "like", "fire", "wow", "sad"]
REACTIONS_PNG = ROOT / "src/client/resources/assets/emberleaf/textures/font/reactions.png"


def hex_rgba(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def draw(rows, palette):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:16]):
            if ch in palette:
                img.putpixel((x, y), hex_rgba(palette[ch]))
    return img


def main():
    sheet = Image.new("RGBA", (16 * len(ORDER), 16), (0, 0, 0, 0))
    UI_DIR.mkdir(parents=True, exist_ok=True)
    for i, badge in enumerate(ORDER):
        rows, palette = ICONS[badge]
        icon = draw(rows, palette)
        sheet.paste(icon, (i * 16, 0))
        icon.resize((64, 64), Image.NEAREST).save(UI_DIR / f"{badge}.png")
    FONT_PNG.parent.mkdir(parents=True, exist_ok=True)
    FONT_JSON.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(FONT_PNG)
    reactions = Image.new("RGBA", (16 * len(REACTION_ORDER), 16), (0, 0, 0, 0))
    for i, reaction in enumerate(REACTION_ORDER):
        rows, palette = REACTION_ICONS[reaction]
        reactions.paste(draw(rows, palette), (i * 16, 0))
    reactions.save(REACTIONS_PNG)
    chars = "".join(chr(0xE100 + i) for i in range(len(ORDER)))
    reaction_chars = "".join(chr(0xE120 + i) for i in range(len(REACTION_ORDER)))
    FONT_JSON.write_text(json.dumps({"providers": [
        {"type": "bitmap", "file": "emberleaf:font/badges.png", "ascent": 8, "height": 9, "chars": [chars]},
        {"type": "bitmap", "file": "emberleaf:font/reactions.png", "ascent": 8, "height": 9, "chars": [reaction_chars]},
        {"type": "reference", "id": "minecraft:default"},
    ]}, ensure_ascii=True, indent=1), "utf-8")
    print("badges:", len(ORDER), "reactions:", len(REACTION_ORDER))


if __name__ == "__main__":
    main()
