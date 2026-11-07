// Lagerfeuer-App: Chats, Freunde, Party und Spielzeit fürs Handy (läuft im Browser und lässt sich installieren).
// Spricht nur mit dem eigenen Lagerfeuer (gleiche Adresse), angemeldet über einen Kopplungscode aus dem Launcher.

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const ESCAPES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ESCAPES[c]);

const BADGES = {
  founder: ["Gründer", "Hat das Emberleaf-Lagerfeuer entzündet"],
  alpha: ["Alpha-Tester", "Spielt Emberleaf schon in der Alpha"],
  h500: ["500 Stunden", "500 Stunden Spielzeit mit Emberleaf"],
  h100: ["100 Stunden", "100 Stunden Spielzeit mit Emberleaf"],
  h50: ["50 Stunden", "50 Stunden Spielzeit mit Emberleaf"],
  h10: ["10 Stunden", "10 Stunden Spielzeit mit Emberleaf"],
  friend: ["Lagerfeuer-Freund", "Hat Freunde am Lagerfeuer"],
  creator: ["Cosmetic-Designer", "Hat ein eigenes Cosmetic gebaut"],
  explorer: ["Weltenbummler", "War in der Oberwelt, im Nether und im End"],
  nightowl: ["Nachteule", "Hat nach Mitternacht gespielt"],
};
// Kondensstreifen-Farben der Elytra: id, Name, ab wie vielen Spielstunden, Farbe
const CONTRAILS = [
  ["white", "Weiß", 0, "#ffffff"], ["ember", "Glut", 1, "#ff8a3a"], ["sky", "Himmel", 3, "#7cc8ff"], ["rose", "Rosa", 5, "#ff8ac8"],
  ["mint", "Minze", 10, "#7cf0b0"], ["violet", "Violett", 25, "#b088ff"], ["gold", "Gold", 50, "#ffd24a"],
  ["rainbow", "Regenbogen", 100, "conic-gradient(#ff6b6b, #ffd24a, #7cf0b0, #7cc8ff, #b088ff, #ff6b6b)"],
];
const REACTIONS = { heart: "❤️", laugh: "😂", like: "👍", fire: "🔥", wow: "😮", sad: "😢" };
const POLL_MS = 4000;
const touch = matchMedia("(pointer: coarse)").matches;

// ---------- Speicher & Server ----------

const storage = {
  get(key) {
    try { return localStorage.getItem(key); } catch { return null; }
  },
  set(key, value) {
    try {
      if (value == null) localStorage.removeItem(key);
      else localStorage.setItem(key, value);
    } catch { /* privater Modus: dann eben nur bis zum Schließen */ }
  },
};
const readJson = (key) => {
  try { return JSON.parse(storage.get(key) || "null"); } catch { return null; }
};

let token = storage.get("lagerfeuer.token");
let me = readJson("lagerfeuer.me");

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function api(method, path, body, raw) {
  const headers = {};
  if (token) headers.authorization = `Bearer ${token}`;
  let payload;
  if (raw) {
    payload = raw.blob;
    headers["content-type"] = raw.type;
  } else if (body !== undefined) {
    payload = JSON.stringify(body);
    headers["content-type"] = "application/json";
  }
  let res;
  try {
    res = await fetch(path, { method, headers, body: payload, cache: "no-store" });
  } catch {
    setOffline(true);
    throw new ApiError(0, "Keine Verbindung zum Lagerfeuer.");
  }
  setOffline(false);
  const data = await res.json().catch(() => ({}));
  if (res.status === 401 && token && path !== "/v1/pair/claim") {
    signOut(false);
    throw new ApiError(401, "Bitte koppel die App neu.");
  }
  if (!res.ok) throw new ApiError(res.status, data.error || "Das hat nicht geklappt.");
  return data;
}

// ---------- kleine Helfer ----------

const pad = (n) => String(n).padStart(2, "0");
const clock = (ms) => {
  const d = new Date(ms);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
};
const sameDay = (a, b) => a.toDateString() === b.toDateString();
function shortTime(ms) {
  const d = new Date(ms);
  const today = new Date();
  if (sameDay(d, today)) return clock(ms);
  const y = new Date(today);
  y.setDate(today.getDate() - 1);
  if (sameDay(d, y)) return "gestern";
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.`;
}
function dayLabel(ms) {
  const d = new Date(ms);
  const today = new Date();
  if (sameDay(d, today)) return "Heute";
  const y = new Date(today);
  y.setDate(today.getDate() - 1);
  if (sameDay(d, y)) return "Gestern";
  return d.toLocaleDateString("de-DE", { weekday: "long", day: "numeric", month: "long" });
}
function playtime(seconds) {
  const h = Math.floor((seconds || 0) / 3600);
  const m = Math.floor(((seconds || 0) % 3600) / 60);
  return h ? `${h} h ${m} min` : `${m} min`;
}
const hours = (seconds) => `${Math.floor((seconds || 0) / 3600)} h`;
function ago(seconds) {
  const d = Math.max(0, Date.now() / 1000 - seconds);
  if (d < 3600) return `vor ${Math.max(1, Math.round(d / 60))} Min.`;
  if (d < 86400) return `vor ${Math.round(d / 3600)} Std.`;
  const days = Math.round(d / 86400);
  return days === 1 ? "gestern" : `vor ${days} Tagen`;
}
const isServer = (s) => Boolean(s) && s !== "Einzelspieler";
const where = (p) => (p.online ? (isServer(p.server) ? `Spielt auf ${p.server}` : "Spielt gerade Einzelspieler")
  : p.lastSeen ? `Zuletzt online ${ago(p.lastSeen)}` : "Offline");
const head = (uuid, size = "") => `<span class="head ${size}" style="--skin:url('/v1/skin/${esc(uuid)}')"></span>`;
const avatar = (uuid, online, size = "") => `<span class="avatar">${head(uuid, size)}<span class="status ${online ? "on" : ""}"></span></span>`;
function badgeIcons(list, large = false) {
  if (!list || !list.length) return "";
  return `<span class="badges ${large ? "l" : ""}">${list.filter((b) => BADGES[b]).slice(0, 5)
    .map((b) => `<img src="badges/${b}.png" alt="${esc(BADGES[b][0])}" title="${esc(BADGES[b][0])}">`).join("")}</span>`;
}
const dmIdWith = (uuid) => `d${[me.uuid, uuid].sort().join("")}`;

let toastTimer = 0;
function toast(text, error = false) {
  const el = $("#toast");
  el.textContent = text;
  el.className = `show ${error ? "error" : ""}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = ""; }, 3200);
}

let offline = false;
function setOffline(value) {
  if (value === offline) return;
  offline = value;
  if (!openChat) $("#subtitle").textContent = value ? "Keine Verbindung …" : subtitleText;
}

