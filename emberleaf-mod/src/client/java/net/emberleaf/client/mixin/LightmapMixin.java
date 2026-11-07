package net.emberleaf.client.mixin;

import net.emberleaf.client.config.Modules;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: the light map behaves as if you had night vision and full brightness. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void emberleaf$fullbright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.needsUpdate && Modules.on("fullbright")) {
			state.brightness = 1.0F;
			state.nightVisionEffectIntensity = 1.0F;
		}
	}
}
