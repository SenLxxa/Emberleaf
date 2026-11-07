"""Which block gets which Emberleaf pixel art (block texture name -> function returning a 16x16 RGBA array).

Palettes go dark -> light with a small hue shift: shadows lean cool/violet, lights lean warm/golden.
"""
from . import pixelart as px
from .pixelart import pal

# ---------- Paletten ----------
STONE = pal("#4a4750", "#5c5962", "#6f6c75", "#84818a", "#99969e")
STONE_MORTAR = pal("#2c2a31", "#38353d")
COBBLE = pal("#47444c", "#5d5a62", "#75727a", "#8e8b92", "#a4a1a7")
MOSS = pal("#3d5a26", "#4f7230", "#64883a", "#7a9e46")
DEEPSLATE = pal("#25242b", "#302f37", "#3c3b44", "#494852", "#57565f")
DEEP_MORTAR = pal("#17161b", "#1e1d23")
ANDESITE = pal("#5a5a5c", "#6c6c6f", "#808083", "#959598", "#a9a9ab")
DIORITE = pal("#99958f", "#b2aea8", "#c8c4be", "#dcd9d3", "#eeebe6")
GRANITE = pal("#6a4334", "#815343", "#976553", "#ab7864", "#bd8b76")
CALCITE = pal("#b9b6b0", "#cac7c1", "#d9d6d0", "#e6e4df", "#f2f1ed")
TUFF = pal("#4a4b41", "#5a5b4f", "#6b6c5e", "#7c7d6e", "#8d8e7e")
DIRT = pal("#4a3020", "#5b3c29", "#6d4a33", "#80593e", "#936a4b")
DIRT_PEBBLE = pal("#6b5f55", "#857a6f")
COARSE = pal("#432b1d", "#553826", "#674630", "#7a553b", "#8c6447")
PODZOL = pal("#3f2714", "#52331a", "#674222", "#7c522c", "#8f6236")
MUD = pal("#2c2829", "#353031", "#3f3a3a", "#4a4444", "#554e4d")
SAND = pal("#b59e69", "#c8b27b", "#d6c18d", "#e2cf9f", "#ecddb3")
RED_SAND = pal("#8a4119", "#a04e20", "#b45d29", "#c66d34", "#d47e41")
GRAVEL = pal("#5a5552", "#6e6965", "#847e79", "#99938d", "#ada7a1")
GRAVEL_GAP = pal("#2f2b29", "#3b3633")
CLAY = pal("#848897", "#9599a7", "#a5a9b6", "#b4b8c4", "#c2c6d1")
SNOW = pal("#c9d5e0", "#d9e3ec", "#e6eef5", "#f1f6fa", "#fbfdff")
ICE = pal("#7ea7e0", "#93b8ea", "#a8c8f1", "#bed7f6", "#d3e5fa")
NETHERRACK = pal("#4b1516", "#5e1c1d", "#722526", "#86302f", "#963b38")
NETHER_BRICK = pal("#1f0b0f", "#2b1015", "#38161c", "#451d23", "#52252b")
NETHER_MORTAR = pal("#100508", "#16080b")
END_STONE = pal("#b5ad7c", "#c7c08e", "#d6cf9f", "#e2dcaf", "#ece7c1")
PURPUR = pal("#7c5a91", "#8f6ba4", "#a17db5", "#b28fc4", "#c1a2d1")
PURPUR_MORTAR = pal("#5d4170", "#694b7d")
OBSIDIAN = pal("#0d0915", "#150f22", "#1f1731", "#2b2143", "#3b2e5a")
BLACKSTONE = pal("#1c181f", "#252029", "#2f2934", "#3a333f", "#463e4b")
BEDROCK = pal("#232224", "#353437", "#4a494d", "#605f63")
SANDSTONE = pal("#b8a16b", "#c9b37c", "#d7c28d", "#e2cf9e", "#ecdcb0")
RED_SANDSTONE = pal("#8f421a", "#a35022", "#b45f2b", "#c46f36", "#d18042")
BRICK = pal("#6e2f22", "#83392a", "#984534", "#ab5340", "#bc634d")
BRICK_MORTAR = pal("#a89c8f", "#bdb2a5")
MUD_BRICK = pal("#7a5f4a", "#8b6e57", "#9c7e64", "#ac8e72", "#ba9d81")
MUD_MORTAR = pal("#5d4737", "#6a5241")
QUARTZ = pal("#c9c0b6", "#d8d0c7", "#e5ded6", "#efeae4", "#f8f5f1")
PRISMARINE = pal("#3e7a70", "#4b8c80", "#5a9f91", "#6cb2a2", "#82c4b3")

