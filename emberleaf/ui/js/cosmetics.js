// Hängt Cosmetics in 3D an eine skinview3d-Figur: Voxel-Modelle, Capes, Auren, Pets, Mini-Ichs und Emotes.
import * as THREE from "../vendor/esm/three.js";
import {
  CrouchAnimation, FlyingAnimation, FunctionAnimation, IdleAnimation, PlayerObject, RunningAnimation,
  SkinViewer, SwimAnimation, WaveAnimation,
} from "../vendor/esm/skinview3d.js";
import { buildEModel, mountOnPart } from "./emodel.js";
import { sprite, toTexture } from "./textures.js";
import { api } from "./ui.js";

const modelCache = new Map();
export function loadModel(id) {
  if (!modelCache.has(id)) modelCache.set(id, api("model", { item_id: id }).catch((e) => { modelCache.delete(id); throw e; }));
  return modelCache.get(id);
}

// ---------- Voxel-Geometrie (verdeckte Flächen werden weggelassen) ----------

const DIRS = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];
const FACES = [
  [[1, 0, 0], [1, 1, 0], [1, 1, 1], [1, 0, 1]],
  [[0, 0, 1], [0, 1, 1], [0, 1, 0], [0, 0, 0]],
  [[0, 1, 1], [1, 1, 1], [1, 1, 0], [0, 1, 0]],
  [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]],
  [[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]],
  [[1, 0, 0], [0, 0, 0], [0, 1, 0], [1, 1, 0]],
];

function shade(x, y, z) {
  const n = Math.sin(x * 12.9898 + y * 78.233 + z * 37.719) * 43758.5453;
  return 0.92 + (n - Math.floor(n)) * 0.12;
}

export function voxelGeometry(voxels) {
  const filled = new Set(voxels.map(([x, y, z]) => `${x},${y},${z}`));
  const pos = [];
  const nor = [];
  const col = [];
  const idx = [];
  const c = new THREE.Color();
  for (const [x, y, z, hex] of voxels) {
    c.set(hex);
    const k = shade(x, y, z);
    for (let f = 0; f < 6; f++) {
      const [dx, dy, dz] = DIRS[f];
      if (filled.has(`${x + dx},${y + dy},${z + dz}`)) continue;
      const base = pos.length / 3;
      for (const [vx, vy, vz] of FACES[f]) {
        pos.push(x + vx, y + vy, z + vz);
        nor.push(dx, dy, dz);
        col.push(c.r * k, c.g * k, c.b * k);
      }
      idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
    }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute("normal", new THREE.Float32BufferAttribute(nor, 3));
  g.setAttribute("color", new THREE.Float32BufferAttribute(col, 3));
  g.setIndex(idx);
  g.computeBoundingSphere();
  return g;
}

export const VOXEL_MATERIAL = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.95, metalness: 0 });

export function voxelMesh(voxels) {
  return new THREE.Mesh(voxelGeometry(voxels), VOXEL_MATERIAL);
}

// ---------- Capes ----------

const CAPE_W = 64;
const CAPE_H = 32;

// Malt ein Cape-Bild (64x32). design(x, y, frame) liefert die Farbe für die sichtbare 10x16-Fläche.
export function paintCape(design, frame = 0) {
  const c = document.createElement("canvas");
  c.width = CAPE_W;
  c.height = CAPE_H;
  const ctx = c.getContext("2d");
  const darker = (hex) => {
    const col = new THREE.Color(hex).multiplyScalar(0.6);
    return `#${col.getHexString()}`;
  };
  ctx.fillStyle = darker(design(0, 0, frame));
  ctx.fillRect(0, 0, 23, 17);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 10; x++) {
    const color = design(x, y, frame);
    ctx.fillStyle = color;
    ctx.fillRect(1 + x, 1 + y, 1, 1); // Außenseite (sieht man von hinten)
    ctx.fillStyle = darker(color);
    ctx.fillRect(12 + (9 - x), 1 + y, 1, 1); // Innenseite
  }
  return c;
}

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}

