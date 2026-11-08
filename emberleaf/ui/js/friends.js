// Freunde-Seite ("Lagerfeuer"): Freundesliste mit Spielzeit und Abzeichen, Chats (Gruppen, Reaktionen, Antworten,
// Spiele, Bilder, Einladungen), Party und „Handy verbinden“ (QR-Code für die Lagerfeuer-App).
import { $, $$, api, confirmDialog, drawHead, esc, modal, setBusy, toast } from "./ui.js";

const REFRESH_MS = 30_000;
const CHAT_POLL_MS = 4000;
const ICON_X = `<svg viewBox="0 0 12 12"><path d="M2 2h2v2H2zM8 2h2v2H8zM4 4h2v2H4zM6 4h2v2H6zM4 6h2v2H4zM6 6h2v2H6zM2 8h2v2H2zM8 8h2v2H8z" fill="currentColor"/></svg>`;
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
const CONTRAILS = { white: ["Weiß", "#ffffff"], ember: ["Glut", "#ff8a3a"], sky: ["Himmel", "#7cc8ff"], rose: ["Rosa", "#ff8ac8"],
  mint: ["Minze", "#7cf0b0"], violet: ["Violett", "#b088ff"], gold: ["Gold", "#ffd24a"],
  rainbow: ["Regenbogen", "linear-gradient(90deg, #ff6b6b, #ffd24a, #7cf0b0, #7cc8ff, #b088ff)"] };
const REACTIONS = { heart: "❤️", laugh: "😂", like: "👍", fire: "🔥", wow: "😮", sad: "😢" };

