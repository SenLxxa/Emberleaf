// Startseite: Scroll-Szenen und die Downloads.
//  1. Titelbild über einem Sonnenuntergang aus dem Spiel: beim Runterscrollen zoomt das Bild, das Logo steigt nach oben weg.
//  2. Collage: Bilder fliegen nacheinander rein – von unten, oben, rechts, links, aus der Mitte und von draußen –,
//     am Ende zoomt alles nach draußen weg.
//  3. Cosmetics: die Looks laufen beim Runterscrollen von rechts nach links durch.
//  4. Zoom: das Ahornblatt aus dem Logo wird zum Fenster, man fliegt hinein – drinnen liegt der Download.
// Auf schmalen Bildschirmen stehen Collage und Looks untereinander bzw. zum Wischen, Karten fliegen beim Erscheinen rein.
import { $, api, esc, initAccount } from "./common.js";
import { LEAF, VEINS } from "./leaf-path.js";

const root = document.documentElement;
const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
const wideQuery = matchMedia("(min-width: 900px) and (min-height: 560px)");
if (!reduced) root.classList.add("motion");
let wide = false;

const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const span = (a, b, v) => clamp((v - a) / (b - a));
const ease = (t) => (t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2);
const px = (v) => `${v.toFixed(1)}px`;
const progress = (scene, stage) => {
  const range = scene.offsetHeight - stage.offsetHeight;
  return range > 0 ? clamp(-scene.getBoundingClientRect().top / range) : 0;
};

// ---------- 1 · Titelbild ----------
const hero = $(".hero-scene");
const heroStage = hero.querySelector(".stage");
const heroBg = hero.querySelector(".hero-bg");
const heroInner = hero.querySelector(".hero");
const heroShade = hero.querySelector(".hero-shade");
const leavesBox = $("#leaves");
const cue = hero.querySelector(".scroll-cue");

function heroFrame() {
  const p = progress(hero, heroStage);
  const H = innerHeight;
  heroBg.style.setProperty("--bs", (1.04 + 0.18 * p).toFixed(4));
  heroBg.style.setProperty("--by", px(p * H * 0.06));
  heroInner.style.setProperty("--hy", px(-ease(p) * H * 0.24));
  heroInner.style.setProperty("--hs", (1 - 0.12 * p).toFixed(4));
  heroInner.style.setProperty("--ho", (1 - span(0.15, 0.8, p)).toFixed(3));
  cue.style.setProperty("--ho", (1 - span(0, 0.25, p)).toFixed(3));
  heroShade.style.setProperty("--shade", (1 + p * 0.6).toFixed(3));
  leavesBox.style.setProperty("--lo", (1 - span(0.3, 1, p)).toFixed(3));
}

// ---------- 2 · Bilder fliegen rein ----------
const fly = $(".fly-scene");
const flyStage = fly.querySelector(".stage");
const collage = $("#collage");
const tiles = [...collage.querySelectorAll(".tile")];
const flyTitle = fly.querySelector(".fly-title");
const flyCaption = $("#fly-caption");
const flyBg = fly.querySelector(".fly-bg");
const FLY_START = 0.25; // Bildschirmhöhen Scrollen, bevor das erste Bild kommt
const FLY_STEP = 0.7; // Abstand zwischen zwei Bildern
const FLY_MOVE = 0.55; // so lange fliegt ein Bild
let shownTile = -2;

function showCaption(i) {
  if (i === shownTile) return;
  shownTile = i;
  const cap = tiles[i]?.querySelector("figcaption");
  $("#fly-kicker").textContent = cap ? cap.querySelector(".kicker").textContent : "Scroll weiter";
  $("#fly-heading").textContent = cap ? cap.querySelector("b").textContent : "Gleich fliegen die Bilder rein";
  $("#fly-text").textContent = cap ? cap.querySelector("span:not(.kicker)").textContent : "";
  flyCaption.classList.remove("swap");
  void flyCaption.offsetWidth; // Animation neu starten
  flyCaption.classList.add("swap");
}

