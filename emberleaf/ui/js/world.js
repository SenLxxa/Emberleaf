// Die Startseite: eine schwebende Insel über einem Wolkenmeer, in vier Szenen (Herbst, Nether, End, Höhle).
// Dieselben Inseln (assets/island/<szene>.json + atlas.png) zeigt auch das Hauptmenü im Spiel.
import * as THREE from "../vendor/esm/three.js";
import { PlayerObject } from "../vendor/esm/skinview3d.js";
import { api } from "./ui.js";

const B = 16; // ein Block = 16 Skin-Pixel, die Figur ist 2 Blöcke groß
const GROUND = -16; // Füße der Figur
export const SCENES = ["autumn", "nether", "end", "cave"];

const FACES = [
  { dir: [1, 0, 0], side: 1, corners: [[1, 0, 1], [1, 0, 0], [1, 1, 0], [1, 1, 1]] },
  { dir: [-1, 0, 0], side: 1, corners: [[0, 0, 0], [0, 0, 1], [0, 1, 1], [0, 1, 0]] },
  { dir: [0, 1, 0], side: 0, corners: [[0, 1, 1], [1, 1, 1], [1, 1, 0], [0, 1, 0]] },
  { dir: [0, -1, 0], side: 2, corners: [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]] },
  { dir: [0, 0, 1], side: 1, corners: [[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]] },
  { dir: [0, 0, -1], side: 1, corners: [[1, 0, 0], [0, 0, 0], [0, 1, 0], [1, 1, 0]] },
];
const SHADE = [1.0, 0.82, 0.6];

// Licht und Himmel je Szene (Herbst richtet sich nach der Tageszeit)
const LOOKS = {
  day: { sun: 1.8, sunColor: 0xfff0d8, sky: 0xd8e8ff, ground: 0x6a5a50, fog: 0xbcd6f0, cloud: 0xffffff },
  sunset: { sun: 1.9, sunColor: 0xffb070, sky: 0xffd2b0, ground: 0x5a3a60, fog: 0xf0a070, cloud: 0xffc8b0 },
  night: { sun: 0.5, sunColor: 0x8fa6ff, sky: 0x6070b0, ground: 0x201830, fog: 0x2a2850, cloud: 0x6a70a8 },
  nether: { sun: 1.2, sunColor: 0xff7a40, sky: 0xff9a70, ground: 0x401010, fog: 0x7a1e14, cloud: 0xc8442a },
  end: { sun: 0.9, sunColor: 0xd8b8ff, sky: 0xb898e8, ground: 0x201030, fog: 0x1e1030, cloud: 0x6a4a9a },
  cave: { sun: 0.6, sunColor: 0x9ad8ff, sky: 0x6a90c0, ground: 0x10161e, fog: 0x0e1622, cloud: 0x34506a },
};

function tileUV(data, name, size) {
  const i = data.atlas.tiles.indexOf(name);
  const cols = data.atlas.columns;
  const t = size.w / cols; // the real-texture atlas has bigger tiles than the bundled drawing
  const inset = 0.5;
  const u0 = ((i % cols) * t + inset) / size.w;
  const u1 = ((i % cols) * t + t - inset) / size.w;
  const v1 = 1 - (Math.floor(i / cols) * t + inset) / size.h;
  const v0 = 1 - (Math.floor(i / cols) * t + t - inset) / size.h;
  return [u0, v0, u1, v1];
}

function geometry(pos, nor, uv, col, idx) {
  const geo = new THREE.BufferGeometry();
  geo.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
  geo.setAttribute("normal", new THREE.Float32BufferAttribute(nor, 3));
  geo.setAttribute("uv", new THREE.Float32BufferAttribute(uv, 2));
  if (col) geo.setAttribute("color", new THREE.Float32BufferAttribute(col, 3));
  geo.setIndex(idx);
  return geo;
}

