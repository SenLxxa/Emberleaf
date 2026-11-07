package net.emberleaf.client.feature;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Hold the zoom key to zoom in smoothly; the mouse wheel changes how far while zooming. */
public final class Zoom {
	private static final float MIN = 0.05F;
	private static final float MAX = 0.6F;

	private static KeyMapping key;
	private static float target = 0.25F;
	private static float current = 1.0F;
	private static float previous = 1.0F;
	private static boolean smoothBefore;
	private static boolean active;

	private Zoom() {}

	public static void init(KeyMapping zoomKey) {
		key = zoomKey;
	}

	public static boolean isZooming() {
		return active;
	}

	public static void tick(Minecraft mc) {
		if (!net.emberleaf.client.config.Modules.on("zoom")) { active = false; return; }
		boolean down = key != null && key.isDown() && mc.gui.screen() == null;
		if (down && !active) {
			smoothBefore = mc.options.smoothCamera;
			mc.options.smoothCamera = true; // sanfte Kamera wie bei einem Fernglas
		} else if (!down && active) {
			mc.options.smoothCamera = smoothBefore;
			target = 0.25F;
		}
		active = down;
		previous = current;
		current += ((active ? target : 1.0F) - current) * 0.5F;
	}

	/** Called from the camera; multiplies the field of view. */
	public static float factor(float partialTicks) {
		return Mth.lerp(partialTicks, previous, current);
	}

	public static void scroll(double amount) {
		target = Mth.clamp(target * (amount > 0 ? 0.8F : 1.25F), MIN, MAX);
	}
}
