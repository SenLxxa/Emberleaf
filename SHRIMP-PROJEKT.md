# Shrimp's Immersive Interface – Projekt-Steckbrief

Stand: 04.11.2026

## Basis
- Minecraft Java 26.3 (bisher über LabyMod 4 mit Fabric)
- PC: GTX 1070, i5-8600K, 32 GB RAM, 1440p
- Mods sind erlaubt
- Alles soll mit Freunden teilbar sein
- Der Screenshot war nur Inspiration

## Stand 05.11.2026 (Nachmittag): Emberleaf Alpha 0.0.2
- **Version:** Launcher und Client-Mod heißen jetzt **0.0.2 Alpha** (`emberleaf/backend/version.py`, `gradle.properties`).
- **Texturenpaket „Emberleaf Texturen“ 3.0** (ersetzt die selbstgemalten 2.0/64x-Pakete): wird auf jedem PC aus den
  Original-Downloads von CurseForge zusammengebaut (`emberleaf/backend/packassembler.py`), nie weitergegeben (CREDITS.txt).
  Basis Faithful 64x; dazu 3D-Gitter/Keimlinge (Prime), 3D Nature, Flowers 3D, Better Lanterns, 3D-Leitern, Torches
  Reimagined, Fancy Beds (+ eigene Symbole), Better Leaves (Büsche aus den 64x-Blättern), Clear 32x (noch klarer),
  leuchtende Erze (eigene 64x-Ränder), Enchantment Glows, Fantasy 3D Tools/Armors, ZeroVision-Bücher (wärmer),
  Cubic Sun & Moon, Tissou's Zombies, Boss Refreshed, Fresh Moves, Icons, Icon Fresh, Ambience Sounds, Elytras+.
  Für 26.3 angepasst: Overlays, alte Pfade (Flowers 3D), Tippfehler (Prime), fehlende Sounds entfernt – 0 Warnungen.
  Nicht nutzbar: Better End City (Datenpaket für eine Mod), Aquaculture (nur für die Mod), Glowing Trims (alte
  OptiFine-Technik, ersetzt durch eigene Funktion).
- **Benötigte Mods** installiert der Launcher selbst in Fabric-26.3-Profile: Iris (+Sodium), Entity Model Features,
  Entity Texture Features, Continuity.
- **Neu im Client:** Elytra per Namen (Elytras+-Regeln, z. B. „Citrus“), leuchtende Rüstungsbesätze (Modul; bei
  3D-Rüstung aus, weil die 3D-Teile die Besätze verdecken), Mob-Bilder auf der Minimap (Icon Fresh), Knöpfe ohne
  Symbol-Kästchen bei Icon-Paketen.
- **Hauptmenü im Spiel** (Höhle, Nether, Oberwelt, End, Insel) zeigt jetzt die echten Blöcke des Spiels: Modelle und
  Texturen aus dem Block-Atlas, also mit dem aktiven Texturenpaket (3D-Blumen, buschige Blätter …). Echter End-Kristall,
  echter schlafender Fuchs. Szenen: `emberleaf-mod/tools/make_live_scenes.py` (Blockzustände + gebackenes Licht).
- **Launcher:** Kamera nah an deiner Figur wie im Minecraft-Hauptmenü; die Insel nutzt die echten Texturen
  (`emberleaf/backend/islandtextures.py`, aus Texturenpaket bzw. Spiel-Jar).
- Als Nächstes: Shader (Wasserwellen, Spiegelungen).

## Stand 04.11.2026
- Der Launcher heißt jetzt **Emberleaf** (Glut + Herbstblatt) und ist fertig gebaut: Minecraft-Look, 3D-Insel-Startseite,
  Profile, Mods (Modrinth), Cosmetics-Shop mit 8 Kategorien + 3D-Editor, echter Spielstart (getestet bis ins Hauptmenü).
- Installiert unter `%USERPROFILE%\Emberleaf`, Verknüpfung auf dem Desktop.
- Client-Mod `emberleaf-mod/` fertig für Fabric 26.3: Hüte, Körper-Teile, Flügel, Pets, animierte Capes und Auren
  im Spiel, Zoom (Taste C + Mausrad). Im Spiel getestet (Screenshots von vorne und hinten).
