package net.emberleaf.client.menu;

import net.emberleaf.client.config.Modules;
import net.emberleaf.client.config.Modules.Module;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** One feature in the Emberleaf menu: icon, name, short description and a switch, slider or choice button. */
public class ModuleCard extends AbstractWidget {
	private final Module module;
	private final Identifier icon;
	private float knob;
	private float hover;

	public ModuleCard(int x, int y, int w, int h, Module module) {
		super(x, y, w, h, Component.literal(module.name()));
		this.module = module;
		// a plain texture, not an ItemStack: item components are not bound yet in the title screen
		this.icon = Identifier.withDefaultNamespace("textures/" + module.icon() + ".png");
		this.knob = Modules.on(module.id()) ? 1 : 0;
	}

	public Module module() {
		return module;
	}

	private boolean hasSwitch() {
		return module.choices().isEmpty();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		Font font = Ui.font();
		boolean on = Modules.on(module.id());
		hover += ((this.isHovered() ? 1 : 0) - hover) * 0.25F;
		knob += ((on ? 1 : 0) - knob) * 0.3F;

		Ui.nineSlice(g, Ui.PANEL, x, y, w, h, 6, ARGB.white(this.alpha));
		if (hover > 0.02F) Ui.nineSlice(g, Ui.BUTTON_HOVER, x, y, w, h, 6, ARGB.white(this.alpha * hover * 0.6F));
		// icon on a soft round plate
		Ui.nineSlice(g, Ui.ROUND, x + 6, y + (h - 20) / 2, 20, 20, 6, ARGB.color(this.alpha * 0.35F, 0x000000));
		g.blit(RenderPipelines.GUI_TEXTURED, icon, x + 8, y + (h - 16) / 2, 0, 0, 16, 16, 16, 16, ARGB.white(this.alpha));

		int tx = x + 32;
		Ui.text(g, Ui.menu(module.name()), tx, y + 5, ARGB.multiplyAlpha(0xFFFFEBD4, this.alpha));
		int nameW = font.width(Ui.menu(module.name()));
		if (module.isNew()) {
			Component neu = Ui.menu("NEU");
			int bw = font.width(neu) + 8;
			Ui.nineSlice(g, Ui.ROUND, tx + nameW + 5, y + 4, bw, 11, 5, ARGB.multiplyAlpha(0xFF3A8FE0, this.alpha));
			Ui.text(g, neu, tx + nameW + 9, y + 5.5F, ARGB.white(this.alpha));
		}
		int right = x + w - 8;
		if (module.slider() != null && on) {
			// slider under the name instead of the description
			Modules.Slider s = module.slider();
			double v = Modules.value(module.id());
			int sx0 = tx;
			int sx1 = right - 56; // room for the value label and the switch
			int sy = y + h - 9;
			Ui.nineSlice(g, Ui.ROUND, sx0, sy - 2, sx1 - sx0, 4, 2, ARGB.color(this.alpha * 0.45F, 0x000000));
			float k = (float) ((v - s.min()) / (s.max() - s.min()));
			Ui.nineSlice(g, Ui.ROUND, sx0, sy - 2, Math.max(4, Math.round((sx1 - sx0) * k)), 4, 2, ARGB.multiplyAlpha(0xFFE8742F, this.alpha));
			int kx = sx0 + Math.round((sx1 - sx0) * k);
			Ui.nineSlice(g, Ui.ROUND, kx - 4, sy - 4, 8, 8, 4, ARGB.white(this.alpha));
			String label = (s.step() >= 1 ? String.valueOf((int) Math.round(v)) : String.format("%.1f", v)) + s.unit();
			Ui.text(g, Ui.menu(label), sx1 + 6, sy - 5, ARGB.multiplyAlpha(0xFFFFD6A8, this.alpha));
		} else {
			String hint = unlockHint();
			Ui.text(g, Ui.menu(trim(font, hint != null ? hint : module.description(), right - tx - (hasSwitch() ? 34 : 76))), tx, y + 15.5F,
				ARGB.multiplyAlpha(hint != null ? 0xFFFFC27A : 0xFFB8A48E, this.alpha));
		}

		if (!module.choices().isEmpty()) {
			String current = Modules.choice(module.id());
			int index = Math.max(0, module.choices().indexOf(current == null ? module.choices().get(0) : current));
			Component label = Ui.menu(module.choiceLabels().get(index) + "  ›");
			int bw = font.width(label) + 14;
			Ui.nineSlice(g, Ui.ROUND, right - bw, y + (h - 16) / 2, bw, 16, 7, ARGB.multiplyAlpha(0xFF5A3A22, this.alpha));
			Ui.text(g, label, right - bw + 7, y + (h - 16) / 2F + 4, ARGB.white(this.alpha));
		} else {
			// switch: ember orange when on, dark when off
			int sw = 24;
			int sh = 12;
			int sx = right - sw;
			int sy = y + (h - sh) / 2;
			int track = ARGB.srgbLerp(knob, 0xFF3A2A22, 0xFFE8742F);
			Ui.nineSlice(g, Ui.ROUND, sx, sy, sw, sh, 6, ARGB.multiplyAlpha(track, this.alpha));
			float kx = sx + 2 + knob * (sw - sh);
			Ui.nineSlice(g, Ui.ROUND, Math.round(kx), sy + 2, sh - 4, sh - 4, 4, ARGB.white(this.alpha));
		}
	}

