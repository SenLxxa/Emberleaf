package net.emberleaf.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.emberleaf.client.model.EModel;
import net.emberleaf.client.model.EModelRenderer;
import net.emberleaf.client.outfit.Outfit;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.emberleaf.client.outfit.Outfits;
import net.emberleaf.client.outfit.VoxelModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Draws the player's voxel cosmetics (head, body, wings, pet) with the same layout as the launcher preview.
 * Coordinates follow the launcher: 1 unit = 1 skin pixel.
 */
public class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("emberleaf", "textures/misc/white.png");
	private static final float PIXEL = 1.0F / 16.0F;

	public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) return;
		java.util.UUID owner = Outfits.uuidOf(state.id);
		if (!net.emberleaf.client.config.Modules.on(Outfits.isLocal(owner) ? "cosmetics" : "cosmetics_others")) return;
		Outfit outfit = Outfits.forPlayer(Outfits.uuidOf(state.id));
		PlayerModel model = this.getParentModel();
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		if (!outfit.models().isEmpty()) submitModels(poseStack, collector, light, overlay, state, outfit, model);
		if (outfit.voxels().isEmpty()) return;

		RenderType type = RenderTypes.entitySolid(WHITE);
		float seconds = state.ageInTicks / 20.0F;

		VoxelModel head = outfit.voxel("head");
		if (head != null) drawOnPart(poseStack, collector, type, model.head, head, VoxelRenderer.HEAD, false, light, overlay);
		VoxelModel body = outfit.voxel("body");
		if (body != null) drawOnPart(poseStack, collector, type, model.body, body, VoxelRenderer.BODY, false, light, overlay);

		VoxelModel wings = outfit.voxel("wings");
		if (wings != null) {
			float flap = 0.45F + (float) Math.sin(seconds * 2.6F) * 0.28F;
			drawOnPart(poseStack, collector, type, model.body, wings, wingMap(1.2F, 1, flap), false, light, overlay);
			drawOnPart(poseStack, collector, type, model.body, wings, wingMap(-1.2F, -1, -flap), true, light, overlay);
		}

		VoxelModel pet = outfit.voxel("pet");
		if (pet != null) {
			poseStack.pushPose();
			poseStack.scale(PIXEL, PIXEL, PIXEL);
			VoxelRenderer.PointMap map = petMap(outfit.petOnGround(), seconds);
			collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> VoxelRenderer.emit(buffer, pose, pet, map, false, light, overlay));
			poseStack.popPose();
		}
	}

	/** Blockbench cosmetics: head items ride on the head, everything else on the body. Pets walk on their own (Pets). */
	private static void submitModels(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, AvatarRenderState state,
		Outfit outfit, PlayerModel model) {
		EModelRenderer.Motion motion = motionOf(state);
		String[] slots = {"head", "body", "wings", "cape"};
		for (int s = 0; s < slots.length; s++) {
			String slot = slots[s];
			EModel m = outfit.model(slot);
			if (m == null) continue;
			poseStack.pushPose();
			(slot.equals("head") ? model.head : model.body).translateAndRotate(poseStack);
			EModelRenderer.toPartSpace(poseStack);
			// one cloth simulation per player and slot, so the cape keeps its swing between frames
			EModelRenderer.submit(m, poseStack, collector, light, overlay, motion, ((long) state.id << 3) | s);
			poseStack.popPose();
		}
	}

	/** How the player moves right now, for capes, wings and the other moving cosmetics (also used by the image cape). */
	public static EModelRenderer.Motion motionOf(AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		Entity entity = mc.level != null ? mc.level.getEntity(state.id) : null;
		boolean airborne = state.isFallFlying || (entity != null && !entity.onGround() && !entity.isInWater() && !entity.isPassenger());
		return new EModelRenderer.Motion(state.ageInTicks / 20F, Math.min(1, state.walkAnimationSpeed),
			state.walkAnimationPos, airborne, state.isCrouching, state.capeLean / 2F + state.capeFlap, state.capeLean2 / 2F, false);
	}

	private static void drawOnPart(
		PoseStack poseStack, SubmitNodeCollector collector, RenderType type, ModelPart part, VoxelModel voxels,
		VoxelRenderer.PointMap map, boolean mirrored, int light, int overlay
	) {
		poseStack.pushPose();
		part.translateAndRotate(poseStack);
		poseStack.scale(PIXEL, PIXEL, PIXEL);
		collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> VoxelRenderer.emit(buffer, pose, voxels, map, mirrored, light, overlay));
		poseStack.popPose();
	}

	/**
	 * One wing, as in the launcher: hinged at the upper back (0, 3, -2.6), offset sideways, flapping around y.
	 * mirrorX = -1 builds the right wing from the left one.
	 */
	private static VoxelRenderer.PointMap wingMap(float pivotX, int mirrorX, float angle) {
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		return (x, y, z, out) -> {
			float lx = x * mirrorX;
			float lz = z - 0.5F;
			float rx = lx * cos + lz * sin;
			float rz = -lx * sin + lz * cos;
			VoxelRenderer.BODY.map(pivotX + rx, 3 + y, -2.6F + rz, out);
		};
	}

	/** The pet floats (or hops) next to the player, like in the launcher; maps into the model root (neck at y = 0). */
	private static VoxelRenderer.PointMap petMap(boolean ground, float seconds) {
		float baseY = ground ? -16 + Math.max(0, (float) Math.sin(seconds * 3)) * 1.2F : 5 + (float) Math.sin(seconds * 2) * 1.6F;
		float baseZ = ground ? 5 : 3;
		float angle = -0.5F + (float) Math.sin(seconds * 0.7F) * 0.25F;
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		return (x, y, z, out) -> {
			float sx = x * 0.75F, sy = y * 0.75F, sz = z * 0.75F;
			float rx = sx * cos + sz * sin;
			float rz = -sx * sin + sz * cos;
			out[0] = 15 + rx;
			out[1] = 8 - (baseY + sy);
			out[2] = -(baseZ + rz);
		};
	}
}