// Liefert die Einzelbilder eines Capes: mitgelieferte (gemalt) oder eigene (PNG, Frames untereinander)
export async function capeFrames(item) {
  if (item.design) {
    const count = item.animated ? item.frames : 1;
    return Array.from({ length: count }, (_, f) => paintCape(item.design, f));
  }
  const img = await loadImage(item.image);
  const frames = item.frames || 1;
  const fh = Math.floor(img.height / frames);
  return Array.from({ length: frames }, (_, f) => {
    const c = document.createElement("canvas");
    c.width = img.width;
    c.height = fh;
    c.getContext("2d").drawImage(img, 0, f * fh, img.width, fh, 0, 0, img.width, fh);
    return c;
  });
}

// ---------- Auren ----------

const spriteCache = new Map();
function spriteTexture(name) {
  if (!spriteCache.has(name)) spriteCache.set(name, toTexture(sprite(name)));
  return spriteCache.get(name);
}

function ringTexture() {
  const c = document.createElement("canvas");
  c.width = 64;
  c.height = 64;
  const ctx = c.getContext("2d");
  const dot = (x, y, color) => { ctx.fillStyle = color; ctx.fillRect(Math.round(x), Math.round(y), 1, 1); };
  for (let a = 0; a < Math.PI * 2; a += 0.02) {
    dot(32 + Math.cos(a) * 29, 32 + Math.sin(a) * 29, "#ff9a3d");
    dot(32 + Math.cos(a) * 22, 32 + Math.sin(a) * 22, "#ffb347");
  }
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * Math.PI * 2;
    for (let r = 23; r < 29; r++) dot(32 + Math.cos(a) * r, 32 + Math.sin(a) * r, i % 2 ? "#ffd36b" : "#ff7a2a");
  }
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2;
    const b = ((i + 2) / 6) * Math.PI * 2;
    for (let t = 0; t <= 1; t += 0.02) {
      dot(32 + Math.cos(a) * 22 * (1 - t) + Math.cos(b) * 22 * t, 32 + Math.sin(a) * 22 * (1 - t) + Math.sin(b) * 22 * t, "#e8622a");
    }
  }
  return toTexture(c);
}

export class Aura {
  constructor(def, parent, { center = [0, 0, 0], floor = -16, top = 18 } = {}) {
    this.def = def;
    this.center = center;
    this.floor = floor;
    this.top = top;
    const n = def.count;
    this.positions = new Float32Array(n * 3);
    this.colors = new Float32Array(n * 3);
    this.geometry = new THREE.BufferGeometry();
    this.geometry.setAttribute("position", new THREE.BufferAttribute(this.positions, 3));
    this.geometry.setAttribute("color", new THREE.BufferAttribute(this.colors, 3));
    this.material = new THREE.PointsMaterial({
      size: def.size,
      map: spriteTexture(def.sprite),
      vertexColors: true,
      transparent: true,
      alphaTest: def.additive ? 0.01 : 0.5,
      depthWrite: !def.additive,
      blending: def.additive ? THREE.AdditiveBlending : THREE.NormalBlending,
    });
    this.points = new THREE.Points(this.geometry, this.material);
    this.points.frustumCulled = false;
    parent.add(this.points);
    this.palette = def.colors.map((hex) => new THREE.Color(hex));
    this.particles = Array.from({ length: n }, () => this.spawn(true));
    if (def.ring) {
      this.ring = new THREE.Mesh(
        new THREE.PlaneGeometry(def.radius * 2.4, def.radius * 2.4),
        new THREE.MeshBasicMaterial({ map: ringTexture(), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false }),
      );
      this.ring.rotation.x = -Math.PI / 2;
      this.ring.position.set(center[0], floor + 0.3, center[2]);
      parent.add(this.ring);
    }
  }

  spawn(initial) {
    const d = this.def;
    const a = Math.random() * Math.PI * 2;
    const r = d.ring ? d.radius * (0.85 + Math.random() * 0.2) : d.radius * (0.55 + Math.random() * 0.45);
    const span = this.top - this.floor;
    const p = {
      a, r,
      x: Math.cos(a) * r,
      z: Math.sin(a) * r,
      y: this.floor + Math.random() * span,
      phase: Math.random() * 10,
      speed: (d.speed || 1) * (0.7 + Math.random() * 0.6),
      color: this.palette[(Math.random() * this.palette.length) | 0],
      life: 0,
      max: 1.5 + Math.random() * 2,
    };
    if (!initial) {
      if (d.mode === "rise") p.y = this.floor + (d.ring ? 0 : Math.random() * 6);
      if (d.mode === "fall") p.y = this.top;
    }
    return p;
  }

