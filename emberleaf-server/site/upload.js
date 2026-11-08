// Upload-Seite: der Besitzer lädt eine neue Emberleaf-Version hoch (Launcher, Client-Mod, Shader).
// Ablauf: SHA-256 jeder Datei im Browser berechnen → Entwurf anlegen → Stücke hochladen → veröffentlichen.
import { $, api, esc, initAccount, isOwner, onSession, session, toast } from "./common.js";

const VERSION_RE = /^\d{1,4}\.\d{1,4}\.\d{1,4}$/;
const NAME_RE = /^[A-Za-z0-9._+-]{1,80}\.(exe|jar|zip)$/;
const KINDS = { launcher: "Launcher (.exe)", mod: "Client-Mod (.jar)", shaderpack: "Shader (.zip)", resourcepack: "Texturen (.zip)" };
const files = []; // { file, name, kind }
let draft = null; // { id, partBytes } während eines Uploads
let busy = false;
let liveVersion = null;

const form = $("#up-form");
const list = $("#file-list");
const mb = (b) => `${(b / 1024 / 1024).toFixed(1).replace(".", ",")} MB`;
const versionTuple = (v) => (VERSION_RE.test(v || "") ? v.split(".").map(Number) : null);
const newer = (a, b) => {
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] > b[i];
  return false;
};

function guessKind(name) {
  if (/\.exe$/i.test(name)) return "launcher";
  if (/\.jar$/i.test(name)) return "mod";
  if (/glut|shader/i.test(name)) return "shaderpack";
  return "resourcepack";
}

function addFiles(list) {
  for (const file of list) {
    const kind = guessKind(file.name);
    // der Launcher heißt beim Herunterladen immer Emberleaf.exe, egal wie die Datei bei dir heißt
    const name = kind === "launcher" ? "Emberleaf.exe" : file.name;
    const old = files.findIndex((f) => f.name === name);
    if (old >= 0) files.splice(old, 1);
    files.push({ file, name, kind });
    const v = file.name.match(/^emberleaf-client-(\d+\.\d+\.\d+)\.jar$/i);
    if (v && !$("#version").value) $("#version").value = v[1];
  }
  renderFiles();
}

function renderFiles() {
  list.innerHTML = files.map((f, i) => `
    <li>
      <b>${esc(f.name)}</b>
      <span class="muted">${mb(f.file.size)}</span>
      <select data-i="${i}" aria-label="Art der Datei">${Object.entries(KINDS).map(([k, label]) => `<option value="${k}"${k === f.kind ? " selected" : ""}>${label}</option>`).join("")}</select>
      <button class="btn small ghost" type="button" data-remove="${i}" aria-label="${esc(f.name)} entfernen">✕</button>
    </li>`).join("");
}

list.addEventListener("change", (e) => {
  const sel = e.target.closest("select[data-i]");
  if (sel) files[Number(sel.dataset.i)].kind = sel.value;
});
list.addEventListener("click", (e) => {
  const btn = e.target.closest("[data-remove]");
  if (btn && !busy) {
    files.splice(Number(btn.dataset.remove), 1);
    renderFiles();
  }
});
$("#files").addEventListener("change", (e) => {
  addFiles(e.target.files);
  e.target.value = "";
});
const drop = $("#drop");
for (const type of ["dragenter", "dragover"]) drop.addEventListener(type, (e) => { e.preventDefault(); drop.classList.add("over"); });
for (const type of ["dragleave", "drop"]) drop.addEventListener(type, () => drop.classList.remove("over"));
drop.addEventListener("drop", (e) => {
  e.preventDefault();
  if (!busy) addFiles(e.dataTransfer.files);
});

function problems(version) {
  const out = [];
  if (!files.length) out.push("Wähle mindestens eine Datei aus.");
  if (!VERSION_RE.test(version)) out.push("Die Version bitte als drei Zahlen mit Punkt, z. B. 0.0.8.");
  else if (liveVersion && !newer(versionTuple(version), versionTuple(liveVersion))) out.push(`Die Version muss höher sein als ${liveVersion}.`);
  if (files.filter((f) => f.kind === "launcher").length > 1) out.push("Nur eine Launcher-Datei pro Update.");
  for (const f of files) {
    if (!NAME_RE.test(f.name)) out.push(`„${f.name}“: nur Buchstaben, Zahlen, Punkt, Minus und Unterstrich im Namen.`);
    else if (f.kind === "launcher" && !f.name.endsWith(".exe")) out.push(`„${f.name}“ ist keine .exe.`);
    else if (f.kind === "mod" && !f.name.endsWith(".jar")) out.push(`„${f.name}“ ist keine .jar.`);
    else if ((f.kind === "shaderpack" || f.kind === "resourcepack") && !f.name.endsWith(".zip")) out.push(`„${f.name}“ ist keine .zip.`);
  }
  return out;
}

