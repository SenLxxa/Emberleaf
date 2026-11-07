package net.emberleaf.client.menu;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.emberleaf.client.model.EModel;
import net.emberleaf.client.model.EModelRenderer;
import net.emberleaf.client.outfit.Outfit;
import net.emberleaf.client.outfit.OutfitStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.animal.fox.AdultFoxModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix4f;

/** Draws the floating island (real blocks, flowers, a sleeping fox, birds and you) into the menu as real 3D. */
public class IslandRenderer extends PictureInPictureRenderer<IslandRenderState> {
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("emberleaf", "textures/misc/white.png");
	private static final Identifier END_CRYSTAL = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
	private static final Identifier FOX_SLEEP = Identifier.withDefaultNamespace("textures/entity/fox/fox_sleep.png");
	private static final int LIGHT = 0xF000F0;
	private final ModelPart[] wide = new ModelPart[5];
	private final ModelPart[] slim = new ModelPart[5];
	private final EndCrystalModel[] crystals = new EndCrystalModel[2];
	private final EndCrystalRenderState[] crystalStates = {new EndCrystalRenderState(), new EndCrystalRenderState()};
	private AdultFoxModel foxModel;
	private final FoxRenderState foxState = new FoxRenderState();

	@Override
	public Class<IslandRenderState> getRenderStateClass() {
		return IslandRenderState.class;
	}

	@Override
	protected String getTextureLabel() {
		return "emberleaf island";
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0F;
	}

