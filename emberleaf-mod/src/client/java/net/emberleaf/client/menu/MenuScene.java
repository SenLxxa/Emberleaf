package net.emberleaf.client.menu;

import java.time.LocalTime;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;

/**
 * The sky behind the Emberleaf menus: sunset (or day/night) sky, a sea of clouds below and the
 * floating 3D island (IslandRenderer) on the right, leaning gently with the mouse.
 */
public final class MenuScene {
	private static final Identifier CLOUDS = tex("clouds");
	private static final Identifier GLOW = tex("glow");
	private static final Identifier LEAVES = tex("leaves");
	private static final Identifier MOON = tex("moon");

	public enum Phase {
		DAY(0xFF5E9BD8, 0xFFF4DDB4, 0xFFFFFFFF, 0xFFFFFFFF),
		SUNSET(0xFF34285A, 0xFFF3A055, 0xFFFFC6AE, 0xFFFFE0CC),
		NIGHT(0xFF060A1C, 0xFF261F4A, 0xFF4A5288, 0xFF8088C0);

		final int skyTop;
		final int skyBottom;
		final int cloudTint;
		final int islandTint;

		Phase(int skyTop, int skyBottom, int cloudTint, int islandTint) {
			this.skyTop = skyTop;
			this.skyBottom = skyBottom;
			this.cloudTint = cloudTint;
			this.islandTint = islandTint;
		}

		/** Same times as the launcher's start page ("emberleaf.menuPhase" can force one, for tests). */
		public static Phase now() {
			String forced = System.getProperty("emberleaf.menuPhase");
			if (forced != null) {
				for (Phase p : values()) if (p.name().equalsIgnoreCase(forced)) return p;
			}
			int h = LocalTime.now().getHour();
			if (h >= 21 || h < 6) return NIGHT;
			if (h >= 17) return SUNSET;
			return DAY;
		}
	}

	private static final float[][] STARS = new float[150][4];
	private static final float[][] FALLING = new float[24][6];

	static {
		Random r = new Random(1611);
		for (float[] s : STARS) {
			s[0] = r.nextFloat();
			s[1] = (float) Math.pow(r.nextFloat(), 1.4) * 0.6F;
			s[2] = 0.5F + r.nextFloat() * 3;
			s[3] = r.nextFloat() * 10;
		}
		for (float[] l : FALLING) {
			l[0] = r.nextFloat();
			l[1] = 0.04F + r.nextFloat() * 0.05F;
			l[2] = r.nextFloat() * 100;
			l[3] = r.nextInt(4);
			l[4] = r.nextInt(4);
			l[5] = (r.nextBoolean() ? 1 : -1) * (0.6F + r.nextFloat() * 1.4F);
		}
	}

	private static float parX;
	private static float parY;
	private static Phase phase = Phase.now();

