package net.emberleaf.client.tools;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;

/** Leather armour dyeing: mix dyes exactly like the crafting table does and see the colour. */
public class DyeTool extends ToolScreen {
	private static final String[][] PIECES = {{"leather_helmet", "Lederkappe"}, {"leather_chestplate", "Lederjacke"}, {"leather_leggings", "Lederhose"},
		{"leather_boots", "Lederstiefel"}, {"leather_horse_armor", "Leder-Pferderüstung"}, {"wolf_armor", "Wolfsrüstung"}};
	private static int piece = 1;
	private static final List<DyeColor> DYES = new ArrayList<>(List.of(DyeColor.ORANGE, DyeColor.RED));

	public DyeTool(Screen parent) {
		super("Rüstung färben", "Farbstoffe mischen wie im Spiel", parent);
	}

	@Override
	protected void build() {
		String[] names = new String[PIECES.length];
		for (int i = 0; i < names.length; i++) names[i] = PIECES[i][1];
		cycle(left, top + 12, 180, names, piece, i -> piece = i);
		int cell = cell();
		addRenderableWidget(new ColorChips(left, top + 48, cell, null, c -> {
			if (DYES.size() < 8) DYES.add(c);
			update();
		}));
		button(left, top + 70, 120, "Farbstoffe leeren", DYES::clear);
		button(left + 124, top + 70, 120, "Letzten entfernen", () -> {
			if (!DYES.isEmpty()) DYES.remove(DYES.size() - 1);
		});
	}

	/** Size of the item preview on the right (it has to fit between the title and the result line). */
	private int previewSize() {
		return Math.max(40, Math.min(80, contentBottom() - top - 12));
	}

	private int cell() {
		// the colours share the width with the preview on the right
		return Math.max(9, Math.min(16, (pw - 24 - previewSize() - 16) / 16));
	}

	/** The colour the game gives an undyed item for these dyes (DyedItemColor.applyDyes). */
	static int mix(List<DyeColor> dyes) {
		if (dyes.isEmpty()) return -1;
		int r = 0;
		int g = 0;
		int b = 0;
		int intensity = 0;
		for (DyeColor d : dyes) {
			int c = d.getTextureDiffuseColor();
			int dr = (c >> 16) & 255;
			int dg = (c >> 8) & 255;
			int db = c & 255;
			intensity += Math.max(dr, Math.max(dg, db));
			r += dr;
			g += dg;
			b += db;
		}
		int n = dyes.size();
		int ar = r / n;
		int ag = g / n;
		int ab = b / n;
		float avgIntensity = (float) intensity / n;
		float max = Math.max(ar, Math.max(ag, ab));
		ar = (int) (ar * avgIntensity / max);
		ag = (int) (ag * avgIntensity / max);
		ab = (int) (ab * avgIntensity / max);
		return (ar << 16) | (ag << 8) | ab;
	}

	@Override
	protected String compute() {
		int c = mix(DYES);
		String item = "minecraft:" + PIECES[piece][0];
		return c < 0 ? "/give @s " + item : "/give @s " + item + "[dyed_color=" + c + "]";
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Gegenstand", left, top);
		StringBuilder list = new StringBuilder();
		for (DyeColor d : DYES) list.append(list.isEmpty() ? "" : " + ").append(dyeName(d));
		label(g, fit("Farbstoffe: " + (list.isEmpty() ? "keine (anklicken zum Hinzufügen)" : list), pw - 24 - previewSize() - 10), left, top + 38);
		int c = mix(DYES);
		// the dyed item on the right, as big as the room allows (in steps of 16 so the pixels stay sharp)
		int box = previewSize();
		int icon = Math.max(32, (box - 8) / 16 * 16);
		int x = px + pw - 12 - box;
		int y = top + 12;
		String tex = PIECES[piece][0];
		boolean overlay = tex.startsWith("leather_") && !tex.contains("horse");
		Identifier base = Identifier.withDefaultNamespace("textures/item/" + tex + ".png");
		g.fill(x, y, x + box, y + box, 0x40000000);
		int ix = x + (box - icon) / 2;
		int iy = y + (box - icon) / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, base, ix, iy, 0, 0, icon, icon, 16, 16, 16, 16, c < 0 ? ARGB.opaque(0xA06540) : ARGB.opaque(c));
		if (overlay) {
			g.blit(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("textures/item/" + tex + "_overlay.png"), ix, iy, 0, 0, icon, icon,
				16, 16, 16, 16, -1);
		}
		// the mixed colour under the buttons
		if (c >= 0) {
			int sy = top + 94;
			if (sy + 14 <= contentBottom()) {
				g.fill(left, sy, left + 28, sy + 14, ARGB.opaque(c));
				label(g, TextCodes.hex(c) + "  ·  " + c, left + 34, sy + 3);
			}
		}
	}
}