function flyFrame() {
  const W = innerWidth;
  const H = innerHeight;
  const u = clamp(-fly.getBoundingClientRect().top / H, 0, (fly.offsetHeight - flyStage.offsetHeight) / H);
  let landed = -1;
  tiles.forEach((tile, i) => {
    const t = ease(span(FLY_START + i * FLY_STEP, FLY_START + i * FLY_STEP + FLY_MOVE, u));
    if (t >= 0.5) landed = i;
    const k = 1 - t;
    let tx = 0;
    let ty = 0;
    let ts = 1;
    let to = 1;
    switch (tile.dataset.from) {
      case "bottom": ty = k * H * 1.15; break;
      case "top": ty = -k * H * 1.15; break;
      case "right": tx = k * W * 1.1; break;
      case "left": tx = -k * W * 1.1; break;
      case "center": ts = 0.05 + 0.95 * t; to = clamp(t * 1.6); break;
      case "outside": ts = 1 + k * 3.5; to = t; break; // kommt von draußen, groß und durchsichtig, auf die Wand
      default: break;
    }
    tile.style.visibility = t <= 0 ? "hidden" : "";
    tile.style.setProperty("--tx", px(tx));
    tile.style.setProperty("--ty", px(ty));
    tile.style.setProperty("--ts", ts.toFixed(4));
    tile.style.setProperty("--to", to.toFixed(3));
  });
  // am Ende geht es nach draußen: alles zoomt weg
  const end = FLY_START + tiles.length * FLY_STEP + 0.3;
  const z = ease(span(end, end + 0.8, u));
  collage.style.setProperty("--cs", (1 - 0.5 * z).toFixed(4));
  collage.style.setProperty("--co", (1 - z).toFixed(3));
  flyTitle.style.setProperty("--fo", (1 - z).toFixed(3));
  flyTitle.style.setProperty("--fy", px(-z * 60));
  flyCaption.style.setProperty("--fo", (1 - z).toFixed(3));
  flyBg.style.setProperty("--bs", (1.04 + 0.1 * span(0, end + 0.8, u)).toFixed(4));
  showCaption(landed);
}

// ---------- 3 · Cosmetics-Band ----------
const cos = $(".cos-scene");
const cosStage = cos.querySelector(".stage");
const track = $("#cos-track");
const looks = [...track.querySelectorAll(".look")];
const cosBg = cos.querySelector(".cos-bg");
const trackDistance = () => Math.max(0, track.scrollWidth - innerWidth + 40);

function cosLayout() {
  // so hoch, dass das Band einmal ganz durchläuft (plus etwas Ruhe am Anfang und Ende)
  cos.style.height = wide && !reduced ? `${Math.round(innerHeight * 1.5 + trackDistance())}px` : "";
}

function cosFrame() {
  const p = progress(cos, cosStage);
  const W = innerWidth;
  const t = ease(span(0.08, 0.92, p));
  track.style.setProperty("--kx", px(W * 0.3 * (1 - t) - trackDistance() * t));
  looks.forEach((look, i) => {
    look.style.setProperty("--ly", px(Math.sin(p * Math.PI * 3 + i * 1.3) * 10));
    look.style.setProperty("--lr", `${(Math.sin(p * Math.PI * 2 + i) * 1.2).toFixed(2)}deg`);
  });
  cosBg.style.setProperty("--by", px(-p * innerHeight * 0.05));
  cosBg.style.setProperty("--bs", (1.06 + 0.06 * p).toFixed(4));
}

// ---------- 4 · Zoom ins Ahornblatt ----------
const zoom = $(".zoom");
const zoomStage = zoom.querySelector(".stage");
const cover = $("#cover");
const [coverFill, coverGlow, coverLine, coverVeins] = cover.querySelectorAll("path");
const zoomTitle = $(".zoom-title");
const roomInner = $(".room-inner");
coverFill.setAttribute("d", `M-20000,-20000 H20100 V20100 H-20000 Z ${LEAF}`);
for (const el of [coverGlow, coverLine]) el.setAttribute("d", LEAF);
coverVeins.setAttribute("d", VEINS);
for (const el of [coverGlow, coverLine, coverVeins]) el.setAttribute("vector-effect", "non-scaling-stroke");

