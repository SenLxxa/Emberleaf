package net.emberleaf.client.menu.live;

import com.mojang.blaze3d.platform.NativeImage;
import java.time.LocalDate;
import java.time.Month;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.menu.IslandRenderState;
import net.emberleaf.client.menu.MenuScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;

/**
 * The live-rendered main menu: a short flight into the scene when the menu opens, then a slowly drifting camera
 * that follows the mouse a little. On top of the 3D scene: bloom around lights, light shafts, particles,
 * snow in winter. Quality (low … ultra) controls the extras; in the background it holds still at 15 FPS.
 */
public final class LiveMenu {
	private static final Identifier RAY = Identifier.fromNamespaceAndPath("emberleaf", "live/ray");
	private static final float FLIGHT = 2.8F;
	private static long opened = System.currentTimeMillis();
	private static long lastDraw;
	private static boolean rayReady;

	private LiveMenu() {}

	private static float seconds() {
		long now = System.currentTimeMillis();
		if (now - lastDraw > 3000) opened = now; // back in the menu after a while: fly in again
		lastDraw = now;
		float t = (now - opened) / 1000F;
		if (!Minecraft.getInstance().isWindowActive()) t = Math.round(t * 15) / 15F; // 15 FPS in the background
		return t;
	}

	public static int quality() {
		String q = Modules.choice("menu_quality");
		return switch (q == null ? "high" : q) {
			case "low" -> 0;
			case "medium" -> 1;
			case "ultra" -> 3;
			default -> 2;
		};
	}

	public static void draw(GuiGraphicsExtractor g, String name, int width, int height, float parX, float parY, MenuScene.Phase phase) {
		float t = seconds();
		backdrop(g, name, width, height, parX, t, phase);
		LiveScene sc = LiveScene.get(name);
		if (sc == null || Minecraft.getInstance().gui.overlay() != null) return;
		int quality = quality();

		// camera: fly in, then drift and lean towards the mouse
		float fly = Modules.on("menu_flight") ? Mth.clamp(t / FLIGHT, 0, 1) : 1;
		float e = 1 - (1 - fly) * (1 - fly) * (1 - fly);
		float[] eye = new float[3];
		float[] target = new float[3];
		for (int i = 0; i < 3; i++) eye[i] = Mth.lerp(e, sc.camStart[i], sc.camEnd[i]);
		eye[0] += Mth.sin(t * 0.13F) * 0.35F;
		eye[1] += Mth.sin(t * 0.17F + 1) * 0.2F;
		target[0] = sc.target[0] + parX * 3.5F;
		target[1] = sc.target[1] - parY * 2.0F;
		target[2] = sc.target[2];

		int ambient;
		int sun;
		if (sc.sky) {
			ambient = switch (phase) {
				case DAY -> rgb(0.62F, 0.66F, 0.76F);
				case SUNSET -> rgb(0.46F, 0.40F, 0.48F);
				case NIGHT -> rgb(0.13F, 0.15F, 0.27F);
			};
			sun = switch (phase) {
				case DAY -> rgb(1.0F, 0.94F, 0.82F);
				case SUNSET -> rgb(1.0F, 0.64F, 0.38F);
				case NIGHT -> rgb(0.22F, 0.27F, 0.42F);
			};
		} else {
			ambient = rgb(sc.ambient[0], sc.ambient[1], sc.ambient[2]);
			sun = 0;
		}
		float flicker = 0.9F + 0.06F * Mth.sin(t * 9.1F) + 0.04F * Mth.sin(t * 23.7F + 1);
		Minecraft mc = Minecraft.getInstance();
		PlayerSkin skin = MenuScene.localSkin(mc);
		Identifier texture = skin != null ? skin.body().texturePath() : null;
		PlayerModelType model = skin != null ? skin.model() : PlayerModelType.WIDE;
		LiveSceneState state = new LiveSceneState(name, Math.round(t * 30) / 30F, eye, target, sc.fov, ambient, sun, flicker, quality,
			texture, model, MenuScene.onlineFriends(mc), 0, 0, width, height);
		g.guiRenderState.addPicturesInPictureState(state);

		// everything on top of the 3D picture
		g.nextStratum();
		LiveCamera cam = state.camera();
		float cx = width / 2F;
		float cy = height / 2F;
		if (quality >= 1) lightGlows(g, sc, cam, cx, cy, flicker, quality);
		if (quality >= 2) rays(g, sc, cam, cx, cy, t);
		particles(g, sc, cam, cx, cy, t, quality, phase);
		if (isWinter()) snow(g, width, height, t, parX, quality);
		if (fly < 1) g.fill(0, 0, width, height, ARGB.color(Mth.clamp(1 - t / 0.9F, 0, 1), 0x000000)); // fade in from the loading screen
		if (Modules.on("menu_blur")) {
			g.nextStratum();
			g.blurBeforeThisStratum();
		}
	}

