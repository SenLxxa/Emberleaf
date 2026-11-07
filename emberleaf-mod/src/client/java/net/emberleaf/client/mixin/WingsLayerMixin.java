package net.emberleaf.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.emberleaf.client.pack.PackFeatures;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Renamed elytras wear the matching elytra from the texture pack (OptiFine CIT rules, e.g. Elytras+: "Citrus").
 * The elytra layer already takes a "player texture" (cape/skin elytra); a named elytra replaces it.
 */
@Mixin(WingsLayer.class)
public class WingsLayerMixin {
	@ModifyArg(
		method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V"),
		index = 8)
	private Identifier emberleaf$namedElytra(EquipmentClientInfo.LayerType layer, ResourceKey<EquipmentAsset> asset, Model<?> model, Object state,
		ItemStack stack, PoseStack pose, SubmitNodeCollector collector, int light, Identifier texture, int outline, int order) {
		Identifier named = PackFeatures.elytraTexture(stack);
		return named != null ? named : texture;
	}
}
