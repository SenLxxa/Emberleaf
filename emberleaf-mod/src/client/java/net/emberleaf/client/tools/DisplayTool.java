package net.emberleaf.client.tools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.joml.Matrix3x2fStack;

/** Display Studio: text, block and item displays (floating text, giant blocks …) as summon commands. */
public class DisplayTool extends ToolScreen {
	private static final String[] TYPES = {"Textanzeige", "Blockanzeige", "Gegenstandsanzeige"};
	private static final String[][] BILLBOARD = {{"center", "Dreht sich zu dir"}, {"fixed", "Fest"}, {"vertical", "Dreht sich seitlich"},
		{"horizontal", "Kippt mit"}};
	private static final String[][] BACKGROUNDS = {{"1073741824", "halb"}, {"0", "ohne"}, {"-16777216", "schwarz"},
		{"-1442840576", "dunkel"}};
	private static int type;
	private static String text = "Willkommen am Lagerfeuer";
	private static String block = "minecraft:campfire";
	private static String item = "minecraft:diamond";
	private static int color = 6;
	private static int billboard;
	private static int background;
	private static boolean shadow = true;
	private static String scale = "1.5";

	public DisplayTool(Screen parent) {
		super("Display Studio", "Schwebende Texte, Blöcke und Gegenstände", parent);
	}

	@Override
	protected void build() {
		int w = (pw - 24 - 8) / 3;
		cycle(left, top + 12, w, TYPES, type, v -> {
			type = v;
			rebuildWidgets();
		});
		String[] bill = new String[BILLBOARD.length];
		for (int i = 0; i < bill.length; i++) bill[i] = BILLBOARD[i][1];
		cycle(left + w + 4, top + 12, w, bill, billboard, v -> billboard = v);
		field(left + 2 * (w + 4), top + 12, w, "Größe, z. B. 1.5", scale, v -> scale = v);
		if (type == 0) {
			field(left, top + 44, pw - 24, "Text", text, v -> text = v);
			String[] bg = new String[BACKGROUNDS.length];
			for (int i = 0; i < bg.length; i++) bg[i] = "Hintergrund: " + BACKGROUNDS[i][1];
			cycle(left, top + 66, w, bg, background, v -> background = v);
			cycle(left + w + 4, top + 66, w, new String[] {"Schatten: an", "Schatten: aus"}, shadow ? 0 : 1, v -> shadow = v == 0);
			cycle(left + 2 * (w + 4), top + 66, w, TextCodes.COLOR_NAMES, color, v -> color = v);
		} else if (type == 1) {
			field(left, top + 44, pw - 24, "Block, z. B. minecraft:campfire", block, v -> block = v);
		} else {
			field(left, top + 44, pw - 24, "Gegenstand, z. B. minecraft:diamond", item, v -> item = v);
		}
	}

	private String transform() {
		double s = parseDouble(scale, 1);
		return "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[" + s + "f," + s + "f," + s + "f]}";
	}

	private static String id(String raw) {
		String t = raw.strip();
		return t.contains(":") ? t : "minecraft:" + t;
	}

	@Override
	protected String compute() {
		String bill = ",billboard:\"" + BILLBOARD[billboard][0] + "\"";
		return switch (type) {
			case 1 -> "/summon block_display ~ ~ ~ {block_state:{Name:\"" + id(block) + "\"}," + transform() + "}";
			case 2 -> "/summon item_display ~ ~1 ~ {item:{id:\"" + id(item) + "\",count:1}" + bill + "," + transform() + "}";
			default -> "/summon text_display ~ ~1.5 ~ {text:{text:" + TextCodes.quote(text) + ",color:\"" + TextCodes.COLOR_IDS[color] + "\"}" + bill
				+ ",background:" + BACKGROUNDS[background][0] + (shadow ? ",shadow:1b" : "") + "," + transform() + "}";
		};
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Art, Ausrichtung und Größe", left, top);
		label(g, type == 0 ? "Text" : type == 1 ? "Block" : "Gegenstand", left, top + 34);
		if (type == 0) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			float scale = Math.max(1, Math.min(2, (contentBottom() - top - 96) / 10F));
			pose.translate(left + 2, top + 96);
			pose.scale(scale, scale);
			int bg = (int) Long.parseLong(BACKGROUNDS[background][0]);
			g.fill(-2, -2, this.font.width(text) + 2, 10, bg);
			g.text(this.font, Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(TextCodes.COLOR_RGB[color]))), 0, 0, 0xFFFFFFFF,
				shadow);
			pose.popMatrix();
		}
	}
}
