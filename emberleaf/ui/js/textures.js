// Eigene Pixel-Texturen im Minecraft-Stil, zur Laufzeit auf Canvas gemalt (keine Mojang-Dateien).
import * as THREE from "../vendor/esm/three.js";

function rng(seed) {
  return () => {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function canvas(w, h, paint) {
  const c = document.createElement("canvas");
  c.width = w;
  c.height = h;
  const ctx = c.getContext("2d");
  paint(ctx, w, h);
  return c;
}

const px = (ctx, x, y, color) => { ctx.fillStyle = color; ctx.fillRect(x, y, 1, 1); };
const pick = (r, list) => list[(r() * list.length) | 0];

function noise(seed, colors, size = 16) {
  const r = rng(seed);
  return canvas(size, size, (ctx) => {
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) px(ctx, x, y, pick(r, colors));
  });
}

const GRASS = ["#7f8e3d", "#8b9b45", "#73823a", "#97a54e", "#869441"];
const DIRT = ["#7a5233", "#6b4629", "#86603d", "#5f3e25", "#73502f"];
const STONE = ["#7c7a77", "#6e6c69", "#888684", "#62605e"];
const BARK = ["#5b3e26", "#6e4b2e", "#4c3420", "#634429"];

export const paint = {
  grassTop: () => {
    const r = rng(11);
    return canvas(16, 16, (ctx) => {
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
        px(ctx, x, y, r() < 0.07 ? pick(r, ["#d27a2e", "#c4572c", "#e3a33f"]) : pick(r, GRASS));
      }
    });
  },
  grassSide: () => {
    const r = rng(12);
    return canvas(16, 16, (ctx) => {
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) px(ctx, x, y, pick(r, DIRT));
      for (let x = 0; x < 16; x++) {
        const depth = 3 + ((r() * 2.4) | 0);
        for (let y = 0; y < depth; y++) px(ctx, x, y, pick(r, GRASS));
      }
    });
  },
  dirt: () => noise(13, DIRT),
  stone: () => noise(14, STONE),
  logSide: () => {
    const r = rng(15);
    return canvas(16, 16, (ctx) => {
      for (let x = 0; x < 16; x++) {
        const base = pick(r, BARK);
        for (let y = 0; y < 16; y++) px(ctx, x, y, r() < 0.25 ? pick(r, BARK) : base);
      }
    });
  },
  logTop: () => canvas(16, 16, (ctx) => {
    const r = rng(16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
      const ring = d > 6.5 ? pick(r, BARK) : (Math.round(d) % 2 ? "#b48a5a" : "#9c744a");
      px(ctx, x, y, ring);
    }
  }),
  leaves: (seed, colors) => {
    const r = rng(seed);
    return canvas(16, 16, (ctx) => {
      const shadow = colors[colors.length - 1];
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
        px(ctx, x, y, r() < 0.12 ? shadow : pick(r, colors)); // dunkle Flecken statt Löcher
      }
    });
  },
  planks: (seed = 17, colors = ["#9c6b3e", "#8f6136", "#a87645", "#875a32"]) => {
    const r = rng(seed);
    return canvas(16, 16, (ctx) => {
      for (let row = 0; row < 4; row++) {
        const offset = row % 2 ? 8 : 0;
        for (let y = row * 4; y < row * 4 + 4; y++) for (let x = 0; x < 16; x++) {
          let c = pick(r, colors);
          if (y === row * 4 + 3) c = "#5e3d22";
          if ((x + offset) % 16 === 0) c = "#6a4527";
          px(ctx, x, y, c);
        }
      }
    });
  },
  darkTile: () => noise(18, ["#221a15", "#251c17", "#1f1712", "#281e18", "#231a14"]),
  pumpkinSide: () => canvas(16, 16, (ctx) => {
    const r = rng(19);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      px(ctx, x, y, x % 4 === 0 ? "#b5561b" : pick(r, ["#e07a26", "#d86f20", "#e98a33"]));
    }
  }),
  pumpkinTop: () => canvas(16, 16, (ctx) => {
    const r = rng(20);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) px(ctx, x, y, pick(r, ["#d86f20", "#c9641c", "#e07a26"]));
    ctx.fillStyle = "#5b7a2a";
    ctx.fillRect(7, 6, 3, 3);
  }),
};

// Partikel-Sprites (8x8, weiß/hell gemalt, damit man sie einfärben kann)
const SPRITES = {
  leaf: ["...##...", "..####..", ".######.", "########", ".######.", "..####..", "...##...", "...#...."],
  heart: ["........", ".##.##..", "#######.", "#######.", ".#####..", "..###...", "...#....", "........"],
  spark: ["...#....", "...#....", "..###...", "#######.", "..###...", "...#....", "...#....", "........"],
  ember: ["........", "........", "...##...", "..####..", "..####..", "...##...", "........", "........"],
  snow: ["#..#..#.", ".#.#.#..", "..###...", "#######.", "..###...", ".#.#.#..", "#..#..#.", "........"],
  note: ["....##..", "....#.#.", "....#...", "....#...", ".###....", "####....", ".##.....", "........"],
  dot: ["........", "........", "...##...", "..####..", "..####..", "...##...", "........", "........"],
};

export function sprite(name) {
  const rows = SPRITES[name];
  return canvas(8, 8, (ctx) => {
    rows.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === "#") px(ctx, x, y, "#ffffff"); }));
  });
}

export function toTexture(c) {
  const tex = new THREE.CanvasTexture(c);
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.generateMipmaps = false;
  return tex;
}

const cache = new Map();
export function texture(key, make) {
  if (!cache.has(key)) cache.set(key, toTexture(make()));
  return cache.get(key);
}

// Texturen als CSS-Variablen, damit auch Buttons und Panels nach Minecraft aussehen
export function installCssTextures() {
  const root = document.documentElement.style;
  root.setProperty("--tex-planks", `url(${paint.planks().toDataURL()})`);
  root.setProperty("--tex-dark-planks", `url(${paint.planks(21, ["#4a3220", "#432d1c", "#523824", "#3d2919"]).toDataURL()})`);
  root.setProperty("--tex-dark", `url(${paint.darkTile().toDataURL()})`);
  root.setProperty("--tex-grass", `url(${paint.grassSide().toDataURL()})`);
  root.setProperty("--tex-dirt", `url(${paint.dirt().toDataURL()})`);
}
