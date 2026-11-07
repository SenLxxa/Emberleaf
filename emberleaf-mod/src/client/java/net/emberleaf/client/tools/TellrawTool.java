package net.emberleaf.client.tools;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.joml.Matrix3x2fStack;

/** Tellraw, title, subtitle and actionbar commands with colour, style and an optional hover text. */
public class TellrawTool extends ToolScreen {
	private static final String[] MODES = {"Chat (tellraw)", "Titel", "Untertitel", "Actionbar"};
	private static String text = "Willkommen am Lagerfeuer!";
	private static String hover = "";
	private static String target = "@a";
	private static int color = 6;
	private static int mode;
	private static boolean bold;
	private static boolean italic;
	private static boolean underlined;

	public TellrawTool(Screen parent) {
		super("Tellraw & Title", "Nachrichten an Spieler schicken", parent);
	}

	@Override
	protected void build() {
		field(left, top + 12, pw - 24, "Text", text, v -> text = v);
		int w = (pw - 24 - 4) / 2;
		field(left, top + 32, w, "Hover-Text (optional)", hover, v -> hover = v);
		field(left + w + 4, top + 32, w, "Ziel, z. B. @a oder Name", target, v -> target = v);
		int cw = (pw - 24 - 3 * 4) / 4;
		cycle(left, top + 54, cw, MODES, mode, i -> mode = i);
		cycle(left + cw + 4, top + 54, cw, new String[] {"Fett: aus", "Fett: an"}, bold ? 1 : 0, i -> bold = i == 1);
		cycle(left + 2 * (cw + 4), top + 54, cw, new String[] {"Kursiv: aus", "Kursiv: an"}, italic ? 1 : 0, i -> italic = i == 1);
		cycle(left + 3 * (cw + 4), top + 54, cw, new String[] {"Unterstr.: aus", "Unterstr.: an"}, underlined ? 1 : 0, i -> underlined = i == 1);
		int cell = Math.max(10, Math.min(18, (pw - 24) / 16));
		addRenderableWidget(new Swatches(left, top + 88, cell, TextCodes.COLOR_RGB, TextCodes.COLOR_NAMES, color, i -> {
			color = i;
			update();
		}));
	}

	private JsonObject component() {
		JsonObject c = new JsonObject();
		c.addProperty("text", text);
		c.addProperty("color", TextCodes.COLOR_IDS[color]);
		if (bold) c.addProperty("bold", true);
		if (italic) c.addProperty("italic", true);
		if (underlined) c.addProperty("underlined", true);
		if (!hover.isBlank()) {
			JsonObject h = new JsonObject();
			h.addProperty("action", "show_text");
			h.addProperty("value", hover);
			c.add("hover_event", h);
		}
		return c;
	}

	@Override
	protected String compute() {
		String who = target.isBlank() ? "@a" : target.strip();
		return switch (mode) {
			case 1 -> "/title " + who + " title " + component();
			case 2 -> "/title " + who + " subtitle " + component();
			case 3 -> "/title " + who + " actionbar " + component();
			default -> "/tellraw " + who + " " + component();
		};
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Text", left, top);
		label(g, "Farbe: " + TextCodes.COLOR_NAMES[color], left, top + 77);
		int cell = Math.max(10, Math.min(18, (pw - 24) / 16));
		int previewY = top + 88 + cell + 6;
		// the preview as big as the room below allows (titles bigger than chat lines)
		float scale = Math.max(1, Math.min(mode == 1 ? 3 : 2, (contentBottom() - previewY) / 9F));
		int previewX = left + 16 * cell + 10;
		boolean beside = previewX + 60 < px + pw - 12 && contentBottom() - previewY < 14;
		if (beside) {
			// no room below: next to the colours, in chat size
			previewX = left + 16 * cell + 10;
			previewY = top + 88;
			scale = 1;
		} else {
			previewX = left;
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(previewX, previewY);
		pose.scale(scale, scale);
		Style style = Style.EMPTY.withColor(TextColor.fromRgb(TextCodes.COLOR_RGB[color])).withBold(bold).withItalic(italic).withUnderlined(underlined);
		g.text(this.font, Component.literal(text).withStyle(style), 0, 0, 0xFFFFFFFF, true);
		pose.popMatrix();
	}
}
