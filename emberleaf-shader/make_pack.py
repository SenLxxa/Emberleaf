"""Zips the Emberleaf Glut shader into emberleaf/packs/shaderpacks (bundled with the launcher, shared via updates).

Run: python emberleaf-shader/make_pack.py   (bump VERSION for every change you want friends to get)
"""
import shutil
import sys
import zipfile
from pathlib import Path

VERSION = "1.0.0"
ROOT = Path(__file__).resolve().parent
OUT = ROOT.parent / "emberleaf" / "packs" / "shaderpacks"
TEST = ROOT.parent / "emberleaf-mod" / "build" / "test-assets" / "shaderpacks"


def main():
    name = f"Emberleaf-Glut-{VERSION}.zip"
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("Emberleaf-Glut-*.zip"):
        old.unlink()
    target = OUT / name
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for file in sorted((ROOT / "shaders").rglob("*")):
            if file.is_file():
                z.write(file, file.relative_to(ROOT).as_posix())
        z.write(ROOT / "LIESMICH.txt", "LIESMICH.txt")
    TEST.mkdir(parents=True, exist_ok=True)
    for old in TEST.glob("Emberleaf-Glut-*.zip"):
        old.unlink()
    shutil.copy2(target, TEST / name)
    print(target, target.stat().st_size)


if __name__ == "__main__":
    sys.exit(main())
