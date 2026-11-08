package net.emberleaf.client.mixin;

import net.emberleaf.client.config.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Ping als Zahl": the tab list shows everyone's ping in milliseconds instead of signal bars. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
	private void emberleaf$pingNumber(GuiGraphicsExtractor graphics, int slotWidth, int xo, int yo, PlayerInfo info, CallbackInfo ci) {
		if (!Modules.on("tab_ping")) return;
		int ms = info.getLatency();
		String text = ms < 0 ? "?" : String.valueOf(ms);
		int color = ms < 0 ? 0xFFAAAAAA : ms < 80 ? 0xFF7DDC5A : ms < 150 ? 0xFFC8E05A : ms < 300 ? 0xFFF2C14E : 0xFFFF6A4A;
		Font font = Minecraft.getInstance().font;
		graphics.text(font, text, xo + slotWidth - font.width(text) - 1, yo, color, true);
		ci.cancel();
	}
}
