package net.emberleaf.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.emberleaf.client.friends.FriendsSync;
import net.emberleaf.client.menu.EmberleafTitleScreen;
import net.emberleaf.client.outfit.OutfitStore;
import net.emberleaf.client.outfit.Outfits;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Pictures for the Emberleaf website in 1920x1080 without HUD: the main menu scenes, the cosmetics from the launcher's
 * catalog (tools/make_site_outfits.py → build/test-assets/outfits), a group of friends and landscapes, all with the
 * Emberleaf texture pack and the Glut shader. Only runs with EMBERLEAF_TEST_ONLY=website; the pictures land in
 * build/run/clientGameTest/screenshots as site-*.png (emberleaf-server/tools/make_site_assets.py picks them up).
 */
public class WebsiteShotsTest implements FabricClientGameTest {
	private static final int W = 1920;
	private static final int H = 1080;
	private static final String[] LOOKS = {"eternal", "herbst", "drache", "frost", "prisma", "leere"};

	private Path data;
	private Path outfits;
	private final List<UUID> friends = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"website".equals(System.getenv("EMBERLEAF_TEST_ONLY"))) return;
		Path assets = context.computeOnClient(mc -> mc.gameDirectory.toPath().toAbsolutePath().getParent().getParent().resolve("test-assets"));
		outfits = assets.resolve("outfits");
		if (!Files.isDirectory(outfits)) throw new AssertionError("Erst tools/make_site_outfits.py laufen lassen: " + outfits + " fehlt");

		// own data folder with a stand-in Lagerfeuer, so the menu shows friends; the outfit lives there too
		context.runOnClient(mc -> io(() -> {
			data = mc.gameDirectory.toPath().toAbsolutePath().resolve("emberleaf-site-data");
			Files.createDirectories(data);
			String me = mc.getUser().getProfileId().toString().replace("-", "");
			MockLagerfeuer lagerfeuer = new MockLagerfeuer(me);
			Files.writeString(data.resolve("friends.json"), "{\"server\": \"" + lagerfeuer.url() + "\"}");
			Files.writeString(data.resolve("friends-session.json"), "{\"server\": \"" + lagerfeuer.url() + "\", \"token\": \"test\", \"uuid\": \""
				+ me + "\", \"name\": \"Player0\", \"expires\": 4102444800}");
			Files.writeString(data.resolve("playtime.json"), "{\"total\": 39600, \"sessions\": 12, \"days\": {}, \"earned\": [\"alpha\"]}");
			System.setProperty("emberleaf.dataDir", data.toString());
			FriendsSync.reloadConfig();
			return null;
		}));
		wear(context, "eternal");
		texturePack(context, assets);
		boolean shader = shader(context, assets);
		resize(context, W, H);

		menus(context);
		hud(context, false);
		world(context, shader);
		hud(context, true);
		resize(context, 854, 480);
	}

	// ---------- Hauptmenü ----------

	private void menus(ClientGameTestContext context) {
		context.waitFor(mc -> mc.gui.screen() instanceof EmberleafTitleScreen, 1200);
		System.setProperty("emberleaf.menuPhase", "sunset");
		for (String scene : new String[] {"island", "overworld", "cave", "nether", "end"}) {
			System.setProperty("emberleaf.menuScene", scene);
			context.waitTicks(90);
			shot(context, "menu-" + scene);
		}
		System.clearProperty("emberleaf.menuPhase");
		System.clearProperty("emberleaf.menuScene");
	}

	// ---------- Welt: Cosmetics, Freunde, Landschaften ----------

	private void world(ClientGameTestContext context, boolean shader) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(s -> s.setSeed("emberleaf")).create()) {
			world.getConnection().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @p");
			// a small meadow to stand on: free view to the west (the sun) and to the east (the camera)
			server.runCommand("execute at @p run fill ~-7 ~-1 ~-7 ~7 ~-1 ~7 minecraft:grass_block");
			server.runCommand("execute at @p run fill ~-7 ~ ~-7 ~7 ~8 ~7 minecraft:air");
			String[] flowers = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "short_grass", "allium", "short_grass", "red_tulip"};
			int f = 0;
			for (int x = -6; x <= 6; x += 2) {
				for (int z = -6; z <= 6; z += 3) {
					if (Math.abs(x) <= 2 && Math.abs(z) <= 4) continue; // the players stand here
					server.runCommand("execute at @p run setblock ~" + x + " ~ ~" + z + " minecraft:" + flowers[f++ % flowers.length]);
				}
			}
			server.runCommand("execute at @p run setblock ~-5 ~ ~5 minecraft:campfire");
			server.runCommand("execute at @p run setblock ~-5 ~ ~-5 minecraft:lantern");
			server.runCommand("time set 10800");
			world.getConnection().waitForChunksRender();

			// every look from the front (the camera looks west into the evening sun) and from behind
			for (String look : LOOKS) {
				wear(context, look);
				pose(context, CameraType.THIRD_PERSON_FRONT, -90F, 8F, 50);
				shot(context, "look-" + look + "-front");
				pose(context, CameraType.THIRD_PERSON_BACK, -120F, 10F, 25);
				shot(context, "look-" + look + "-back");
			}

			// a group of Emberleaf friends, everyone sees everyone's cosmetics
			wear(context, "eternal");
			friend(context, "herbst", "Funke", 0.0, 1.7);
			friend(context, "drache", "Glut", -0.6, -1.7);
			friend(context, "frost", "Asche", -1.2, 3.3);
			friend(context, "prisma", "Kohle", -1.2, -3.3);
			pose(context, CameraType.THIRD_PERSON_FRONT, -90F, 6F, 60);
			shot(context, "group");
			context.runOnClient(mc -> {
				for (UUID id : friends) {
					var p = mc.level.getPlayerByUUID(id);
					if (p != null) p.discard();
					Outfits.removeRemote(id);
				}
			});
			friends.clear();

			// landscapes from above, morning to night
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute at @p run tp @p ~ ~22 ~");
			Object[][] views = {
				{"12600", -100F, 6F, "bg-sunset"}, {"5000", 200F, 18F, "bg-day"}, {"23200", 80F, 4F, "bg-morning"},
				{"18000", 20F, -12F, "bg-night"}, {"12000", 150F, 24F, "bg-valley"},
			};
			for (Object[] v : views) {
				server.runCommand("time set " + v[0]);
				for (int i = 0; i < 40; i++) {
					context.runOnClient(mc -> {
						mc.player.setYRot((float) v[1]);
						mc.player.setXRot((float) v[2]);
					});
					context.waitTick();
				}
				world.getConnection().waitForChunksRender();
				context.waitTicks(10);
				shot(context, (String) v[3]);
			}
			if (shader) shaderOff(context);
		}
	}

	private void pose(ClientGameTestContext context, CameraType camera, float yaw, float pitch, int ticks) {
		context.runOnClient(mc -> mc.options.setCameraType(camera));
		for (int i = 0; i < ticks; i++) {
			context.runOnClient(mc -> {
				mc.player.setYRot(yaw);
				mc.player.setYHeadRot(yaw);
				mc.player.setYBodyRot(yaw);
				mc.player.setXRot(pitch);
			});
			context.waitTick();
		}
	}

	/** Puts a look from build/test-assets/outfits on the local player. */
	private void wear(ClientGameTestContext context, String look) {
		try {
			Path target = data.resolve("outfit.json");
			Files.copy(outfits.resolve(look + ".json"), target, StandardCopyOption.REPLACE_EXISTING);
			Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis() + 5000));
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		context.runOnClient(mc -> OutfitStore.reloadIfChanged());
		context.waitTicks(5);
	}

	/** Another Emberleaf player next to you (east is where the camera is), wearing a look. */
	private void friend(ClientGameTestContext context, String look, String name, double dx, double dz) {
		UUID id = UUID.nameUUIDFromBytes(("emberleaf-site-" + name).getBytes(StandardCharsets.UTF_8));
		friends.add(id);
		JsonObject root = io(() -> JsonParser.parseString(Files.readString(outfits.resolve(look + ".json"), StandardCharsets.UTF_8)).getAsJsonObject());
		context.runOnClient(mc -> {
			Outfits.putRemote(id, OutfitStore.parse(root.getAsJsonObject("items")));
			var player = new net.minecraft.client.player.RemotePlayer(mc.level, new com.mojang.authlib.GameProfile(id, name));
			player.setId(910_000 + friends.size());
			player.snapTo(mc.player.getX() + dx, mc.player.getY(), mc.player.getZ() + dz, -90F, 5F);
			player.setYHeadRot(-90F);
			player.setYBodyRot(-90F);
			mc.level.addEntity(player);
		});
	}

	// ---------- Texturenpaket, Shader, Fenstergröße ----------

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of("site-" + name).withSize(W, H).disableCounterPrefix());
	}

	/** The whole run in website size: the GUI scale follows, so the menus look like on a big screen. */
	private static void resize(ClientGameTestContext context, int width, int height) {
		context.runOnClient(mc -> {
			mc.getWindow().setWidth(width);
			mc.getWindow().setHeight(height);
			mc.gameRenderer.resize(width, height);
			mc.resizeGui();
		});
		context.waitTicks(5);
	}

	/** F1: pictures of the world without hotbar and hearts. */
	private static void hud(ClientGameTestContext context, boolean visible) {
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() == visible) mc.gui.hud.toggle();
		});
	}

	private static void texturePack(ClientGameTestContext context, Path assets) {
		Path src = find(assets.resolve("resourcepacks"), "Emberleaf-Texturen-");
		if (src == null) {
			System.out.println("[Emberleaf] Kein Texturenpaket in test-assets, Website-Bilder ohne");
			return;
		}
		CompletableFuture<Void> reload = context.computeOnClient(mc -> {
			Path dir = mc.getResourcePackDirectory();
			io(() -> Files.copy(src, Files.createDirectories(dir).resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING));
			PackRepository repo = mc.getResourcePackRepository();
			repo.reload();
			String id = "file/" + src.getFileName();
			if (!repo.isAvailable(id)) throw new AssertionError("Emberleaf-Texturenpaket wurde nicht erkannt: " + id);
			List<String> selected = new ArrayList<>(repo.getSelectedIds());
			selected.add(id);
			repo.setSelected(selected);
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone() && mc.gui.overlay() == null, 2400);
	}

	private static boolean shader(ClientGameTestContext context, Path assets) {
		if (!FabricLoader.getInstance().isModLoaded("iris")) return false;
		Path src = find(assets.resolve("shaderpacks"), "Emberleaf-Glut-");
		if (src == null) return false;
		context.runOnClient(mc -> {
			Path dir = mc.gameDirectory.toPath().resolve("shaderpacks");
			io(() -> Files.copy(src, Files.createDirectories(dir).resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING));
			irisSet(src.getFileName().toString(), true);
		});
		return true;
	}

	private static Path find(Path folder, String prefix) {
		if (!Files.isDirectory(folder)) return null;
		return io(() -> {
			try (Stream<Path> files = Files.list(folder)) {
				return files.filter(p -> p.getFileName().toString().startsWith(prefix)).findFirst().orElse(null);
			}
		});
	}

	private interface IoAction<T> {
		T run() throws IOException;
	}

	private static <T> T io(IoAction<T> action) {
		try {
			return action.run();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void shaderOff(ClientGameTestContext context) {
		context.runOnClient(mc -> irisSet(null, false));
	}

	/** Iris is only in the test client: reached by reflection so the mod does not depend on it. */
	private static void irisSet(String pack, boolean on) {
		try {
			Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
			Object config = iris.getMethod("getIrisConfig").invoke(null);
			if (pack != null) config.getClass().getMethod("setShaderPackName", String.class).invoke(config, pack);
			config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, on);
			config.getClass().getMethod("save").invoke(config);
			iris.getMethod("reload").invoke(null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Iris konnte den Shader nicht umschalten", e);
		}
	}
}
