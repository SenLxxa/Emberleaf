import { IdleAnimation, SkinViewer } from "./vendor/esm/skinview3d.js";
import { CATEGORIES } from "./js/catalog.js";
import { Outfit, capeFrames, emoteAnimation } from "./js/cosmetics.js";
import { createFriends } from "./js/friends.js";
import { createShop } from "./js/shop.js";
import { createSkins } from "./js/skins.js";
import { createUpdates } from "./js/updates.js";
import { installCssTextures } from "./js/textures.js";
import { $, $$, api, confirmDialog, defaultSkin, drawHead, esc, modal, setBusy, toast } from "./js/ui.js";
import { createWorld } from "./js/world.js";

const LOADER_NAMES = { vanilla: "Vanilla", fabric: "Fabric", forge: "Forge", neoforge: "NeoForge" };
const TYPE_NAMES = { mod: "Mod", resourcepack: "Resourcepack", shader: "Shader" };
const SHADER_LOADERS = ["fabric", "neoforge"];
const GREETINGS = {
  morning: ["Guten Morgen", "Die Sonne geht auf, die Blätter glitzern. Bereit für ein Abenteuer?"],
  day: ["Hallo", "Die Sonne scheint, die Blätter tanzen. Bereit für ein Abenteuer?"],
  sunset: ["Guten Abend", "Das Lagerfeuer brennt schon. Zeit für eine gemütliche Runde?"],
  night: ["Gute Nacht", "Die Sterne sind draußen. Noch eine kleine Runde vor dem Schlafen?"],
};
const ICON_FOLDER = `<svg viewBox="0 0 12 12"><path d="M1 2h4l1 1h5v7H1z" fill="none" stroke="currentColor" stroke-width="1.4"/></svg>`;
const ICON_TRASH = `<svg viewBox="0 0 12 12"><path d="M2 3h8M4 3V1h4v2M3 3l1 8h4l1-8" fill="none" stroke="currentColor" stroke-width="1.4"/></svg>`;
const ICON_X = `<svg viewBox="0 0 12 12"><path d="M2 2h2v2H2zM8 2h2v2H8zM4 4h2v2H4zM6 4h2v2H6zM4 6h2v2H4zM6 6h2v2H6zM2 8h2v2H2zM8 8h2v2H8z" fill="currentColor"/></svg>`;

const state = {
  version: "",
  settings: {},
  profiles: [],
  loaders: [],
  icons: [],
  account: null,
  cosmetics: { equipped: {}, custom: [] },
  game: { state: "idle" },
  desktop: false,
  skin: null,
  page: "play",
  modsProfile: "",
  modsType: "mod",
  modsSource: "modrinth",
};
const versionCache = {};
const compact = new Intl.NumberFormat("de-DE", { notation: "compact", maximumFractionDigits: 1 });

// ---------- Tageszeit ----------

function phase() {
  const h = new Date().getHours();
  if (h >= 21 || h < 6) return "night";
  if (h >= 17) return "sunset";
  return "day";
}

function applyPhase() {
  const p = phase();
  const scene = $("#scene");
  scene.classList.remove("day", "sunset", "night");
  scene.classList.add(p);
  home.world?.setPhase(p);
}

// ---------- Startseite: 3D-Insel mit deiner Figur ----------

const home = {
  viewer: null,
  world: null,
  outfit: null,
  emoteTimer: 0,
  init() {
    const canvas = $("#world-canvas");
    const scene = $("#scene");
    this.viewer = new SkinViewer({ canvas, width: scene.clientWidth || 1200, height: scene.clientHeight || 700, fov: 34 });
    const v = this.viewer;
    v.controls.enableZoom = false;
    v.controls.enablePan = false;
    v.controls.minPolarAngle = 1.05;
    v.controls.maxPolarAngle = 1.62;
    v.controls.rotateSpeed = 0.6;
    v.globalLight.intensity = 1.7;
    v.cameraLight.intensity = 0.35;
    v.animation = new IdleAnimation();
    this.world = createWorld(v.scene);
    this.outfit = new Outfit(v);
    const place = () => {
      const w = scene.clientWidth;
      const h = scene.clientHeight;
      if (!w || !h) return;
      v.setSize(w, h);
      // Figur links von der Mitte zeigen, ohne den Drehpunkt zu verschieben
      v.camera.setViewOffset(w, h, w * 0.09, h * 0.05, w, h);
    };
    v.controls.maxDistance = 2000; // skinview3d limits this to 256, the island needs more room
    v.camera.far = 6000;
    v.camera.updateProjectionMatrix();
    // close to your figure like the Minecraft main menu: you in front, the island around and behind you
    v.camera.position.set(84, 58, 146);
    v.controls.target.set(0, 11, 0);
    v.controls.update();
    new ResizeObserver(place).observe(scene);
    place();
    canvas.addEventListener("dblclick", () => this.playEmote());
    $("#stars").innerHTML = Array.from({ length: 70 }, () =>
      `<span class="star" style="left:${(Math.random() * 100).toFixed(1)}%;top:${(Math.random() * 100).toFixed(1)}%;animation-delay:${(-Math.random() * 3).toFixed(1)}s"></span>`).join("");
  },
  async syncOutfit() {
    const equipped = state.cosmetics.equipped || {};
    for (const cat of CATEGORIES) {
      if (cat.id === "emote") continue;
      const item = equipped[cat.id] ? shop.findItem(equipped[cat.id]) : null;
      if ((this.outfit.items[cat.id]?.id || null) !== (item?.id || null) || item?.custom) await this.outfit.equip(cat.id, item);
    }
    exportOutfit().catch((e) => toast(`Outfit fürs Spiel: ${e.message}`, "warn"));
  },
  playEmote() {
    const id = state.cosmetics.equipped?.emote || "emote-wave";
    clearTimeout(this.emoteTimer);
    this.viewer.animation = emoteAnimation(id);
    this.emoteTimer = setTimeout(() => { this.viewer.animation = new IdleAnimation(); }, 3500);
  },
  setActive(on) {
    if (this.viewer) this.viewer.renderPaused = !on;
  },
};

