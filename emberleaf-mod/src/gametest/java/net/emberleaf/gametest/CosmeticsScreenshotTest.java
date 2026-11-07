package net.emberleaf.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;
import net.emberleaf.client.feature.Emotes;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Puts a test outfit on the player in a flat world and takes screenshots from the front and the back. */
public class CosmeticsScreenshotTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Path dir = writeTestOutfit();
		System.setProperty("emberleaf.dataDir", dir.toString());

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setXRot(10.0F);
			});
			for (int i = 0; i < 60; i++) { // outfit reload + aura particles; body must face the camera too
				context.runOnClient(mc -> {
					mc.player.setYRot(180.0F);
					mc.player.setYHeadRot(180.0F);
					mc.player.setYBodyRot(180.0F);
				});
				context.waitTick();
			}
			context.takeScreenshot("emberleaf-front");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(20);
			context.takeScreenshot("emberleaf-back");

			// Skin-Totem: in both hands, in the inventory and when it saves you
			world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:totem_of_undying");
			world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			for (int i = 0; i < 20; i++) {
				context.runOnClient(mc -> {
					mc.player.setYRot(180.0F);
					mc.player.setYHeadRot(180.0F);
					mc.player.setYBodyRot(180.0F);
				});
				context.waitTick();
			}
			context.takeScreenshot("emberleaf-totem-hands");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(10);
			context.takeScreenshot("emberleaf-totem-firstperson");
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(10);
			context.takeScreenshot("emberleaf-totem-inventory");
			context.setScreen(() -> null);
			context.runOnClient(mc -> mc.player.displayItemActivation(new ItemStack(Items.TOTEM_OF_UNDYING)));
			context.waitTicks(12);
			context.takeScreenshot("emberleaf-totem-pop");

			// Emote (cheer, equipped in the test outfit): the camera swings to the front
			context.waitTicks(40);
			context.runOnClient(Emotes::playEquipped);
			context.waitTicks(14);
			context.takeScreenshot("emberleaf-emote-cheer");
		}
	}

	private static Path writeTestOutfit() {
		try {
			Path dir = Files.createTempDirectory("emberleaf-gametest");
			JsonObject items = new JsonObject();
			items.add("head", item("beanie", head()));
			items.add("body", item("scarf", scarf()));
			items.add("wings", item("leafwings", wing()));
			JsonObject pet = item("pet-fox", fox());
			pet.addProperty("ground", true);
			items.add("pet", pet);
			JsonObject cape = new JsonObject();
			cape.addProperty("id", "cape-test");
			cape.addProperty("fps", 8);
			JsonArray frames = new JsonArray();
			frames.add("data:image/png;base64," + Base64.getEncoder().encodeToString(capePng()));
			cape.add("frames", frames);
			items.add("cape", cape);
			JsonObject aura = new JsonObject();
			aura.addProperty("id", "aura-leaves");
			JsonObject def = new JsonObject();
			def.addProperty("mode", "swirl");
			def.addProperty("sprite", "leaf");
			aura.add("aura", def);
			items.add("aura", aura);
			JsonObject emote = new JsonObject();
			emote.addProperty("id", "emote-cheer");
			items.add("emote", emote);
			JsonObject root = new JsonObject();
			root.addProperty("version", 1);
			root.add("items", items);
			Files.writeString(dir.resolve("outfit.json"), root.toString(), StandardCharsets.UTF_8);
			return dir;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static JsonObject item(String id, JsonArray voxels) {
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		o.add("voxels", voxels);
		return o;
	}

	private static void add(JsonArray out, int x, int y, int z, String color) {
		JsonArray v = new JsonArray();
		v.add(x);
		v.add(y);
		v.add(z);
		v.add(color);
		out.add(v);
	}

	/** Orange beanie: band around the head (y 5..7), cap on top, cream pom-pom. */
	private static JsonArray head() {
		JsonArray out = new JsonArray();
		for (int y = 5; y <= 7; y++) {
			for (int x = -5; x <= 4; x++) {
				for (int z = -5; z <= 4; z++) {
					if (x == -5 || x == 4 || z == -5 || z == 4) add(out, x, y, z, y == 6 ? "#e8873a" : "#c4572c");
				}
			}
		}
		for (int x = -4; x <= 3; x++) for (int z = -4; z <= 3; z++) add(out, x, 8, z, "#e8873a");
		for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) add(out, x, 9, z, "#d0692f");
		add(out, -1, 10, -1, "#f4e3c3");
		add(out, 0, 10, 0, "#f4e3c3");
		add(out, -1, 10, 0, "#f4e3c3");
		add(out, 0, 10, -1, "#f4e3c3");
		// a red block on the face side (+z) to check front/back orientation
		add(out, -1, 3, 4, "#ff0000");
		return out;
	}

	private static JsonArray scarf() {
		JsonArray out = new JsonArray();
		for (int y = 4; y <= 5; y++) {
			for (int x = -5; x <= 4; x++) {
				for (int z = -3; z <= 2; z++) {
					if (x == -5 || x == 4 || z == -3 || z == 2) add(out, x, y, z, (x & 1) == 0 ? "#f4e3c3" : "#c4472c");
				}
			}
		}
		for (int y = -1; y <= 3; y++) add(out, 1, y, 3, "#c4472c");
		return out;
	}

	private static JsonArray wing() {
		JsonArray out = new JsonArray();
		for (int x = 0; x < 16; x++) {
			for (int y = -8; y < 12; y++) {
				double dx = (x - 8) / 8.0;
				double dy = (y - 2) / 10.0;
				if (dx * dx + dy * dy <= 1) add(out, x, y, 0, (x + y) % 5 == 0 ? "#a8321f" : "#f2813a");
			}
		}
		return out;
	}

	private static JsonArray fox() {
		JsonArray out = new JsonArray();
		for (int x = -2; x <= 1; x++) for (int y = 0; y <= 3; y++) for (int z = -3; z <= 1; z++) add(out, x, y, z, "#e07a2e");
		for (int x = -2; x <= 1; x++) for (int y = 4; y <= 7; y++) for (int z = -1; z <= 2; z++) add(out, x, y, z, "#e07a2e");
		add(out, -2, 6, 3, "#1f1612");
		add(out, 1, 6, 3, "#1f1612");
		for (int z = -6; z <= -4; z++) add(out, 0, 1, z, "#f4e3c3");
		return out;
	}

	private static byte[] capePng() throws IOException {
		BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				boolean outer = x >= 1 && x <= 10 && y >= 1 && y <= 16;
				int rgb = outer ? ((x + y) % 4 == 0 ? 0xFFF2A541 : 0xFF5B3A22) : 0xFF3A2416;
				img.setRGB(x, y, rgb);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}
}
