package net.emberleaf.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Turns on the assembled "Emberleaf Texturen" pack (if the test run has one in test-assets) and photographs it:
 * plants, beds, ladders, torches, lanterns, glass, bushy leaves, 3D tools and armour, zombies, glowing ores, sun and moon.
 */
public class TexturePackTest implements FabricClientGameTest {
	private static final String[] WALL = {
		"stone", "cobblestone", "mossy_cobblestone", "stone_bricks", "bricks", "oak_planks", "spruce_planks", "birch_planks",
		"oak_log", "birch_log", "dark_oak_log", "cherry_planks", "deepslate", "cobbled_deepslate", "sandstone", "red_sandstone",
		"coal_ore", "iron_ore", "copper_ore", "gold_ore", "redstone_ore", "lapis_ore", "diamond_ore", "emerald_ore",
		"crafting_table", "furnace", "bookshelf", "pumpkin", "tnt", "glowstone", "hay_block", "diamond_block",
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Only.skip("texturepack")) return;
		// quick runs while working on the menu: EMBERLEAF_TEST_MENU_ONLY=1 skips the worlds, EMBERLEAF_MESH_STATS=1 logs heavy blocks
		if (System.getenv("EMBERLEAF_TEST_MENU_ONLY") != null) System.setProperty("emberleaf.test.menuOnly", "true");
		if (System.getenv("EMBERLEAF_MESH_STATS") != null) System.setProperty("emberleaf.meshStats", "true");
		// never touch the real Emberleaf settings of this PC: the test keeps its own data folder in the run folder
		context.runOnClient(mc -> {
			Path data = mc.gameDirectory.toPath().toAbsolutePath().resolve("emberleaf-test-data");
			try {
				Files.createDirectories(data);
			} catch (IOException e) {
				throw new AssertionError(e);
			}
			System.setProperty("emberleaf.dataDir", data.toString());
		});
		CompletableFuture<Void> reload = context.computeOnClient(mc -> {
			// the run folder is wiped before every test run, so the pack waits next to it in build/test-assets
			Path dir = mc.getResourcePackDirectory();
			Path assets = mc.gameDirectory.toPath().toAbsolutePath().getParent().getParent().resolve("test-assets/resourcepacks");
			String file;
			try (Stream<Path> files = Files.isDirectory(assets) ? Files.list(assets) : Stream.empty()) {
				Path src = files.filter(p -> p.getFileName().toString().startsWith("Emberleaf-Texturen-")).findFirst().orElse(null);
				file = src == null ? null : src.getFileName().toString();
				if (src != null) {
					Files.createDirectories(dir);
					Files.copy(src, dir.resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				throw new AssertionError("Texturenpaket konnte nicht kopiert werden", e);
			}
			if (file == null) return null;
			PackRepository repo = mc.getResourcePackRepository();
			repo.reload();
			String id = "file/" + file;
			if (!repo.isAvailable(id)) throw new AssertionError("Emberleaf-Texturenpaket wurde nicht erkannt: " + id);
			List<String> selected = new ArrayList<>(repo.getSelectedIds());
			selected.add(id);
			repo.setSelected(selected);
			return mc.reloadResourcePacks();
		});
		if (reload == null) {
			System.out.println("[Emberleaf] Kein Emberleaf-Texturenpaket in test-assets, Texturen-Test übersprungen");
			return;
		}
		context.waitFor(mc -> reload.isDone() && mc.gui.overlay() == null, 2400);
		context.runOnClient(mc -> {
			if (reload.isCompletedExceptionally()) throw new AssertionError("Neuladen mit dem Texturenpaket ist fehlgeschlagen");
		});
		if (!Boolean.getBoolean("emberleaf.test.menuOnly")) worlds(context);
		menus(context);
	}

	private void worlds(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("time set 6000");
			server.runCommand("gamerule advance_time false");
			server.runCommand("execute at @p run fill ~-9 ~-1 ~-12 ~9 ~-1 ~-1 minecraft:grass_block");
			for (int i = 0; i < WALL.length; i++) {
				int x = (i % 16) - 8;
				int y = 2 - i / 16;
				server.runCommand("execute at @p run setblock ~" + x + " ~" + y + " ~-9 minecraft:" + WALL[i]);
			}
			server.runCommand("execute at @p run fill ~-8 ~ ~-9 ~7 ~ ~-9 minecraft:oak_leaves[persistent=true]");
			server.runCommand("execute at @p run fill ~-7 ~-1 ~-6 ~-4 ~-1 ~-4 minecraft:water");
			server.runCommand("execute at @p run fill ~4 ~-1 ~-6 ~7 ~-1 ~-4 minecraft:lava");
			// plants, beds, ladders, torches, lanterns, bars and glass from the pack
			String[] plants = {"poppy", "dandelion", "cornflower", "allium", "azure_bluet", "oxeye_daisy", "red_tulip", "lily_of_the_valley"};
			for (int i = 0; i < plants.length; i++) server.runCommand("execute at @p run setblock ~" + (i - 4) + " ~ ~-3 minecraft:" + plants[i]);
			server.runCommand("execute at @p run setblock ~5 ~ ~-3 minecraft:wheat[age=7]");
			server.runCommand("execute at @p run setblock ~6 ~ ~-3 minecraft:sweet_berry_bush[age=3]");
			server.runCommand("execute at @p run setblock ~7 ~ ~-3 minecraft:short_grass");
			String[] beds = {"orange", "red", "yellow", "green", "light_blue", "purple", "white"};
			for (int i = 0; i < beds.length; i++) {
				int x = -8 + i * 2;
				server.runCommand("execute at @p run setblock ~" + x + " ~ ~-6 minecraft:" + beds[i] + "_bed[facing=south,part=head]");
				server.runCommand("execute at @p run setblock ~" + x + " ~ ~-5 minecraft:" + beds[i] + "_bed[facing=south,part=foot]");
			}
			server.runCommand("execute at @p run fill ~6 ~ ~-8 ~8 ~2 ~-8 minecraft:spruce_planks");
			server.runCommand("execute at @p run fill ~7 ~ ~-7 ~7 ~2 ~-7 minecraft:ladder[facing=south]");
			server.runCommand("execute at @p run setblock ~6 ~1 ~-7 minecraft:wall_torch[facing=south]");
			server.runCommand("execute at @p run setblock ~8 ~1 ~-7 minecraft:wall_torch[facing=south]");
			server.runCommand("execute at @p run setblock ~3 ~ ~-6 minecraft:torch");
			server.runCommand("execute at @p run setblock ~4 ~ ~-6 minecraft:lantern");
			server.runCommand("execute at @p run fill ~-9 ~ ~-7 ~-9 ~1 ~-4 minecraft:glass");
			server.runCommand("execute at @p run fill ~9 ~ ~-6 ~9 ~1 ~-4 minecraft:iron_bars");
			// a bushy tree, a hanging lantern
			server.runCommand("execute at @p run fill ~-6 ~ ~-10 ~-6 ~3 ~-10 minecraft:oak_log");
			server.runCommand("execute at @p run fill ~-8 ~3 ~-12 ~-4 ~4 ~-8 minecraft:oak_leaves[persistent=true] keep");
			server.runCommand("execute at @p run fill ~-7 ~5 ~-11 ~-5 ~5 ~-9 minecraft:oak_leaves[persistent=true] keep");
			server.runCommand("execute at @p run setblock ~0 ~3 ~-7 minecraft:spruce_planks");
			server.runCommand("execute at @p run setblock ~0 ~2 ~-7 minecraft:lantern[hanging=true]");
			String[] hotbar = {
				"diamond_sword", "netherite_pickaxe", "bow",
				"enchanted_book[stored_enchantments={\"minecraft:sharpness\":5}]",
				"enchanted_book[stored_enchantments={\"minecraft:mending\":1}]",
				"enchanted_book[stored_enchantments={\"minecraft:protection\":4}]",
				"elytra[custom_name='Citrus']", "diamond_axe[enchantments={\"minecraft:efficiency\":5}]", "totem_of_undying",
			};
			for (int i = 0; i < hotbar.length; i++) server.runCommand("item replace entity @p hotbar." + i + " with minecraft:" + hotbar[i]);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			for (int i = 0; i < 40; i++) {
				context.runOnClient(mc -> {
					mc.player.setYRot(180.0F);
					mc.player.setXRot(14.0F);
				});
				context.waitTick();
			}
			context.takeScreenshot("pack-showcase");
			// 3D armour and tools in third person, zombies with their own looks
			server.runCommand("item replace entity @p armor.head with minecraft:diamond_helmet");
			server.runCommand("item replace entity @p armor.chest with minecraft:netherite_chestplate");
			server.runCommand("item replace entity @p armor.legs with minecraft:iron_leggings");
			server.runCommand("item replace entity @p armor.feet with minecraft:golden_boots");
			for (int i = 0; i < 6; i++) server.runCommand("execute at @p run summon zombie ~" + (i * 2 - 5) + " ~ ~4 {NoAI:1b,Rotation:[0f,0f]}");
			for (int i = 0; i < 30; i++) {
				context.runOnClient(mc -> {
					mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
					mc.player.setYRot(0.0F);
					mc.player.setYBodyRot(0.0F);
					mc.player.setYHeadRot(0.0F);
					mc.player.setXRot(10.0F);
				});
				context.waitTick();
			}
			context.takeScreenshot("pack-armor");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			for (int i = 0; i < 20; i++) {
				context.runOnClient(mc -> {
					mc.player.setYRot(0.0F);
					mc.player.setXRot(6.0F);
				});
				context.waitTick();
			}
			context.takeScreenshot("pack-zombies");
			// glowing ores at night
			server.runCommand("time set 18000");
			for (int i = 0; i < 30; i++) {
				context.runOnClient(mc -> {
					mc.player.setYRot(180.0F);
					mc.player.setXRot(2.0F);
				});
				context.waitTick();
			}
			context.takeScreenshot("pack-night");
			server.runCommand("time set 6000");
			// the cube sun at sunset (west) and the cube moon at night (straight up)
			server.runCommand("time set 12000");
			for (int i = 0; i < 20; i++) {
				context.runOnClient(mc -> {
					mc.player.setYRot(90.0F);
					mc.player.setXRot(-12.0F);
				});
				context.waitTick();
			}
			context.takeScreenshot("pack-sun");
			server.runCommand("time set 18000");
			for (int i = 0; i < 20; i++) {
				context.runOnClient(mc -> mc.player.setXRot(-90.0F));
				context.waitTick();
			}
			context.takeScreenshot("pack-moon");
			server.runCommand("time set 6000");
			context.runOnClient(mc -> {
				mc.player.setYRot(180.0F);
				mc.player.setXRot(8.0F);
			});
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(10);
			context.takeScreenshot("pack-inventory");
			context.setScreen(() -> null);

			// renamed elytras (Elytras+ rules, done by the Emberleaf client) on armor stands, seen from behind
			server.runCommand("kill @e[type=zombie]");
			String[] names = {"Citrus", "Gapple", "Ivan", "Hunger"};
			for (int i = 0; i < names.length; i++) {
				server.runCommand("execute at @p run summon armor_stand ~" + (i * 2 - 3) + " ~ ~3 {Rotation:[0f,0f],equipment:{chest:{id:\"minecraft:elytra\","
					+ "components:{\"minecraft:custom_name\":\"" + names[i] + "\"}}}}");
			}
			server.runCommand("execute at @p run summon armor_stand ~5 ~ ~3 {Rotation:[0f,0f],equipment:{chest:{id:\"minecraft:elytra\"}}}");
			for (int i = 0; i < 20; i++) {
				context.runOnClient(mc -> {
					mc.player.setYRot(0.0F);
					mc.player.setXRot(16.0F);
				});
				context.waitTick();
			}
			context.takeScreenshot("pack-elytras");
			server.runCommand("kill @e[type=armor_stand]");

			// minimap with mob pictures (Icon Fresh): a few animals and monsters around the player
			context.runOnClient(mc -> {
				net.emberleaf.client.config.Modules.set("minimap", true);
				net.emberleaf.client.config.Modules.set("minimap_mobs", true);
				net.emberleaf.client.config.Modules.setValue("minimap", 1);
			});
			String[] mobs = {"cow", "pig", "sheep", "chicken", "creeper", "skeleton", "spider", "enderman", "wolf", "fox", "villager", "witch"};
			for (int i = 0; i < mobs.length; i++) {
				double a = i * Math.PI * 2 / mobs.length;
				int mx = (int) Math.round(Math.cos(a) * (6 + i % 3 * 5));
				int mz = (int) Math.round(Math.sin(a) * (6 + i % 3 * 5));
				server.runCommand("execute at @p run summon " + mobs[i] + " ~" + mx + " ~ ~" + mz + " {NoAI:1b,PersistenceRequired:1b}");
			}
			for (int i = 0; i < 40; i++) {
				context.runOnClient(mc -> {
					mc.player.setYRot(180.0F);
					mc.player.setXRot(20.0F);
				});
				context.waitTick();
			}
			context.takeScreenshot("pack-minimap");
			context.runOnClient(mc -> net.emberleaf.client.config.Modules.set("minimap", false));
			server.runCommand("kill @e[type=!player]");

			// a sealed, pitch-black room: glowing ores and glowing armor trims must still shine
			server.runCommand("time set 18000");
			server.runCommand("execute at @p run fill ~-6 ~40 ~-2 ~6 ~46 ~10 minecraft:deepslate_tiles hollow");
			String[] ores = {
				"coal_ore", "iron_ore", "copper_ore", "gold_ore", "redstone_ore", "lapis_ore", "diamond_ore", "emerald_ore",
				"deepslate_coal_ore", "deepslate_iron_ore", "deepslate_copper_ore", "deepslate_gold_ore", "deepslate_redstone_ore",
				"deepslate_lapis_ore", "deepslate_diamond_ore", "deepslate_emerald_ore", "nether_gold_ore", "nether_quartz_ore", "ancient_debris",
			};
			for (int i = 0; i < ores.length; i++) {
				int x = (i % 10) - 5;
				int y = 44 - i / 10;
				server.runCommand("execute at @p run setblock ~" + x + " ~" + y + " ~10 minecraft:" + ores[i]);
			}
			String[][] trims = {{"diamond", "gold", "sentry"}, {"netherite", "diamond", "rib"}, {"iron", "emerald", "wild"}, {"golden", "redstone", "silence"}};
			for (int i = 0; i < trims.length; i++) {
				String[] t = trims[i];
				String trim = "\"minecraft:trim\":{material:\"minecraft:" + t[1] + "\",pattern:\"minecraft:" + t[2] + "\"}";
				server.runCommand("execute at @p run summon armor_stand ~" + (i * 2 - 3) + " ~41 ~6 {Rotation:[180f,0f],equipment:{"
					+ "head:{id:\"minecraft:" + t[0] + "_helmet\",components:{" + trim + "}},"
					+ "chest:{id:\"minecraft:" + t[0] + "_chestplate\",components:{" + trim + "}},"
					+ "legs:{id:\"minecraft:" + t[0] + "_leggings\",components:{" + trim + "}},"
					+ "feet:{id:\"minecraft:" + t[0] + "_boots\",components:{" + trim + "}}}}");
			}
			server.runCommand("execute at @p run tp @p ~ ~41 ~ 0 8");
			for (int i = 0; i < 40; i++) {
				context.runOnClient(mc -> {
					mc.player.setYRot(0.0F);
					mc.player.setXRot(8.0F);
				});
				context.waitTick();
			}
			context.takeScreenshot("pack-dark-room");
			context.runOnClient(mc -> net.emberleaf.client.config.Modules.set("glowing_trims", false));
			context.waitTicks(5);
			context.takeScreenshot("pack-dark-room-noglow");
			context.runOnClient(mc -> net.emberleaf.client.config.Modules.set("glowing_trims", true));
		}

		// a real generated world (fixed seed): forests, hills, water, the bushy canopies from above
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(s -> s.setSeed("emberleaf")).create()) {
			world.getConnection().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("time set 5000");
			server.runCommand("gamerule advance_time false");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute at @p run tp @p ~ ~14 ~");
			float[][] views = {{200F, 22F}, {20F, 18F}, {110F, 30F}};
			for (int v = 0; v < views.length; v++) {
				float[] view = views[v];
				for (int i = 0; i < 50; i++) {
					context.runOnClient(mc -> {
						mc.player.setYRot(view[0]);
						mc.player.setXRot(view[1]);
					});
					context.waitTick();
				}
				world.getConnection().waitForChunksRender();
				context.takeScreenshot("pack-world-" + (v + 1));
			}
		}
	}

	/** The main menu is built from the game's own blocks: with the pack on it must show the pack's textures and models. */
	private void menus(ClientGameTestContext context) {
		context.waitFor(mc -> mc.gui.screen() instanceof net.emberleaf.client.menu.EmberleafTitleScreen, 600);
		System.setProperty("emberleaf.menuPhase", "sunset");
		for (String scene : new String[] {"overworld", "cave", "nether", "end", "island"}) {
			System.setProperty("emberleaf.menuScene", scene);
			context.waitTicks(80);
			context.runOnClient(mc -> System.out.println("[Emberleaf] Menü " + scene + " mit Texturenpaket: " + mc.getFps() + " FPS"));
			context.takeScreenshot("pack-menu-" + scene);
		}
		System.clearProperty("emberleaf.menuPhase");
		System.clearProperty("emberleaf.menuScene");
	}
}