WOODS = {  # planks, bark
    "oak": (pal("#5f4227", "#755233", "#8b633f", "#a0754c", "#b38659"), pal("#33261a", "#413121", "#4f3d2a", "#5e4a34", "#6c5740")),
    "spruce": (pal("#3a2717", "#49321e", "#583d26", "#67492f", "#755539"), pal("#251a11", "#2f2217", "#3a2b1e", "#463526", "#523f2e")),
    "birch": (pal("#a08458", "#b49868", "#c4aa79", "#d2ba8b", "#dfca9e"), pal("#b9b3a7", "#cdc8bd", "#ddd9cf", "#ebe8e0", "#f6f4ef")),
    "jungle": (pal("#63402a", "#784e33", "#8c5d3e", "#a06d4a", "#b17d57"), pal("#372e15", "#463b1d", "#554826", "#65562f", "#736339")),
    "acacia": (pal("#733a1c", "#8a4623", "#9f532a", "#b26133", "#c3703e"), pal("#47413b", "#58514a", "#696259", "#7a7369", "#8a8379")),
    "dark_oak": (pal("#2b1b0e", "#372313", "#432c19", "#4f3520", "#5b3f27"), pal("#1c140c", "#251a10", "#2f2216", "#392a1c", "#433223")),
    "mangrove": (pal("#551e1b", "#682621", "#7b2f29", "#8d3931", "#9e443a"), pal("#3a2b24", "#47352d", "#554037", "#634b41", "#71564b")),
    "cherry": (pal("#a96f69", "#ba817a", "#c9938b", "#d6a59c", "#e2b8ae"), pal("#2a1920", "#36212a", "#432a34", "#50333f", "#5d3d4a")),
    "pale_oak": (pal("#ada39a", "#bdb4ab", "#cbc3bb", "#d8d1ca", "#e3ddd7"), pal("#47423d", "#56504a", "#655f58", "#746e67", "#837d75")),
    "crimson": (pal("#481d2d", "#5a2438", "#6c2c43", "#7e354f", "#8f3f5a"), pal("#561330", "#6a193c", "#7f2049", "#932856", "#a43163")),
    "warped": (pal("#1d4744", "#245754", "#2c6864", "#357a75", "#3f8b85"), pal("#1a4b47", "#205c57", "#286e68", "#31817a", "#3b938b")),
}
BIRCH_MARKS = pal("#2b2723", "#3f3a34")
LEAF_GREY = pal("#474747", "#5c5c5c", "#727272", "#888888", "#9d9d9d")
ORES = {  # nugget colours: shadow, body, light; gems are drawn as crystals
    "coal": (pal("#141317", "#25242a", "#3b3a41"), False), "iron": (pal("#8f6249", "#b9896a", "#e2bc9c"), False),
    "copper": (pal("#8c4426", "#b8653a", "#5fae8a"), False), "gold": (pal("#a8771a", "#dcae2c", "#fbe36a"), False),
    "redstone": (pal("#7c0d0d", "#bd1f17", "#f05539"), False), "lapis": (pal("#1b2b72", "#2c46aa", "#5a7ddc"), True),
    "diamond": (pal("#1a807b", "#38bcb0", "#a6f3e8"), True), "emerald": (pal("#106d34", "#1fa84f", "#86ecaa"), True),
}
DYES = {
    "white": "#e9ecec", "light_gray": "#9a9a94", "gray": "#4c5155", "black": "#1d1d21", "brown": "#7a4f2d", "red": "#a8302a",
    "orange": "#e57319", "yellow": "#f4c22d", "lime": "#73b81b", "green": "#55702a", "cyan": "#178c91", "light_blue": "#3caed8",
    "blue": "#383c9e", "purple": "#7b2fab", "magenta": "#b844b0", "pink": "#ee8eac",
}
TERRACOTTA = {
    "white": "#d2b2a1", "light_gray": "#876b62", "gray": "#3a2a24", "black": "#251710", "brown": "#4d3324", "red": "#8f3d2e",
    "orange": "#a25426", "yellow": "#ba8523", "lime": "#677535", "green": "#4c532a", "cyan": "#575b5b", "light_blue": "#716d8a",
    "blue": "#4a3c5b", "purple": "#764656", "magenta": "#96586d", "pink": "#a24e4f",
}
MINERALS = {
    "iron_block": pal("#8f8f93", "#b4b4b8", "#cfcfd2", "#e2e2e4", "#f4f4f5"), "gold_block": pal("#a8771a", "#d29f26", "#eec13a", "#f8d75a", "#fff0a0"),
    "diamond_block": pal("#1b7f79", "#2fa79e", "#4ccabd", "#7ee3d6", "#c0f7ef"), "emerald_block": pal("#0f6a33", "#178a44", "#21a853", "#47c972", "#8de8a9"),
    "lapis_block": pal("#1a2a6e", "#22389a", "#2f4cb8", "#4a6bd0", "#7b98e6"), "redstone_block": pal("#6e0b0b", "#951410", "#ba2016", "#d8352a", "#f06a52"),
    "coal_block": pal("#101014", "#18181d", "#212127", "#2c2c33", "#3d3d46"), "netherite_block": pal("#2a2427", "#362f33", "#433b3f", "#51484d", "#625a5e"),
    "copper_block": pal("#8a4426", "#a95332", "#c1653e", "#d47a4d", "#e49163"), "raw_iron_block": pal("#8f6249", "#a8775b", "#bd8b6c", "#d0a080", "#dfb496"),
}