function zoomFrame() {
  const W = innerWidth;
  const H = innerHeight;
  const p = progress(zoom, zoomStage);
  const appear = ease(span(0, 0.14, p));
  const z = ease(span(0.14, 0.86, p));
  const s0 = Math.min(W, H) * 0.0054; // Pixel pro Blatt-Einheit: das Blatt ist gut halb so groß wie der Bildschirm
  const s1 = Math.hypot(W, H) / 36; // so groß, dass das Innere des Blatts den ganzen Bildschirm füllt
  const s = s0 * (0.82 + 0.18 * appear) * (s1 / s0) ** z;
  const rot = (1 - appear) * -10;
  const transform = `translate(${(W / 2).toFixed(1)} ${(H / 2).toFixed(1)}) rotate(${rot.toFixed(2)}) scale(${s.toFixed(4)}) translate(-50 -47)`;
  for (const el of [coverFill, coverGlow, coverLine, coverVeins]) el.setAttribute("transform", transform);
  coverLine.style.opacity = coverGlow.style.opacity = (1 - span(0.25, 0.55, z)).toFixed(3);
  coverVeins.style.opacity = (1 - span(0.02, 0.22, z)).toFixed(3);
  cover.style.display = z >= 0.999 ? "none" : "";
  zoomTitle.style.setProperty("--to", (1 - span(0.03, 0.2, p)).toFixed(3));
  zoomTitle.style.setProperty("--ty", px(-p * 120));
  roomInner.style.setProperty("--ro", span(0.35, 0.75, z).toFixed(3));
  roomInner.style.setProperty("--rs", (0.86 + 0.14 * ease(span(0.3, 1, z))).toFixed(4));
  zoomStage.style.setProperty("--room-events", z >= 0.98 ? "auto" : "none");
}

// ---------- Bild für Bild ----------
const topbar = $("#topbar");
let queued = false;
function frame() {
  queued = false;
  topbar.classList.toggle("solid", scrollY > 40);
  if (reduced) return;
  heroFrame();
  zoomFrame();
  if (wide) {
    flyFrame();
    cosFrame();
  }
}
const request = () => {
  if (!queued) {
    queued = true;
    requestAnimationFrame(frame);
  }
};

function setWide() {
  wide = wideQuery.matches;
  root.classList.toggle("wide", wide);
  if (!wide) {
    // die Inline-Werte der Animationen dürfen die Liste nicht verschieben
    for (const el of [...tiles, track, collage]) el.removeAttribute("style");
    for (const tile of tiles) tile.style.cssText = tile.dataset.pos;
  }
  cosLayout();
  observeReveals();
  request();
}
for (const tile of tiles) tile.dataset.pos = tile.getAttribute("style") || "";
addEventListener("scroll", request, { passive: true });
addEventListener("resize", () => {
  cosLayout();
  request();
});
wideQuery.addEventListener("change", setWide);
addEventListener("load", () => {
  cosLayout(); // die Bilder im Band haben jetzt ihre Breite
  request();
});

// Sprünge aus dem Menü landen an der richtigen Stelle der Szenen
function jumpTarget(name) {
  if (reduced) return null;
  const H = innerHeight;
  if (name === "top") return 0;
  if (name === "features") return wide ? fly.offsetTop + (FLY_START + FLY_MOVE) * H : fly.offsetTop;
  if (name === "cosmetics") return wide ? cos.offsetTop + (cos.offsetHeight - cosStage.offsetHeight) * 0.12 : cos.offsetTop;
  if (name === "download") return zoom.offsetTop + zoom.offsetHeight - zoomStage.offsetHeight;
  return null;
}
document.addEventListener("click", (e) => {
  const link = e.target.closest("[data-jump]");
  if (link) {
    const top = jumpTarget(link.dataset.jump);
    if (top !== null) {
      e.preventDefault();
      scrollTo({ top, behavior: "smooth" });
      history.replaceState(null, "", link.dataset.jump === "top" ? location.pathname : `#${link.dataset.jump}`);
    }
  }
  const open = e.target.closest("[data-open]");
  if (open) {
    const details = $(open.getAttribute("href"));
    if (details) details.open = true;
  }
});

// fallende Blätter vor dem Titelbild
if (!reduced) {
  const count = innerWidth < 600 ? 7 : 14;
  for (let i = 0; i < count; i++) {
    const leaf = document.createElement("i");
    leaf.className = "falling";
    leaf.style.left = `${(Math.random() * 100).toFixed(1)}%`;
    leaf.style.setProperty("--s", `${(14 + Math.random() * 20).toFixed(0)}px`);
    leaf.style.setProperty("--d", `${(11 + Math.random() * 12).toFixed(1)}s`);
    leaf.style.setProperty("--delay", `${(-Math.random() * 20).toFixed(1)}s`);
    leaf.style.setProperty("--sway", `${(20 + Math.random() * 60).toFixed(0)}px`);
    leaf.style.setProperty("--h", `${(Math.random() * 40 - 25).toFixed(0)}deg`);
    leavesBox.append(leaf);
  }
}