  update(dt, t) {
    const d = this.def;
    const pos = this.positions;
    const col = this.colors;
    this.particles.forEach((p, i) => {
      let bright = 1;
      p.life += dt;
      switch (d.mode) {
        case "swirl":
          p.a += dt * p.speed * 0.9;
          p.y += dt * 3;
          p.x = Math.cos(p.a) * p.r;
          p.z = Math.sin(p.a) * p.r;
          if (p.y > this.top) Object.assign(p, this.spawn(false), { y: this.floor });
          break;
        case "rise":
          p.y += dt * p.speed;
          p.x += Math.sin(t * 2 + p.phase) * dt * 1.5;
          bright = 1 - (p.y - this.floor) / (this.top - this.floor);
          if (p.y > this.top) Object.assign(p, this.spawn(false));
          break;
        case "fall":
          p.y -= dt * p.speed;
          p.x += Math.sin(t + p.phase) * dt * 2;
          if (p.y < this.floor) Object.assign(p, this.spawn(false));
          break;
        case "twinkle":
          bright = (Math.sin(t * 3 + p.phase * 5) + 1) / 2;
          break;
        case "wander":
          p.x = Math.cos(p.a) * p.r + Math.sin(t * 0.7 + p.phase) * 5;
          p.z = Math.sin(p.a) * p.r + Math.cos(t * 0.5 + p.phase) * 5;
          p.y += Math.sin(t + p.phase) * dt * 2;
          bright = (Math.sin(t * 2.5 + p.phase * 3) + 1) / 2;
          break;
        default:
          break;
      }
      pos[i * 3] = this.center[0] + p.x;
      pos[i * 3 + 1] = p.y;
      pos[i * 3 + 2] = this.center[2] + p.z;
      const k = d.additive ? Math.max(0, bright) : 1;
      col[i * 3] = p.color.r * k;
      col[i * 3 + 1] = p.color.g * k;
      col[i * 3 + 2] = p.color.b * k;
    });
    this.geometry.attributes.position.needsUpdate = true;
    this.geometry.attributes.color.needsUpdate = true;
    if (this.ring) {
      this.ring.rotation.z = t * 0.4;
      this.ring.material.opacity = 0.75 + Math.sin(t * 3) * 0.2;
    }
  }

  dispose() {
    this.points.removeFromParent();
    this.geometry.dispose();
    this.material.dispose();
    if (this.ring) {
      this.ring.removeFromParent();
      this.ring.geometry.dispose();
    }
  }
}

// ---------- Emotes ----------

