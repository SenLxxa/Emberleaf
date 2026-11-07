"""Emberleaf Launcher: starts the local bridge and opens the launcher window."""
import argparse
import ctypes
import sys
import threading
from pathlib import Path

from backend.server import Bridge

ICON = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent)) / "emberleaf.ico"


def wait_for_exit(pid, timeout_ms=20000):
    """After a self-update: wait until the old Emberleaf has closed (it still holds the window's browser data)."""
    synchronize = 0x00100000
    kernel32 = ctypes.windll.kernel32
    handle = kernel32.OpenProcess(synchronize, False, pid)
    if handle:
        kernel32.WaitForSingleObject(handle, timeout_ms)
        kernel32.CloseHandle(handle)


def main():
    parser = argparse.ArgumentParser(description="Emberleaf Launcher")
    parser.add_argument("--server-only", action="store_true",
                        help="nur den lokalen Server starten, z.B. zum Testen im Browser")
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--play", action="store_true", help="startet gleich das zuletzt gewählte Profil")
    parser.add_argument("--after-update", type=int, default=0, help=argparse.SUPPRESS)
    args = parser.parse_args()

    if args.after_update:
        wait_for_exit(args.after_update)
    from backend import updates
    if updates.startup():
        return  # a downloaded update was installed and has started in our place

    bridge = Bridge(args.port).start()
    if args.server_only:
        print(bridge.url, flush=True)
        try:
            threading.Event().wait()
        except KeyboardInterrupt:
            pass
        return

    import webview

    window = webview.create_window(
        "Emberleaf",
        bridge.url,
        width=1380,
        height=840,
        min_size=(1120, 720),
        frameless=True,
        easy_drag=False,
        background_color="#17110d",
    )
    bridge.api.attach_window(window)
    if args.play:
        from backend import storage
        from backend.storage import UserError
        try:
            bridge.api.play(storage.get_settings()["last_profile"])
        except UserError:
            pass  # e.g. no profile yet: the launcher opens normally and the player presses "Spielen"
    # Eigene Taskleisten-Gruppe, sonst landet Emberleaf unter dem Python-Symbol
    ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("Emberleaf.Launcher")
    webview.start(icon=str(ICON) if ICON.is_file() else None)


if __name__ == "__main__":
    main()
