package net.emberleaf.client.outfit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.emberleaf.client.model.EModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads the local player's outfit.json from the Emberleaf data folder and reloads it when the launcher changes it. */
public final class OutfitStore {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	private static final List<String> VOXEL_SLOTS = List.of("head", "body", "wings", "pet");
	private static final List<String> MODEL_SLOTS = List.of("head", "body", "wings", "cape", "pet");
	private static final String PNG_PREFIX = "data:image/png;base64,";

	private static volatile Outfit current = Outfit.EMPTY;
	private static volatile String currentJson = null;
	private static volatile int generation;
	private static long lastModified = -1;

	private OutfitStore() {}

	public static Outfit current() {
		return current;
	}

	/** The raw "items" JSON of the local outfit, for uploading it to the friends server. */
	public static String currentJson() {
		return currentJson;
	}

	/** Increases every time a new outfit was loaded. */
	public static int generation() {
		return generation;
	}

	public static Path dataDir() {
		String dir = System.getProperty("emberleaf.dataDir");
		if (dir == null || dir.isBlank()) {
			dir = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), ".emberleaf").toString();
		}
		return Path.of(dir);
	}

	/** Cheap check, called about once a second: reloads only if the file changed. */
	public static void reloadIfChanged() {
		Path file = dataDir().resolve("outfit.json");
		long modified;
		try {
			modified = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
		} catch (IOException e) {
			return;
		}
		if (modified == lastModified) return;
		lastModified = modified;
		current.release();
		if (modified == 0) {
			current = Outfit.EMPTY;
			currentJson = null;
		} else {
			try {
				JsonObject items = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("items");
				current = parse(items);
				currentJson = items.toString();
				LOG.info("Emberleaf outfit loaded: {} Blockbench cosmetics {}, {} voxel cosmetics, {} cape frames, aura: {}",
					current.models().size(), current.models().keySet(), current.voxels().size(), current.capeFrames().size(), current.aura() != null);
			} catch (IOException | RuntimeException e) {
				LOG.warn("Could not read Emberleaf outfit {}", file, e);
				current = Outfit.EMPTY;
				currentJson = null;
			}
		}
		generation++;
	}

	/** Turns the launcher's outfit "items" object into an {@link Outfit}. Also used for friends' outfits. */
	public static Outfit parse(JsonObject items) {
		Map<String, VoxelModel> voxels = new HashMap<>();
		for (String slot : VOXEL_SLOTS) {
			if (items.has(slot) && items.getAsJsonObject(slot).has("voxels")) {
				voxels.put(slot, VoxelModel.of(parseVoxels(items.getAsJsonObject(slot).getAsJsonArray("voxels"))));
			}
		}
		boolean petOnGround = items.has("pet") && items.getAsJsonObject("pet").has("ground")
			&& items.getAsJsonObject("pet").get("ground").getAsBoolean();

		List<byte[]> frames = new ArrayList<>();
		int fps = 8;
		if (items.has("cape")) {
			JsonObject cape = items.getAsJsonObject("cape");
			if (cape.has("fps")) fps = Math.max(1, Math.min(30, cape.get("fps").getAsInt()));
			if (cape.has("frames")) {
				for (JsonElement frame : cape.getAsJsonArray("frames")) {
					String url = frame.getAsString();
					if (url.startsWith(PNG_PREFIX)) frames.add(Base64.getDecoder().decode(url.substring(PNG_PREFIX.length())));
				}
			}
		}

		Outfit.Aura aura = null;
		if (items.has("aura") && items.getAsJsonObject("aura").has("aura")) {
			JsonObject a = items.getAsJsonObject("aura").getAsJsonObject("aura");
			aura = new Outfit.Aura(
				a.has("mode") ? a.get("mode").getAsString() : "swirl",
				a.has("sprite") ? a.get("sprite").getAsString() : "spark",
				a.has("ring") && a.get("ring").getAsBoolean());
		}
		String emote = items.has("emote") ? items.getAsJsonObject("emote").get("id").getAsString() : null;
		Map<String, EModel> models = new HashMap<>();
		for (String slot : MODEL_SLOTS) {
			if (items.has(slot) && items.getAsJsonObject(slot).has("model")) {
				EModel model = EModel.parse(items.getAsJsonObject(slot).getAsJsonObject("model"));
				if (model != null) models.put(slot, model);
			}
		}
		return new Outfit(Map.copyOf(voxels), petOnGround, List.copyOf(frames), fps, aura, emote, Map.copyOf(models));
	}

	private static List<int[]> parseVoxels(JsonArray array) {
		List<int[]> voxels = new ArrayList<>(array.size());
		for (JsonElement el : array) {
			JsonArray v = el.getAsJsonArray();
			int rgb = Integer.parseInt(v.get(3).getAsString().substring(1), 16);
			voxels.add(new int[] {v.get(0).getAsInt(), v.get(1).getAsInt(), v.get(2).getAsInt(), rgb});
		}
		return voxels;
	}
}