async function sha256(file) {
  const digest = await crypto.subtle.digest("SHA-256", await file.arrayBuffer());
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function progress(done, total, text) {
  $("#progress").hidden = false;
  $("#bar").style.width = `${total ? Math.min(100, (done / total) * 100) : 0}%`;
  $("#progress-text").textContent = text;
}

async function putPart(id, f, part, blob) {
  for (let attempt = 1; ; attempt++) {
    try {
      await api(`/v1/update/part?release=${encodeURIComponent(id)}&file=${encodeURIComponent(f.name)}&part=${part}`, { method: "PUT", raw: blob });
      return;
    } catch (e) {
      if (attempt >= 4) throw e;
      await new Promise((r) => setTimeout(r, 1500 * attempt));
    }
  }
}

function setBusy(on) {
  busy = on;
  $("#publish").setAttribute("aria-disabled", String(on));
  for (const el of form.querySelectorAll("input, textarea, select")) el.disabled = on;
}

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  if (busy) return;
  const version = $("#version").value.trim();
  const errors = problems(version);
  $("#up-error").innerHTML = errors.map(esc).join("<br>");
  if (errors.length) return;
  $("#done").hidden = true;
  setBusy(true);
  try {
    const total = files.reduce((n, f) => n + f.file.size, 0);
    const meta = [];
    for (const [i, f] of files.entries()) {
      progress(i, files.length, `Prüft ${f.name} …`);
      meta.push({ name: f.name, kind: f.kind, size: f.file.size, sha256: await sha256(f.file) });
    }
    if (!draft) draft = await api("/v1/update/begin", { method: "POST", body: { version, notes: $("#notes").value, files: meta } });
    $("#discard").hidden = false;
    let sent = 0;
    for (const f of files) {
      const parts = Math.ceil(f.file.size / draft.partBytes);
      for (let part = 0; part < parts; part++) {
        const blob = f.file.slice(part * draft.partBytes, Math.min(f.file.size, (part + 1) * draft.partBytes));
        progress(sent, total, `Lädt ${f.name} hoch … ${Math.round((sent / total) * 100)} %`);
        await putPart(draft.id, f, part, blob);
        sent += blob.size;
      }
    }
    progress(total, total, "Veröffentlicht …");
    await api("/v1/update/publish", { method: "POST", body: { id: draft.id } });
    draft = null;
    files.length = 0;
    renderFiles();
    $("#notes").value = "";
    $("#version").value = "";
    $("#discard").hidden = true;
    $("#progress").hidden = true;
    const done = $("#done");
    done.innerHTML = `<b>Emberleaf ${esc(version)} ist online!</b> Alle Launcher holen sich das Update in den nächsten 30 Minuten. <a href="/#download">Zur Download-Seite</a>`;
    done.hidden = false;
    toast(`Emberleaf ${version} ist veröffentlicht.`);
    await loadHistory();
  } catch (err) {
    $("#up-error").textContent = `${err.message} Klick nochmal auf „Hochladen und veröffentlichen“, dann geht es weiter.`;
  } finally {
    setBusy(false);
  }
});

$("#discard").addEventListener("click", async () => {
  if (busy || !draft) return;
  try {
    await api("/v1/update/discard", { method: "POST", body: { id: draft.id } });
  } catch (err) {
    toast(err.message, "error");
  }
  draft = null;
  $("#discard").hidden = true;
  $("#progress").hidden = true;
  $("#up-error").textContent = "";
  toast("Der Entwurf wurde verworfen.");
});

// die Dateien (oder der Text) haben sich nach einem abgebrochenen Upload geändert: neuer Entwurf
form.addEventListener("input", () => {
  if (!busy && draft) {
    api("/v1/update/discard", { method: "POST", body: { id: draft.id } }).catch(() => {});
    draft = null;
    $("#discard").hidden = true;
  }
});
addEventListener("beforeunload", (e) => {
  if (busy) e.preventDefault();
});

async function loadHistory() {
  const box = $("#history");
  try {
    const [{ releases }, latest] = await Promise.all([api("/v1/releases"), api("/v1/update")]);
    liveVersion = latest.release?.version || null;
    $("#live-version").textContent = liveVersion ? `Gerade online: ${liveVersion}` : "Noch keine Version online.";
    box.innerHTML = releases.length ? releases.map((r) => `
      <article class="release">
        <header><h3>${esc(r.version)}</h3><time>${new Date(r.published * 1000).toLocaleString("de-DE")}</time>
        ${r.available ? '<span class="tag">zum Download</span>' : '<span class="muted">Dateien aufgeräumt</span>'}</header>
        <div class="files">${r.files.map((f) => `<span>${esc(f.name)} · ${mb(f.size)}</span>`).join("")}</div>
      </article>`).join("") : '<p class="muted">Noch nichts hochgeladen.</p>';
  } catch (err) {
    box.innerHTML = `<p class="muted">${esc(err.message)}</p>`;
  }
}

function renderGate() {
  const gate = $("#gate");
  if (!session()) {
    form.hidden = true;
    gate.hidden = false;
    gate.innerHTML = `<div class="up-card"><p>Melde dich mit deinem Emberleaf an, um Updates hochzuladen.</p><button class="btn primary" type="button" data-login>Anmelden</button></div>`;
  } else if (!isOwner()) {
    form.hidden = true;
    gate.hidden = false;
    gate.innerHTML = `<div class="up-card"><p>Du bist als <b>${esc(session().name)}</b> angemeldet. Updates hochladen kann nur der Besitzer von Emberleaf.</p></div>`;
  } else {
    gate.hidden = true;
    form.hidden = false;
  }
}

onSession(renderGate);
initAccount().then(renderGate);
loadHistory();
