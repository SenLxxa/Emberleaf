package net.emberleaf.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.emberleaf.client.config.Modules;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Feuer niedriger": the burning overlay sits lower, so you still see what is in front of you. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Unique private static boolean emberleaf$lowered;

	@Inject(method = "submitFire", at = @At("HEAD"))
	private static void emberleaf$lowFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		emberleaf$lowered = Modules.on("low_fire");
		if (!emberleaf$lowered) return;
		poseStack.pushPose();
		poseStack.translate(0.0F, -0.28F, 0.0F);
	}

	@Inject(method = "submitFire", at = @At("RETURN"))
	private static void emberleaf$lowFireEnd(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (emberleaf$lowered) poseStack.popPose();
		emberleaf$lowered = false;
	}
}