- Freunde "Lagerfeuer": Server `emberleaf-server/` (Cloudflare, kostenlos, 28 Tests bestanden), Freunde-Seite im Launcher
  (Freunde hinzufügen, Anfragen, online/Server, Mitspielen), der Mod teilt Outfits und Online-Status.
- Eigenes Hauptmenü im Spiel (HD gemalter Herbstwald mit Lagerfeuer, Tag/Abend/Nacht, weiche Nunito-Schrift, runde Knöpfe,
  dein Skin in 3D, Lagerfeuer-Freundesliste mit Mitspielen). Bilder: `emberleaf-mod/tools/make_menu_art.py`.
- Skin-Totem: Totem als Mini-Ich mit dem Skin des Halters, beim Auslösen Blätter, Glut und Klang.
- Emotes im Spiel: Taste B spielt das im Launcher angelegte Emote, Kamera dreht nach vorne.
- Offen: Lagerfeuer bei Cloudflare einrichten (Anleitung `emberleaf-server/ANLEITUNG.md`), Mojang-Freigabe für den Login,
  CurseForge-Suche, Emotes für andere sichtbar machen, 3D-Inventar, Resourcepack, Shader.

## Stand 05.11.2026 (Vormittag): neues Texturenpaket & Live-Hauptmenü
- **Texturenpaket „Emberleaf Texturen“ 2.0** (16x Pixelkunst, ersetzt das weichgezeichnete 64x-Paket): Stil gemischt aus
  Stay True / Excalibur / Ashen (nichts kopiert, alles aus Code: `emberleaf/backend/pixelart.py`, `pixelblocks.py`):
  ~235 eigene Blöcke (Steine, Erde, 11 Holzarten, Erze, Wolle/Beton/Terrakotta in 16 Farben, Mineralblöcke …),
  buschige Blätter mit Außenschicht (wie Better Leaves), 3D-Laternen (wie Better Lanterns), würfelige Sonne und Mond mit
  8 Mondphasen (wie Cubic Sun & Moon), Zufallsvarianten gegen Wiederholung, 16x-Zauberbücher pro Verzauberung, dunkle
  Hotbar. Alles andere bekommt ruhige, warme Farben. Wird beim ersten Spielstart gebaut (~7 s, 3 MB).
- **Live-Hauptmenü** (wie Laby, eigene Szenen): Höhle mit Lavafluss, Fackeln, Amethyst, Tropfsteinen und Lichtstrahl;
  Nether mit Lavameer, Festungsbrücke, Karmesinwald und Glowstone; Oberwelt mit Fluss, Hütte, Lagerfeuer, Bergen
  (Tag/Abend/Nacht); End mit Obsidiansäulen, Kristallen und Chorus. Echte Perspektive, Kameraflug beim Öffnen,
  Mausneigung, Bloom, Partikel (Glut, Staub, Asche, Glühwürmchen, Rauch), Schnee im Winter, 15 FPS im Hintergrund.
  Einstellungen im Emberleaf-Menü: Szene, Qualität (Niedrig–Ultra), Kameraflug, Unschärfe. Generator:
  `emberleaf-mod/tools/make_live_scenes.py`. Die schwebende Insel gibt es weiter als Szene „Schwebende Insel“.
- Launcher: doppelte Knöpfe unten entfernt, „Neu“ ist jetzt der ✨-Knopf oben.
- Später: Shader (Wasserwellen, Spiegelungen) überarbeiten, dunkles Inventar-Design (Prominent UI).

## Stand 05.11.2026 (Nachtschicht)
- **Hauptmenü** (Launcher + Spiel): schwebende 3D-Insel in 4 Szenen (Herbstwald, Nether, End, Kristallhöhle), wählbar unter
  Einstellungen → Hauptmenü. Figur sinkt nicht mehr ein, online Freunde stehen mit Namensschild auf der Insel.
  Launcher-Startseite mit ganzer Insel und Schnellzugriff (Freunde online, Dein Look, Skins, Neu in Emberleaf).