function sheet(html) {
  const el = $("#sheet");
  el.innerHTML = `<div class="grip"></div>${html}`;
  el.hidden = false;
  $("#sheet-backdrop").hidden = false;
  return el;
}
function closeSheet() {
  $("#sheet").hidden = true;
  $("#sheet-backdrop").hidden = true;
  $("#sheet").innerHTML = "";
}
$("#sheet-backdrop").addEventListener("click", closeSheet);

async function confirmSheet(title, text, action) {
  return new Promise((resolve) => {
    const s = sheet(`<h2>${esc(title)}</h2><p class="hint">${esc(text)}</p>
      <div class="sheet-actions"><button class="btn danger block" id="yes">${esc(action)}</button><button class="btn block" id="no">Abbrechen</button></div>`);
    $("#yes", s).onclick = () => { closeSheet(); resolve(true); };
    $("#no", s).onclick = () => { closeSheet(); resolve(false); };
  });
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text);
    toast("Kopiert");
  } catch {
    toast("Kopieren ging nicht", true);
  }
}

// ---------- Zustand ----------

const view = $("#view");
const tabs = $("#tabs");
let subtitleText = "";
let tab = "chats";
const data = { chats: null, friends: null, party: null, partyInvites: [], profile: null };
let openChat = null;
let lastSeenMessage = Number(storage.get("lagerfeuer.lastSeen")) || 0;
let installPrompt = null;

function header(title, subtitle = "") {
  $("#title").textContent = title;
  subtitleText = subtitle;
  $("#subtitle").textContent = offline ? "Keine Verbindung …" : subtitle;
}

function showTab(name) {
  tab = name;
  storage.set("lagerfeuer.tab", name);
  $$("#tabs button").forEach((b) => b.classList.toggle("active", b.dataset.tab === name));
  $("#top-actions").innerHTML = "";
  view.scrollTop = 0;
  render();
}

function render() {
  if (openChat) return;
  if (tab === "chats") renderChats();
  else if (tab === "friends") renderFriends();
  else if (tab === "party") renderParty();
  else renderMe();
}

tabs.addEventListener("click", (e) => {
  const b = e.target.closest("[data-tab]");
  if (b) showTab(b.dataset.tab);
});

function updateDots() {
  const unread = (data.chats || []).reduce((n, c) => n + c.unread, 0);
  const dot = $("#unread-dot");
  dot.hidden = !unread;
  dot.textContent = unread > 99 ? "99+" : String(unread);
  const requests = data.friends?.incoming.length || 0;
  $("#request-dot").hidden = !requests;
  $("#request-dot").textContent = String(requests);
  $("#party-dot").hidden = !data.partyInvites.length;
  $("#party-dot").textContent = String(data.partyInvites.length);
  document.title = unread ? `(${unread}) Lagerfeuer` : "Lagerfeuer";
  if (navigator.setAppBadge) (unread ? navigator.setAppBadge(unread) : navigator.clearAppBadge()).catch(() => {});
}

// ---------- Laden ----------

let chatsSig = "";
async function loadChats() {
  const res = await api("GET", "/v1/chats");
  // neue Nachrichten von anderen, während die App offen ist: kurz anzeigen
  const newest = Math.max(0, ...res.chats.map((c) => c.lastMessage?.id || 0));
  if (data.chats && newest > lastSeenMessage) {
    for (const c of res.chats) {
      const m = c.lastMessage;
      if (m && m.id > lastSeenMessage && m.sender !== me.uuid && m.kind !== "system" && openChat?.id !== c.id) {
        toast(`${c.kind === "dm" ? c.name : `${c.name} · ${c.members.find((x) => x.uuid === m.sender)?.name || "?"}`}: ${preview(m, c, false)}`);
        navigator.vibrate?.(30);
        break;
      }
    }
  }
  if (newest > lastSeenMessage) {
    lastSeenMessage = newest;
    storage.set("lagerfeuer.lastSeen", String(newest));
  }
  data.chats = res.chats;
  updateDots();
  const sig = JSON.stringify(res.chats);
  if (sig !== chatsSig) {
    chatsSig = sig;
    if (tab === "chats" && !openChat) renderChats();
  }
}

let friendsSig = "";
async function loadFriends() {
  const res = await api("GET", "/v1/friends");
  data.friends = res;
  updateDots();
  const sig = JSON.stringify(res);
  if (sig !== friendsSig) {
    friendsSig = sig;
    if (!openChat && (tab === "friends" || tab === "me")) render();
  }
}

let partySig = "";
async function loadParty() {
  const res = await api("GET", "/v1/party");
  data.party = res.party;
  data.partyInvites = res.partyInvites;
  updateDots();
  const sig = JSON.stringify(res);
  if (sig !== partySig) {
    partySig = sig;
    if (tab === "party" && !openChat) renderParty();
  }
}

const quiet = (promise) => promise.catch((e) => {
  if (e.status && e.status !== 401) console.warn(e);
});

let ticks = 0;
async function tick() {
  if (!token || document.hidden) return;
  ticks++;
  const jobs = [];
  if (openChat) jobs.push(loadChat());
  if (!openChat || ticks % 3 === 0) jobs.push(loadChats());
  if (tab === "party" || ticks % 3 === 0) jobs.push(loadParty());
  if (tab === "friends" || ticks % 4 === 0) jobs.push(loadFriends());
  await Promise.all(jobs.map(quiet));
}

// ---------- Anmelden (koppeln) ----------

function renderLogin(prefill = "") {
  tabs.hidden = true;
  $("#back").hidden = true;
  $("#top-actions").innerHTML = "";
  header("Lagerfeuer");
  view.className = "";
  view.innerHTML = `<div class="login">
      <img src="icon-192.png" alt="">
      <h2>Lagerfeuer aufs Handy</h2>
      <p class="hint">Chatte mit deinen Emberleaf-Freunden, sieh wer gerade spielt, starte Partys und schau dir deine Spielzeit an.</p>
      <ol class="steps">
        <li>Öffne am PC den Emberleaf-Launcher → <b>Freunde</b> → <b>Handy verbinden</b>.</li>
        <li>Scanne den QR-Code mit der Kamera oder tippe den Code hier ein.</li>
      </ol>
      <input class="input code-input" id="code" maxlength="7" autocomplete="one-time-code" autocapitalize="characters" placeholder="ABC123"
        value="${esc(prefill)}">
      <button class="btn primary block" id="pair">Koppeln</button>
    </div>`;
  const input = $("#code");
  const go = async () => {
    const btn = $("#pair");
    btn.disabled = true;
    try {
      await pairWith(input.value);
      start();
    } catch (e) {
      toast(e.message, true);
      btn.disabled = false;
    }
  };
  $("#pair").onclick = go;
  input.onkeydown = (e) => {
    if (e.key === "Enter") go();
  };
}

async function pairWith(code) {
  const res = await api("POST", "/v1/pair/claim", { code });
  token = res.token;
  me = { uuid: res.uuid, name: res.name };
  storage.set("lagerfeuer.token", token);
  storage.set("lagerfeuer.me", JSON.stringify(me));
}

