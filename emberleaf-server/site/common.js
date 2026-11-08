// Gemeinsam für Startseite und Upload-Seite: Anmeldung über Emberleaf, Server-Aufrufe, Kopfzeile.
//
// Anmelden geht mit einem Einmal-Code aus dem Launcher (wie das Handy-Koppeln). Der Launcher-Knopf "Webseite"
// öffnet die Seite mit #login=CODE, dann klappt es ohne Tippen.

const SESSION_KEY = "emberleaf.site.session";
const listeners = new Set();
let current = loadSession();
let ownerFlag = false;

export const $ = (sel, root = document) => root.querySelector(sel);
export const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

function loadSession() {
  try {
    const s = JSON.parse(localStorage.getItem(SESSION_KEY) || "null");
    return s && s.token && s.expires * 1000 > Date.now() ? s : null;
  } catch {
    return null;
  }
}

function saveSession(s) {
  current = s;
  try {
    if (s) localStorage.setItem(SESSION_KEY, JSON.stringify(s));
    else localStorage.removeItem(SESSION_KEY);
  } catch {
    // privates Fenster: die Anmeldung gilt dann nur bis zum Neuladen
  }
  for (const fn of listeners) fn(s);
}

export const session = () => current;
export const isOwner = () => ownerFlag;
export function onSession(fn) {
  listeners.add(fn);
  fn(current);
}

export async function api(path, { method = "GET", body, raw, signal } = {}) {
  const headers = {};
  if (current) headers.authorization = `Bearer ${current.token}`;
  let payload;
  if (raw !== undefined) payload = raw;
  else if (body !== undefined) {
    headers["content-type"] = "application/json";
    payload = JSON.stringify(body);
  }
  let res;
  try {
    res = await fetch(path, { method, headers, body: payload, signal });
  } catch (e) {
    if (e.name === "AbortError") throw e;
    throw new Error("Keine Verbindung zum Server. Bist du online?");
  }
  let data = null;
  try {
    data = await res.json();
  } catch {
    // keine JSON-Antwort
  }
  if (res.status === 401 && current) {
    saveSession(null);
    throw new Error("Deine Anmeldung ist abgelaufen. Melde dich neu an.");
  }
  if (!res.ok) throw new Error(data?.error || `Fehler ${res.status}`);
  return data;
}

export function toast(text, kind = "") {
  const el = document.createElement("div");
  el.className = `toast ${kind}`;
  el.setAttribute("role", "status");
  el.textContent = text;
  document.body.append(el);
  setTimeout(() => el.remove(), 4200);
}

// ---------- Anmelden ----------

async function claim(code) {
  const clean = String(code || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (clean.length !== 6) throw new Error("Der Code hat 6 Zeichen.");
  const data = await api("/v1/pair/claim", { method: "POST", body: { code: clean } });
  saveSession({ token: data.token, uuid: data.uuid, name: data.name, expires: data.expires });
  await refreshOwner();
  return data;
}

async function refreshOwner() {
  if (!current) {
    ownerFlag = false;
    return false;
  }
  try {
    ownerFlag = Boolean((await api("/v1/update")).owner);
  } catch {
    ownerFlag = false;
  }
  for (const fn of listeners) fn(current);
  return ownerFlag;
}

export async function logout() {
  try {
    if (current) await api("/v1/logout", { method: "POST", body: {} });
  } catch {
    // abgelaufen oder offline: lokal trotzdem abmelden
  }
  ownerFlag = false;
  saveSession(null);
  toast("Du bist abgemeldet.");
}

export function openLogin() {
  const dialog = $("#login-dialog");
  if (!dialog) return;
  $("#login-error").textContent = "";
  $("#login-code").value = "";
  dialog.showModal();
  $("#login-code").focus();
}

function setupLoginDialog() {
  const dialog = $("#login-dialog");
  if (!dialog) return;
  const form = $("#login-form");
  form.addEventListener("submit", async (e) => {
    if (e.submitter?.value === "cancel") return; // Abbrechen schließt einfach
    e.preventDefault();
    const btn = $("#login-submit");
    btn.setAttribute("aria-disabled", "true");
    $("#login-error").textContent = "";
    try {
      const data = await claim($("#login-code").value);
      dialog.close();
      toast(`Hallo ${data.name}!`);
    } catch (err) {
      $("#login-error").textContent = err.message;
    } finally {
      btn.removeAttribute("aria-disabled");
    }
  });
  $("#login-code").addEventListener("input", (e) => {
    const v = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 6);
    e.target.value = v.length > 3 ? `${v.slice(0, 3)} ${v.slice(3)}` : v;
  });
  document.addEventListener("click", (e) => {
    if (e.target.closest("[data-login]")) openLogin();
    const pop = $("#user-pop");
    if (pop && !pop.hidden && !e.target.closest(".user-menu")) {
      pop.hidden = true;
      $("#user-btn")?.setAttribute("aria-expanded", "false");
    }
  });
}

// Kopf aus dem Minecraft-Skin (Gesicht + Hut-Ebene), 8x8 Pixel groß gezeichnet
async function headImage(uuid) {
  const img = new Image();
  img.src = `/v1/skin/${uuid}`;
  await img.decode();
  const c = document.createElement("canvas");
  c.width = c.height = 8;
  const g = c.getContext("2d");
  g.imageSmoothingEnabled = false;
  g.drawImage(img, 8, 8, 8, 8, 0, 0, 8, 8);
  g.drawImage(img, 40, 8, 8, 8, 0, 0, 8, 8);
  return c.toDataURL();
}

function renderAccount() {
  const box = $("#account");
  if (!box) return;
  if (!current) {
    box.innerHTML = `<button class="btn small" type="button" data-login>Anmelden</button>`;
    return;
  }
  box.innerHTML = `
    <div class="user-menu">
      <button type="button" aria-haspopup="true" aria-expanded="false" id="user-btn">
        <img class="user-head" alt="" width="32" height="32" id="user-head">
        <span class="user-name">${esc(current.name)}</span>
        ${ownerFlag ? '<span class="badge-owner">Besitzer</span>' : ""}
      </button>
      <div class="menu-pop" id="user-pop" hidden>
        ${ownerFlag ? '<a href="/upload">Update hochladen</a>' : ""}
        <a href="/#packs">Meine Packs (bald)</a>
        <button type="button" id="logout-btn">Abmelden</button>
      </div>
    </div>`;
  const head = $("#user-head");
  headImage(current.uuid).then((src) => { head.src = src; }).catch(() => { head.src = "/s/img/leaf.svg"; });
  const btn = $("#user-btn");
  const pop = $("#user-pop");
  btn.addEventListener("click", () => {
    pop.hidden = !pop.hidden;
    btn.setAttribute("aria-expanded", String(!pop.hidden));
  });
  $("#logout-btn").addEventListener("click", logout);
}

// vom Launcher geöffnet: #login=ABC123 (auch wenn die Seite schon offen war)
async function loginFromHash() {
  const m = location.hash.match(/^#login=([A-Za-z0-9]{6})$/);
  if (!m) return false;
  history.replaceState(null, "", location.pathname + location.search);
  try {
    const data = await claim(m[1]);
    toast(`Hallo ${data.name}! Du bist über Emberleaf angemeldet.`);
  } catch (err) {
    toast(err.message, "error");
  }
  return true;
}

export async function initAccount() {
  setupLoginDialog();
  listeners.add(renderAccount);
  renderAccount();
  addEventListener("hashchange", loginFromHash);
  if (!(await loginFromHash()) && current) await refreshOwner();
}
