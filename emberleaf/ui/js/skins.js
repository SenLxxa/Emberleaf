// Skins-Seite: gespeicherte Skins ansehen, neue PNGs hinzufügen und mit einem Klick anziehen.
// Die Bibliothek liegt im Emberleaf-Ordner (skins/library.json), der Client-Mod liest sie fürs Wechseln im Spiel.
import { SkinViewer, WalkingAnimation } from "../vendor/esm/skinview3d.js";
import { $, api, confirmDialog, esc, modal, setBusy, toast } from "./ui.js";

const PARTS = {
  // [x, y, w, h] on the skin, drawn at [dx, dy] in a 16x32 front view
  head: [8, 8, 8, 8, 4, 0], hat: [40, 8, 8, 8, 4, 0],
  body: [20, 20, 8, 12, 4, 8], jacket: [20, 36, 8, 12, 4, 8],
  rleg: [4, 20, 4, 12, 4, 20], rpants: [4, 36, 4, 12, 4, 20],
  lleg: [20, 52, 4, 12, 8, 20], lpants: [4, 52, 4, 12, 8, 20],
};

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error("Bild kaputt"));
    img.src = src;
  });
}

// Slim skins leave the 4th arm column empty (x=54..55, y=20..31 is transparent).
function guessSlim(img) {
  if (img.height !== 64) return false;
  const c = document.createElement("canvas");
  c.width = 64; c.height = 64;
  const ctx = c.getContext("2d", { willReadFrequently: true });
  ctx.drawImage(img, 0, 0);
  const px = ctx.getImageData(54, 20, 2, 12).data;
  for (let i = 3; i < px.length; i += 4) if (px[i] > 0) return false;
  return true;
}

// Flat front view (like a skin card in the official launcher), 16x32 pixels scaled up.
async function drawFront(canvas, dataUrl, slim) {
  const img = await loadImage(dataUrl);
  const ctx = canvas.getContext("2d");
  const s = canvas.width / 16;
  ctx.imageSmoothingEnabled = false;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const legacy = img.height === 32;
  const aw = slim ? 3 : 4;
  const part = (x, y, w, h, dx, dy, flip = false) => {
    ctx.save();
    if (flip) { ctx.translate((dx + w) * s, dy * s); ctx.scale(-1, 1); ctx.drawImage(img, x, y, w, h, 0, 0, w * s, h * s); }
    else ctx.drawImage(img, x, y, w, h, dx * s, dy * s, w * s, h * s);
    ctx.restore();
  };
  const p = (k, flip) => { const [x, y, w, h, dx, dy] = PARTS[k]; part(x, y, w, h, dx, dy, flip); };
  p("head"); p("body"); p("rleg");
  if (legacy) part(4, 20, 4, 12, 8, 20, true); else p("lleg");
  // arms: right arm (viewer's left) at x 0..4, left arm at 12..16
  part(44, 20, aw, 12, 4 - aw, 8);
  if (legacy) part(44, 20, aw, 12, 12, 8, true); else part(36, 52, aw, 12, 12, 8);
  if (!legacy) {
    p("jacket"); p("rpants"); p("lpants");
    part(44, 36, aw, 12, 4 - aw, 8);
    part(52, 52, aw, 12, 12, 8);
  }
  p("hat");
}

const readFile = (file) => new Promise((resolve, reject) => {
  const r = new FileReader();
  r.onload = () => resolve(r.result);
  r.onerror = () => reject(new Error("Datei konnte nicht gelesen werden."));
  r.readAsDataURL(file);
});

