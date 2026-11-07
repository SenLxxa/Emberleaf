package net.emberleaf.client.skin;

import java.util.ArrayList;
import java.util.List;
import net.emberleaf.client.menu.EmberButton;
import net.emberleaf.client.menu.MenuScene;
import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Switch skins in game: the launcher's skin collection as cards, one click to wear one. */
public class SkinsScreen extends Screen {
	private static final int CARD_W = 58;
	private static final int CARD_H = 84;
	private static final int GAP = 6;
	private final @Nullable Screen parent;
	private final List<Card> cards = new ArrayList<>();
	private List<SkinLibrary.Entry> skins = List.of();
	private SkinLibrary.@Nullable Entry selected;
	private @Nullable EmberButton wear;
	private String status = "";
	private int statusColor = 0xFFB8A48E;
	private boolean busy;
	private int scroll;
	private int contentHeight;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;

	public SkinsScreen(@Nullable Screen parent) {
		super(Component.literal("Skins"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (skins.isEmpty()) skins = new ArrayList<>(SkinLibrary.load());
		if (selected == null && !skins.isEmpty()) selected = skins.get(0);
		cards.clear();
		int margin = Math.max(10, this.width / 40);
		int side = Math.min(170, Math.max(120, this.width / 4));
		int top = 34;
		gridX = margin + side + 14;
		gridY = top + 8;
		gridW = this.width - gridX - margin - 6;
		gridH = this.height - gridY - margin - 6;

		wear = addRenderableWidget(new EmberButton(margin + 8, this.height - margin - 54, side - 16, 20, Component.literal("Anziehen"), b -> wearSelected()));
		addRenderableWidget(new EmberButton(margin + 8, this.height - margin - 30, (side - 20) / 2, 20, Component.literal("Ordner"),
			b -> com.mojang.blaze3d.Blaze3D.openPath(SkinLibrary.folder())));
		addRenderableWidget(new EmberButton(margin + 12 + (side - 20) / 2, this.height - margin - 30, (side - 20) / 2, 20, Component.literal("Fertig"),
			b -> onClose()));

		int columns = Math.max(1, (gridW + GAP) / (CARD_W + GAP));
		int used = columns * (CARD_W + GAP) - GAP;
		int x0 = gridX + (gridW - used) / 2;
		for (int i = 0; i < skins.size(); i++) {
			Card card = new Card(x0 + (i % columns) * (CARD_W + GAP), gridY + (i / columns) * (CARD_H + GAP), skins.get(i));
			cards.add(card);
			addRenderableWidget(card);
		}
		contentHeight = skins.isEmpty() ? 0 : ((skins.size() - 1) / columns + 1) * (CARD_H + GAP) - GAP;
		scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - gridH));
		layout();
		updateWear();
	}

	private void layout() {
		for (Card c : cards) {
			c.setY(c.baseY - scroll);
			c.visible = c.getY() + CARD_H > gridY && c.getY() < gridY + gridH;
		}
	}

	private void updateWear() {
		if (wear != null) wear.active = selected != null && !busy;
	}

	private void wearSelected() {
		SkinLibrary.Entry e = selected;
		if (e == null || busy) return;
		busy = true;
		status = "Lädt „" + e.name() + "“ hoch …";
		statusColor = 0xFFFFD6A8;
		updateWear();
		SkinLibrary.upload(e).thenAccept(error -> this.minecraft.execute(() -> {
			busy = false;
			if (error == null) {
				SkinLibrary.wearLocally(e);
				status = "Du trägst jetzt „" + e.name() + "“. Andere sehen ihn nach dem Neubetreten.";
				statusColor = 0xFF9FE37A;
			} else {
				status = error;
				statusColor = 0xFFFF8A6A;
			}
			updateWear();
		}));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Mth.clamp(scroll - (int) (scrollY * 24), 0, Math.max(0, contentHeight - gridH));
		layout();
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int margin = Math.max(10, this.width / 40);
		int side = Math.min(170, Math.max(120, this.width / 4));
		Ui.nineSlice(g, Ui.PANEL, margin, 30, side, this.height - 30 - margin, 8, 0xF0FFFFFF);
		Ui.nineSlice(g, Ui.PANEL, gridX - 8, 30, this.width - gridX - margin + 8, this.height - 30 - margin, 8, 0xC8FFFFFF);
		MenuScene.glow(g, margin + 14, 16, 18, ARGB.color(0.7F, 0xFF9A40));
		Ui.text(g, Ui.title("Skins"), margin + 24, 11, 0xFFFFE6C4);
		Ui.text(g, Ui.menu("Deine Sammlung aus dem Launcher"), margin + 24 + Ui.font().width(Ui.title("Skins")) + 8, 12, 0xFFB8A48E);

		// big preview of the selected skin
		SkinLibrary.Entry e = selected;
		int previewH = this.height - margin - 64 - 40;
		if (e != null && previewH > 60) {
			int scale = Math.max(2, Math.min((side - 40) / 16, previewH / 34));
			int pw = 16 * scale;
			int px = margin + (side - pw) / 2;
			int py = 42 + Math.max(0, (previewH - 32 * scale - 24) / 2);
			MenuScene.glow(g, px + pw / 2F, py + 18 * scale, 20 * scale, ARGB.color(0.35F, 0xFF9A40));
			drawFront(g, e, px, py, scale);
			Component name = Ui.menu(e.name());
			Ui.text(g, name, margin + (side - Ui.font().width(name)) / 2F, py + 32 * scale + 6, 0xFFFFEBD4);
			Component kind = Ui.menu(e.slim() ? "Schmale Arme" : "Breite Arme");
			Ui.text(g, kind, margin + (side - Ui.font().width(kind)) / 2F, py + 32 * scale + 17, 0xFFB8A48E);
		}

		g.enableScissor(gridX - 4, gridY - 2, gridX + gridW + 4, gridY + gridH);
		if (skins.isEmpty()) {
			Component hint = Ui.menu("Noch keine Skins gespeichert.");
			Component hint2 = Ui.menu("Im Launcher unter „Skins“ hinzufügen, dann hier wechseln.");
			Ui.text(g, hint, gridX + (gridW - Ui.font().width(hint)) / 2F, gridY + gridH / 2F - 12, 0xFFFFEBD4);
			Ui.text(g, hint2, gridX + (gridW - Ui.font().width(hint2)) / 2F, gridY + gridH / 2F + 2, 0xFFB8A48E);
		}
		for (Card c : cards) if (c.visible) c.extractRenderState(g, mouseX, mouseY, a);
		g.disableScissor();

		if (!status.isEmpty()) {
			Component s = Ui.menu(status);
			int sw = Ui.font().width(s) + 16;
			int sx = gridX + (gridW - sw) / 2;
			int sy = this.height - margin - 22;
			Ui.nineSlice(g, Ui.ROUND, sx, sy, sw, 16, 7, 0xE0201812);
			Ui.text(g, s, sx + 8, sy + 4, statusColor);
		}
		for (var child : this.children()) {
			if (child instanceof EmberButton b) b.extractRenderState(g, mouseX, mouseY, a);
		}
	}

