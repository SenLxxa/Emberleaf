package net.emberleaf.client.totem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Skin-Totem: the Totem of Undying becomes a little 3D figure with a big head, wearing the skin of
 * whoever holds it (your own skin in your inventory).
 */
public class SkinTotemRenderer implements SpecialModelRenderer<PlayerSkin> {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("emberleaf", "skin_totem");
	/** Set by SpecialModelWrapperMixin while an item model is updated, so we know who holds the totem. */
	public static @Nullable ItemOwner currentOwner;

	private final ModelPart wide;
	private final ModelPart slim;

	private SkinTotemRenderer(ModelPart wide, ModelPart slim) {
		this.wide = pose(wide);
		this.slim = pose(slim);
	}

	/** Totem pose: arms spread a little, big cute head. */
	private static ModelPart pose(ModelPart root) {
		ModelPart head = root.getChild("head");
		head.xScale = head.yScale = head.zScale = 1.45F;
		ModelPart rightArm = root.getChild("right_arm");
		ModelPart leftArm = root.getChild("left_arm");
		rightArm.zRot = 0.42F;
		leftArm.zRot = -0.42F;
		rightArm.xRot = -0.12F;
		leftArm.xRot = -0.12F;
		root.getChild("right_leg").zRot = 0.04F;
		root.getChild("left_leg").zRot = -0.04F;
		return root;
	}

	@Override
	public void submit(@Nullable PlayerSkin skin, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
		PlayerSkin s = skin != null ? skin : localSkin();
		if (s == null) return;
		ModelPart model = s.model() == PlayerModelType.SLIM ? slim : wide;
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.02F, 0.5F);
		poseStack.mulPose(new Matrix4f().rotationY((float) Math.PI));
		poseStack.scale(0.42F, 0.42F, 0.42F);
		poseStack.scale(-1, -1, 1);
		poseStack.translate(0, -1.5F, 0);
		collector.submitModelPart(model, poseStack, RenderTypes.entityTranslucent(s.body().texturePath()), light, overlay, null, -1, outline);
		poseStack.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		output.accept(new Vector3f(0.2F, 0.0F, 0.35F));
		output.accept(new Vector3f(0.8F, 1.0F, 0.65F));
	}

	@Override
	public @Nullable PlayerSkin extractArgument(ItemStack stack) {
		ItemOwner owner = currentOwner;
		LivingEntity holder = owner != null ? owner.asLivingEntity() : null;
		if (holder instanceof AbstractClientPlayer player) return player.getSkin();
		return localSkin();
	}

	private static @Nullable PlayerSkin localSkin() {
		return net.emberleaf.client.menu.MenuScene.localSkin(Minecraft.getInstance());
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<PlayerSkin> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SkinTotemRenderer bake(SpecialModelRenderer.BakingContext context) {
			return new SkinTotemRenderer(context.entityModelSet().bakeLayer(ModelLayers.PLAYER), context.entityModelSet().bakeLayer(ModelLayers.PLAYER_SLIM));
		}
	}
}
