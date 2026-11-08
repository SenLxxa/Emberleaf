// Blockbench-Cosmetics (vom Backend in das kompakte "emodel"-Format umgewandelt) als three.js-Objekt.
// Koordinaten wie in Blockbench: Füße bei y = 0, die Figur schaut nach Norden (-z), 1 Einheit = 1 Pixel.
// Bewegungen wie im Spiel: Flügel schlagen, Capes wehen wie Stoff, Schwänze wedeln, Ohren zucken, Pet-Beine laufen.
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
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

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

// ---------- Stoff-Physik für Capes (wie im Spiel: CapeCloth.java) ----------

export const CLOTH_SEGMENTS = 12;

/**
 * Das Cape hängt an einer Kette aus Segmenten, die der Bewegung nachschwingen (der Saum stärker als die Schultern),
 * mit einer laufenden Welle und Kräuseln über die Breite. Raum: y nach oben, der Rücken ist +z, Aufhängung bei 0.
 */
export class ClothSim {
  constructor() {
    const n = CLOTH_SEGMENTS;
    this.theta = new Float32Array(n);
    this.sin = new Float32Array(n);
    this.cos = new Float32Array(n);
    this.py = new Float32Array(n + 1);
    this.pz = new Float32Array(n + 1);
    this.fresh = true;
    this.length = 16;
    this.seg = 16 / n;
  }

  /** m: { time, walk 0..1, airborne, crouching, capeLean (Grad) }; dt in Sekunden (< 0 = sofort einrasten). */
  solve(dt, length, m) {
    const n = CLOTH_SEGMENTS;
    this.length = Math.max(1, length);
    this.seg = this.length / n;
    this.time = m.time;
    const walk = clamp(m.walk || 0, 0, 1);
    const air = m.airborne ? 1 : 0;
    const base = rad(clamp(6 + (m.capeLean || 0), 2, 82)) + (m.crouching ? 0.3 : 0);
    const waveA = rad(3 + 9 * walk + 7 * air);
    const waveF = 1.6 + 5.5 * walk + 3 * air;
    this.rippleAmp = 0.16 + 0.42 * walk + 0.3 * air;
    this.rippleFreq = 2.2 + 6 * walk + 2 * air;
    const snap = this.fresh || dt < 0;
    const step = Math.min(0.1, Math.max(0, dt));
    for (let k = 0; k < n; k++) {
      const s = (k + 0.5) / n;
      const target = clamp(base * (0.5 + 0.9 * s) + waveA * s * Math.sin(this.time * waveF - k * 0.42), 0.02, 1.45);
      if (snap) this.theta[k] = target;
      else this.theta[k] += (target - this.theta[k]) * (1 - Math.exp(-step * (12 - 7 * s)));
      this.sin[k] = Math.sin(this.theta[k]);
      this.cos[k] = Math.cos(this.theta[k]);
      this.py[k + 1] = this.py[k] - this.seg * this.cos[k];
      this.pz[k + 1] = this.pz[k] + this.seg * this.sin[k];
    }
    this.fresh = false;
    return this;
  }

  /** Biegt Punkt p und Normale nrm (Vector3, relativ zur Aufhängung) auf das Cape. lift = Winkel, den das Objekt schon selbst kippt. */
  apply(p, nrm, lift = 0) {
    const n = CLOTH_SEGMENTS;
    const d = -p.y;
    let off = p.z;
    const fk = d / this.seg;
    const k = clamp(Math.floor(fk), 0, n - 1);
    const along = d - k * this.seg;
    const c = fk - 0.5;
    const k0 = clamp(Math.floor(c), 0, n - 1);
    const k1 = Math.min(k0 + 1, n - 1);
    const f = c < 0 ? 0 : clamp(c - Math.floor(c), 0, 1);
    const th = this.theta[k0] + (this.theta[k1] - this.theta[k0]) * f - lift;
    // Kette ohne den schon vorhandenen Kippwinkel nachrechnen
    let sy = 0;
    let sz = 0;
    for (let i = 0; i < k; i++) {
      sy -= this.seg * Math.cos(this.theta[i] - lift);
      sz += this.seg * Math.sin(this.theta[i] - lift);
    }
    sy -= along * Math.cos(this.theta[k] - lift);
    sz += along * Math.sin(this.theta[k] - lift);
    off += this.rippleAmp * clamp(d / this.length, 0, 1) * Math.sin(p.x * 0.55 + this.time * this.rippleFreq - d * 0.3);
    const st = Math.sin(th);
    const ct = Math.cos(th);
    p.set(p.x, sy + off * st, sz + off * ct);
    if (nrm) nrm.set(nrm.x, nrm.y * ct + nrm.z * st, -nrm.y * st + nrm.z * ct);
  }

