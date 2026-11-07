package net.emberleaf.client.tools;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

/** Firework rockets: flight time and one explosion with shape, colours, fade colours, trail and twinkle. */
public class FireworkTool extends ToolScreen {
	private static final String[][] SHAPES = {{"small_ball", "Kleine Kugel"}, {"large_ball", "Große Kugel"}, {"star", "Stern"}, {"creeper", "Creeper"},
		{"burst", "Explosion"}};
	private static int flight = 2;
	private static int shape = 2;
	private static final Set<DyeColor> COLORS = new LinkedHashSet<>(List.of(DyeColor.ORANGE, DyeColor.YELLOW));
	private static final Set<DyeColor> FADE = new LinkedHashSet<>(List.of(DyeColor.RED));
	private static boolean trail = true;
	private static boolean twinkle = true;
	private static int count = 16;

	public FireworkTool(Screen parent) {
		super("Feuerwerk", "Raketen mit Effekten und Farben", parent);
	}

	@Override
	protected void build() {
		int w = (pw - 24 - 12) / 4;
		cycle(left, top + 12, w, new String[] {"Flugdauer 1", "Flugdauer 2", "Flugdauer 3"}, flight - 1, i -> flight = i + 1);
		String[] shapes = new String[SHAPES.length];
		for (int i = 0; i < shapes.length; i++) shapes[i] = SHAPES[i][1];
		cycle(left + w + 4, top + 12, w, shapes, shape, i -> shape = i);
		cycle(left + 2 * (w + 4), top + 12, w, new String[] {"Schweif: an", "Schweif: aus"}, trail ? 0 : 1, i -> trail = i == 0);
		cycle(left + 3 * (w + 4), top + 12, w, new String[] {"Funkeln: an", "Funkeln: aus"}, twinkle ? 0 : 1, i -> twinkle = i == 0);
		int cell = Math.max(10, Math.min(16, (pw - 24) / 16));
		addRenderableWidget(new ColorChips(left, top + 48, cell, null, c -> {
			if (!COLORS.remove(c) && COLORS.size() < 6) COLORS.add(c);
			update();
		}));
		addRenderableWidget(new ColorChips(left, top + 82, cell, null, c -> {
			if (!FADE.remove(c) && FADE.size() < 6) FADE.add(c);
			update();
		}));
		field(left, top + 112, 60, "Anzahl", String.valueOf(count), v -> count = Math.max(1, Math.min(64, parseInt(v, 1))));
	}

	private static String ints(Set<DyeColor> set) {
		StringBuilder b = new StringBuilder("[I;");
		int i = 0;
		for (DyeColor c : set) {
			if (i++ > 0) b.append(',');
			b.append(c.getFireworkColor());
		}
		return b.append(']').toString();
	}

	@Override
	protected String compute() {
		StringBuilder e = new StringBuilder("{shape:\"" + SHAPES[shape][0] + "\",colors:" + ints(COLORS.isEmpty() ? Set.of(DyeColor.WHITE) : COLORS));
		if (!FADE.isEmpty()) e.append(",fade_colors:").append(ints(FADE));
		if (trail) e.append(",has_trail:true");
		if (twinkle) e.append(",has_twinkle:true");
		e.append('}');
		return "/give @s minecraft:firework_rocket[fireworks={flight_duration:" + flight + ",explosions:[" + e + "]}]" + (count > 1 ? " " + count : "");
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Farben (anklicken = an/aus): " + names(COLORS), left, top + 38);
		label(g, "Übergangsfarben: " + names(FADE), left, top + 72);
		label(g, "Anzahl", left + 66, top + 116);
		// a little burst as preview
		int cx = px + pw - 70;
		int cy = top + 150;
		if (cy + 50 > py + ph - 62) cy = py + ph - 112;
		g.fill(cx - 52, cy - 52, cx + 52, cy + 52, 0xFF0A0A1A);
		List<DyeColor> cols = new ArrayList<>(COLORS.isEmpty() ? List.of(DyeColor.WHITE) : COLORS);
		long t = System.currentTimeMillis();
		int n = SHAPES[shape][0].equals("large_ball") ? 40 : 24;
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			double r = shapeRadius(a) * (SHAPES[shape][0].equals("large_ball") ? 44 : 32);
			int x = cx + (int) (Math.cos(a) * r);
			int y = cy + (int) (Math.sin(a) * r);
			boolean faded = !FADE.isEmpty() && (t / 400 + i) % 3 == 0;
			int color = 0xFF000000 | (faded ? new ArrayList<>(FADE).get(i % FADE.size()) : cols.get(i % cols.size())).getFireworkColor();
			int s = twinkle && (t / 150 + i) % 4 == 0 ? 1 : 2;
			g.fill(x - s, y - s, x + s, y + s, color);
			if (trail) g.fill(x - 1, y + 3, x, y + 6, (color & 0x00FFFFFF) | 0x80000000);
		}
	}

	private double shapeRadius(double a) {
		return switch (SHAPES[shape][0]) {
			case "star" -> 0.55 + 0.45 * Math.abs(Mth.cos((float) (a * 2.5)));
			case "creeper" -> 0.6 + 0.4 * Math.abs(Mth.sin((float) (a * 2)));
			case "burst" -> 0.3 + 0.7 * ((a * 7) % 1.0);
			default -> 1.0;
		};
	}

	private static String names(Set<DyeColor> set) {
		if (set.isEmpty()) return "–";
		List<String> n = new ArrayList<>();
		for (DyeColor c : set) n.add(dyeName(c));
		String s = String.join(", ", n);
		return s.length() > 60 ? s.substring(0, 57) + "…" : s;
	}

	@Override
	protected boolean hasResult() {
		return true;
	}

	@SuppressWarnings("unused")
	private static String ui(String s) {
		return Ui.menu(s).getString();
	}
}
