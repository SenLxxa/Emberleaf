// Blockbench-Cosmetics (vom Backend in das kompakte "emodel"-Format umgewandelt) als three.js-Objekt.
// Koordinaten wie in Blockbench: Füße bei y = 0, die Figur schaut nach Norden (-z), 1 Einheit = 1 Pixel.
// Bewegungen wie im Spiel: Flügel schlagen, Capes schwingen, Schwänze wedeln, Ohren zucken, Pet-Beine laufen.
import * as THREE from "../vendor/esm/three.js";

const FACES = {
  // Ecken von außen gesehen: oben links, oben rechts, unten rechts, unten links
  north: (a, b) => [[b[0], b[1], a[2]], [a[0], b[1], a[2]], [a[0], a[1], a[2]], [b[0], a[1], a[2]]],
  east: (a, b) => [[b[0], b[1], b[2]], [b[0], b[1], a[2]], [b[0], a[1], a[2]], [b[0], a[1], b[2]]],
  south: (a, b) => [[a[0], b[1], b[2]], [b[0], b[1], b[2]], [b[0], a[1], b[2]], [a[0], a[1], b[2]]],
  west: (a, b) => [[a[0], b[1], a[2]], [a[0], b[1], b[2]], [a[0], a[1], b[2]], [a[0], a[1], a[2]]],
  up: (a, b) => [[a[0], b[1], a[2]], [b[0], b[1], a[2]], [b[0], b[1], b[2]], [a[0], b[1], b[2]]],
  down: (a, b) => [[a[0], a[1], b[2]], [b[0], a[1], b[2]], [b[0], a[1], a[2]], [a[0], a[1], a[2]]],
};
const NORMALS = { north: [0, 0, -1], east: [1, 0, 0], south: [0, 0, 1], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };
const rad = (d) => (d * Math.PI) / 180;

function kind(name) {
  const n = name.toLowerCase();
  if (/wing|flügel|fluegel/.test(n)) return "wing";
  if (/cape|umhang/.test(n)) return "cape";
  if (/tail|schwanz/.test(n)) return "tail";
  if (/ear|ohr/.test(n)) return "ear";
  if (/leg|bein/.test(n)) return "leg";
  if (n === "head" || n.includes("kopf")) return "head";
  return "";
}

function side(name) {
  const n = name.toLowerCase();
  if (n.includes("left") || n.endsWith("_l")) return -1;
  if (n.includes("right") || n.endsWith("_r")) return 1;
  return 0;
}

