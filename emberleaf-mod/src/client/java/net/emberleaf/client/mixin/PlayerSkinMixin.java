package net.emberleaf.client.mixin;

import net.emberleaf.client.skin.SkinLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** After switching skins in game, the local player wears the new one right away (Mojang's copy updates on the next login). */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerSkinMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void emberleaf$override(CallbackInfoReturnable<PlayerSkin> cir) {
		if (SkinLibrary.override() == null) return;
		Minecraft mc = Minecraft.getInstance();
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (self.getUUID().equals(mc.getUser().getProfileId())) cir.setReturnValue(SkinLibrary.apply(cir.getReturnValue()));
	}
}
