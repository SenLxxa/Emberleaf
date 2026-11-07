package net.emberleaf.client.mixin;

import net.emberleaf.client.menu.MenuScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menus outside a world (world list, options …) show the Emberleaf forest instead of the vanilla panorama. */
@Mixin(Screen.class)
public abstract class ScreenPanoramaMixin {
	@Shadow
	public int width;

	@Shadow
	public int height;

	@Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
	private void emberleaf$forestPanorama(GuiGraphicsExtractor graphics, float a, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
		double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight());
		MenuScene.draw(graphics, this.width, this.height, (float) mx, (float) my, 0);
		MenuScene.drawFront(graphics, this.width, this.height);
		ci.cancel();
	}
}
