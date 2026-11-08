package net.emberleaf.client.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
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
 * The pet's bone names pick how it moves: "..._hover" floats beside you at shoulder height,
 * "..._orbit" flies circles around your body and lands on your left shoulder when you stand still.
 */
public final class Pets {
	private static final Map<UUID, Follower> FOLLOWERS = new HashMap<>();

	private static final int WALK = 0;
	private static final int HOVER = 1;
	private static final int ORBIT = 2;
	private static final Map<EModel, Integer> STYLES = new WeakHashMap<>();

	/** Floating pets stay this many blocks above the ground. */
	private static final double HOVER_HEIGHT = 1.0;
	/** Orbit flight around the owner (blocks, radians per tick). */
	private static final double ORBIT_RADIUS = 17 / 16.0;
	private static final double ORBIT_HEIGHT = 21 / 16.0;
	private static final double ORBIT_SPEED = 0.0775;
	/** Where the coiled-up pet sits: left shoulder, in the owner's own space (blocks). */
	private static final double SHOULDER_X = 8.7 / 16.0;
	private static final double SHOULDER_Y = 21.6 / 16.0;
	private static final double SHOULDER_Z = 0;
	/** Ticks the owner has to stand still before an orbiting pet lands. */
	private static final int LAND_AFTER = 20;

	private static final class Follower {
		Vec3 pos;
		Vec3 prev;
		float yaw;
		float prevYaw;
		float walk;
		float walkPos;
		int idleTicks;
		// orbit pets
		float roll;
		float prevRoll;
		double angle = Double.NaN;
		Vec3 ownerLast;
		int stillTicks;
		boolean perched;

		Follower(Vec3 start) {
			pos = prev = start;
		}
	}

	private Pets() {}

	private static int style(EModel model) {
		return STYLES.computeIfAbsent(model, m -> {
			for (EModel.Bone bone : m.bones) {
				String name = bone.name().toLowerCase(Locale.ROOT);
				if (name.contains("orbit")) return ORBIT;
				if (name.contains("hover")) return HOVER;
			}
			return WALK;
		});
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			if (mc.level == null) FOLLOWERS.clear();
			return;
		}
		FOLLOWERS.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
		for (Player player : mc.level.players()) {
			EModel model = Outfits.forPlayer(player.getUUID()).model("pet");
			if (model == null) {
				FOLLOWERS.remove(player.getUUID());
				continue;
			}
			float bodyYaw = (float) Math.toRadians(player.yBodyRot);
			// spot slightly behind and to the right of the owner
			Vec3 target = player.position().add(-Mth.cos(bodyYaw) * 1.1 - -Mth.sin(bodyYaw) * 0.8, 0, -Mth.sin(bodyYaw) * 1.1 - Mth.cos(bodyYaw) * 0.8);
			Follower f = FOLLOWERS.computeIfAbsent(player.getUUID(), id -> new Follower(target));
			f.prev = f.pos;
			f.prevYaw = f.yaw;
			f.prevRoll = f.roll;
			if (style(model) == ORBIT) {
				orbit(player, f);
				continue;
			}
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

	/** Circles the owner while they move; after a second of standing still it flies to the left shoulder and curls up. */
	private static void orbit(Player player, Follower f) {
		Vec3 owner = player.position();
		double moved = f.ownerLast == null ? 0 : owner.subtract(f.ownerLast).horizontalDistance();
		f.ownerLast = owner;
		f.stillTicks = moved > 0.01 ? 0 : f.stillTicks + 1;
		if (Double.isNaN(f.angle)) f.angle = Math.atan2(f.pos.z - owner.z, f.pos.x - owner.x);
		boolean land = f.stillTicks > LAND_AFTER;
		Vec3 target;
		float wantedYaw;
		if (land) {
			double b = Math.toRadians(player.yBodyRot);
			double c = Math.cos(b);
			double s = Math.sin(b);
			double y = SHOULDER_Y - (player.isCrouching() ? 0.2 : 0);
			target = owner.add(SHOULDER_X * c - SHOULDER_Z * s, y, SHOULDER_X * s + SHOULDER_Z * c);
			wantedYaw = player.yBodyRot;
		} else {
			f.angle += ORBIT_SPEED;
			double h = ORBIT_HEIGHT + Math.sin(f.angle * 2 + 0.6) * 3.5 / 16;
			target = owner.add(Math.cos(f.angle) * ORBIT_RADIUS, h, Math.sin(f.angle) * ORBIT_RADIUS);
			// the model looks along its flight direction around the owner
			wantedYaw = (float) Math.toDegrees(f.angle);
		}
		Vec3 delta = target.subtract(f.pos);
		double dist = delta.length();
		if (dist > 8) {
			f.pos = f.prev = target;
		} else {
			double k = land ? (dist < 0.05 ? 0.6 : 0.2) : 0.35;
			f.pos = f.pos.add(delta.scale(k));
		}
		f.perched = land && dist < 0.1;
		f.yaw += Mth.wrapDegrees(wantedYaw - f.yaw) * (land ? 0.2F : 0.35F);
		f.roll += ((land ? 0 : -0.28F) - f.roll) * 0.15F;
		f.walk += ((f.perched ? 0F : 1F) - f.walk) * 0.3F;
		f.walkPos += 0.4F;
		f.idleTicks = f.perched ? f.idleTicks + 1 : 0;
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
			int style = style(model);
			// a pet on your own shoulder would sit right in front of the first-person camera
			if (style == ORBIT && f.perched && owner == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
			double x = Mth.lerp(pt, f.prev.x, f.pos.x);
			double y = Mth.lerp(pt, f.prev.y, f.pos.y) + (style == HOVER ? HOVER_HEIGHT : 0);
			double z = Mth.lerp(pt, f.prev.z, f.pos.z);
			float yaw = Mth.rotLerp(pt, f.prevYaw, f.yaw);
			int light = mc.getEntityRenderDispatcher().getRenderer(owner).getPackedLightCoords(owner, pt);
			float time = (owner.tickCount + pt) / 20F;
			boolean sitting = style == ORBIT ? f.perched : f.idleTicks > 80;
			EModelRenderer.Motion motion = new EModelRenderer.Motion(time, f.walk, f.walkPos, style == HOVER && !sitting, false, 0, 0, sitting);
			pose.pushPose();
			pose.translate(x - cam.x, y - cam.y, z - cam.z);
			// Blockbench front is -z; Minecraft yaw 0 looks along +z
			pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-yaw + 180)));
			if (style == ORBIT) {
				float roll = Mth.lerp(pt, f.prevRoll, f.roll);
				if (roll != 0) pose.mulPose(new Matrix4f().rotationZ(roll));
			}
			pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
			EModelRenderer.submit(model, pose, context.submitNodeCollector(), light, OverlayTexture.NO_OVERLAY, motion);
			pose.popPose();
		}
	}
}
