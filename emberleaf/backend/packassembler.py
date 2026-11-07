"""Builds the "Emberleaf Texturen" resource pack on this PC from the packs the player picked.

Every pack is downloaded from its official CurseForge page (the same files a player would download by hand),
nothing is shipped or shared by Emberleaf. On this PC they are stacked into one pack for Minecraft 26.3:
  Faithful 64x as the base, 3D models (bars, plants, flowers, ladders, lanterns, torches, beds), Better Leaves
  with leaves regenerated in 64x, glowing ores, enchanted books, 3D tools and armour, cubic sun & moon,
  zombie variants, boss and player animations, icons, minimap icons and ambient sounds.
Overlays are resolved for 26.3, sounds/lang/atlas/font files are merged instead of overwritten, and a few
things are tuned for Emberleaf (books, glass, beds, ores). Credits for every pack go into the result.

Test run:  python -m backend.packassembler <out.zip> [cache-dir]
"""
import fnmatch
import io
import json
import re
import sys
import time
import zipfile
from pathlib import Path
from urllib.parse import quote

import numpy as np
import requests
from PIL import Image

from . import storage
from .storage import UserError
from .version import USER_AGENT

GENERATOR_VERSION = "3.0.0"
PACK_FAMILY = "Emberleaf-Texturen"
LEGACY_FAMILIES = ("Emberleaf-64x",)
FORMAT = 97.1  # Minecraft 26.3
GENERATED_DIR = storage.DATA_DIR / "generated" / "resourcepack"
CACHE_DIR = storage.DATA_DIR / "packcache"
CDN = "https://mediafilez.forgecdn.net/files/{a}/{b}/{name}"
HEADERS = {"User-Agent": USER_AGENT}
# mods the pack needs in the game: entity models/animations (EMF), random + glowing mobs (ETF),
# connected glass and glowing ores (Continuity); Iris (+ Sodium) for the shader
REQUIRED_MODS = ("iris", "entity-model-features", "entitytexturefeatures", "continuity")


def src(slug, file_id, file_name, size, title, author, license_, include=("assets/**",), exclude=(), project=None, transform=None):
    return {"slug": slug, "file_id": file_id, "file_name": file_name, "size": size, "title": title, "author": author,
            "license": license_, "include": include, "exclude": exclude, "transform": transform,
            "url": f"https://www.curseforge.com/minecraft/texture-packs/{project or slug}"}


def _flowers_1_18(path, data):
    """Flowers 3D is from 1.18 and keeps its textures in "blocks/", which 26.3 no longer stitches into the atlas."""
    if path.startswith("assets/minecraft/textures/blocks/"):
        return "assets/minecraft/textures/block/flowers3d/" + path.rsplit("/", 1)[1], data
    if path.endswith(".json"):
        data = data.replace(b"\"blocks/", b"\"block/flowers3d/").replace(b"\"minecraft:blocks/", b"\"minecraft:block/flowers3d/")
    return path, data


def _prime_fixes(path, data):
    """Prime's copper bars have a typo in one model (weathred)."""
    return path, data.replace(b"weathred_copper", b"weathered_copper")


