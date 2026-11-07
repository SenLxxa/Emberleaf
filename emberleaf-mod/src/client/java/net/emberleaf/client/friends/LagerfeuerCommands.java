package net.emberleaf.client.friends;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.emberleaf.client.menu.LagerfeuerScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client commands behind the clickable Lagerfeuer chat lines (and for typing):
 * /emberleaf chat [chat], /emberleaf party join|decline <party>, /emberleaf party follow.
 */
public final class LagerfeuerCommands {
	private LagerfeuerCommands() {}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("emberleaf")
			.then(ClientCommands.literal("chat")
				.executes(c -> open(null))
				.then(ClientCommands.argument("chat", StringArgumentType.word()).executes(c -> open(StringArgumentType.getString(c, "chat")))))
			.then(ClientCommands.literal("party")
				.then(ClientCommands.literal("join").then(ClientCommands.argument("party", StringArgumentType.word())
					.executes(c -> party("/v1/party/join", StringArgumentType.getString(c, "party"), "Du bist jetzt in der Party"))))
				.then(ClientCommands.literal("decline").then(ClientCommands.argument("party", StringArgumentType.word())
					.executes(c -> party("/v1/party/decline", StringArgumentType.getString(c, "party"), "Einladung abgelehnt"))))
				.then(ClientCommands.literal("follow").executes(c -> follow())))));
	}

	private static int open(String chat) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(new LagerfeuerScreen(null, chat));
		return 1;
	}

	private static int party(String path, String id, String done) {
		JsonObject body = new JsonObject();
		body.addProperty("party", id);
		Minecraft mc = Minecraft.getInstance();
		FriendsSync.call("POST", path, body).whenCompleteAsync((result, error) -> {
			if (error != null) {
				say(mc, Component.literal(FriendsSync.errorText(error)).withStyle(ChatFormatting.RED));
				return;
			}
			if (result.has("party")) Lagerfeuer.setParty(result);
			say(mc, Component.literal(done).withStyle(ChatFormatting.WHITE));
		}, mc);
		return 1;
	}

	private static int follow() {
		Minecraft mc = Minecraft.getInstance();
		String server = Lagerfeuer.leaderServer();
		if (server == null || server.equals("Einzelspieler")) {
			say(mc, Component.literal("Der Party-Leiter spielt gerade auf keinem Server.").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Lagerfeuer.joinServer(mc, server);
		return 1;
	}

	private static void say(Minecraft mc, Component text) {
		if (mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Lagerfeuer.prefix().append(text));
	}
}