/** One mesh for lit blocks (hidden faces removed, soft corner shadows) and one for glowing blocks. */
function buildMeshes(data, blocks, size, mats) {
  const solid = new Set(blocks.map(([x, y, z]) => `${x},${y},${z}`));
  const has = (x, y, z) => solid.has(`${x},${y},${z}`);
  const glowTiles = new Set(data.atlas.glow || []);
  const out = { lit: { pos: [], nor: [], uv: [], col: [], idx: [] }, glow: { pos: [], nor: [], uv: [], col: [], idx: [] } };
  for (const [x, y, z, type] of blocks) {
    const tiles = data.blocks_types[type];
    if (!tiles) continue;
    for (const f of FACES) {
      const [dx, dy, dz] = f.dir;
      if (has(x + dx, y + dy, z + dz)) continue;
      const tileName = tiles[f.side];
      const g = glowTiles.has(tileName) ? out.glow : out.lit;
      const [u0, v0, u1, v1] = tileUV(data, tileName, size);
      const uvs = [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
      const base = g.pos.length / 3;
      f.corners.forEach((c, k) => {
        g.pos.push((x + c[0]) * B, (y + c[1]) * B, (z + c[2]) * B);
        g.nor.push(dx, dy, dz);
        g.uv.push(...uvs[k]);
        const ox = c[0] ? 1 : -1;
        const oy = c[1] ? 1 : -1;
        const oz = c[2] ? 1 : -1;
        const n = [];
        if (dx) n.push([x + dx, y + oy, z], [x + dx, y, z + oz], [x + dx, y + oy, z + oz]);
        if (dy) n.push([x + ox, y + dy, z], [x, y + dy, z + oz], [x + ox, y + dy, z + oz]);
        if (dz) n.push([x + ox, y, z + dz], [x, y + oy, z + dz], [x + ox, y + oy, z + dz]);
        const occ = n.filter(([a, b2, c2]) => has(a, b2, c2)).length;
        const ao = g === out.glow ? 1 : [1, 0.82, 0.68, 0.56][occ] * SHADE[f.side];
        g.col.push(ao, ao, ao);
      });
      g.idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
    }
  }
  const group = new THREE.Group();
  for (const [key, mat] of [["lit", mats.lit], ["glow", mats.glow]]) {
    const g = out[key];
    if (g.idx.length) group.add(new THREE.Mesh(geometry(g.pos, g.nor, g.uv, g.col, g.idx), mat));
  }
  return group;
}

function buildProps(data, props, size, mats) {
  const glowTiles = new Set(data.atlas.glow || []);
  const parts = { lit: { pos: [], nor: [], uv: [], idx: [] }, glow: { pos: [], nor: [], uv: [], idx: [] } };
  for (const [x, y, z, type] of props) {
    const g = glowTiles.has(type) ? parts.glow : parts.lit;
    const [u0, v0, u1, v1] = tileUV(data, type, size);
    const jitter = ((x * 7 + z * 13) % 5) / 10 - 0.2;
    for (const [ax, az, bx, bz] of [[0.15, 0.15, 0.85, 0.85], [0.15, 0.85, 0.85, 0.15]]) {
      const base = g.pos.length / 3;
      [[ax, 0, az], [bx, 0, bz], [bx, 0.9, bz], [ax, 0.9, az]]
        .forEach(([px, py, pz]) => g.pos.push((x + px + jitter * 0.3) * B, (y + py) * B, (z + pz) * B));
      g.uv.push(u0, v0, u1, v0, u1, v1, u0, v1);
      for (let k = 0; k < 4; k++) g.nor.push(0, 1, 0);
      g.idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
    }
  }
  const group = new THREE.Group();
  for (const [key, mat] of [["lit", mats.prop], ["glow", mats.propGlow]]) {
    const g = parts[key];
    if (g.idx.length) group.add(new THREE.Mesh(geometry(g.pos, g.nor, g.uv, null, g.idx), mat));
  }
  return group;
}

function box(w, h, d, color, x, y, z) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({ color }));
  m.position.set(x, y, z);
  return m;
}

/** A little fox, curled up asleep. */
function fox() {
  const g = new THREE.Group();
  const fur = 0xe0782e;
  const cream = 0xf4e3c3;
  const dark = 0x2a1a14;
  g.add(box(12, 6, 8, fur, 0, 3, 0));
  g.add(box(10, 2, 7, cream, 0, 0.9, 0.4));
  const head = new THREE.Group();
  head.position.set(7.5, 3.2, 1.5);
  head.rotation.y = -0.4;
  head.add(box(6, 5, 6, fur, 0, 0, 0), box(3, 2.5, 3, cream, 3.5, -0.8, 0), box(1, 1, 1, dark, 5.2, -0.4, 0));
  head.add(box(2, 2.5, 1.2, fur, -1, 3.5, -1.8), box(2, 2.5, 1.2, fur, -1, 3.5, 1.8));
  head.add(box(1.6, 0.4, 0.6, dark, 2.9, 0.6, -1.6), box(1.6, 0.4, 0.6, dark, 2.9, 0.6, 1.6));
  g.add(head);
  const tail = new THREE.Group();
  tail.position.set(-6, 2.5, 3);
  tail.rotation.y = -1.1;
  tail.add(box(4.5, 4, 11, fur, 0, 0, 5), box(4.6, 4.1, 3, cream, 0, 0, 10));
  g.add(tail);
  return g;
}

