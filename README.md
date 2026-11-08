# 🍁 Emberleaf

Ein gemütlicher Minecraft-Launcher mit eigenem Client: 3D-Cosmetics, Freundesliste, Chat, Minimap,
eigener Shader und ein live gerendertes Hauptmenü. Für Minecraft Java **26.3** mit Fabric.

## ⬇️ Herunterladen

**[Zur Emberleaf-Webseite](https://lagerfeuer.emberleaf-server.workers.dev/#download)** – dort gibt es den Download
(Windows 10/11, 64 Bit).

1. Leg `Emberleaf.exe` in einen eigenen Ordner, zum Beispiel `Dokumente\Emberleaf`.
2. Doppelklick. Windows zeigt beim ersten Mal vielleicht „Der Computer wurde durch Windows geschützt“:
   auf **Weitere Informationen → Trotzdem ausführen** klicken. Emberleaf ist (noch) nicht signiert, deshalb fragt Windows.
3. Mit deinem Microsoft-Konto anmelden, Profil wählen, **Spielen**.

Updates kommen von selbst: Emberleaf lädt neue Versionen im Hintergrund und fragt dann
„Update ist da – Jetzt neu starten / Später“.

> Ist bei dir **Smart App Control** an (Windows-Sicherheit → App- und Browsersteuerung), blockiert Windows
> nicht signierte Programme komplett. Dann sag Bescheid.

## Was alles drin ist

- **Launcher** – Profile (Vanilla, Fabric, Forge, NeoForge), Mods von Modrinth mit einem Klick, Skins wechseln
- **Cosmetics** – Hüte, Flügel, Umhänge, Auren, Haustiere und Emotes in 3D, im Launcher und im Spiel
- **Freunde (Lagerfeuer)** – Freundesliste, Online-Status, Chat, Party, „Mitspielen“ auf dem Server deiner Freunde
- **Im Spiel** – eigenes Hauptmenü, Minimap, HUD, Zoom, Emberleaf-Menü mit der rechten Shift-Taste
- **Emberleaf Glut** – eigener Shader für Iris
- **Texturen** – das Emberleaf-Texturenpaket wird auf deinem PC aus den offiziellen Downloads der Pack-Autoren gebaut
  (nichts davon liegt in diesem Repo)

## Für Entwickler

| Ordner | Inhalt |
| --- | --- |
| `emberleaf/` | Launcher (Python, pywebview, minecraft-launcher-lib) |
| `emberleaf-mod/` | Client-Mod für Fabric 26.3 (Java 25, Loom) |
| `emberleaf-server/` | Webseite + Lagerfeuer (Freunde-Server): Cloudflare Worker mit D1 und KV |
| `emberleaf-shader/` | Emberleaf-Glut-Shader für Iris |

Neue Version veröffentlichen: erst bauen (Mod, Shader und exe landen in `emberleaf\dist\release-<Version>\`) …

```powershell
powershell -ExecutionPolicy Bypass -File emberleaf\tools\release.ps1 -Version 0.0.9
```

… dann die Dateien auf der Webseite unter **/upload** hochladen (nur der Besitzer). Alle Launcher holen sich das
Update innerhalb von 30 Minuten. Die Webseite selbst kommt mit `emberleaf-server\tools\deploy.ps1` zu Cloudflare.

Emberleaf ist ein Fanprojekt und gehört nicht zu Mojang oder Microsoft.
