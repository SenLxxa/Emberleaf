// Alle mitgelieferten Cosmetics ohne 3D-Modell: Auren, Mini-Ichs und Emotes.
// 3D-Cosmetics (auch alle Capes) kommen als Blockbench-Modelle: das Starter-Set aus assets/models und eigene Importe.
// Eigene Bild-Capes malen die Spieler weiter selbst im Cape-Editor.

export const CATEGORIES = [
  { id: "head", label: "Kopf", icon: "👑", tag: "KOPF", importable: true, hint: "Mützen, Kronen, Bandanas und Ohren" },
  { id: "wings", label: "Flügel", icon: "🪽", tag: "FLÜGEL", importable: true, hint: "Flügel, die beim Laufen und Springen schlagen" },
  { id: "cape", label: "Capes", icon: "🧣", tag: "CAPE", importable: true, paintable: true, hint: "Umhänge, die beim Laufen wehen, auch als animiertes Bild" },
  { id: "body", label: "Rücken", icon: "🎒", tag: "RÜCKEN", importable: true, hint: "Rucksäcke, Schwerter, Köcher und Schals" },
  { id: "pet", label: "Pets", icon: "🐾", tag: "PET", importable: true, hint: "Begleiter, die neben dir herlaufen" },
  { id: "aura", label: "Auren", icon: "✨", tag: "AURA", hint: "Partikel, die um dich herum schweben" },
  { id: "minime", label: "Mini-Ichs", icon: "🧍", tag: "MINI-ICH", hint: "Eine Mini-Version von dir" },
  { id: "emote", label: "Emotes", icon: "💃", tag: "EMOTE", hint: "Bewegungen und Tänze, im Spiel mit Taste B" },
];

// ---------- Auren ----------

const AURAS = [
  { id: "aura-leaves", name: "Blätterwirbel", isNew: true, aura: { mode: "swirl", sprite: "leaf", count: 40, size: 2.6, colors: ["#e8743b", "#d9532f", "#f2a541", "#c96a2b"], radius: 13, speed: 1.1 } },
  { id: "aura-embers", name: "Glutfunken", aura: { mode: "rise", sprite: "ember", count: 60, size: 1.8, additive: true, colors: ["#ffb347", "#ff7a2a", "#ffd36b"], radius: 11, speed: 9 } },
  { id: "aura-hearts", name: "Herzen", aura: { mode: "rise", sprite: "heart", count: 16, size: 2.8, colors: ["#ff6b8a", "#ff9eb3", "#ff4f73"], radius: 12, speed: 5 } },
  { id: "aura-stars", name: "Sternenstaub", aura: { mode: "twinkle", sprite: "spark", count: 32, size: 2.4, additive: true, colors: ["#fff1b8", "#ffe08a", "#bfe8ff"], radius: 15 } },
  { id: "aura-snow", name: "Schneeflocken", aura: { mode: "fall", sprite: "snow", count: 60, size: 2.0, colors: ["#ffffff", "#dff3ff"], radius: 14, speed: 6 } },
  { id: "aura-fireflies", name: "Glühwürmchen", aura: { mode: "wander", sprite: "dot", count: 22, size: 2.2, additive: true, colors: ["#fff3a0", "#d8ff8a"], radius: 14 } },
  { id: "aura-notes", name: "Musiknoten", aura: { mode: "swirl", sprite: "note", count: 14, size: 3.0, colors: ["#9be7ff", "#ffd36b", "#ff9eb3"], radius: 12, speed: 0.7 } },
  { id: "aura-ring", name: "Glutkreis", isNew: true, aura: { mode: "rise", sprite: "ember", count: 40, size: 1.6, additive: true, colors: ["#ffb347", "#ff7a2a"], radius: 17, ring: true, speed: 7 } },
  { id: "aura-holy", name: "Himmelsglanz", isNew: true, aura: { mode: "rise", sprite: "spark", count: 44, size: 2.2, additive: true, colors: ["#fff6d8", "#ffe08a", "#ffffff"], radius: 16, ring: true, speed: 4 } },
];

// ---------- Mini-Ichs & Emotes ----------

const MINIMES = [
  { id: "mini-idle", name: "Mini-Ich", minime: { place: "ground", pose: "idle" } },
  { id: "mini-wave", name: "Winkendes Mini-Ich", isNew: true, minime: { place: "ground", pose: "wave" } },
  { id: "mini-dance", name: "Tanzendes Mini-Ich", minime: { place: "ground", pose: "dance" } },
  { id: "mini-shoulder", name: "Schulter-Mini-Ich", isNew: true, minime: { place: "shoulder", pose: "sit" } },
];

const EMOTES = [
  { id: "emote-wave", name: "Winken" },
  { id: "emote-dance", name: "Tanzen", isNew: true },
  { id: "emote-cheer", name: "Jubeln" },
  { id: "emote-jump", name: "Hüpfen" },
  { id: "emote-clap", name: "Klatschen", isNew: true },
  { id: "emote-facepalm", name: "Facepalm" },
  { id: "emote-nod", name: "Nicken" },
  { id: "emote-spin", name: "Drehen" },
  { id: "emote-run", name: "Rennen" },
  { id: "emote-fly", name: "Fliegen" },
  { id: "emote-swim", name: "Schwimmen" },
  { id: "emote-sneak", name: "Schleichen" },
  { id: "emote-float", name: "Schweben", isNew: true },
];

const withCategory = (category, items) => items.map((item) => ({ ...item, category }));

export const PRESETS = [
  ...withCategory("aura", AURAS),
  ...withCategory("minime", MINIMES),
  ...withCategory("emote", EMOTES),
];