  /** Mitte des Saums (unterer Rand, Außenseite) im Cape-Raum. */
  hem(lift = 0, out = new THREE.Vector3()) {
    const p = out.set(0, -this.length, 1);
    this.apply(p, null, lift);
    return p;
  }
}

// Fläche in ein Raster zerlegen, damit sie sich biegen kann (für Cape-Teile)
function cuts(dx, dy, seg) {
  return clamp(Math.ceil(Math.max(Math.abs(dy) / seg, Math.abs(dx) / 2.5) - 1e-3), 1, 16);
}

function cubeGeometry(cube, res, seg = 0) {
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
    const cu = corners.map((_, k) => uvs[(k + 4 - turns) % 4]);
    const [tl, tr, br, bl] = corners;
    const na = seg ? cuts(tr[0] - tl[0], tr[1] - tl[1], seg) : 1;
    const nb = seg ? cuts(bl[0] - tl[0], bl[1] - tl[1], seg) : 1;
    const base = pos.length / 3;
    for (let j = 0; j <= nb; j++) {
      const fb = j / nb;
      for (let i = 0; i <= na; i++) {
        const fa = i / na;
        for (let e = 0; e < 3; e++) {
          const top = tl[e] + (tr[e] - tl[e]) * fa;
          const bottom = bl[e] + (br[e] - bl[e]) * fa;
          pos.push(top + (bottom - top) * fb);
        }
        for (let e = 0; e < 2; e++) {
          const top = cu[0][e] + (cu[1][e] - cu[0][e]) * fa;
          const bottom = cu[3][e] + (cu[2][e] - cu[3][e]) * fa;
          uv.push(top + (bottom - top) * fb);
        }
        nor.push(...NORMALS[face]);
      }
    }
    for (let j = 0; j < nb; j++) {
      for (let i = 0; i < na; i++) {
        const v00 = base + j * (na + 1) + i;
        const v10 = v00 + 1;
        const v01 = v00 + na + 1;
        const v11 = v01 + 1;
        idx.push(v00, v01, v11, v00, v11, v10);
      }
    }
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

  // Cape-Teile: alles, was an einem "cape"-Knochen hängt, biegt sich mit ihm
  const capeRoot = [];
  data.bones.forEach((bone, i) => {
    const p = bone.parent >= 0 && bone.parent < i ? bone.parent : -1;
    capeRoot[i] = p >= 0 && capeRoot[p] >= 0 ? capeRoot[p] : kind(bone.name) === "cape" ? i : -1;
  });
  let capeLength = 1;
  data.bones.forEach((bone, i) => {
    if (capeRoot[i] < 0) return;
    const top = data.bones[capeRoot[i]].origin[1];
    for (const c of bone.cubes) capeLength = Math.max(capeLength, top - Math.min(c.from[1], c.to[1]) + (c.inflate || 0));
  });
  const capeSeg = capeLength / CLOTH_SEGMENTS;
  const clothMeshes = [];

  data.bones.forEach((bone, i) => {
    const g = new THREE.Group();
    g.rotation.order = "ZYX";
    const parent = bone.parent >= 0 ? data.bones[bone.parent] : null;
    const po = parent ? parent.origin : [0, 0, 0];
    g.position.set(bone.origin[0] - po[0], bone.origin[1] - po[1], bone.origin[2] - po[2]);
    g.userData = { rest: bone.rotation.map(rad), kind: kind(bone.name), side: side(bone.name), name: bone.name.toLowerCase(), glow: /glow|leucht/i.test(bone.name) };
    if (parent && groups[bone.parent]?.userData.glow) g.userData.glow = true;
    for (const cube of bone.cubes) {
      const geo = cubeGeometry(cube, data.resolution, capeRoot[i] >= 0 ? capeSeg : 0);
      geos.push(geo);
      const holder = new THREE.Group();
      holder.rotation.order = "ZYX";
      holder.position.set(cube.origin[0] - bone.origin[0], cube.origin[1] - bone.origin[1], cube.origin[2] - bone.origin[2]);
      holder.rotation.set(...cube.rotation.map(rad));
      const mesh = new THREE.Mesh(geo, g.userData.glow ? glow : lit);
      holder.add(mesh);
      g.add(holder);
      if (capeRoot[i] >= 0) {
        mesh.frustumCulled = false;
        clothMeshes.push({ mesh, root: capeRoot[i], rest: Float32Array.from(geo.attributes.position.array), restN: Float32Array.from(geo.attributes.normal.array) });
      }
    }
    (parent ? groups[bone.parent] : root).add(g);
    groups.push(g);
  });
  const cloth = clothMeshes.length ? new ClothSim() : null;
  let lastT = null;
  const tmpM = new THREE.Matrix4();
  const tmpInv = new THREE.Matrix4();
  const rootInv = new THREE.Matrix4();
  const p = new THREE.Vector3();
  const nrm = new THREE.Vector3();

  const bendCape = () => {
    root.updateMatrixWorld(true);
    for (const cm of clothMeshes) {
      const rg = groups[cm.root];
      rootInv.copy(rg.matrixWorld).invert();
      tmpM.multiplyMatrices(rootInv, cm.mesh.matrixWorld); // Mesh -> Cape-Raum
      tmpInv.copy(tmpM).invert();
      const pos = cm.mesh.geometry.attributes.position;
      const nor = cm.mesh.geometry.attributes.normal;
      for (let v = 0; v < pos.count; v++) {
        p.fromArray(cm.rest, v * 3).applyMatrix4(tmpM);
        nrm.fromArray(cm.restN, v * 3).transformDirection(tmpM);
        cloth.apply(p, nrm);
        p.applyMatrix4(tmpInv);
        nrm.transformDirection(tmpInv);
        pos.setXYZ(v, p.x, p.y, p.z);
        nor.setXYZ(v, nrm.x, nrm.y, nrm.z);
      }
      pos.needsUpdate = true;
      nor.needsUpdate = true;
    }
  };

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
    texture: tex,
    hasGlow: groups.some((g) => g.userData.glow),
    cloth,
    /** Saum-Mitte des Capes in Weltkoordinaten (für Funken), oder null. */
    hemWorld(out = new THREE.Vector3()) {
      if (!cloth || !clothMeshes.length) return null;
      return groups[clothMeshes[0].root].localToWorld(cloth.hem(0, out));
    },
    update(t, motion = {}) {
      const m = { walk: 0, walkPos: t * 6, airborne: false, crouching: false, capeLean: 0, capeSide: 0, sitting: false, ...motion };
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
        if (capeRoot[i] >= 0) {
          // Capes biegen sich als Stoff (ClothSim), nur der oberste Cape-Knochen schwingt seitlich
          if (capeRoot[i] === i) rz += rad(m.capeSide);
          g.rotation.set(rx, ry, rz);
          return;
        }
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
      if (cloth) {
        cloth.solve(lastT === null ? -1 : t - lastT, capeLength, { ...m, time: t });
        lastT = t;
        bendCape();
      }
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