const shop = createShop({ state, onEquippedChange: () => home.syncOutfit() });

// Schreibt das angelegte Outfit komplett aufgelöst (Voxel, Cape-Bilder …) für den Emberleaf-Client-Mod
async function exportOutfit() {
  const outfit = {};
  for (const [cat, id] of Object.entries(state.cosmetics.equipped || {})) {
    const item = shop.findItem(id);
    if (!item) continue;
    const base = { id: item.id, name: item.name };
    if (item.model) outfit[cat] = { ...base, model: true }; // the backend adds the whole model
    else if (item.voxels) outfit[cat] = { ...base, voxels: item.voxels, ground: Boolean(item.ground) };
    else if (cat === "cape") outfit[cat] = { ...base, fps: item.fps || 8, frames: (await capeFrames(item)).map((c) => c.toDataURL("image/png")) };
    else if (item.aura) outfit[cat] = { ...base, aura: item.aura };
    else if (item.minime) outfit[cat] = { ...base, minime: item.minime };
    else outfit[cat] = base;
  }
  await api("export_outfit", { outfit });
}

// ---------- Navigation ----------

function showPage(page) {
  if (state.page === "cosmetics" && page !== "cosmetics") shop.close();
  if (state.page === "friends" && page !== "friends") friendsPage.close();
  if (state.page === "skins" && page !== "skins") skinsPage.close();
  state.page = page;
  $$(".tab").forEach((t) => t.classList.toggle("active", t.dataset.page === page));
  $$(".page").forEach((p) => p.classList.toggle("active", p.id === `page-${page}`));
  home.setActive(page === "play");
  toggleProfileMenu(false);
  if (page === "play") renderPlay();
  if (page === "profiles") renderProfiles();
  if (page === "mods") openMods();
  if (page === "cosmetics") shop.open();
  if (page === "friends") friendsPage.open();
  if (page === "skins") skinsPage.open();
  if (page === "settings") { renderSettings(); updatesCard.open(); }
}

// ---------- Konto & Skin ----------

function renderAccount() {
  const name = state.account?.name || state.settings.username;
  $("#account-name").textContent = state.account ? state.account.name : (name ? `${name}` : "Anmelden");
  $("#account-dot").classList.toggle("on", Boolean(state.account));
  $("#account-dot").title = state.account ? "Mit Microsoft angemeldet" : "Nicht angemeldet";
}

async function loadSkin() {
  const name = state.account?.name || state.settings.username;
  renderAccount();
  let skin = null;
  if (name) {
    try {
      skin = await api("skin", { name });
    } catch (e) {
      toast(e.message, "error");
    }
  }
  // Without an own skin (guest, Mojang's default skin, offline) the figure wears Emberleaf's default skin,
  // otherwise the figure and every cosmetics card would stay empty.
  state.skin = skin?.skin ? skin : { ...(skin || {}), name: skin?.name || name || "", skin: defaultSkin(), model: "default", fallback: true };
  try {
    drawHead($("#account-head"), state.skin.skin);
    await home.viewer.loadSkin(state.skin.skin, { model: state.skin.model });
    await home.outfit.refreshSkin();
    shop.skinChanged();
    skinsPage.skinChanged();
  } catch (e) {
    toast(e.message, "error");
  }
  renderPlay();
}

function openLoginDialog() {
  return new Promise((resolve) => {
    let ok = false;
    if (!state.desktop) {
      modal(`<h3>Anmelden</h3><p>Die Anmeldung funktioniert nur im Emberleaf-Programm, nicht im Browser.</p>
        <div class="modal-actions"><button class="btn primary" data-close>Okay</button></div>`, () => resolve(false));
      return;
    }
    const m = modal(`
      <h3>Mit Microsoft anmelden</h3>
      <p>Es öffnet sich ein kleines Fenster von Microsoft. Emberleaf sieht dein Passwort nie, es bekommt nur die Erlaubnis, Minecraft für dich zu starten.</p>
      <div class="modal-actions"><button class="btn" data-close>Abbrechen</button><button class="btn primary" id="do-login">Anmelden</button></div>`, () => resolve(ok));
    $("#do-login", m.el).addEventListener("click", async (e) => {
      setBusy(e.currentTarget, true, "Warte auf Microsoft …");
      try {
        state.account = await api("login");
        ok = true;
        toast(`Angemeldet als ${state.account.name} 🎉`);
        m.close();
        loadSkin();
        renderSettings();
        friendsPage.accountChanged();
      } catch (err) {
        toast(err.message, "error");
        m.close();
      }
    });
  });
}

