// Werkstatt: eigene 3D-Cosmetics Block für Block bauen, und Capes malen oder als PNG hochladen.
import * as THREE from "../vendor/esm/three.js";
import { SkinViewer } from "../vendor/esm/skinview3d.js";
import { VOXEL_MATERIAL, capeFrames, paintCape, voxelGeometry } from "./cosmetics.js";
import { $, $$, api, esc, setBusy, toast } from "./ui.js";

export const PALETTE = [
  "#f4e3c3", "#e8d2a6", "#c9a77a", "#8a5a32", "#5b3a22", "#2b1a14",
  "#ffd76a", "#f2a541", "#f2813a", "#e8743b", "#d9462b", "#a8321f",
  "#ff9eb3", "#c76b98", "#7d5ba6", "#4f6fb8", "#6fb7d8", "#9fe3e0",
  "#9fd36b", "#5f9a4a", "#2f4d2a", "#ffffff", "#8a8580", "#3a3330",
];
const LIMIT = 30;
const LABELS = { head: "Kopf", body: "Körper", wings: "Flügel", pet: "Pet", cape: "Cape" };

function shell(category, name, toolsHtml) {
  const root = document.createElement("div");
  root.className = "editor-backdrop";
  root.innerHTML = `
    <div class="editor">
      <header class="editor-head">
        <div class="editor-title"><span>Werkstatt</span><small>${esc(LABELS[category])}</small></div>
        <input class="input editor-name" maxlength="30" value="${esc(name)}" placeholder="Name deines Cosmetics">
        <div class="editor-actions">
          <button class="btn icon" data-ed="undo" title="Rückgängig (Strg+Z)">↶</button>
          <button class="btn icon" data-ed="redo" title="Wiederholen (Strg+Y)">↷</button>
          <button class="btn" data-ed="clear">Leeren</button>
          <button class="btn" data-ed="cancel">Abbrechen</button>
          <button class="btn primary" data-ed="save">Speichern</button>
        </div>
      </header>
      <div class="editor-main">
        <aside class="editor-tools">${toolsHtml}</aside>
        <div class="editor-stage"><canvas></canvas><div class="editor-hint"></div></div>
      </div>
    </div>`;
  document.body.append(root);
  return root;
}

function paletteHtml(current) {
  return `<div class="palette">${PALETTE.map((c) => `<button class="swatch ${c === current ? "active" : ""}" data-color="${c}" style="background:${c}" title="${c}"></button>`).join("")}</div>
    <label class="custom-color">Eigene Farbe <input type="color" value="${current}" data-ed="color"></label>`;
}

function makeViewer(canvas, stage, skin) {
  const viewer = new SkinViewer({ canvas, width: stage.clientWidth, height: stage.clientHeight, fov: 40 });
  viewer.controls.enablePan = false;
  viewer.animation = null;
  if (skin?.skin) viewer.loadSkin(skin.skin, { model: skin.model });
  const resize = new ResizeObserver(() => {
    if (stage.clientWidth && stage.clientHeight) viewer.setSize(stage.clientWidth, stage.clientHeight);
  });
  resize.observe(stage);
  return { viewer, resize };
}

// ---------- Voxel-Editor (Kopf, Körper, Flügel, Pets) ----------