function bird() {
  const g = new THREE.Group();
  const c = 0x3a2a40;
  g.add(box(5, 2, 2, c, 0, 0, 0), box(2, 2, 2, c, 3, 0.6, 0));
  const wings = [-1, 1].map((s) => {
    const w = new THREE.Group();
    w.position.set(0, 0.6, s);
    w.add(box(3.5, 0.5, 6, c, 0, 0, s * 3));
    g.add(w);
    return w;
  });
  return { g, wings };
}

function cloudTexture() {
  const c = document.createElement("canvas");
  c.width = c.height = 512;
  const ctx = c.getContext("2d");
  for (let i = 0; i < 70; i++) {
    const x = Math.random() * 512;
    const y = Math.random() * 512;
    const r = 30 + Math.random() * 90;
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, "rgba(255,255,255,0.55)");
    g.addColorStop(1, "rgba(255,255,255,0)");
    ctx.fillStyle = g;
    for (const dx of [-512, 0, 512]) for (const dy of [-512, 0, 512]) {
      ctx.beginPath();
      ctx.arc(x + dx, y + dy, r, 0, Math.PI * 2);
      ctx.fill();
    }
  }
  const tex = new THREE.CanvasTexture(c);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function nameTag(name) {
  const c = document.createElement("canvas");
  c.width = 256;
  c.height = 48;
  const ctx = c.getContext("2d");
  ctx.font = "bold 26px Nunito, sans-serif";
  const w = Math.min(250, ctx.measureText(name).width + 24);
  ctx.fillStyle = "rgba(20,12,8,0.6)";
  ctx.beginPath();
  ctx.roundRect((256 - w) / 2, 6, w, 36, 12);
  ctx.fill();
  ctx.fillStyle = "#fff4e4";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(name, 128, 25);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, depthWrite: false }));
  sprite.scale.set(32, 6, 1);
  return sprite;
}

