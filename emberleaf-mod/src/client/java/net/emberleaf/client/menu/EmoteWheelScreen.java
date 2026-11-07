package net.emberleaf.client.menu;

import net.emberleaf.client.feature.Emotes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** The emote wheel: hold B, point at an emote, let go (or click). */
public class EmoteWheelScreen extends Screen {
	private int selected = -1;
	private float open;

	public EmoteWheelScreen() {
		super(Component.literal("Emotes"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		open = Math.min(1, open + 0.18F);
		int cx = this.width / 2;
		int cy = this.height / 2;
		// an ellipse: wider than high, so twelve labels fit next to each other even on a small window
		float k = 0.6F + 0.4F * open;
		float ry = Math.max(40, Math.min(this.height / 2F - 22, 100));
		float rx = Math.max(60, Math.min(this.width / 2F - 56, ry * 1.8F));
		int n = Emotes.ALL.length;
		double step = Math.PI * 2 / n;
		double start = -Math.PI / 2 + step / 2; // no label straight above or below the centre: they would touch their neighbours
		double dx = (mouseX - cx) / rx;
		double dy = (mouseY - cy) / ry;
		selected = dx * dx + dy * dy > 0.2 * 0.2 ? Math.floorMod((int) Math.round((Math.atan2(dy, dx) - start) / step), n) : -1;
		MenuScene.glow(g, cx, cy, ry * k * 1.35F, ARGB.color(0.35F * open, 0x0B0604));
		for (int i = 0; i < n; i++) {
			double angle = start + i * step;
			float x = cx + (float) Math.cos(angle) * rx * k;
			float y = cy + (float) Math.sin(angle) * ry * k;
			Component label = Ui.menu(Emotes.ALL[i][1]);
			int w = Ui.font().width(label) + 14;
			boolean on = i == selected;
			Ui.nineSlice(g, on ? Ui.BUTTON_HOVER : Ui.BUTTON, Math.round(x - w / 2F), Math.round(y - 9), w, 18, 6, ARGB.white(open));
			Ui.text(g, label, x - w / 2F + 7, y - 4, ARGB.multiplyAlpha(on ? 0xFFFFFFFF : 0xFFEBDCC8, open));
		}
		Component hint = Ui.menu(selected >= 0 ? Emotes.ALL[selected][1] : "Emote wählen");
		Ui.text(g, hint, cx - Ui.font().width(hint) / 2F, cy - 4, ARGB.multiplyAlpha(0xFFFFD6A8, open));
		MenuScene.glow(g, cx, cy, 6 + Mth.sin(System.currentTimeMillis() / 200F) * 1.5F, ARGB.color(0.8F * open, 0xFF9A40));
	}

	private void playSelected() {
		int s = selected;
		onClose();
		if (s >= 0) Emotes.play(this.minecraft, Emotes.ALL[s][0]);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		var key = Emotes.key();
		if (key != null && key.matches(event)) {
			playSelected();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		playSelected();
		return true;
	}
}