export function openVoxelEditor({ category, item, skin, onSaved }) {
  let color = PALETTE[9];
  let tool = "build";
  let mirror = category !== "wings";
  const cells = new Map((item?.voxels || []).map(([x, y, z, c]) => [`${x},${y},${z}`, c]));
  const history = [];
  const future = [];
  let dirty = false;

  const root = shell(category, item?.name || "", `
    <div class="tool-group"><label>Werkzeug</label>
      <div class="tool-grid">
        <button data-tool="build" class="active">🧱 Bauen</button>
        <button data-tool="paint">🖌️ Malen</button>
        <button data-tool="erase">🧽 Löschen</button>
        <button data-tool="pick">💧 Pipette</button>
      </div>
    </div>
    <label class="check ${category === "wings" ? "disabled" : ""}"><input type="checkbox" data-ed="mirror" ${mirror ? "checked" : ""} ${category === "wings" ? "disabled" : ""}> Spiegeln (links = rechts)</label>
    <div class="tool-group"><label>Farbe</label>${paletteHtml(color)}</div>
    <div class="editor-count"></div>
    <p class="editor-tip">${category === "wings" ? "Du baust den linken Flügel, der rechte wird automatisch gespiegelt." : category === "pet" ? "Bau dein Pet auf der Bodenplatte. Vorne ist die Seite zur Kamera." : "Klick auf deine Figur, um dort Blöcke anzusetzen."}</p>`);
  const canvas = $("canvas", root);
  const stage = $(".editor-stage", root);
  $(".editor-hint", root).textContent = "Linksklick: Werkzeug · Rechtsklick: Löschen · Ziehen: Drehen · Mausrad: Zoom";
  const { viewer, resize } = makeViewer(canvas, stage, skin);
  const player = viewer.playerObject;

  // Wo das Modell hängt und woran man den ersten Block ansetzt
  const model = new THREE.Group();
  let anchorSize;
  let anchorPos;
  let inside = false;
  let mirrorMesh = null;
  let mirrorGroup = null;
  if (category === "head") {
    player.skin.head.add(model);
    anchorSize = [8.02, 8.02, 8.02];
    anchorPos = [0, 4, 0];
    viewer.camera.position.set(28, 30, 44);
    viewer.controls.target.set(0, 12, 0);
  } else if (category === "body") {
    player.skin.body.add(model);
    anchorSize = [8.02, 12.02, 4.02];
    anchorPos = [0, 0, 0];
    viewer.camera.position.set(26, 12, 44);
    viewer.controls.target.set(0, 2, 0);
  } else if (category === "wings") {
    const root3 = new THREE.Group();
    root3.position.set(0, 3, -2.6);
    const left = new THREE.Group();
    left.position.x = 1.2;
    model.position.z = -0.5;
    left.add(model);
    mirrorGroup = new THREE.Group();
    mirrorGroup.position.set(-1.2, 0, 0);
    mirrorGroup.scale.x = -1;
    root3.add(left, mirrorGroup);
    player.skin.body.add(root3);
    anchorSize = [28, 30, 0.05];
    anchorPos = [14, 0, 0.5];
    inside = true;
    viewer.camera.position.set(-34, 16, -58);
    viewer.controls.target.set(0, 6, 0);
  } else {
    player.visible = false;
    viewer.scene.add(model);
    const grid = new THREE.GridHelper(24, 24, 0x8a6a4a, 0x4a3828);
    grid.position.y = 0.01;
    viewer.scene.add(grid);
    anchorSize = [24, 0.05, 24];
    anchorPos = [0, -0.025, 0];
    viewer.camera.position.set(22, 20, 30);
    viewer.controls.target.set(0, 4, 0);
  }
  viewer.controls.update();

  const anchor = new THREE.Mesh(new THREE.BoxGeometry(...anchorSize), new THREE.MeshBasicMaterial({ visible: false }));
  anchor.position.set(...anchorPos);
  model.add(anchor);

  const cube = new THREE.BoxGeometry(1, 1, 1).translate(0.5, 0.5, 0.5);
  const materials = new Map();
  const mat = (c) => {
    if (!materials.has(c)) materials.set(c, new THREE.MeshStandardMaterial({ color: c, roughness: 0.95 }));
    return materials.get(c);
  };
  const meshes = new Map();

  const ghost = new THREE.Group();
  const ghostFill = new THREE.Mesh(cube, new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.45, depthWrite: false }));
  const ghostEdge = new THREE.LineSegments(new THREE.EdgesGeometry(cube), new THREE.LineBasicMaterial({ color: 0xffffff }));
  ghost.add(ghostFill, ghostEdge);
  ghost.visible = false;
  model.add(ghost);

  function rebuild() {
    for (const [key, mesh] of meshes) {
      if (!cells.has(key)) { mesh.removeFromParent(); meshes.delete(key); }
    }
    for (const [key, c] of cells) {
      let mesh = meshes.get(key);
      if (!mesh) {
        mesh = new THREE.Mesh(cube, mat(c));
        mesh.position.set(...key.split(",").map(Number));
        mesh.userData.key = key;
        model.add(mesh);
        meshes.set(key, mesh);
      } else if (mesh.material !== mat(c)) {
        mesh.material = mat(c);
      }
    }
    if (mirrorGroup) {
      if (mirrorMesh) { mirrorMesh.removeFromParent(); mirrorMesh.geometry.dispose(); }
      mirrorMesh = new THREE.Mesh(voxelGeometry(list()), VOXEL_MATERIAL);
      mirrorMesh.position.z = -0.5;
      mirrorGroup.add(mirrorMesh);
    }
    $(".editor-count", root).textContent = `${cells.size} ${cells.size === 1 ? "Block" : "Blöcke"}`;
  }

  const list = () => [...cells].map(([key, c]) => [...key.split(",").map(Number), c]);
  const snapshot = () => { history.push(JSON.stringify([...cells])); if (history.length > 80) history.shift(); future.length = 0; dirty = true; };
  const restore = (json) => { cells.clear(); JSON.parse(json).forEach(([k, c]) => cells.set(k, c)); rebuild(); };
  const mirrored = ([x, y, z]) => [-x - 1, y, z];
  const inBounds = (cell) => cell.every((v) => Math.abs(v) <= LIMIT);
  const keyOf = (cell) => cell.join(",");

  const ray = new THREE.Raycaster();
  const ndc = new THREE.Vector2();
  function hitAt(ev) {
    const rect = canvas.getBoundingClientRect();
    ndc.set(((ev.clientX - rect.left) / rect.width) * 2 - 1, -((ev.clientY - rect.top) / rect.height) * 2 + 1);
    ray.setFromCamera(ndc, viewer.camera);
    const hit = ray.intersectObjects([...meshes.values(), anchor], false)[0];
    if (!hit) return null;
    const n = hit.face.normal;
    if (hit.object === anchor) {
      const p = model.worldToLocal(hit.point.clone());
      const s = inside ? -0.5 : 0.5;
      return { place: [Math.floor(p.x + n.x * s), Math.floor(p.y + n.y * s), Math.floor(p.z + n.z * s)], cell: null };
    }
    const cell = hit.object.userData.key.split(",").map(Number);
    return { cell, place: [cell[0] + n.x, cell[1] + n.y, cell[2] + n.z] };
  }

  function apply(hit, action) {
    const targets = (cell) => (mirror ? [cell, mirrored(cell)] : [cell]);
    if (action === "build") {
      if (!inBounds(hit.place) || cells.has(keyOf(hit.place))) return;
      snapshot();
      targets(hit.place).forEach((c) => cells.set(keyOf(c), color));
    } else if (!hit.cell) {
      return;
    } else if (action === "paint") {
      snapshot();
      targets(hit.cell).forEach((c) => { if (cells.has(keyOf(c))) cells.set(keyOf(c), color); });
    } else if (action === "erase") {
      snapshot();
      targets(hit.cell).forEach((c) => cells.delete(keyOf(c)));
    } else if (action === "pick") {
      setColor(cells.get(keyOf(hit.cell)));
      setTool("build");
      return;
    }
    rebuild();
  }

  function setTool(next) {
    tool = next;
    $$("[data-tool]", root).forEach((b) => b.classList.toggle("active", b.dataset.tool === tool));
  }
  function setColor(next) {
    color = next;
    ghostFill.material.color.set(color);
    $$(".swatch", root).forEach((s) => s.classList.toggle("active", s.dataset.color === color));
    $("[data-ed=color]", root).value = color;
  }

  let down = null;
  canvas.addEventListener("contextmenu", (e) => e.preventDefault());
  canvas.addEventListener("pointerdown", (e) => { down = { x: e.clientX, y: e.clientY, button: e.button }; });
  canvas.addEventListener("pointerup", (e) => {
    if (!down || Math.hypot(e.clientX - down.x, e.clientY - down.y) > 5) { down = null; return; }
    const hit = hitAt(e);
    if (hit) apply(hit, down.button === 2 ? "erase" : tool);
    down = null;
  });
  canvas.addEventListener("pointermove", (e) => {
    const hit = hitAt(e);
    const action = tool;
    const cell = hit && (action === "build" ? hit.place : hit.cell);
    ghost.visible = Boolean(cell && inBounds(cell));
    if (ghost.visible) {
      ghost.position.set(...cell);
      ghostFill.material.color.set(action === "erase" ? "#ff4f4f" : color);
    }
  });
  canvas.addEventListener("pointerleave", () => { ghost.visible = false; });

  async function save() {
    const btn = $("[data-ed=save]", root);
    if (!cells.size) return toast("Dein Modell ist noch leer. Setz ein paar Blöcke!", "warn");
    setBusy(btn, true, "Speichert …");
    try {
      const saved = await api("save_cosmetic", {
        item: { id: item?.custom ? item.id : undefined, category, name: $(".editor-name", root).value, voxels: list() },
      });
      close();
      toast(`„${saved.name}“ gespeichert 🎉`);
      onSaved?.(saved);
    } catch (e) {
      toast(e.message, "error");
      setBusy(btn, false);
    }
  }

  function close() {
    document.removeEventListener("keydown", onKey);
    resize.disconnect();
    viewer.dispose();
    root.remove();
  }

  function onKey(e) {
    if (e.target instanceof HTMLInputElement && e.target.type === "text") return;
    if (e.ctrlKey && e.key.toLowerCase() === "z") { e.preventDefault(); undo(); }
    else if (e.ctrlKey && e.key.toLowerCase() === "y") { e.preventDefault(); redo(); }
    else if (e.key === "b") setTool("build");
    else if (e.key === "p") setTool("paint");
    else if (e.key === "e") setTool("erase");
    else if (e.key === "Escape") cancel();
  }
  const undo = () => { if (history.length) { future.push(JSON.stringify([...cells])); restore(history.pop()); } };
  const redo = () => { if (future.length) { history.push(JSON.stringify([...cells])); restore(future.pop()); } };
  const cancel = () => { if (!dirty || window.confirm("Änderungen verwerfen?")) close(); };

  root.addEventListener("click", (e) => {
    const t = e.target.closest("[data-tool]");
    if (t) setTool(t.dataset.tool);
    const sw = e.target.closest("[data-color]");
    if (sw) setColor(sw.dataset.color);
    const action = e.target.closest("[data-ed]")?.dataset.ed;
    if (action === "undo") undo();
    if (action === "redo") redo();
    if (action === "clear" && cells.size) { snapshot(); cells.clear(); rebuild(); }
    if (action === "cancel") cancel();
    if (action === "save") save();
  });
  $("[data-ed=color]", root).addEventListener("input", (e) => setColor(e.target.value));
  $("[data-ed=mirror]", root).addEventListener("change", (e) => { mirror = e.target.checked; });
  document.addEventListener("keydown", onKey);
  rebuild();
  $(".editor-name", root).focus();
}