	/** Flat front view of a skin (16 x 32 skin pixels) at the given scale. */
	static void drawFront(GuiGraphicsExtractor g, SkinLibrary.Entry e, int x, int y, int s) {
		boolean legacy = e.texHeight() == 32;
		int aw = e.slim() ? 3 : 4;
		part(g, e, 8, 8, 8, 8, x + 4 * s, y, s); // head
		part(g, e, 20, 20, 8, 12, x + 4 * s, y + 8 * s, s); // body
		part(g, e, 4, 20, 4, 12, x + 4 * s, y + 20 * s, s); // right leg
		part(g, e, legacy ? 4 : 20, legacy ? 20 : 52, 4, 12, x + 8 * s, y + 20 * s, s); // left leg
		part(g, e, 44, 20, aw, 12, x + (4 - aw) * s, y + 8 * s, s); // right arm
		part(g, e, legacy ? 44 : 36, legacy ? 20 : 52, aw, 12, x + 12 * s, y + 8 * s, s); // left arm
		if (!legacy) {
			part(g, e, 20, 36, 8, 12, x + 4 * s, y + 8 * s, s);
			part(g, e, 4, 36, 4, 12, x + 4 * s, y + 20 * s, s);
			part(g, e, 4, 52, 4, 12, x + 8 * s, y + 20 * s, s);
			part(g, e, 44, 36, aw, 12, x + (4 - aw) * s, y + 8 * s, s);
			part(g, e, 52, 52, aw, 12, x + 12 * s, y + 8 * s, s);
		}
		part(g, e, 40, 8, 8, 8, x + 4 * s, y, s); // hat
	}

	private static void part(GuiGraphicsExtractor g, SkinLibrary.Entry e, int u, int v, int w, int h, int x, int y, int s) {
		g.blit(RenderPipelines.GUI_TEXTURED, e.texture(), x, y, u, v, w * s, h * s, w, h, 64, e.texHeight(), -1);
	}

	@Override
	public void onClose() {
		SkinLibrary.release();
		this.minecraft.gui.setScreen(parent);
	}

	private class Card extends AbstractWidget {
		final SkinLibrary.Entry entry;
		final int baseY;
		float hover;

		Card(int x, int y, SkinLibrary.Entry entry) {
			super(x, y, CARD_W, CARD_H, Component.literal(entry.name()));
			this.entry = entry;
			this.baseY = y;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			int x = getX();
			int y = getY();
			hover += ((isHovered() ? 1 : 0) - hover) * 0.25F;
			boolean sel = entry == selected;
			Ui.nineSlice(g, Ui.PANEL, x, y, CARD_W, CARD_H, 6, -1);
			if (hover > 0.02F || sel) Ui.nineSlice(g, Ui.BUTTON_HOVER, x, y, CARD_W, CARD_H, 6, ARGB.white(sel ? 0.9F : hover * 0.6F));
			int s = 2;
			drawFront(g, entry, x + (CARD_W - 16 * s) / 2, y + 6 - Math.round(hover * 2), s);
			String name = entry.name();
			while (name.length() > 1 && Ui.font().width(Ui.menu(name)) > CARD_W - 6) name = name.substring(0, name.length() - 1);
			if (!name.equals(entry.name())) name = name.substring(0, Math.max(1, name.length() - 1)) + "…";
			Component label = Ui.menu(name);
			Ui.text(g, label, x + (CARD_W - Ui.font().width(label)) / 2F, y + CARD_H - 14, sel ? 0xFFFFFFFF : 0xFFFFEBD4);
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			selected = entry;
			if (doubleClick) wearSelected();
			updateWear();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
