package net.emberleaf.gametest;

import net.emberleaf.client.menu.EmberleafSettingsScreen;
import net.emberleaf.client.menu.EmberleafTitleScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/** Screenshots of the live main menu: cave, nether, overworld (day, sunset, night), end, the classic island, winter snow. */
public class MainMenuScreenshotTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (Only.skip("menu")) return;
		context.waitFor(mc -> mc.gui.screen() instanceof EmberleafTitleScreen, 600);
		System.setProperty("emberleaf.menuPhase", "sunset");
		for (String scene : new String[] {"cave", "nether", "end", "island"}) {
			System.setProperty("emberleaf.menuScene", scene);
			context.waitTicks(scene.equals("cave") ? 90 : 70); // let the camera finish its flight in
			context.takeScreenshot("emberleaf-menu-" + scene);
		}
		System.setProperty("emberleaf.menuScene", "overworld");
		for (String phase : new String[] {"sunset", "day", "night"}) {
			System.setProperty("emberleaf.menuPhase", phase);
			context.waitTicks(phase.equals("sunset") ? 70 : 15);
			context.takeScreenshot("emberleaf-menu-overworld-" + phase);
		}
		System.setProperty("emberleaf.menuPhase", "sunset");
		System.setProperty("emberleaf.menuScene", "cave");
		System.setProperty("emberleaf.winter", "true");
		context.waitTicks(20);
		context.takeScreenshot("emberleaf-menu-winter");
		System.clearProperty("emberleaf.winter");
		context.setScreen(() -> new EmberleafSettingsScreen(Minecraft.getInstance().gui.screen()));
		context.waitTicks(15);
		context.takeScreenshot("emberleaf-settings");
		context.setScreen(() -> null);
		context.waitFor(mc -> mc.gui.screen() instanceof EmberleafTitleScreen, 100);
		context.setScreen(() -> new SelectWorldScreen(Minecraft.getInstance().gui.screen()));
		context.waitTicks(20);
		context.takeScreenshot("emberleaf-menu-worlds");
		context.setScreen(() -> null);
		context.waitFor(mc -> mc.gui.screen() instanceof EmberleafTitleScreen, 100);
		System.clearProperty("emberleaf.menuPhase");
		System.clearProperty("emberleaf.menuScene");
	}
}