// ---------- Profile ----------

function currentProfile() {
  return state.profiles.find((p) => p.id === state.settings.last_profile) || state.profiles[0] || null;
}
const profileMeta = (p) => `${LOADER_NAMES[p.loader]} · ${p.mc_version}`;
const ramLabel = (mb) => `${(mb / 1024).toLocaleString("de-DE", { maximumFractionDigits: 1 })} GB`;

async function refreshProfiles() {
  state.profiles = await api("profiles");
}

async function selectProfile(id) {
  state.settings.last_profile = id;
  renderPlay();
  try {
    state.settings = await api("save_settings", { last_profile: id });
  } catch (e) {
    toast(e.message, "error");
  }
}

function playtimeLabel(seconds) {
  const h = Math.floor((seconds || 0) / 3600);
  const m = Math.floor(((seconds || 0) % 3600) / 60);
  return h ? `${h} h ${m} min` : `${m} min`;
}

function renderPlay() {
  const name = state.skin?.name || state.account?.name || state.settings.username;
  const p = phase();
  const [hello, sub] = GREETINGS[p === "day" && new Date().getHours() < 11 ? "morning" : p];
  $("#greeting-title").textContent = name ? `${hello}, ${name}!` : `${hello}!`;
  $("#greeting-sub").textContent = sub;
  // total playtime as counted by the Emberleaf client mod
  const pt = state.playtime;
  const playtime = $("#greeting-playtime");
  playtime.hidden = !pt || !pt.total;
  if (pt && pt.total) {
    playtime.textContent = `⏳ ${playtimeLabel(pt.total)} mit Emberleaf gespielt · heute ${playtimeLabel(pt.today)} · diese Woche ${playtimeLabel(pt.week)}`;
  }

  const prof = currentProfile();
  const icon = $("#pp-icon");
  if (prof) {
    icon.dataset.loader = prof.loader;
    icon.textContent = prof.icon;
    $("#pp-name").textContent = prof.name;
    $("#pp-meta").textContent = profileMeta(prof);
  } else {
    delete icon.dataset.loader;
    icon.textContent = "🍁";
    $("#pp-name").textContent = "Noch kein Profil";
    $("#pp-meta").textContent = "Erstell dir eins";
  }
  const chips = [];
  if (prof) {
    chips.push(`🧩 ${prof.content_count} ${prof.content_count === 1 ? "Inhalt" : "Inhalte"}`);
    if (prof.voicechat) chips.push("🎙️ Voice Chat");
    chips.push(`🧠 ${ramLabel(state.settings.ram_mb)}`);
  }
  $("#pc-chips").innerHTML = chips.map((c) => `<span class="chip">${esc(c)}</span>`).join("");
  $("#scene-foot").textContent = `Emberleaf v${state.version}`;
  renderGameState();
}

function renderGameState() {
  const g = state.game || { state: "idle" };
  const prof = currentProfile();
  const btn = $("#play-btn");
  const busy = g.state === "installing";
  const running = g.state === "running";
  btn.disabled = busy;
  btn.classList.toggle("running", running);
  $("#play-title").textContent = !prof ? "Profil erstellen" : busy ? "Lädt …" : running ? "Läuft" : "Spielen";
  $("#play-sub").textContent = !prof ? "Dauert nur ein paar Sekunden" : running ? "Klick zum Beenden"
    : state.account ? profileMeta(prof) : offlinePlay() ? `${profileMeta(prof)} · Offline` : `${profileMeta(prof)} · Anmeldung nötig`;
  const bar = $("#play-progress");
  bar.hidden = !busy;
  bar.classList.toggle("indeterminate", busy && !g.max);
  $("#play-progress-bar").style.width = g.max ? `${Math.min(100, (g.progress / g.max) * 100)}%` : "";
  const status = $("#pc-status");
  status.hidden = !(busy || running);
  status.textContent = g.status || "";
}

// ohne Microsoft-Anmeldung startet der Launcher (nur aus dem Quellcode, nicht die exe) offline mit dem eingetragenen Namen
const offlinePlay = () => !state.account && state.offline_play && !!state.settings?.username;

let pollTimer = 0;
function startPolling() {
  clearInterval(pollTimer);
  renderGameState();
  pollTimer = setInterval(async () => {
    try {
      state.game = await api("game_status");
    } catch {
      return;
    }
    renderGameState();
    if (!["installing", "running"].includes(state.game.state)) {
      clearInterval(pollTimer);
      if (state.game.state === "error") toast(state.game.error, "error");
      // the mod counted new playtime while the game ran
      api("playtime").then((p) => { state.playtime = p; }).catch(() => {}).finally(() => refreshProfiles().then(renderPlay));
    }
  }, 700);
}

