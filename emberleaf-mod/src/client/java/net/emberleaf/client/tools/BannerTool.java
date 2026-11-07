package net.emberleaf.client.tools;

import java.util.ArrayList;
import java.util.List;
import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;

/** Banner designer: base colour plus up to six pattern layers, previewed with the game's own banner textures. */
public class BannerTool extends ToolScreen {
	static final String[][] PATTERNS = {
		{"stripe_bottom", "Fußleiste"}, {"stripe_top", "Kopfleiste"}, {"stripe_left", "Linke Flanke"}, {"stripe_right", "Rechte Flanke"},
		{"stripe_center", "Pfahl"}, {"stripe_middle", "Balken"}, {"stripe_downright", "Schrägbalken"}, {"stripe_downleft", "Schräglinksbalken"},
		{"small_stripes", "Pfähle"}, {"cross", "Andreaskreuz"}, {"straight_cross", "Kreuz"}, {"triangle_bottom", "Spitze"},
		{"triangle_top", "Gestürzte Spitze"}, {"triangles_bottom", "Gespickelter Fuß"}, {"triangles_top", "Gespickeltes Haupt"},
		{"diagonal_left", "Schräglinks geteilt"}, {"diagonal_right", "Schrägrechts geteilt"}, {"diagonal_up_left", "Schräglinks (unten)"},
		{"diagonal_up_right", "Schrägrechts (unten)"}, {"circle", "Kugel"}, {"rhombus", "Raute"}, {"half_vertical", "Gespalten"},
		{"half_horizontal", "Geteilt"}, {"half_vertical_right", "Gespalten (rechts)"}, {"half_horizontal_bottom", "Geteilt (unten)"},
		{"border", "Bord"}, {"curly_border", "Spickelbord"}, {"gradient", "Farbverlauf"}, {"gradient_up", "Farbverlauf (unten)"},
		{"bricks", "Gemauert"}, {"square_bottom_left", "Rechtes Untereck"}, {"square_bottom_right", "Linkes Untereck"},
		{"square_top_left", "Rechtes Obereck"}, {"square_top_right", "Linkes Obereck"}, {"creeper", "Creeper"}, {"skull", "Schädel"},
		{"flower", "Blume"}, {"mojang", "Logo"}, {"globe", "Globus"}, {"piglin", "Schnauze"}, {"flow", "Fluss"}, {"guster", "Böe"}};

	private record Layer(int pattern, DyeColor color) {}

	private static DyeColor base = DyeColor.WHITE;
	private static final List<Layer> LAYERS = new ArrayList<>(List.of(new Layer(4, DyeColor.ORANGE), new Layer(34, DyeColor.BLACK)));
	private static int selected = 1;

	public BannerTool(Screen parent) {
		super("Banner-Designer", "Muster und Farben kombinieren", parent);
	}

	// layout (computed in build, so it follows the window size): editor on the left, layer list in the middle, flag on the right
	private static final int ROW = 13;
	private int cell;
	private int editY;
	private int listX;
	private int listW;
	private int listY;

	private int previewScale() {
		return Math.max(2, Math.min(4, (ph - 120) / 45));
	}