// ---------- Cape-Editor: malen oder PNG (auch animiert) ----------

export function openCapeEditor({ item, skin, onSaved }) {
  let color = PALETTE[10];
  let mode = item?.image ? "image" : "paint";
  let image = item?.image || "";
  let frames = item?.frames || 1;
  let fps = item?.fps || 8;
  const grid = Array.from({ length: 16 }, (_, y) => Array.from({ length: 10 }, (_, x) => (item?.design ? item.design(x, y, 0) : "#5b3a22")));

  const root = shell("cape", item?.custom ? item.name : (item ? `Mein ${item.name}` : ""), `
    <div class="tool-group"><label>Cape malen</label><canvas class="cape-grid" width="200" height="320"></canvas></div>
    <div class="tool-group"><label>Farbe</label>${paletteHtml(color)}</div>
    <div class="tool-group"><label>Oder Bild hochladen</label>
      <label class="btn upload">📁 PNG auswählen<input type="file" accept="image/png" hidden data-ed="file"></label>
      <p class="editor-tip">Für animierte Capes: alle Bilder untereinander in eine PNG (je 64×32 Pixel).</p>
      <div class="image-opts" hidden>
        <label>Bilder <input class="input small" type="number" min="1" max="64" data-ed="frames"></label>
        <label>Tempo <input type="range" min="1" max="30" data-ed="fps"></label>
      </div>
    </div>`);
  $("[data-ed=undo]", root).hidden = true;
  $("[data-ed=redo]", root).hidden = true;
  $(".editor-hint", root).textContent = "Ziehen: Drehen · so sehen dich die anderen von hinten";
  const stage = $(".editor-stage", root);
  const { viewer, resize } = makeViewer($("canvas", stage), stage, skin);
  viewer.camera.position.set(-30, 10, -52);
  viewer.controls.target.set(0, 2, 0);
  viewer.controls.update();
  viewer.autoRotate = false;

  const gridCanvas = $(".cape-grid", root);
  const gctx = gridCanvas.getContext("2d");
  let previewFrames = [];
  let frameIndex = 0;
  let timer = 0;
  const startTimer = () => {
    clearInterval(timer);
    timer = setInterval(() => {
      if (previewFrames.length > 1) {
        frameIndex = (frameIndex + 1) % previewFrames.length;
        viewer.loadCape(previewFrames[frameIndex]);
      }
    }, 1000 / fps);
  };
  startTimer();

  function drawGrid() {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 10; x++) {
      gctx.fillStyle = grid[y][x];
      gctx.fillRect(x * 20, y * 20, 20, 20);
    }
    gctx.strokeStyle = "rgba(0,0,0,.25)";
    for (let i = 0; i <= 10; i++) gctx.strokeRect(i * 20, 0, 0, 320);
    for (let i = 0; i <= 16; i++) gctx.strokeRect(0, i * 20, 200, 0);
  }

  async function refresh() {
    if (mode === "paint") {
      previewFrames = [paintCape((x, y) => grid[y][x])];
    } else {
      previewFrames = await capeFrames({ image, frames });
    }
    frameIndex = 0;
    viewer.loadCape(previewFrames[0]);
    $(".image-opts", root).hidden = mode !== "image";
    gridCanvas.classList.toggle("dim", mode === "image");
  }

  let painting = false;
  const paintAt = (e) => {
    const r = gridCanvas.getBoundingClientRect();
    const x = Math.floor(((e.clientX - r.left) / r.width) * 10);
    const y = Math.floor(((e.clientY - r.top) / r.height) * 16);
    if (x < 0 || x > 9 || y < 0 || y > 15 || grid[y][x] === color) return;
    grid[y][x] = color;
    mode = "paint";
    drawGrid();
    refresh();
  };
  const stopPainting = () => { painting = false; };
  gridCanvas.addEventListener("pointerdown", (e) => { painting = true; paintAt(e); });
  gridCanvas.addEventListener("pointermove", (e) => { if (painting) paintAt(e); });
  window.addEventListener("pointerup", stopPainting);

  $("[data-ed=file]", root).addEventListener("change", (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (file.type !== "image/png") return toast("Bitte eine PNG-Datei wählen.", "warn");
    if (file.size > 2_000_000) return toast("Das Bild ist zu groß (max. 2 MB).", "warn");
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        image = reader.result;
        frames = Math.max(1, Math.round(img.height / (img.width / 2)));
        mode = "image";
        $("[data-ed=frames]", root).value = frames;
        refresh();
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
  $("[data-ed=frames]", root).value = frames;
  $("[data-ed=fps]", root).value = fps;
  $("[data-ed=frames]", root).addEventListener("change", (e) => { frames = Math.max(1, Math.min(64, Number(e.target.value) || 1)); refresh(); });
  $("[data-ed=fps]", root).addEventListener("input", (e) => { fps = Number(e.target.value); startTimer(); });

  function setColor(next) {
    color = next;
    $$(".swatch", root).forEach((s) => s.classList.toggle("active", s.dataset.color === color));
    $("[data-ed=color]", root).value = color;
  }

  function close() {
    clearInterval(timer);
    window.removeEventListener("pointerup", stopPainting);
    resize.disconnect();
    viewer.dispose();
    root.remove();
  }

  async function save() {
    const btn = $("[data-ed=save]", root);
    setBusy(btn, true, "Speichert …");
    try {
      const png = mode === "paint" ? paintCape((x, y) => grid[y][x]).toDataURL("image/png") : image;
      const saved = await api("save_cosmetic", {
        item: {
          id: item?.custom ? item.id : undefined, category: "cape", name: $(".editor-name", root).value,
          image: png, frames: mode === "paint" ? 1 : frames, fps,
        },
      });
      close();
      toast(`„${saved.name}“ gespeichert 🎉`);
      onSaved?.(saved);
    } catch (e) {
      toast(e.message, "error");
      setBusy(btn, false);
    }
  }

  root.addEventListener("click", (e) => {
    const sw = e.target.closest("[data-color]");
    if (sw) setColor(sw.dataset.color);
    const action = e.target.closest("[data-ed]")?.dataset.ed;
    if (action === "clear") { grid.forEach((row) => row.fill(color)); mode = "paint"; drawGrid(); refresh(); }
    if (action === "cancel") close();
    if (action === "save") save();
  });
  $("[data-ed=color]", root).addEventListener("input", (e) => setColor(e.target.value));
  $("[data-ed=clear]", root).textContent = "Alles füllen";
  drawGrid();
  refresh();
}