const EMOTE_FNS = {
  "emote-dance": (p, t) => {
    const s = t * Math.PI * 2 * 1.2;
    p.skin.position.y = 8 + Math.abs(Math.sin(s)) * 1.2;
    p.skin.body.rotation.y = Math.sin(s) * 0.25;
    p.skin.head.rotation.z = Math.sin(s) * 0.15;
    p.skin.leftArm.rotation.z = 0.6 + Math.sin(s) * 0.5;
    p.skin.rightArm.rotation.z = -0.6 + Math.sin(s) * 0.5;
    p.skin.leftArm.rotation.x = Math.cos(s) * 0.5;
    p.skin.rightArm.rotation.x = -Math.cos(s) * 0.5;
    p.skin.leftLeg.rotation.x = Math.sin(s) * 0.35;
    p.skin.rightLeg.rotation.x = -Math.sin(s) * 0.35;
  },
  "emote-cheer": (p, t) => {
    const s = t * Math.PI * 2 * 1.4;
    p.skin.position.y = 8 + Math.max(0, Math.sin(s)) * 2.5;
    p.skin.leftArm.rotation.x = Math.PI;
    p.skin.rightArm.rotation.x = Math.PI;
    p.skin.leftArm.rotation.z = -0.35 - Math.sin(s * 2) * 0.15;
    p.skin.rightArm.rotation.z = 0.35 + Math.sin(s * 2) * 0.15;
    p.skin.head.rotation.x = -0.2;
  },
  "emote-jump": (p, t) => {
    const h = Math.max(0, Math.sin(t * Math.PI * 2));
    p.skin.position.y = 8 + h * 4;
    p.skin.leftLeg.rotation.x = -h * 0.5;
    p.skin.rightLeg.rotation.x = h * 0.3;
    p.skin.leftArm.rotation.z = h * 0.9;
    p.skin.rightArm.rotation.z = -h * 0.9;
  },
  "emote-clap": (p, t) => {
    const a = (Math.sin(t * 12) + 1) / 2;
    p.skin.leftArm.rotation.x = -1.3;
    p.skin.rightArm.rotation.x = -1.3;
    p.skin.leftArm.rotation.z = -(0.15 + a * 0.35);
    p.skin.rightArm.rotation.z = 0.15 + a * 0.35;
    p.skin.head.rotation.x = Math.sin(t * 6) * 0.08;
  },
  "emote-facepalm": (p, t) => {
    p.skin.rightArm.rotation.x = -2.35;
    p.skin.rightArm.rotation.z = 0.45;
    p.skin.head.rotation.x = 0.35;
    p.skin.head.rotation.y = Math.sin(t * 7) * 0.12;
  },
  "emote-nod": (p, t) => { p.skin.head.rotation.x = Math.sin(t * 6) * 0.35; },
  "emote-spin": (p, t) => {
    p.rotation.y = t * 5;
    p.skin.leftArm.rotation.z = 1.2;
    p.skin.rightArm.rotation.z = -1.2;
  },
};

export function emoteAnimation(id) {
  switch (id) {
    case "emote-wave": return new WaveAnimation("right");
    case "emote-run": return new RunningAnimation();
    case "emote-fly": return new FlyingAnimation();
    case "emote-swim": return new SwimAnimation();
    case "emote-sneak": return new CrouchAnimation();
    default: return EMOTE_FNS[id] ? new FunctionAnimation(EMOTE_FNS[id]) : new IdleAnimation();
  }
}

export function resetPose(player) {
  player.resetJoints();
  player.position.set(0, 0, 0);
  player.rotation.set(0, 0, 0);
  player.skin.position.set(0, 8, 0);
}

// Mini-Ich-Posen (laufen jedes Bild auf der kleinen Figur)
const MINI_POSES = {
  idle: (m, t) => {
    m.skin.leftArm.rotation.z = 0.04 + Math.sin(t * 1.5) * 0.03;
    m.skin.rightArm.rotation.z = -0.04 - Math.sin(t * 1.5) * 0.03;
    m.skin.head.rotation.y = Math.sin(t * 0.6) * 0.3;
  },
  wave: (m, t) => {
    m.skin.rightArm.rotation.x = Math.PI;
    m.skin.rightArm.rotation.z = -0.3 + Math.sin(t * 6) * 0.4;
  },
  dance: (m, t) => EMOTE_FNS["emote-dance"](m, t),
  sit: (m, t) => {
    m.skin.leftLeg.rotation.x = -Math.PI / 2;
    m.skin.rightLeg.rotation.x = -Math.PI / 2;
    m.skin.leftArm.rotation.x = -0.3;
    m.skin.rightArm.rotation.x = -0.3 + Math.sin(t * 4) * 0.2;
    m.skin.head.rotation.y = Math.sin(t * 0.8) * 0.4;
  },
};

// ---------- Outfit: alle angelegten Cosmetics einer Figur ----------

export class Outfit {
  constructor(viewer) {
    this.viewer = viewer;
    this.parts = {};
    this.items = {};
    this.t = 0;
    this.version = 0;
  }

  get player() {
    return this.viewer.playerObject;
  }

  async equip(category, item) {
    this.clear(category);
    if (!item || category === "emote") {
      if (item) this.items.emote = item;
      return;
    }
    const version = ++this.version;
    this.items[category] = item;
    const part = await this.build(category, item);
    if (version !== this.version && this.items[category] !== item) {
      part?.dispose?.();
      return;
    }
    if (part) this.parts[category] = part;
  }

