package net.emberleaf.client.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Things the Emberleaf texture pack brings that 26.3 no longer understands by itself, done by the client:
 * <ul>
 *   <li>OptiFine "CIT" elytras: rename an elytra (e.g. "Citrus") and you wear that elytra (Elytras+)</li>
 *   <li>Xaero-style minimap pictures for mobs (Icon Fresh), shown on the Emberleaf minimap</li>
 * </ul>
 */
public final class PackFeatures {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	/** Mob pictures are shrunk to this size (plus a 1 px dark outline on every side). */
	public static final int ICON_INNER = 16;
	public static final int ICON_SIZE = ICON_INNER + 2;

	private record ElytraRule(Pattern name, Identifier texture) {}

	private static volatile List<ElytraRule> elytras = List.of();
	private static final Map<EntityType<?>, Optional<Identifier>> ICONS = new HashMap<>();
	private static volatile int generation;

	private PackFeatures() {}

	/** Goes up with every resource reload: things built from block models or textures (menu scenes) rebuild themselves. */
	public static int generation() {
		return generation;
	}

	public static void register() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Identifier.fromNamespaceAndPath("emberleaf", "pack_features"),
			(ResourceManagerReloadListener) PackFeatures::reload);
	}

	/** Runs on the game thread after every resource reload (game start, F3+T, texture pack changes). */
	private static void reload(ResourceManager rm) {
		List<ElytraRule> rules = new ArrayList<>();
		try {
			for (Map.Entry<Identifier, Resource> e : rm.listResources("optifine/cit", id -> id.getPath().endsWith(".properties")).entrySet()) {
				Map<String, String> p = properties(e.getValue());
				if (!"elytra".equals(p.get("type"))) continue;
				String items = p.getOrDefault("items", p.getOrDefault("matchItems", ""));
				if (!items.contains("elytra")) continue;
				String name = p.getOrDefault("nbt.display.Name", p.get("components.custom_name"));
				String tex = p.getOrDefault("texture", p.get("texture.elytra"));
				if (name == null || tex == null) continue;
				Pattern pattern = namePattern(name);
				Identifier texture = resolve(e.getKey(), tex);
				if (pattern != null && texture != null && rm.getResource(texture).isPresent()) rules.add(new ElytraRule(pattern, texture));
			}
		} catch (Exception ex) {
			LOG.warn("Emberleaf: Elytra-Namen aus dem Texturenpaket konnten nicht gelesen werden", ex);
		}
		elytras = List.copyOf(rules);
		generation++;
		synchronized (ICONS) {
			var textures = Minecraft.getInstance().getTextureManager();
			for (Optional<Identifier> icon : ICONS.values()) icon.ifPresent(textures::release);
			ICONS.clear();
		}
		if (!rules.isEmpty()) LOG.info("Emberleaf: {} Elytra-Namen aus dem Texturenpaket", rules.size());
	}

	private static Map<String, String> properties(Resource r) throws java.io.IOException {
		Map<String, String> out = new HashMap<>();
		try (BufferedReader in = r.openAsReader()) {
			String line;
			while ((line = in.readLine()) != null) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) continue;
				int eq = line.indexOf('=');
				if (eq > 0) out.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
			}
		}
		return out;
	}

	/** OptiFine name matchers: plain, pattern:/ipattern: (globs), regex:/iregex:. */
	static @Nullable Pattern namePattern(String raw) {
		try {
			if (raw.startsWith("iregex:")) return Pattern.compile(raw.substring(7), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			if (raw.startsWith("regex:")) return Pattern.compile(raw.substring(6));
			if (raw.startsWith("ipattern:")) return Pattern.compile(glob(raw.substring(9)), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			if (raw.startsWith("pattern:")) return Pattern.compile(glob(raw.substring(8)));
			return Pattern.compile(Pattern.quote(raw));
		} catch (Exception e) {
			return null;
		}
	}

	private static String glob(String g) {
		StringBuilder b = new StringBuilder();
		for (char c : g.toCharArray()) {
			if (c == '*') b.append(".*");
			else if (c == '?') b.append('.');
			else b.append(Pattern.quote(String.valueOf(c)));
		}
		return b.toString();
	}

	/** OptiFine texture paths: "name" or "./name" next to the .properties file, "~/x" = optifine/x, "ns:path", or a path from the namespace root. */
	private static @Nullable Identifier resolve(Identifier propertiesFile, String tex) {
		String path = tex.endsWith(".png") ? tex : tex + ".png";
		String ns = propertiesFile.getNamespace();
		if (path.startsWith("~/")) {
			path = "optifine/" + path.substring(2);
		} else if (path.contains(":")) {
			ns = path.substring(0, path.indexOf(':'));
			path = path.substring(path.indexOf(':') + 1);
		} else if (path.startsWith("./") || !path.contains("/")) {
			String dir = propertiesFile.getPath().substring(0, propertiesFile.getPath().lastIndexOf('/') + 1);
			path = dir + (path.startsWith("./") ? path.substring(2) : path);
		}
		try {
			return Identifier.fromNamespaceAndPath(ns, path.toLowerCase(Locale.ROOT));
		} catch (Exception e) {
			return null;
		}
	}

	/** The elytra texture for a renamed elytra, or null for the normal one. */
	public static @Nullable Identifier elytraTexture(ItemStack stack) {
		List<ElytraRule> rules = elytras;
		if (rules.isEmpty() || stack.isEmpty() || !stack.is(Items.ELYTRA)) return null;
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		if (name == null) return null;
		String text = name.getString().strip();
		for (ElytraRule r : rules) {
			if (r.name().matcher(text).matches()) return r.texture();
		}
		return null;
	}

	/**
	 * The minimap picture for a mob type (Icon Fresh / Xaero format) if the texture pack has one:
	 * a texture of {@link #ICON_SIZE}² pixels with a dark outline, or null.
	 */
	public static @Nullable Identifier mobIcon(EntityType<?> type) {
		synchronized (ICONS) {
			return ICONS.computeIfAbsent(type, PackFeatures::loadIcon).orElse(null);
		}
	}

	private static Optional<Identifier> loadIcon(EntityType<?> type) {
		Identifier id = EntityType.getKey(type);
		Identifier def = Identifier.fromNamespaceAndPath("xaerominimap", "entity/icon/definition/" + id.getNamespace() + "/" + id.getPath() + ".json");
		Minecraft mc = Minecraft.getInstance();
		var rm = mc.getResourceManager();
		var res = rm.getResource(def);
		if (res.isEmpty()) return Optional.empty();
		try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
			JsonObject variants = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("variants");
			if (variants == null) return Optional.empty();
			String chosen = sprite(variants.get("default"));
			if (chosen == null) {
				for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
					chosen = sprite(e.getValue());
					if (chosen != null) break;
				}
			}
			if (chosen == null) return Optional.empty();
			Identifier spriteFile = Identifier.fromNamespaceAndPath("xaerominimap", "entity/icon/sprite/" + chosen);
			var sprite = rm.getResource(spriteFile);
			if (sprite.isEmpty()) return Optional.empty();
			NativeImage icon;
			try (InputStream in = sprite.get().open(); NativeImage src = NativeImage.read(in)) {
				icon = iconImage(src);
			}
			Identifier tex = Identifier.fromNamespaceAndPath("emberleaf", "minimap/mob/" + id.getNamespace() + "/" + id.getPath());
			DynamicTexture texture = new DynamicTexture(() -> "Emberleaf minimap " + id, icon);
			texture.upload();
			mc.getTextureManager().register(tex, texture);
			return Optional.of(tex);
		} catch (Exception e) {
			LOG.debug("Emberleaf: no minimap picture for {}", id, e);
			return Optional.empty();
		}
	}

	/** "outlined_sprite:minecraft/zombie/zombie.png" -> "minecraft/zombie/zombie.png"; anything else (comments, flags) -> null. */
	private static @Nullable String sprite(@Nullable JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
		String s = value.getAsString();
		if (!s.endsWith(".png")) return null;
		return s.contains(":") ? s.substring(s.indexOf(':') + 1) : s;
	}

	/** Shrinks a (usually 64x64) mob picture to 16 px by averaging (keeps it clean instead of flickery) and adds a dark outline. */
	private static NativeImage iconImage(NativeImage src) {
		int sw = src.getWidth();
		int sh = src.getHeight();
		float scale = Math.min(1F, ICON_INNER / (float) Math.max(sw, sh));
		int tw = Math.max(1, Math.round(sw * scale));
		int th = Math.max(1, Math.round(sh * scale));
		NativeImage out = new NativeImage(ICON_SIZE, ICON_SIZE, true);
		int ox = 1 + (ICON_INNER - tw) / 2;
		int oy = 1 + (ICON_INNER - th) / 2;
		for (int ty = 0; ty < th; ty++) {
			int y0 = ty * sh / th;
			int y1 = Math.max(y0 + 1, (ty + 1) * sh / th);
			for (int tx = 0; tx < tw; tx++) {
				int x0 = tx * sw / tw;
				int x1 = Math.max(x0 + 1, (tx + 1) * sw / tw);
				double a = 0;
				double r = 0;
				double g = 0;
				double b = 0;
				int n = 0;
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						int c = src.getPixel(x, y);
						double ca = ARGB.alpha(c) / 255.0;
						a += ca;
						r += ARGB.red(c) * ca;
						g += ARGB.green(c) * ca;
						b += ARGB.blue(c) * ca;
						n++;
					}
				}
				if (a <= 0.001) continue;
				// coverage below a third reads as "outside", above two thirds as solid: crisp edges at minimap size
				double cover = a / n;
				int alpha = cover < 0.34 ? 0 : cover > 0.66 ? 255 : (int) Math.round(cover * 255);
				if (alpha == 0) continue;
				out.setPixel(ox + tx, oy + ty, ARGB.color(alpha, (int) Math.round(r / a), (int) Math.round(g / a), (int) Math.round(b / a)));
			}
		}
		// 1 px outline around the picture so it stands out on every kind of ground
		boolean[] solid = new boolean[ICON_SIZE * ICON_SIZE];
		for (int y = 0; y < ICON_SIZE; y++) {
			for (int x = 0; x < ICON_SIZE; x++) solid[y * ICON_SIZE + x] = ARGB.alpha(out.getPixel(x, y)) >= 128;
		}
		for (int y = 0; y < ICON_SIZE; y++) {
			for (int x = 0; x < ICON_SIZE; x++) {
				if (solid[y * ICON_SIZE + x]) continue;
				boolean edge = false;
				for (int dy = -1; dy <= 1 && !edge; dy++) {
					for (int dx = -1; dx <= 1 && !edge; dx++) {
						int nx = x + dx;
						int ny = y + dy;
						edge = nx >= 0 && ny >= 0 && nx < ICON_SIZE && ny < ICON_SIZE && solid[ny * ICON_SIZE + nx];
					}
				}
				if (edge) out.setPixel(x, y, ARGB.color(235, 30, 20, 12));
			}
		}
		return out;
	}
}