export function createWorld(scene) {
  const group = new THREE.Group();
  scene.add(group);
  scene.fog = new THREE.Fog(0xf0a070, 420, 1500);

  const sun = new THREE.DirectionalLight(0xffc08a, 1.9);
  sun.position.set(-320, 220, -160);
  const fill = new THREE.HemisphereLight(0xffd2b0, 0x5a3a60, 0.9);
  const rim = new THREE.DirectionalLight(0xb08ae0, 0.5);
  rim.position.set(200, 80, 300);
  group.add(sun, fill, rim);

  const cloudTex = cloudTexture();
  cloudTex.repeat.set(5, 5);
  const clouds = [0, 1, 2].map((i) => {
    const mat = new THREE.MeshBasicMaterial({ map: cloudTex, transparent: true, depthWrite: false, color: 0xffd0b8, opacity: 0.85 - i * 0.18 });
    const m = new THREE.Mesh(new THREE.PlaneGeometry(5000, 5000), mat);
    m.rotation.x = -Math.PI / 2;
    m.position.y = -260 - i * 45;
    group.add(m);
    return m;
  });

  const island = new THREE.Group();
  group.add(island);
  const birds = [0, 1, 2, 3].map((i) => {
    const b = bird();
    Object.assign(b, { radius: 230 + i * 40, height: 60 + i * 25, speed: 0.18 + i * 0.03, phase: i * 1.7 });
    group.add(b.g);
    return b;
  });
  const friendGroup = new THREE.Group();
  island.add(friendGroup);
  let data = null;
  let sleepy = null;
  let current = null;
  let phase = "sunset";
  let friendList = [];

  // the real block textures of the game (built on this PC from the texture pack / the game), else the bundled drawing
  const atlasReady = (async () => {
    let url = "assets/island/atlas.png";
    let real = false;
    try {
      const res = await api("island_atlas");
      if (res && res.url) {
        url = res.url;
        real = true;
      }
    } catch {
      // the bundled island still works
    }
    return new Promise((resolve) => {
      new THREE.TextureLoader().load(url, (atlas) => {
        atlas.colorSpace = THREE.SRGBColorSpace;
        atlas.magFilter = real ? THREE.NearestFilter : THREE.LinearFilter; // crisp pixels like in the game
        atlas.minFilter = THREE.LinearFilter;
        atlas.generateMipmaps = false;
        resolve(atlas);
      });
    });
  })();

  function applyLook() {
    const look = LOOKS[current === "autumn" || !current ? phase : current] || LOOKS.sunset;
    sun.intensity = look.sun;
    sun.color.setHex(look.sunColor);
    fill.color.setHex(look.sky);
    fill.groundColor.setHex(look.ground);
    scene.fog.color.setHex(look.fog);
    clouds.forEach((c) => c.material.color.setHex(look.cloud));
    birds.forEach((b) => { b.g.visible = current === "autumn"; });
  }

  async function load(name) {
    const atlas = await atlasReady;
    const next = await (await fetch(`assets/island/${name}.json`)).json();
    if (name !== current) return; // a newer scene was requested meanwhile
    data = next;
    for (const child of [...island.children]) if (child !== friendGroup) island.remove(child);
    const size = { w: atlas.image.width, h: atlas.image.height };
    const mats = {
      lit: new THREE.MeshLambertMaterial({ map: atlas, vertexColors: true }),
      glow: new THREE.MeshBasicMaterial({ map: atlas }),
      prop: new THREE.MeshLambertMaterial({ map: atlas, transparent: true, alphaTest: 0.4, side: THREE.DoubleSide }),
      propGlow: new THREE.MeshBasicMaterial({ map: atlas, transparent: true, alphaTest: 0.4, side: THREE.DoubleSide }),
    };
    const [px, py, pz] = data.player;
    island.position.set(-(px + 0.5) * B, GROUND - py * B, -(pz + 0.5) * B);
    island.add(buildMeshes(data, data.blocks, size, mats), buildProps(data, data.props, size, mats));
    for (const small of data.small_islands) {
      const g = new THREE.Group();
      g.position.set(small.offset[0] * B, small.offset[1] * B, small.offset[2] * B);
      g.add(buildMeshes(data, small.blocks, size, mats), buildProps(data, small.props, size, mats));
      island.add(g);
    }
    sleepy = null;
    if (data.fox) {
      sleepy = fox();
      const [fx, fy, fz] = data.fox;
      sleepy.position.set((fx + 0.5) * B, fy * B, (fz + 0.5) * B);
      sleepy.rotation.y = 0.6;
      island.add(sleepy);
    }
    placeFriends();
    applyLook();
  }

  function placeFriends() {
    friendGroup.clear();
    if (!data) return;
    friendList.slice(0, data.friends.length).forEach((f, i) => {
      const [x, y, z] = data.friends[i];
      const p = new PlayerObject();
      const tex = new THREE.TextureLoader().load(f.skin);
      tex.magFilter = THREE.NearestFilter;
      tex.minFilter = THREE.NearestFilter;
      tex.colorSpace = THREE.SRGBColorSpace;
      p.skin.map = tex;
      p.skin.modelType = f.model === "slim" ? "slim" : "default";
      p.cape.visible = false;
      p.elytra.visible = false;
      p.ears.visible = false;
      p.position.set((x + 0.5) * B, y * B + 16, (z + 0.5) * B);
      p.rotation.y = 0.45 + i * 0.25;
      const tag = nameTag(f.name);
      tag.position.y = 22;
      p.add(tag);
      p.userData.phase = i * 1.3;
      friendGroup.add(p);
    });
  }

  return {
    group,
    setScene(name) {
      const scene2 = SCENES.includes(name) ? name : SCENES[Math.floor(Math.random() * SCENES.length)];
      if (scene2 === current) return scene2;
      current = scene2;
      load(scene2);
      return scene2;
    },
    /** Online friends standing next to you: [{ name, skin: dataURL, model }] */
    setFriends(list) {
      friendList = list;
      placeFriends();
    },
    setPhase(p) {
      phase = p;
      applyLook();
    },
    update(dt, t) {
      clouds.forEach((c, i) => { c.material.map.offset.x = t * 0.004 * (i + 1); });
      birds.forEach((b) => {
        const a = t * b.speed + b.phase;
        b.g.position.set(Math.cos(a) * b.radius, b.height + Math.sin(t * 0.9 + b.phase) * 10, Math.sin(a) * b.radius - 60);
        b.g.rotation.y = -a - Math.PI / 2;
        const flap = Math.sin(t * 9 + b.phase) * 0.7;
        b.wings[0].rotation.x = flap;
        b.wings[1].rotation.x = -flap;
      });
      if (sleepy) sleepy.scale.y = 1 + Math.sin(t * 2.2) * 0.03;
      friendGroup.children.forEach((p) => {
        const s = t * 1.4 + p.userData.phase;
        p.skin.leftArm.rotation.x = Math.sin(s) * 0.08;
        p.skin.rightArm.rotation.x = -Math.sin(s) * 0.08;
        p.skin.head.rotation.y = Math.sin(s * 0.4) * 0.35;
      });
    },
  };
}
