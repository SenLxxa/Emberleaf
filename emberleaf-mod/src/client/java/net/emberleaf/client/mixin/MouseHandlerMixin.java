package net.emberleaf.client.mixin;

import net.emberleaf.client.feature.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While zooming, the mouse wheel changes the zoom instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void emberleaf$scrollZoom(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (Zoom.isZooming() && yoffset != 0) {
			Zoom.scroll(yoffset);
			ci.cancel();
		}
	}
}
