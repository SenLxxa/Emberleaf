package net.emberleaf.client.feature;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.emberleaf.client.config.Modules;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Speech bubbles: what a player wrote in chat floats above their head for a few seconds. */
public final class SpeechBubbles {
	private static final long SHOW_MS = 7000;
	private static final int MAX_CHARS = 46;

	private record Bubble(String text, long at) {}

	private static final Map<UUID, Bubble> BUBBLES = new ConcurrentHashMap<>();

	private SpeechBubbles() {}

	public static void register() {
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> {
			if (sender == null || signed == null) return;
			say(sender.id(), signed.signedContent());
		});
	}

	public static void say(UUID uuid, String text) {
		String t = text.strip();
		if (t.isEmpty() || t.startsWith("/")) return;
		if (t.length() > MAX_CHARS) t = t.substring(0, MAX_CHARS - 1).strip() + "…";
		BUBBLES.put(uuid, new Bubble(t, System.currentTimeMillis()));
	}

	/** The bubble text of a player, or null when they said nothing recently. */
	public static @Nullable Component of(UUID uuid) {
		if (!Modules.on("speech_bubbles")) return null;
		Bubble b = BUBBLES.get(uuid);
		if (b == null) return null;
		if (System.currentTimeMillis() - b.at() > SHOW_MS) {
			BUBBLES.remove(uuid);
			return null;
		}
		return Component.literal("» " + b.text() + " «").withStyle(ChatFormatting.WHITE);
	}
}
