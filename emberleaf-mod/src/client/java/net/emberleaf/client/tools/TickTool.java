package net.emberleaf.client.tools;

import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Real time, Minecraft time and game ticks (20 ticks = 1 second, 24000 ticks = one Minecraft day = 20 minutes). */
public class TickTool extends ToolScreen {
	private static final String[] UNITS = {"Ticks", "Sekunden", "Minuten", "Stunden", "Minecraft-Tage"};
	private static final double[] TICKS_PER = {1, 20, 1200, 72000, 24000};
	private static String value = "6000";
	private static int unit;

	public TickTool(Screen parent) {
		super("Tick-Umrechner", "Echtzeit, Minecraft-Zeit und Ticks", parent);
	}

	@Override
	protected void build() {
		field(left, top + 12, 120, "Wert", value, v -> value = v);
		cycle(left + 126, top + 12, 150, UNITS, unit, v -> unit = v);
	}

	private double ticks() {
		return parseDouble(value, 0) * TICKS_PER[unit];
	}

	@Override
	protected String compute() {
		return String.valueOf(Math.round(ticks()));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Wert und Einheit", left, top);
		double t = ticks();
		// rows as tall as the room allows (12 to 14 pixels)
		int row = Math.max(11, Math.min(14, (contentBottom() - top - 40 - 26) / UNITS.length));
		for (int i = 0; i < UNITS.length; i++) {
			double v = t / TICKS_PER[i];
			String s = Math.abs(v - Math.round(v)) < 1e-6 ? String.valueOf(Math.round(v)) : String.format("%.3f", v);
			Ui.text(g, Ui.menu(UNITS[i]), left, top + 38 + i * row, 0xFFB8A48E);
			Ui.text(g, Ui.menu(s), left + 120, top + 38 + i * row, 0xFFFFE6C4);
		}
		long dayTime = Math.floorMod(Math.round(t), 24000L);
		long hour = (dayTime / 1000 + 6) % 24;
		long minute = dayTime % 1000 * 60 / 1000;
		int y = top + 40 + UNITS.length * row;
		Ui.text(g, Ui.menu(String.format("Als Tageszeit (/time set %d): %02d:%02d Uhr", dayTime, hour, minute)), left, y, 0xFFFFD6A8);
		Component hint = Ui.menu("0 = 6 Uhr morgens · 6000 = Mittag · 12000 = Abend · 18000 = Mitternacht");
		if (Ui.font().width(hint) > pw - 24) hint = Ui.menu("0 = Morgen · 6000 = Mittag · 12000 = Abend · 18000 = Nacht");
		if (y + 12 + 9 <= contentBottom()) Ui.text(g, hint, left, y + 12, 0xFFB8A48E);
	}
}