async function onPlay() {
  const prof = currentProfile();
  if (!prof) return openCreateProfile();
  if (state.game.state === "running") {
    if (await confirmDialog("Minecraft beenden?", "Nicht gespeicherte Fortschritte in Einzelspieler-Welten können verloren gehen.", "Beenden")) {
      state.game = await api("stop_game");
    }
    return;
  }
  if (!state.account && !offlinePlay() && !(await openLoginDialog())) return;
  try {
    state.game = await api("play", { profile_id: prof.id });
    startPolling();
  } catch (e) {
    toast(e.message, "error");
  }
}

// "Mitspielen" bei einem Freund: startet das ausgewählte Profil und verbindet direkt mit dem Server
async function joinServer(server) {
  const prof = currentProfile();
  if (!prof) return openCreateProfile();
  if (["installing", "running"].includes(state.game.state)) {
    return toast("Minecraft läuft schon. Beende es zuerst oder tritt dem Server im Spiel bei.", "warn");
  }
  if (!state.account && !offlinePlay() && !(await openLoginDialog())) return;
  try {
    state.game = await api("play", { profile_id: prof.id, server });
    toast(`Startet „${prof.name}“ und verbindet mit ${server} …`);
    showPage("play");
    startPolling();
  } catch (e) {
    toast(e.message, "error");
  }
}

// online friends stand on the island next to you
let friendsShown = "";
async function showFriendsOnIsland(friends) {
  const online = friends.filter((f) => f.online).slice(0, 4);
  const key = online.map((f) => f.uuid).join(",");
  if (key === friendsShown) return;
  friendsShown = key;
  const list = await Promise.all(online.map(async (f) => {
    const s = await api("skin", { name: f.name }).catch(() => null);
    return s?.skin ? { name: f.name, skin: s.skin, model: s.model } : null;
  }));
  home.world?.setFriends(list.filter(Boolean));
}

// "Neu in Emberleaf": the sparkle button in the title bar (with a dot until it was opened once)
const NEWS_VERSION = "2026-11-05";
function showNews() {
  try { localStorage.setItem("emberleaf.news", NEWS_VERSION); } catch { /* private mode */ }
  $("#news-dot").hidden = true;
  modal(`
    <h3>Neu in Emberleaf ✨</h3>
    <ul class="news-list">
      <li><b>Eigenes Texturenpaket</b> – buschige Blätter, 3D-Laternen, würfelige Sonne und Mond, dunkle Menüs und ein eigenes Buch für jede Verzauberung. Wird beim ersten Spielstart auf deinem PC gebaut.</li>
      <li><b>Live-Hauptmenü</b> – Höhle, Nether, Oberwelt und End, live gerendert mit Fackel- und Lavalicht.</li>
      <li><b>Emberleaf Glut</b> – eigener Shader. Iris wird automatisch installiert.</li>
      <li><b>Minimap &amp; HUD</b> – alles im Emberleaf-Menü (Rechte Shift-Taste).</li>
      <li><b>Skins</b> – speichern und mit einem Klick wechseln, hier und im Spiel.</li>
      <li><b>Auto-Updates</b> – neue Emberleaf-Versionen kommen von der Emberleaf-Webseite zu allen Freunden, mit Meldung „Jetzt neu starten“.</li>
    </ul>
    <div class="modal-actions"><button class="btn primary" data-close>Cool!</button></div>`);
}
$("#news-btn").addEventListener("click", showNews);
// "Webseite": öffnet die Emberleaf-Seite im Browser, mit dem eigenen Konto schon angemeldet
$("#web-btn").addEventListener("click", async (e) => {
  const btn = e.currentTarget;
  btn.disabled = true;
  try {
    await api("open_website");
    toast("Die Emberleaf-Webseite geht im Browser auf.");
  } catch (err) {
    toast(err.message, "error");
  } finally {
    setTimeout(() => { btn.disabled = false; }, 1500);
  }
});
try { $("#news-dot").hidden = localStorage.getItem("emberleaf.news") === NEWS_VERSION; } catch { $("#news-dot").hidden = false; }
const friendsPage = createFriends({ state, login: openLoginDialog, join: joinServer, onFriends: showFriendsOnIsland });
const skinsPage = createSkins({ state, login: openLoginDialog, onApplied: () => loadSkin() });
const updatesCard = createUpdates({ state });

function toggleProfileMenu(open) {
  const menu = $("#profile-menu");
  const show = open ?? menu.hidden;
  menu.hidden = !show;
  if (!show) return;
  const cur = currentProfile();
  menu.innerHTML = state.profiles.map((p) => `
    <button class="pm-item ${p.id === cur?.id ? "active" : ""}" data-select-profile="${p.id}">
      <span class="pp-icon" data-loader="${p.loader}">${esc(p.icon)}</span>
      <span class="pp-text"><b>${esc(p.name)}</b><small>${esc(profileMeta(p))}</small></span>
    </button>`).join("") + `<button class="pm-new" data-new-profile>+ Neues Profil</button>`;
}