AZALEA = pal("#344d1c", "#466628", "#5b7f33", "#71963f", "#88ac4f")
LEAF_DEFS = {  # name: (seed, palette, blossoms); grey ones are coloured by the biome
    "oak_leaves": (60, LEAF_GREY, None), "jungle_leaves": (61, LEAF_GREY, None), "acacia_leaves": (62, LEAF_GREY, None),
    "dark_oak_leaves": (63, LEAF_GREY, None), "mangrove_leaves": (64, LEAF_GREY, None), "birch_leaves": (65, LEAF_GREY, None),
    "spruce_leaves": (66, LEAF_GREY, None), "cherry_leaves": (67, pal("#a2556f", "#c06d8a", "#d888a3", "#eba6bd", "#f8c6d6"), None),
    "azalea_leaves": (68, AZALEA, None), "flowering_azalea_leaves": (68, AZALEA, pal("#8f3a7a", "#c45aa8", "#e88ccd")),
    "pale_oak_leaves": (69, pal("#646a62", "#757b72", "#868c83", "#979d93", "#a7ada3"), None),
}


def fluff(name):
    seed, palette, blossoms = LEAF_DEFS[name]
    return px.fluff(seed, palette, blossoms)


def _wood(name, planks, bark_pal):
    seed = sum(map(ord, name))
    marks = BIRCH_MARKS if name == "birch" else None
    stem = name in ("crimson", "warped")
    log = f"{name}_stem" if stem else f"{name}_log"
    return {
        f"{name}_planks": lambda: px.planks(seed, planks),
        log: lambda: px.bark(seed, bark_pal, marks),
        f"{log}_top": lambda: px.log_top(seed, planks, bark_pal),
        f"stripped_{log}": lambda: px.bark(seed + 1, planks),
        f"stripped_{log}_top": lambda: px.log_top(seed + 1, planks, planks),
    }


