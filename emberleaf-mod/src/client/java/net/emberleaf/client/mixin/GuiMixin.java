package net.emberleaf.client.mixin;

import net.emberleaf.client.menu.EmberleafTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shows the Emberleaf main menu wherever the game would open the vanilla title screen. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private boolean clientLevelTeardownInProgress;

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen emberleaf$ownTitleScreen(Screen screen) {
		if (minecraft.isDemo() || !net.emberleaf.client.config.Modules.on("menu")) return screen;
		if (screen != null) return screen.getClass() == TitleScreen.class ? new EmberleafTitleScreen() : screen;
		// setScreen(null) without a world means "back to the title screen"
		return minecraft.level == null && !clientLevelTeardownInProgress ? new EmberleafTitleScreen() : null;
	}
}
