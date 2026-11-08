package net.emberleaf.client.mixin;

import java.time.LocalTime;
import java.util.List;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.feature.ChatExtras;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat extras: peek (hold a key to read the whole chat), the time in front of every line, and repeated messages
 * stacked into one line with a counter ("(×3)") instead of flooding the chat.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Shadow @Final private List<GuiMessage> allMessages;
	@Shadow @Final private List<GuiMessage.Line> trimmedMessages;

	@Unique private static boolean emberleaf$busy;
	@Unique private static @Nullable String emberleaf$lastText;
	@Unique private static @Nullable GuiMessage emberleaf$lastMessage;
	@Unique private static int emberleaf$count;

	@Shadow
	private void addMessage(Component contents, @Nullable MessageSignature signature, GuiMessageSource source, @Nullable GuiMessageTag tag) {
		throw new AssertionError();
	}

	@Inject(method = "isChatFocused", at = @At("HEAD"), cancellable = true)
	private void emberleaf$peek(CallbackInfoReturnable<Boolean> cir) {
		if (ChatExtras.peeking()) cir.setReturnValue(true);
	}

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
		at = @At("HEAD"), cancellable = true)
	private void emberleaf$extras(Component contents, @Nullable MessageSignature signature, GuiMessageSource source, @Nullable GuiMessageTag tag,
		CallbackInfo ci) {
		if (emberleaf$busy) return;
		boolean stack = Modules.on("chat_stack");
		boolean stamp = Modules.on("chat_timestamps");
		if (!stack && !stamp) return;
		String text = contents.getString();
		int count = 1;
		if (stack && text.equals(emberleaf$lastText) && !allMessages.isEmpty() && allMessages.getFirst() == emberleaf$lastMessage) {
			// the same line again: replace the old one by one with a counter
			count = emberleaf$count + 1;
			GuiMessage previous = allMessages.removeFirst();
			trimmedMessages.removeIf(line -> line.parent() == previous);
		}
		MutableComponent line = Component.empty();
		if (stamp) {
			LocalTime now = LocalTime.now();
			line.append(Component.literal(String.format("[%02d:%02d] ", now.getHour(), now.getMinute())).withStyle(ChatFormatting.DARK_GRAY));
		}
		line.append(contents);
		if (count > 1) line.append(Component.literal(" (×" + count + ")").withStyle(ChatFormatting.GRAY));
		emberleaf$busy = true;
		try {
			addMessage(line, signature, source, tag);
		} finally {
			emberleaf$busy = false;
		}
		emberleaf$lastText = text;
		emberleaf$count = count;
		emberleaf$lastMessage = allMessages.isEmpty() ? null : allMessages.getFirst();
		ci.cancel();
	}
}
