// Alle mitgelieferten Cosmetics ohne 3D-Modell: Capes (Bild), Auren, Mini-Ichs und Emotes.
// 3D-Cosmetics kommen als Blockbench-Modelle: das Starter-Set aus assets/models und eigene Importe.

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

const hash = (x, y, z) => {
  const n = Math.sin(x * 12.9898 + y * 78.233 + z * 37.719) * 43758.5453;
  return n - Math.floor(n);
};
const mix = (...colors) => (x, y, z) => colors[Math.floor(hash(x, y, z) * colors.length)];
// ---------- Capes (Malfunktionen für die sichtbare 10x16-Fläche) ----------

const CAPES = [
  { id: "cape-autumn", name: "Herbstlaub", isNew: true, design: (x, y) => (hash(x, y, 1) > 0.78 ? mix("#e8743b", "#f2a541", "#d9462b")(x, y, 2) : (y < 2 ? "#4a2f1c" : "#5b3a22")) },
  { id: "cape-fire", name: "Lagerfeuer", design: (x, y) => {
    const flame = 16 - y - (Math.sin(x * 1.7) * 2 + 4);
    if (flame > 5) return "#ffd36b";
    if (flame > 2.5) return "#ffb347";
    if (flame > 0) return "#e8622a";
    return y < 4 ? "#2a1a1f" : "#3a2226";
  } },
  { id: "cape-stars", name: "Sternennacht", design: (x, y) => {
    if ((x - 6) ** 2 + (y - 4) ** 2 < 4.5) return (x - 7) ** 2 + (y - 3.5) ** 2 < 3 ? "#1b1f3a" : "#f4f1ea";
    if (hash(x, y, 3) > 0.9) return "#ffe08a";
    return y > 11 ? "#2b2550" : "#1b1f3a";
  } },
  { id: "cape-maple", name: "Ahorn-Wappen", design: (x, y) => {
    const leaf = ["....#....", "..#.#.#..", "#.#####.#", "#########", ".#######.", "..#####..", "....#....", "....#...."];
    const row = leaf[y - 4];
    if (row && x >= 0 && x < 9 && row[x] === "#") return y < 7 ? "#e8743b" : "#d9462b";
    return y === 0 || y === 15 || x === 0 || x === 9 ? "#c9962e" : "#f4e3c3";
  } },
  { id: "cape-plaid", name: "Holzfäller-Karo", design: (x, y) => {
    const a = x % 4 < 2;
    const b = y % 4 < 2;
    if (a && b) return "#1f1612";
    if (a || b) return "#7a1f18";
    return "#b8322a";
  } },
  { id: "cape-ember", name: "Glut (animiert)", animated: true, frames: 6, design: (x, y, f) => {
    const glow = Math.sin((y + f * 1.4) * 0.9 + x * 0.6) + Math.sin(x * 1.3 - f);
    if (glow > 1.2) return "#ffd36b";
    if (glow > 0.4) return "#ff8a3d";
    if (glow > -0.4) return "#b8401f";
    return "#3a1a14";
  } },
  { id: "cape-aurora", name: "Polarlicht (animiert)", animated: true, frames: 8, design: (x, y, f) => {
    const wave = Math.sin(x * 0.7 + f * 0.8) * 2 + 6;
    const d = Math.abs(y - wave);
    if (d < 1) return "#9fe3a0";
    if (d < 2.2) return "#6fb7d8";
    if (d < 3.4) return "#7d5ba6";
    return hash(x, y, 7) > 0.93 ? "#ffffff" : "#141a33";
  } },
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
];

const withCategory = (category, items) => items.map((item) => ({ ...item, category }));

export const PRESETS = [
  ...withCategory("cape", CAPES),
  ...withCategory("aura", AURAS),
  ...withCategory("minime", MINIMES),
  ...withCategory("emote", EMOTES),
];
