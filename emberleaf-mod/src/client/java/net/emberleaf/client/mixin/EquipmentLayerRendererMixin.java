package net.emberleaf.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.emberleaf.client.config.Modules;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Glowing armor trims: the trim pass (2nd model submit per armor layer) is drawn at full brightness, so trims shine at night and in caves. */
@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererMixin {
	private static final int FULL_BRIGHT = 0xF000F0;

	@ModifyArg(
		method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
		at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"),
		index = 4)
	private int emberleaf$glowingTrim(Model<?> model, Object state, PoseStack pose, RenderType type, int light, int overlay, int tint, UvMapping uv,
		int outline) {
		if (!Modules.on("glowing_trims")) return light;
		// 3D armour (Entity Model Features models) draws its own textured 3D parts again in the trim pass;
		// lighting that pass up would make the whole armour glow, not just the trim
		if (model.root().getClass() != ModelPart.class) return light;
		return FULL_BRIGHT;
	}
}