def build():
    g = {}
    stone = lambda: px.blotchy(1, STONE, speckle=0.05)  # noqa: E731
    deep = lambda: px.blotchy(14, DEEPSLATE, scales=((2, 1.0), (5, 0.5)), speckle=0.04)  # noqa: E731
    nether = lambda: px.blotchy(16, NETHERRACK, scales=((3, 1.0), (6, 0.6)), speckle=0.08)  # noqa: E731
    dirt = lambda: px.blotchy(2, DIRT, scales=((3, 1.0), (6, 0.6)), speckle=0.07, speck_pal=DIRT_PEBBLE)  # noqa: E731
    g.update({
        "stone": stone, "smooth_stone": lambda: px.blotchy(3, STONE[1:], scales=((2, 1.0),)),
        "cobblestone": lambda: px.cobbled(4, COBBLE, STONE_MORTAR), "mossy_cobblestone": lambda: px.cobbled(4, COBBLE, STONE_MORTAR, moss=MOSS),
        "stone_bricks": lambda: px.bricks(5, STONE, STONE_MORTAR), "mossy_stone_bricks": lambda: px.bricks(5, STONE, STONE_MORTAR, moss=MOSS),
        "cracked_stone_bricks": lambda: px.bricks(5, STONE, STONE_MORTAR, cracked=True),
        "chiseled_stone_bricks": lambda: px.bricks(5, STONE, STONE_MORTAR, rows=2, per_row=1, chiseled=True),
        "andesite": lambda: px.blotchy(6, ANDESITE, speckle=0.12), "polished_andesite": lambda: px.bricks(6, ANDESITE, ANDESITE[:2], rows=2, per_row=1, offset=False),
        "diorite": lambda: px.blotchy(7, DIORITE, speckle=0.14, speck_pal=pal("#77736d", "#8a867f")),
        "polished_diorite": lambda: px.bricks(7, DIORITE, DIORITE[:2], rows=2, per_row=1, offset=False),
        "granite": lambda: px.blotchy(8, GRANITE, speckle=0.14, speck_pal=pal("#5b3a2e", "#d0a08a")),
        "polished_granite": lambda: px.bricks(8, GRANITE, GRANITE[:2], rows=2, per_row=1, offset=False),
        "calcite": lambda: px.blotchy(9, CALCITE), "tuff": lambda: px.blotchy(10, TUFF, speckle=0.1),
        "deepslate": deep, "deepslate_top": lambda: px.blotchy(15, DEEPSLATE),
        "cobbled_deepslate": lambda: px.cobbled(11, DEEPSLATE, DEEP_MORTAR, n=9),
        "deepslate_bricks": lambda: px.bricks(12, DEEPSLATE, DEEP_MORTAR), "cracked_deepslate_bricks": lambda: px.bricks(12, DEEPSLATE, DEEP_MORTAR, cracked=True),
        "deepslate_tiles": lambda: px.bricks(13, DEEPSLATE, DEEP_MORTAR, rows=4, per_row=4, offset=False),
        "polished_deepslate": lambda: px.bricks(13, DEEPSLATE, DEEP_MORTAR, rows=1, per_row=1, offset=False),
        "netherrack": nether, "nether_bricks": lambda: px.bricks(17, NETHER_BRICK, NETHER_MORTAR, rows=8, per_row=2),
        "red_nether_bricks": lambda: px.bricks(18, pal("#3e0a0c", "#510e10", "#651315", "#78191b", "#8a2023"), NETHER_MORTAR, rows=8, per_row=2),
        "end_stone": lambda: px.blotchy(19, END_STONE, speckle=0.1, speck_pal=pal("#a39b6c", "#f3efd0")),
        "end_stone_bricks": lambda: px.bricks(20, END_STONE, pal("#9c9468", "#aaa276"), rows=4, per_row=2),
        "purpur_block": lambda: px.bricks(21, PURPUR, PURPUR_MORTAR, rows=2, per_row=2, offset=False),
        "obsidian": lambda: px.blotchy(22, OBSIDIAN, scales=((3, 1.0), (6, 0.5)), speckle=0.06, speck_pal=pal("#5b3f8a", "#7a5ab8")),
        "blackstone": lambda: px.blotchy(23, BLACKSTONE, speckle=0.05), "polished_blackstone": lambda: px.bricks(24, BLACKSTONE, BLACKSTONE[:2], rows=1, per_row=1),
        "polished_blackstone_bricks": lambda: px.bricks(24, BLACKSTONE, pal("#121014", "#18151a")),
        "bedrock": lambda: px.cobbled(25, BEDROCK, pal("#0f0f10", "#151516"), n=10),
        "bricks": lambda: px.bricks(26, BRICK, BRICK_MORTAR, rows=4, per_row=2),
        "mud_bricks": lambda: px.bricks(27, MUD_BRICK, MUD_MORTAR, rows=4, per_row=2),
        "packed_mud": lambda: px.blotchy(28, MUD_BRICK, speckle=0.08), "mud": lambda: px.blotchy(29, MUD),
        "quartz_block_side": lambda: px.blotchy(30, QUARTZ, scales=((2, 1.0),)), "quartz_block_top": lambda: px.blotchy(31, QUARTZ, scales=((2, 1.0),)),
        "quartz_bricks": lambda: px.bricks(32, QUARTZ, QUARTZ[:2]),
        "prismarine_bricks": lambda: px.bricks(33, PRISMARINE, pal("#2a5a52", "#336a61")),
        "dirt": dirt, "coarse_dirt": lambda: px.blotchy(34, COARSE, speckle=0.16, speck_pal=pal("#77695d", "#958879")),
        "rooted_dirt": lambda: px.blotchy(35, DIRT, speckle=0.1, speck_pal=pal("#b18e64", "#c9a77b")),
        "podzol_top": lambda: px.blotchy(36, PODZOL, scales=((4, 1.0), (8, 0.6)), speckle=0.15, speck_pal=pal("#9a6a34", "#b98340")),
        "podzol_side": lambda: _side(dirt(), PODZOL),
        "grass_block_top": lambda: px.grass_top(37), "grass_block_side": lambda: px.grass_side(37, dirt()),
        "grass_block_side_overlay": lambda: px.grass_overlay(37),
        "dirt_path_top": lambda: px.blotchy(38, pal("#7b5f3b", "#8f7047", "#a28154", "#b39162", "#c2a172"), scales=((3, 1.0), (6, 0.4))),
        "sand": lambda: px.sand(39, SAND), "red_sand": lambda: px.sand(40, RED_SAND), "suspicious_sand_0": lambda: px.sand(39, SAND),
        "gravel": lambda: px.gravel(41, GRAVEL, GRAVEL_GAP), "clay": lambda: px.blotchy(42, CLAY),
        "snow": lambda: px.sand(43, SNOW, ripples=False), "ice": lambda: px.blotchy(44, ICE, scales=((2, 1.0), (5, 0.4))),
        "packed_ice": lambda: px.cobbled(45, ICE, pal("#6a92cc", "#7aa0d6"), n=6),
        "soul_sand": lambda: px.blotchy(46, pal("#3a2a20", "#4a372a", "#5b4535", "#6b5341", "#7a614c"), speckle=0.12),
        "soul_soil": lambda: px.blotchy(47, pal("#33251c", "#413025", "#4f3b2e", "#5d4737", "#6b5341")),
        "sandstone": lambda: _sandstone(48, SANDSTONE, "side"), "sandstone_top": lambda: _sandstone(48, SANDSTONE, "top"),
        "sandstone_bottom": lambda: _sandstone(48, SANDSTONE, "bottom"), "cut_sandstone": lambda: px.bricks(49, SANDSTONE, SANDSTONE[:2], rows=2, per_row=1),
        "red_sandstone": lambda: _sandstone(50, RED_SANDSTONE, "side"), "red_sandstone_top": lambda: _sandstone(50, RED_SANDSTONE, "top"),
        "red_sandstone_bottom": lambda: _sandstone(50, RED_SANDSTONE, "bottom"),
        "cut_red_sandstone": lambda: px.bricks(51, RED_SANDSTONE, RED_SANDSTONE[:2], rows=2, per_row=1),
        "glass": lambda: px.glass(pal("#7d97a3", "#a9c3cd", "#e6f4f8")),
        "water_still": lambda: px.liquid(52, pal("#8f8f8f", "#a3a3a3", "#b8b8b8", "#cdcdcd"), alpha=200),
        "water_flow": lambda: px.liquid(53, pal("#8f8f8f", "#a3a3a3", "#b8b8b8", "#cdcdcd"), alpha=200),
        "lava_still": lambda: px.liquid(54, pal("#a8260a", "#d8470f", "#f9751c", "#ffaa38", "#ffe27a")),
        "lava_flow": lambda: px.liquid(55, pal("#a8260a", "#d8470f", "#f9751c", "#ffaa38", "#ffe27a")),
        "bookshelf": lambda: px.bookshelf(56, WOODS["oak"][0]),
        "hay_block_side": lambda: px.planks(57, pal("#8a6a12", "#a8841c", "#c39f2a", "#d8b63c", "#e8ca52"), boards=8),
    })
    for name, (planks, bark_pal) in WOODS.items():
        g.update(_wood(name, planks, bark_pal))
    for name, (seed, palette, blossoms) in LEAF_DEFS.items():
        g[name] = (lambda s=seed, p=palette, b=blossoms: px.leaves(s, p, blossoms=b))
    for i, (name, (cols, gem)) in enumerate(ORES.items()):
        g[f"{name}_ore"] = (lambda c=cols, gm=gem, s=70 + i: px.ore(stone(), s, c, gm))
        g[f"deepslate_{name}_ore"] = (lambda c=cols, gm=gem, s=80 + i: px.ore(deep(), s, c, gm))
    g["nether_gold_ore"] = lambda: px.ore(nether(), 90, ORES["gold"][0], False, clusters=4)
    g["nether_quartz_ore"] = lambda: px.ore(nether(), 91, pal("#b8aea6", "#e2dad3", "#ffffff"), True, clusters=4)
    for name, p in MINERALS.items():
        g[name] = (lambda pp=p, n=name: px.mineral_block(sum(map(ord, n)), pp))
    for i, (dye, col) in enumerate(DYES.items()):
        g[f"{dye}_wool"] = (lambda c=col, s=100 + i: px.wool(s, c))
        g[f"{dye}_concrete"] = (lambda c=col, s=120 + i: px.flat(s, c, noise=0.03))
        g[f"{dye}_concrete_powder"] = (lambda c=col, s=140 + i: px.flat(s, c, noise=0.08, grain=True))
        g[f"{dye}_terracotta"] = (lambda c=TERRACOTTA[dye], s=160 + i: px.flat(s, c, noise=0.07))
    g["terracotta"] = lambda: px.flat(180, "#985e43", noise=0.07)
    return g