	private static int rgb(float r, float g, float b) {
		return ARGB.colorFromFloat(1, Mth.clamp(r, 0, 1), Mth.clamp(g, 0, 1), Mth.clamp(b, 0, 1));
	}

	private static boolean isWinter() {
		Month m = LocalDate.now().getMonth();
		return m == Month.DECEMBER || m == Month.JANUARY || m == Month.FEBRUARY || "true".equals(System.getProperty("emberleaf.winter"));
	}

	// ---------- Hintergrund ----------

	private static void backdrop(GuiGraphicsExtractor g, String name, int w, int h, float parX, float t, MenuScene.Phase phase) {
		switch (name) {
			case "cave" -> g.fillGradient(0, 0, w, h, 0xFF06070C, 0xFF0E0B10);
			case "nether" -> g.fillGradient(0, 0, w, h, 0xFF1A0507, 0xFF4A140A);
			case "end" -> {
				g.fillGradient(0, 0, w, h, 0xFF05030C, 0xFF1C0F2E);
				MenuScene.stars(g, w, h, parX, t, 0xF0C8FF);
				float u = Math.max(w, h) / 480F;
				for (int i = 0; i < 8; i++) {
					MenuScene.glow(g, w * (0.1F + i * 0.12F) - parX * 4 * u, h * (0.2F + Mth.sin(i * 2.1F) * 0.08F), 140 * u,
						ARGB.color(0.10F, i % 2 == 0 ? 0xB06AD8 : 0x6A4AE0));
				}
			}
			default -> MenuScene.sky(g, w, h, parX, t, phase);
		}
	}

	// ---------- Leuchten, Strahlen, Partikel ----------

	private static void lightGlows(GuiGraphicsExtractor g, LiveScene sc, LiveCamera cam, float cx, float cy, float flicker, int quality) {
		float[] o = new float[3];
		int drawn = 0;
		int max = quality >= 3 ? 90 : quality >= 2 ? 60 : 30;
		for (float[] l : sc.lights) {
			if (l.length > 8 && l[8] < 0.5F) continue; // hidden behind rock from the camera
			if (!cam.project(l[0], l[1], l[2], o) || o[2] > sc.fogEnd) continue;
			float fade = 1 - o[2] / sc.fogEnd;
			float size = Mth.clamp(l[6] * 0.5F * cam.scaleAt(o[2]), 6, 260);
			int c = ARGB.colorFromFloat(1, l[3], l[4], l[5]);
			MenuScene.glow(g, cx + o[0], cy + o[1], size, ARGB.color(0.13F * l[7] * flicker * fade, c & 0xFFFFFF));
			MenuScene.glow(g, cx + o[0], cy + o[1], size * 0.25F, ARGB.color(0.22F * l[7] * flicker * fade, c & 0xFFFFFF));
			if (++drawn >= max) break;
		}
	}