- **Emberleaf-Menü im Spiel** (Rechte Shift-Taste): Kategorien Spiel, Kampf & PvP, Cosmetics, Grafik & Effekte, HUD-Widgets,
  Oberfläche. Module: Zoom, Emotes, Fullbright, Treffer-Partikel (1–10×), Immer Krit-Funken, Cosmetics, Pets, Auren,
  Elytra-Kondensstreifen, **Minimap** (Zoom 1–4), FPS, Koordinaten, Richtung, Biom, Uhrzeit, Ping, Tempo, Rüstung, Tastendrücke.
- **Skins**: Launcher-Seite „Skins“ (Sammlung, PNG hinzufügen, aktuellen speichern, mit einem Klick anziehen) und
  „Skins wechseln“ im Emberleaf-Menü im Spiel (lädt hoch, eigene Figur wechselt sofort).
- **Eigene Mods**: Mods-Seite → „+ Eigene Datei“ (.jar/.zip).
- **Cosmetics**: Blockbench-Import (.bbmodel), Starter-Set mit 8 HD-Modellen, Prompt für Claude Design in `COSMETICS-PROMPT.md`.
- **Auto-Updates über GitHub** (github.com/SenLxxa/Emberleaf): `emberleaf\tools\release.ps1 -Version x.y.z -Notes "…"`
  baut Mod, Shader und exe und legt ein GitHub-Release an (mit `emberleaf-release.json`: Dateiliste + SHA-256). Jeder Launcher
  schaut beim Start und alle 30 Minuten nach, lädt Mod/Shader und die neue `Emberleaf.exe` im Hintergrund und meldet
  „Update ist da – Jetzt neu starten / Später“ (Später = beim nächsten Start). Danach: „Emberleaf ist jetzt auf x.y.z“.
- **Texturenpaket „Emberleaf 64x“** (`emberleaf/backend/texturepack.py`, `cozyart.py`): wird beim ersten Spielstart auf dem PC
  aus dem eigenen Spiel gebaut (~20 s): 121 handgemalte kachelbare Blöcke (Steine, Hölzer, Rinde, Erze, Wasser, Lava, Blätter …),
  3200 weitere Texturen (Blöcke, Items, Mobs, Partikel) im Emberleaf-Stil in 64x, 128 Zauberbücher (eins pro Verzauberung + Stufe).
- **Shader „Emberleaf Glut“** (`emberleaf-shader/`): weiche Schatten, warmes Fackellicht, Wasser mit Spiegelung, Bloom,
  goldene Sonnenuntergänge, Milchstraße, wehende Pflanzen; Profile Schnell/Schön/Ultra. Unter Iris 1.11.7 getestet (~110 FPS
  im Test). Iris + Sodium installiert der Launcher automatisch in Fabric-26.3-Profile.
- Offen: Lagerfeuer bei Cloudflare einrichten (dein Schritt), Minimap-Wünsche/weitere Mods (du gibst noch Daten),
  eigene Cosmetics aus Claude Design importieren.

## Neuer Plan (05.11.2026): 3D-Insel & Cosmetics neu
**Hauptmenü-Hintergrund**
- Schwebende 3D-Insel im Sonnenuntergang, rechts im Bild (Knöpfe bleiben links)
- Eigene Cozy-Texturen (nicht Vanilla)
- Auf der Insel: deine Figur mit allen angelegten Cosmetics, Tiere (schlafender Fuchs, Vögel um die Insel)
- Wolkenmeer darunter, weitere kleine Inseln im Hintergrund
- Kamera fest, neigt sich leicht mit der Maus
- Dieselbe Insel im Launcher und im Spiel-Menü

