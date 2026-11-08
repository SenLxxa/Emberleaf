// Startseite: zwei Scroll-Szenen und die Downloads.
//  1. Titelbild: beim Scrollen fliegt Emberleaf nach rechts weg, die Features kommen nacheinander von links.
//  2. Zoom: das Ahornblatt aus dem Logo wird zum Fenster, man fliegt hinein – drinnen liegt der Download.
import { $, api, esc, initAccount } from "./common.js";
import { LEAF, VEINS } from "./leaf-path.js";

const root = document.documentElement;
const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
if (!reduced) root.classList.add("motion");

const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const span = (a, b, v) => clamp((v - a) / (b - a));
const ease = (t) => (t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2);

// ---------- Szene 1: Titelbild und Features ----------
const intro = $(".intro");
const introStage = intro.querySelector(".stage");
const slides = [...intro.querySelectorAll(".slide")];
const dots = [...intro.querySelectorAll(".dots li")];
const sky = intro.querySelector(".sky");
const STEP = 1; // so viele Bildschirmhöhen Scrollen pro Folie
const HOLD = 0.3; // am Anfang jedes Schritts steht alles still

function introTrack(u) {
  const steps = slides.length - 1;
  const k = Math.floor(u / STEP);
  if (k >= steps) return steps;
  return k + ease(span(HOLD, 1, (u - k * STEP) / STEP));
}

function introFrame() {
  const H = innerHeight;
  const W = innerWidth;
  const u = Math.max(0, -intro.getBoundingClientRect().top) / H;
  const t = introTrack(u);
  slides.forEach((slide, i) => {
    const d = i - t; // < 0: schon dran gewesen (geht nach rechts), > 0: kommt noch (wartet links)
    const far = Math.abs(d) >= 1.02;
    slide.style.visibility = far ? "hidden" : "";
    if (far) return;
    slide.style.setProperty("--x", `${(-d * W).toFixed(1)}px`);
    slide.style.setProperty("--r", `${(-d * 5).toFixed(2)}deg`);
    slide.style.setProperty("--o", (1 - span(0.55, 1, Math.abs(d))).toFixed(3));
    slide.style.setProperty("--px", `${(-d * W * 0.14).toFixed(1)}px`);
  });
  const active = Math.round(t);
  dots.forEach((dot, i) => dot.classList.toggle("on", i === active));
  sky.style.setProperty("--dusk", (t / (slides.length - 1)).toFixed(3));
}

// ---------- Szene 2: Zoom ins Ahornblatt ----------
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
  const range = zoom.offsetHeight - zoomStage.offsetHeight;
  const p = range > 0 ? clamp(-zoom.getBoundingClientRect().top / range) : 1;
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
  zoomTitle.style.setProperty("--ty", `${(-p * 120).toFixed(1)}px`);
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
  if (!reduced) {
    introFrame();
    zoomFrame();
  }
}
const request = () => {
  if (!queued) {
    queued = true;
    requestAnimationFrame(frame);
  }
};
addEventListener("scroll", request, { passive: true });
addEventListener("resize", request);
frame();

// Sprünge aus dem Menü landen an der richtigen Stelle der Szenen
function jumpTarget(name) {
  if (reduced) return null;
  const H = innerHeight;
  if (name === "top") return 0;
  if (name === "features") return intro.offsetTop + STEP * H;
  if (name === "download") return zoom.offsetTop + zoom.offsetHeight - zoomStage.offsetHeight;
  return null;
}
// direkt mit …/#download oder …/#features geöffnet: an die fertige Stelle der Szene springen
if (location.hash === "#download" || location.hash === "#features") {
  const top = jumpTarget(location.hash.slice(1));
  if (top !== null) requestAnimationFrame(() => scrollTo(0, top));
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

// fallende Blätter im Titelbild
if (!reduced) {
  const box = $("#leaves");
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
    box.append(leaf);
  }
}

// Karten tauchen beim Scrollen auf
const io = new IntersectionObserver((entries) => {
  for (const entry of entries) {
    if (entry.isIntersecting) {
      entry.target.classList.add("in");
      io.unobserve(entry.target);
    }
  }
}, { rootMargin: "0px 0px -8% 0px" });
const observe = (els) => {
  if (reduced) return;
  for (const el of els) {
    el.classList.add("reveal");
    io.observe(el);
  }
};
observe(document.querySelectorAll(".card, .pack, .join-card, .legal details"));

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
    <article class="release">
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

initAccount();
loadReleases();