function openCreateProfile() {
  toggleProfileMenu(false);
  let loader = "fabric";
  let icon = state.icons[0];
  const m = modal(`
    <h3>Neues Profil</h3>
    <p>Jedes Profil hat eigene Mods, Welten und Einstellungen.</p>
    <div class="field"><label for="np-name">Name</label><input class="input" id="np-name" maxlength="40" placeholder="z.B. Cozy Survival" autocomplete="off"></div>
    <div class="field"><label>Mod-Loader</label><div class="loader-pick">${state.loaders.map((l) => `
      <button class="loader-opt" data-loader-opt="${l}"><span class="dot" data-loader="${l}"></span>${LOADER_NAMES[l]}</button>`).join("")}
    </div></div>
    <div class="field"><label for="np-version">Minecraft-Version</label><select class="input" id="np-version"></select></div>
    <div class="field"><label>Symbol</label><div class="icon-pick">${state.icons.map((i) => `
      <button class="icon-opt" data-icon-opt="${esc(i)}">${esc(i)}</button>`).join("")}
    </div></div>
    <label class="check" id="np-voice-row"><input type="checkbox" id="np-voice" checked> Simple Voice Chat gleich mitinstallieren</label>
    <div class="modal-actions"><button class="btn" data-close>Abbrechen</button><button class="btn primary" id="np-create">Profil erstellen</button></div>`);
  const el = m.el;
  const select = $("#np-version", el);
  const voice = $("#np-voice", el);
  const create = $("#np-create", el);
  const markIcon = () => $$("[data-icon-opt]", el).forEach((b) => b.classList.toggle("active", b.dataset.iconOpt === icon));

  async function pickLoader(next) {
    loader = next;
    $$("[data-loader-opt]", el).forEach((b) => b.classList.toggle("active", b.dataset.loaderOpt === next));
    voice.disabled = next === "vanilla";
    $("#np-voice-row", el).classList.toggle("disabled", voice.disabled);
    select.innerHTML = "<option>Lädt …</option>";
    select.disabled = true;
    create.disabled = true;
    try {
      const list = versionCache[next] ||= await api("versions", { loader: next });
      if (loader !== next) return;
      select.innerHTML = list.map((v, i) => `<option value="${esc(v)}">${esc(v)}${i === 0 ? " (neueste)" : ""}</option>`).join("");
      select.disabled = false;
      create.disabled = false;
    } catch (e) {
      if (loader === next) select.innerHTML = `<option>${esc(e.message)}</option>`;
    }
  }

  el.addEventListener("click", (e) => {
    const lo = e.target.closest("[data-loader-opt]");
    if (lo) pickLoader(lo.dataset.loaderOpt);
    const io = e.target.closest("[data-icon-opt]");
    if (io) { icon = io.dataset.iconOpt; markIcon(); }
  });
  create.addEventListener("click", async () => {
    const version = select.value;
    const name = $("#np-name", el).value.trim() || `${LOADER_NAMES[loader]} ${version}`;
    const withVoice = voice.checked && !voice.disabled;
    setBusy(create, true, loader === "fabric" || withVoice ? "Lädt Mods …" : "Wird erstellt …");
    try {
      const res = await api("create_profile", { name, loader, mc_version: version, icon, voicechat: withVoice });
      await refreshProfiles();
      state.settings.last_profile = res.profile.id;
      m.close();
      toast(res.installed.length ? `Profil „${res.profile.name}“ erstellt, mit ${res.installed.join(", ")} 🎉` : `Profil „${res.profile.name}“ erstellt 🎉`);
      res.warnings.forEach((w) => toast(w, "warn"));
      showPage(state.page);
    } catch (e) {
      toast(e.message, "error");
      setBusy(create, false);
    }
  });
  markIcon();
  pickLoader("fabric");
  $("#np-name", el).focus();
}

function renderProfiles() {
  const cur = currentProfile();
  $("#profile-grid").innerHTML = state.profiles.map((p) => {
    const selected = p.id === cur?.id;
    return `
    <article class="profile-card ${selected ? "selected" : ""}" data-loader="${p.loader}">
      <div class="pc-top">
        <span class="loader-badge">${LOADER_NAMES[p.loader]}</span>
        ${selected ? `<span class="selected-badge">Ausgewählt</span>` : ""}
        <span>${esc(p.icon)}</span>
      </div>
      <div class="pc-body">
        <div class="pc-name">${esc(p.name)}</div>
        <div class="pc-meta">Minecraft ${esc(p.mc_version)} · ${p.content_count} ${p.content_count === 1 ? "Inhalt" : "Inhalte"}${p.voicechat ? " · 🎙️" : ""}</div>
        <div class="pc-actions">
          <button class="btn small grow" data-act="select" data-id="${p.id}">${selected ? "Spielen" : "Auswählen"}</button>
          <button class="btn small" data-act="mods" data-id="${p.id}">Mods</button>
          <button class="btn small icon" data-act="folder" data-id="${p.id}" title="Ordner öffnen">${ICON_FOLDER}</button>
          <button class="btn small icon ghost-danger" data-act="delete" data-id="${p.id}" title="Löschen">${ICON_TRASH}</button>
        </div>
      </div>
    </article>`;
  }).join("") + `<button class="profile-card new-card" data-new-profile><span class="plus">+</span>Neues Profil</button>`;
}

