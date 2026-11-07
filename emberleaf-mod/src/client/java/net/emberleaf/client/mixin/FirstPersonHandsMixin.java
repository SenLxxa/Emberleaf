package net.emberleaf.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.emberleaf.client.config.Modules;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Totem in der Zweithand verbergen": the totem keeps working, it just does not block your view any more. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void emberleaf$hideTotem(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float a, float b, InteractionHand hand,
		float c, ItemStack stack, float d, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (hand == InteractionHand.OFF_HAND && stack.is(Items.TOTEM_OF_UNDYING) && Modules.on("hide_offhand_totem")) ci.cancel();
	}
}
