package net.emberleaf.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.emberleaf.client.config.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HUD extras: hide the scoreboard, no pumpkin blur, a coloured crosshair. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void emberleaf$hideScoreboard(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (Modules.on("hide_scoreboard")) ci.cancel();
	}

	@Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
	private void emberleaf$noPumpkin(GuiGraphicsExtractor graphics, Identifier texture, float alpha, CallbackInfo ci) {
		if (Modules.on("no_pumpkin") && texture.getPath().contains("pumpkinblur")) ci.cancel();
	}

	@WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void emberleaf$crosshairColor(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h,
		Operation<Void> original) {
		int color = Modules.crosshairColor();
		if (color == 0) original.call(graphics, pipeline, sprite, x, y, w, h);
		else graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, color);
	}
}
