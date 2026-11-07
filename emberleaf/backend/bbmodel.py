"""Reads Blockbench models (.bbmodel) into Emberleaf's compact cosmetic format.

Convention for cosmetics (see the starter set): the player stands with the feet at y=0 and looks
north (-z); body from y=12 to 24, head from 24 to 32, units are pixels (1/16 block).

Output ("emodel", version 1):
  {"resolution": [w, h], "textures": [data-url, ...],
   "bones": [{"name", "parent": index|-1, "origin": [x,y,z], "rotation": [x,y,z],
              "cubes": [{"from", "to", "origin", "rotation", "inflate",
                         "faces": {"north": [u0, v0, u1, v1, texture, rotation], ...}}]}],
   "animations": [{"name", "loop", "length", "bones": {bone: {"rotation"|"position"|"scale": [[t, x, y, z, interp], ...]}}}]}
"""
import json

from .storage import UserError

MAX_TEXT = 8_000_000
MAX_CUBES = 1500
MAX_BONES = 256
FACES = ("north", "east", "south", "west", "up", "down")


def _vec(value, default=(0.0, 0.0, 0.0)):
    try:
        x, y, z = (float(v) for v in value)
        return [x, y, z]
    except (TypeError, ValueError):
        return list(default)


def _num(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0  # Molang expressions are not supported: treat as 0


def _box_uv(cube, offset, mirror):
    """Per-face UVs for Blockbench's "box UV" layout."""
    fx, fy, fz = cube["from"]
    tx, ty, tz = cube["to"]
    w, h, d = round(abs(tx - fx)), round(abs(ty - fy)), round(abs(tz - fz))
    u, v = offset
    faces = {
        "east": [u, v + d, u + d, v + d + h],
        "north": [u + d, v + d, u + d + w, v + d + h],
        "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "up": [u + d + w, v + d, u + d, v],
        "down": [u + d + 2 * w, v, u + d + w, v + d],
    }
    if mirror:
        faces = {k: [f[2], f[1], f[0], f[3]] for k, f in faces.items()}
        faces["east"], faces["west"] = faces["west"], faces["east"]
    return {k: f + [0, 0] for k, f in faces.items()}


def parse(text):
    if len(text) > MAX_TEXT:
        raise UserError("Das Modell ist zu groß (maximal 8 MB).")
    try:
        data = json.loads(text)
    except ValueError as e:
        raise UserError("Das ist keine gültige Blockbench-Datei (.bbmodel).") from e
    if not isinstance(data, dict) or "elements" not in data:
        raise UserError("Das ist keine gültige Blockbench-Datei (.bbmodel).")

    res = data.get("resolution") or {}
    resolution = [int(res.get("width", 16)), int(res.get("height", 16))]
    textures = []
    for t in data.get("textures", []):
        source = str(t.get("source", ""))
        if not source.startswith("data:image/png;base64,"):
            raise UserError("Eine Textur fehlt. Speichere das Modell in Blockbench mit eingebetteter Textur (Datei > Speichern).")
        textures.append(source)
    if not textures:
        raise UserError("Das Modell hat keine Textur. Male in Blockbench eine Textur und speichere erneut.")

    cubes_by_uuid = {}
    for el in data["elements"]:
        if el.get("type", "cube") != "cube" or el.get("visibility") is False:
            continue
        cube = {"from": _vec(el.get("from")), "to": _vec(el.get("to")), "origin": _vec(el.get("origin")),
                "rotation": _vec(el.get("rotation")), "inflate": _num(el.get("inflate", 0))}
        if el.get("box_uv"):
            cube["faces"] = _box_uv(cube, _vec(list(el.get("uv_offset", [0, 0])) + [0])[:2], bool(el.get("mirror_uv")))
        else:
            faces = {}
            for name in FACES:
                f = (el.get("faces") or {}).get(name)
                if not f or f.get("texture") is None:
                    continue
                uv = [_num(v) for v in f.get("uv", [0, 0, 0, 0])]
                faces[name] = uv + [int(f.get("texture", 0)), int(_num(f.get("rotation", 0)))]
            cube["faces"] = faces
        cubes_by_uuid[el.get("uuid")] = cube
    if len(cubes_by_uuid) > MAX_CUBES:
        raise UserError(f"Das Modell hat zu viele Würfel (maximal {MAX_CUBES}).")
    if not cubes_by_uuid:
        raise UserError("Das Modell ist leer.")

    bones = [{"name": "root", "parent": -1, "origin": [0, 0, 0], "rotation": [0, 0, 0], "cubes": []}]
    bone_by_uuid = {}
    used = set()

    def walk(node, parent):
        if isinstance(node, str):
            cube = cubes_by_uuid.get(node)
            if cube is not None:
                bones[parent]["cubes"].append(cube)
                used.add(node)
            return
        if not isinstance(node, dict) or node.get("visibility") is False:
            return
        if len(bones) >= MAX_BONES:
            raise UserError(f"Das Modell hat zu viele Gruppen (maximal {MAX_BONES}).")
        index = len(bones)
        bones.append({"name": str(node.get("name", "group"))[:40], "parent": parent, "origin": _vec(node.get("origin")),
                      "rotation": _vec(node.get("rotation")), "cubes": []})
        bone_by_uuid[node.get("uuid")] = index
        for child in node.get("children", []):
            walk(child, index)

    for node in data.get("outliner", []):
        walk(node, 0)
    for uuid, cube in cubes_by_uuid.items():
        if uuid not in used:
            bones[0]["cubes"].append(cube)

    animations = []
    for anim in data.get("animations", [])[:16]:
        out = {"name": str(anim.get("name", "animation"))[:40], "loop": anim.get("loop", "loop") == "loop",
               "length": max(0.05, _num(anim.get("length", 1))), "bones": {}}
        for uuid, animator in (anim.get("animators") or {}).items():
            bone = bone_by_uuid.get(uuid)
            if bone is None:
                continue
            channels = {}
            for kf in animator.get("keyframes", []):
                channel = kf.get("channel")
                if channel not in ("rotation", "position", "scale") or not kf.get("data_points"):
                    continue
                p = kf["data_points"][0]
                channels.setdefault(channel, []).append(
                    [_num(kf.get("time", 0)), _num(p.get("x")), _num(p.get("y")), _num(p.get("z")), str(kf.get("interpolation", "linear"))])
            for frames in channels.values():
                frames.sort(key=lambda f: f[0])
            if channels:
                out["bones"][str(bone)] = channels
        if out["bones"]:
            animations.append(out)

    return {"format": "emodel", "version": 1, "resolution": resolution, "textures": textures, "bones": bones, "animations": animations}
