package net.emberleaf.client.menu.live;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.emberleaf.client.menu.BlockMesher;
import net.emberleaf.client.menu.IslandRenderState;
import net.emberleaf.client.menu.IslandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a live menu scene in real perspective, with the game's own block models and textures. The quads come with
 * baked light (ambient, sun, coloured block light); here they get the time of day, flickering fire light and fog,
 * then end crystals, you (with cosmetics) and online friends.
 */
public class LiveSceneRenderer extends PictureInPictureRenderer<LiveSceneState> {
	private static final Identifier END_CRYSTAL = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
	private static final int LIGHT = 0xF000F0;
	private final ModelPart[] wide = new ModelPart[4];
	private final ModelPart[] slim = new ModelPart[4];
	private final EndCrystalModel[] crystals = new EndCrystalModel[6];
	private final EndCrystalRenderState crystalState = new EndCrystalRenderState();

	@Override
	public Class<LiveSceneState> getRenderStateClass() {
		return LiveSceneState.class;
	}

	@Override
	protected String getTextureLabel() {
		return "emberleaf live scene";
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0F;
	}

	@Override
	protected void renderToTexture(LiveSceneState s, PoseStack pose, SubmitNodeCollector collector) {
		LiveScene scene = LiveScene.get(s.scene());
		if (scene == null) return;
		BlockMesher.Mesh mesh = scene.mesh();
		if (mesh == null) return;
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		LiveCamera cam = s.camera();
		// the real blocks of the game (block atlas = whatever texture packs are on)
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS),
			(m, buf) -> drawScene(buf, m, mesh.solid, mesh.solidTint, mesh.solidDetail, scene, s, cam));
		if (mesh.translucentVertices() > 0) {
			collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
				(m, buf) -> drawScene(buf, m, mesh.translucent, mesh.translucentTint, mesh.translucentDetail, scene, s, cam));
		}
		for (int i = 0; i < Math.min(scene.crystals.size(), crystals.length); i++) crystal(s, pose, collector, scene, cam, i);
		figure(s, pose, collector, scene, cam, 0, s.skin(), s.model());
		for (int i = 0; i < Math.min(s.friends().size(), scene.figures.size() - 1); i++) {
			IslandRenderState.Figure f = s.friends().get(i);
			figure(s, pose, collector, scene, cam, i + 1, f.skin(), f.model());
		}
	}

	/** Small parts of very detailed 3D models (3D flowers, roots …) are left out beyond this distance (blocks). */
	private static final float DETAIL_DEPTH = 24F;

	private static void drawScene(VertexConsumer buf, PoseStack.Pose m, float[] v, int[] tints, boolean[] detail, LiveScene sc, LiveSceneState s,
		LiveCamera cam) {
		int st = BlockMesher.STRIDE;
		int vertices = tints.length;
		// the visible part of the menu (GUI px around its centre), with a little room for big quads
		float halfW = (s.x1() - s.x0()) / 2F + 8;
		float halfH = (s.y1() - s.y0()) / 2F + 8;
		float ar = ARGB.red(s.ambient()) / 255F;
		float ag = ARGB.green(s.ambient()) / 255F;
		float ab = ARGB.blue(s.ambient()) / 255F;
		float sr = ARGB.red(s.sun()) / 255F;
		float sg = ARGB.green(s.sun()) / 255F;
		float sb = ARGB.blue(s.sun()) / 255F;
		float fl = s.flicker();
		float fogR = sc.fog[0];
		float fogG = sc.fog[1];
		float fogB = sc.fog[2];
		float fogStart = sc.fogStart;
		float fogRange = Math.max(1, sc.fogEnd - sc.fogStart);
		float[] px = new float[4];
		float[] py = new float[4];
		float[] pz = new float[4];
		int[] col = new int[4];
		float[] out = new float[3];
		quads:
		for (int q = 0; q + 4 <= vertices; q += 4) {
			int b = q * st;
			// back faces (seen from the current camera) are skipped
			float ax = v[b], ay = v[b + 1], az = v[b + 2];
			float ux = v[b + st] - ax, uy = v[b + st + 1] - ay, uz = v[b + st + 2] - az;
			float wx = v[b + 3 * st] - ax, wy = v[b + 3 * st + 1] - ay, wz = v[b + 3 * st + 2] - az;
			float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
			if (nx * (cam.ex - ax) + ny * (cam.ey - ay) + nz * (cam.ez - az) <= 0) continue;
			for (int k = 0; k < 4; k++) {
				int o = b + k * st;
				if (!cam.project(v[o], v[o + 1], v[o + 2], out)) continue quads;
				float depth = out[2];
				if (depth > sc.fogEnd + 6 || (k == 0 && depth > DETAIL_DEPTH && detail[q >> 2])) continue quads;
				px[k] = out[0];
				py[k] = out[1];
				pz[k] = LiveCamera.poseZ(depth);
				float amb = v[o + 5];
				float sun = v[o + 6];
				int tint = tints[q + k];
				float r = (amb * ar + sun * sr + v[o + 7] * fl) * (ARGB.red(tint) / 255F);
				float g = (amb * ag + sun * sg + v[o + 8] * fl) * (ARGB.green(tint) / 255F);
				float bl = (amb * ab + sun * sb + v[o + 9] * fl) * (ARGB.blue(tint) / 255F);
				float f = Mth.clamp((depth - fogStart) / fogRange, 0, 1);
				f = f * f * (3 - 2 * f);
				r = r * (1 - f) + fogR * f;
				g = g * (1 - f) + fogG * f;
				bl = bl * (1 - f) + fogB * f;
				col[k] = ARGB.colorFromFloat(1, Mth.clamp(r, 0, 1), Mth.clamp(g, 0, 1), Mth.clamp(bl, 0, 1));
			}
			// completely outside the picture on one side: nothing to draw
			if ((px[0] < -halfW && px[1] < -halfW && px[2] < -halfW && px[3] < -halfW)
				|| (px[0] > halfW && px[1] > halfW && px[2] > halfW && px[3] > halfW)
				|| (py[0] < -halfH && py[1] < -halfH && py[2] < -halfH && py[3] < -halfH)
				|| (py[0] > halfH && py[1] > halfH && py[2] > halfH && py[3] > halfH)) continue;
			// one winding is enough: the block pipelines here draw both sides (back faces were skipped above)
			for (int k = 0; k < 4; k++) put(buf, m, px[k], py[k], pz[k], v[b + k * st + 3], v[b + k * st + 4], col[k]);
		}
	}

	private static void put(VertexConsumer buf, PoseStack.Pose m, float x, float y, float z, float u, float v, int color) {
		buf.addVertex(m, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(m, 0, 1, 0);
	}

	/** A real end crystal turning above its pillar (same model, texture and motion as in the End). */
	private void crystal(LiveSceneState s, PoseStack pose, SubmitNodeCollector collector, LiveScene sc, LiveCamera cam, int i) {
		float[] at = sc.crystals.get(i);
		float[] o = new float[3];
		if (!cam.project(at[0], at[1], at[2], o) || o[2] > sc.fogEnd) return;
		if (crystals[i] == null) crystals[i] = new EndCrystalModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.END_CRYSTAL));
		EndCrystalModel model = crystals[i];
		crystalState.ageInTicks = s.time() * 20F + i * 37F;
		crystalState.showsBottom = false;
		model.setupAnim(crystalState);
		float scale = cam.scaleAt(o[2]);
		Matrix3f rot = new Matrix3f();
		cam.view().get3x3(rot);
		Matrix4f m = new Matrix4f().translate(o[0], o[1], LiveCamera.poseZ(o[2])).scale(scale, -scale, -3F).mul(new Matrix4f().set(rot));
		pose.pushPose();
		pose.mulPose(m);
		pose.scale(2F, 2F, 2F);
		pose.translate(0F, -0.5F, 0F);
		collector.submitModel(model, crystalState, pose, END_CRYSTAL, LIGHT, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	/** You (slot 0) or a friend, standing in the scene in perspective (scaled by distance, turned towards the camera). */
	private void figure(LiveSceneState s, PoseStack pose, SubmitNodeCollector collector, LiveScene sc, LiveCamera cam, int slot,
		Identifier skin, PlayerModelType type) {
		if (skin == null || slot >= sc.figures.size()) return;
		float[] at = sc.figures.get(slot);
		float fx = at[0] + 0.5F;
		float fy = at[1];
		float fz = at[2] + 0.5F;
		float[] o = new float[3];
		if (!cam.project(fx, fy, fz, o) || o[2] > sc.fogEnd) return;
		if (wide[slot] == null) {
			wide[slot] = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER);
			slim[slot] = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM);
		}
		ModelPart root = type == PlayerModelType.SLIM ? slim[slot] : wide[slot];
		float t = s.time() + slot * 1.7F;
		root.getChild("head").yRot = Mth.sin(t * 0.4F) * 0.25F;
		root.getChild("head").xRot = Mth.sin(t * 0.6F) * 0.05F;
		root.getChild("right_arm").zRot = 0.06F + Mth.sin(t * 1.3F) * 0.03F;
		root.getChild("left_arm").zRot = -0.06F - Mth.sin(t * 1.3F) * 0.03F;
		float scale = cam.scaleAt(o[2]);
		// camera space (x right, y up, z towards you) -> menu space (x right, y down, z away), then world orientation
		Matrix3f rot = new Matrix3f();
		cam.view().get3x3(rot);
		Matrix4f m = new Matrix4f().translate(o[0], o[1], LiveCamera.poseZ(o[2])).scale(scale, -scale, -3F).mul(new Matrix4f().set(rot));
		pose.pushPose();
		pose.mulPose(m);
		float yaw = (float) Math.atan2(cam.ex - fx, cam.ez - fz) + Mth.PI; // model space faces -z
		pose.mulPose(new Matrix4f().rotationY(yaw));
		if (slot == 0) IslandRenderer.pet(s.time(), pose, collector);
		pose.scale(-1, -1, 1);
		pose.translate(0, -1.5F, 0);
		collector.submitModelPart(root, pose, RenderTypes.entityTranslucent(skin), LIGHT, OverlayTexture.NO_OVERLAY, null);
		if (slot == 0) IslandRenderer.cosmetics(s.time(), pose, collector, root);
		pose.popPose();
	}
}
