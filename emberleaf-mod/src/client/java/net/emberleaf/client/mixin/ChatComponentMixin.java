package net.emberleaf.client.mixin;

import net.emberleaf.client.feature.ChatExtras;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat peek: while the key is held, the chat shows all lines as if it were open. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Inject(method = "isChatFocused", at = @At("HEAD"), cancellable = true)
	private void emberleaf$peek(CallbackInfoReturnable<Boolean> cir) {
		if (ChatExtras.peeking()) cir.setReturnValue(true);
	}
}
