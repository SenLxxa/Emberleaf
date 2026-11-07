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
