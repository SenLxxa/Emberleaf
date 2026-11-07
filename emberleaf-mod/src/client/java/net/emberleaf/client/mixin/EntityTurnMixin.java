package net.emberleaf.client.mixin;

import net.emberleaf.client.feature.Freelook;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the camera is free (freelook), the mouse turns the camera, not you. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void emberleaf$freelook(double yRot, double xRot, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer && Freelook.turn(yRot, xRot)) ci.cancel();
	}
}
