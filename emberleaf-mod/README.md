# Emberleaf Client-Mod

Der Emberleaf-Client für Fabric 26.3: Cosmetics im Spiel, eigenes Hauptmenü, Minimap, HUD, Zoom, Emotes und Freunde.

Bauen (braucht Java 25, Pfad in `tools/jdk-path.txt` oder `JAVA_HOME`):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File emberleaf-mod\tools\gradle.ps1 build
```

Die fertige Datei liegt danach in `build/libs/emberleaf-client-<version>.jar`. Der Launcher legt sie selbst in jedes
Fabric-26.3-Profil. Veröffentlicht wird sie mit `emberleaf\tools\release.ps1`.