$("#profile-grid").addEventListener("click", async (e) => {
  const btn = e.target.closest("[data-act]");
  if (!btn) return;
  const p = state.profiles.find((x) => x.id === btn.dataset.id);
  if (!p) return;
  switch (btn.dataset.act) {
    case "select":
      if (p.id === currentProfile()?.id) showPage("play");
      else { await selectProfile(p.id); renderProfiles(); }
      break;
    case "mods":
      state.modsProfile = p.id;
      showPage("mods");
      break;
    case "folder":
      api("open_folder", { profile_id: p.id }).catch((err) => toast(err.message, "error"));
      break;
    case "delete": {
      const ok = await confirmDialog(`„${p.name}“ löschen?`,
        "Das Profil wird mit allen Mods, Welten und Einstellungen von deinem PC gelöscht. Das kann man nicht rückgängig machen.", "Endgültig löschen");
      if (!ok) return;
      try {
        state.profiles = await api("delete_profile", { profile_id: p.id });
        toast(`„${p.name}“ gelöscht`);
        renderProfiles();
      } catch (err) {
        toast(err.message, "error");
      }
      break;
    }
    default:
      break;
  }
});

// ---------- Mods ----------

let searchSeq = 0;
let searchTimer = 0;
const modsProfile = () => state.profiles.find((p) => p.id === state.modsProfile);

function openMods() {
  const has = state.profiles.length > 0;
  $("#mods-empty").hidden = has;
  $("#mods-main").hidden = !has;
  if (!has) return;
  if (!modsProfile()) state.modsProfile = currentProfile().id;
  $("#mods-profile").innerHTML = state.profiles
    .map((p) => `<option value="${p.id}">${esc(p.icon)} ${esc(p.name)} · ${esc(profileMeta(p))}</option>`).join("");
  $("#mods-profile").value = state.modsProfile;
  updateModsControls();
  runSearch();
  renderInstalled();
}

function updateModsControls() {
  const p = modsProfile();
  const allowed = { mod: p.loader !== "vanilla", resourcepack: true, shader: SHADER_LOADERS.includes(p.loader) };
  if (!allowed[state.modsType]) state.modsType = allowed.mod ? "mod" : "resourcepack";
  $$("#mods-type button").forEach((b) => {
    b.disabled = !allowed[b.dataset.type];
    b.classList.toggle("active", b.dataset.type === state.modsType);
  });
  $$("#mods-source button").forEach((b) => b.classList.toggle("active", b.dataset.source === state.modsSource));
  let hint = "";
  if (p.loader === "vanilla") hint = "Vanilla-Profile können keine Mods und Shader laden, Resourcepacks gehen aber.";
  else if (p.loader === "forge" && state.modsType !== "resourcepack") hint = "Für Forge gibt es kein Iris, deshalb sind Shader hier aus. Nimm dafür Fabric oder NeoForge.";
  else if (state.modsType === "shader") hint = "Beim ersten Shader installiert Emberleaf automatisch Iris und Sodium mit.";
  $("#mods-hint").hidden = !hint;
  $("#mods-hint").textContent = hint;
}

const iconHtml = (url) => (url ? `<img class="mod-icon" src="${esc(url)}" alt="" loading="lazy">` : `<div class="mod-icon"></div>`);

function runSearch() {
  clearTimeout(searchTimer);
  const seq = ++searchSeq;
  const list = $("#mod-results");
  const count = $("#mods-count");
  if (state.modsSource === "curseforge") {
    count.textContent = "";
    list.innerHTML = state.settings.curseforge_key
      ? `<div class="notice">Die CurseForge-Suche kommt in einem der nächsten Updates. Dein Schlüssel ist schon gespeichert 👍</div>`
      : `<div class="notice">Für CurseForge brauchst du einen kostenlosen API-Schlüssel. <button class="btn small" data-page="settings">Zu den Einstellungen</button></div>`;
    return;
  }
  count.textContent = "Suche …";
  list.innerHTML = `<div class="skeleton"></div>`.repeat(4);
  api("search", { profile_id: state.modsProfile, project_type: state.modsType, query: $("#mods-query").value })
    .then((res) => {
      if (seq !== searchSeq) return;
      count.textContent = `${res.total.toLocaleString("de-DE")} Treffer auf Modrinth`;
      list.innerHTML = res.hits.length ? res.hits.map((h) => `
        <div class="result">
          ${iconHtml(h.icon)}
          <div class="result-body">
            <div class="result-title">${esc(h.title)}</div>
            <div class="result-author">von ${esc(h.author)}</div>
            <div class="result-desc">${esc(h.description)}</div>
          </div>
          <div class="result-side">
            <span class="downloads">⬇ ${compact.format(h.downloads)}</span>
            ${h.installed ? `<span class="installed-pill">✓ Installiert</span>` : `<button class="btn primary small" data-install="${esc(h.id)}">Installieren</button>`}
          </div>
        </div>`).join("") : `<div class="empty">Nichts gefunden. Probier ein anderes Wort 🍂</div>`;
    })
    .catch((e) => {
      if (seq !== searchSeq) return;
      count.textContent = "";
      list.innerHTML = `<div class="notice error">${esc(e.message)}</div>`;
    });
}

