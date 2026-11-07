package net.emberleaf.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.feature.Freelook;
import net.emberleaf.client.feature.SpeechBubbles;
import net.emberleaf.client.friends.FriendsSync;
import net.emberleaf.client.friends.Lagerfeuer;
import net.emberleaf.client.menu.LagerfeuerScreen;
import net.emberleaf.client.menu.PlayerMenuScreen;
import net.emberleaf.client.menu.EmberleafSettingsScreen;
import net.emberleaf.client.menu.EmberleafTitleScreen;
import net.emberleaf.client.menu.EmoteWheelScreen;
import java.util.function.Function;
import net.emberleaf.client.tools.ArmorStandTool;
import net.emberleaf.client.tools.BannerTool;
import net.emberleaf.client.tools.BookTool;
import net.emberleaf.client.tools.CircleTool;
import net.emberleaf.client.tools.ColorCodesTool;
import net.emberleaf.client.tools.CoordsTool;
import net.emberleaf.client.tools.DisplayTool;
import net.emberleaf.client.tools.DyeTool;
import net.emberleaf.client.tools.EnchantTool;
import net.emberleaf.client.tools.FireworkTool;
import net.emberleaf.client.tools.GradientTool;
import net.emberleaf.client.tools.HeadTool;
import net.emberleaf.client.tools.LocatorTool;
import net.emberleaf.client.tools.MotdTool;
import net.emberleaf.client.tools.PotionTool;
import net.emberleaf.client.tools.TellrawTool;
import net.emberleaf.client.tools.TickTool;
import net.emberleaf.client.tools.ToolsScreen;
import net.emberleaf.client.tools.XpTool;
import net.minecraft.client.gui.screens.Screen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.ChatScreen;