	private static void ensureRay() {
		if (rayReady) return;
		NativeImage img = new NativeImage(32, 128, true);
		for (int y = 0; y < 128; y++) {
			float v = 1 - y / 127F;
			for (int x = 0; x < 32; x++) {
				float d = (x - 15.5F) / 15.5F;
				float a = (float) Math.exp(-d * d * 3.2) * (0.25F + 0.75F * v * v);
				img.setPixel(x, y, ARGB.color(Math.round(a * 255), 255, 255, 255));
			}
		}
		Minecraft.getInstance().getTextureManager().register(RAY, new DynamicTexture(() -> "Emberleaf ray", img));
		rayReady = true;
	}

	private static void rays(GuiGraphicsExtractor g, LiveScene sc, LiveCamera cam, float cx, float cy, float t) {
		if (sc.rays.isEmpty()) return;
		ensureRay();
		float[] a = new float[3];
		float[] b = new float[3];
		for (float[] r : sc.rays) {
			for (int k = 0; k < 3; k++) {
				float off = (k - 1) * r[6] * 0.28F;
				if (!cam.project(r[0] + off, r[1], r[2], a) || !cam.project(r[3] + off * 1.4F, r[4], r[5], b)) continue;
				float dx = b[0] - a[0];
				float dy = b[1] - a[1];
				float len = (float) Math.sqrt(dx * dx + dy * dy);
				float width = r[6] * 0.55F * cam.scaleAt((a[2] + b[2]) / 2);
				float shimmer = 0.16F + 0.06F * Mth.sin(t * 0.8F + k * 2.1F);
				Matrix3x2fStack pose = g.pose();
				pose.pushMatrix();
				pose.translate(cx + a[0], cy + a[1]);
				pose.rotate((float) Math.atan2(dy, dx) - Mth.HALF_PI);
				g.blit(RenderPipelines.GUI_TEXTURED, RAY, Math.round(-width / 2), 0, 0, 0, Math.round(width), Math.round(len), 32, 128, 32, 128,
					ARGB.colorFromFloat(shimmer, r[7], r[8], r[9]));
				pose.popMatrix();
			}
		}
	}

	private static float hash(int i, int k) {
		int h = i * 374761393 + k * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
	}

