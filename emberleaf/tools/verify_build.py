"""Checks a built Emberleaf.exe and the release files before they go to everyone.

Unpacks every part of the PyInstaller archive (zlib notices damaged data) and opens every zip/jar completely
(CRC check). Catches files that were damaged while building, e.g. by a flaky RAM bit flip.

    python tools/verify_build.py dist/release-0.0.3
"""
import sys
import zipfile
import zlib
from pathlib import Path

from PyInstaller.archive.readers import CArchiveReader, ZlibArchiveReader


def check_exe(path):
    archive = CArchiveReader(str(path))
    count = 0
    for name, (*_, typecode) in archive.toc.items():
        data = archive.extract(name)  # raises when a compressed part is damaged
        count += 1
        if typecode == "z":  # the PYZ with all Python modules
            pyz_path = path.with_name(path.name + ".pyz.check")
            pyz_path.write_bytes(data)
            try:
                pyz = ZlibArchiveReader(str(pyz_path))
                for module in pyz.toc:
                    pyz.extract(module)
                    count += 1
            finally:
                pyz_path.unlink(missing_ok=True)
        elif name.lower().endswith((".zip", ".jar")):
            tmp = path.with_name("check-" + Path(name).name)
            tmp.write_bytes(data)
            try:
                count += check_zip(tmp)
            finally:
                tmp.unlink(missing_ok=True)
    return count


def check_zip(path):
    with zipfile.ZipFile(path) as z:
        bad = z.testzip()
        if bad:
            raise ValueError(f"{path.name}: {bad} ist beschädigt")
        return len(z.infolist())


def main():
    folder = Path(sys.argv[1])
    ok = True
    for file in sorted(folder.iterdir()):
        try:
            if file.suffix == ".exe":
                print(f"OK  {file.name}: {check_exe(file)} Teile entpackt")
            elif file.suffix in (".jar", ".zip"):
                print(f"OK  {file.name}: {check_zip(file)} Dateien geprüft")
        except (zlib.error, ValueError, zipfile.BadZipFile, OSError) as e:
            ok = False
            print(f"KAPUTT  {file.name}: {e}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
