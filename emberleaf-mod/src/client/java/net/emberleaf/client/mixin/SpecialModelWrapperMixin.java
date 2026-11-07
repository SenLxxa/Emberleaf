package net.emberleaf.client.mixin;

import net.emberleaf.client.totem.SkinTotemRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers who holds an item while its special model is set up (for the Skin-Totem). */
@Mixin(SpecialModelWrapper.class)
public abstract class SpecialModelWrapperMixin {
	@Inject(method = "update", at = @At("HEAD"))
	private void emberleaf$rememberOwner(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext context,
		ClientLevel level, ItemOwner owner, int seed, CallbackInfo ci) {
		SkinTotemRenderer.currentOwner = owner;
	}

	@Inject(method = "update", at = @At("RETURN"))
	private void emberleaf$forgetOwner(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext context,
		ClientLevel level, ItemOwner owner, int seed, CallbackInfo ci) {
		SkinTotemRenderer.currentOwner = null;
	}
}