# lowest priority first: later packs win where two packs change the same file
SOURCES = [
    src("faithful-64x", 8887820, "Faithful 64x - Release 15.zip", 19411638, "Faithful 64x", "Faithful Resource Pack team",
        "Faithful License"),
    src("primes-hd-textures", 9397954, "Prime-s-HD-Textures-32x.zip", 21869204, "Prime's HD Textures (3D-Gitter & Keimlinge)", "PrimeDream",
        "All Rights Reserved",
        include=("assets/minecraft/models/block/*bars*.json", "assets/minecraft/models/block/mangrove_propagule_hanging_*.json",
                 "assets/minecraft/textures/block/mangrove_propagule_hanging.png"), transform=_prime_fixes),
    src("realistic-nature", 9069727, "3D Nature - MC 26.1.2 - 2.0.0.zip", 672076, "3D Nature", "Ferrum", "All Rights Reserved"),
    src("flowers-3d", 6328986, "3D_Flowers_1.18_0.1.0.zip", 171238, "Flowers 3D", "Kai", "All Rights Reserved",
        exclude=("assets/minecraft/models/block/flower_pot_*", "assets/minecraft/models/block/houstonia.json",
                 "assets/minecraft/models/block/orchid.json", "assets/minecraft/models/block/daisy.json",
                 "assets/minecraft/models/item/*"), transform=_flowers_1_18),
    src("better-lanterns", 8908152, "Better Lanterns v1.4.0 - 26.2+26.3.zip", 83775, "Better Lanterns", "Nico4play", "All Rights Reserved",
        include=("assets/minecraft/**",)),
    src("nicos-3d-ladders", 8914180, "§l3D§f Ladders v1.2.2 - 26.3.zip", 11814, "3D Ladders", "Nico4play", "All Rights Reserved"),
    src("torches-reimagined", 9290731, "§eTorches§8-§eReimagined§0_§8[v1.10]§0.zip", 191667, "Torches Reimagined",
        "Reijvi", "All Rights Reserved"),
    src("fancy-beds", 8672129, "Fancy_Beds-v4.2.zip", 271419, "Fancy Beds", "Agente_511", "All Rights Reserved"),
    src("motschens-better-leaves", 8882877, "Better-Leaves-9.6.zip", 1977146, "Motschen's Better Leaves", "Motschen", "MIT License",
        include=("assets/minecraft/**", "assets/betterleaves/**")),
    src("clear-32x", 8966147, "Clear 32x 26.2.zip", 356948, "Clear 32x", "PrimeDream", "MIT License"),
    src("new-glowing-ores", 5836470, "NewGlowingOres-§6[Border]§r.zip", 344937, "New Glowing Ores (Idee, in 64x neu gemalt)",
        "GridExpert", "CC BY-NC-SA 4.0", include=("assets/minecraft/optifine/emissive.properties",)),
    src("enchantment-glows", 9103929, "Enchantment Glows v1.12.1.zip", 2126138, "Enchantment Glows", "Sinnelar", "All Rights Reserved"),
    src("fantasy-3d-tools", 9187513, "Fantasy-3D-Tools-v1.3.zip", 1332074, "Fantasy 3D Tools", "Terra Media", "All Rights Reserved"),
    src("fantasy-3d-armors", 9187719, "Fantasy-3D-Armors-v1.3.zip", 5576829, "Fantasy 3D Armors", "Terra Media", "All Rights Reserved"),
    src("zerovision-enchants", 8839581, "Zerovision_Enchants_v1.0.zip", 948871, "ZeroVision Enchants", "Zerotekz", "All Rights Reserved"),
    src("cubic-sun-moon", 7381066, "cubic-sun-moon-v1.8.5.zip", 16194897, "Cubic Sun & Moon", "JoeFly", "All Rights Reserved",
        include=("assets/minecraft/textures/environment/celestial/**",)),
    src("tissous-zombie-pack", 4750306, "TZP_1.20.1_2.7.zip", 14177090, "Tissou's Zombie Pack", "Tissou", "All Rights Reserved",
        project="tissous-zombie-pack-optifine-1-7x-1-20"),
    src("boss-refreshed", 6597394, "boss-refreshed-v2-1.19-1.21.zip", 237777, "Boss Refreshed", "Lukidon", "All Rights Reserved"),
    src("fresh-moves", 6105679, "+1.21.3 Fresh Moves v3.1.1 (With Animated Eyes).zip", 273002, "Fresh Moves", "FreshLX", "CC BY-NC-SA 4.0"),
    src("icons", 8906797, "Icons v.1.14.zip", 9414964, "Icons", "WeNAN Studios", "All Rights Reserved",
        include=("assets/minecraft/**", "assets/icons/**")),
    src("icon-fresh", 8990214, "Icon Fresh 0.5.zip", 632713, "Icon Fresh (Minimap-Icons)", "Kyo98", "All Rights Reserved"),
    src("ambience-sounds-pack", 9403140, "AmbientSound6_R14.zip", 48796783, "Ambience Sounds Pack", "Ambience team", "LGPL-2.1"),
    src("elytras-more-elytras", 3361532, "Elytras+.zip", 120328, "Elytras+", "TheTerrain", "All Rights Reserved"),
]
# the player asked for these too, but they only work together with other mods (not part of Emberleaf):
NOT_USED = {
    "better-end-city-for-better-end": "ein Datenpaket für die Mod „Better End“ (Weltgenerierung), keine Texturen",
    "aquaculture-updated": "Texturen nur für die Mod „Aquaculture“",
    "glowing-trims": "braucht die alte OptiFine-CIT-Technik; leuchtende Rüstungsbesätze macht jetzt der Emberleaf-Client selbst",
}
MERGE_JSON = re.compile(r"^assets/[^/]+/(sounds\.json|lang/[^/]+\.json|atlases/[^/]+\.json|font/[^/]+\.json)$")
SKIP = re.compile(r"(^|/)(\.rpo|[^/]*_rpo/|\.vscode/|\.DS_Store|Thumbs\.db|desktop\.ini)|\.(psd|txt|md|bbmodel|xcf|ase)$", re.I)


