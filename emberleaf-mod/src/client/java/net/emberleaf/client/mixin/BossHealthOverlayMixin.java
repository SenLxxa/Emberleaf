package net.emberleaf.client.mixin;

import net.emberleaf.client.config.Modules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Bossleiste ausblenden": no boss bars at the top of the screen. */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void emberleaf$hide(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (Modules.on("hide_bossbar")) ci.cancel();
	}
}