export function createSkins({ state, login, onApplied }) {
  const root = $("#skins-body");
  let library = [];
  let selected = null;
  let viewer = null;
  let loaded = false;

  function ensureViewer() {
    if (viewer) return viewer;
    const canvas = $("#skins-canvas");
    viewer = new SkinViewer({ canvas, width: 300, height: 420, fov: 40, zoom: 0.82 });
    viewer.renderer.setClearAlpha?.(0);
    viewer.background = null;
    viewer.animation = new WalkingAnimation();
    viewer.animation.speed = 0.6;
    viewer.autoRotate = true;
    viewer.autoRotateSpeed = 0.6;
    viewer.controls.enableZoom = false;
    return viewer;
  }

  function resize() {
    if (!viewer) return;
    const stage = $("#skins-stage");
    viewer.setSize(stage.clientWidth, stage.clientHeight);
  }

  async function show(entry) {
    selected = entry;
    ensureViewer();
    resize();
    if (entry?.skin) await viewer.loadSkin(entry.skin, { model: entry.model === "slim" ? "slim" : "default" });
    $("#skins-name").textContent = entry ? entry.name : "";
    $("#skins-meta").textContent = entry ? (entry.model === "slim" ? "Schmale Arme (Alex)" : "Breite Arme (Steve)") : "";
    const apply = $("#skins-apply");
    apply.hidden = !entry || entry.current;
    root.querySelectorAll(".skin-card").forEach((c) => c.classList.toggle("selected", c.dataset.id === entry?.id));
  }

  function render() {
    const current = state.skin?.skin && !state.skin.fallback ? { id: "current", name: `${state.skin.name} (aktuell)`, model: state.skin.model, skin: state.skin.skin, current: true } : null;
    const all = current ? [current, ...library] : library;
    const grid = $("#skins-grid");
    if (!all.length) {
      grid.innerHTML = `<div class="notice">Noch keine Skins gespeichert. Füg ein PNG hinzu oder speichere deinen aktuellen Skin.</div>`;
      return;
    }
    grid.innerHTML = all.map((e) => `
      <button class="skin-card ${e.current ? "current" : ""}" data-id="${esc(e.id)}">
        <canvas width="96" height="192"></canvas>
        <span class="skin-card-name">${esc(e.name)}</span>
        ${e.current ? `<span class="skin-card-tag">Trägst du</span>` : `<span class="skin-card-del" data-del="${esc(e.id)}" title="Löschen">✕</span>`}
      </button>`).join("");
    grid.querySelectorAll(".skin-card").forEach((card, i) => {
      drawFront(card.querySelector("canvas"), all[i].skin, all[i].model === "slim").catch(() => {});
      card.addEventListener("click", async (ev) => {
        const del = ev.target.closest("[data-del]");
        if (del) {
          ev.stopPropagation();
          const entry = all[i];
          if (!(await confirmDialog("Skin löschen?", `„${entry.name}“ wird aus deiner Sammlung entfernt.`, "Löschen"))) return;
          try {
            library = await api("skin_remove", { skin_id: entry.id });
            if (selected?.id === entry.id) selected = null;
            render();
            show(selected || all[0]);
          } catch (err) { toast(err.message, "error"); }
          return;
        }
        show(all[i]);
      });
    });
    show(all.find((e) => e.id === selected?.id) || all[0]);
  }

  async function refresh() {
    try {
      library = await api("skin_library");
      loaded = true;
      render();
    } catch (e) {
      toast(e.message, "error");
    }
  }

  function addDialog(file) {
    readFile(file).then(async (dataUrl) => {
      const img = await loadImage(dataUrl).catch(() => null);
      if (!img || img.width !== 64 || (img.height !== 64 && img.height !== 32)) {
        return toast("Ein Skin ist ein PNG mit 64×64 (oder alt 64×32) Pixeln.", "error");
      }
      const slim = guessSlim(img);
      const m = modal(`
        <h3>Skin hinzufügen</h3>
        <div class="skin-add">
          <canvas width="96" height="192" id="skin-add-preview"></canvas>
          <div>
            <div class="field"><label for="skin-add-name">Name</label>
              <input class="input" id="skin-add-name" maxlength="30" value="${esc(file.name.replace(/\.png$/i, "").slice(0, 30))}"></div>
            <div class="field"><label>Arme</label>
              <div class="seg" id="skin-add-model">
                <button data-model="default" class="${slim ? "" : "active"}">Breit (Steve)</button>
                <button data-model="slim" class="${slim ? "active" : ""}">Schmal (Alex)</button>
              </div></div>
          </div>
        </div>
        <div class="modal-actions"><button class="btn" data-close>Abbrechen</button><button class="btn primary" id="skin-add-ok">Speichern</button></div>`);
      let model = slim ? "slim" : "default";
      const preview = $("#skin-add-preview", m.el);
      drawFront(preview, dataUrl, slim);
      m.el.querySelectorAll("[data-model]").forEach((b) => b.addEventListener("click", () => {
        model = b.dataset.model;
        m.el.querySelectorAll("[data-model]").forEach((x) => x.classList.toggle("active", x === b));
        drawFront(preview, dataUrl, model === "slim");
      }));
      $("#skin-add-ok", m.el).addEventListener("click", async (ev) => {
        setBusy(ev.currentTarget, true, "Speichert …");
        try {
          library = await api("skin_add", { name: $("#skin-add-name", m.el).value, model, data: dataUrl });
          selected = library[0];
          m.close();
          render();
          toast("Skin gespeichert 👍");
        } catch (err) {
          setBusy(ev.currentTarget, false);
          toast(err.message, "error");
        }
      });
    }).catch((e) => toast(e.message, "error"));
  }

  $("#skins-add").addEventListener("click", () => $("#skins-file").click());
  $("#skins-file").addEventListener("change", (e) => {
    const file = e.target.files[0];
    e.target.value = "";
    if (file) addDialog(file);
  });
  $("#skins-save-current").addEventListener("click", async (e) => {
    setBusy(e.currentTarget, true, "Speichert …");
    try {
      library = await api("skin_save_current");
      selected = library[0];
      render();
      toast("Dein aktueller Skin ist jetzt in der Sammlung 👍");
    } catch (err) { toast(err.message, "error"); }
    setBusy(e.currentTarget, false);
  });
  $("#skins-apply").addEventListener("click", async (e) => {
    if (!selected || selected.current) return;
    if (!state.account) {
      const ok = await login();
      if (!ok) return;
    }
    const button = e.currentTarget;
    setBusy(button, true, "Lädt hoch …");
    try {
      await api("skin_apply", { skin_id: selected.id });
      toast(`„${selected.name}“ ist jetzt dein Skin 🎉 Auf Servern siehst du ihn nach dem Neubetreten.`);
      await onApplied?.();
      render();
    } catch (err) { toast(err.message, "error"); }
    setBusy(button, false);
  });
  window.addEventListener("resize", () => { if (state.page === "skins") resize(); });

  return {
    open() {
      ensureViewer();
      viewer.renderPaused = false;
      requestAnimationFrame(resize);
      if (!loaded) refresh(); else render();
    },
    close() {
      if (viewer) viewer.renderPaused = true;
    },
    skinChanged() {
      if (state.page === "skins") render();
    },
  };
}
