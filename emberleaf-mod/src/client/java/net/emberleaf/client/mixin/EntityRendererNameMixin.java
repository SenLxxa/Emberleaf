package net.emberleaf.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.feature.Badges;
import net.emberleaf.client.feature.SpeechBubbles;
import net.emberleaf.client.outfit.Outfits;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Above a player's name: their Emberleaf badges, and above those what they just said (speech bubble). */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererNameMixin {
	private static final float LINE = 9.0F * 1.15F * 0.025F;

	@Inject(
		method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
	private void emberleaf$above(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, int offset,
		CallbackInfo ci) {
		if (state.nameTag == null || state.nameTagAttachment == null || !(state instanceof AvatarRenderState avatar)) return;
		UUID uuid = Outfits.uuidOf(avatar.id);
		if (uuid == null) return;
		if (Modules.on("badges")) {
			Component badges = Badges.line(Badges.of(uuid));
			if (badges != null) {
				pose.translate(0, LINE, 0);
				collector.submitNameTag(pose, state.nameTagAttachment, offset, badges, !state.isDiscrete, state.lightCoords, camera);
			}
		}
		Component bubble = SpeechBubbles.of(uuid);
		if (bubble != null) {
			pose.translate(0, LINE * 1.1F, 0);
			collector.submitNameTag(pose, state.nameTagAttachment, offset, bubble, !state.isDiscrete, state.lightCoords, camera);
		}
	}
}