async function signOut(tellServer = true) {
  if (tellServer) await api("POST", "/v1/logout").catch(() => {});
  token = null;
  me = null;
  for (const key of ["lagerfeuer.token", "lagerfeuer.me", "lagerfeuer.lastSeen", "lagerfeuer.tab"]) storage.set(key, null);
  openChat = null;
  Object.assign(data, { chats: null, friends: null, party: null, partyInvites: [], profile: null });
  renderLogin();
}

// ---------- Chats ----------

function nameOf(members, uuid) {
  return members.find((m) => m.uuid === uuid)?.name || "?";
}

function preview(m, chat, withSender = true) {
  const who = !withSender || m.kind === "system" ? "" : m.sender === me.uuid ? "Du: " : chat.kind !== "dm" ? `${nameOf(chat.members, m.sender)}: ` : "";
  const body = { image: "📷 Bild", invite: "🎮 Server-Einladung", game: `🎲 ${m.text}`, deleted: "Nachricht gelöscht" }[m.kind] ?? m.text;
  return who + body;
}

function renderChats() {
  header("Chats", data.chats ? `${data.chats.length} ${data.chats.length === 1 ? "Chat" : "Chats"}` : "");
  $("#top-actions").innerHTML = `<button class="icon-btn" id="new-group" aria-label="Neue Gruppe" title="Neue Gruppe">＋</button>`;
  $("#new-group").onclick = newGroupSheet;
  if (!data.chats) {
    view.innerHTML = `<div class="empty">Lädt …</div>`;
    return;
  }
  if (!data.chats.length) {
    view.innerHTML = `<div class="empty"><span class="big">💬</span>Noch keine Chats.<br>Schreib einem Freund unter <b>Freunde</b> oder starte mit ＋ eine Gruppe.</div>`;
    return;
  }
  view.innerHTML = data.chats.map((c) => {
    const partner = c.kind === "dm" ? c.members.find((m) => m.uuid === c.partner) : null;
    const icon = partner ? avatar(partner.uuid, partner.online) : `<span class="group-icon">${c.kind === "party" ? "🎉" : "👥"}</span>`;
    const m = c.lastMessage;
    return `<button class="list-item" data-chat="${esc(c.id)}">${icon}
        <span class="text"><b>${esc(c.name)}</b><small>${esc(m ? preview(m, c) : "Noch keine Nachrichten")}</small></span>
        <span class="side">${m ? shortTime(m.sent) : ""}${c.unread ? `<span class="count">${c.unread > 99 ? "99+" : c.unread}</span>` : ""}</span>
      </button>`;
  }).join("");
}

view.addEventListener("click", (e) => {
  if (openChat) return;
  const chat = e.target.closest("[data-chat]");
  if (chat) openChatView(chat.dataset.chat);
});

function friendChecklist(exclude = []) {
  const list = (data.friends?.friends || []).filter((f) => !exclude.includes(f.uuid));
  if (!list.length) return `<p class="hint">Keine Freunde zum Auswählen.</p>`;
  return list.map((f) => `<label class="check-item"><input type="checkbox" value="${esc(f.uuid)}">${head(f.uuid, "s")}<span>${esc(f.name)}</span></label>`).join("");
}

function newGroupSheet() {
  const s = sheet(`<h2>Neue Gruppe</h2><p class="hint">Bis zu 20 Leute, nur Freunde.</p>
    <input class="input" id="g-name" maxlength="32" placeholder="Name der Gruppe">
    <div class="section">Mitglieder</div><div>${friendChecklist()}</div>
    <div class="sheet-actions"><button class="btn primary block" id="g-create">Gruppe erstellen</button></div>`);
  $("#g-create", s).onclick = async () => {
    const members = $$("input[type=checkbox]:checked", s).map((i) => i.value);
    try {
      const res = await api("POST", "/v1/chat/group", { name: $("#g-name", s).value, members });
      closeSheet();
      await quiet(loadChats());
      openChatView(res.chat);
    } catch (e) {
      toast(e.message, true);
    }
  };
}

// ---------- ein offener Chat ----------

function openChatView(id, partner = null) {
  openChat = { id, partner, data: null, sig: "", replyTo: null, stick: true, limit: 50, sending: false };
  history.pushState({ chat: id }, "");
  tabs.hidden = true;
  $("#back").hidden = false;
  view.className = "chat-open";
  const known = data.chats?.find((c) => c.id === id);
  header(known?.name || partner?.name || "Chat");
  $("#top-actions").innerHTML = "";
  view.innerHTML = `<div class="messages" id="msgs"></div>
    <div class="composer">
      <div id="replying"></div>
      <div class="row">
        <button class="icon-btn" id="attach" aria-label="Mehr" title="Bild, Spiel, Einladung">＋</button>
        <textarea class="input" id="text" rows="1" maxlength="1000" placeholder="Nachricht" enterkeyhint="send"></textarea>
        <button class="icon-btn send" id="send" aria-label="Senden">➤</button>
      </div>
    </div>`;
  const text = $("#text");
  text.addEventListener("input", () => {
    text.style.height = "auto";
    text.style.height = `${Math.min(120, text.scrollHeight)}px`;
  });
  text.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey && !touch) {
      e.preventDefault();
      sendText();
    }
  });
  $("#send").onclick = sendText;
  $("#attach").onclick = attachSheet;
  $("#msgs").addEventListener("click", onMessagesClick);
  quiet(loadChat());
}

function closeChatView() {
  openChat = null;
  view.className = "";
  tabs.hidden = false;
  $("#back").hidden = true;
  showTab(tab);
  quiet(loadChats());
}

window.addEventListener("popstate", () => {
  if (!$("#sheet").hidden) closeSheet();
  if (openChat) closeChatView();
});
$("#back").addEventListener("click", () => history.back());