	private boolean unlocked(String choice) {
		return net.emberleaf.client.feature.Playtime.hours() >= Modules.unlockHours(module.id(), choice);
	}

	/** For playtime rewards: which choice comes next and when, or null when everything is unlocked. */
	private String unlockHint() {
		int bestHours = Integer.MAX_VALUE;
		String best = null;
		for (int i = 0; i < module.choices().size(); i++) {
			int h = Modules.unlockHours(module.id(), module.choices().get(i));
			if (!unlocked(module.choices().get(i)) && h < bestHours) {
				bestHours = h;
				best = module.choiceLabels().get(i);
			}
		}
		if (best == null) return null;
		return "Nächste: " + best + " ab " + bestHours + " h (du: " + net.emberleaf.client.feature.Playtime.format(
			net.emberleaf.client.feature.Playtime.total()) + ")";
	}

	private static String trim(Font font, String text, int max) {
		if (max <= 10 || font.width(Ui.menu(text)) <= max) return text;
		while (!text.isEmpty() && font.width(Ui.menu(text + "…")) > max) text = text.substring(0, text.length() - 1);
		return text + "…";
	}

	private boolean onSlider(double mx, double my) {
		return module.slider() != null && Modules.on(module.id()) && my > getY() + getHeight() - 14 && mx < getX() + getWidth() - 56;
	}

	private void setSlider(double mx) {
		Modules.Slider s = module.slider();
		int sx0 = getX() + 32;
		int sx1 = getX() + getWidth() - 64;
		double k = Mth.clamp((mx - sx0) / (double) (sx1 - sx0), 0, 1);
		double v = s.min() + Math.round(k * (s.max() - s.min()) / s.step()) * s.step();
		Modules.setValue(module.id(), v);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (onSlider(event.x(), event.y())) {
			setSlider(event.x());
			return;
		}
		if (!module.choices().isEmpty()) {
			String current = Modules.choice(module.id());
			int index = Math.max(0, module.choices().indexOf(current == null ? module.choices().get(0) : current));
			int n = module.choices().size();
			// locked choices (playtime rewards) are skipped
			for (int step = 1; step <= n; step++) {
				String next = module.choices().get((index + step) % n);
				if (unlocked(next)) {
					Modules.setChoice(module.id(), next);
					break;
				}
			}
			return;
		}
		Modules.set(module.id(), !Modules.on(module.id()));
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		if (onSlider(event.x(), event.y())) setSlider(event.x());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
