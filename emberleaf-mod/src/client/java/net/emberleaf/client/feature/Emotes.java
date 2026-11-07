package net.emberleaf.client.feature;

import net.emberleaf.client.outfit.OutfitStore;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Plays the emote you equipped in the launcher (key B). The camera swings to the front so you can watch
 * yourself, and comes back when the emote ends or you start walking.
 */
public final class Emotes {
	private static final long DURATION_MS = 3600;
	private static @Nullable KeyMapping key;
	private static @Nullable String playing;
	private static long startedAt;
	private static int playerId = -1;
	private static @Nullable CameraType cameraBefore;

	private Emotes() {}

	public static void init(KeyMapping mapping) {
		key = mapping;
	}

	/** Every emote of the game: id and German name (also the order on the emote wheel). */
	public static final String[][] ALL = {
		{"emote-wave", "Winken"}, {"emote-cheer", "Jubeln"}, {"emote-dance", "Tanzen"}, {"emote-clap", "Klatschen"},
		{"emote-jump", "Springen"}, {"emote-spin", "Drehen"}, {"emote-nod", "Nicken"}, {"emote-facepalm", "Facepalm"},
		{"emote-run", "Rennen"}, {"emote-fly", "Fliegen"}, {"emote-swim", "Schwimmen"}, {"emote-sneak", "Schleichen"}};
	private static boolean wasDown;
	private static long pressedAt;
	private static boolean wheelOpened;

	public static @Nullable KeyMapping key() {
		return key;
	}

	public static void tick(Minecraft mc) {
		tickRemote(mc);
		if (key == null || mc.player == null) return;
		// tap B: your equipped emote; hold B: the emote wheel
		boolean down = key.isDown() && mc.gui.screen() == null;
		long now = System.currentTimeMillis();
		if (down && !wasDown) {
			pressedAt = now;
			wheelOpened = false;
		}
		if (down && !wheelOpened && now - pressedAt > 220 && net.emberleaf.client.config.Modules.on("emotes")) {
			wheelOpened = true;
			mc.gui.setScreen(new net.emberleaf.client.menu.EmoteWheelScreen());
		}
		if (!down && wasDown && !wheelOpened) playEquipped(mc);
		wasDown = down;
		while (key.consumeClick()) {
			// handled above by the key state
		}
		if (playing == null) return;
		boolean moving = mc.player.input.hasForwardImpulse() || mc.player.input.keyPresses.jump()
			|| Math.abs(mc.player.input.getMoveVector().x) > 0.01F;
		if (System.currentTimeMillis() - startedAt > DURATION_MS || moving) stop(mc);
	}

	/** Plays the emote equipped in the launcher, or explains how to equip one. */
	public static void playEquipped(Minecraft mc) {
		if (!net.emberleaf.client.config.Modules.on("emotes")) return;
		String emote = OutfitStore.current().emote();
		if (emote == null) {
			mc.gui.hud.setOverlayMessage(Component.literal("Leg im Emberleaf-Launcher unter Cosmetics ein Emote an"), false);
		} else {
			start(mc, emote);
		}
	}

	/** Plays any emote (emote wheel). */
	public static void play(Minecraft mc, String emote) {
		if (mc.player == null || !net.emberleaf.client.config.Modules.on("emotes")) return;
		start(mc, emote);
	}

	/** The emote you are playing right now and since when (for friends to see it), or null. */
	public static @Nullable String playing() {
		return playing;
	}

	public static long startedAt() {
		return startedAt;
	}

	private static void start(Minecraft mc, String emote) {
		playing = emote;
		startedAt = System.currentTimeMillis();
		playerId = mc.player.getId();
		if (cameraBefore == null && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
			cameraBefore = mc.options.getCameraType();
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		}
	}

	private static void stop(Minecraft mc) {
		playing = null;
		playerId = -1;
		if (cameraBefore != null) {
			mc.options.setCameraType(cameraBefore);
			cameraBefore = null;
		}
	}

	// ---------- emotes of other Emberleaf players (from the Lagerfeuer) ----------

	private record Remote(String emote, long startedAt, double x, double z) {}

	private static final java.util.Map<Integer, Remote> REMOTE = new java.util.HashMap<>();

