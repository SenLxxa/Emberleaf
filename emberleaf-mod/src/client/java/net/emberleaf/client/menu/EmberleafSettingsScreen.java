package net.emberleaf.client.menu;

import java.util.ArrayList;
import java.util.List;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.config.Modules.Category;
import net.emberleaf.client.config.Modules.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The Emberleaf menu (Right Shift, or the leaf button in the title and pause menu): every feature sorted into
 * categories, each one with a switch, slider or choice, in Emberleaf's warm look.
 */
public class EmberleafSettingsScreen extends Screen {
	private static final int CARD_H = 30;
	private static final int GAP = 5;
	private final @Nullable Screen parent;
	private final List<ModuleCard> cards = new ArrayList<>();
	private @Nullable Category category;
	private int scroll;
	private int contentHeight;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;
	private int headerRight;
	/** Height of the playtime block above "Fertig". */
	private static final int INFO_H = 23;

	public EmberleafSettingsScreen(@Nullable Screen parent) {
		super(Component.literal("Emberleaf"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		cards.clear();
		int side = Math.min(130, Math.max(100, this.width / 5));
		int margin = Math.max(10, this.width / 40);
		int top = 34;
		int y = top + 4;
		// "Alle" and every category share the room above your playtime (two lines) and "Fertig"
		int buttons = Category.values().length + 1;
		int room = this.height - margin - 26 - INFO_H - 6 - y;
		int step = Math.max(15, Math.min(24, room / buttons));
		int bh = Math.min(20, step - 2);
		addRenderableWidget(new EmberButton(margin + 6, y, side - 12, bh, Component.literal("Alle"), b -> select(null)));
		for (Category c : Category.values()) {
			y += step;
			addRenderableWidget(new EmberButton(margin + 6, y, side - 12, bh, Component.literal(c.label), b -> select(c)));
		}
		addRenderableWidget(new EmberButton(margin + 6, this.height - margin - 26, side - 12, 20, Component.literal("Fertig"), b -> onClose()));
		// tools and extras along the top
		String[] labels = {"Werkzeuge", "Screenshots", "AutoText", "Skins"};
		Runnable[] actions = {
			() -> this.minecraft.gui.setScreen(new net.emberleaf.client.tools.ToolsScreen(this)),
			() -> this.minecraft.gui.setScreen(new ScreenshotsScreen(this)),
			() -> this.minecraft.gui.setScreen(new AutoTextScreen(this)),
			() -> this.minecraft.gui.setScreen(new net.emberleaf.client.skin.SkinsScreen(this))};
		int x = this.width - margin;
		for (int i = labels.length - 1; i >= 0; i--) {
			int w = Ui.font().width(Ui.menu(labels[i])) + 22;
			x -= w;
			Runnable action = actions[i];
			addRenderableWidget(new EmberButton(x, 7, w, 18, Component.literal(labels[i]), b -> action.run()));
			x -= 4;
		}
		headerRight = x;

		gridX = margin + side + 10;
		gridY = top + 4;
		gridW = this.width - gridX - margin;
		gridH = this.height - gridY - margin;
		int columns = gridW > 420 ? 2 : 1;
		int cardW = (gridW - (columns - 1) * GAP) / columns;
		int row = 0;
		int col = 0;
		Category last = null;
		int yy = gridY;
		for (Module m : Modules.all()) {
			if (category != null && m.category() != category) continue;
			if (m.category() != last) {
				if (col != 0) {
					row++;
					col = 0;
					yy += CARD_H + GAP;
				}
				if (last != null) yy += 6;
				yy += 16; // room for the section title
				last = m.category();
			}
			ModuleCard card = new ModuleCard(gridX + col * (cardW + GAP), yy, cardW, CARD_H, m);
			cards.add(card);
			addRenderableWidget(card);
			col++;
			if (col >= columns) {
				col = 0;
				row++;
				yy += CARD_H + GAP;
			}
		}
		contentHeight = yy + (col != 0 ? CARD_H : 0) - gridY;
		scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - gridH));
		layout();
	}

	private void select(@Nullable Category c) {
		category = c;
		scroll = 0;
		rebuildWidgets();
	}


	private final List<Integer> base = new ArrayList<>();

