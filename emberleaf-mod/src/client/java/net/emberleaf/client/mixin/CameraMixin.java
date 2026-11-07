package net.emberleaf.client.mixin;

import net.emberleaf.client.feature.Freelook;
import net.emberleaf.client.feature.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void emberleaf$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		float factor = Zoom.factor(partialTicks);
		if (factor < 0.999F) cir.setReturnValue(cir.getReturnValueF() * factor);
	}

	/** Freelook and the AFK camera: the camera takes its own direction instead of the player's head. */
	@ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
	private void emberleaf$freelook(Args args) {
		if (Freelook.overriding()) {
			args.set(0, Freelook.yaw());
			args.set(1, Freelook.pitch());
		}
	}
}
