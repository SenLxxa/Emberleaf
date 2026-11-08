package net.emberleaf.client.feature;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.emberleaf.client.model.EModel;
import net.emberleaf.client.outfit.Outfit;
import net.emberleaf.client.outfit.Outfits;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Cape effects in the world: capes with glowing colours shed sparks in their own colours from the hem.
 * Standing still they glimmer now and then, running leaves a trail, gliding and falling a long streak.
 * Plain capes (no bright colours, no "glow" parts) stay quiet.
 */
public final class CapeFx {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<UUID, Palette> PALETTES = new HashMap<>();

	/** Bright colours of a cape; empty = the cape has no effects. */
	private record Palette(int generation, int[] colors) {}

	private CapeFx() {}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			PALETTES.clear();
			return;
		}
		if (mc.isPaused() || !net.emberleaf.client.config.Modules.on("cape_fx")) return;
		for (Player player : mc.level.players()) {
			if (player.isInvisible() || player.distanceToSqr(mc.player) > 48 * 48) continue;
			if (player == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) continue;
			UUID uuid = player.getUUID();
			boolean local = Outfits.isLocal(uuid);
			if (!net.emberleaf.client.config.Modules.on(local ? "cosmetics" : "cosmetics_others")) continue;
			int[] colors = palette(uuid);
			if (colors.length > 0) spawn(mc, player, colors);
		}
		if (PALETTES.size() > 128) PALETTES.clear();
	}

	private static void spawn(Minecraft mc, Player player, int[] colors) {
		Vec3 v = player.getDeltaMovement();
		float speed = (float) Math.sqrt(v.x * v.x + v.z * v.z);
		boolean gliding = player.isFallFlying();
		boolean airborne = !player.onGround() && !player.isInWater() && !player.isPassenger();
		float rate = gliding ? 1.4F : airborne ? 0.5F + speed * 2 : speed > 0.04F ? 0.25F + speed * 3 : 0.07F;
		int count = (int) rate + (RANDOM.nextFloat() < rate - (int) rate ? 1 : 0);
		if (count == 0) return;

		float yaw = (float) Math.toRadians(player.yBodyRot);
		Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
		Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		// the hem lifts and swings back the faster you go, like the cloth does
		double lift = Math.min(0.45, speed * 1.6) + (player.isCrouching() ? -0.12 : 0);
		double behind = 0.2 + Math.min(0.5, speed * 1.8);
		Vec3 hem = gliding ? player.position().add(back.scale(0.6)).add(0, 0.3, 0)
			: player.position().add(0, 0.48 + lift, 0).add(back.scale(behind));
		for (int i = 0; i < count; i++) {
			Vec3 p = hem.add(side.scale((RANDOM.nextFloat() - 0.5F) * 0.55F)).add(0, (RANDOM.nextFloat() - 0.5F) * 0.12F, 0);
			int color = colors[RANDOM.nextInt(colors.length)];
			double vx = back.x * 0.02 + (RANDOM.nextFloat() - 0.5F) * 0.02;
			double vz = back.z * 0.02 + (RANDOM.nextFloat() - 0.5F) * 0.02;
			if (RANDOM.nextFloat() < 0.22F) {
				// a few bright sparks that really glow
				mc.level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, vx * 0.5, 0.012, vz * 0.5);
			} else {
				mc.level.addParticle(new DustParticleOptions(color, 0.55F + RANDOM.nextFloat() * 0.4F), p.x, p.y, p.z, vx, 0.01, vz);
			}
		}
	}

	/** The cape's effect colours, read once from its texture (Blockbench cape or image cape). */
	private static int[] palette(UUID uuid) {
		int generation = Outfits.generationOf(uuid);
		Palette cached = PALETTES.get(uuid);
		if (cached != null && cached.generation == generation) return cached.colors;
		Outfit outfit = Outfits.forPlayer(uuid);
		int[] colors = new int[0];
		EModel model = outfit.model("cape");
		try {
			if (model != null) {
				boolean glows = model.bones.stream().anyMatch(EModel.Bone::glow);
				BufferedImage img = read(model.textureBytes());
				if (img != null) colors = brightColors(img, 0, 0, img.getWidth(), img.getHeight(), glows);
			} else if (!outfit.capeFrames().isEmpty()) {
				BufferedImage img = read(outfit.capeFrames().get(0));
				if (img != null) {
					// only the outside of the cape (1,1)-(11,17) in the 64x32 layout
					float sx = img.getWidth() / 64F;
					float sy = img.getHeight() / 32F;
					colors = brightColors(img, Math.round(sx), Math.round(sy), Math.round(11 * sx), Math.round(17 * sy), false);
				}
			}
		} catch (RuntimeException ignored) {
			colors = new int[0];
		}
		PALETTES.put(uuid, new Palette(generation, colors));
		return colors;
	}

	private static BufferedImage read(byte[] bytes) {
		if (bytes == null) return null;
		try {
			return ImageIO.read(new ByteArrayInputStream(bytes));
		} catch (java.io.IOException e) {
			return null;
		}
	}

	/**
	 * Up to four of the most common bright, colourful pixels. A cape counts as "with effects" when it has glowing parts
	 * or at least 4 % of it is such bright colour.
	 */
	private static int[] brightColors(BufferedImage img, int x0, int y0, int x1, int y1, boolean glows) {
		Map<Integer, Integer> buckets = new HashMap<>();
		Map<Integer, long[]> sums = new HashMap<>();
		int opaque = 0;
		int bright = 0;
		for (int y = Math.max(0, y0); y < Math.min(img.getHeight(), y1); y++) {
			for (int x = Math.max(0, x0); x < Math.min(img.getWidth(), x1); x++) {
				int argb = img.getRGB(x, y);
				if ((argb >>> 24) < 160) continue;
				opaque++;
				int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
				int max = Math.max(r, Math.max(g, b));
				int min = Math.min(r, Math.min(g, b));
				boolean light = max >= 200 && (max - min >= 60 || min >= 215);
				if (!light) continue;
				bright++;
				int key = (r >> 5) << 6 | (g >> 5) << 3 | (b >> 5);
				buckets.merge(key, 1, Integer::sum);
				long[] s = sums.computeIfAbsent(key, k -> new long[3]);
				s[0] += r;
				s[1] += g;
				s[2] += b;
			}
		}
		if (bright == 0 || (!glows && bright < Math.max(3, opaque * 0.04F))) return new int[0];
		List<Map.Entry<Integer, Integer>> top = new ArrayList<>(buckets.entrySet());
		top.sort((a, b) -> b.getValue() - a.getValue());
		int n = Math.min(4, top.size());
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			int key = top.get(i).getKey();
			int count = top.get(i).getValue();
			long[] s = sums.get(key);
			out[i] = Mth.clamp((int) (s[0] / count), 0, 255) << 16 | Mth.clamp((int) (s[1] / count), 0, 255) << 8 | Mth.clamp((int) (s[2] / count), 0, 255);
		}
		return out;
	}
}