# ---------- Herunterladen ----------

def _cache_file(s, cache_dir):
    return Path(cache_dir) / f"{s['slug']}-{s['file_id']}.zip"


def download_all(cache_dir=None, progress=None):
    cache_dir = Path(cache_dir or CACHE_DIR)
    cache_dir.mkdir(parents=True, exist_ok=True)
    total = sum(s["size"] for s in SOURCES)
    done = 0
    for s in SOURCES:
        target = _cache_file(s, cache_dir)
        if not (target.is_file() and target.stat().st_size == s["size"]):
            fid = s["file_id"]
            url = CDN.format(a=fid // 1000, b=fid % 1000, name=quote(s["file_name"]))
            tmp = target.with_suffix(".part")
            try:
                with requests.get(url, headers=HEADERS, timeout=60, stream=True) as r:
                    if r.status_code != 200:
                        raise UserError(f"{s['title']} konnte nicht geladen werden ({r.status_code}).")
                    with tmp.open("wb") as out:
                        for chunk in r.iter_content(256 * 1024):
                            out.write(chunk)
                            if progress:
                                progress("download", done + tmp.stat().st_size, total, s["title"])
            except requests.RequestException as e:
                raise UserError(f"{s['title']} konnte nicht geladen werden. Bist du online?") from e
            if tmp.stat().st_size != s["size"]:
                tmp.unlink(missing_ok=True)
                raise UserError(f"{s['title']} kam unvollständig an.")
            tmp.replace(target)
        done += s["size"]
        if progress:
            progress("download", done, total, s["title"])
    return cache_dir


# ---------- Paket lesen (mit Overlays für 26.3) ----------

def _fmt(v):
    if isinstance(v, (list, tuple)):
        return float(v[0]) + (float(v[1]) / 10 if len(v) > 1 else 0)
    return float(v)


def _overlay_applies(entry):
    lo = hi = None
    if "min_format" in entry or "max_format" in entry:
        lo = _fmt(entry.get("min_format", 0))
        hi = _fmt(entry.get("max_format", 9999))
    elif "formats" in entry:
        f = entry["formats"]
        if isinstance(f, dict):
            lo, hi = _fmt(f.get("min_inclusive", 0)), _fmt(f.get("max_inclusive", 9999))
        elif isinstance(f, list) and len(f) == 2:
            lo, hi = _fmt(f[0]), _fmt(f[1])
        else:
            lo = hi = _fmt(f)
    if lo is None:
        return False
    return lo <= FORMAT and (hi >= FORMAT or hi >= int(FORMAT))


def effective_files(z):
    """{path inside a normal pack: zip entry name} after applying the overlays that fit Minecraft 26.3."""
    names = [n for n in z.namelist() if not n.endswith("/")]
    metas = [n for n in names if n.endswith("pack.mcmeta")]
    root = min(metas, key=len).rsplit("pack.mcmeta", 1)[0] if metas else ""
    files = {}
    for n in names:
        if n.startswith(root):
            files[n[len(root):]] = n
    meta = {}
    if metas:
        try:
            meta = json.loads(z.read(root + "pack.mcmeta").decode("utf-8-sig"))
        except (ValueError, KeyError):
            meta = {}
    overlays = meta.get("overlays", {}).get("entries", []) if isinstance(meta.get("overlays"), dict) else []
    overlay_dirs = [e.get("directory", "") for e in overlays]
    base = {k: v for k, v in files.items() if not any(k.startswith(d + "/") for d in overlay_dirs if d)}
    for e in overlays:
        d = e.get("directory", "")
        if d and _overlay_applies(e):
            for k, v in files.items():
                if k.startswith(d + "/"):
                    base[k[len(d) + 1:]] = v
    return base


def _matches(path, patterns):
    return any(fnmatch.fnmatchcase(path, p) for p in patterns)


# ---------- Zusammenführen ----------

def _merge_json(path, old, new):
    try:
        a = json.loads(old.decode("utf-8-sig"))
        b = json.loads(new.decode("utf-8-sig"))
    except ValueError:
        return new
    if path.endswith("sounds.json") or "/lang/" in path:
        a.update(b)
        return json.dumps(a, ensure_ascii=False).encode("utf-8")
    key = "sources" if "/atlases/" in path else "providers"
    if isinstance(a.get(key), list) and isinstance(b.get(key), list):
        seen = {json.dumps(x, sort_keys=True) for x in a[key]}
        a[key] += [x for x in b[key] if json.dumps(x, sort_keys=True) not in seen]
        return json.dumps(a, ensure_ascii=False).encode("utf-8")
    return new


def _png(arr):
    buf = io.BytesIO()
    Image.fromarray(np.asarray(arr, np.uint8), "RGBA").save(buf, "PNG", optimize=True)
    return buf.getvalue()


def _img(data):
    return np.asarray(Image.open(io.BytesIO(data)).convert("RGBA"), np.uint8)


# ---------- Emberleaf-Anpassungen ----------

LEAF_TEXTURE = re.compile(r"assets/minecraft/textures/block/[a-z_]+_leaves\.png$")


def _better_leaves_64(out, bl_files, bl_zip, faithful_leaves):
    """Better Leaves uses 2x textures (leaf in the middle, bushy border around). Rebuild them from the
    64x Faithful leaves (as they were before Better Leaves replaced them) so the bushes match the rest of the
    pack (Better Leaves is MIT licensed)."""
    count = 0
    for path, entry in bl_files.items():
        if not LEAF_TEXTURE.match(path):
            continue
        faithful = faithful_leaves.get(path)
        if faithful is None:
            continue
        hd = _img(faithful)
        bl = _img(bl_zip.read(entry))
        n = hd.shape[0]
        if hd.shape[0] != hd.shape[1] or bl.shape[0] != bl.shape[1] or bl.shape[0] < 32:
            continue
        if n <= bl.shape[0] // 2:  # Faithful texture is not bigger than BL's centre: keep BL's own
            continue
        k = 2 * n // bl.shape[0]
        mask = np.repeat(np.repeat(bl[..., 3], k, 0), k, 1)[: 2 * n, : 2 * n] > 0
        tiled = np.tile(hd, (3, 3, 1))[n // 2: n // 2 + 2 * n, n // 2: n // 2 + 2 * n]
        result = tiled.copy()
        inner = np.zeros((2 * n, 2 * n), bool)
        inner[n // 2: n // 2 + n, n // 2: n // 2 + n] = True
        result[..., 3] = np.where(inner, tiled[..., 3], np.where(mask, tiled[..., 3], 0))
        out[path] = _png(result)
        count += 1
    return count


ORES = {
    "coal_ore": "stone", "iron_ore": "stone", "copper_ore": "stone", "gold_ore": "stone", "redstone_ore": "stone",
    "lapis_ore": "stone", "diamond_ore": "stone", "emerald_ore": "stone",
    "deepslate_coal_ore": "deepslate", "deepslate_iron_ore": "deepslate", "deepslate_copper_ore": "deepslate",
    "deepslate_gold_ore": "deepslate", "deepslate_redstone_ore": "deepslate", "deepslate_lapis_ore": "deepslate",
    "deepslate_diamond_ore": "deepslate", "deepslate_emerald_ore": "deepslate",
    "nether_gold_ore": "netherrack", "nether_quartz_ore": "netherrack",
}


def _glowing_ores(out):
    """New-Glowing-Ores style in 64x: the ore spots glow (emissive *_e textures for Continuity/Iris) and a soft
    border in the ore's colour makes them easy to spot, a little calmer than the original."""
    made = 0
    for ore, base_name in ORES.items():
        path = f"assets/minecraft/textures/block/{ore}.png"
        base_path = f"assets/minecraft/textures/block/{base_name}.png"
        if path not in out or base_path not in out:
            continue
        o = _img(out[path]).astype(np.int32)
        b = _img(out[base_path]).astype(np.int32)
        if o.shape != b.shape or o.shape[0] < 16:
            continue
        diff = np.abs(o[..., :3] - b[..., :3]).max(-1)
        spots = diff > 38
        # remove single noise pixels: keep pixels with at least two spot neighbours
        nb = sum(np.roll(np.roll(spots, dy, 0), dx, 1) for dy in (-1, 0, 1) for dx in (-1, 0, 1)) - spots
        spots &= nb >= 2
        if spots.sum() < 8:
            continue
        colour = np.median(o[spots][:, :3], 0)
        n = o.shape[0]
        edge = max(1, n // 32)
        yy, xx = np.mgrid[0:n, 0:n]
        dist = np.minimum(np.minimum(xx, n - 1 - xx), np.minimum(yy, n - 1 - yy))
        border = dist < edge
        tex = o.copy()
        tex[border, :3] = (tex[border, :3] * 0.45 + colour * 0.55).astype(np.int32)
        out[path] = _png(np.clip(tex, 0, 255))
        glow = np.zeros_like(o)
        glow[spots, :3] = np.clip(o[spots][:, :3] * 1.12 + 6, 0, 255)
        glow[spots, 3] = 255
        glow[border, :3] = np.clip(colour * 1.1, 0, 255)
        glow[border, 3] = 150
        out[f"assets/minecraft/textures/block/{ore}_e.png"] = _png(np.clip(glow, 0, 255))
        made += 1
    out["assets/minecraft/optifine/emissive.properties"] = b"suffix.emissive=_e\n"
    return made


def _warm_books(out, book_paths):
    """ZeroVision books in Emberleaf colours: a bit richer and warmer."""
    for p in book_paths:
        if p not in out:
            continue
        a = _img(out[p]).astype(np.float32) / 255
        rgb = a[..., :3]
        lum = rgb @ np.array([0.2126, 0.7152, 0.0722], np.float32)
        rgb = lum[..., None] + (rgb - lum[..., None]) * 1.18
        w = np.clip((lum - 0.35) / 0.5, 0, 1)[..., None]
        rgb = rgb * (np.array([1.0, 1.0, 1.0]) * (1 - w) + np.array([1.06, 1.01, 0.92]) * w)
        a[..., :3] = np.clip(rgb, 0, 1)
        out[p] = _png((a * 255 + 0.5).astype(np.uint8))


def _clearer_glass(out):
    """Clear 32x, even clearer: the frame of plain glass becomes softer."""
    for p in [k for k in out if re.match(r"assets/minecraft/(textures/block|optifine/ctm/[^/]+(/[^/]+)*)/glass[^/]*\.png$", k)
              and "stained" not in k and "pane" not in k]:
        try:
            a = _img(out[p]).astype(np.float32)
        except OSError:
            continue
        a[..., 3] = np.where(a[..., 3] > 0, np.clip(a[..., 3] * 0.72, 0, 255), 0)
        out[p] = _png(a.astype(np.uint8))


BED_SYMBOLS = {  # colour: 7x7 symbol painted onto the blanket ("#" = symbol, "+" = highlight)
    "orange": ["...#...", "..###..", ".#####.", "#######", ".#+#+#.", "...#...", "...#..."],   # maple leaf
    "red": ["...#...", "..##...", "..###..", ".#####.", ".##+##.", "#######", ".#####."],       # ember flame
    "yellow": ["#..#..#", ".#.#.#.", "..###..", "###+###", "..###..", ".#.#.#.", "#..#..#"],   # sun
    "green": ["...#...", "..###..", ".#####.", "..###..", ".#####.", "#######", "...#..."],     # pine tree
    "light_blue": ["...#...", "#..#..#", ".#.#.#.", "..###..", ".#.#.#.", "#..#..#", "...#..."],  # snowflake
    "purple": ["..###..", ".#...#.", "#..#..#", "#.###.#", "#..#..#", ".#...#.", "..###.."],    # ender eye
}


def _bed_symbols(out):
    """Fancy Beds, made a bit more Emberleaf: some colours get their own symbol on the blanket."""
    made = 0
    for colour, pattern in BED_SYMBOLS.items():
        p = f"assets/minecraft/textures/block/{colour}_bed_foot_up.png"
        if p not in out:
            continue
        a = _img(out[p]).astype(np.int32)
        n = a.shape[0]
        k = max(1, n // 16)
        cloth = np.median(a[n // 4: 3 * n // 4, n // 4: 3 * n // 4, :3].reshape(-1, 3), 0)
        lum = cloth @ np.array([0.2126, 0.7152, 0.0722])
        sym = np.array([255, 236, 190]) if lum < 150 else np.array([90, 50, 28])
        hi = np.clip(sym * 1.1 + 20, 0, 255)
        x0 = (n - 7 * k) // 2
        y0 = (n - 7 * k) // 2
        for y, row in enumerate(pattern):
            for x, ch in enumerate(row):
                if ch in "#+":
                    a[y0 + y * k: y0 + (y + 1) * k, x0 + x * k: x0 + (x + 1) * k, :3] = hi if ch == "+" else sym
        out[p] = _png(np.clip(a, 0, 255).astype(np.uint8))
        made += 1
    return made


def _elytra_icons(out):
    """Elytras+ was made for OptiFine: name-based elytra icons become a normal 26.3 item definition
    (the worn elytra is switched by the Emberleaf client itself)."""
    cases = []
    for p in sorted(k for k in out if k.startswith("assets/minecraft/optifine/cit/") and k.endswith(".properties")):
        props = {}
        for line in out[p].decode("utf-8", "replace").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, val = line.split("=", 1)
                props[key.strip()] = val.strip()
        if props.get("type") != "item" or "elytra" not in props.get("items", ""):
            continue
        tex = props.get("texture", "")
        name = props.get("nbt.display.Name", "") or props.get("components.custom_name", "")
        m = re.match(r"i?(?:regex|pattern):\(?([^)]*)\)?$", name) or re.match(r"()(.+)$", name)
        if not tex or not m:
            continue
        alts = [x for x in (m.group(1) or name).split("|") if x and re.fullmatch(r"[A-Za-z0-9 _'\-]+", x)]
        tex_path = tex[:-4] if tex.endswith(".png") else tex
        base = p.rsplit("/", 1)[0]
        if not tex_path.startswith("optifine/"):
            tex_path = base.split("assets/minecraft/")[1] + "/" + tex_path
        src_png = f"assets/minecraft/{tex_path}.png"
        if src_png not in out:
            continue
        key = re.sub(r"[^a-z0-9_]", "_", Path(tex_path).name.lower())
        out[f"assets/emberleaf/textures/item/elytra/{key}.png"] = out[src_png]
        out[f"assets/emberleaf/models/item/elytra/{key}.json"] = json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"emberleaf:item/elytra/{key}"}}).encode()
        for alt in alts:
            for variant in {alt, alt.lower(), alt.upper(), alt.capitalize()}:
                cases.append({"when": variant, "model": {"type": "minecraft:model", "model": f"emberleaf:item/elytra/{key}"}})
    if not cases:
        return 0
    base_def = {"type": "minecraft:condition", "property": "minecraft:broken",
                "on_true": {"type": "minecraft:model", "model": "minecraft:item/elytra_broken"},
                "on_false": {"type": "minecraft:model", "model": "minecraft:item/elytra"}}
    out["assets/minecraft/items/elytra.json"] = json.dumps({"model": {
        "type": "minecraft:select", "property": "minecraft:component", "component": "minecraft:custom_name",
        "cases": cases, "fallback": base_def}}, ensure_ascii=False).encode("utf-8")
    return len(cases)


def _clean_sounds(out, vanilla_sounds):
    """Drop sound entries that point to files neither the pack nor Minecraft has (old packs reference some)."""
    if vanilla_sounds is None:
        return 0
    removed = 0
    for path in [p for p in out if p.endswith("/sounds.json")]:
        ns = path.split("/")[1]
        try:
            events = json.loads(out[path].decode("utf-8-sig"))
        except ValueError:
            continue
        for name in list(events):
            ev = events[name]
            if not isinstance(ev, dict) or not isinstance(ev.get("sounds"), list):
                continue
            keep = []
            for s in ev["sounds"]:
                entry = s if isinstance(s, dict) else {"name": s}
                if entry.get("type", "file") != "file":
                    keep.append(s)
                    continue
                snd = str(entry.get("name", ""))
                sns, spath = snd.split(":", 1) if ":" in snd else (ns, snd)
                if f"assets/{sns}/sounds/{spath}.ogg" in out or f"{sns}/sounds/{spath}.ogg" in vanilla_sounds:
                    keep.append(s)
                else:
                    removed += 1
            if keep:
                ev["sounds"] = keep
            else:
                del events[name]
        out[path] = json.dumps(events, ensure_ascii=False).encode("utf-8")
    return removed


def vanilla_sound_index(index_files):
    """All sound files Minecraft itself has, from the launcher's asset indexes."""
    sounds = set()
    for f in index_files:
        try:
            for key in json.loads(Path(f).read_text("utf-8")).get("objects", {}):
                if "/sounds/" in key and key.endswith(".ogg"):
                    sounds.add(key)
        except (OSError, ValueError):
            continue
    return sounds or None


def _credits():
    lines = ["Emberleaf Texturen " + GENERATOR_VERSION, "",
             "Auf diesem PC aus diesen Paketen zusammengebaut (jeweils von der offiziellen Seite geladen,",
             "nur für den eigenen Gebrauch, nicht weitergeben):", ""]
    for s in SOURCES:
        lines.append(f"- {s['title']} von {s['author']} ({s['license']}) – {s['url']}")
    lines += ["", "Emberleaf-Anpassungen: Better-Leaves-Büsche in 64x, leuchtende Erze in 64x, wärmere Buchfarben,",
              "klareres Glas, eigene Bettsymbole, Elytra-Namen für 26.3."]
    return "\n".join(lines)


def pack_name():
    return f"{PACK_FAMILY}-{GENERATOR_VERSION}.zip"


def assemble(out_zip, cache_dir=None, progress=None, vanilla_sounds=None):
    started = time.time()
    cache_dir = download_all(cache_dir, progress)
    out = {}
    stats = {}
    zips = {}
    bl_files = None
    faithful_leaves = {}
    zero_books = []
    try:
        for i, s in enumerate(SOURCES):
            if progress:
                progress("merge", i, len(SOURCES), s["title"])
            z = zipfile.ZipFile(_cache_file(s, cache_dir))
            zips[s["slug"]] = z
            files = effective_files(z)
            used = 0
            for path, entry in files.items():
                if not path.startswith("assets/") or SKIP.search(path):
                    continue
                if not _matches(path, s["include"]) or _matches(path, s["exclude"]):
                    continue
                data = z.read(entry)
                if s["transform"]:
                    path, data = s["transform"](path, data)
                if s["slug"] == "motschens-better-leaves" and path in out and LEAF_TEXTURE.match(path):
                    faithful_leaves[path] = out[path]  # the 64x leaf, Better Leaves' bushes get rebuilt from it
                if MERGE_JSON.match(path) and path in out:
                    out[path] = _merge_json(path, out[path], data)
                else:
                    out[path] = data
                if s["slug"] == "zerovision-enchants" and path.startswith("assets/minecraft/textures/item/") and path.endswith(".png"):
                    zero_books.append(path)
                used += 1
            if s["slug"] == "motschens-better-leaves":
                bl_files = files
            stats[s["slug"]] = used
        if progress:
            progress("merge", len(SOURCES), len(SOURCES), "Emberleaf-Feinschliff")
        stats["leaves_64x"] = _better_leaves_64(out, bl_files or {}, zips["motschens-better-leaves"], faithful_leaves)
        stats["glowing_ores"] = _glowing_ores(out)
        _warm_books(out, zero_books)
        _clearer_glass(out)
        stats["bed_symbols"] = _bed_symbols(out)
        stats["elytra_names"] = _elytra_icons(out)
        stats["dead_sounds_removed"] = _clean_sounds(out, vanilla_sounds)
    finally:
        for z in zips.values():
            z.close()
    out_zip = Path(out_zip)
    out_zip.parent.mkdir(parents=True, exist_ok=True)
    tmp = out_zip.with_suffix(".part")
    with zipfile.ZipFile(tmp, "w") as zout:
        for path in sorted(out):
            data = out[path]
            stored = path.endswith((".png", ".ogg"))
            zout.writestr(path, data, compress_type=zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED)
        zout.writestr("pack.mcmeta", json.dumps({"pack": {
            "description": "§6Emberleaf Texturen§r – Faithful 64x & Freunde, für Emberleaf zusammengestellt",
            "min_format": [97, 0], "max_format": [97, 1]}}, ensure_ascii=False, indent=1), compress_type=zipfile.ZIP_DEFLATED)
        logo = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent)) / "ui" / "assets" / "logo.png"
        if logo.is_file():
            buf = io.BytesIO()
            Image.open(logo).convert("RGBA").resize((128, 128), Image.LANCZOS).save(buf, "PNG")
            zout.writestr("pack.png", buf.getvalue())
        zout.writestr("CREDITS.txt", _credits(), compress_type=zipfile.ZIP_DEFLATED)
        zout.writestr("assets/emberleaf/emberleaf_pack.json", json.dumps({"generator": GENERATOR_VERSION, "dark_hud": False,
                                                                           "sources": [s["slug"] for s in SOURCES]}))
    tmp.replace(out_zip)
    stats["files"] = len(out)
    stats["seconds"] = round(time.time() - started, 1)
    stats["bytes"] = out_zip.stat().st_size
    return stats


def ensure(progress=None, asset_indexes=()):
    """Builds the pack once per generator version. Returns its path."""
    target = GENERATED_DIR / pack_name()
    if target.is_file():
        return target
    GENERATED_DIR.mkdir(parents=True, exist_ok=True)
    for family in (PACK_FAMILY, *LEGACY_FAMILIES):
        for old in GENERATED_DIR.glob(f"{family}-*"):
            old.unlink(missing_ok=True)
    assemble(target, progress=progress, vanilla_sounds=vanilla_sound_index(asset_indexes))
    return target


if __name__ == "__main__":
    def show(stage, done, total, what):
        print(f"\r{stage} {done}/{total} {what[:40]:40}", end="", flush=True)

    index = vanilla_sound_index(sys.argv[3:]) if len(sys.argv) > 3 else None
    result = assemble(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None, show, index)
    print("\n" + json.dumps(result, indent=1))