async function loadChat() {
  const chat = openChat;
  if (!chat) return;
  let res;
  try {
    res = await api("GET", `/v1/chat/messages?chat=${encodeURIComponent(chat.id)}&limit=${chat.limit}`);
  } catch (e) {
    if (e.status === 404 && chat.partner) {
      // noch kein Chat mit diesem Freund: die erste Nachricht legt ihn an
      res = { chat: { id: chat.id, kind: "dm", name: chat.partner.name, partner: chat.partner.uuid }, messages: [], more: false,
        members: [{ uuid: me.uuid, name: me.name, read: 0 }, { uuid: chat.partner.uuid, name: chat.partner.name, online: chat.partner.online, read: 0 }] };
    } else {
      if (e.status === 404) {
        toast(e.message, true);
        history.back();
      }
      throw e;
    }
  }
  if (openChat !== chat) return;
  chat.data = res;
  const others = res.members.filter((m) => m.uuid !== me.uuid);
  const online = res.members.filter((m) => m.online).length;
  header(res.chat.name, res.chat.kind === "dm" ? (others[0] ? where(others[0]) : "") : `${res.members.length} Mitglieder · ${online} online`);
  if (res.chat.kind !== "dm") {
    if (!$("#chat-more")) {
      $("#top-actions").innerHTML = `<button class="icon-btn" id="chat-more" aria-label="Chat-Einstellungen">⋯</button>`;
      $("#chat-more").onclick = chatSettingsSheet;
    }
  }
  const sig = JSON.stringify([res.messages, res.members.map((m) => [m.uuid, m.read, m.online])]);
  if (sig !== chat.sig) {
    chat.sig = sig;
    renderMessages();
  }
  const last = res.messages[res.messages.length - 1];
  const mine = res.members.find((m) => m.uuid === me.uuid);
  if (last && mine && last.id > mine.read && !document.hidden) {
    mine.read = last.id;
    quiet(api("POST", "/v1/chat/read", { chat: chat.id, message: last.id }));
    const listed = data.chats?.find((c) => c.id === chat.id);
    if (listed) {
      listed.unread = 0;
      updateDots();
    }
  }
}

function renderMessages() {
  const box = $("#msgs");
  if (!box || !openChat?.data) return;
  const { messages, members, more, chat } = openChat.data;
  const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 80;
  const names = new Map(members.map((m) => [m.uuid, m.name]));
  let html = more ? `<button class="btn small more" id="older">Ältere Nachrichten laden</button>` : "";
  if (!messages.length) html += `<div class="empty"><span class="big">👋</span>Schreib die erste Nachricht!</div>`;
  let lastDay = "";
  let lastSender = null;
  for (const m of messages) {
    const day = new Date(m.sent).toDateString();
    if (day !== lastDay) {
      html += `<div class="day">${esc(dayLabel(m.sent))}</div>`;
      lastDay = day;
      lastSender = null;
    }
    if (m.kind === "system") {
      html += `<div class="system">${esc(m.text)}</div>`;
      lastSender = null;
      continue;
    }
    const mine = m.sender === me.uuid;
    const cont = lastSender === m.sender;
    lastSender = m.sender;
    const showHead = !mine && chat.kind !== "dm";
    html += `<div class="msg ${mine ? "mine" : ""} ${cont ? "cont" : ""}">
        ${showHead ? `<span class="head-slot">${cont ? "" : head(m.sender, "s")}</span>` : ""}
        <div>${bubble(m, mine, cont && !showHead ? true : cont, names, chat)}${reactionsHtml(m)}</div>
      </div>`;
  }
  // gelesen? (nur unter deiner letzten Nachricht, wenn sie die letzte im Chat ist)
  const last = messages[messages.length - 1];
  if (last && last.sender === me.uuid && last.kind !== "system") {
    const others = members.filter((m) => m.uuid !== me.uuid);
    const readers = others.filter((o) => o.read >= last.id).length;
    if (chat.kind === "dm") html += `<div class="receipt ${readers ? "read" : ""}">${readers ? "✓✓ Gelesen" : "✓ Gesendet"}</div>`;
    else html += `<div class="receipt ${readers ? "read" : ""}">${readers ? `✓✓ Gelesen von ${readers}` : "✓ Gesendet"}</div>`;
  }
  box.innerHTML = html;
  if (atBottom || openChat.stick) {
    box.scrollTop = box.scrollHeight;
    openChat.stick = false;
  }
}

function bubble(m, mine, cont, names, chat) {
  const who = !mine && chat.kind !== "dm" && !cont ? `<span class="who">${esc(names.get(m.sender) || "?")}</span>` : "";
  const time = `<span class="time">${clock(m.sent)}</span>`;
  if (m.kind === "deleted") return `<div class="bubble deleted" data-msg="${m.id}">${who}Nachricht gelöscht${time}</div>`;
  const quote = m.reply
    ? `<div class="quote"><b>${esc(names.get(m.reply.sender) || "?")}</b>${esc(m.reply.missing ? "Nachricht nicht mehr da"
      : m.reply.kind === "image" ? "📷 Bild" : m.reply.kind === "deleted" ? "Nachricht gelöscht" : m.reply.text || "…")}</div>`
    : "";
  let content = "";
  if (m.kind === "text") {
    content = esc(m.text);
  } else if (m.kind === "image") {
    const size = m.data?.w ? ` width="${Number(m.data.w)}" height="${Number(m.data.h)}"` : "";
    content = `<img class="photo" src="/v1/image/${esc(m.data?.image)}" alt="Bild" loading="lazy"${size}>${m.text ? `<div>${esc(m.text)}</div>` : ""}`;
  } else if (m.kind === "invite") {
    content = `<div class="invite"><span>🎮 <b>${mine ? "Du hast eingeladen" : "Komm auf meinen Server!"}</b></span><code>${esc(m.data?.server)}</code>
      ${m.text ? `<span>${esc(m.text)}</span>` : ""}<span class="hint">In Minecraft: Multiplayer → Direkt verbinden</span>
      <button class="btn small" data-copy="${esc(m.data?.server)}">Adresse kopieren</button></div>`;
  } else if (m.kind === "game") {
    content = gameHtml(m, names);
  }
  return `<div class="bubble" data-msg="${m.id}">${who}${quote}${content}${time}</div>`;
}

function reactionsHtml(m) {
  const list = Object.entries(m.reactions || {}).filter(([, users]) => users.length);
  if (!list.length) return "";
  return `<div class="reactions">${list.map(([k, users]) => `<button class="reaction ${users.includes(me.uuid) ? "on" : ""}" data-react="${esc(k)}"
    data-id="${m.id}">${REACTIONS[k] || "?"} ${users.length}</button>`).join("")}</div>`;
}

function gameHtml(m, names) {
  const d = m.data;
  if (!d) return "";
  const seat = d.players.indexOf(me.uuid);
  const canJoin = d.players[1] === null && d.players[0] !== me.uuid;
  const myTurn = !d.winner && ((seat >= 0 && seat === d.turn) || (canJoin && d.turn === 1));
  const nameOfPlayer = (u) => (u === me.uuid ? "Du" : names.get(u) || "?");
  let status;
  if (d.winner === "draw") status = "Unentschieden 🤝";
  else if (d.winner) status = d.winner === me.uuid ? "Du hast gewonnen! 🏆" : `${nameOfPlayer(d.winner)} hat gewonnen`;
  else if (myTurn) status = "Du bist dran";
  else if (d.players[d.turn]) status = `${nameOfPlayer(d.players[d.turn])} ist dran`;
  else status = "Wer spielt mit? Einfach ziehen";
  const cls = (v) => (v === "x" ? "x" : v === "o" ? "o" : "");
  const cells = [...d.board];
  const board = d.game === "ttt"
    ? `<div class="board ttt">${cells.map((v, i) => `<button class="cell ${cls(v)}" data-move="${i}" data-id="${m.id}" ${myTurn && v === "." ? "" : "disabled"}>${v === "x" ? "✕" : v === "o" ? "○" : ""}</button>`).join("")}</div>`
    : `<div class="board c4">${cells.map((v, i) => `<button class="cell ${cls(v)} ${d.last === i ? "last" : ""}" data-move="${i % 7}" data-id="${m.id}" ${myTurn ? "" : "disabled"} aria-label="Spalte ${(i % 7) + 1}"></button>`).join("")}</div>`;
  return `<div class="game"><b>🎲 ${d.game === "ttt" ? "Tic-Tac-Toe" : "Vier gewinnt"}</b><div class="status">${esc(status)}</div>${board}</div>`;
}

