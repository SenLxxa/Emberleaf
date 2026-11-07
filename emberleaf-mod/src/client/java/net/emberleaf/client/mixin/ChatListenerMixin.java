package net.emberleaf.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import net.emberleaf.client.config.Modules;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** "Köpfe im Chat": every chat message starts with the little head of whoever wrote it. */
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
	@ModifyVariable(method = "showMessageToPlayer", at = @At("HEAD"), argsOnly = true)
	private Component emberleaf$head(Component decorated, @Local(argsOnly = true) GameProfile sender) {
		if (!Modules.on("chat_heads") || sender == null) return decorated;
		MutableComponent head = MutableComponent.create(new ObjectContents(new PlayerSprite(ResolvableProfile.createResolved(sender), true),
			Optional.empty()));
		return Component.empty().append(head).append(" ").append(decorated);
	}
}