  async build(category, item) {
    const player = this.player;
    const anchor = this.viewer.playerWrapper;
    if (item.model) return this.buildModel(category, item, player, anchor);
    switch (category) {
      case "head":
      case "body": {
        const mesh = voxelMesh(item.voxels);
        (category === "head" ? player.skin.head : player.skin.body).add(mesh);
        return { dispose: () => { mesh.removeFromParent(); mesh.geometry.dispose(); } };
      }
      case "wings": {
        const geometry = voxelGeometry(item.voxels);
        const root = new THREE.Group();
        root.position.set(0, 3, -2.6);
        const left = new THREE.Group();
        const right = new THREE.Group();
        left.position.x = 1.2;
        right.position.x = -1.2;
        right.scale.x = -1;
        for (const side of [left, right]) {
          const mesh = new THREE.Mesh(geometry, VOXEL_MATERIAL);
          mesh.position.z = -0.5;
          side.add(mesh);
          root.add(side);
        }
        player.skin.body.add(root);
        return {
          update: (dt, t) => {
            const flap = 0.45 + Math.sin(t * 2.6) * 0.28;
            left.rotation.y = flap;
            right.rotation.y = -flap;
          },
          dispose: () => { root.removeFromParent(); geometry.dispose(); },
        };
      }
      case "cape": {
        const frames = await capeFrames(item);
        await this.viewer.loadCape(frames[0]);
        const fps = item.fps || 8;
        let index = 0;
        let acc = 0;
        return {
          update: (dt) => {
            if (frames.length < 2) return;
            acc += dt;
            if (acc < 1 / fps) return;
            acc = 0;
            index = (index + 1) % frames.length;
            this.viewer.loadCape(frames[index]);
          },
          dispose: () => this.viewer.resetCape(),
        };
      }
      case "aura": {
        const aura = new Aura(item.aura, anchor);
        return { aura, update: (dt, t) => aura.update(dt, t), dispose: () => aura.dispose() };
      }
      case "pet": {
        const mesh = voxelMesh(item.voxels);
        const group = new THREE.Group();
        group.add(mesh);
        group.scale.setScalar(0.75);
        const base = item.ground ? new THREE.Vector3(15, -16, 5) : new THREE.Vector3(15, 5, 3);
        group.position.copy(base);
        group.rotation.y = -0.5;
        anchor.add(group);
        return {
          group,
          update: (dt, t) => {
            group.position.y = base.y + (item.ground ? Math.max(0, Math.sin(t * 3)) * 1.2 : Math.sin(t * 2) * 1.6);
            group.rotation.y = -0.5 + Math.sin(t * 0.7) * 0.25;
          },
          dispose: () => { group.removeFromParent(); mesh.geometry.dispose(); },
        };
      }
      case "minime": {
        const mini = new PlayerObject();
        mini.skin.map = player.skin.map;
        mini.skin.modelType = player.skin.modelType;
        mini.cape.visible = false;
        mini.elytra.visible = false;
        mini.ears.visible = false;
        mini.scale.setScalar(0.4);
        const shoulder = item.minime.place === "shoulder";
        if (shoulder) {
          mini.position.set(-6, 10, 0);
          player.add(mini);
        } else {
          mini.position.set(-15, -16 + 6.4, 6);
          mini.rotation.y = 0.4;
          anchor.add(mini);
        }
        const pose = MINI_POSES[item.minime.pose];
        return {
          update: (dt, t) => { mini.resetJoints(); mini.skin.position.set(0, 8, 0); pose(mini, t); },
          dispose: () => mini.removeFromParent(),
        };
      }
      default:
        return null;
    }
  }

  // Blockbench-Modelle: Kopf-Sachen am Kopf, Rücken/Flügel/Cape am Körper, Pets neben der Figur am Boden
  async buildModel(category, item, player, anchor) {
    const em = buildEModel(await loadModel(item.id));
    await em.ready;
    if (category === "pet") {
      const holder = mountOnPart(em.object, 16);
      holder.position.set(17, 0, 7);
      holder.rotation.y = Math.PI - 0.55;
      anchor.add(holder);
      return {
        group: holder,
        update: (dt, t) => em.update(t, { walk: 0 }),
        dispose: () => { holder.removeFromParent(); em.dispose(); },
      };
    }
    const mount = mountOnPart(em.object, category === "head" ? 24 : 18);
    (category === "head" ? player.skin.head : player.skin.body).add(mount);
    if (category === "cape") this.viewer.resetCape();
    return {
      update: (dt, t) => em.update(t, { capeLean: 4 }),
      dispose: () => { mount.removeFromParent(); em.dispose(); },
    };
  }

