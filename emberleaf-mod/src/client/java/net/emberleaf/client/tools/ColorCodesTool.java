package net.emberleaf.client.tools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/** Colour and formatting codes with a live preview: click a colour to put its code into your text. */
public class ColorCodesTool extends ToolScreen {
	private static String text = "&6Emberleaf &7- &lgemütlich &r&aund schön";
	private static int format;
	private EditBox input;

	public ColorCodesTool(Screen parent) {
		super("Farbcodes", "Farben und Formatierung mit &", parent);
	}

	/** Short names for the format buttons (the full name is in the tooltip of the code). */
	private static final String[] FORMAT_SHORT = {"Magie", "Fett", "Durchgestr.", "Unterstr.", "Kursiv", "Reset"};

	private int cell() {
		// all 16 colours in one row
		return Math.max(16, Math.min(26, (pw - 24 + 2) / 16 - 2));
	}

	@Override
	protected void build() {
		input = field(left, top + 12, pw - 24, "Text mit &-Codes", text, v -> text = v);
		int cell = cell();
		for (int i = 0; i < 16; i++) {
			int idx = i;
			int x = left + i * (cell + 2);
			addRenderableWidget(new net.emberleaf.client.menu.EmberButton(x, top + 34, cell, 18,
				Component.literal((cell >= 22 ? "&" : "") + TextCodes.COLOR_CODES.charAt(i)), b -> {
					input.insertText("&" + TextCodes.COLOR_CODES.charAt(idx));
					update();
				}).centered());
		}
		int fw = (pw - 24 - 5 * 2) / 6;
		for (int i = 0; i < 6; i++) {
			int idx = i;
			button(left + i * (fw + 2), top + 58, fw, FORMAT_SHORT[i], () -> input.insertText("&" + TextCodes.FORMAT_CODES.charAt(idx)));
		}
		cycle(left, top + 82, 160, new String[] {"Ausgabe: §-Codes", "Ausgabe: &-Codes", "Ausgabe: JSON"}, format, i -> format = i);
	}

	@Override
	protected String compute() {
		return switch (format) {
			case 1 -> text;
			case 2 -> TextCodes.toJson(text);
			default -> TextCodes.toSection(text);
		};
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Dein Text", left, top);
		int cell = cell();
		for (int i = 0; i < 16; i++) {
			int x = left + i * (cell + 2);
			g.fill(x + 1, top + 50, x + cell - 1, top + 53, 0xFF000000 | TextCodes.COLOR_RGB[i]);
		}
		label(g, "Vorschau", left + 172, top + 87);
		// the preview as big as the room below allows
		int py0 = top + 108;
		float scale = Math.max(1, Math.min(2, (contentBottom() - py0) / 10F));
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(left + 4, py0 + 3);
		pose.scale(scale, scale);
		g.fill(-4, -3, (int) ((pw - 24) / scale) - 4, 11, 0xA0000000);
		g.text(this.font, Component.literal(TextCodes.toSection(text)), 0, 0, 0xFFFFFFFF, true);
		pose.popMatrix();
		for (int i = 0; i < 16; i++) {
			int x = left + i * (cell + 2);
			int y = top + 34;
			if (mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + 18) {
				g.setTooltipForNextFrame(this.font, Component.literal(TextCodes.COLOR_NAMES[i] + "  (" + TextCodes.hex(TextCodes.COLOR_RGB[i]) + ")"), mouseX,
					mouseY);
			}
		}
	}
}
