package net.emberleaf.client.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The pause menu's "Disconnect" button, for "Verlassen bestätigen". */
@Mixin(PauseScreen.class)
public interface PauseScreenAccessor {
	@Accessor("disconnectButton")
	Button emberleaf$disconnectButton();
}