	/** Another player started an emote {@code ageMs} ago (reported by the Lagerfeuer): play it on their model. */
	public static void playRemote(Minecraft mc, java.util.UUID uuid, String emote, long ageMs) {
		if (mc.level == null || !net.emberleaf.client.config.Modules.on("emotes") || ageMs >= DURATION_MS) return;
		net.minecraft.world.entity.player.Player p = mc.level.getPlayerByUUID(uuid);
		if (p == null || p == mc.player) return;
		long started = System.currentTimeMillis() - ageMs;
		Remote current = REMOTE.get(p.getId());
		// the next sync reports the same emote again: keep it running smoothly
		if (current != null && current.emote().equals(emote) && Math.abs(current.startedAt() - started) < 1500) return;
		REMOTE.put(p.getId(), new Remote(emote, started, p.getX(), p.getZ()));
	}

	/** Ends other players' emotes when they are over or the player walks away. */
	private static void tickRemote(Minecraft mc) {
		if (REMOTE.isEmpty()) return;
		long now = System.currentTimeMillis();
		REMOTE.entrySet().removeIf(e -> {
			if (mc.level == null || now - e.getValue().startedAt() > DURATION_MS) return true;
			net.minecraft.world.entity.Entity entity = mc.level.getEntity(e.getKey());
			if (entity == null) return true;
			double dx = entity.getX() - e.getValue().x();
			double dz = entity.getZ() - e.getValue().z();
			return dx * dx + dz * dz > 0.6 * 0.6;
		});
	}