function cubeGeometry(cube, res) {
  const inf = cube.inflate || 0;
  const a = [0, 1, 2].map((i) => Math.min(cube.from[i], cube.to[i]) - inf - cube.origin[i]);
  const b = [0, 1, 2].map((i) => Math.max(cube.from[i], cube.to[i]) + inf - cube.origin[i]);
  const pos = [];
  const nor = [];
  const uv = [];
  const idx = [];
  for (const [face, f] of Object.entries(cube.faces)) {
    const corners = FACES[face](a, b);
    const [u0, v0, u1, v1, , rot = 0] = f;
    const uvs = [[u0, v0], [u1, v0], [u1, v1], [u0, v1]].map(([u, v]) => [u / res[0], 1 - v / res[1]]);
    const turns = ((Math.round(rot / 90) % 4) + 4) % 4;
    const base = pos.length / 3;
    corners.forEach((c, k) => {
      pos.push(...c);
      nor.push(...NORMALS[face]);
      uv.push(...uvs[(k + 4 - turns) % 4]);
    });
    idx.push(base, base + 3, base + 2, base, base + 2, base + 1);
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
  geo.setAttribute("normal", new THREE.Float32BufferAttribute(nor, 3));
  geo.setAttribute("uv", new THREE.Float32BufferAttribute(uv, 2));
  geo.setIndex(idx);
  return geo;
}

/** Builds the model. Returns { object, update(t, motion), dispose() }; object is in Blockbench space. */
export function buildEModel(data) {
  let markReady;
  const ready = new Promise((resolve) => { markReady = resolve; });
  const tex = new THREE.TextureLoader().load(data.textures[0], () => markReady(), undefined, () => markReady());
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.colorSpace = THREE.SRGBColorSpace;
  const lit = new THREE.MeshLambertMaterial({ map: tex, transparent: true, alphaTest: 0.08, side: THREE.DoubleSide });
  const glow = new THREE.MeshBasicMaterial({ map: tex, transparent: true, alphaTest: 0.08, side: THREE.DoubleSide });
  const root = new THREE.Group();
  const groups = [];
  const geos = [];
  data.bones.forEach((bone, i) => {
    const g = new THREE.Group();
    g.rotation.order = "ZYX";
    const parent = bone.parent >= 0 ? data.bones[bone.parent] : null;
    const po = parent ? parent.origin : [0, 0, 0];
    g.position.set(bone.origin[0] - po[0], bone.origin[1] - po[1], bone.origin[2] - po[2]);
    g.userData = { rest: bone.rotation.map(rad), kind: kind(bone.name), side: side(bone.name), name: bone.name.toLowerCase(), glow: /glow|leucht/i.test(bone.name) };
    if (parent && groups[bone.parent]?.userData.glow) g.userData.glow = true;
    for (const cube of bone.cubes) {
      const geo = cubeGeometry(cube, data.resolution);
      geos.push(geo);
      const holder = new THREE.Group();
      holder.rotation.order = "ZYX";
      holder.position.set(cube.origin[0] - bone.origin[0], cube.origin[1] - bone.origin[1], cube.origin[2] - bone.origin[2]);
      holder.rotation.set(...cube.rotation.map(rad));
      holder.add(new THREE.Mesh(geo, g.userData.glow ? glow : lit));
      g.add(holder);
    }
    (parent ? groups[bone.parent] : root).add(g);
    groups.push(g);
  });

  const clipFor = (motion) => {
    const find = (...names) => data.animations.find((a) => names.some((n) => a.name.toLowerCase().includes(n)));
    if (motion.sitting) return find("sit", "idle");
    if (motion.walk > 0.15) return find("walk", "run", "lauf", "idle");
    return find("idle", "loop") || data.animations[0];
  };
  const sample = (frames, t) => {
    if (!frames?.length) return null;
    let a = frames[0];
    if (t <= a[0] || frames.length === 1) return a.slice(1, 4);
    for (const b of frames.slice(1)) {
      if (t <= b[0]) {
        let k = (t - a[0]) / Math.max(1e-4, b[0] - a[0]);
        k = k * k * (3 - 2 * k);
        return [0, 1, 2].map((i) => a[i + 1] + (b[i + 1] - a[i + 1]) * k);
      }
      a = b;
    }
    return a.slice(1, 4);
  };

  return {
    object: root,
    ready,
    update(t, motion = {}) {
      const m = { walk: 0, walkPos: t * 6, airborne: false, crouching: false, capeLean: 0, sitting: false, ...motion };
      const clip = clipFor(m);
      const ct = clip ? (clip.loop ? t % clip.length : Math.min(t, clip.length)) : 0;
      groups.forEach((g, i) => {
        const u = g.userData;
        let [rx, ry, rz] = u.rest;
        const ch = clip?.bones[String(i)];
        const r = sample(ch?.rotation, ct);
        if (r) { rx += rad(r[0]); ry += rad(r[1]); rz += rad(r[2]); }
        const p = sample(ch?.position, ct);
        const bone = data.bones[i];
        const po = bone.parent >= 0 ? data.bones[bone.parent].origin : [0, 0, 0];
        g.position.set(bone.origin[0] - po[0] + (p?.[0] || 0), bone.origin[1] - po[1] + (p?.[1] || 0), bone.origin[2] - po[2] + (p?.[2] || 0));
        const s = sample(ch?.scale, ct);
        if (s) g.scale.set(...s);
        const sd = u.side;
        switch (u.kind) {
          case "wing": {
            if (!sd) break;
            const speed = m.airborne ? 4.6 : 1.5 + m.walk * 1.4;
            const amp = m.airborne ? 38 : 9 + m.walk * 12;
            const sweep = m.crouching ? 62 : 18 + Math.sin((t * speed * Math.PI * 2) / 2) * amp;
            const lift = m.crouching ? 0 : (m.airborne ? 14 : 4) + Math.cos((t * speed * Math.PI * 2) / 2) * (m.airborne ? 16 : 4);
            ry += rad(-sd * sweep);
            rz += rad(sd * lift);
            break;
          }
          case "cape": rx -= rad(6 + m.capeLean + Math.sin(t * 1.3) * 3); break;
          case "tail": ry += rad(Math.sin(t * (m.walk > 0.15 ? 9 : 4.5)) * (m.walk > 0.15 ? 12 : 18)); break;
          case "ear": rz += rad(sd * 8 * Math.max(0, Math.sin(t * 1.3 + sd)) ** 10); break;
          case "leg": {
            if (m.sitting) break;
            const front = /front|vorn/.test(u.name);
            const phase = front === sd < 0 ? 1 : -1;
            rx += rad(Math.sin(m.walkPos * 0.9) * 32 * Math.min(1, m.walk * 1.5) * phase);
            break;
          }
          case "head": ry += rad(Math.sin(t * 0.45) * 12 * (1 - Math.min(1, m.walk))); break;
          default: break;
        }
        g.rotation.set(rx, ry, rz);
      });
    },
    dispose() {
      root.removeFromParent();
      geos.forEach((g) => g.dispose());
      tex.dispose();
      lit.dispose();
      glow.dispose();
    },
  };
}

/** Wraps a Blockbench-space object so it can hang on a skinview3d part. originY = Blockbench height of the part's pivot. */
export function mountOnPart(object, originY) {
  const turn = new THREE.Group();
  turn.rotation.y = Math.PI; // Blockbench looks north (-z), skinview3d figures look +z
  const shift = new THREE.Group();
  shift.position.y = -originY;
  shift.add(object);
  turn.add(shift);
  return turn;
}