// Karten, Packs und (auf schmalen Bildschirmen) die Collage-Bilder fliegen beim Erscheinen aus ihrer Richtung rein
const io = new IntersectionObserver((entries) => {
  for (const entry of entries) {
    if (entry.isIntersecting) {
      entry.target.classList.remove("out");
      io.unobserve(entry.target);
    }
  }
}, { rootMargin: "0px 0px -10% 0px" });
function observe(els) {
  if (reduced) return;
  for (const el of els) {
    if (el.dataset.seen) continue;
    el.dataset.seen = "1";
    el.classList.add("out");
    io.observe(el);
  }
}
function observeReveals() {
  observe(document.querySelectorAll(".card[data-from], .pack[data-from], .join-card[data-from]"));
  if (!wide) observe(tiles);
  else for (const tile of tiles) tile.classList.remove("out");
}

// ---------- Downloads und Releases ----------
const mb = (bytes) => `${(bytes / 1024 / 1024).toFixed(bytes < 10 * 1024 * 1024 ? 1 : 0).replace(".", ",")} MB`;
const date = (sec) => new Date(sec * 1000).toLocaleDateString("de-DE", { day: "numeric", month: "long", year: "numeric" });

// Release-Text: Zeilen mit "-" oder "*" werden Punkte, der Rest Absätze
function notesHtml(text) {
  const out = [];
  let list = [];
  const flush = () => {
    if (list.length) out.push(`<ul>${list.map((l) => `<li>${esc(l)}</li>`).join("")}</ul>`);
    list = [];
  };
  for (const raw of String(text || "").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line) continue;
    const item = line.match(/^[-*•]\s*(.+)$/);
    if (item) list.push(item[1]);
    else {
      flush();
      out.push(`<p>${esc(line)}</p>`);
    }
  }
  flush();
  return out.join("");
}

function showLatest(release) {
  const btn = $("#dl-btn");
  const meta = $("#dl-meta");
  const exe = release?.files.find((f) => f.kind === "launcher");
  if (!exe) {
    btn.setAttribute("aria-disabled", "true");
    $("#dl-label").textContent = "Bald verfügbar";
    meta.textContent = "Die erste Version kommt gleich auf die Webseite.";
    return;
  }
  btn.href = exe.url;
  btn.setAttribute("download", exe.name);
  btn.removeAttribute("aria-disabled");
  $("#dl-label").textContent = "Für Windows herunterladen";
  meta.textContent = `Version ${release.version} · ${mb(exe.size)} · ${date(release.published)}`;
  $("#hero-version").textContent = `Version ${release.version} · Alpha`;
  const notes = $("#latest-notes");
  if (release.notes) {
    notes.innerHTML = `<h3>Neu in ${esc(release.version)}</h3>${notesHtml(release.notes)}`;
    notes.hidden = false;
  }
}

function showReleases(releases) {
  const box = $("#release-list");
  if (!releases.length) {
    box.innerHTML = '<p class="muted">Noch keine Version auf der Webseite. Bald geht es los!</p>';
    return;
  }
  box.innerHTML = releases.map((r, i) => `
    <article class="release" data-from="${i % 2 ? "right" : "left"}">
      <header>
        <h3>Emberleaf ${esc(r.version)}</h3>
        <time datetime="${new Date(r.published * 1000).toISOString()}">${esc(date(r.published))}</time>
        ${i === 0 ? '<span class="tag">Neu</span>' : ""}
      </header>
      ${notesHtml(r.notes) || '<p class="muted">Kleine Verbesserungen.</p>'}
      <div class="files">${r.files.map((f) => (r.available
        ? `<a href="${esc(f.url)}" download="${esc(f.name)}">${esc(f.name)} · ${mb(f.size)}</a>`
        : `<span title="Ältere Dateien werden aufgeräumt">${esc(f.name)}</span>`)).join("")}</div>
    </article>`).join("");
  observe(box.querySelectorAll(".release"));
  cosLayout();
  request();
}

async function loadReleases() {
  try {
    const [latest, list] = await Promise.all([api("/v1/update"), api("/v1/releases")]);
    showLatest(latest.release);
    showReleases(list.releases);
  } catch (e) {
    $("#dl-meta").textContent = "Der Server ist gerade nicht erreichbar. Versuch es gleich nochmal.";
    $("#release-list").innerHTML = `<p class="muted">${esc(e.message)}</p>`;
  }
}

setWide();
// direkt mit …/#download, #features oder #cosmetics geöffnet: an die fertige Stelle der Szene springen
if (["#download", "#features", "#cosmetics"].includes(location.hash)) {
  const top = jumpTarget(location.hash.slice(1));
  if (top !== null) requestAnimationFrame(() => scrollTo(0, top));
}
initAccount();
loadReleases();
