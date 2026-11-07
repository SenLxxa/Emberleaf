package net.emberleaf.client.tools;

import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Experience levels ↔ points (Minecraft's own formulas). */
public class XpTool extends ToolScreen {
	private static String from = "0";
	private static String to = "30";
	private static String points = "1395";

	public XpTool(Screen parent) {
		super("XP-Rechner", "Erfahrungslevel und -punkte", parent);
	}

	/** Points needed to reach this level from zero. */
	static long total(int level) {
		if (level <= 16) return (long) level * level + 6L * level;
		if (level <= 31) return Math.round(2.5 * level * level - 40.5 * level + 360);
		return Math.round(4.5 * level * level - 162.5 * level + 2220);
	}

	/** Points to go from this level to the next. */
	static int step(int level) {
		if (level >= 30) return 112 + (level - 30) * 9;
		if (level >= 15) return 37 + (level - 15) * 5;
		return 7 + level * 2;
	}

	@Override
	protected void build() {
		int w = 90;
		field(left, top + 12, w, "Von Level", from, v -> from = v);
		field(left + w + 6, top + 12, w, "Bis Level", to, v -> to = v);
		field(left, top + 80, w * 2 + 6, "Erfahrungspunkte", points, v -> points = v);
	}

	@Override
	protected String compute() {
		int a = Math.max(0, Math.min(21863, parseInt(from, 0)));
		int b = Math.max(0, Math.min(21863, parseInt(to, 30)));
		return "Level " + a + " → " + b + ": " + Math.abs(total(b) - total(a)) + " Punkte";
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Von Level  ·  Bis Level", left, top);
		int a = Math.max(0, Math.min(21863, parseInt(from, 0)));
		int b = Math.max(0, Math.min(21863, parseInt(to, 30)));
		Ui.text(g, Ui.menu("Du brauchst " + Math.abs(total(b) - total(a)) + " Erfahrungspunkte (" + step(Math.min(a, b)) + " für das nächste Level)."), left,
			top + 36, 0xFFFFE6C4);
		Ui.text(g, Ui.menu("Zum Verzaubern (Level 30) ab null: " + total(30) + " Punkte."), left, top + 50, 0xFFB8A48E);
		label(g, "Punkte → Level", left, top + 68);
		long p = Math.max(0, (long) parseDouble(points, 0));
		int level = 0;
		while (level < 21863 && total(level + 1) <= p) level++;
		long rest = p - total(level);
		int pct = (int) (rest * 100 / Math.max(1, step(level)));
		Ui.text(g, Ui.menu(p + " Punkte = Level " + level + " und " + pct + " % zum nächsten"), left, top + 104, 0xFFFFE6C4);
		int barW = Math.min(220, pw - 24);
		g.fill(left, top + 118, left + barW, top + 123, 0xFF1A120C);
		g.fill(left, top + 118, left + barW * pct / 100, top + 123, 0xFF80FF20);
	}
}