**Cosmetics**
- Feiner HD-Stil (64x-Texturen, weichere Formen)
- Neue Cosmetics kommen per Blockbench-Import (.bbmodel) in den Launcher, der alte Klötzchen-Editor fliegt raus
- Starter-Set mit 6–8 fertigen HD-Beispielen (auch als Vorlage für Blockbench)
- Wichtig: Flügel, Hüte & Bandanas, Pets & Begleiter, Capes & Rücken
- Animationen automatisch nach Art + eigene Blockbench-Animationen, falls vorhanden:
  Flügel schlagen echt, Cape mit Physik, Pets laufen mit, Ohren/Schwänze wackeln, Teile leuchten

## 1. Emberleaf Launcher (komplett neu programmiert)
- Eigenes Cozy-Design, nichts kopiert
- Dein Skin wird in 3D im Launcher angezeigt
- Auswahl: Vanilla / Fabric / Forge / NeoForge
- Mod-Browser mit Modrinth + CurseForge, Mods direkt ins Profil laden
- Simple Voice Chat wird automatisch mitinstalliert

## 2. Shrimp Client (Mod im Spiel)
- **Skin-Totem:** 3D-Mini-Ich, zeigt automatisch den Skin von dem, der es hält
  - Beim Auslösen: Mini-Ich ploppt groß auf, eigene Partikel, eigener Sound
- **3D-Inventar:** kippt mit der Maus, Aufklapp-Animation, Hover-Effekte
- **Funktionen:** Zoom, Freelook, HUD-Infos (FPS, Koordinaten, Rüstung, Keystrokes)
- **Pets:** selbst designt im Editor
- **Cosmetics:** Capes, Bandanas & Hüte, Flügel, Schulter-Begleiter & Rucksäcke
  - Editor direkt im Client (Designs auch mit Claude Design)
  - PNG-Import nur für Capes, auch animiert
- **Mini-Server:** synchronisiert Cosmetics und Pets, damit Freunde mit Shrimp Client sie sehen

## 3. Resourcepack "Shrimp's Immersive Interface"
- 32x, warm & herbstlich, stilisiert-realistisch
- Alle Bereiche: Natur-Blöcke, Bau-Blöcke, Items & Werkzeuge, Erze & Nether
- **Jede UI passt zu ihrem Block:** Ofen sieht aus wie Ofen (Stein, Glut), Werkbank wie Holz-Werkbank, Kiste wie Kiste usw.
- Neu gestaltet: Inventar & alle Container, HUD, Menüs, Ladebildschirm & Titel mit Logo
- Extras: eigene Schrift, Cozy-UI-Sounds, schöne Tooltips, Shrimp-Easter-Eggs
- Spiegel-Maps (LabPBR), damit polierte Blöcke im Shader glänzen

## 4. Shrimp's Cozy Shader (eigener, für Iris)
- Weiche Schatten & goldenes Licht
- Schönes Wasser mit Spiegelungen und Wellen
- Wehende Pflanzen
- Nebel & Lichtstrahlen
- Echte 3D-Wolken
- Leuchtende Lampen (Bloom) und farbiges Licht in der Farbe der Lichtquelle
- Regen-Pfützen, in denen sich die Lichter spiegeln
- Polierte Blöcke spiegeln
- Nachts ein Galaxie-Himmel mit schönem Mond und Sternen
- Ziel: hübsch bei 60 FPS, mit Qualitätsstufen

## Reihenfolge
1. Client (zuerst, nach Vorlage-Bild des Users, aber komplett eigener Look)
2. Danach Resourcepack, Shader, Launcher (Reihenfolge noch offen)

## Wichtige Hinweise
- **Login im eigenen Launcher:** Mojang muss neue Launcher für den Microsoft-Login freischalten (kostenloser Antrag, dauert etwas). Bis dahin wird der Client in einer Entwickler-Umgebung getestet.
- **CurseForge** braucht einen kostenlosen API-Schlüssel. Modrinth geht sofort.
- **Vanilla** startet ohne Shrimp-Funktionen, weil die einen Mod-Loader brauchen. Der Shrimp Client kommt zuerst für Fabric, danach für NeoForge und Forge.
- **Shader-Leistung:** farbiges Licht und Spiegelungen sind die teuersten Effekte und bekommen eigene Regler, damit die GTX 1070 die 60 FPS schafft.