async function onMessagesClick(e) {
  const t = e.target;
  if (t.id === "older") {
    openChat.limit = Math.min(100, openChat.limit + 50);
    const box = $("#msgs");
    const fromBottom = box.scrollHeight - box.scrollTop;
    await quiet(loadChat());
    box.scrollTop = box.scrollHeight - fromBottom;
    return;
  }
  const copyBtn = t.closest("[data-copy]");
  if (copyBtn) return copy(copyBtn.dataset.copy);
  const move = t.closest("[data-move]");
  if (move) {
    move.disabled = true;
    try {
      await api("POST", "/v1/chat/game/move", { message: Number(move.dataset.id), cell: Number(move.dataset.move) });
    } catch (err) {
      toast(err.message, true);
    }
    return quiet(loadChat());
  }
  const reaction = t.closest("[data-react]");
  if (reaction) return toggleReaction(Number(reaction.dataset.id), reaction.dataset.react);
  if (t.matches("img.photo")) {
    const box = $("#lightbox");
    $("img", box).src = t.src;
    box.hidden = false;
    return;
  }
  const b = t.closest("[data-msg]");
  if (b) {
    const m = openChat.data.messages.find((x) => x.id === Number(b.dataset.msg));
    if (m) messageSheet(m);
  }
}
$("#lightbox").addEventListener("click", () => { $("#lightbox").hidden = true; });

async function toggleReaction(id, emoji) {
  try {
    await api("POST", "/v1/chat/react", { message: id, emoji });
  } catch (e) {
    toast(e.message, true);
  }
  await quiet(loadChat());
}

function messageSheet(m) {
  const mine = m.sender === me.uuid;
  const s = sheet(`${m.kind === "deleted" ? "" : `<div class="reaction-bar">${Object.entries(REACTIONS)
    .map(([k, emoji]) => `<button data-r="${k}" class="${(m.reactions?.[k] || []).includes(me.uuid) ? "on" : ""}" aria-label="${k}">${emoji}</button>`).join("")}</div>`}
    <div class="sheet-actions">
      ${m.kind === "deleted" ? "" : `<button class="btn block" id="a-reply">↩︎ Antworten</button>`}
      ${m.kind === "text" ? `<button class="btn block" id="a-copy">Text kopieren</button>` : ""}
      ${mine && m.kind !== "deleted" ? `<button class="btn danger block" id="a-delete">Nachricht löschen</button>` : ""}
      <button class="btn block" id="a-close">Schließen</button>
    </div>`);
  $$("[data-r]", s).forEach((b) => {
    b.onclick = () => {
      closeSheet();
      toggleReaction(m.id, b.dataset.r);
    };
  });
  const reply = $("#a-reply", s);
  if (reply) {
    reply.onclick = () => {
      closeSheet();
      setReply(m);
    };
  }
  const copyBtn = $("#a-copy", s);
  if (copyBtn) {
    copyBtn.onclick = () => {
      closeSheet();
      copy(m.text);
    };
  }
  const del = $("#a-delete", s);
  if (del) {
    del.onclick = async () => {
      closeSheet();
      if (!(await confirmSheet("Nachricht löschen?", "Sie verschwindet für alle im Chat.", "Löschen"))) return;
      try {
        await api("POST", "/v1/chat/delete", { message: m.id });
      } catch (e) {
        toast(e.message, true);
      }
      quiet(loadChat());
    };
  }
  $("#a-close", s).onclick = closeSheet;
}

function setReply(m) {
  openChat.replyTo = m;
  const box = $("#replying");
  if (!m) {
    box.innerHTML = "";
    return;
  }
  const names = new Map(openChat.data.members.map((x) => [x.uuid, x.name]));
  const text = m.kind === "image" ? "📷 Bild" : m.kind === "game" ? "🎲 Spiel" : m.kind === "invite" ? "🎮 Einladung" : m.text;
  box.innerHTML = `<div class="replying"><div class="quote"><b>${esc(names.get(m.sender) || "?")}</b>${esc(text.slice(0, 100))}</div>
    <button class="icon-btn" id="reply-cancel" aria-label="Antwort abbrechen">✕</button></div>`;
  $("#reply-cancel").onclick = () => setReply(null);
  $("#text").focus();
}

async function send(payload) {
  const chat = openChat;
  if (!chat || chat.sending) return false;
  chat.sending = true;
  try {
    const body = { ...payload, reply: chat.replyTo?.id ?? undefined };
    if (!chat.data?.messages.length && chat.partner) body.to = chat.partner.uuid;
    else body.chat = chat.id;
    const res = await api("POST", "/v1/chat/send", body);
    if (res.chat && res.chat !== chat.id) chat.id = res.chat;
    chat.partner = null;
    setReply(null);
    chat.stick = true;
    await quiet(loadChat());
    return true;
  } catch (e) {
    toast(e.message, true);
    return false;
  } finally {
    chat.sending = false;
  }
}

async function sendText() {
  const text = $("#text");
  const value = text.value.trim();
  if (!value) return;
  if (await send({ text: value })) {
    text.value = "";
    text.style.height = "auto";
  }
}

function attachSheet() {
  const s = sheet(`<h2>Senden</h2><div class="sheet-actions">
      <button class="btn block" id="s-image">📷 Bild</button>
      <button class="btn block" id="s-ttt">❌⭕ Tic-Tac-Toe spielen</button>
      <button class="btn block" id="s-c4">🔴🟡 Vier gewinnt spielen</button>
      <button class="btn block" id="s-invite">🎮 Auf einen Server einladen</button>
    </div>`);
  $("#s-image", s).onclick = () => {
    closeSheet();
    pickImage();
  };
  for (const game of ["ttt", "c4"]) {
    $(`#s-${game}`, s).onclick = async () => {
      closeSheet();
      await ensureChat();
      try {
        await api("POST", "/v1/chat/game", { chat: openChat.id, game });
        openChat.stick = true;
        quiet(loadChat());
      } catch (e) {
        toast(e.message, true);
      }
    };
  }
  $("#s-invite", s).onclick = () => {
    const last = storage.get("lagerfeuer.server") || "";
    const s2 = sheet(`<h2>Auf einen Server einladen</h2><p class="hint">Die Adresse, die ihr in Minecraft unter „Direkt verbinden“ eingebt.</p>
      <input class="input" id="srv" maxlength="120" placeholder="z. B. play.example.net" value="${esc(last)}" autocapitalize="off" autocomplete="off">
      <div class="sheet-actions"><button class="btn primary block" id="srv-send">Einladung senden</button></div>`);
    $("#srv-send", s2).onclick = async () => {
      const server = $("#srv", s2).value.trim();
      if (!server) return;
      storage.set("lagerfeuer.server", server);
      closeSheet();
      await send({ kind: "invite", data: { server } });
    };
  };
}