	private MenuScene() {}

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath("emberleaf", "textures/gui/menu/" + name + ".png");
	}

	public static Phase phase() {
		return phase;
	}

	private static float time() {
		return (System.currentTimeMillis() % 3_600_000L) / 1000F;
	}

	/** Sky, clouds and the island. {@code islandX0} is where the island's area starts (0 = whole screen). */
	public static void draw(GuiGraphicsExtractor g, int width, int height, float mouseX, float mouseY, int islandX0) {
		phase = Phase.now();
		float t = time();
		float tx = width > 0 ? Mth.clamp(mouseX / width * 2 - 1, -1, 1) : 0;
		float ty = height > 0 ? Mth.clamp(mouseY / height * 2 - 1, -1, 1) : 0;
		parX += (tx - parX) * 0.05F;
		parY += (ty - parY) * 0.05F;

		String scene = scene();
		if (net.emberleaf.client.menu.live.LiveScene.SCENES.contains(scene)) {
			net.emberleaf.client.menu.live.LiveMenu.draw(g, scene, width, height, parX, parY, phase);
			return;
		}
		scene = scene.equals("island") ? "autumn" : scene;
		Look look = look(scene);
		int horizon = Math.round(height * 0.72F);
		g.fillGradient(0, 0, width, horizon, look.skyTop, look.skyBottom);
		g.fill(0, horizon, width, height, look.skyBottom);
		float u = Math.max(width, height) / 480F; // one "view unit"
		if (!scene.equals("autumn")) {
			sceneSky(g, scene, width, height, horizon, u, t);
		} else switch (phase) {
			case NIGHT -> {
				for (int i = 0; i < 12; i++) {
					glow(g, width * (0.04F + i * 0.085F) - parX * 4 * u, height * (0.08F + i * 0.025F) + Mth.sin(i * 1.7F) * 9 * u, 120 * u,
						ARGB.color(0.14F, i % 3 == 0 ? 0xB06AD8 : i % 3 == 1 ? 0x5A8AE0 : 0xD87AA8));
				}
				for (float[] s : STARS) {
					float tw = 0.6F + 0.4F * Mth.sin(t * s[2] + s[3]);
					float x = s[0] * width - parX * 3 * u;
					float y = s[1] * height;
					glow(g, x, y, (s[2] > 2.6F ? 5 : 3.2F) * u, ARGB.color(tw, s[2] > 2.8F ? 0xFFE8C8 : 0xE6EEFF));
				}
				float mx = width * 0.6F - parX * 6 * u;
				float my = height * 0.13F;
				glow(g, mx, my, 120 * u, ARGB.color(0.34F, 0xF3EAD2));
				blitScaled(g, MOON, mx - 12 * u, my - 12 * u, 24 * u, 24 * u, 176, 176, -1);
			}
			case SUNSET -> {
				glow(g, width * 0.56F - parX * 8 * u, horizon - 6 * u, 360 * u, ARGB.color(0.6F, 0xFFB060));
				glow(g, width * 0.56F - parX * 8 * u, horizon - 4 * u, 90 * u, ARGB.color(0.85F, 0xFFE3A6));
			}
			case DAY -> {
				glow(g, width * 0.78F - parX * 6 * u, height * 0.16F, 150 * u, ARGB.color(0.45F, 0xFFFFFF));
				glow(g, width * 0.78F - parX * 6 * u, height * 0.16F, 26 * u, ARGB.color(0.95F, 0xFFF6D8));
			}
		}
		// Wolken oben und das Wolkenmeer unten
		float drift = t * 1.6F;
		if (look.upperClouds) {
			cloudBand(g, width, -10 * u, 135 * u, -parX * 8 * u - drift % (544 * u), ARGB.multiplyAlpha(look.cloudTint, phase == Phase.NIGHT ? 0.45F : 0.8F), u);
		}
		for (int i = 0; i < 4; i++) {
			float y = horizon - 40 * u + i * 26 * u + parY * 3 * (i + 1) * u;
			float shift = -parX * (10 + i * 8) * u - (drift * (0.6F + i * 0.3F)) % (544 * u);
			cloudBand(g, width, y, (110 + i * 30) * u, shift, ARGB.multiplyAlpha(look.cloudTint, 0.75F + i * 0.06F), u);
		}
		g.fillGradient(0, Math.round(horizon + 40 * u), width, height, ARGB.multiplyAlpha(look.cloudTint, 0.0F), ARGB.multiplyAlpha(look.cloudTint, 0.85F));

		// die schwebende Insel (erst wenn Minecraft fertig geladen hat)
		if (Minecraft.getInstance().gui.overlay() != null) return;
		int x0 = Math.max(0, islandX0);
		int areaW = width - x0;
		float scale = Math.min(areaW / 33F, height / 22F);
		float yaw = -38 + parX * 7;
		float pitch = 24 + parY * 4;
		Minecraft mc = Minecraft.getInstance();
		PlayerSkin skin = localSkin(mc);
		Identifier texture = skin != null ? skin.body().texturePath() : null;
		PlayerModelType model = skin != null ? skin.model() : PlayerModelType.WIDE;
		float lookYaw = Mth.clamp(-parX * 0.6F, -0.6F, 0.6F);
		g.guiRenderState.addPicturesInPictureState(new IslandRenderState(scene, yaw, pitch, t, look.islandTint, texture, model, lookYaw,
			onlineFriends(mc), x0, 0, width, height, scale));
	}

	/** Sky colours, cloud-sea tint and island light for each scene (autumn follows the time of day). */
	private record Look(int skyTop, int skyBottom, int cloudTint, int islandTint, boolean upperClouds) {}

	private static Look look(String scene) {
		return switch (scene) {
			case "nether" -> new Look(0xFF1A0608, 0xFF8A2A10, 0xFFC8442A, 0xFFFFD6C6, false);
			case "end" -> new Look(0xFF05030C, 0xFF2A1640, 0xFF6A4A9A, 0xFFE4D6FF, false);
			case "cave" -> new Look(0xFF040810, 0xFF142838, 0xFF34506A, 0xFFB8CCE0, false);
			default -> new Look(phase.skyTop, phase.skyBottom, phase.cloudTint, phase.islandTint, true);
		};
	}

	private static void sceneSky(GuiGraphicsExtractor g, String scene, int width, int height, int horizon, float u, float t) {
		switch (scene) {
			case "nether" -> {
				glow(g, width * 0.55F - parX * 8 * u, horizon, 420 * u, ARGB.color(0.45F, 0xFF5A1A));
				for (int i = 0; i < 40; i++) { // embers drifting up from the lava sea
					float life = (t * 0.07F + i * 0.137F) % 1F;
					float x = (i * 0.61803F % 1F) * width + Mth.sin(t * 0.7F + i) * 12 * u - parX * 5 * u;
					float y = horizon + 30 * u - life * height * 0.8F;
					glow(g, x, y, 4 * u, ARGB.color((1 - life) * 0.9F, life < 0.5F ? 0xFFC060 : 0xFF6A2A));
				}
			}
			case "end" -> {
				for (float[] s : STARS) {
					float tw = 0.6F + 0.4F * Mth.sin(t * s[2] + s[3]);
					glow(g, s[0] * width - parX * 3 * u, s[1] * height * 1.2F, (s[2] > 2.6F ? 5 : 3.2F) * u, ARGB.color(tw, s[2] > 2.8F ? 0xF0C8FF : 0xE6DEFF));
				}
				for (int i = 0; i < 8; i++) {
					glow(g, width * (0.1F + i * 0.12F) - parX * 4 * u, height * (0.2F + Mth.sin(i * 2.1F) * 0.08F), 140 * u,
						ARGB.color(0.10F, i % 2 == 0 ? 0xB06AD8 : 0x6A4AE0));
				}
			}
			case "cave" -> {
				for (int i = 0; i < 46; i++) { // glowing spores
					float x = (i * 0.61803F % 1F) * width + Mth.sin(t * 0.3F + i) * 20 * u - parX * 6 * u;
					float y = ((i * 0.3819F + t * 0.01F) % 1F) * height;
					float pulse = 0.4F + 0.6F * Math.max(0, Mth.sin(t * 1.3F + i * 2));
					glow(g, x, y, 5 * u, ARGB.color(pulse * 0.8F, i % 3 == 0 ? 0xC8A8FF : 0x9AFFC8));
				}
			}
			default -> {
			}
		}
	}

	private static String scene;

	/** Live scenes plus the classic floating island. */
	public static final java.util.List<String> CHOICES = java.util.List.of("cave", "nether", "overworld", "end", "island");

	/** The scene to show: setting "menu_scene" (in game or from the launcher); auto = a different live scene each start. */
	public static String scene() {
		String forced = System.getProperty("emberleaf.menuScene");
		if (forced != null && CHOICES.contains(forced)) return forced;
		String chosen = net.emberleaf.client.config.Modules.choice("menu_scene");
		if (chosen != null && CHOICES.contains(chosen)) return chosen;
		if (scene == null) {
			String wanted = "auto";
			try {
				java.nio.file.Path file = net.emberleaf.client.outfit.OutfitStore.dataDir().resolve("settings.json");
				if (java.nio.file.Files.exists(file)) {
					var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
					if (json.has("menu_scene")) wanted = json.get("menu_scene").getAsString();
				}
			} catch (Exception ignored) {
				// keep auto
			}
			if (wanted.equals("autumn")) wanted = "overworld"; // the launcher's names
			var live = net.emberleaf.client.menu.live.LiveScene.SCENES;
			scene = CHOICES.contains(wanted) ? wanted : live.get(new Random().nextInt(live.size()));
		}
		return scene;
	}

	/** The sky of the overworld scenes: gradient, sun, moon and stars by time of day, clouds drifting along. */
	public static void sky(GuiGraphicsExtractor g, int width, int height, float parX, float t, Phase p) {
		float u = Math.max(width, height) / 480F;
		int horizon = Math.round(height * 0.72F);
		g.fillGradient(0, 0, width, horizon, p.skyTop, p.skyBottom);
		g.fill(0, horizon, width, height, p.skyBottom);
		switch (p) {
			case NIGHT -> {
				stars(g, width, height, parX, t, 0xE6EEFF);
				float mx = width * 0.66F - parX * 6 * u;
				float my = height * 0.14F;
				glow(g, mx, my, 120 * u, ARGB.color(0.34F, 0xF3EAD2));
				blitScaled(g, MOON, mx - 12 * u, my - 12 * u, 24 * u, 24 * u, 176, 176, -1);
			}
			case SUNSET -> {
				glow(g, width * 0.3F - parX * 8 * u, horizon - 30 * u, 380 * u, ARGB.color(0.6F, 0xFFB060));
				glow(g, width * 0.3F - parX * 8 * u, horizon - 28 * u, 90 * u, ARGB.color(0.85F, 0xFFE3A6));
			}
			case DAY -> {
				glow(g, width * 0.3F - parX * 6 * u, height * 0.16F, 150 * u, ARGB.color(0.45F, 0xFFFFFF));
				glow(g, width * 0.3F - parX * 6 * u, height * 0.16F, 26 * u, ARGB.color(0.95F, 0xFFF6D8));
			}
		}
		float drift = t * 1.6F;
		cloudBand(g, width, -10 * u, 135 * u, -parX * 8 * u - drift % (544 * u), ARGB.multiplyAlpha(p.cloudTint, p == Phase.NIGHT ? 0.45F : 0.8F), u);
	}

	public static void stars(GuiGraphicsExtractor g, int width, int height, float parX, float t, int color) {
		float u = Math.max(width, height) / 480F;
		for (float[] s : STARS) {
			float tw = 0.6F + 0.4F * Mth.sin(t * s[2] + s[3]);
			glow(g, s[0] * width - parX * 3 * u, s[1] * height, (s[2] > 2.6F ? 5 : 3.2F) * u, ARGB.color(tw, s[2] > 2.8F ? 0xFFE8C8 : color));
		}
	}
	/** Friends who are online right now, standing on the island next to you. */
	public static java.util.List<IslandRenderState.Figure> onlineFriends(Minecraft mc) {
		var state = net.emberleaf.client.friends.FriendsSync.friends();
		java.util.List<IslandRenderState.Figure> out = new java.util.ArrayList<>();
		for (var f : state.friends()) {
			if (!f.online() || out.size() >= 4) continue;
			try {
				String raw = f.uuid();
				java.util.UUID id = java.util.UUID.fromString(raw.length() == 32
					? raw.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5") : raw);
				PlayerSkin s = mc.playerSkinRenderCache().getOrDefault(net.minecraft.world.item.component.ResolvableProfile.createUnresolved(id)).playerSkin();
				out.add(new IslandRenderState.Figure(s.body().texturePath(), s.model()));
			} catch (IllegalArgumentException ignored) {
				// broken uuid from the server: skip
			}
		}
		return out;
	}

	/** Set by the title screen once the game profile is ready; never block the render thread waiting for it. */
	public static volatile java.util.function.Supplier<PlayerSkin> skinLookup;

	public static PlayerSkin localSkin(Minecraft mc) {
		if (mc.player instanceof AbstractClientPlayer p) return p.getSkin();
		java.util.function.Supplier<PlayerSkin> lookup = skinLookup;
		PlayerSkin skin = lookup != null ? lookup.get() : null;
		return skin != null ? net.emberleaf.client.skin.SkinLibrary.apply(skin) : null;
	}

	/** Leaves drifting across the whole menu, in front of everything else. */
	public static void drawFront(GuiGraphicsExtractor g, int width, int height) {
		if (!scene().equals("island")) return; // leaves only fall in the autumn island scene
		float t = time();
		float u = Math.max(width, height) / 480F;
		int tint = phase == Phase.NIGHT ? 0xFFA8A4C8 : -1;
		Matrix3x2fStack pose = g.pose();
		for (float[] l : FALLING) {
			float fall = ((t * l[1] + l[2] * 0.37F) % 1.15F - 0.08F) * height;
			float x = ((l[0] + Mth.sin(t * 0.7F + l[2]) * 0.03F - parX * 0.04F + 10) % 1.05F) * width;
			float size = (4.5F + (l[2] % 4)) * u;
			float flutter = 0.55F + 0.45F * Math.abs(Mth.cos(t * 1.7F + l[2]));
			pose.pushMatrix();
			pose.translate(x, fall);
			pose.rotate(t * l[5] + l[2]);
			pose.scale(size / 64F * flutter, size / 64F);
			g.blit(RenderPipelines.GUI_TEXTURED, LEAVES, -32, -32, (int) l[3] * 64, (int) l[4] * 64, 64, 64, 64, 64, 256, 256, tint);
			pose.popMatrix();
		}
	}

	private static void cloudBand(GuiGraphicsExtractor g, int width, float y, float h, float shift, int tint, float u) {
		float w = 544 * u * (h / (135 * u));
		float x = shift % w;
		if (x > 0) x -= w;
		for (; x < width; x += w) blitScaled(g, CLOUDS, x, y, w, h, 2176, 540, tint);
	}

	private static void blitScaled(GuiGraphicsExtractor g, Identifier tex, float x, float y, float w, float h, int tw, int th, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(w / tw, h / th);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0, 0, tw, th, tw, th, color);
		pose.popMatrix();
	}

	/** A soft round light (radial gradient) centred on (cx, cy). */
	public static void glow(GuiGraphicsExtractor g, float cx, float cy, float size, int color) {
		if (size <= 0) return;
		blitScaled(g, GLOW, cx - size / 2, cy - size / 2, size, size, 256, 256, color);
	}
}
