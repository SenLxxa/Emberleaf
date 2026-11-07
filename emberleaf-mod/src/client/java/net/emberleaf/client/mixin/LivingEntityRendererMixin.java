package net.emberleaf.client.mixin;

import net.emberleaf.client.config.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Eigenen Namen anzeigen": in third person you see your own name (and badges) like everyone else does. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
	private void emberleaf$ownName(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player && Modules.on("own_name") && !mc.options.getCameraType().isFirstPerson() && !entity.isInvisible()
			&& !mc.gui.hud.isHidden()) {
			cir.setReturnValue(true);
		}
	}
}