  clear(category) {
    const part = this.parts[category];
    if (part) part.dispose?.();
    delete this.parts[category];
    delete this.items[category];
  }

  clearAll() {
    Object.keys({ ...this.parts, ...this.items }).forEach((c) => this.clear(c));
  }

  // Skin geändert: Mini-Ich bekommt die neue Textur
  async refreshSkin() {
    const mini = this.items.minime;
    if (mini) await this.equip("minime", mini);
  }

  update(dt) {
    this.t += dt;
    for (const part of Object.values(this.parts)) part.update?.(dt, this.t);
  }
}

// ---------- Vorschaubilder für die Karten ----------

const FRAMING = {
  head: { pos: [22, 20, 34], target: [0, 11, 0] },
  body: { pos: [26, 10, 46], target: [0, 2, 0] },
  wings: { pos: [-34, 14, -54], target: [0, 4, 0] },
  cape: { pos: [-28, 10, -50], target: [0, 1, 0] },
  aura: { pos: [32, 14, 58], target: [0, 1, 0] },
  pet: { pos: [40, 8, 50], target: [8, -2, 0] },
  minime: { pos: [10, 6, 58], target: [-5, -2, 0] },
  emote: { pos: [30, 10, 52], target: [0, 1, 0] },
};
const EMOTE_POSE_TIME = { "emote-wave": 0.6, "emote-jump": 0.25, "emote-cheer": 0.2, "emote-spin": 0.25 };

export class ThumbnailRenderer {
  constructor() {
    this.canvas = document.createElement("canvas");
    this.viewer = new SkinViewer({
      canvas: this.canvas,
      width: 300,
      height: 360,
      preserveDrawingBuffer: true,
      renderPaused: true,
      fov: 35,
    });
    this.viewer.cameraLight.intensity = 0.8;
    this.outfit = new Outfit(this.viewer);
    this.cache = new Map();
    this.queue = Promise.resolve();
    this.skinKey = "";
  }

  async setSkin(skin) {
    this.skinKey = skin?.skin ? skin.skin.slice(-40) : "";
    this.cache.clear();
    if (skin?.skin) await this.viewer.loadSkin(skin.skin, { model: skin.model });
  }

  render(item) {
    const key = `${this.skinKey}|${item.id}|${item.updated || 0}`;
    if (this.cache.has(key)) return Promise.resolve(this.cache.get(key));
    this.queue = this.queue.then(async () => {
      if (this.cache.has(key)) return this.cache.get(key);
      const url = await this.draw(item);
      this.cache.set(key, url);
      return url;
    });
    return this.queue;
  }

  async draw(item) {
    const { viewer, outfit } = this;
    outfit.clearAll();
    viewer.resetCape();
    resetPose(viewer.playerObject);
    await outfit.equip(item.category, item);
    if (item.category === "emote") {
      const anim = emoteAnimation(item.id);
      anim.progress = EMOTE_POSE_TIME[item.id] ?? 0.35; // eine typische Pose der Bewegung zeigen
      anim.update(viewer.playerObject, 0);
    }
    for (let i = 0; i < 40; i++) outfit.update(0.05); // Partikel & Posen einschwingen lassen
    const frame = FRAMING[item.category];
    const pet = outfit.parts.pet?.group;
    if (pet) {
      // Kamera auf das Pet richten, die Figur steht angeschnitten daneben
      const target = pet.position.clone().add({ x: 0, y: 4, z: 0 });
      viewer.camera.position.set(target.x + 20, target.y + 9, target.z + 30);
      viewer.camera.lookAt(target.x - 3, target.y, target.z);
    } else {
      viewer.camera.position.set(...frame.pos);
      viewer.camera.lookAt(...frame.target);
    }
    const aura = outfit.parts.aura?.aura;
    if (aura) aura.material.size = item.aura.size * 1.7; // auf kleinen Karten sonst kaum zu sehen
    viewer.render();
    return this.canvas.toDataURL("image/png");
  }
}
