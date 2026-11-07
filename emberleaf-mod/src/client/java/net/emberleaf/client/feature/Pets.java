package net.emberleaf.client.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.emberleaf.client.model.EModel;
import net.emberleaf.client.model.EModelRenderer;
import net.emberleaf.client.outfit.Outfits;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Blockbench pets walk next to their owner: they follow at a little distance, turn where they go,
 * hop over steps, sit down when you stand still and teleport back if they fall too far behind.
 */
public final class Pets {
	private static final Map<UUID, Follower> FOLLOWERS = new HashMap<>();

	private static final class Follower {
		Vec3 pos;
		Vec3 prev;
		float yaw;
		float prevYaw;
		float walk;
		float walkPos;
		int idleTicks;

		Follower(Vec3 start) {
			pos = prev = start;
		}
	}

	private Pets() {}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			if (mc.level == null) FOLLOWERS.clear();
			return;
		}
		FOLLOWERS.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
		for (Player player : mc.level.players()) {
			if (Outfits.forPlayer(player.getUUID()).model("pet") == null) {
				FOLLOWERS.remove(player.getUUID());
				continue;
			}
			float bodyYaw = (float) Math.toRadians(player.yBodyRot);
			// spot slightly behind and to the right of the owner
			Vec3 target = player.position().add(-Mth.cos(bodyYaw) * 1.1 - -Mth.sin(bodyYaw) * 0.8, 0, -Mth.sin(bodyYaw) * 1.1 - Mth.cos(bodyYaw) * 0.8);
			Follower f = FOLLOWERS.computeIfAbsent(player.getUUID(), id -> new Follower(target));
			f.prev = f.pos;
			f.prevYaw = f.yaw;
			Vec3 delta = target.subtract(f.pos);
			double dist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
			if (dist > 14 || Math.abs(delta.y) > 6) {
				f.pos = f.prev = target;
				continue;
			}
			double speed = dist > 0.35 ? Math.min(0.42, dist * 0.18 + 0.04) : 0;
			if (speed > 0) {
				f.pos = f.pos.add(delta.x / dist * speed, 0, delta.z / dist * speed);
				float wanted = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
				f.yaw += Mth.wrapDegrees(wanted - f.yaw) * 0.25F;
				f.idleTicks = 0;
			} else {
				f.idleTicks++;
				f.yaw += Mth.wrapDegrees(player.yBodyRot - f.yaw) * 0.05F;
			}
			// follow the ground the owner stands on (smoothly, so steps look like hops)
			double y = player.onGround() ? player.getY() : f.pos.y;
			f.pos = new Vec3(f.pos.x, f.pos.y + (y - f.pos.y) * 0.35, f.pos.z);
			f.walk += ((float) Math.min(1, speed * 4) - f.walk) * 0.3F;
			f.walkPos += (float) speed * 6;
		}
	}

	public static void render(LevelRenderContext context) {
		if (!net.emberleaf.client.config.Modules.on("pets")) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || FOLLOWERS.isEmpty()) return;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		PoseStack pose = context.poseStack();
		for (Map.Entry<UUID, Follower> e : FOLLOWERS.entrySet()) {
			Player owner = mc.level.getPlayerByUUID(e.getKey());
			EModel model = Outfits.forPlayer(e.getKey()).model("pet");
			if (owner == null || model == null || owner.isInvisible()) continue;
			Follower f = e.getValue();
			double x = Mth.lerp(pt, f.prev.x, f.pos.x);
			double y = Mth.lerp(pt, f.prev.y, f.pos.y);
			double z = Mth.lerp(pt, f.prev.z, f.pos.z);
			float yaw = Mth.rotLerp(pt, f.prevYaw, f.yaw);
			int light = mc.getEntityRenderDispatcher().getRenderer(owner).getPackedLightCoords(owner, pt);
			float time = (owner.tickCount + pt) / 20F;
			EModelRenderer.Motion motion = new EModelRenderer.Motion(time, f.walk, f.walkPos, false, false, 0, 0, f.idleTicks > 80);
			pose.pushPose();
			pose.translate(x - cam.x, y - cam.y, z - cam.z);
			// Blockbench front is -z; Minecraft yaw 0 looks along +z
			pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-yaw + 180)));
			pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
			EModelRenderer.submit(model, pose, context.submitNodeCollector(), light, OverlayTexture.NO_OVERLAY, motion);
			pose.popPose();
		}
	}
}