async function renderInstalled() {
  const box = $("#installed-list");
  const pid = state.modsProfile;
  try {
    const content = await api("content", { profile_id: pid });
    if (pid !== state.modsProfile) return;
    box.innerHTML = content.length ? content.map((c) => `
      <div class="installed-item">
        ${iconHtml(c.icon)}
        <span class="ii-text"><b>${esc(c.title)}</b><small>${TYPE_NAMES[c.type]} · ${esc(c.version)}</small></span>
        <button class="btn small icon ghost-danger" data-remove="${esc(c.project_id)}" title="Entfernen">${ICON_X}</button>
      </div>`).join("") : `<div class="empty">Noch nichts installiert.</div>`;
  } catch (e) {
    box.innerHTML = `<div class="notice error">${esc(e.message)}</div>`;
  }
}

$("#mods-profile").addEventListener("change", (e) => {
  state.modsProfile = e.target.value;
  updateModsControls();
  runSearch();
  renderInstalled();
});
$("#mods-type").addEventListener("click", (e) => {
  const b = e.target.closest("button[data-type]");
  if (!b || b.disabled) return;
  state.modsType = b.dataset.type;
  updateModsControls();
  runSearch();
});
$("#mods-source").addEventListener("click", (e) => {
  const b = e.target.closest("button[data-source]");
  if (!b) return;
  state.modsSource = b.dataset.source;
  updateModsControls();
  runSearch();
});
$("#mods-query").addEventListener("input", () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(runSearch, 350);
});
$("#mod-results").addEventListener("click", async (e) => {
  const btn = e.target.closest("[data-install]");
  if (!btn) return;
  setBusy(btn, true, "Lädt …");
  try {
    const res = await api("install", { profile_id: state.modsProfile, project_id: btn.dataset.install, project_type: state.modsType });
    btn.outerHTML = `<span class="installed-pill">✓ Installiert</span>`;
    toast(res.installed.length ? `Installiert: ${res.installed.join(", ")}` : "War schon installiert 👍");
    res.warnings.forEach((w) => toast(w, "warn"));
    await refreshProfiles();
    renderInstalled();
  } catch (err) {
    toast(err.message, "error");
    setBusy(btn, false);
  }
});
// eigene Mods, Resourcepacks oder Shader (Dateien vom PC) ins Profil legen
$("#add-local").addEventListener("click", () => $("#add-local-file").click());
$("#add-local-file").addEventListener("change", async (e) => {
  const file = e.target.files[0];
  e.target.value = "";
  if (!file) return;
  const btn = $("#add-local");
  setBusy(btn, true, "Lädt …");
  try {
    const buf = new Uint8Array(await file.arrayBuffer());
    let bin = "";
    for (let i = 0; i < buf.length; i += 0x8000) bin += String.fromCharCode(...buf.subarray(i, i + 0x8000));
    const res = await api("add_local", { profile_id: state.modsProfile, project_type: state.modsType, filename: file.name, data: btoa(bin) });
    toast(`„${res.installed[0]}“ ist jetzt im Profil 👍`);
    await refreshProfiles();
    renderInstalled();
  } catch (err) {
    toast(err.message, "error");
  }
  setBusy(btn, false);
});
$("#installed-list").addEventListener("click", async (e) => {
  const btn = e.target.closest("[data-remove]");
  if (!btn) return;
  btn.disabled = true;
  try {
    await api("remove", { profile_id: state.modsProfile, project_id: btn.dataset.remove });
    await refreshProfiles();
    renderInstalled();
    runSearch();
  } catch (err) {
    toast(err.message, "error");
    btn.disabled = false;
  }
});
document.addEventListener("error", (e) => {
  if (e.target instanceof HTMLImageElement && e.target.classList.contains("mod-icon")) e.target.classList.add("broken");
}, true);

// ---------- Einstellungen ----------

function renderSettings() {
  const s = state.settings;
  $("#set-username").value = s.username;
  $("#set-ram").value = s.ram_mb;
  $("#set-ram-value").textContent = ramLabel(s.ram_mb);
  $("#set-cf").value = s.curseforge_key;
  $("#set-azure").value = s.azure_client_id;
  $("#set-friends").value = s.friends_server || "";
  $("#set-scene").value = s.menu_scene || "auto";
  $("#set-minimize").checked = s.minimize_on_play;
  $("#set-restore").checked = s.restore_after_play !== false;
  $("#set-width").value = s.game_width || "";
  $("#set-height").value = s.game_height || "";
  $("#set-fullscreen").checked = Boolean(s.fullscreen);
  $("#set-jvm").value = s.jvm_args || "";
  if (state.account) {
    $("#account-hint").textContent = `Angemeldet als ${state.account.name}. Deine Anmeldung ist verschlüsselt auf diesem PC gespeichert.`;
    $("#account-actions").innerHTML = `<button class="btn" id="logout">Abmelden</button>`;
  } else {
    $("#account-hint").textContent = "Melde dich an, damit Emberleaf Minecraft mit deinem Konto starten kann.";
    $("#account-actions").innerHTML = `<button class="btn primary" id="login">Mit Microsoft anmelden</button>`;
  }
}