const ago = (seconds) => {
  const d = Math.max(0, Date.now() / 1000 - seconds);
  if (d < 3600) return `vor ${Math.max(1, Math.round(d / 60))} Min.`;
  if (d < 86400) return `vor ${Math.round(d / 3600)} Std.`;
  const days = Math.round(d / 86400);
  return days === 1 ? "gestern" : `vor ${days} Tagen`;
};
const isMultiplayer = (server) => Boolean(server) && server !== "Einzelspieler";
const playtime = (s) => {
  const h = Math.floor((s || 0) / 3600);
  const m = Math.floor(((s || 0) % 3600) / 60);
  return h ? `${h} h ${m} min` : `${m} min`;
};
const pad = (n) => String(n).padStart(2, "0");
const clock = (ms) => {
  const d = new Date(ms);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
};
function shortTime(ms) {
  const d = new Date(ms);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) return clock(ms);
  const y = new Date(now);
  y.setDate(now.getDate() - 1);
  if (d.toDateString() === y.toDateString()) return "gestern";
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.`;
}
const where = (p) => (p.online ? (isMultiplayer(p.server) ? `Spielt auf ${p.server}` : "Spielt gerade Einzelspieler")
  : p.lastSeen ? `Zuletzt online ${ago(p.lastSeen)}` : "Offline");
const badgeIcons = (list) => (list || []).filter((b) => BADGES[b]).slice(0, 5)
  .map((b) => `<img class="badge-icon" src="assets/badges/${b}.png" alt="${esc(BADGES[b][0])}" title="${esc(BADGES[b][0])}: ${esc(BADGES[b][1])}">`).join("");

/**
 * @param {object} opts
 * @param {object} opts.state      gemeinsamer App-Zustand (settings, account, page)
 * @param {() => Promise<boolean>} opts.login   öffnet den Microsoft-Login
 * @param {(server: string) => void} opts.join  startet das gewählte Profil direkt auf einem Server
 */
export function createFriends({ state, login, join, onFriends }) {
  const body = $("#friends-body");
  const refreshBtn = $("#friends-refresh");
  const phoneBtn = $("#friends-phone");
  const tabsEl = $("#friends-tabs");
  const heads = new Map(); // Name -> Skin-Daten-URL (oder Promise)
  let data = null;
  let chats = null;
  let party = null;
  let sub = "friends";
  let chat = null; // { id, partner, name, data, sig, replyTo, limit }
  let timer = 0;
  let chatTimer = 0;
  let loading = null;

  function setBadge() {
    const requests = data?.state === "ok" ? data.incoming.length : 0;
    const unread = (chats?.chats || []).reduce((n, c) => n + c.unread, 0);
    const invites = party?.partyInvites?.length || 0;
    const total = requests + unread + invites;
    const badge = $("#friends-badge");
    badge.hidden = !total;
    badge.textContent = total > 9 ? "9+" : String(total);
    const cb = $("#chats-badge");
    cb.hidden = !unread;
    cb.textContent = unread > 99 ? "99+" : String(unread);
    const pb = $("#party-badge");
    pb.hidden = !invites;
    pb.textContent = String(invites);
  }

  async function headFor(name, canvas) {
    if (!heads.has(name)) heads.set(name, api("skin", { name }).then((s) => s.skin).catch(() => null));
    const skin = await heads.get(name);
    if (skin && canvas.isConnected) drawHead(canvas, skin);
  }

  function paintHeads(root = body) {
    root.querySelectorAll("canvas[data-head]").forEach((c) => headFor(c.dataset.head, c));
  }

  const headCanvas = (name, size = 8) => `<canvas width="${size}" height="${size}" data-head="${esc(name)}"></canvas>`;

  // ---------- Einrichten ----------

  function renderSetup() {
    refreshBtn.hidden = true;
    body.innerHTML = `
      <div class="setup-card">
        <div class="setup-emoji">🔥</div>
        <h3>Das Lagerfeuer brennt noch nicht</h3>
        <p>Das Lagerfeuer ist der kleine Emberleaf-Server für euch alle: Über ihn findet ihr euch als Freunde, chattet (auch vom Handy),
          macht Partys und seht im Spiel gegenseitig eure Cosmetics, Abzeichen und Emotes.</p>
        <ol class="setup-steps">
          <li><b>Ihr müsst nichts eintragen.</b> Sobald das Lagerfeuer einmal angezündet ist, kommt es mit dem nächsten Emberleaf-Update automatisch zu allen.</li>
          <li>Danach steht hier deine Freundesliste. Freunde fügst du mit ihrem Minecraft-Namen hinzu.</li>
          <li>Im Spiel öffnest du das Lagerfeuer mit <b>U</b>, im Hauptmenü steht die Liste rechts oben.</li>
        </ol>
        <details class="setup-advanced">
          <summary>Eigenes Lagerfeuer verbinden (für Bastler)</summary>
          <p class="hint">Habt ihr schon ein eigenes Lagerfeuer bei Cloudflare? Dann trag seine Adresse hier ein. <button class="btn small" id="fr-guide">Anleitung</button></p>
          <div class="setup-row">
            <input class="input" id="fr-server" autocomplete="off" placeholder="https://lagerfeuer.dein-name.workers.dev">
            <button class="btn primary" id="fr-server-save">Verbinden</button>
          </div>
        </details>
      </div>`;
    const input = $("#fr-server", body);
    const save = async () => {
      const btn = $("#fr-server-save", body);
      setBusy(btn, true, "Verbindet …");
      try {
        state.settings = await api("save_settings", { friends_server: input.value });
        await refresh(true);
      } catch (err) {
        toast(err.message, "error");
        setBusy(btn, false);
      }
    };
    $("#fr-server-save", body).addEventListener("click", save);
    $("#fr-guide", body).addEventListener("click", () => api("open_folder", { target: "lagerfeuer" }).catch((err) => toast(err.message, "error")));
    input.addEventListener("keydown", (e) => { if (e.key === "Enter") save(); });
  }

  function renderLogin() {
    refreshBtn.hidden = true;
    body.innerHTML = `
      <div class="setup-card">
        <div class="setup-emoji">🏕️</div>
        <h3>Fast geschafft</h3>
        <p>Melde dich mit deinem Microsoft-Konto an. Dann prüft das Lagerfeuer bei Mojang, dass du wirklich du bist, und deine Freunde können dich finden.</p>
        <div class="row-actions" style="justify-content:center"><button class="btn primary" id="fr-login">Mit Microsoft anmelden</button></div>
      </div>`;
    $("#fr-login", body).addEventListener("click", async () => {
      if (await login()) refresh(true);
    });
  }

  function renderError(message) {
    refreshBtn.hidden = false;
    body.innerHTML = `<div class="notice error">${esc(message)} <button class="btn small" id="fr-retry">Nochmal versuchen</button>
      <button class="btn small" data-page="settings">Adresse ändern</button></div>`;
    $("#fr-retry", body).addEventListener("click", () => refresh(true));
  }

  // ---------- Freunde ----------

  function friendRow(f) {
    const canJoin = f.online && isMultiplayer(f.server);
    return `
      <div class="friend ${f.online ? "online" : "offline"}" data-profile="${esc(f.uuid)}">
        <div class="friend-head">${headCanvas(f.name)}<span class="status-dot"></span></div>
        <div class="friend-text"><b>${esc(f.name)} <span class="badge-row">${badgeIcons(f.badges)}</span></b>
          <small title="${esc(where(f))}">${esc(where(f))}${f.playtime ? ` · ⏳ ${playtime(f.playtime)}` : ""}</small></div>
        <div class="friend-actions">
          ${canJoin ? `<button class="btn primary small" data-join="${esc(f.server)}">Mitspielen</button>` : ""}
          <button class="btn small" data-message="${esc(f.uuid)}" data-name="${esc(f.name)}" title="Nachricht schreiben">💬</button>
          <button class="btn small icon ghost-danger" data-unfriend="${esc(f.uuid)}" data-name="${esc(f.name)}" title="Freund entfernen">${ICON_X}</button>
        </div>
      </div>`;
  }

  function renderList() {
    const online = data.friends.filter((f) => f.online);
    const offline = data.friends.filter((f) => !f.online);
    const list = data.friends.length
      ? (online.length ? `<div class="friend-group">Online · ${online.length}</div>${online.map(friendRow).join("")}` : "")
        + (offline.length ? `<div class="friend-group">Offline · ${offline.length}</div>${offline.map(friendRow).join("")}` : "")
      : `<div class="empty">Noch keine Freunde am Lagerfeuer. Füg rechts jemanden mit seinem Minecraft-Namen hinzu 🔥</div>`;
    const incoming = data.incoming.length
      ? data.incoming.map((r) => `
          <div class="request">${headCanvas(r.name)}<b>${esc(r.name)}</b>
            <button class="btn primary small" data-accept="${esc(r.uuid)}" data-name="${esc(r.name)}">Annehmen</button>
            <button class="btn small icon ghost-danger" data-decline="${esc(r.uuid)}" title="Ablehnen">${ICON_X}</button></div>`).join("")
      : `<div class="side-empty">Keine neuen Anfragen.</div>`;
    const outgoing = data.outgoing.length
      ? data.outgoing.map((r) => `
          <div class="request">${headCanvas(r.name)}<b>${esc(r.name)}</b>
            <button class="btn small" data-cancel="${esc(r.uuid)}">Zurückziehen</button></div>`).join("")
      : `<div class="side-empty">Du wartest auf niemanden.</div>`;
    const me = data.me || {};
    body.innerHTML = `
      <div class="friends-layout">
        <div class="friends-list">${list}</div>
        <aside class="friends-side">
          <div class="card me-card">
            <h3>Du am Lagerfeuer</h3>
            <div class="me-stats"><div><small>Spielzeit</small><b>${playtime(me.playtime ?? state.playtime?.total)}</b></div>
              <div><small>Heute</small><b>${playtime(me.today ?? state.playtime?.today)}</b></div></div>
            <div class="badge-row big">${badgeIcons(me.badges) || `<span class="side-empty">Noch keine Abzeichen</span>`}</div>
          </div>
          <div class="card">
            <h3>Freund hinzufügen</h3>
            <p class="hint">Dein Freund braucht auch Emberleaf und muss einmal angemeldet gewesen sein.</p>
            <div class="add-row"><input class="input" id="fr-add-name" maxlength="16" placeholder="Minecraft-Name" autocomplete="off">
              <button class="btn primary" id="fr-add">Hinzufügen</button></div>
          </div>
          <div class="card"><h3>Anfragen an dich${data.incoming.length ? ` · ${data.incoming.length}` : ""}</h3>${incoming}</div>
          <div class="card"><h3>Gesendet</h3>${outgoing}</div>
          <p class="side-empty">Angemeldet als ${esc(me.name || "")}</p>
        </aside>
      </div>`;
    const input = $("#fr-add-name", body);
    $("#fr-add", body).addEventListener("click", () => add(input));
    input.addEventListener("keydown", (e) => { if (e.key === "Enter") add(input); });
    paintHeads();
  }

  function profileDialog(f) {
    const contrail = CONTRAILS[f.contrail] || CONTRAILS.white;
    const inParty = party?.party?.members.some((m) => m.uuid === f.uuid);
    const m = modal(`
      <div class="profile-head">${headCanvas(f.name, 64)}<div><h3>${esc(f.name)}</h3><p>${esc(where(f))}</p></div></div>
      <div class="me-stats"><div><small>Spielzeit gesamt</small><b>${playtime(f.playtime)}</b></div><div><small>Heute</small><b>${playtime(f.today)}</b></div></div>
      <h4>Abzeichen</h4>
      <div class="badge-list">${(f.badges || []).filter((b) => BADGES[b]).map((b) => `<div class="badge-line"><img src="assets/badges/${b}.png" alt="">
        <span><b>${esc(BADGES[b][0])}</b><small>${esc(BADGES[b][1])}</small></span></div>`).join("") || `<p class="side-empty">Noch keine Abzeichen.</p>`}</div>
      <h4>Kondensstreifen</h4><p><span class="swatch" style="background:${contrail[1]}"></span> ${esc(contrail[0])}</p>
      <div class="modal-actions">
        <button class="btn danger" data-p-remove>Entfernen</button>
        ${inParty ? "" : `<button class="btn" data-p-party>In Party einladen</button>`}
        ${f.online && isMultiplayer(f.server) ? `<button class="btn" data-p-join>Mitspielen</button>` : ""}
        <button class="btn primary" data-p-chat>Nachricht schreiben</button>
      </div>`, null, true);
    paintHeads(m.el);
    $("[data-p-chat]", m.el).addEventListener("click", () => {
      m.close();
      openDirect(f.uuid, f.name);
    });
    $("[data-p-join]", m.el)?.addEventListener("click", () => {
      m.close();
      join(f.server);
    });
    $("[data-p-party]", m.el)?.addEventListener("click", async () => {
      try {
        await api("party_action", { action: "invite", uuid: f.uuid });
        toast(`${f.name} ist in die Party eingeladen 🎉`);
        m.close();
        loadParty();
      } catch (e) {
        toast(e.message, "error");
      }
    });
    $("[data-p-remove]", m.el).addEventListener("click", async () => {
      m.close();
      if (!(await confirmDialog(`${f.name} entfernen?`, `${f.name} verschwindet aus deiner Freundesliste und du aus seiner.`, "Entfernen"))) return;
      try {
        await api("friend_remove", { uuid: f.uuid });
        toast(`${f.name} entfernt`);
      } catch (e) {
        toast(e.message, "error");
      }
      refresh();
    });
  }

  // ---------- Chats ----------

  function chatPreview(m, c) {
    const who = m.kind === "system" ? "" : m.sender === data?.me?.uuid ? "Du: " : c.kind !== "dm" ? `${c.members.find((x) => x.uuid === m.sender)?.name || "?"}: ` : "";
    const text = { image: "📷 Bild", invite: "🎮 Server-Einladung", game: `🎲 ${m.text}`, deleted: "Nachricht gelöscht" }[m.kind] ?? m.text;
    return who + text;
  }

  function chatItems() {
    const list = chats?.chats || [];
    return list.length ? list.map((c) => {
      const partner = c.kind === "dm" ? c.members.find((m) => m.uuid === c.partner) : null;
      const icon = partner ? `<span class="chat-head">${headCanvas(partner.name)}<i class="${partner.online ? "on" : ""}"></i></span>`
        : `<span class="chat-icon">${c.kind === "party" ? "🎉" : "👥"}</span>`;
      const m = c.lastMessage;
      return `<button class="chat-item ${chat?.id === c.id ? "active" : ""}" data-chat="${esc(c.id)}">${icon}
        <span class="chat-text"><b>${esc(c.name)}</b><small>${esc(m ? chatPreview(m, c) : "Noch keine Nachrichten")}</small></span>
        <span class="chat-side">${m ? shortTime(m.sent) : ""}${c.unread ? `<span class="count">${c.unread}</span>` : ""}</span></button>`;
    }).join("") : `<div class="side-empty chat-empty">Noch keine Chats. Schreib einem Freund (💬 bei Freunde) oder starte eine Gruppe.</div>`;
  }

  /** Only the list on the left (the open chat, its scroll position and what you type stay as they are). */
  function renderChatList() {
    const list = $(".chat-items", body);
    if (!list) return renderChats();
    const scroll = list.scrollTop;
    list.innerHTML = chatItems();
    list.scrollTop = scroll;
    paintHeads(list);
  }

  function renderChats() {
    body.innerHTML = `
      <div class="chat-layout">
        <aside class="chat-list">
          <div class="chat-list-head"><b>Chats</b><button class="btn small" id="new-group">+ Gruppe</button></div>
          <div class="chat-items">${chatItems()}</div>
        </aside>
        <section class="chat-main" id="chat-main">${chat ? "" : `<div class="chat-placeholder">🔥<p>Wähle links einen Chat oder schreib einem Freund.</p></div>`}</section>
      </div>`;
    paintHeads();
    $("#new-group", body).addEventListener("click", newGroupDialog);
    if (chat) renderChatMain();
  }

  function openDirect(uuid, name) {
    const me = data?.me?.uuid;
    if (!me) return;
    const id = `d${[me, uuid].sort().join("")}`;
    sub = "chats";
    syncTabs();
    openChat(id, { uuid, name });
  }

  function openChat(id, partner = null) {
    chat = { id, partner, data: null, sig: "", replyTo: null, limit: 50, stick: true };
    if ($(".chat-items", body)) {
      renderChatList();
      renderChatMain();
    } else {
      renderChats();
    }
    loadChat();
  }

  async function loadChat() {
    const current = chat;
    if (!current) return;
    let res;
    try {
      res = await api("chat_messages", { chat: current.id, limit: current.limit });
    } catch (e) {
      if (current.partner && /gibt es nicht/.test(e.message)) {
        res = { chat: { id: current.id, kind: "dm", name: current.partner.name, partner: current.partner.uuid }, messages: [], more: false,
          members: [{ uuid: data.me.uuid, name: data.me.name, read: 0 }, { uuid: current.partner.uuid, name: current.partner.name, read: 0 }] };
      } else {
        toast(e.message, "error");
        return;
      }
    }
    if (chat !== current) return;
    current.data = res;
    const sig = JSON.stringify([res.messages, res.members.map((m) => [m.uuid, m.read, m.online])]);
    if (sig !== current.sig) {
      current.sig = sig;
      renderChatMain();
    }
    const last = res.messages[res.messages.length - 1];
    const mine = res.members.find((m) => m.uuid === data.me.uuid);
    if (last && mine && last.id > mine.read) {
      mine.read = last.id;
      api("chat_read", { chat: current.id, message: last.id }).catch(() => {});
      const listed = chats?.chats.find((c) => c.id === current.id);
      if (listed && listed.unread) {
        listed.unread = 0;
        setBadge();
        $(`.chat-item[data-chat="${current.id}"] .count`, body)?.remove();
      }
    }
  }

  function renderChatMain() {
    const main = $("#chat-main", body);
    if (!main || !chat) return;
    const d = chat.data;
    const me = data.me.uuid;
    if (!d) {
      main.innerHTML = `<div class="chat-placeholder"><span class="spinner"></span></div>`;
      return;
    }
    const names = new Map(d.members.map((m) => [m.uuid, m.name]));
    const others = d.members.filter((m) => m.uuid !== me);
    const subtitle = d.chat.kind === "dm" ? (others[0] ? where(others[0]) : "") : `${d.members.length} Mitglieder · ${d.members.filter((m) => m.online).length} online`;
    const box = $(".messages", main);
    const atBottom = !box || box.scrollHeight - box.scrollTop - box.clientHeight < 80;
    let html = d.more ? `<button class="btn small older" data-older>Ältere Nachrichten</button>` : "";
    if (!d.messages.length) html += `<div class="chat-placeholder small">👋<p>Schreib die erste Nachricht!</p></div>`;
    let lastDay = "";
    let lastSender = null;
    for (const m of d.messages) {
      const day = new Date(m.sent).toDateString();
      if (day !== lastDay) {
        lastDay = day;
        lastSender = null;
        html += `<div class="day">${esc(new Date(m.sent).toLocaleDateString("de-DE", { weekday: "long", day: "numeric", month: "long" }))}</div>`;
      }
      if (m.kind === "system") {
        html += `<div class="system">${esc(m.text)}</div>`;
        lastSender = null;
        continue;
      }
      const mine = m.sender === me;
      const cont = lastSender === m.sender;
      lastSender = m.sender;
      html += `<div class="msg ${mine ? "mine" : ""} ${cont ? "cont" : ""}">${!mine && d.chat.kind !== "dm" ? `<span class="msg-head">${cont ? "" : headCanvas(names.get(m.sender) || "?")}</span>` : ""}
        <div>${bubble(m, mine, cont, names, d.chat.kind)}${reactions(m)}</div></div>`;
    }
    const last = d.messages[d.messages.length - 1];
    if (last && last.sender === me && last.kind !== "system") {
      const readers = others.filter((o) => o.read >= last.id).length;
      html += `<div class="receipt ${readers ? "read" : ""}">${readers ? (d.chat.kind === "dm" ? "✓✓ Gelesen" : `✓✓ Gelesen von ${readers}`) : "✓ Gesendet"}</div>`;
    }
    const reply = chat.replyTo
      ? `<div class="replying"><span class="quote"><b>${esc(names.get(chat.replyTo.sender) || "?")}</b>${esc(chat.replyTo.kind === "text" ? chat.replyTo.text : "…")}</span>
          <button class="btn small icon" data-reply-cancel>${ICON_X}</button></div>` : "";
    const keep = $("#chat-text", main)?.value || "";
    main.innerHTML = `
      <div class="chat-head-row">${d.chat.kind === "dm" && others[0] ? headCanvas(others[0].name) : `<span class="chat-icon">${d.chat.kind === "party" ? "🎉" : "👥"}</span>`}
        <div><b>${esc(d.chat.name)}</b><small>${esc(subtitle)}</small></div>
        ${d.chat.kind === "group" ? `<button class="btn small" data-group-settings>Gruppe</button>` : ""}</div>
      <div class="messages">${html}</div>
      <div class="composer">${reply}
        <div class="composer-row">
          <button class="btn small" data-attach title="Bild senden">📷</button>
          <button class="btn small" data-games title="Spiel starten">🎲</button>
          <button class="btn small" data-invite title="Auf einen Server einladen">🎮</button>
          <textarea class="input" id="chat-text" rows="1" maxlength="1000" placeholder="Nachricht an ${esc(d.chat.name)} …"></textarea>
          <button class="btn primary" data-send>Senden</button>
        </div>
      </div>`;
    const text = $("#chat-text", main);
    text.value = keep;
    text.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        sendText();
      }
    });
    const messages = $(".messages", main);
    if (atBottom || chat.stick) {
      messages.scrollTop = messages.scrollHeight;
      chat.stick = false;
    }
    paintHeads(main);
  }

  function bubble(m, mine, cont, names, kind) {
    const who = !mine && kind !== "dm" && !cont ? `<span class="who">${esc(names.get(m.sender) || "?")}</span>` : "";
    const time = `<span class="time">${clock(m.sent)}</span>`;
    if (m.kind === "deleted") return `<div class="bubble deleted" data-msg="${m.id}">${who}Nachricht gelöscht${time}</div>`;
    const quote = m.reply ? `<div class="quote"><b>${esc(names.get(m.reply.sender) || "?")}</b>${esc(m.reply.missing ? "nicht mehr da"
      : m.reply.kind === "image" ? "📷 Bild" : m.reply.text || "…")}</div>` : "";
    let content = "";
    if (m.kind === "text") content = esc(m.text);
    else if (m.kind === "image") {
      content = `<img class="photo" src="${esc(`${(state.settings.friends_server || "").replace(/\/+$/, "")}/v1/image/${m.data?.image}`)}" alt="Bild">${m.text ? `<div>${esc(m.text)}</div>` : ""}`;
    } else if (m.kind === "invite") {
      content = `<div class="invite">🎮 <b>${mine ? "Du hast eingeladen" : "Komm auf meinen Server!"}</b><code>${esc(m.data?.server)}</code>
        ${m.text ? `<span>${esc(m.text)}</span>` : ""}${mine ? "" : `<button class="btn small primary" data-join="${esc(m.data?.server)}">Mitspielen</button>`}</div>`;
    } else if (m.kind === "game") content = gameHtml(m, names);
    return `<div class="bubble" data-msg="${m.id}">${who}${quote}${content}${time}</div>`;
  }

  function reactions(m) {
    const list = Object.entries(m.reactions || {}).filter(([, u]) => u.length);
    if (!list.length) return "";
    return `<div class="reactions">${list.map(([k, users]) => `<button class="reaction ${users.includes(data.me.uuid) ? "on" : ""}" data-react="${esc(k)}"
      data-id="${m.id}">${REACTIONS[k] || "?"} ${users.length}</button>`).join("")}</div>`;
  }

  function gameHtml(m, names) {
    const d = m.data;
    if (!d) return "";
    const me = data.me.uuid;
    const seat = d.players.indexOf(me);
    const canJoin = d.players[1] === null && d.players[0] !== me;
    const myTurn = !d.winner && ((seat >= 0 && seat === d.turn) || (canJoin && d.turn === 1));
    const nameOf = (u) => (u === me ? "Du" : names.get(u) || "?");
    let status;
    if (d.winner === "draw") status = "Unentschieden 🤝";
    else if (d.winner) status = d.winner === me ? "Du hast gewonnen! 🏆" : `${nameOf(d.winner)} hat gewonnen`;
    else if (myTurn) status = "Du bist dran";
    else if (d.players[d.turn]) status = `${nameOf(d.players[d.turn])} ist dran`;
    else status = "Wer spielt mit? Einfach ziehen";
    const cls = (v) => (v === "x" ? "x" : v === "o" ? "o" : "");
    const cells = [...d.board];
    const board = d.game === "ttt"
      ? `<div class="board ttt">${cells.map((v, i) => `<button class="cell ${cls(v)}" data-move="${i}" data-id="${m.id}" ${myTurn && v === "." ? "" : "disabled"}>${v === "x" ? "✕" : v === "o" ? "○" : ""}</button>`).join("")}</div>`
      : `<div class="board c4">${cells.map((v, i) => `<button class="cell ${cls(v)} ${d.last === i ? "last" : ""}" data-move="${i % 7}" data-id="${m.id}" ${myTurn ? "" : "disabled"}></button>`).join("")}</div>`;
    return `<div class="game"><b>🎲 ${d.game === "ttt" ? "Tic-Tac-Toe" : "Vier gewinnt"}</b><div class="game-status">${esc(status)}</div>${board}</div>`;
  }

  async function send(payload) {
    const current = chat;
    if (!current || current.sending) return false;
    current.sending = true;
    try {
      const body = { ...payload };
      if (current.replyTo) body.reply = current.replyTo.id;
      if (current.partner && !current.data?.messages.length) body.to = current.partner.uuid;
      else body.chat = current.id;
      const res = await api("chat_send", body);
      if (res.chat) current.id = res.chat;
      current.partner = null;
      current.replyTo = null;
      current.stick = true;
      await loadChat();
      loadChats();
      return true;
    } catch (e) {
      toast(e.message, "error");
      return false;
    } finally {
      current.sending = false;
    }
  }

  async function sendText() {
    const text = $("#chat-text", body);
    const value = text?.value.trim();
    if (!value) return;
    if (await send({ text: value })) {
      const fresh = $("#chat-text", body);
      if (fresh) fresh.value = "";
    }
  }

  async function ensureChat() {
    if (chat?.partner && !chat.data?.messages.length) await send({ text: "👋" });
  }

  function shrinkImage(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        if (file.type === "image/gif" && file.size <= 440 * 1024) {
          const img = new Image();
          img.onload = () => resolve({ dataUrl: reader.result, w: img.width, h: img.height });
          img.src = reader.result;
          return;
        }
        const img = new Image();
        img.onload = () => {
          let scale = Math.min(1, 1280 / Math.max(img.width, img.height));
          for (let attempt = 0; attempt < 6; attempt++) {
            const w = Math.max(1, Math.round(img.width * scale));
            const h = Math.max(1, Math.round(img.height * scale));
            const canvas = document.createElement("canvas");
            canvas.width = w;
            canvas.height = h;
            canvas.getContext("2d").drawImage(img, 0, 0, w, h);
            const dataUrl = canvas.toDataURL("image/jpeg", Math.max(0.5, 0.84 - attempt * 0.07));
            if (dataUrl.length * 0.75 <= 440 * 1024) return resolve({ dataUrl, w, h });
            scale *= 0.75;
          }
          reject(new Error("Das Bild ist zu groß."));
        };
        img.onerror = () => reject(new Error("Das Bild lässt sich nicht öffnen."));
        img.src = reader.result;
      };
      reader.onerror = () => reject(new Error("Das Bild lässt sich nicht lesen."));
      reader.readAsDataURL(file);
    });
  }

  function pickImage() {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/png,image/jpeg,image/webp,image/gif";
    input.onchange = async () => {
      const file = input.files[0];
      if (!file) return;
      try {
        const image = await shrinkImage(file);
        const up = await api("chat_image", { data_url: image.dataUrl });
        await ensureChat();
        await send({ kind: "image", data: { image: up.id, w: image.w, h: image.h } });
      } catch (e) {
        toast(e.message, "error");
      }
    };
    input.click();
  }

  function gamesDialog() {
    const m = modal(`<h3>🎲 Spiel starten</h3><p>Dein Gegenüber zieht im Chat – im Launcher, im Spiel oder auf dem Handy.</p>
      <div class="row-actions"><button class="btn primary" data-g="ttt">❌⭕ Tic-Tac-Toe</button><button class="btn primary" data-g="c4">🔴🟡 Vier gewinnt</button></div>
      <div class="modal-actions"><button class="btn" data-close>Abbrechen</button></div>`);
    $$("[data-g]", m.el).forEach((b) => b.addEventListener("click", async () => {
      m.close();
      await ensureChat();
      try {
        await api("chat_game", { chat: chat.id, game: b.dataset.g });
        chat.stick = true;
        loadChat();
      } catch (e) {
        toast(e.message, "error");
      }
    }));
  }

  function inviteDialog() {
    const last = state.settings.last_server || "";
    const m = modal(`<h3>🎮 Auf einen Server einladen</h3><p>Die Adresse, die ihr in Minecraft unter „Direkt verbinden“ eingebt.</p>
      <input class="input" id="inv-server" maxlength="120" placeholder="z. B. play.example.net" value="${esc(last)}">
      <div class="modal-actions"><button class="btn" data-close>Abbrechen</button><button class="btn primary" data-inv>Einladung senden</button></div>`);
    $("[data-inv]", m.el).addEventListener("click", async () => {
      const server = $("#inv-server", m.el).value.trim();
      if (!server) return;
      m.close();
      await send({ kind: "invite", data: { server } });
    });
  }

  function messageDialog(m) {
    const mine = m.sender === data.me.uuid;
    const dlg = modal(`<h3>Nachricht</h3>
      <div class="reaction-bar">${Object.entries(REACTIONS).map(([k, e]) => `<button class="${(m.reactions?.[k] || []).includes(data.me.uuid) ? "on" : ""}" data-r="${k}">${e}</button>`).join("")}</div>
      <div class="modal-actions">
        ${mine ? `<button class="btn danger" data-del>Löschen</button>` : ""}
        ${m.kind === "text" ? `<button class="btn" data-copy>Kopieren</button>` : ""}
        <button class="btn primary" data-reply>Antworten</button>
      </div>`);
    $$("[data-r]", dlg.el).forEach((b) => b.addEventListener("click", async () => {
      dlg.close();
      await api("chat_react", { message: m.id, emoji: b.dataset.r }).catch((e) => toast(e.message, "error"));
      loadChat();
    }));
    $("[data-reply]", dlg.el).addEventListener("click", () => {
      dlg.close();
      chat.replyTo = m;
      renderChatMain();
      $("#chat-text", body)?.focus();
    });
    $("[data-copy]", dlg.el)?.addEventListener("click", () => {
      navigator.clipboard?.writeText(m.text).then(() => toast("Kopiert")).catch(() => {});
      dlg.close();
    });
    $("[data-del]", dlg.el)?.addEventListener("click", async () => {
      dlg.close();
      if (!(await confirmDialog("Nachricht löschen?", "Sie verschwindet für alle im Chat.", "Löschen"))) return;
      await api("chat_delete", { message: m.id }).catch((e) => toast(e.message, "error"));
      loadChat();
    });
  }

  function friendChecklist(exclude = []) {
    const list = (data?.friends || []).filter((f) => !exclude.includes(f.uuid));
    if (!list.length) return `<p class="side-empty">Keine Freunde zum Auswählen.</p>`;
    return `<div class="check-list">${list.map((f) => `<label class="check-item"><input type="checkbox" value="${esc(f.uuid)}">${headCanvas(f.name)}<span>${esc(f.name)}</span></label>`).join("")}</div>`;
  }

  function newGroupDialog() {
    const m = modal(`<h3>👥 Neue Gruppe</h3><p>Bis zu 20 Leute, nur Freunde.</p>
      <input class="input" id="g-name" maxlength="32" placeholder="Name der Gruppe">
      <h4>Mitglieder</h4>${friendChecklist()}
      <div class="modal-actions"><button class="btn" data-close>Abbrechen</button><button class="btn primary" data-create>Erstellen</button></div>`);
    paintHeads(m.el);
    $("[data-create]", m.el).addEventListener("click", async () => {
      const members = $$("input:checked", m.el).map((i) => i.value);
      try {
        const res = await api("chat_group", { name: $("#g-name", m.el).value, members });
        m.close();
        await loadChats();
        openChat(res.chat);
      } catch (e) {
        toast(e.message, "error");
      }
    });
  }

  function groupDialog() {
    const d = chat?.data;
    if (!d) return;
    const m = modal(`<h3>👥 ${esc(d.chat.name)}</h3>
      <div class="member-list">${d.members.map((x) => `<div class="request">${headCanvas(x.name)}<b>${esc(x.name)}</b><small>${esc(where(x))}</small></div>`).join("")}</div>
      <h4>Freunde hinzufügen</h4>${friendChecklist(d.members.map((x) => x.uuid))}
      <div class="add-row"><input class="input" id="g-rename" maxlength="32" value="${esc(d.chat.name)}"><button class="btn" data-rename>Umbenennen</button></div>
      <div class="modal-actions"><button class="btn danger" data-leave>Gruppe verlassen</button><button class="btn" data-close>Schließen</button><button class="btn primary" data-add>Hinzufügen</button></div>`, null, true);
    paintHeads(m.el);
    $("[data-add]", m.el).addEventListener("click", async () => {
      for (const box of $$("input:checked", m.el)) await api("chat_group_add", { chat: d.chat.id, uuid: box.value }).catch((e) => toast(e.message, "error"));
      m.close();
      loadChat();
    });
    $("[data-rename]", m.el).addEventListener("click", async () => {
      await api("chat_group_rename", { chat: d.chat.id, name: $("#g-rename", m.el).value }).catch((e) => toast(e.message, "error"));
      m.close();
      loadChat();
      loadChats();
    });
    $("[data-leave]", m.el).addEventListener("click", async () => {
      m.close();
      if (!(await confirmDialog("Gruppe verlassen?", "Du siehst die Nachrichten danach nicht mehr.", "Verlassen"))) return;
      await api("chat_group_leave", { chat: d.chat.id }).catch((e) => toast(e.message, "error"));
      chat = null;
      loadChats();
    });
  }

  // ---------- Party ----------

  function renderParty() {
    const p = party?.party;
    const invites = party?.partyInvites || [];
    const me = data.me.uuid;
    let html = invites.map((i) => `<div class="card party-invite"><h3>🎉 ${esc(i.leaderName)} lädt dich in die Party ein</h3>
      <div class="row-actions"><button class="btn primary" data-pjoin="${esc(i.party)}">Beitreten</button><button class="btn" data-pdecline="${esc(i.party)}">Ablehnen</button></div></div>`).join("");
    if (!p) {
      html += `<div class="setup-card"><div class="setup-emoji">🎉</div><h3>Zusammen von Server zu Server</h3>
        <p>In einer Party seht ihr, wo der Leiter spielt. Wechselt er den Server, kommt ihr im Spiel mit einem Klick hinterher (oder automatisch:
        Emberleaf-Einstellungen → Chat → „Party: Leiter folgen“). Dazu gibt es einen Party-Chat.</p>
        <div class="row-actions" style="justify-content:center"><button class="btn primary" data-pcreate>Party starten</button></div></div>`;
    } else {
      const lead = p.leader === me;
      html += `<div class="friends-layout"><div class="friends-list">${p.members.map((m) => `
        <div class="friend ${m.online ? "online" : "offline"}"><div class="friend-head">${headCanvas(m.name)}<span class="status-dot"></span></div>
          <div class="friend-text"><b>${m.uuid === p.leader ? "👑 " : ""}${esc(m.name)}${m.uuid === me ? " (du)" : ""}</b><small>${esc(where(m))}</small></div>
          <div class="friend-actions">${!lead && m.uuid === p.leader && m.online && isMultiplayer(m.server) ? `<button class="btn primary small" data-join="${esc(m.server)}">Hinterher</button>` : ""}
            ${lead && m.uuid !== me ? `<button class="btn small" data-pkick="${esc(m.uuid)}">Entfernen</button>` : ""}</div></div>`).join("")}</div>
        <aside class="friends-side"><div class="card"><h3>Party</h3><p class="hint">${lead ? "Du leitest die Party." : "Der Leiter bestimmt, wo es hingeht."}</p>
          <div class="row-actions"><button class="btn primary" data-pchat>💬 Party-Chat</button><button class="btn danger" data-pleave>Verlassen</button></div></div>
          <div class="card"><h3>Freunde einladen</h3>${(data.friends || []).filter((f) => !p.members.some((m) => m.uuid === f.uuid))
            .map((f) => `<div class="request">${headCanvas(f.name)}<b>${esc(f.name)}</b><button class="btn small" data-pinvite="${esc(f.uuid)}">Einladen</button></div>`).join("")
            || `<div class="side-empty">Alle deine Freunde sind dabei.</div>`}</div></aside></div>`;
    }
    body.innerHTML = html;
    paintHeads();
  }

  async function loadParty() {
    try {
      party = await api("party");
    } catch {
      party = null;
    }
    setBadge();
    if (state.page === "friends" && sub === "party" && data?.state === "ok") renderParty();
  }

  async function partyAction(action, extra = {}, done = "") {
    try {
      const res = await api("party_action", { action, ...extra });
      if (res && "party" in res) party = res;
      if (done) toast(done);
    } catch (e) {
      toast(e.message, "error");
    }
    await loadParty();
    loadChats();
  }

  // ---------- Handy verbinden ----------

  async function phoneDialog() {
    let countdown = 0;
    const m = modal(`<h3>📱 Lagerfeuer aufs Handy</h3>
      <p>Mit der Lagerfeuer-App chattest du auch unterwegs, siehst wer gerade spielt, startest Partys und schaust dir deine Spielzeit an.</p>
      <div class="pair-box" id="pair-box"><span class="spinner"></span></div>
      <div class="modal-actions"><button class="btn" data-close>Fertig</button></div>`, () => clearInterval(countdown), true);
    const box = $("#pair-box", m.el);
    async function load() {
      clearInterval(countdown);
      box.innerHTML = `<span class="spinner"></span>`;
      try {
        const pair = await api("pair_phone");
        box.innerHTML = `<img class="pair-qr" src="${pair.qr}" alt="QR-Code">
          <div class="pair-info">
            <div class="pair-code">${esc(pair.code.slice(0, 3))} ${esc(pair.code.slice(3))}</div>
            <ol>
              <li>Scanne den QR-Code mit der Handy-Kamera – fertig.</li>
              <li>Oder öffne <code>${esc(pair.app)}</code> und tippe den Code ein.</li>
              <li>Tipp: „Zum Startbildschirm hinzufügen“, dann ist es eine richtige App.</li>
            </ol>
            <p class="hint">Der Code gilt <b id="pair-left">5:00</b> Minuten und nur einmal. <button class="btn small" id="pair-new">Neuer Code</button></p>
          </div>`;
        $("#pair-new", box).addEventListener("click", load);
        countdown = setInterval(() => {
          const left = Math.max(0, pair.expires - Math.floor(Date.now() / 1000));
          const el = $("#pair-left", box);
          if (el) el.textContent = `${Math.floor(left / 60)}:${pad(left % 60)}`;
          if (left <= 0) {
            clearInterval(countdown);
            box.querySelector(".pair-qr")?.classList.add("expired");
          }
        }, 1000);
      } catch (e) {
        box.innerHTML = `<div class="notice error">${esc(e.message)}</div>`;
      }
    }
    load();
  }

  // ---------- Laden & Ereignisse ----------

  function syncTabs() {
    const ok = data?.state === "ok";
    tabsEl.hidden = !ok;
    phoneBtn.hidden = !ok;
    $$("[data-ftab]", tabsEl).forEach((b) => b.classList.toggle("active", b.dataset.ftab === sub));
  }

  function render() {
    if (!data) return;
    syncTabs();
    clearInterval(chatTimer);
    if (data.state === "no_server") return renderSetup();
    if (data.state === "no_login") return renderLogin();
    if (data.state === "error") return renderError(data.error);
    refreshBtn.hidden = false;
    if (sub === "chats") {
      renderChats();
      if (!chats) loadChats();
      chatTimer = setInterval(() => {
        if (document.hidden || state.page !== "friends" || sub !== "chats") return;
        loadChats();
        loadChat();
      }, CHAT_POLL_MS);
    } else if (sub === "party") {
      renderParty();
      loadParty();
    } else {
      renderList();
    }
  }

  let chatsSig = "";
  async function loadChats() {
    try {
      chats = await api("chats");
    } catch {
      return;
    }
    setBadge();
    const sig = JSON.stringify(chats);
    if (sig === chatsSig) return;
    chatsSig = sig;
    if (state.page === "friends" && sub === "chats" && data?.state === "ok") renderChatList();
  }

  async function refresh(show = false) {
    if (loading) return loading;
    if (show && !data) body.innerHTML = `<div class="skeleton"></div>`.repeat(3);
    loading = api("friends")
      .then((d) => { data = d; })
      .catch((e) => { data = { state: "error", error: e.message }; })
      .finally(() => { loading = null; });
    await loading;
    if (data.state === "ok") {
      await Promise.all([loadChats(), loadParty()]);
    }
    setBadge();
    onFriends?.(data.state === "ok" ? data.friends : []);
    if (state.page === "friends") render();
  }

  async function add(input) {
    const name = input.value.trim();
    if (!name) return input.focus();
    const btn = $("#fr-add", body);
    setBusy(btn, true, "Sucht …");
    try {
      const res = await api("friend_add", { name });
      toast(res.status === "friends" ? `${res.friend.name} ist jetzt dein Freund 🎉` : `Anfrage an ${res.friend.name} geschickt ✉️`);
      await refresh();
    } catch (e) {
      toast(e.message, "error");
      setBusy(btn, false);
    }
  }

  tabsEl.addEventListener("click", (e) => {
    const b = e.target.closest("[data-ftab]");
    if (!b) return;
    sub = b.dataset.ftab;
    render();
  });
  phoneBtn.addEventListener("click", phoneDialog);

  body.addEventListener("click", async (e) => {
    const t = e.target;
    const joinBtn = t.closest("[data-join]");
    if (joinBtn) return join(joinBtn.dataset.join);
    const chatItem = t.closest("[data-chat]");
    if (chatItem) return openChat(chatItem.dataset.chat);
    const msgBtn = t.closest("[data-message]");
    if (msgBtn) return openDirect(msgBtn.dataset.message, msgBtn.dataset.name);
    if (t.closest("[data-send]")) return sendText();
    if (t.closest("[data-attach]")) return pickImage();
    if (t.closest("[data-games]")) return gamesDialog();
    if (t.closest("[data-invite]")) return inviteDialog();
    if (t.closest("[data-group-settings]")) return groupDialog();
    if (t.closest("[data-reply-cancel]")) {
      chat.replyTo = null;
      return renderChatMain();
    }
    if (t.closest("[data-older]")) {
      chat.limit = 100;
      return loadChat();
    }
    const move = t.closest("[data-move]");
    if (move) {
      move.disabled = true;
      await api("chat_move", { message: Number(move.dataset.id), cell: Number(move.dataset.move) }).catch((err) => toast(err.message, "error"));
      return loadChat();
    }
    const reaction = t.closest("[data-react]");
    if (reaction) {
      await api("chat_react", { message: Number(reaction.dataset.id), emoji: reaction.dataset.react }).catch((err) => toast(err.message, "error"));
      return loadChat();
    }
    if (t.matches("img.photo")) {
      return modal(`<img class="photo-big" src="${esc(t.src)}" alt="Bild"><div class="modal-actions"><button class="btn" data-close>Schließen</button></div>`, null, true);
    }
    const bubbleEl = t.closest("[data-msg]");
    if (bubbleEl && chat?.data) {
      const m = chat.data.messages.find((x) => x.id === Number(bubbleEl.dataset.msg));
      if (m && m.kind !== "deleted") return messageDialog(m);
    }
    if (t.closest("[data-pcreate]")) return partyAction("create", {}, "Party gestartet 🎉");
    const pjoin = t.closest("[data-pjoin]");
    if (pjoin) return partyAction("join", { party: pjoin.dataset.pjoin }, "Du bist in der Party 🎉");
    const pdecline = t.closest("[data-pdecline]");
    if (pdecline) return partyAction("decline", { party: pdecline.dataset.pdecline });
    const pinvite = t.closest("[data-pinvite]");
    if (pinvite) return partyAction("invite", { uuid: pinvite.dataset.pinvite }, "Eingeladen 🎉");
    const pkick = t.closest("[data-pkick]");
    if (pkick) return partyAction("kick", { uuid: pkick.dataset.pkick });
    if (t.closest("[data-pleave]")) {
      if (await confirmDialog("Party verlassen?", "Du kannst später wieder eingeladen werden.", "Verlassen")) partyAction("leave");
      return;
    }
    if (t.closest("[data-pchat]") && party?.party) {
      sub = "chats";
      syncTabs();
      return openChat(party.party.chat);
    }
    const accept = t.closest("[data-accept]");
    const decline = t.closest("[data-decline], [data-cancel]");
    const unfriend = t.closest("[data-unfriend]");
    try {
      if (accept) {
        accept.disabled = true;
        await api("friend_accept", { uuid: accept.dataset.accept });
        toast(`${accept.dataset.name} ist jetzt dein Freund 🎉`);
      } else if (decline) {
        decline.disabled = true;
        await api("friend_remove", { uuid: decline.dataset.decline || decline.dataset.cancel });
      } else if (unfriend) {
        const name = unfriend.dataset.name;
        if (!(await confirmDialog(`${name} entfernen?`, `${name} verschwindet aus deiner Freundesliste und du aus seiner.`, "Entfernen"))) return;
        await api("friend_remove", { uuid: unfriend.dataset.unfriend });
        toast(`${name} entfernt`);
      } else {
        const profile = t.closest("[data-profile]");
        if (profile && !t.closest("button")) {
          const f = data.friends.find((x) => x.uuid === profile.dataset.profile);
          if (f) profileDialog(f);
        }
        return;
      }
      await refresh();
    } catch (err) {
      toast(err.message, "error");
      refresh();
    }
  });
  refreshBtn.addEventListener("click", async () => {
    setBusy(refreshBtn, true, "Lädt …");
    await refresh();
    if (chat) await loadChat();
    setBusy(refreshBtn, false);
  });

  return {
    open() {
      render();
      refresh(true);
      clearInterval(timer);
      timer = setInterval(() => { if (!document.hidden) refresh(); }, REFRESH_MS);
    },
    close() {
      clearInterval(timer);
      clearInterval(chatTimer);
    },
    /** Lädt im Hintergrund, damit neue Anfragen und Nachrichten als Zahl am Reiter auftauchen. */
    background() {
      if (state.settings.friends_server) refresh();
    },
    accountChanged() {
      heads.clear();
      data = null;
      chats = null;
      party = null;
      chat = null;
      if (state.page === "friends") refresh(true);
      else this.background();
    },
  };
}