	private static void particles(GuiGraphicsExtractor g, LiveScene sc, LiveCamera cam, float cx, float cy, float t, int quality,
		MenuScene.Phase phase) {
		float amount = quality == 0 ? 0.35F : quality == 1 ? 0.6F : quality == 2 ? 1 : 1.5F;
		float[] o = new float[3];
		int n = 0;
		for (LiveScene.Emitter em : sc.emitters) {
			int count = Math.round(em.count() * amount);
			float[] a = em.area();
			for (int i = 0; i < count; i++) {
				n++;
				float rx = hash(n, 1), ry = hash(n, 2), rz = hash(n, 3);
				float x, y, z, size, alpha;
				int color;
				switch (em.type()) {
					case "embers" -> {
						float life = (t * (0.12F + rz * 0.08F) + rx * 7.3F) % 1F;
						x = a[0] + rx * (a[3] - a[0]) + Mth.sin(t * 1.3F + i) * 0.4F;
						z = a[2] + rz * (a[5] - a[2]);
						y = a[1] + life * (a[4] - a[1] + 9);
						size = 0.09F;
						alpha = (1 - life) * 0.95F;
						color = life < 0.4F ? 0xFFC060 : 0xFF6A2A;
					}
					case "dust" -> {
						x = a[0] + rx * (a[3] - a[0]) + Mth.sin(t * 0.11F + i) * 0.9F;
						y = a[1] + ry * (a[4] - a[1]) + Mth.sin(t * 0.13F + i * 1.3F) * 0.6F;
						z = a[2] + rz * (a[5] - a[2]);
						size = 0.05F;
						alpha = 0.3F + 0.2F * Mth.sin(t + i);
						color = 0xFFE2C0;
					}
					case "drip" -> {
						float life = (t * 0.55F + rx * 5.1F) % 1F;
						x = a[0] + rx * (a[3] - a[0]);
						z = a[2] + rz * (a[5] - a[2]);
						y = a[4] - life * life * (a[4] - a[1] + 4);
						size = 0.05F;
						alpha = 0.7F * (1 - life);
						color = 0xA8D8FF;
					}
					case "ash" -> {
						float life = (t * 0.035F + ry) % 1F;
						x = a[0] + rx * (a[3] - a[0]) + Mth.sin(t * 0.4F + i) * 1.4F;
						z = a[2] + rz * (a[5] - a[2]);
						y = a[4] - life * (a[4] - a[1]);
						size = 0.07F;
						alpha = 0.55F;
						color = i % 3 == 0 ? 0xFF8A4A : 0xD8C8C0;
					}
					case "fireflies" -> {
						if (phase == MenuScene.Phase.DAY) continue;
						x = a[0] + rx * (a[3] - a[0]) + Mth.sin(t * 0.3F + i * 1.7F) * 1.6F;
						y = a[1] + ry * (a[4] - a[1]) + Mth.sin(t * 0.47F + i) * 0.9F;
						z = a[2] + rz * (a[5] - a[2]) + Mth.cos(t * 0.27F + i) * 1.6F;
						size = 0.12F;
						alpha = Math.max(0, Mth.sin(t * 1.9F + i * 3.1F)) * (phase == MenuScene.Phase.NIGHT ? 1 : 0.5F);
						color = 0xD8FF7A;
					}
					case "leaves" -> {
						float life = (t * 0.05F + ry) % 1F;
						x = a[0] + rx * (a[3] - a[0]) + Mth.sin(t * 0.9F + i) * 1.2F;
						z = a[2] + rz * (a[5] - a[2]);
						y = a[4] - life * (a[4] - a[1]);
						size = 0.14F;
						alpha = 0.85F;
						color = i % 3 == 0 ? 0xD8642C : i % 3 == 1 ? 0xE8A23A : 0xB8462A;
					}
					case "smoke" -> {
						float[] at = em.at();
						float life = (t * 0.22F + (float) i / count) % 1F;
						x = at[0] + Mth.sin(life * 6 + i) * 0.6F * life;
						y = at[1] + life * 7;
						z = at[2] + Mth.cos(life * 5 + i) * 0.4F * life;
						size = 0.25F + life * 0.9F;
						alpha = (1 - life) * 0.22F;
						color = 0xB8B0A8;
					}
					case "sparkles" -> {
						x = a[0] + rx * (a[3] - a[0]) + Mth.sin(t * 0.2F + i) * 1.0F;
						y = a[1] + ry * (a[4] - a[1]) + Mth.sin(t * 0.31F + i * 2) * 0.8F;
						z = a[2] + rz * (a[5] - a[2]);
						size = 0.08F;
						alpha = Math.max(0, Mth.sin(t * 2.4F + i * 1.7F));
						color = i % 2 == 0 ? 0xE8C8FF : 0xB890FF;
					}
					default -> {
						continue;
					}
				}
				if (!cam.project(x, y, z, o) || o[2] > sc.fogEnd) continue;
				float px = Math.max(1.2F, size * cam.scaleAt(o[2]));
				MenuScene.glow(g, cx + o[0], cy + o[1], px * 2.4F, ARGB.color(alpha * 0.45F, color));
				MenuScene.glow(g, cx + o[0], cy + o[1], px, ARGB.color(alpha, color));
			}
		}
	}

	private static void snow(GuiGraphicsExtractor g, int w, int h, float t, float parX, int quality) {
		int count = quality == 0 ? 60 : quality == 1 ? 110 : 170;
		for (int i = 0; i < count; i++) {
			float depth = 0.4F + hash(i, 9) * 0.6F;
			float life = (t * 0.06F * depth + hash(i, 7)) % 1F;
			float x = (hash(i, 8) * w + Mth.sin(t * 0.7F + i) * 14 * depth - parX * 20 * depth + w) % w;
			float y = life * (h + 20) - 10;
			MenuScene.glow(g, x, y, 2.6F * depth + 1, ARGB.color(0.75F * depth, 0xFFFFFF));
		}
	}
}
