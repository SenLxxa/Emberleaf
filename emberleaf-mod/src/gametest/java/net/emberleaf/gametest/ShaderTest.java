package net.emberleaf.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;

/**
 * Turns on the Emberleaf Glut shader in Iris (only present in the test client) and photographs a small scene
 * with water, lava, a tree, flowers and torches at noon, sunset and night.
 */
public class ShaderTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			System.out.println("[Emberleaf] Iris fehlt im Test-Client, Shader-Test übersprungen");
			return;
		}
		String pack = context.computeOnClient(mc -> {
			Path assets = mc.gameDirectory.toPath().toAbsolutePath().getParent().getParent().resolve("test-assets/shaderpacks");
			try (Stream<Path> files = Files.isDirectory(assets) ? Files.list(assets) : Stream.empty()) {
				Path src = files.filter(p -> p.getFileName().toString().startsWith("Emberleaf-Glut-")).findFirst().orElse(null);
				if (src == null) return null;
				Path dir = mc.gameDirectory.toPath().resolve("shaderpacks");
				Files.createDirectories(dir);
				Files.copy(src, dir.resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				return src.getFileName().toString();
			} catch (IOException e) {
				throw new AssertionError("Shader konnte nicht kopiert werden", e);
			}
		});
		if (pack == null) {
			System.out.println("[Emberleaf] Kein Emberleaf-Glut in test-assets, Shader-Test übersprungen");
			return;
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("execute at @p run fill ~-10 ~-1 ~-14 ~10 ~-1 ~-1 minecraft:grass_block");
			server.runCommand("execute at @p run fill ~-8 ~-1 ~-10 ~-2 ~-1 ~-5 minecraft:water");
			server.runCommand("execute at @p run fill ~4 ~-1 ~-9 ~6 ~-1 ~-7 minecraft:lava");
			server.runCommand("execute at @p run fill ~1 ~ ~-12 ~1 ~4 ~-12 minecraft:oak_log");
			server.runCommand("execute at @p run fill ~-1 ~3 ~-14 ~3 ~6 ~-10 minecraft:oak_leaves[persistent=true] keep");
			server.runCommand("execute at @p run fill ~-6 ~ ~-13 ~-3 ~2 ~-13 minecraft:stone_bricks");
			server.runCommand("execute at @p run setblock ~-4 ~1 ~-12 minecraft:wall_torch[facing=south]");
			server.runCommand("execute at @p run setblock ~3 ~ ~-6 minecraft:lantern");
			for (int x = -9; x <= 9; x += 2) {
				server.runCommand("execute at @p run setblock ~" + x + " ~ ~-3 minecraft:" + (x % 4 == 1 ? "poppy" : "short_grass"));
			}
			server.runCommand("execute at @p run summon pig ~-1 ~ ~-4 {NoAI:1b,Rotation:[150f,0f]}");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// switch Iris to our pack (internal API, reached by reflection so the mod does not depend on Iris)
			context.runOnClient(mc -> {
				try {
					Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
					Object config = iris.getMethod("getIrisConfig").invoke(null);
					config.getClass().getMethod("setShaderPackName", String.class).invoke(config, pack);
					config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
					config.getClass().getMethod("save").invoke(config);
					iris.getMethod("reload").invoke(null);
				} catch (ReflectiveOperationException e) {
					throw new AssertionError("Iris konnte den Shader nicht laden", e);
				}
			});
			context.waitTicks(40);
			String[][] times = {{"6000", "shader-noon"}, {"12600", "shader-sunset"}, {"18000", "shader-night"}};
			for (String[] t : times) {
				server.runCommand("time set " + t[0]);
				for (int i = 0; i < 30; i++) {
					context.runOnClient(mc -> {
						mc.player.setYRot(180.0F);
						mc.player.setXRot(t[1].equals("shader-night") ? -18.0F : 6.0F);
					});
					context.waitTick();
				}
				context.takeScreenshot(t[1]);
			}
			context.runOnClient(mc -> {
				try {
					Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
					Object config = iris.getMethod("getIrisConfig").invoke(null);
					config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, false);
					iris.getMethod("reload").invoke(null);
				} catch (ReflectiveOperationException e) {
					throw new AssertionError(e);
				}
			});
		}
	}
}
