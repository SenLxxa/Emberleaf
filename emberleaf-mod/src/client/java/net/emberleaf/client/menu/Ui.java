package net.emberleaf.client.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2fStack;

/** Drawing helpers for the Emberleaf menus: smooth Nunito text and rounded, nine-sliced panels. */
public final class Ui {
	public static final FontDescription MENU_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("emberleaf", "menu"));
	public static final FontDescription TITLE_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("emberleaf", "title"));
	public static final Identifier BUTTON = tex("button");
	public static final Identifier BUTTON_HOVER = tex("button_hover");
	public static final Identifier PANEL = tex("panel");
	/** White rounded shape: tint it for pills, switches and badges. */
	public static final Identifier ROUND = tex("round");
	private static final int TEX = 96;
	private static final int TEX_CORNER = 26;

	private Ui() {}

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath("emberleaf", "textures/gui/menu/" + name + ".png");
	}

	public static MutableComponent menu(Component text) {
		// texture packs like "Icons" put little pictures (private-use characters of Minecraft's own font) into button texts;
		// the Emberleaf font has no such pictures, so they are left out here instead of showing as empty boxes
		String plain = text.getString();
		if (plain.codePoints().anyMatch(Ui::privateUse)) {
			StringBuilder b = new StringBuilder();
			plain.codePoints().filter(c -> !privateUse(c)).forEach(b::appendCodePoint);
			return Component.literal(b.toString().strip().replaceAll("\\s{2,}", " ")).withStyle(s -> s.withFont(MENU_FONT));
		}
		return text.copy().withStyle(s -> s.withFont(MENU_FONT));
	}

	private static boolean privateUse(int c) {
		return (c >= 0xE000 && c <= 0xF8FF) || c >= 0xF0000;
	}

	public static MutableComponent menu(String text) {
		return menu(Component.literal(text));
	}

	public static MutableComponent title(String text) {
		return Component.literal(text).withStyle(s -> s.withFont(TITLE_FONT));
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Text with a soft, half-pixel shadow instead of Minecraft's hard one. */
	public static void text(GuiGraphicsExtractor g, Component text, float x, float y, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + 0.6F, y + 0.8F);
		g.text(font(), text, 0, 0, ARGB.color(ARGB.alpha(color) * 0.55F / 255F, 0x120804), false);
		pose.popMatrix();
		pose.pushMatrix();
		pose.translate(x, y);
		g.text(font(), text, 0, 0, color, false);
		pose.popMatrix();
	}

	/** A rounded panel texture stretched to any size without distorting its corners. */
	public static void nineSlice(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h, float corner, int color) {
		float c = Math.min(corner, Math.min(w, h) / 2F);
		Matrix3x2fStack pose = g.pose();
		float[] xs = {x, x + c, x + w - c, x + w};
		float[] ys = {y, y + c, y + h - c, y + h};
		int[] us = {0, TEX_CORNER, TEX - TEX_CORNER, TEX};
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				float dw = xs[col + 1] - xs[col];
				float dh = ys[row + 1] - ys[row];
				int sw = us[col + 1] - us[col];
				int sh = us[row + 1] - us[row];
				if (dw <= 0 || dh <= 0) continue;
				pose.pushMatrix();
				pose.translate(xs[col], ys[row]);
				pose.scale(dw / sw, dh / sh);
				g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, us[col], us[row], sw, sh, sw, sh, TEX, TEX, color);
				pose.popMatrix();
			}
		}
	}
}