// a DM that does not exist yet is created by its first message; games and pictures need it first
async function ensureChat() {
  if (openChat?.partner && !openChat.data?.messages.length) await send({ text: "👋" });
}

function pickImage() {
  const input = document.createElement("input");
  input.type = "file";
  input.accept = "image/*";
  input.onchange = async () => {
    const file = input.files[0];
    if (!file) return;
    try {
      toast("Bild wird gesendet …");
      const image = await shrinkImage(file);
      const up = await api("PUT", "/v1/image", undefined, image);
      await ensureChat();
      await send({ kind: "image", data: { image: up.id, w: image.w, h: image.h } });
    } catch (e) {
      toast(e.message, true);
    }
  };
  input.click();
}

async function shrinkImage(file) {
  const bitmap = await createImageBitmap(file);
  if (file.type === "image/gif" && file.size <= 440 * 1024) return { blob: file, type: "image/gif", w: bitmap.width, h: bitmap.height };
  let scale = Math.min(1, 1280 / Math.max(bitmap.width, bitmap.height));
  for (let attempt = 0; attempt < 6; attempt++) {
    const w = Math.max(1, Math.round(bitmap.width * scale));
    const h = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    canvas.getContext("2d").drawImage(bitmap, 0, 0, w, h);
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", Math.max(0.5, 0.84 - attempt * 0.07)));
    if (blob && blob.size <= 440 * 1024) return { blob, type: "image/jpeg", w, h };
    scale *= 0.75;
  }
  throw new Error("Das Bild ist zu groß.");
}

function chatSettingsSheet() {
  const d = openChat?.data;
  if (!d) return;
  const isGroup = d.chat.kind === "group";
  const members = d.members.map((m) => `<div class="list-item">${avatar(m.uuid, m.online, "s")}<span class="text"><b>${esc(m.name)}${m.uuid === me.uuid ? " (du)" : ""}</b>
      <small>${esc(where(m))}</small></span></div>`).join("");
  const s = sheet(`<h2>${esc(d.chat.name)}</h2><p class="hint">${d.chat.kind === "party" ? "Party-Chat: alle in der Party sind hier drin." : `${d.members.length} Mitglieder`}</p>
    ${members}
    ${isGroup ? `<div class="section">Freunde hinzufügen</div><div id="add-list">${friendChecklist(d.members.map((m) => m.uuid))}</div>
      <div class="sheet-actions"><button class="btn block" id="g-add">Ausgewählte hinzufügen</button>
      <div class="row"><input class="input" id="g-rename" maxlength="32" placeholder="Neuer Name" value="${esc(d.chat.name)}"><button class="btn" id="g-rename-btn">Umbenennen</button></div>
      <button class="btn danger block" id="g-leave">Gruppe verlassen</button></div>` : ""}`);
  if (!isGroup) return;
  $("#g-add", s).onclick = async () => {
    for (const box of $$("#add-list input:checked", s)) {
      try {
        await api("POST", "/v1/chat/group/add", { chat: d.chat.id, uuid: box.value });
      } catch (e) {
        toast(e.message, true);
      }
    }
    closeSheet();
    quiet(loadChat());
  };
  $("#g-rename-btn", s).onclick = async () => {
    try {
      await api("POST", "/v1/chat/group/rename", { chat: d.chat.id, name: $("#g-rename", s).value });
      closeSheet();
      quiet(loadChat());
    } catch (e) {
      toast(e.message, true);
    }
  };
  $("#g-leave", s).onclick = async () => {
    closeSheet();
    if (!(await confirmSheet("Gruppe verlassen?", "Du siehst die Nachrichten danach nicht mehr.", "Verlassen"))) return;
    try {
      await api("POST", "/v1/chat/group/leave", { chat: d.chat.id });
      history.back();
    } catch (e) {
      toast(e.message, true);
    }
  };
}

// ---------- Freunde ----------

function renderFriends() {
  const f = data.friends;
  header("Freunde", f ? `${f.friends.filter((x) => x.online).length} von ${f.friends.length} online` : "");
  if (!f) {
    view.innerHTML = `<div class="empty">Lädt …</div>`;
    return;
  }
  let html = `<div class="card"><div class="row"><input class="input" id="add-name" maxlength="16" placeholder="Minecraft-Name" autocomplete="off" autocapitalize="off">
    <button class="btn primary" id="add-friend">Hinzufügen</button></div></div>`;
  if (f.incoming.length) {
    html += `<div class="section">Anfragen · ${f.incoming.length}</div>${f.incoming.map((r) => `<div class="list-item">${head(r.uuid)}
      <span class="text"><b>${esc(r.name)}</b><small>möchte dein Freund sein</small></span>
      <span class="row"><button class="btn primary small" data-accept="${esc(r.uuid)}">Annehmen</button><button class="btn small" data-decline="${esc(r.uuid)}" aria-label="Ablehnen">✕</button></span></div>`).join("")}`;
  }
  const online = f.friends.filter((x) => x.online);
  const offlineList = f.friends.filter((x) => !x.online);
  const row = (x) => `<button class="list-item" data-friend="${esc(x.uuid)}">${avatar(x.uuid, x.online)}
      <span class="text"><b>${esc(x.name)} ${badgeIcons(x.badges)}</b><small>${esc(where(x))}</small></span>
      <span class="side">${x.playtime ? `⏳ ${hours(x.playtime)}` : ""}</span></button>`;
  if (online.length) html += `<div class="section">Online · ${online.length}</div>${online.map(row).join("")}`;
  if (offlineList.length) html += `<div class="section">Offline · ${offlineList.length}</div>${offlineList.map(row).join("")}`;
  if (!f.friends.length) html += `<div class="empty"><span class="big">🔥</span>Noch keine Freunde am Lagerfeuer.<br>Füg oben jemanden mit seinem Minecraft-Namen hinzu.</div>`;
  if (f.outgoing.length) {
    html += `<div class="section">Gesendet</div>${f.outgoing.map((r) => `<div class="list-item">${head(r.uuid)}<span class="text"><b>${esc(r.name)}</b>
      <small>wartet auf Antwort</small></span><button class="btn small" data-decline="${esc(r.uuid)}">Zurückziehen</button></div>`).join("")}`;
  }
  view.innerHTML = html;
  const input = $("#add-name");
  const add = async () => {
    const name = input.value.trim();
    if (!name) return input.focus();
    try {
      const res = await api("POST", "/v1/friends/add", { name });
      toast(res.status === "friends" ? `${res.friend.name} ist jetzt dein Freund 🎉` : `Anfrage an ${res.friend.name} geschickt ✉️`);
      input.value = "";
      await quiet(loadFriends());
    } catch (e) {
      toast(e.message, true);
    }
  };
  $("#add-friend").onclick = add;
  input.onkeydown = (e) => {
    if (e.key === "Enter") add();
  };
  $$("[data-accept]").forEach((b) => {
    b.onclick = async () => {
      b.disabled = true;
      try {
        await api("POST", "/v1/friends/accept", { uuid: b.dataset.accept });
        toast("Ihr seid jetzt Freunde 🎉");
      } catch (e) {
        toast(e.message, true);
      }
      quiet(loadFriends());
    };
  });
  $$("[data-decline]").forEach((b) => {
    b.onclick = async () => {
      b.disabled = true;
      try {
        await api("POST", "/v1/friends/remove", { uuid: b.dataset.decline });
      } catch (e) {
        toast(e.message, true);
      }
      quiet(loadFriends());
    };
  });
  $$("[data-friend]").forEach((b) => {
    b.onclick = () => friendSheet(f.friends.find((x) => x.uuid === b.dataset.friend));
  });
}

