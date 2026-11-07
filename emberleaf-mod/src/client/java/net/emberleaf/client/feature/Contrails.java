package net.emberleaf.client.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.emberleaf.client.config.Modules;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Contrails at the wing tips of players gliding with an elytra; they grow with your speed and fade out. */
public final class Contrails {
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("emberleaf", "textures/misc/white.png");
	private static final Map<UUID, Trail> TRAILS = new HashMap<>();

	private record Point(Vec3 left, Vec3 right, long time, float strength) {}

	private static final class Trail {
		final Deque<Point> points = new ArrayDeque<>();
	}

	private Contrails() {}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			TRAILS.clear();
			return;
		}
		long now = System.currentTimeMillis();
		long life = (long) (Modules.value("contrails") * 1000);
		for (Player p : mc.level.players()) {
			if (!Modules.on("contrails") || !p.isFallFlying() || p.distanceToSqr(mc.player) > 128 * 128) continue;
			float yaw = (float) Math.toRadians(p.getYRot());
			Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(0.95);
			Vec3 center = p.position().add(0, 0.35, 0);
			float speed = (float) p.getDeltaMovement().length();
			TRAILS.computeIfAbsent(p.getUUID(), id -> new Trail()).points.addLast(
				new Point(center.add(side), center.subtract(side), now, Mth.clamp(speed / 1.6F, 0.25F, 1)));
		}
		Iterator<Map.Entry<UUID, Trail>> it = TRAILS.entrySet().iterator();
		while (it.hasNext()) {
			Trail trail = it.next().getValue();
			while (!trail.points.isEmpty() && now - trail.points.peekFirst().time() > life) trail.points.pollFirst();
			if (trail.points.isEmpty()) it.remove();
		}
	}

	public static void render(LevelRenderContext context) {
		if (TRAILS.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		long now = System.currentTimeMillis();
		float life = (float) (Modules.value("contrails") * 1000);
		boolean night = mc.level != null && mc.level.getSkyDarken() > 6;
		PoseStack pose = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE), (m, buf) -> {
			for (Map.Entry<UUID, Trail> entry : TRAILS.entrySet()) {
				String color = colorOf(entry.getKey());
				Point prev = null;
				for (Point p : entry.getValue().points) {
					if (prev != null) {
						float a0 = (1 - (now - prev.time()) / life) * prev.strength() * 0.55F;
						float a1 = (1 - (now - p.time()) / life) * p.strength() * 0.55F;
						int c0 = ARGB.color(Math.max(0, a0), rgb(color, prev.time(), night));
						int c1 = ARGB.color(Math.max(0, a1), rgb(color, p.time(), night));
						ribbon(buf, m, prev.left().subtract(cam), p.left().subtract(cam), c0, c1);
						ribbon(buf, m, prev.right().subtract(cam), p.right().subtract(cam), c0, c1);
					}
					prev = p;
				}
			}
		});
	}

	private static final Map<UUID, String> REMOTE_COLORS = new java.util.concurrent.ConcurrentHashMap<>();

	/** Another player's contrail colour, as the Lagerfeuer reported it. */
	public static void putRemoteColor(UUID uuid, String color) {
		REMOTE_COLORS.put(uuid, color);
	}

	/** Your chosen colour, if you have played long enough for it; otherwise white. */
	public static String myColor() {
		String c = Modules.choice("contrail_color");
		if (c == null || Playtime.hours() < Modules.unlockHours("contrail_color", c)) return "white";
		return c;
	}

	private static String colorOf(UUID uuid) {
		if (net.emberleaf.client.outfit.Outfits.isLocal(uuid)) return myColor();
		return REMOTE_COLORS.getOrDefault(uuid, "white");
	}

	private static int rgb(String color, long time, boolean night) {
		int i = Modules.CONTRAIL_COLORS.indexOf(color);
		if (i < 0 || color.equals("white")) return night ? 0xB8C0E0 : 0xFFFFFF;
		if (color.equals("rainbow")) return java.awt.Color.HSBtoRGB((time % 3000) / 3000F, 0.6F, 1F) & 0xFFFFFF;
		return Modules.CONTRAIL_RGB[i];
	}

	private static void ribbon(com.mojang.blaze3d.vertex.VertexConsumer buf, PoseStack.Pose m, Vec3 a, Vec3 b, int ca, int cb) {
		float w = 0.035F;
		// two crossed quads so the streak is visible from every side
		quad(buf, m, a, b, new Vec3(0, w, 0), ca, cb);
		quad(buf, m, a, b, new Vec3(w, 0, w), ca, cb);
	}

	private static void quad(com.mojang.blaze3d.vertex.VertexConsumer buf, PoseStack.Pose m, Vec3 a, Vec3 b, Vec3 o, int ca, int cb) {
		vertex(buf, m, a.add(o), ca);
		vertex(buf, m, a.subtract(o), ca);
		vertex(buf, m, b.subtract(o), cb);
		vertex(buf, m, b.add(o), cb);
		vertex(buf, m, b.add(o), cb);
		vertex(buf, m, b.subtract(o), cb);
		vertex(buf, m, a.subtract(o), ca);
		vertex(buf, m, a.add(o), ca);
	}

	private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer buf, PoseStack.Pose m, Vec3 p, int color) {
		buf.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(0.5F, 0.5F)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(m, 0, 1, 0);
	}
}
