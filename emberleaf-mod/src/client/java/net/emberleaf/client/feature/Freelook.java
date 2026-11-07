package net.emberleaf.client.feature;

import net.emberleaf.client.config.Modules;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Freelook: hold the key (Left Alt) and the camera turns around you while you keep walking where you were going.
 * Also the AFK camera: after a minute without input the camera slowly circles around you until you move.
 */
public final class Freelook {
	private static final long AFK_MS = 60_000;
	private static @Nullable KeyMapping key;
	private static boolean active;
	private static boolean afk;
	private static float yaw;
	private static float pitch;
	private static @Nullable CameraType before;
	private static long lastInput = System.currentTimeMillis();
	private static float lastPlayerYaw;
	private static float lastPlayerPitch;

	private Freelook() {}

	public static void init(KeyMapping mapping) {
		key = mapping;
	}

	/** True while the camera is not following the player's head (freelook or AFK camera). */
	public static boolean overriding() {
		return active || afk;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null) {
			active = false;
			afk = false;
			return;
		}
		boolean want = key != null && key.isDown() && Modules.on("freelook") && mc.gui.screen() == null;
		if (want && !active) {
			stopAfk(mc);
			start(mc);
			active = true;
		} else if (!want && active) {
			active = false;
			restore(mc);
		}
		afkTick(mc);
	}

	private static void start(Minecraft mc) {
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		before = mc.options.getCameraType();
		if (before.isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
	}

	private static void restore(Minecraft mc) {
		if (before != null) mc.options.setCameraType(before);
		before = null;
	}

	/** Mouse movement while the camera is free: turns the camera instead of the player. */
	public static boolean turn(double dx, double dy) {
		if (dx == 0 && dy == 0) return active || afk; // the game calls this every frame, also without any mouse movement
		if (afk) {
			// moving the mouse wakes you up again
			stopAfk(Minecraft.getInstance());
			lastInput = System.currentTimeMillis();
			return true;
		}
		if (!active) return false;
		yaw += (float) dx * 0.15F;
		pitch = Mth.clamp(pitch + (float) dy * 0.15F, -90, 90);
		return true;
	}

	private static void afkTick(Minecraft mc) {
		long now = System.currentTimeMillis();
		// the pause menu (also when the window loses focus) does not count as input: the camera keeps circling behind it
		boolean screen = mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen);
		boolean input = screen || mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown()
			|| mc.options.keyRight.isDown() || mc.options.keyJump.isDown() || mc.options.keyShift.isDown() || mc.options.keyAttack.isDown()
			|| mc.options.keyUse.isDown() || mc.player.getYRot() != lastPlayerYaw || mc.player.getXRot() != lastPlayerPitch || active
			|| mc.player.hurtTime > 0;
		lastPlayerYaw = mc.player.getYRot();
		lastPlayerPitch = mc.player.getXRot();
		if (input) {
			lastInput = now;
			if (afk) stopAfk(mc);
			return;
		}
		if (!afk && Modules.on("afk_camera") && now - lastInput > AFK_MS) {
			afk = true;
			start(mc);
			pitch = 18;
		}
		if (afk) yaw += 0.35F; // a slow circle around you
	}

	private static void stopAfk(Minecraft mc) {
		if (!afk) return;
		afk = false;
		restore(mc);
	}

	/** For tests: pretend nobody touched anything for a while. */
	public static void forceAfk() {
		lastInput = 0;
	}
}
