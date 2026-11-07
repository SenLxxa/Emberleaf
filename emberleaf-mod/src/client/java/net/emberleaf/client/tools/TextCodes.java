package net.emberleaf.client.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Minecraft's colour and formatting codes (&amp;a, &amp;l …): names, colours and conversion to JSON text components. */
public final class TextCodes {
	public static final String COLOR_CODES = "0123456789abcdef";
	public static final String[] COLOR_NAMES = {"Schwarz", "Dunkelblau", "Dunkelgrün", "Dunkeltürkis", "Dunkelrot", "Dunkelviolett", "Gold", "Grau",
		"Dunkelgrau", "Blau", "Grün", "Türkis", "Rot", "Hellviolett", "Gelb", "Weiß"};
	public static final String[] COLOR_IDS = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray",
		"blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
	public static final int[] COLOR_RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF,
		0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
	public static final String FORMAT_CODES = "klmnor";
	public static final String[] FORMAT_NAMES = {"Verschleiert", "Fett", "Durchgestrichen", "Unterstrichen", "Kursiv", "Zurücksetzen"};

	private TextCodes() {}

	/** &amp;-codes to §-codes (what signs, books and the MOTD use). */
	public static String toSection(String amp) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < amp.length(); i++) {
			char c = amp.charAt(i);
			if (c == '&' && i + 1 < amp.length() && (COLOR_CODES + FORMAT_CODES).indexOf(Character.toLowerCase(amp.charAt(i + 1))) >= 0) {
				b.append('§');
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}

	/** &amp;-codes to a JSON text component (array of parts). */
	public static String toJson(String amp) {
		JsonArray parts = new JsonArray();
		parts.add("");
		String color = null;
		boolean bold = false;
		boolean italic = false;
		boolean underlined = false;
		boolean strike = false;
		boolean obfuscated = false;
		StringBuilder text = new StringBuilder();
		for (int i = 0; i <= amp.length(); i++) {
			boolean code = i < amp.length() - 1 && amp.charAt(i) == '&' && (COLOR_CODES + FORMAT_CODES).indexOf(Character.toLowerCase(amp.charAt(i + 1))) >= 0;
			if (i == amp.length() || code) {
				if (!text.isEmpty()) {
					JsonObject part = new JsonObject();
					part.addProperty("text", text.toString());
					if (color != null) part.addProperty("color", color);
					if (bold) part.addProperty("bold", true);
					if (italic) part.addProperty("italic", true);
					if (underlined) part.addProperty("underlined", true);
					if (strike) part.addProperty("strikethrough", true);
					if (obfuscated) part.addProperty("obfuscated", true);
					parts.add(part);
					text.setLength(0);
				}
				if (i == amp.length()) break;
				char k = Character.toLowerCase(amp.charAt(i + 1));
				int ci = COLOR_CODES.indexOf(k);
				if (ci >= 0) {
					color = COLOR_IDS[ci];
					bold = italic = underlined = strike = obfuscated = false;
				} else {
					switch (k) {
						case 'k' -> obfuscated = true;
						case 'l' -> bold = true;
						case 'm' -> strike = true;
						case 'n' -> underlined = true;
						case 'o' -> italic = true;
						default -> {
							color = null;
							bold = italic = underlined = strike = obfuscated = false;
						}
					}
				}
				i++;
				continue;
			}
			text.append(amp.charAt(i));
		}
		return parts.size() == 1 ? "\"\"" : parts.size() == 2 && parts.get(1).isJsonObject() && parts.get(1).getAsJsonObject().size() == 1
			? parts.get(1).getAsJsonObject().toString() : parts.toString();
	}

	/** A string for SNBT ("…" with escapes). */
	public static String quote(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/** §-codes for server.properties (the § must be written as §). */
	public static String toProperties(String section) {
		StringBuilder b = new StringBuilder();
		for (char c : section.toCharArray()) {
			if (c == '§') b.append("\\u00A7");
			else if (c > 126) b.append(String.format("\\u%04X", (int) c));
			else b.append(c);
		}
		return b.toString();
	}

	public static String hex(int rgb) {
		return String.format("#%06X", rgb & 0xFFFFFF);
	}

	/** "#ff8a3a" or "ff8a3a" to RGB, or -1. */
	public static int parseHex(String text) {
		String t = text.strip();
		if (t.startsWith("#")) t = t.substring(1);
		if (t.length() != 6) return -1;
		try {
			return Integer.parseInt(t, 16);
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