	private void layout() {
		if (base.size() != cards.size()) {
			base.clear();
			for (ModuleCard c : cards) base.add(c.getY());
		}
		for (int i = 0; i < cards.size(); i++) {
			ModuleCard c = cards.get(i);
			int y = base.get(i) - scroll;
			c.setY(y);
			c.visible = y + CARD_H > gridY && y < gridY + gridH;
		}
	}

	@Override
	protected void rebuildWidgets() {
		base.clear();
		super.rebuildWidgets();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, contentHeight - gridH);
		scroll = Mth.clamp(scroll - (int) (scrollY * 24), 0, max);
		layout();
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int side = Math.min(130, Math.max(100, this.width / 5));
		int margin = Math.max(10, this.width / 40);
		// sidebar and content panels
		Ui.nineSlice(g, Ui.PANEL, margin, 30, side, this.height - 30 - margin, 8, 0xF0FFFFFF);
		Ui.nineSlice(g, Ui.PANEL, gridX - 6, 30, this.width - gridX - margin + 6, this.height - 30 - margin, 8, 0xC8FFFFFF);
		MenuScene.glow(g, margin + 14, 16, 18, ARGB.color(0.7F, 0xFF9A40));
		Ui.text(g, Ui.title("Emberleaf"), margin + 24, 11, 0xFFFFE6C4);
		Component sub = Ui.menu("Einstellungen & Module");
		int subX = margin + 24 + Ui.font().width(Ui.title("Emberleaf")) + 8;
		if (subX + Ui.font().width(sub) < headerRight - 6) Ui.text(g, sub, subX, 12, 0xFFB8A48E);
		// your playtime and badges at the bottom of the sidebar, right above "Fertig"
		int infoY = this.height - margin - 26 - INFO_H;
		Component label = Ui.menu("Spielzeit");
		Ui.text(g, label, margin + 8, infoY, 0xFFB8A48E);
		Ui.text(g, Ui.menu(net.emberleaf.client.feature.Playtime.format(net.emberleaf.client.feature.Playtime.total())), margin + 8, infoY + 11,
			0xFFFFEBD4);
		// your badges next to "Spielzeit", as many as fit (the best ones first)
		java.util.List<String> mine = net.emberleaf.client.feature.Badges.mine();
		int fit = Math.max(0, (side - 16 - Ui.font().width(label) - 6 + 4) / 14); // a badge is 10 pixels plus a 4 pixel space
		Component badges = net.emberleaf.client.feature.Badges.line(mine.subList(0, Math.min(mine.size(), fit)));
		if (badges != null) g.text(Ui.font(), badges, margin + side - 8 - Ui.font().width(badges), infoY, 0xFFFFFFFF, false);

		g.enableScissor(gridX - 4, gridY - 2, gridX + gridW + 4, gridY + gridH);
		Category last = null;
		for (ModuleCard card : cards) {
			Module m = moduleOf(card);
			if (m != null && m.category() != last) {
				last = m.category();
				int ty = card.getY() - 13;
				if (ty > gridY - 14 && ty < gridY + gridH) {
					Ui.text(g, Ui.menu(last.label), gridX + 2, ty, 0xFFFFD6A8);
					g.fill(gridX + 6 + Ui.font().width(Ui.menu(last.label)), ty + 5, gridX + gridW - 2, ty + 6, 0x30FFE6C8);
				}
			}
		}
		for (ModuleCard card : cards) if (card.visible) card.extractRenderState(g, mouseX, mouseY, a);
		g.disableScissor();
		// the sidebar and header buttons
		for (var child : this.children()) {
			if (child instanceof EmberButton b) b.extractRenderState(g, mouseX, mouseY, a);
		}
		// badge names when hovering your badges
		if (badges != null && mouseX >= margin + side - 8 - Ui.font().width(badges) && mouseX < margin + side - 8 && mouseY >= infoY - 1
			&& mouseY < infoY + 10) {
			java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
			for (String id : net.emberleaf.client.feature.Badges.mine()) {
				var b = net.emberleaf.client.feature.Badges.byId(id);
				if (b != null) lines.add(Component.literal(b.name() + ": " + b.description()).getVisualOrderText());
			}
			g.setTooltipForNextFrame(Ui.font(), lines, mouseX, mouseY);
		}
	}

	private @Nullable Module moduleOf(ModuleCard card) {
		return card.module();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
