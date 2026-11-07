# Lagerfeuer einrichten (Emberleaf-Freundeserver)

Das Lagerfeuer ist der kleine Server, über den Emberleaf-Spieler sich gegenseitig finden:
Freundesliste, Online-Status („spielt auf …“), Mitspielen und eure Cosmetics im Spiel.

Er läuft kostenlos bei **Cloudflare** (Workers + D1-Datenbank). Das kostenlose Kontingent
reicht für hunderte Spieler. Eine Kreditkarte braucht ihr nicht.

**Nur einer von euch** muss das einmal machen. Dauert etwa 5 Minuten.

---

## Was der Server speichert

- Minecraft-Name und UUID (öffentlich bei Mojang sowieso sichtbar)
- wer mit wem befreundet ist und offene Anfragen
- ob du online bist und auf welchem Server (Adresse)
- dein angelegtes Emberleaf-Outfit, damit andere es im Spiel sehen
- die Emberleaf-Updates, die der Besitzer verteilt (Client-Mod, Texturenpaket, Shader)

Passwörter oder Minecraft-Tokens bekommt der Server **nie**. Die Anmeldung läuft wie beim
Betreten eines echten Minecraft-Servers: Mojang bestätigt dem Lagerfeuer, dass du wirklich du bist.

---

## Weg A: im Browser (nichts installieren)

1. **Konto anlegen:** Geh auf <https://dash.cloudflare.com/sign-up> und leg ein kostenloses Konto an
   (E-Mail bestätigen).

2. **Datenbank anlegen:** Links **Storage & Databases → D1 SQL Database → Create**.
   Name: `lagerfeuer`, dann **Create**. Mehr musst du hier nicht tun, die Tabellen legt der
   Server beim ersten Start selbst an.

3. **Worker anlegen:** Links **Compute (Workers) → Workers & Pages → Create → Start with Hello World**.
   Name: `lagerfeuer`, dann **Deploy**.

4. **Code einfügen:** Beim neuen Worker auf **Edit code** klicken. Den ganzen Beispielcode löschen
   und stattdessen den Inhalt der Datei `worker.js` aus diesem Ordner einfügen. Oben rechts **Deploy**.

5. **Datenbank verbinden:** Beim Worker **Settings → Bindings → Add → D1 database**.
   - Variable name: `DB` (genau so, in Großbuchstaben)
   - D1 database: `lagerfeuer`

   Speichern bzw. **Deploy**.

   **Updates verteilen (nur der Besitzer):** Beim Worker **Settings → Variables and Secrets → Add**.
   - Type: `Text`, Variable name: `UPDATE_OWNER`, Value: dein Minecraft-Name (z.B. `SenLxca`)

   Dann erscheint bei dir in Emberleaf unter **Einstellungen → Emberleaf-Updates** der Knopf
   **An alle verteilen**. Die Launcher deiner Freunde holen neue Versionen von selbst (beim Start und
   alle 30 Minuten) und nutzen sie ab dem nächsten Spielstart.

6. **Testen:** Öffne `https://lagerfeuer.<dein-name>.workers.dev/v1/health` im Browser
   (die genaue Adresse steht beim Worker unter **Domains & Routes**).
   Da muss stehen: `{"ok":true,"name":"Emberleaf Lagerfeuer"}`

7. **In Emberleaf eintragen:** Emberleaf öffnen → **Freunde** → die Adresse
   `https://lagerfeuer.<dein-name>.workers.dev` eintragen → **Verbinden**.
   Schick die Adresse deinen Freunden, sie tragen genau dieselbe ein.

---

## Weg B: mit Wrangler (für Leute mit Node.js)

```bash
npx wrangler login
npx wrangler d1 create lagerfeuer
```

Die ausgegebene `database_id` in `wrangler.toml` eintragen (bei `UPDATE_OWNER` steht schon `SenLxca`), dann:

```bash
npx wrangler deploy
```

---

## Wenn etwas nicht klappt

| Meldung | Lösung |
| --- | --- |
| „Die Datenbank ist nicht verbunden (D1-Bindung „DB“ fehlt)“ | Schritt 5: Die Bindung muss genau `DB` heißen. |
| „Die Minecraft-Anmeldung konnte nicht bestätigt werden“ | Im Launcher ab- und wieder anmelden, dann neu versuchen. |
| „… nutzt Emberleaf noch nicht“ | Dein Freund muss Emberleaf einmal mit derselben Lagerfeuer-Adresse geöffnet und sich angemeldet haben. |
| „Updates verteilen ist aus“ | Schritt 5: Variable `UPDATE_OWNER` mit deinem Minecraft-Namen anlegen. |
| Freunde sehen deine Cosmetics nicht | Ihr müsst beide dieselbe Adresse eingetragen haben und Emberleaf im Spiel nutzen (Fabric-Profil 26.3). |
