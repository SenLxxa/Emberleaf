package net.emberleaf.client.menu;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/** A warm, dark wooden button with an ember stripe that grows on hover. Optionally shows only an icon. */
public class EmberButton extends AbstractButton {
	private final Consumer<EmberButton> action;
	private final @Nullable Identifier icon;
	private float hover;

	public EmberButton(int x, int y, int width, int height, Component message, Consumer<EmberButton> action) {
		this(x, y, width, height, message, null, action);
	}

	public EmberButton(int x, int y, int width, int height, Component message, @Nullable Identifier icon, Consumer<EmberButton> action) {
		super(x, y, width, height, message);
		this.action = action;
		this.icon = icon;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.accept(this);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float target = this.active && this.isHoveredOrFocused() ? 1 : 0;
		hover += (target - hover) * 0.22F;
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		float fade = this.alpha;

		Ui.nineSlice(g, Ui.BUTTON, x, y, w, h, 5, ARGB.white(fade));
		if (hover > 0.01F) Ui.nineSlice(g, Ui.BUTTON_HOVER, x, y, w, h, 5, ARGB.white(fade * hover));

		if (icon != null) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + (w - 15) / 2, y + (h - 15) / 2, 15, 15,
				ARGB.white(fade * (this.active ? 1 : 0.5F)));
			return;
		}
		Component label = Ui.menu(getMessage());
		int color = this.active ? ARGB.srgbLerp(hover, 0xFFEBDCC8, 0xFFFFFFFF) : 0xFF8A7868;
		if (centered) {
			// small keys: the label in the middle, no room for the ember dot
			Ui.text(g, label, x + (w - Ui.font().width(label)) / 2F, y + (h - 9) / 2F + 0.5F, ARGB.multiplyAlpha(color, fade));
			return;
		}
		// small ember dot that slides in on hover
		if (hover > 0.02F) {
			MenuScene.glow(g, x + 7.5F + hover * 1.5F, y + h / 2F, 9, ARGB.color(fade * hover, 0xFFB45A));
			MenuScene.glow(g, x + 7.5F + hover * 1.5F, y + h / 2F, 3.5F, ARGB.color(fade * hover, 0xFFF0C8));
		}
		Ui.text(g, label, x + 11 + hover * 4, y + (h - 9) / 2F + 0.5F, ARGB.multiplyAlpha(color, fade));
	}

	private boolean centered;

	/** Draws the label in the middle (for small key-like buttons). */
	public EmberButton centered() {
		centered = true;
		return this;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