def _side(dirt_tex, top_pal):
    """Podzol/mycelium-like sides: a band of the top colour over dirt."""
    out = dirt_tex.copy()
    for x in range(16):
        depth = 2 + (x * 7 % 3)
        for y in range(depth + 1):
            out[y, x, :3] = top_pal[3 if y == 0 else 2 if y < depth else 1]
    return out


def _sandstone(seed, p, kind):
    import numpy as np
    out = px.sand(seed, p, ripples=False)
    if kind == "side":
        for y in (0, 1, 2):
            out[y, :, :3] = p[4 - y // 2] if y < 2 else p[1]
        out[12:16, :, :3] = np.clip(out[12:16, :, :3].astype(int) - 10, 0, 255).astype(np.uint8)
        out[3, :, :3] = p[0]
        out[11, :, :3] = p[1]
    elif kind == "bottom":
        out[..., :3] = np.clip(out[..., :3].astype(int) - 8, 0, 255).astype(np.uint8)
    return out


TEXTURES = build()
ANIMATED = {"water_still": 2, "water_flow": 2, "lava_still": 3, "lava_flow": 3}
LEAVES = [n for n in TEXTURES if n.endswith("_leaves")]
# blocks that get extra random texture variants against visible repetition: name -> (count, generator(variant))
VARIANTS = {
    "stone": (2, lambda v: px.blotchy(1 + 97 * v, STONE, speckle=0.05)),
    "cobblestone": (2, lambda v: px.cobbled(4 + 97 * v, COBBLE, STONE_MORTAR)),
    "dirt": (2, lambda v: px.blotchy(2 + 97 * v, DIRT, scales=((3, 1.0), (6, 0.6)), speckle=0.07, speck_pal=DIRT_PEBBLE)),
    "sand": (1, lambda v: px.sand(39 + 97 * v, SAND)),
    "gravel": (1, lambda v: px.gravel(41 + 97 * v, GRAVEL, GRAVEL_GAP)),
    "deepslate": (1, lambda v: px.blotchy(14 + 97 * v, DEEPSLATE, scales=((2, 1.0), (5, 0.5)), speckle=0.04)),
    "andesite": (1, lambda v: px.blotchy(6 + 97 * v, ANDESITE, speckle=0.12)),
    "netherrack": (1, lambda v: px.blotchy(16 + 97 * v, NETHERRACK, scales=((3, 1.0), (6, 0.6)), speckle=0.08)),
    "end_stone": (1, lambda v: px.blotchy(19 + 97 * v, END_STONE, speckle=0.1, speck_pal=pal("#a39b6c", "#f3efd0"))),
    "oak_planks": (1, lambda v: px.planks(sum(map(ord, "oak")) + 97 * v, WOODS["oak"][0])),
    "spruce_planks": (1, lambda v: px.planks(sum(map(ord, "spruce")) + 97 * v, WOODS["spruce"][0])),
}