function badgeRows(list) {
  if (!list?.length) return `<p class="hint">Noch keine Abzeichen.</p>`;
  return `<div class="badge-list">${list.filter((b) => BADGES[b]).map((b) => `<div class="badge-row"><img src="badges/${b}.png" alt="">
    <span><b>${esc(BADGES[b][0])}</b><small>${esc(BADGES[b][1])}</small></span></div>`).join("")}</div>`;
}

function contrailSwatch(id) {
  const c = CONTRAILS.find((x) => x[0] === id) || CONTRAILS[0];
  return `<span class="contrail current"><span class="swatch" style="background:${c[3]}"></span>${esc(c[1])}</span>`;
}

function friendSheet(f) {
  if (!f) return;
  const inParty = data.party?.members.some((m) => m.uuid === f.uuid);
  const s = sheet(`<div class="profile-top">${head(f.uuid, "l")}<div><h2>${esc(f.name)}</h2><small class="hint">${esc(where(f))}</small>
      <div>${badgeIcons(f.badges, true)}</div></div></div>
    <div class="stat-grid"><div class="stat"><small>Spielzeit gesamt</small><b>${playtime(f.playtime)}</b></div>
      <div class="stat"><small>Heute</small><b>${playtime(f.today)}</b></div></div>
    <div class="section">Abzeichen</div>${badgeRows(f.badges)}
    <div class="section">Kondensstreifen</div><div class="contrails">${contrailSwatch(f.contrail)}</div>
    <div class="sheet-actions">
      <button class="btn primary block" id="f-chat">💬 Nachricht schreiben</button>
      ${inParty ? "" : `<button class="btn block" id="f-party">🎉 In Party einladen</button>`}
      ${f.online && isServer(f.server) ? `<button class="btn block" id="f-server">Serveradresse kopieren</button>` : ""}
      <button class="btn danger block" id="f-remove">Freund entfernen</button>
    </div>`);
  $("#f-chat", s).onclick = () => {
    closeSheet();
    openChatView(dmIdWith(f.uuid), { uuid: f.uuid, name: f.name, online: f.online });
  };
  const party = $("#f-party", s);
  if (party) {
    party.onclick = async () => {
      try {
        await api("POST", "/v1/party/invite", { uuid: f.uuid });
        toast(`${f.name} ist eingeladen 🎉`);
        closeSheet();
        quiet(loadParty());
      } catch (e) {
        toast(e.message, true);
      }
    };
  }
  const server = $("#f-server", s);
  if (server) server.onclick = () => copy(f.server);
  $("#f-remove", s).onclick = async () => {
    closeSheet();
    if (!(await confirmSheet(`${f.name} entfernen?`, `${f.name} verschwindet aus deiner Freundesliste und du aus seiner.`, "Entfernen"))) return;
    try {
      await api("POST", "/v1/friends/remove", { uuid: f.uuid });
      toast(`${f.name} entfernt`);
    } catch (e) {
      toast(e.message, true);
    }
    quiet(loadFriends());
  };
}

// ---------- Party ----------

function renderParty() {
  const p = data.party;
  header("Party", p ? `${p.members.length} ${p.members.length === 1 ? "Person" : "Leute"}` : "Zusammen spielen");
  let html = "";
  for (const inv of data.partyInvites) {
    html += `<div class="card"><h3>🎉 Einladung</h3><p class="hint"><b>${esc(inv.leaderName)}</b> lädt dich in die Party ein.</p>
      <div class="row"><button class="btn primary" data-join="${esc(inv.party)}">Beitreten</button><button class="btn" data-pdecline="${esc(inv.party)}">Ablehnen</button></div></div>`;
  }
  if (!p) {
    html += `<div class="empty"><span class="big">🎉</span><b>Zusammen von Server zu Server</b><br>In einer Party seht ihr, wo der Leiter spielt, und kommt im Spiel
      mit einem Klick hinterher. Dazu gibt es einen Party-Chat.</div><button class="btn primary block" id="p-create">Party starten</button>`;
  } else {
    const lead = p.leader === me.uuid;
    html += `<div class="section">In der Party</div>${p.members.map((m) => `<div class="list-item">${avatar(m.uuid, m.online)}
        <span class="text"><b>${m.uuid === p.leader ? "👑 " : ""}${esc(m.name)}${m.uuid === me.uuid ? " (du)" : ""}</b>
        <small>${esc(m.online ? (isServer(m.server) ? `auf ${m.server}` : "spielt Einzelspieler") : "offline")}</small></span>
        ${lead && m.uuid !== me.uuid ? `<button class="btn small" data-kick="${esc(m.uuid)}">Entfernen</button>` : ""}</div>`).join("")}
      <div class="sheet-actions"><button class="btn primary block" id="p-chat">💬 Party-Chat</button>
      <button class="btn block" id="p-invite">Freunde einladen</button><button class="btn danger block" id="p-leave">Party verlassen</button></div>`;
  }
  view.innerHTML = html;
  $$("[data-join]").forEach((b) => {
    b.onclick = () => partyAction("/v1/party/join", { party: b.dataset.join }, "Du bist in der Party 🎉");
  });
  $$("[data-pdecline]").forEach((b) => {
    b.onclick = () => partyAction("/v1/party/decline", { party: b.dataset.pdecline });
  });
  $$("[data-kick]").forEach((b) => {
    b.onclick = () => partyAction("/v1/party/kick", { uuid: b.dataset.kick });
  });
  const create = $("#p-create");
  if (create) create.onclick = () => partyAction("/v1/party/create", {}, "Party gestartet 🎉");
  if (!p) return;
  $("#p-chat").onclick = () => openChatView(p.chat);
  $("#p-invite").onclick = () => {
    const list = (data.friends?.friends || []).filter((f) => !p.members.some((m) => m.uuid === f.uuid));
    const s = sheet(`<h2>Freunde einladen</h2>${list.length ? list.map((f) => `<div class="list-item">${avatar(f.uuid, f.online, "s")}
        <span class="text"><b>${esc(f.name)}</b><small>${esc(where(f))}</small></span><button class="btn small primary" data-pinvite="${esc(f.uuid)}">Einladen</button></div>`).join("")
      : `<p class="hint">Alle deine Freunde sind schon dabei.</p>`}`);
    $$("[data-pinvite]", s).forEach((b) => {
      b.onclick = async () => {
        b.disabled = true;
        try {
          await api("POST", "/v1/party/invite", { uuid: b.dataset.pinvite });
          b.textContent = "Eingeladen ✓";
        } catch (e) {
          toast(e.message, true);
          b.disabled = false;
        }
      };
    });
  };
  $("#p-leave").onclick = async () => {
    if (await confirmSheet("Party verlassen?", "Du kannst später wieder eingeladen werden.", "Verlassen")) partyAction("/v1/party/leave", {});
  };
}

