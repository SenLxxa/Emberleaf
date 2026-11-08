// Emberleaf-Updates: neue Versionen kommen von der Emberleaf-Webseite (der Besitzer lädt sie dort hoch).
// Der Launcher lädt sie im Hintergrund und meldet sich dann mit "Jetzt neu starten / Später".
import { $, api, esc, modal, setBusy, toast } from "./ui.js";

const KIND_NAMES = { mod: "Client-Mod", resourcepack: "Texturenpaket", shaderpack: "Shader" };
const SOURCE_NAMES = { bundled: "mitgeliefert", download: "Update", generated: "auf deinem PC gebaut" };

function when(seconds) {
  if (!seconds) return "noch nie";
  const diff = Date.now() / 1000 - seconds;
  if (diff < 90) return "gerade eben";
  if (diff < 3600) return `vor ${Math.round(diff / 60)} Min.`;
  if (diff < 86400) return `vor ${Math.round(diff / 3600)} Std.`;
  return new Date(seconds * 1000).toLocaleDateString("de-DE");
}

// Release-Text: Zeilen mit "-" oder "*" werden Punkte, der Rest Absätze. Alles escaped.
export function notesHtml(text) {
  const lines = String(text || "").split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  if (!lines.length) return "";
  const items = [];
  const paras = [];
  for (const line of lines) {
    const bullet = line.match(/^[-*•]\s+(.*)$/);
    const clean = (s) => esc(s.replace(/^#+\s*/, "")).replace(/\*\*(.+?)\*\*/g, "<b>$1</b>");
    if (bullet) items.push(`<li>${clean(bullet[1])}</li>`);
    else paras.push(`<p>${clean(line)}</p>`);
  }
  return paras.join("") + (items.length ? `<ul class="news-list">${items.join("")}</ul>` : "");
}

export function createUpdates({ state }) {
  const root = $("#updates-card");
  let info = null;
  let polling = null;
  let promptedFor = null; // only ask once per version and launcher run
  let newsShown = false;

  const busyNow = () => ["checking", "downloading"].includes(info?.status?.state);

  function render() {
    if (!info) return;
    const rows = Object.entries(info.local).flatMap(([kind, files]) => files.map((f) => `
      <div class="upd-row"><span>${esc(KIND_NAMES[kind] || kind)}</span><b>${esc(f.version)}</b>
      <small>${esc(SOURCE_NAMES[f.source] || f.source)}</small></div>`)).join("");
    const st = info.status || {};
    const busy = busyNow();
    let headline;
    if (info.pending) headline = `<b>Emberleaf ${esc(info.pending.version)} ist fertig geladen.</b> Starte neu, um es zu installieren.`;
    else if (info.newer && !info.self_update) headline = `Auf der Webseite gibt es Emberleaf ${esc(info.latest)}. Du startest Emberleaf aus dem Quellcode, dort aktualisierst du mit <code>tools/install.ps1</code>.`;
    else if (info.newer) headline = `Emberleaf ${esc(info.latest)} wird gerade geladen …`;
    else if (info.latest) headline = `Du hast die neueste Version (${esc(info.app_version)}).`;
    else headline = "Neue Versionen kommen automatisch von der Emberleaf-Webseite, bei dir und bei deinen Freunden.";
    root.innerHTML = `
      <h3>Emberleaf-Updates</h3>
      <p class="hint">${headline}</p>
      <div class="upd-list">
        <div class="upd-row"><span>Launcher</span><b>${esc(info.app_version)}</b><small>${info.latest ? `neueste: ${esc(info.latest)}` : "installiert"}</small></div>
        ${rows}
      </div>
      <p class="upd-meta">${busy ? `<span class="spinner"></span>${esc(st.message || "")} ${Math.round((st.progress || 0) * 100)} %`
        : st.state === "error" ? `<span class="upd-error">${esc(st.message)}</span>` : `Zuletzt geprüft: ${when(info.checked)}`}</p>
      <div class="row-actions">
        ${info.pending ? `<button class="btn primary" id="upd-install">Jetzt neu starten</button>` : ""}
        <button class="btn" id="upd-check" ${busy ? "disabled" : ""}>Jetzt prüfen</button>
        <button class="btn" id="upd-site">Auf der Webseite ansehen</button>
        ${info.owner ? `<button class="btn" id="upd-upload">Update hochladen</button>` : ""}
      </div>`;
  }

  async function install(button) {
    if (button) setBusy(button, true, "Startet neu …");
    try {
      await api("update_install");
      toast("Emberleaf startet neu …");
    } catch (err) {
      toast(err.message, "warn");
      if (button) setBusy(button, false);
    }
  }

  // "Update ist da": once per version, as soon as the download is complete
  function promptInstall() {
    if (!info?.pending || promptedFor === info.pending.version) return;
    promptedFor = info.pending.version;
    let restarting = false;
    const m = modal(`
      <h3>Update ${esc(info.pending.version)} ist da 🎉</h3>
      <p>Eine neue Emberleaf-Version ist fertig geladen.</p>
      ${notesHtml(info.pending.notes) ? `<h4>Was ist neu?</h4>${notesHtml(info.pending.notes)}` : ""}
      <div class="modal-actions">
        <button class="btn" data-close>Später</button>
        <button class="btn primary" id="upd-now">Jetzt neu starten</button>
      </div>`, () => {
      if (!restarting) toast("Okay! Das Update wird beim nächsten Start von Emberleaf installiert.");
    });
    const btn = $("#upd-now", m.el);
    btn.addEventListener("click", async () => {
      setBusy(btn, true, "Startet neu …");
      try {
        await api("update_install");
        restarting = true;
        m.close();
        toast("Emberleaf startet neu …");
      } catch (err) {
        toast(err.message, "warn");
        restarting = true; // the message already said what to do
        m.close();
      }
    });
  }

  // after an update: say once what is new
  function announce() {
    const news = info?.news;
    if (!news || newsShown) return;
    newsShown = true;
    const files = (news.files || []).join(", ");
    const title = news.kind === "launcher" ? `Emberleaf ist jetzt auf ${esc(news.version)} 🎉` : "Neue Emberleaf-Dateien geladen 🎉";
    const text = news.kind === "launcher"
      ? "Das Update wurde installiert."
      : `${esc(files)} ${news.files?.length > 1 ? "sind" : "ist"} da und gilt ab dem nächsten Spielstart.`;
    modal(`
      <h3>${title}</h3>
      <p>${text}</p>
      ${notesHtml(news.notes) ? `<h4>Was ist neu?</h4>${notesHtml(news.notes)}` : ""}
      <div class="modal-actions"><button class="btn primary" data-close>Cool!</button></div>`,
    () => api("update_seen").catch(() => {}));
  }

  async function refresh() {
    try {
      info = await api("update_status");
      render();
      announce();
      promptInstall();
      const busy = busyNow();
      if (busy && !polling) polling = setInterval(refresh, 800);
      if (!busy && polling) { clearInterval(polling); polling = null; }
    } catch {
      // the settings page still works without it
    }
  }

  root.addEventListener("click", async (e) => {
    const check = e.target.closest("#upd-check");
    if (check) {
      setBusy(check, true, "Prüft …");
      polling ||= setInterval(refresh, 800);
      try {
        const res = await api("update_check");
        info = res;
        if (res.pending) toast(`Emberleaf ${res.pending.version} ist fertig geladen 🎉`);
        else if (res.new?.length) toast(`Neu geladen: ${res.new.join(", ")}`);
        else if (!res.latest) toast("Auf der Webseite gibt es noch keine Emberleaf-Version.");
        else toast("Alles aktuell 👍");
      } catch (err) { toast(err.message, "error"); }
      refresh();
    }
    const btn = e.target.closest("#upd-install");
    if (btn) install(btn);
    if (e.target.closest("#upd-site")) api("open_website", { page: "releases" }).catch((err) => toast(err.message, "error"));
    if (e.target.closest("#upd-upload")) api("open_website", { page: "upload" }).catch((err) => toast(err.message, "error"));
  });

  return {
    open: refresh,
    background() {
      refresh();
      setInterval(() => { if (!document.hidden) refresh(); }, 60 * 1000);
    },
  };
}
