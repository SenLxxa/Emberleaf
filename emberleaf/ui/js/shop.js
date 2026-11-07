// Cosmetics-Seite: Kategorien, Karten mit 3D-Vorschaubild und die Live-Vorschau "Dein Look".
import { IdleAnimation, SkinViewer } from "../vendor/esm/skinview3d.js";
import { CATEGORIES, PRESETS } from "./catalog.js";
import { Outfit, ThumbnailRenderer, emoteAnimation } from "./cosmetics.js";
import { openCapeEditor } from "./editor.js";
import { $, api, confirmDialog, esc, modal, setBusy, toast } from "./ui.js";


export function createShop({ state, onEquippedChange }) {
  let active = "head";
  let viewer = null;
  let outfit = null;
  let thumbs = null;
  const stage = $("#sp-stage");

  const allItems = () => [
    ...(state.cosmetics.custom || []).map((c) => ({ ...c, custom: true })).reverse(),
    ...(state.cosmetics.models || []).map((m) => ({ ...m, model: true })),
    ...PRESETS,
  ];
  const findItem = (id) => allItems().find((i) => i.id === id);
  const category = (id) => CATEGORIES.find((c) => c.id === id);

  function ensureViewer() {
    if (viewer) return;
    viewer = new SkinViewer({ canvas: $("#sp-canvas"), width: stage.clientWidth || 340, height: stage.clientHeight || 460, fov: 38 });
    viewer.controls.enableZoom = false;
    viewer.controls.enablePan = false;
    viewer.zoom = 0.62;
    viewer.playerWrapper.rotation.y = 0.35;
    viewer.animation = new IdleAnimation();
    new ResizeObserver(() => {
      if (stage.clientWidth && stage.clientHeight) viewer.setSize(stage.clientWidth, stage.clientHeight);
    }).observe(stage);
    outfit = new Outfit(viewer);
    thumbs = new ThumbnailRenderer();
  }

  async function applySkin() {
    if (!viewer) return;
    if (state.skin?.skin) await viewer.loadSkin(state.skin.skin, { model: state.skin.model });
    await outfit.refreshSkin();
    await thumbs.setSkin(state.skin);
    if (state.page === "cosmetics") renderGrid();
  }

  async function syncOutfit() {
    const equipped = state.cosmetics.equipped || {};
    for (const cat of CATEGORIES) {
      const item = equipped[cat.id] ? findItem(equipped[cat.id]) : null;
      if ((outfit.items[cat.id]?.id || null) !== (item?.id || null) || item?.custom) await outfit.equip(cat.id, item);
    }
    viewer.animation = equipped.emote ? emoteAnimation(equipped.emote) : new IdleAnimation();
    renderEquipped();
  }

  function renderTabs() {
    $("#shop-tabs").innerHTML = CATEGORIES.map((c) => `
      <button class="shop-tab ${c.id === active ? "active" : ""}" data-cat="${c.id}">
        <span class="shop-tab-icon">${c.icon}</span>${c.label}
      </button>`).join("");
  }

  function badge(item) {
    if (item.custom) return `<span class="cos-badge own">${item.model ? "BLOCKBENCH" : "EIGENES"}</span>`;
    if (item.model) return `<span class="cos-badge new">HD</span>`;
    if (item.animated) return `<span class="cos-badge anim">ANIMIERT</span>`;
    if (item.isNew) return `<span class="cos-badge new">NEU</span>`;
    return "";
  }

  function cardHtml(item) {
    const cat = category(item.category);
    const equipped = state.cosmetics.equipped?.[item.category] === item.id;
    let actions = "";
    if (item.custom) {
      const edit = item.image ? `<span class="cos-act" data-act="edit" title="Bearbeiten">✎</span>` : "";
      actions = `<span class="cos-actions">${edit}<span class="cos-act danger" data-act="delete" title="Löschen">🗑</span></span>`;
    } else if (item.design) {
      actions = `<span class="cos-actions"><span class="cos-act" data-act="remix" title="Als Vorlage für dein eigenes nehmen">✎</span></span>`;
    }
    return `
      <div class="cos-card ${equipped ? "equipped" : ""}" data-id="${esc(item.id)}" tabindex="0" role="button" aria-pressed="${equipped}">
        <div class="cos-thumb"><img alt="" data-thumb="${esc(item.id)}"><span class="cos-loading"></span></div>
        ${badge(item)}
        ${actions}
        <span class="cos-tag">${cat.tag}</span>
        <div class="cos-foot"><span class="cos-name">${esc(item.name)}</span><span class="cos-state">${equipped ? "✓ Angelegt" : "Anlegen"}</span></div>
      </div>`;
  }

  function renderGrid() {
    const cat = category(active);
    $("#shop-title").textContent = `${cat.icon} ${cat.label}`;
    $("#shop-hint").textContent = cat.hint;
    $("#shop-create").hidden = !cat.importable;
    $("#shop-paint").hidden = !cat.paintable;
    const items = allItems().filter((i) => i.category === active);
    $("#shop-grid").innerHTML = items.map(cardHtml).join("");
    for (const item of items) {
      thumbs.render(item).then((url) => {
        const img = document.querySelector(`[data-thumb="${CSS.escape(item.id)}"]`);
        if (!img) return;
        img.src = url;
        img.closest(".cos-thumb").classList.add("ready");
      });
    }
  }

  function renderEquipped() {
    const equipped = state.cosmetics.equipped || {};
    const chips = CATEGORIES.filter((c) => equipped[c.id]).map((c) => {
      const item = findItem(equipped[c.id]);
      return item ? `<span class="eq-chip">${c.icon} ${esc(item.name)}<button data-unequip="${c.id}" title="Ablegen">×</button></span>` : "";
    });
    $("#sp-equipped").innerHTML = chips.join("") || `<span class="sp-empty">Noch nichts angelegt. Klick eine Karte an!</span>`;
  }

  async function setEquipped(cat, id) {
    try {
      state.cosmetics.equipped = await api("equip", { category: cat, item_id: id });
    } catch (e) {
      return toast(e.message, "error");
    }
    await syncOutfit();
    renderGrid();
    onEquippedChange();
  }

  function openEditor(cat, item) {
    const onSaved = async (saved) => {
      const list = state.cosmetics.custom;
      const i = list.findIndex((c) => c.id === saved.id);
      if (i >= 0) list[i] = saved; else list.push(saved);
      await setEquipped(cat, saved.id);
    };
    openCapeEditor({ item, skin: state.skin, onSaved });
  }

  $("#shop-tabs").addEventListener("click", (e) => {
    const tab = e.target.closest("[data-cat]");
    if (!tab) return;
    active = tab.dataset.cat;
    renderTabs();
    renderGrid();
  });

  $("#shop-grid").addEventListener("click", async (e) => {
    const card = e.target.closest(".cos-card");
    if (!card) return;
    const item = findItem(card.dataset.id);
    const act = e.target.closest("[data-act]")?.dataset.act;
    if (act === "remix") {
      const copy = { ...item, custom: false, name: `Mein ${item.name}` };
      return openEditor(item.category, copy);
    }
    if (act === "edit") return openEditor(item.category, item);
    if (act === "delete") {
      if (!(await confirmDialog(`„${item.name}“ löschen?`, "Dein selbst gebautes Cosmetic wird gelöscht.", "Löschen"))) return;
      try {
        state.cosmetics = await api("delete_cosmetic", { item_id: item.id });
      } catch (err) {
        return toast(err.message, "error");
      }
      await syncOutfit();
      renderGrid();
      onEquippedChange();
      return;
    }
    const equipped = state.cosmetics.equipped?.[item.category] === item.id;
    setEquipped(item.category, equipped ? null : item.id);
  });

  $("#shop-create").addEventListener("click", () => openImport(active));
  $("#shop-paint").addEventListener("click", () => openEditor("cape", null));
  $("#shop-templates").addEventListener("click", () => api("open_folder", { target: "models" }).catch((e) => toast(e.message, "error")));

  // Blockbench-Modell (.bbmodel) hochladen
  function openImport(cat) {
    const cats = CATEGORIES.filter((c) => c.importable);
    const m = modal(`
      <h3>Blockbench-Modell laden</h3>
      <p>Baue dein Cosmetic in Blockbench (oder lass es von Claude Design bauen) und speichere es als <b>.bbmodel</b> mit eingebetteter Textur.
        Die Figur steht mit den Füßen auf 0 und schaut nach Norden. Gruppen mit „wing_left/right“, „cape“, „tail“, „ear“, „leg“ oder „glow“ im Namen bewegen bzw. leuchten automatisch.</p>
      <div class="field"><label for="im-file">Datei</label><input class="input" id="im-file" type="file" accept=".bbmodel,.json"></div>
      <div class="field"><label for="im-name">Name</label><input class="input" id="im-name" maxlength="30" placeholder="z. B. Sternen-Flügel"></div>
      <div class="field"><label for="im-cat">Wo sitzt es?</label><select class="input" id="im-cat">${cats.map((c) => `<option value="${c.id}" ${c.id === cat ? "selected" : ""}>${c.icon} ${c.label}</option>`).join("")}</select></div>
      <div class="modal-actions"><button class="btn" data-close>Abbrechen</button><button class="btn primary" id="im-go">Hinzufügen</button></div>`);
    const file = $("#im-file", m.el);
    file.addEventListener("change", () => {
      const f = file.files[0];
      if (f && !$("#im-name", m.el).value) $("#im-name", m.el).value = f.name.replace(/\.(bbmodel|json)$/i, "").slice(0, 30);
    });
    $("#im-go", m.el).addEventListener("click", async (e) => {
      const f = file.files[0];
      if (!f) return toast("Wähle zuerst eine .bbmodel-Datei aus.", "warn");
      setBusy(e.currentTarget, true, "Lädt …");
      try {
        const item = await api("import_model", { name: $("#im-name", m.el).value, category: $("#im-cat", m.el).value, text: await f.text() });
        state.cosmetics.custom.push(item);
        m.close();
        active = item.category;
        renderTabs();
        await setEquipped(item.category, item.id);
        toast(`„${item.name}“ ist da und angelegt 🎉`);
      } catch (err) {
        toast(err.message, "error");
        setBusy(e.currentTarget, false);
      }
    });
  }
  $("#sp-equipped").addEventListener("click", (e) => {
    const btn = e.target.closest("[data-unequip]");
    if (btn) setEquipped(btn.dataset.unequip, null);
  });
  $("#sp-clear").addEventListener("click", async () => {
    for (const cat of Object.keys(state.cosmetics.equipped || {})) {
      state.cosmetics.equipped = await api("equip", { category: cat, item_id: null });
    }
    await syncOutfit();
    renderGrid();
    onEquippedChange();
  });

  return {
    findItem,
    async open() {
      ensureViewer();
      viewer.renderPaused = false;
      renderTabs();
      await applySkin();
      await syncOutfit();
    },
    close() {
      if (viewer) viewer.renderPaused = true;
    },
    skinChanged: applySkin,
    update(dt) {
      outfit?.update(dt);
    },
  };
}
