package net.emberleaf.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.emberleaf.client.model.EModelRenderer;
import net.emberleaf.client.outfit.Outfits;
import net.emberleaf.client.render.CapeCloth;
import net.emberleaf.client.render.CapeTextures;
import net.emberleaf.client.render.CosmeticsLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the Emberleaf cape as bending cloth (CapeCloth) on the body instead of the vanilla cape. */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
	@Shadow
	@Final
	private HumanoidModel<AvatarRenderState> model;

	@Shadow
	private boolean hasLayer(ItemStack itemStack, EquipmentClientInfo.LayerType layerType) {
		throw new AssertionError("replaced by mixin");
	}

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void emberleaf$submitCape(
		PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci
	) {
		if (state.isInvisible) return;
		java.util.UUID owner = Outfits.uuidOf(state.id);
		if (!net.emberleaf.client.config.Modules.on(Outfits.isLocal(owner) ? "cosmetics" : "cosmetics_others")) return;
		if (Outfits.forPlayer(Outfits.uuidOf(state.id)).model("cape") != null) {
			ci.cancel(); // a Blockbench cape is drawn by the cosmetics layer instead
			return;
		}
		if (!state.showCape) return;
		Identifier cape = CapeTextures.frameFor(Outfits.uuidOf(state.id), state.ageInTicks);
		if (cape == null) return;
		ci.cancel();
		if (this.hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) return; // Elytra replaces the cape
		poseStack.pushPose();
		if (this.hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID)) {
			poseStack.translate(0.0F, -0.053125F, 0.06875F);
		}
		// follow the body (crouching tilts it), then hang the cape as bending cloth instead of the stiff vanilla plate
		this.model.setupAnim(state);
		this.model.root().translateAndRotate(poseStack);
		this.model.body.translateAndRotate(poseStack);
		EModelRenderer.toPartSpace(poseStack);
		CapeCloth.submitImageCape(poseStack, collector, cape, light, ((long) state.id << 3) | 7, CosmeticsLayer.motionOf(state));
		poseStack.popPose();
	}
}
