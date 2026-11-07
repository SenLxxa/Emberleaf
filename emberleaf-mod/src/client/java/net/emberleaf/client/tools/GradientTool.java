package net.emberleaf.client.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.joml.Matrix3x2fStack;

/** A soft colour gradient over any text, as tellraw JSON, MiniMessage, &amp;#-codes or §x-codes. */
public class GradientTool extends ToolScreen {
	private static final String[] FORMATS = {"Tellraw-Befehl", "JSON-Text", "MiniMessage", "&#RRGGBB", "§x-Codes"};
	private static String text = "Emberleaf Lagerfeuer";
	private static String from = "#FF8A3A";
	private static String via = "";
	private static String to = "#FFD24A";
	private static boolean bold = true;
	private static int format;

	public GradientTool(Screen parent) {
		super("Farbverlauf", "Weiche Farben über deinen Text", parent);
	}

	@Override
	protected void build() {
		field(left, top + 12, pw - 24, "Text", text, v -> text = v);
		int w = (pw - 24 - 8) / 3;
		field(left, top + 44, w, "Startfarbe #RRGGBB", from, v -> from = v);
		field(left + w + 4, top + 44, w, "Mitte (optional)", via, v -> via = v);
		field(left + 2 * (w + 4), top + 44, w, "Endfarbe", to, v -> to = v);
		cycle(left, top + 68, 110, new String[] {"Fett: an", "Fett: aus"}, bold ? 0 : 1, i -> bold = i == 0);
		cycle(left + 114, top + 68, 170, prefix(FORMATS), format, i -> format = i);
	}

	private static String[] prefix(String[] in) {
		String[] out = new String[in.length];
		for (int i = 0; i < in.length; i++) out[i] = "Ausgabe: " + in[i];
		return out;
	}

	private int[] colors() {
		int[] c = new int[text.length()];
		int a = TextCodes.parseHex(from);
		int m = TextCodes.parseHex(via);
		int b = TextCodes.parseHex(to);
		if (a < 0) a = 0xFFFFFF;
		if (b < 0) b = a;
		for (int i = 0; i < c.length; i++) {
			float t = c.length <= 1 ? 0 : i / (float) (c.length - 1);
			if (m >= 0) c[i] = t < 0.5F ? mix(a, m, t * 2) : mix(m, b, (t - 0.5F) * 2);
			else c[i] = mix(a, b, t);
		}
		return c;
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	@Override
	protected String compute() {
		int[] c = colors();
		StringBuilder s = new StringBuilder();
		switch (format) {
			case 0, 1 -> {
				JsonArray parts = new JsonArray();
				parts.add("");
				for (int i = 0; i < text.length(); i++) {
					JsonObject p = new JsonObject();
					p.addProperty("text", String.valueOf(text.charAt(i)));
					p.addProperty("color", TextCodes.hex(c[i]).toLowerCase());
					if (bold) p.addProperty("bold", true);
					parts.add(p);
				}
				return format == 0 ? "/tellraw @a " + parts : parts.toString();
			}
			case 2 -> {
				s.append("<gradient:").append(TextCodes.hex(TextCodes.parseHex(from) < 0 ? 0xFFFFFF : TextCodes.parseHex(from)));
				if (TextCodes.parseHex(via) >= 0) s.append(':').append(TextCodes.hex(TextCodes.parseHex(via)));
				s.append(':').append(TextCodes.hex(TextCodes.parseHex(to) < 0 ? 0xFFFFFF : TextCodes.parseHex(to))).append('>');
				if (bold) s.append("<bold>");
				s.append(text);
				if (bold) s.append("</bold>");
				return s.append("</gradient>").toString();
			}
			case 3 -> {
				for (int i = 0; i < text.length(); i++) {
					s.append("&").append(TextCodes.hex(c[i]));
					if (bold) s.append("&l");
					s.append(text.charAt(i));
				}
				return s.toString();
			}
			default -> {
				for (int i = 0; i < text.length(); i++) {
					s.append("§x");
					for (char h : String.format("%06x", c[i]).toCharArray()) s.append('§').append(h);
					if (bold) s.append("§l");
					s.append(text.charAt(i));
				}
				return s.toString();
			}
		}
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Text", left, top);
		label(g, "Farben (Hex)", left, top + 32);
		int[] c = colors();
		MutableComponent preview = Component.empty();
		for (int i = 0; i < text.length(); i++) {
			int color = c[i];
			preview.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)).withBold(bold)));
		}
		label(g, "Vorschau", left, top + 94);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(left, top + 108);
		pose.scale(2, 2);
		g.fill(-4, -4, (pw - 24) / 2, 14, 0xA0000000);
		g.text(this.font, preview, 0, 0, 0xFFFFFFFF, true);
		pose.popMatrix();
	}
}
