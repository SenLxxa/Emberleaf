package net.emberleaf.client.mixin;

import net.emberleaf.client.feature.Emotes;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets Emberleaf emotes override the player's pose after vanilla animated it. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void emberleaf$emote(AvatarRenderState state, CallbackInfo ci) {
		Emotes.apply((PlayerModel) (Object) this, state.id);
	}
}
