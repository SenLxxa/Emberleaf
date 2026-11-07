package net.emberleaf.client.mixin;

import net.emberleaf.client.config.Modules;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Klares Wasser": under water you see much further. */
@Mixin(WaterFogEnvironment.class)
public abstract class WaterFogMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void emberleaf$clear(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta, CallbackInfo ci) {
		if (!Modules.on("clear_water")) return;
		fog.environmentalStart = Math.max(fog.environmentalStart, 8F);
		fog.environmentalEnd = Math.max(fog.environmentalEnd * 3.5F, 96F);
	}
}
