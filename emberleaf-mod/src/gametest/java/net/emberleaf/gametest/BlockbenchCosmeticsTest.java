package net.emberleaf.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.menu.EmberleafSettingsScreen;
import net.emberleaf.client.skin.SkinLibrary;
import net.emberleaf.client.skin.SkinsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/** Wears the HD starter set (Blockbench models) and takes screenshots from the front, the back and while walking. */
public class BlockbenchCosmeticsTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Path dir;
		try {
			dir = Files.createTempDirectory("emberleaf-bbmodel-test");
			copy("outfit-a", dir);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		System.setProperty("emberleaf.dataDir", dir.toString());

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			shoot(context, "bb-a-front", CameraType.THIRD_PERSON_FRONT, 60);
			shoot(context, "bb-a-back", CameraType.THIRD_PERSON_BACK, 20);
			// walk forward a bit: the pet follows, the cape swings, the wings flap harder
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.getInput().holdKey(o -> o.keyUp);
			context.waitTicks(25);
			context.takeScreenshot("bb-a-walking");
			context.getInput().releaseKey(o -> o.keyUp);

			try {
				copy("outfit-b", dir);
				Files.setLastModifiedTime(dir.resolve("outfit.json"), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			shoot(context, "bb-b-front", CameraType.THIRD_PERSON_FRONT, 40);
			shoot(context, "bb-b-back", CameraType.THIRD_PERSON_BACK, 20);

			// HUD widgets and the Emberleaf menu inside a world
			for (String id : new String[] {"hud_keys", "hud_clock", "hud_biome", "hud_direction", "hud_speed", "minimap"}) Modules.set(id, true);
			world.getServer().runCommand("item replace entity @a armor.head with minecraft:iron_helmet");
			world.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(20);
			context.takeScreenshot("hud-widgets");
			context.setScreen(() -> new EmberleafSettingsScreen(null));
			context.waitTicks(15);
			context.takeScreenshot("settings-ingame");
			// the skin switcher with three saved skins, then wear one (locally, no upload in tests)
			try {
				writeSkins(dir.resolve("skins"));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			context.setScreen(() -> new SkinsScreen(null));
			context.waitTicks(15);
			context.takeScreenshot("skins-ingame");
			context.setScreen(() -> null);
			context.runOnClient(mc -> SkinLibrary.wearLocally(SkinLibrary.load().get(1)));
			shoot(context, "skin-switched", CameraType.THIRD_PERSON_FRONT, 20);
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				SkinLibrary.clearOverride();
			});
			for (String id : new String[] {"hud_keys", "hud_clock", "hud_biome", "hud_direction", "hud_speed", "minimap"}) Modules.set(id, false);
		}
	}

	private static void shoot(ClientGameTestContext context, String name, CameraType camera, int ticks) {
		context.runOnClient(mc -> mc.options.setCameraType(camera));
		for (int i = 0; i < ticks; i++) {
			context.runOnClient(mc -> {
				mc.player.setYRot(180.0F);
				mc.player.setYHeadRot(180.0F);
				mc.player.setYBodyRot(180.0F);
				mc.player.setXRot(10.0F);
			});
			context.waitTick();
		}
		context.takeScreenshot(name);
	}

	/** Three simple 64x64 test skins (shirt, trousers, hair colours) plus the launcher's library.json. */
	private static void writeSkins(Path folder) throws IOException {
		Files.createDirectories(folder);
		String[][] skins = {{"a1b2c3d4e5f6", "Herbst-Ich", "default", "e8742f", "4a3a2a", "6b3a1a"},
			{"b2c3d4e5f6a1", "Waldläuferin", "slim", "5f9a4a", "2e3b24", "f2c14e"},
			{"c3d4e5f6a1b2", "Nachtwanderer", "default", "3a5a9a", "22223a", "1a1a1a"}};
		StringBuilder json = new StringBuilder("[");
		for (String[] s : skins) {
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = img.createGraphics();
			int skin = 0xFFE0B48C;
			int shirt = 0xFF000000 | Integer.parseInt(s[3], 16);
			int pants = 0xFF000000 | Integer.parseInt(s[4], 16);
			int hair = 0xFF000000 | Integer.parseInt(s[5], 16);
			fill(g, skin, 0, 0, 32, 16); // head
			fill(g, hair, 8, 0, 8, 8); // top of the head
			fill(g, hair, 0, 8, 32, 3); // fringe all around
			fill(g, 0xFF2A1A10, 10, 12, 1, 1);
			fill(g, 0xFF2A1A10, 13, 12, 1, 1);
			fill(g, shirt, 16, 16, 24, 16); // body
			fill(g, shirt, 40, 16, 16, 6); // right arm sleeve
			fill(g, skin, 40, 22, 16, 10);
			fill(g, shirt, 32, 48, 16, 6); // left arm sleeve
			fill(g, skin, 32, 54, 16, 10);
			fill(g, pants, 0, 16, 16, 16); // right leg
			fill(g, pants, 16, 48, 16, 16); // left leg
			fill(g, 0xFF3A2A20, 0, 28, 16, 4);
			fill(g, 0xFF3A2A20, 16, 60, 16, 4);
			if (s[2].equals("slim")) {
				g.setComposite(java.awt.AlphaComposite.Clear);
				g.fillRect(54, 20, 2, 12);
				g.fillRect(46, 52, 2, 12);
			}
			g.dispose();
			javax.imageio.ImageIO.write(img, "png", folder.resolve(s[0] + ".png").toFile());
			if (json.length() > 1) json.append(',');
			json.append("{\"id\":\"").append(s[0]).append("\",\"name\":\"").append(s[1]).append("\",\"model\":\"").append(s[2]).append("\"}");
		}
		Files.writeString(folder.resolve("library.json"), json.append(']').toString());
	}

	private static void fill(java.awt.Graphics2D g, int argb, int x, int y, int w, int h) {
		g.setColor(new java.awt.Color(argb, true));
		g.fillRect(x, y, w, h);
	}

	private static void copy(String name, Path dir) throws IOException {
		try (InputStream in = BlockbenchCosmeticsTest.class.getResourceAsStream("/emberleaf-test/" + name + ".json")) {
			if (in == null) throw new IOException("missing test outfit " + name);
			Files.copy(in, dir.resolve("outfit.json"), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
