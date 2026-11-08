// Kleine gemeinsame Helfer: API-Aufrufe, Dialoge, Meldungen.
const TOKEN = new URLSearchParams(location.search).get("t") || "";

export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

export async function api(name, body = {}) {
  let res;
  try {
    res = await fetch(`/api/${name}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Emberleaf-Token": TOKEN },
      body: JSON.stringify(body),
    });
  } catch {
    throw new Error("Emberleaf antwortet nicht. Starte das Programm bitte neu.");
  }
  const json = await res.json().catch(() => ({ ok: false, error: "Ungültige Antwort." }));
  if (!json.ok) throw new Error(json.error || "Unbekannter Fehler.");
  return json.data;
}

export function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

export function toast(message, kind = "") {
  const el = document.createElement("div");
  el.className = `toast ${kind}`;
  el.textContent = message;
  $("#toasts").append(el);
  setTimeout(() => {
    el.classList.add("out");
    setTimeout(() => el.remove(), 300);
  }, kind === "error" ? 7000 : 4000);
}

export function setBusy(button, busy, label = "") {
  if (busy) {
    button.dataset.label = button.innerHTML;
    button.disabled = true;
    button.innerHTML = `<span class="spinner"></span>${esc(label)}`;
  } else if (button.dataset.label !== undefined) {
    button.disabled = false;
    button.innerHTML = button.dataset.label;
  }
}

export function modal(html, onClose, wide = false) {
  const backdrop = document.createElement("div");
  backdrop.className = "modal-backdrop";
  backdrop.innerHTML = `<div class="modal ${wide ? "wide" : ""}" role="dialog" aria-modal="true">${html}</div>`;
  $("#modal-root").append(backdrop);
  const onKey = (e) => { if (e.key === "Escape") close(); };
  function close() {
    if (!backdrop.isConnected) return;
    backdrop.remove();
    document.removeEventListener("keydown", onKey);
    onClose?.();
  }
  backdrop.addEventListener("click", (e) => {
    if (e.target === backdrop || e.target.closest("[data-close]")) close();
  });
  document.addEventListener("keydown", onKey);
  return { el: backdrop.firstElementChild, close };
}

export function confirmDialog(title, text, okLabel) {
  return new Promise((resolve) => {
    let ok = false;
    const m = modal(
      `<h3>${esc(title)}</h3><p>${esc(text)}</p>
       <div class="modal-actions"><button class="btn" data-close>Abbrechen</button><button class="btn danger" data-ok>${esc(okLabel)}</button></div>`,
      () => resolve(ok),
    );
    $("[data-ok]", m.el).addEventListener("click", () => { ok = true; m.close(); });
  });
}

// Alte Skins haben oft eine komplett deckende Hut-Ebene; Minecraft blendet sie dann aus (wie hier).
function hatIsTransparent(img) {
  const probe = document.createElement("canvas");
  probe.width = 32;
  probe.height = 16;
  const ctx = probe.getContext("2d", { willReadFrequently: true });
  ctx.drawImage(img, 32, 0, 32, 16, 0, 0, 32, 16);
  const px = ctx.getImageData(0, 0, 32, 16).data;
  for (let i = 3; i < px.length; i += 4) if (px[i] < 128) return true;
  return false;
}

let defaultSkinUrl = null;

/**
 * Emberleafs eigener Standard-Skin (ein Camper im Holzfällerhemd) für alle ohne eigenen Skin: Gäste, Konten mit dem
 * Mojang-Standard-Skin und wenn Mojang gerade nicht erreichbar ist. Ohne ihn blieben die Figur und alle Cosmetics-Karten leer.
 */
export function defaultSkin() {
  if (defaultSkinUrl) return defaultSkinUrl;
  const canvas = document.createElement("canvas");
  canvas.width = 64;
  canvas.height = 64;
  const c = canvas.getContext("2d");
  const fill = (x, y, w, h, color) => { c.fillStyle = color; c.fillRect(x, y, w, h); };
  // a box at texture corner (u, v), w x h x d: top and bottom in one colour, side(x, y) colours the four sides row by row
  const box = (u, v, w, h, d, side, top, bottom) => {
    fill(u + d, v, w, d, top);
    fill(u + d + w, v, w, d, bottom);
    for (let y = 0; y < h; y++) for (let x = 0; x < 2 * (d + w); x++) fill(u + x, v + d + y, 1, 1, side(x, y));
  };
  const SKIN = "#c98e62";
  const SKIN_DARK = "#b07750";
  const HAIR = "#3b2a1e";
  const plaid = (x, y) => (x % 4 === 0 && y % 4 === 0 ? "#7a2f12" : x % 4 === 0 || y % 4 === 0 ? "#a8481c" : "#d9662b");
  // head: hair on top, at the back and over the forehead
  box(0, 0, 8, 8, 8, (x, y) => ((x >= 24 ? y < 7 : x >= 8 && x < 16 ? y < 2 : y < 3) ? HAIR : SKIN), HAIR, SKIN_DARK);
  for (const [x, y, color] of [[9, 12, "#f4efe6"], [10, 12, "#2f3d5c"], [13, 12, "#2f3d5c"], [14, 12, "#f4efe6"],
    [11, 13, SKIN_DARK], [12, 13, SKIN_DARK], [10, 14, "#8a5a3c"], [11, 14, "#8a5a3c"], [12, 14, "#8a5a3c"], [13, 14, "#8a5a3c"]]) {
    fill(x, y, 1, 1, color);
  }
  // body: plaid shirt with a belt; arms: sleeves and hands; legs: trousers and boots
  box(16, 16, 8, 12, 4, (x, y) => (y === 11 ? "#4a3020" : plaid(x, y)), "#d9662b", "#4a3020");
  for (const [u, v] of [[40, 16], [32, 48]]) box(u, v, 4, 12, 4, (x, y) => (y >= 8 ? SKIN : plaid(x, y)), "#d9662b", SKIN);
  for (const [u, v] of [[0, 16], [16, 48]]) {
    box(u, v, 4, 12, 4, (x, y) => (y >= 9 ? "#3a2a1e" : y === 0 ? "#3d4a66" : "#4a5a7a"), "#4a5a7a", "#2a1e16");
  }
  defaultSkinUrl = canvas.toDataURL("image/png");
  return defaultSkinUrl;
}

export function drawHead(canvas, dataUrl) {
  const img = new Image();
  img.onload = () => {
    const c = canvas.getContext("2d");
    c.imageSmoothingEnabled = false;
    c.clearRect(0, 0, canvas.width, canvas.height);
    c.drawImage(img, 8, 8, 8, 8, 0, 0, canvas.width, canvas.height);
    if (img.height === 64 || hatIsTransparent(img)) c.drawImage(img, 40, 8, 8, 8, 0, 0, canvas.width, canvas.height);
  };
  img.src = dataUrl;
}