/** The Laby-style extras: tools, badges above names, speech bubbles, heads in chat, emote wheel, AFK camera, playtime. */
public class FeaturesTest implements FabricClientGameTest {
	private MockLagerfeuer lagerfeuer;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			Path data = mc.gameDirectory.toPath().toAbsolutePath().resolve("emberleaf-features-data");
			try {
				Files.createDirectories(data);
				// pretend a few hours were played, so playtime badges and contrail colours show up
				Files.writeString(data.resolve("playtime.json"), "{\"total\": 39600, \"sessions\": 12, \"days\": {}, \"dimensions\": "
					+ "[\"overworld\", \"the_nether\", \"the_end\"], \"earned\": [\"alpha\"], \"nightOwl\": true}");
				// a small stand-in Lagerfeuer on this PC, already logged in
				String me = mc.getUser().getProfileId().toString().replace("-", "");
				lagerfeuer = new MockLagerfeuer(me);
				Files.writeString(data.resolve("friends.json"), "{\"server\": \"" + lagerfeuer.url() + "\"}");
				Files.writeString(data.resolve("friends-session.json"), "{\"server\": \"" + lagerfeuer.url() + "\", \"token\": \"test\", \"uuid\": \""
					+ me + "\", \"name\": \"Player0\", \"expires\": 4102444800}");
			} catch (IOException e) {
				throw new AssertionError(e);
			}
			System.setProperty("emberleaf.dataDir", data.toString());
			FriendsSync.reloadConfig();
		});
		context.waitFor(mc -> mc.gui.screen() instanceof EmberleafTitleScreen, 600);
		context.waitTicks(40);
		context.takeScreenshot("features-title");
		context.setScreen(() -> new ToolsScreen(null));
		context.waitTicks(10);
		context.takeScreenshot("features-tools");
		// every tool at the smallest window size: nothing may overlap the result line and the buttons
		tool(context, "colorcodes", ColorCodesTool::new);
		tool(context, "gradient", GradientTool::new);
		tool(context, "motd", MotdTool::new);
		tool(context, "tellraw", TellrawTool::new);
		tool(context, "head", HeadTool::new);
		tool(context, "book", BookTool::new);
		tool(context, "banner", BannerTool::new);
		tool(context, "firework", FireworkTool::new);
		tool(context, "dye", DyeTool::new);
		tool(context, "armorstand", ArmorStandTool::new);
		tool(context, "display", DisplayTool::new);
		tool(context, "coords", CoordsTool::new);
		tool(context, "circle", CircleTool::new);
		tool(context, "xp", XpTool::new);
		tool(context, "tick", TickTool::new);
		tool(context, "locator", LocatorTool::new);
		tool(context, "potion", PotionTool::new);
		tool(context, "enchant", EnchantTool::new);
		context.setScreen(() -> null);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("time set 6000");
			server.runCommand("gamerule advance_time false");
			context.runOnClient(mc -> {
				Modules.set("own_name", true);
				Modules.set("hud_playtime", true);
				Modules.setChoice("contrail_color", "rose");
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				SpeechBubbles.say(mc.player.getUUID(), "Hallo vom Lagerfeuer!");
			});
			for (int i = 0; i < 40; i++) {
				context.runOnClient(mc -> {
					mc.player.setYRot(180.0F);
					mc.player.setYHeadRot(180.0F);
					mc.player.setYBodyRot(180.0F);
					mc.player.setXRot(5.0F);
				});
				context.waitTick();
			}
			context.takeScreenshot("features-badges-bubble");
			// a chat message from you: your head appears in front of it
			context.runOnClient(mc -> mc.getConnection().sendChat("Emberleaf 0.0.2 Alpha ist da!"));
			context.waitTicks(20);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(10);
			context.takeScreenshot("features-chat");
			context.setScreen(() -> null);
			context.setScreen(EmoteWheelScreen::new);
			context.waitTicks(15);
			context.takeScreenshot("features-emote-wheel");
			context.setScreen(() -> null);
			context.setScreen(() -> new EmberleafSettingsScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("features-settings");
			context.setScreen(() -> null);
			// AFK camera: nobody touched anything for a minute
			context.runOnClient(mc -> Freelook.forceAfk());
			context.waitTicks(60);
			context.takeScreenshot("features-afk-camera");
			context.runOnClient(mc -> {
				if (!Freelook.overriding()) throw new AssertionError("AFK-Kamera ist nicht angesprungen");
			});
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.setScreen(() -> null);

			// Lagerfeuer: a friend's message pops up top right (the sync runs every few seconds)
			context.waitFor(mc -> Lagerfeuer.cursor() >= 0, 400);
			lagerfeuer.announceNext();
			context.waitFor(mc -> Lagerfeuer.hasNotices(), 400);
			context.waitTicks(12);
			context.takeScreenshot("features-lagerfeuer-notice");
			context.runOnClient(mc -> {
				if (Lagerfeuer.party() == null) throw new AssertionError("Party vom Lagerfeuer fehlt");
			});
			context.setScreen(() -> new LagerfeuerScreen(null, null));
			context.waitTicks(60);
			context.takeScreenshot("features-lagerfeuer-chat");
			context.runOnClient(mc -> ((LagerfeuerScreen) mc.gui.screen()).selectFriend(MockLagerfeuer.PERRY));
			context.waitTicks(20);
			context.takeScreenshot("features-lagerfeuer-friends");
			context.runOnClient(mc -> ((LagerfeuerScreen) mc.gui.screen()).select("PARTY"));
			context.waitTicks(20);
			context.takeScreenshot("features-lagerfeuer-party");
			context.setScreen(() -> new PlayerMenuScreen(Lagerfeuer.uuid(MockLagerfeuer.PERRY), "PerryDasHuhn", null));
			context.waitTicks(15);
			context.takeScreenshot("features-player-menu");
			context.setScreen(() -> null);
		} finally {
			if (lagerfeuer != null) lagerfeuer.stop();
		}
	}

	private static void tool(ClientGameTestContext context, String name, Function<Screen, Screen> open) {
		context.setScreen(() -> open.apply(null));
		context.waitTicks(4);
		context.takeScreenshot("features-tool-" + name);
	}
}