	/** Called after the vanilla pose was set up (PlayerModelMixin). */
	public static void apply(PlayerModel m, int entityId) {
		String emote = playing;
		long since = startedAt;
		if (emote == null || entityId != playerId) {
			Remote remote = REMOTE.get(entityId);
			if (remote == null) return;
			emote = remote.emote();
			since = remote.startedAt();
		}
		float t = (System.currentTimeMillis() - since) / 1000F;
		float ease = Mth.clamp(t * 4, 0, 1) * Mth.clamp((DURATION_MS / 1000F - t) * 4, 0, 1);
		float bob = 0;
		switch (emote) {
			case "emote-wave" -> {
				m.rightArm.xRot = lerp(m.rightArm.xRot, -2.7F, ease);
				m.rightArm.zRot = lerp(m.rightArm.zRot, 0.25F + Mth.sin(t * 11) * 0.35F, ease);
				m.head.zRot = lerp(m.head.zRot, -0.1F, ease);
			}
			case "emote-dance", "emote-spin" -> {
				float s = t * Mth.TWO_PI * 1.2F;
				bob = Math.abs(Mth.sin(s)) * 1.2F;
				m.body.yRot = Mth.sin(s) * 0.25F * ease;
				m.head.zRot = Mth.sin(s) * 0.15F * ease;
				m.leftArm.zRot = lerp(m.leftArm.zRot, -(0.6F + Mth.sin(s) * 0.5F), ease);
				m.rightArm.zRot = lerp(m.rightArm.zRot, 0.6F - Mth.sin(s) * 0.5F, ease);
				m.leftArm.xRot = lerp(m.leftArm.xRot, Mth.cos(s) * 0.5F, ease);
				m.rightArm.xRot = lerp(m.rightArm.xRot, -Mth.cos(s) * 0.5F, ease);
				m.leftLeg.xRot = lerp(m.leftLeg.xRot, Mth.sin(s) * 0.35F, ease);
				m.rightLeg.xRot = lerp(m.rightLeg.xRot, -Mth.sin(s) * 0.35F, ease);
			}
			case "emote-cheer" -> {
				float s = t * Mth.TWO_PI * 1.4F;
				bob = Math.max(0, Mth.sin(s)) * 2.5F;
				m.leftArm.xRot = lerp(m.leftArm.xRot, -Mth.PI, ease);
				m.rightArm.xRot = lerp(m.rightArm.xRot, -Mth.PI, ease);
				m.leftArm.zRot = lerp(m.leftArm.zRot, 0.35F + Mth.sin(s * 2) * 0.15F, ease);
				m.rightArm.zRot = lerp(m.rightArm.zRot, -0.35F - Mth.sin(s * 2) * 0.15F, ease);
				m.head.xRot = lerp(m.head.xRot, -0.25F, ease);
			}
			case "emote-jump" -> {
				float h = Math.max(0, Mth.sin(t * Mth.TWO_PI));
				bob = h * 4;
				m.leftLeg.xRot = lerp(m.leftLeg.xRot, -h * 0.5F, ease);
				m.rightLeg.xRot = lerp(m.rightLeg.xRot, h * 0.3F, ease);
				m.leftArm.zRot = lerp(m.leftArm.zRot, -h * 0.9F, ease);
				m.rightArm.zRot = lerp(m.rightArm.zRot, h * 0.9F, ease);
			}
			case "emote-clap" -> {
				float a = (Mth.sin(t * 12) + 1) / 2;
				m.leftArm.xRot = lerp(m.leftArm.xRot, -1.3F, ease);
				m.rightArm.xRot = lerp(m.rightArm.xRot, -1.3F, ease);
				m.leftArm.yRot = lerp(m.leftArm.yRot, -(0.15F + a * 0.4F), ease);
				m.rightArm.yRot = lerp(m.rightArm.yRot, 0.15F + a * 0.4F, ease);
				m.head.xRot = lerp(m.head.xRot, Mth.sin(t * 6) * 0.08F, ease);
			}
			case "emote-facepalm" -> {
				m.rightArm.xRot = lerp(m.rightArm.xRot, -2.3F, ease);
				m.rightArm.yRot = lerp(m.rightArm.yRot, 0.55F, ease);
				m.head.xRot = lerp(m.head.xRot, 0.4F, ease);
				m.head.yRot = lerp(m.head.yRot, Mth.sin(t * 7) * 0.12F, ease);
			}
			case "emote-nod" -> m.head.xRot = lerp(m.head.xRot, Mth.sin(t * 6) * 0.35F, ease);
			case "emote-run" -> {
				float s = t * 14;
				m.rightArm.xRot = lerp(m.rightArm.xRot, Mth.cos(s) * 1.2F, ease);
				m.leftArm.xRot = lerp(m.leftArm.xRot, -Mth.cos(s) * 1.2F, ease);
				m.rightLeg.xRot = lerp(m.rightLeg.xRot, -Mth.cos(s) * 1.1F, ease);
				m.leftLeg.xRot = lerp(m.leftLeg.xRot, Mth.cos(s) * 1.1F, ease);
				bob = Math.abs(Mth.sin(s)) * 1.0F;
			}
			case "emote-fly" -> {
				m.leftArm.zRot = lerp(m.leftArm.zRot, -1.45F + Mth.sin(t * 6) * 0.25F, ease);
				m.rightArm.zRot = lerp(m.rightArm.zRot, 1.45F - Mth.sin(t * 6) * 0.25F, ease);
				m.leftLeg.zRot = lerp(m.leftLeg.zRot, -0.15F, ease);
				m.rightLeg.zRot = lerp(m.rightLeg.zRot, 0.15F, ease);
			}
			case "emote-swim" -> {
				float s = t * 5;
				m.rightArm.xRot = lerp(m.rightArm.xRot, -2.6F + Mth.sin(s) * 0.6F, ease);
				m.leftArm.xRot = lerp(m.leftArm.xRot, -2.6F - Mth.sin(s) * 0.6F, ease);
				m.rightLeg.xRot = lerp(m.rightLeg.xRot, Mth.sin(s * 2) * 0.4F, ease);
				m.leftLeg.xRot = lerp(m.leftLeg.xRot, -Mth.sin(s * 2) * 0.4F, ease);
			}
			case "emote-sneak" -> {
				m.body.xRot = lerp(m.body.xRot, 0.5F, ease);
				m.head.y = lerp(m.head.y, 4.2F, ease);
				m.body.y = lerp(m.body.y, 3.2F, ease);
				m.rightArm.y = lerp(m.rightArm.y, 5.2F, ease);
				m.leftArm.y = lerp(m.leftArm.y, 5.2F, ease);
				m.rightArm.xRot = lerp(m.rightArm.xRot, 0.4F, ease);
				m.leftArm.xRot = lerp(m.leftArm.xRot, 0.4F, ease);
			}
			default -> {
				return;
			}
		}
		if (bob != 0) {
			float b = bob * ease;
			m.head.y -= b;
			m.body.y -= b;
			m.rightArm.y -= b;
			m.leftArm.y -= b;
			m.rightLeg.y -= b;
			m.leftLeg.y -= b;
		}
	}

	private static float lerp(float from, float to, float k) {
		return from + (to - from) * k;
	}
}