async function partyAction(path, body, success) {
  try {
    const res = await api("POST", path, body);
    if (res.party !== undefined) {
      data.party = res.party;
      data.partyInvites = res.partyInvites;
    }
    if (success) toast(success);
  } catch (e) {
    toast(e.message, true);
  }
  await quiet(loadParty());
  renderParty();
  quiet(loadChats());
}

// ---------- Ich ----------

async function renderMe() {
  header("Ich", me?.name || "");
  if (!data.profile) {
    view.innerHTML = `<div class="empty">Lädt …</div>`;
    try {
      data.profile = await api("GET", "/v1/profile");
    } catch (e) {
      view.innerHTML = `<div class="empty">${esc(e.message)}</div>`;
      return;
    }
    if (tab !== "me" || openChat) return;
  }
  const p = data.profile;
  const have = new Set(p.badges);
  const nextContrail = CONTRAILS.find((c) => p.playtime < c[2] * 3600);
  const since = p.since ? new Date(p.since * 1000).toLocaleDateString("de-DE", { month: "long", year: "numeric" }) : "";
  let html = `<div class="profile-top">${head(p.uuid, "l")}<div><h2>${esc(p.name)}</h2>
      <small class="hint">${since ? `Am Lagerfeuer seit ${esc(since)} · ` : ""}${p.friends} ${p.friends === 1 ? "Freund" : "Freunde"}</small></div></div>
    <div class="stat-grid"><div class="stat"><small>Spielzeit gesamt</small><b>${playtime(p.playtime)}</b></div>
      <div class="stat"><small>Heute</small><b>${playtime(p.today)}</b></div></div>
    <p class="hint">Gezählt wird die Zeit in Welten mit Emberleaf (nicht im Menü oder pausiert).</p>
    <div class="section">Abzeichen · ${p.badges.length} von ${Object.keys(BADGES).length}</div>
    <div class="badge-list">${Object.entries(BADGES).map(([id, [name, text]]) => `<div class="badge-row ${have.has(id) ? "" : "locked"}">
      <img src="badges/${id}.png" alt=""><span><b>${esc(name)}</b><small>${esc(text)}</small></span></div>`).join("")}</div>
    <div class="section">Kondensstreifen-Farben</div>
    <div class="contrails">${CONTRAILS.map(([id, name, h, color]) => `<div class="contrail ${p.playtime >= h * 3600 ? "" : "locked"} ${p.contrail === id ? "current" : ""}">
      <span class="swatch" style="background:${color}"></span>${esc(name)}<br><small>${h ? `ab ${h} h` : "immer"}</small></div>`).join("")}</div>`;
  if (nextContrail) {
    const prev = CONTRAILS[CONTRAILS.indexOf(nextContrail) - 1];
    const from = prev[2] * 3600;
    const share = Math.min(1, (p.playtime - from) / (nextContrail[2] * 3600 - from));
    html += `<p class="hint">Nächste Farbe: <b>${esc(nextContrail[1])}</b> in ${playtime(nextContrail[2] * 3600 - p.playtime)} Spielzeit.</p>
      <div class="progress"><i style="width:${(share * 100).toFixed(1)}%"></i></div>`;
  }
  html += `<p class="hint">Die Farbe stellst du im Spiel ein: Emberleaf-Einstellungen → Cosmetics → Kondensstreifen.</p>
    <div class="sheet-actions">
      ${installPrompt ? `<button class="btn primary block" id="install">📲 App installieren</button>` : ""}
      ${/iphone|ipad/i.test(navigator.userAgent) && !navigator.standalone ? `<p class="hint">Auf dem iPhone: Teilen-Knopf → „Zum Home-Bildschirm“.</p>` : ""}
      <button class="btn block" id="refresh-me">Aktualisieren</button>
      <button class="btn danger block" id="logout">Abmelden</button>
    </div>`;
  view.innerHTML = html;
  const install = $("#install");
  if (install) {
    install.onclick = async () => {
      installPrompt.prompt();
      await installPrompt.userChoice.catch(() => {});
      installPrompt = null;
      renderMe();
    };
  }
  $("#refresh-me").onclick = () => {
    data.profile = null;
    renderMe();
  };
  $("#logout").onclick = async () => {
    if (await confirmSheet("Abmelden?", "Zum Wiederkommen brauchst du einen neuen Code aus dem Launcher.", "Abmelden")) signOut(true);
  };
}

window.addEventListener("beforeinstallprompt", (e) => {
  e.preventDefault();
  installPrompt = e;
  if (tab === "me" && !openChat) renderMe();
});

// ---------- Start ----------

let started = false;
async function start() {
  $("#app").classList.remove("loading");
  if (!token || !me) return renderLogin();
  tabs.hidden = false;
  view.className = "";
  showTab(["chats", "friends", "party", "me"].includes(storage.get("lagerfeuer.tab")) ? storage.get("lagerfeuer.tab") : "chats");
  await Promise.all([loadChats(), loadFriends(), loadParty()].map(quiet));
  if (started) return;
  started = true;
  setInterval(tick, POLL_MS);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && token) tick();
  });
}

async function boot() {
  if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
  const code = new URLSearchParams(location.hash.slice(1)).get("pair");
  if (code) {
    history.replaceState(null, "", location.pathname);
    try {
      await pairWith(code);
      toast(`Hallo ${me.name}! 🔥`);
    } catch (e) {
      $("#app").classList.remove("loading");
      renderLogin(code);
      toast(e.message, true);
      return;
    }
  }
  start();
}

boot();