	@Override
	protected void build() {
		int contentW = pw - 24 - (20 * previewScale() + 18);
		cell = Math.max(9, Math.min(14, (int) (contentW * 0.56F) / 16));
		int leftW = 16 * cell;
		listX = left + leftW + 12;
		listW = Math.max(90, left + contentW - listX);
		listY = top + 11;
		editY = top + 11 + cell + 10;
		addRenderableWidget(new ColorChips(left, top + 11, cell, base, c -> {
			base = c;
			update();
		}));
		if (!LAYERS.isEmpty()) {
			selected = Math.min(selected, LAYERS.size() - 1);
			Layer l = LAYERS.get(selected);
			String[] names = new String[PATTERNS.length];
			for (int i = 0; i < names.length; i++) names[i] = PATTERNS[i][1];
			cycle(left, editY + 11, leftW, names, l.pattern(), i -> LAYERS.set(selected, new Layer(i, LAYERS.get(selected).color())));
			addRenderableWidget(new ColorChips(left, editY + 33, cell, l.color(), c -> {
				LAYERS.set(selected, new Layer(LAYERS.get(selected).pattern(), c));
				update();
			}));
		}
		int half = (listW - 4) / 2;
		int buttonsY = listY + 6 * ROW + 4;
		button(listX, buttonsY, half, "+ Ebene", () -> {
			if (LAYERS.size() < 6) {
				LAYERS.add(new Layer(0, DyeColor.RED));
				selected = LAYERS.size() - 1;
			}
			rebuildWidgets();
		});
		button(listX + half + 4, buttonsY, half, "− Ebene", () -> {
			if (!LAYERS.isEmpty()) {
				LAYERS.remove(Math.min(selected, LAYERS.size() - 1));
				selected = Math.max(0, Math.min(selected, LAYERS.size() - 1));
			}
			rebuildWidgets();
		});
	}

	@Override
	protected String compute() {
		StringBuilder b = new StringBuilder("/give @s minecraft:" + base.getSerializedName() + "_banner");
		if (!LAYERS.isEmpty()) {
			b.append("[banner_patterns=[");
			for (int i = 0; i < LAYERS.size(); i++) {
				Layer l = LAYERS.get(i);
				if (i > 0) b.append(',');
				b.append("{pattern:\"minecraft:").append(PATTERNS[l.pattern()][0]).append("\",color:\"").append(l.color().getSerializedName()).append("\"}");
			}
			b.append("]]");
		}
		return b.toString();
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Grundfarbe: " + dyeName(base), left, top);
		if (LAYERS.isEmpty()) label(g, "Noch keine Ebene: rechts hinzufügen", left, editY);
		else label(g, "Ebene " + (selected + 1) + ": Muster und Farbe", left, editY);
		label(g, "Ebenen (" + LAYERS.size() + "/6)", listX, top);
		for (int i = 0; i < 6; i++) {
			int y = listY + i * ROW;
			if (i >= LAYERS.size()) {
				g.fill(listX, y, listX + listW, y + ROW - 1, 0x18000000);
				continue;
			}
			Layer l = LAYERS.get(i);
			boolean sel = i == selected;
			boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW - 1;
			g.fill(listX, y, listX + listW, y + ROW - 1, sel ? 0x80E8742F : hover ? 0x60000000 : 0x40000000);
			g.fill(listX + 2, y + 2, listX + 10, y + ROW - 3, 0xFF000000 | l.color().getTextureDiffuseColor());
			Ui.text(g, Ui.menu(fit((i + 1) + ". " + PATTERNS[l.pattern()][1], listW - 16)), listX + 14, y + 2, 0xFFFFEBD4);
		}
		// preview with the real banner textures (front of the flag: 20 x 40 pixels at 1,1)
		int scale = previewScale();
		int bx = px + pw - 12 - 20 * scale - 6;
		int by = top + 8;
		g.fill(bx - 3, by - 3, bx + 20 * scale + 3, by + 40 * scale + 3, 0xFF2A1A10);
		flag(g, "base", base, bx, by, scale);
		for (Layer l : LAYERS) flag(g, PATTERNS[l.pattern()][0], l.color(), bx, by, scale);
	}

	private static void flag(GuiGraphicsExtractor g, String pattern, DyeColor color, int x, int y, int scale) {
		Identifier tex = Identifier.withDefaultNamespace("textures/entity/banner/" + pattern + ".png");
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 1, 1, 20 * scale, 40 * scale, 20, 40, 64, 64, ARGB.opaque(color.getTextureDiffuseColor()));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (int i = 0; i < LAYERS.size(); i++) {
			int y = listY + i * ROW;
			if (event.x() >= listX && event.x() < listX + listW && event.y() >= y && event.y() < y + ROW - 1) {
				selected = i;
				rebuildWidgets();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}