	@Override
	protected void renderToTexture(IslandRenderState s, PoseStack pose, SubmitNodeCollector collector) {
		IslandData island = IslandData.get(s.scene());
		if (island == null) return;
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		float[] p = island.player;
		pose.pushPose();
		pose.scale(1, -1, 1); // y up
		// the GUI flips z, so turn half way round to look at the island's front (+x/+z) like the launcher does
		pose.mulPose(new Matrix4f().rotationX((float) Math.toRadians(-s.pitch())).rotateY((float) Math.toRadians(s.yaw() + 180)));
		pose.translate(-(p[0] + 0.5F), -(p[1]) + Mth.sin(s.time() * 0.8F) * 0.12F - 1.6F, -(p[2] + 0.5F));

		BlockMesher.Mesh mesh = island.mesh();
		if (mesh != null) {
			// real blocks: the game's block models and textures (with the active texture packs)
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS),
				(m, buf) -> quads(buf, m, mesh.solid, mesh.solidTint, s.tint()));
			if (mesh.translucentVertices() > 0) {
				collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
					(m, buf) -> quads(buf, m, mesh.translucent, mesh.translucentTint, s.tint()));
			}
		}
		for (int i = 0; i < Math.min(island.crystals.size(), crystals.length); i++) crystal(pose, collector, island.crystals.get(i), s.time(), i);
		if (island.fox != null) fox(pose, collector, island.fox, s.time());
		if (island.scene.equals("autumn")) {
			collector.submitCustomGeometry(pose, RenderTypes.entitySolid(WHITE), (m, buf) -> {
				for (int i = 0; i < 4; i++) bird(buf, m, i, s.time());
			});
		}
		player(s, pose, collector, p, s.skin(), s.model(), s.lookYaw(), 180 + 38, 0);
		for (int i = 0; i < Math.min(s.friends().size(), island.friends.size()); i++) {
			IslandRenderState.Figure f = s.friends().get(i);
			player(s, pose, collector, island.friends.get(i), f.skin(), f.model(), Mth.sin(s.time() * 0.5F + i) * 0.4F, 180 + 20 + i * 18, i + 1);
		}
		pose.popPose();
	}

	/** Block quads: corner shade x block colour (grass, leaves …) x the scene's light colour; glowing blocks stay bright. */
	private static void quads(VertexConsumer buf, PoseStack.Pose m, float[] v, int[] tints, int sceneTint) {
		int st = BlockMesher.STRIDE;
		for (int i = 0; i < tints.length; i++) {
			int o = i * st;
			float glow = v[o + 7];
			float shade = Math.min(1F, v[o + 5] + glow);
			int tint = tints[i];
			float light = glow > 0 ? 1F : 0F;
			float r = shade * (ARGB.red(tint) / 255F) * (light + (1 - light) * ARGB.red(sceneTint) / 255F);
			float g = shade * (ARGB.green(tint) / 255F) * (light + (1 - light) * ARGB.green(sceneTint) / 255F);
			float b = shade * (ARGB.blue(tint) / 255F) * (light + (1 - light) * ARGB.blue(sceneTint) / 255F);
			buf.addVertex(m, v[o], v[o + 1], v[o + 2]).setColor(ARGB.colorFromFloat(1F, r, g, b)).setUv(v[o + 3], v[o + 4])
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(m, 0, 1, 0);
		}
	}

	/** A real end crystal turning above the pillar. */
	private void crystal(PoseStack pose, SubmitNodeCollector collector, float[] at, float time, int i) {
		if (crystals[i] == null) crystals[i] = new EndCrystalModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.END_CRYSTAL));
		EndCrystalRenderState state = crystalStates[i];
		state.ageInTicks = time * 20F + i * 37F;
		state.showsBottom = false;
		crystals[i].setupAnim(state);
		pose.pushPose();
		pose.translate(at[0], at[1], at[2]);
		pose.scale(2F, 2F, 2F);
		pose.translate(0F, -0.5F, 0F);
		collector.submitModel(crystals[i], state, pose, END_CRYSTAL, LIGHT, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	/** The real fox, curled up asleep (vanilla model and sleeping texture), breathing slowly. */
	private void fox(PoseStack pose, SubmitNodeCollector collector, float[] at, float time) {
		if (foxModel == null) foxModel = new AdultFoxModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.FOX));
		foxState.isSleeping = true;
		foxState.ageInTicks = time * 20F;
		foxModel.setupAnim(foxState);
		float breathe = 1 + Mth.sin(time * 2.2F) * 0.02F;
		pose.pushPose();
		pose.translate(at[0] + 0.5F, at[1], at[2] + 0.5F);
		pose.mulPose(new Matrix4f().rotationY(0.6F + Mth.PI));
		pose.scale(-1F, -breathe, 1F); // entity model space: y down, like LivingEntityRenderer
		pose.translate(0F, -1.501F, 0F);
		collector.submitModel(foxModel, foxState, pose, FOX_SLEEP, LIGHT, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	/** An axis-aligned box in block units, coloured, with simple per-face shading. */
	private static void box(VertexConsumer buf, PoseStack.Pose m, Matrix4f local, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
		float[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
		int[][] faces = {{1, 2, 6, 5}, {4, 7, 3, 0}, {3, 7, 6, 2}, {0, 1, 5, 4}, {4, 5, 6, 7}, {1, 0, 3, 2}};
		float[][] normals = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		float[] shade = {0.82F, 0.82F, 1.0F, 0.6F, 0.82F, 0.82F};
		org.joml.Vector3f v = new org.joml.Vector3f();
		for (int f = 0; f < 6; f++) {
			int col = ARGB.scaleRGB(0xFF000000 | rgb, shade[f]);
			for (int k : faces[f]) {
				local.transformPosition(c[k][0], c[k][1], c[k][2], v);
				buf.addVertex(m, v.x, v.y, v.z).setColor(col).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT)
					.setNormal(m, normals[f][0], normals[f][1], normals[f][2]);
			}
		}
	}

	private static void bird(VertexConsumer buf, PoseStack.Pose m, int i, float t) {
		float radius = 14 + i * 2.5F;
		float a = t * (0.18F + i * 0.03F) + i * 1.7F;
		float h = 4 + i * 1.6F + Mth.sin(t * 0.9F + i) * 0.6F;
		Matrix4f l = new Matrix4f().translate(Mth.cos(a) * radius, h, Mth.sin(a) * radius - 4).rotateY(-a - Mth.HALF_PI).scale(1 / 16F);
		int c = 0x3A2A40;
		box(buf, m, l, -2.5F, -1, -1, 2.5F, 1, 1, c);
		box(buf, m, l, 2, -0.4F, -1, 4, 1.6F, 1, c);
		float flap = Mth.sin(t * 9 + i) * 0.7F;
		box(buf, m, new Matrix4f(l).translate(0, 0.6F, 1).rotateX(flap), -1.75F, -0.25F, 0, 1.75F, 0.25F, 6, c);
		box(buf, m, new Matrix4f(l).translate(0, 0.6F, -1).rotateX(-flap), -1.75F, -0.25F, -6, 1.75F, 0.25F, 0, c);
	}

	/**
	 * A figure standing on the island: you (slot 0, with your cosmetics) or a friend (slots 1..4).
	 * Each slot has its own model, because model parts are drawn later and must keep their own pose.
	 */
	private void player(IslandRenderState s, PoseStack pose, SubmitNodeCollector collector, float[] p, Identifier skin, PlayerModelType type,
		float look, float yawDeg, int slot) {
		if (skin == null) return;
		if (wide[slot] == null) {
			wide[slot] = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER);
			slim[slot] = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM);
		}
		ModelPart root = type == PlayerModelType.SLIM ? slim[slot] : wide[slot];
		float t = s.time() + slot * 1.7F;
		root.getChild("head").yRot = look;
		root.getChild("head").xRot = Mth.sin(t * 0.6F) * 0.05F;
		root.getChild("right_arm").xRot = Mth.sin(t * 1.1F) * 0.06F;
		root.getChild("left_arm").xRot = -Mth.sin(t * 1.1F) * 0.06F;
		root.getChild("right_arm").zRot = 0.06F + Mth.sin(t * 1.3F) * 0.03F;
		root.getChild("left_arm").zRot = -0.06F - Mth.sin(t * 1.3F) * 0.03F;
		pose.pushPose();
		pose.translate(p[0] + 0.5F, p[1], p[2] + 0.5F);
		pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(yawDeg)));
		if (slot == 0) pet(s.time(), pose, collector);
		pose.scale(-1, -1, 1); // entity model space: y down, like LivingEntityRenderer
		pose.translate(0, -1.5F, 0);
		collector.submitModelPart(root, pose, RenderTypes.entityTranslucent(skin), LIGHT, OverlayTexture.NO_OVERLAY, null);
		if (slot == 0) cosmetics(s.time(), pose, collector, root);
		pose.popPose();
	}

	/** Your Blockbench cosmetics on the island figure, exactly as in the game. */
	public static void cosmetics(float time, PoseStack pose, SubmitNodeCollector collector, ModelPart root) {
		Outfit outfit = OutfitStore.current();
		EModelRenderer.Motion motion = EModelRenderer.Motion.idle(time);
		for (String slot : new String[] {"head", "body", "wings", "cape"}) {
			EModel m = outfit.model(slot);
			if (m == null) continue;
			pose.pushPose();
			root.getChild(slot.equals("head") ? "head" : "body").translateAndRotate(pose);
			EModelRenderer.toPartSpace(pose);
			EModelRenderer.submit(m, pose, collector, LIGHT, OverlayTexture.NO_OVERLAY, motion);
			pose.popPose();
		}
	}

	/** Your pet sits next to you on the island. */
	public static void pet(float time, PoseStack pose, SubmitNodeCollector collector) {
		EModel pet = OutfitStore.current().model("pet");
		if (pet == null) return;
		pose.pushPose();
		pose.translate(-0.95F, 0, -0.35F);
		pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-25)));
		pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
		EModelRenderer.submit(pet, pose, collector, LIGHT, OverlayTexture.NO_OVERLAY, EModelRenderer.Motion.idle(time));
		pose.popPose();
	}
}
