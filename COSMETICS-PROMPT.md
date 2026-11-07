# Prompt für Claude Design: Emberleaf-Cosmetics

Kopiere den Block unten komplett in Claude Design. Ersetze nur die letzte Zeile („Baue: …“) durch das,
was du haben willst. Die fertige Datei (.bbmodel) lädst du im Emberleaf-Launcher unter
**Cosmetics → Blockbench-Modell laden** hoch.

---

```
Du baust ein Cosmetic für meinen eigenen Minecraft-Client „Emberleaf“. Liefere es als Blockbench-Datei
(.bbmodel, Modellformat „Generic Model“ / free) mit EINGEBETTETER PNG-Textur. Nur Würfel (cubes), keine
Mesh-Objekte. Die Datei muss sich in Blockbench öffnen lassen.

STIL
- Feiner HD-Stil: echte 3D-Form aus vielen kleinen Würfeln, Textur mit 2 bis 4 Texeln pro Modell-Pixel
  (Textur z. B. 128x128 oder 256x256, UV-Auflösung = Texturgröße).
- Saubere, liebevoll gemalte Textur: Farbverläufe, weiche Schattierung oben hell / unten dunkler,
  feine Details (Nähte, Adern, Fellstriche, Glanzkanten), keine einfarbigen Flächen.
- Passend zu Minecraft, aber hochwertiger als Vanilla. Keine Marken, Logos oder bekannten Figuren.
- Pro Würfel eigene UV-Flächen (kein Box-UV nötig), nichts darf sich auf der Textur überlappen.

KOORDINATEN (1 Einheit = 1 Minecraft-Pixel, 16 = 1 Block)
- Die Spielerfigur steht mit den Füßen auf y = 0 und schaut nach NORDEN (-Z).
- Beine: y 0–12. Körper: x -4..4, y 12–24, z -2..2. Kopf: x -4..4, y 24–32, z -4..4.
- Die Figur schaut nach Norden, deshalb ist ihre LINKE Seite -X und ihre RECHTE Seite +X:
  linker Arm x -8..-4, rechter Arm x 4..8.
- Der Rücken ist bei z = +2, die Brust bei z = -2. Das Gesicht ist die Nordseite des Kopfes (z = -4).
- Cosmetics sitzen 0,2 bis 0,6 Pixel über der Haut, damit nichts hineinschneidet.

KATEGORIEN (sag mir, welche es ist, und halte dich an die Regeln)
- KOPF (Hüte, Mützen, Kronen, Bandanas, Ohren, Masken): um/auf den Kopf (y 24–40). Alles in eine Gruppe
  mit Drehpunkt (0, 24, 0).
- FLÜGEL: zwei Gruppen „wing_left“ (linke Seite, -X) und „wing_right“ (rechte Seite, +X), Drehpunkt am oberen Rücken,
  etwa (-1.5, 22, 2.5) und (1.5, 22, 2.5). Die Flügel zeigen nach außen und leicht nach hinten.
  Spannweite pro Flügel 10–18 Pixel. Bitte beide Flügel spiegelgleich.
- CAPE / UMHANG: Gruppe „cape“, Drehpunkt oben am Rücken (0, 24, 2.2). Das Cape hängt nach unten
  hinter dem Rücken (etwa x -5..5, y 7..24, z 2.2..3.2). Leuchtende Stickereien in eine Untergruppe
  „cape_glow“.
- RÜCKEN (Rucksäcke, Schwerter, Köcher, Schals): Gruppe mit Drehpunkt (0, 18, 2), sitzt am Körper.
- PET (Begleiter, läuft neben mir): eigenständiges Tier, Füße auf y = 0 um den Ursprung herum,
  schaut nach NORDEN (-Z), Größe 6–16 Pixel lang. Gruppen: „body“, darin „head“, „tail“,
  „ear_left“, „ear_right“, „leg_front_left“, „leg_front_right“, „leg_back_left“, „leg_back_right“
  (Drehpunkt jedes Beins oben am Bein, Kopf am Hals, Schwanz an der Wurzel).

AUTOMATISCHE BEWEGUNG (Emberleaf bewegt Gruppen anhand ihres Namens, Drehpunkte bitte sinnvoll setzen)
- Name enthält „wing“ + „left“/„right“: Flügel schlagen (ruhig im Stehen, stark beim Springen/Fliegen).
- „cape“: weht beim Laufen nach hinten.
- „tail“: wedelt. „ear“: zuckt ab und zu. „leg“: läuft beim Gehen. „head“ (beim Pet): schaut sich um.
- Name enthält „glow“: leuchtet im Dunkeln (Augen, Kristalle, Glut, Runen).

EIGENE ANIMATIONEN (optional, als Blockbench-Animationen, Schleife)
- „idle“ (Stehen), „walk“ (Laufen), „fly“ (Fliegen/Springen), „sit“ (Pet sitzt).
- Nur Drehung, Position und Größe von Gruppen, keine Molang-Formeln, nur feste Zahlen.

GRENZEN
- Höchstens 1500 Würfel, Datei unter 8 MB, genau eine Textur, eingebettet.

Liefere: die .bbmodel-Datei und einen Satz, welche Kategorie es ist.

Baue: [HIER BESCHREIBEN, z. B. „Kristall-Feenflügel in Blau und Violett mit leuchtenden Adern“]
```

---

## Ideen zum Einsetzen
- „Kopf: Strickmütze in Herbstorange mit Zopfmuster und großem Bommel“
- „Kopf: goldene Krone mit roten Edelsteinen, die leuchten (glow)“
- „Flügel: Drachenflügel in Dunkelrot mit Knochenstreben und zerrissenem Rand“
- „Flügel: Schmetterlingsflügel (Monarch) mit fein gemaltem Muster“
- „Cape: Sternennacht-Umhang mit leuchtenden Sternbildern (cape_glow)“
- „Rücken: Wanderrucksack mit Laterne, die leuchtet“
- „Pet: kleiner roter Panda mit buschigem Schwanz“
- „Pet: Mini-Drache, der mit den Flügeln schlägt (wing_left/wing_right am Pet)“

## Tipp
Im Launcher unter **Cosmetics → Vorlagen** findest du die acht Modelle des Starter-Sets als .bbmodel.
Du kannst Claude Design eine davon als Beispiel mitgeben („mach es so aufgebaut wie diese Datei“).