$("#account-actions").addEventListener("click", async (e) => {
  if (e.target.closest("#login")) {
    await openLoginDialog();
  } else if (e.target.closest("#logout")) {
    state.account = await api("logout");
    toast("Abgemeldet");
    renderSettings();
    loadSkin();
    friendsPage.accountChanged();
  }
});
$("#set-ram").addEventListener("input", (e) => { $("#set-ram-value").textContent = ramLabel(Number(e.target.value)); });
$("#settings-save").addEventListener("click", async (e) => {
  const btn = e.currentTarget;
  const before = state.settings.username;
  const serverBefore = state.settings.friends_server;
  setBusy(btn, true, "Speichert …");
  try {
    state.settings = await api("save_settings", {
      username: $("#set-username").value,
      ram_mb: Number($("#set-ram").value),
      curseforge_key: $("#set-cf").value,
      azure_client_id: $("#set-azure").value,
      friends_server: $("#set-friends").value,
      menu_scene: $("#set-scene").value,
      minimize_on_play: $("#set-minimize").checked,
      restore_after_play: $("#set-restore").checked,
      game_width: Number($("#set-width").value) || 0,
      game_height: Number($("#set-height").value) || 0,
      fullscreen: $("#set-fullscreen").checked,
      jvm_args: $("#set-jvm").value,
    });
    home.world.setScene(state.settings.menu_scene);
    toast("Gespeichert ✓");
    if (state.settings.username !== before && !state.account) loadSkin();
    if (state.settings.friends_server !== serverBefore) { friendsPage.accountChanged(); updatesCard.open(); }
  } catch (err) {
    toast(err.message, "error");
  }
  setBusy(btn, false);
});
$("#open-data").addEventListener("click", () => api("open_folder").catch((e) => toast(e.message, "error")));

function welcome() {
  const m = modal(`
    <h3>Willkommen bei Emberleaf 🍁</h3>
    <p>Wie heißt du in Minecraft? Dann holen wir deinen Skin und stellen dich ans Lagerfeuer. Anmelden kannst du dich später.</p>
    <div class="field"><label for="wl-name">Minecraft-Name</label><input class="input" id="wl-name" maxlength="16" placeholder="Dein Name" autocomplete="off"></div>
    <div class="modal-actions"><button class="btn" data-close>Später</button><button class="btn primary" id="wl-save">Los geht's</button></div>`);
  const input = $("#wl-name", m.el);
  const btn = $("#wl-save", m.el);
  const save = async () => {
    setBusy(btn, true, "Lädt …");
    try {
      state.settings = await api("save_settings", { username: input.value });
      m.close();
      await loadSkin();
    } catch (e) {
      toast(e.message, "error");
      setBusy(btn, false);
    }
  };
  btn.addEventListener("click", save);
  input.addEventListener("keydown", (e) => { if (e.key === "Enter") save(); });
  input.focus();
}

// ---------- Start ----------

document.addEventListener("click", (e) => {
  const nav = e.target.closest("[data-page]");
  if (nav) return showPage(nav.dataset.page);
  const win = e.target.closest("[data-win]");
  if (win) return void api("window_action", { action: win.dataset.win }).catch(() => {});
  if (e.target.closest("[data-new-profile]")) return openCreateProfile();
  const sel = e.target.closest("[data-select-profile]");
  if (sel) {
    toggleProfileMenu(false);
    return void selectProfile(sel.dataset.selectProfile);
  }
  if (!e.target.closest(".picker-wrap")) toggleProfileMenu(false);
});
$("#profile-picker").addEventListener("click", () => {
  if (state.profiles.length) toggleProfileMenu();
  else openCreateProfile();
});
$("#play-btn").addEventListener("click", onPlay);

let last = performance.now();
let clock = 0;
function tick(now) {
  const dt = Math.min(0.05, (now - last) / 1000);
  last = now;
  clock += dt;
  if (state.page === "play" && !document.hidden) {
    home.world.update(dt, clock);
    home.outfit.update(dt);
  }
  if (state.page === "cosmetics") shop.update(dt);
  requestAnimationFrame(tick);
}

async function init() {
  installCssTextures();
  home.init();
  try {
    Object.assign(state, await api("state"));
    home.world.setScene(state.settings.menu_scene);
  } catch (e) {
    toast(e.message, "error");
    return;
  }
  applyPhase();
  setInterval(() => { applyPhase(); if (state.page === "play") renderPlay(); }, 5 * 60 * 1000);
  showPage("play");
  requestAnimationFrame(tick);
  await home.syncOutfit();
  if (["installing", "running"].includes(state.game.state)) startPolling();
  friendsPage.background();
  setTimeout(() => updatesCard.background(), 3000); // "updated to …" right away, "update ready" after the download
  setInterval(() => { if (state.page !== "friends" && !document.hidden) friendsPage.background(); }, 2 * 60 * 1000);
  if (!state.account && !state.settings.username) welcome();
  await loadSkin();
}

init();
