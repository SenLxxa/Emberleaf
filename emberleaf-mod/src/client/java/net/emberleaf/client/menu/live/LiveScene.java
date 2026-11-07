package net.emberleaf.client.menu.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.emberleaf.client.menu.BlockMesher;
import net.emberleaf.client.pack.PackFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One live menu scene from tools/make_live_scenes.py: real Minecraft blocks with baked light (per face corner),
 * plus everything the menu needs around them (camera path, fog, light shafts, particle emitters, where figures stand).
 * The blocks are drawn with the game's own models and textures, so the active texture packs show up in the menu.
 */
public final class LiveScene {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	public static final List<String> SCENES = List.of("cave", "nether", "overworld", "end");

	public final String name;
	public final float[] camStart;
	public final float[] camEnd;
	public final float[] target;
	public final float fov;
	public final float[] ambient;
	public final float[] fog;
	public final float fogStart;
	public final float fogEnd;
	public final boolean sky;
	public final List<float[]> lights;
	public final List<float[]> rays;
	public final List<Emitter> emitters;
	public final List<float[]> figures;
	/** End crystals floating above pillars (drawn with the real end crystal model). */
	public final List<float[]> crystals;

	private final List<BlockMesher.Placed> blocks;
	private final float[] corners;
	private final int[] faceStart;
	private final float[] centers;
	private final BlockMesher.Tints tints;
	private BlockMesher.@Nullable Mesh mesh;
	private int meshGeneration = Integer.MIN_VALUE;

	public record Emitter(String type, float[] area, float[] at, int count) {}

	private static final Map<String, LiveScene> LOADED = new HashMap<>();
	private static final Set<String> FAILED = new HashSet<>();

	private LiveScene(String name, JsonObject meta, List<BlockMesher.Placed> blocks, float[] corners, int[] faceStart, float[] centers) {
		this.name = name;
		this.blocks = blocks;
		this.corners = corners;
		this.faceStart = faceStart;
		this.centers = centers;
		JsonObject cam = meta.getAsJsonObject("camera");
		this.camStart = floats(cam.getAsJsonArray("start"));
		this.camEnd = floats(cam.getAsJsonArray("end"));
		this.target = floats(cam.getAsJsonArray("target"));
		this.fov = cam.get("fov").getAsFloat();
		this.ambient = floats(meta.getAsJsonArray("ambient"));
		this.fog = floats(meta.getAsJsonArray("fog"));
		this.fogStart = meta.get("fogStart").getAsFloat();
		this.fogEnd = meta.get("fogEnd").getAsFloat();
		this.sky = meta.has("sky") && meta.get("sky").getAsBoolean();
		this.lights = list(meta.getAsJsonArray("lights"));
		this.figures = list(meta.getAsJsonArray("figures"));
		this.crystals = meta.has("crystals") ? list(meta.getAsJsonArray("crystals")) : List.of();
		List<float[]> r = new ArrayList<>();
		for (JsonElement e : meta.getAsJsonArray("rays")) {
			JsonObject o = e.getAsJsonObject();
			float[] from = floats(o.getAsJsonArray("from"));
			float[] to = floats(o.getAsJsonArray("to"));
			float[] col = floats(o.getAsJsonArray("color"));
			r.add(new float[] {from[0], from[1], from[2], to[0], to[1], to[2], o.get("width").getAsFloat(), col[0], col[1], col[2]});
		}
		this.rays = List.copyOf(r);
		List<Emitter> em = new ArrayList<>();
		for (JsonElement e : meta.getAsJsonArray("emitters")) {
			JsonObject o = e.getAsJsonObject();
			em.add(new Emitter(o.get("type").getAsString(), o.has("area") ? floats(o.getAsJsonArray("area")) : null,
				o.has("at") ? floats(o.getAsJsonArray("at")) : null, o.get("count").getAsInt()));
		}
		this.emitters = List.copyOf(em);
		JsonObject t = meta.getAsJsonObject("tints");
		this.tints = new BlockMesher.Tints(color(t, "grass", 0xFF79C05A), color(t, "foliage", 0xFF59AE30), color(t, "water", 0xFF3F76E4));
	}

	public static @Nullable LiveScene get(String name) {
		LiveScene loaded = LOADED.get(name);
		// never touch the resource manager while Minecraft is still (re)loading resources
		if (loaded != null || FAILED.contains(name) || Minecraft.getInstance().gui.overlay() != null) return loaded;
		try {
			loaded = load(name);
			LOADED.put(name, loaded);
		} catch (Exception e) {
			FAILED.add(name);
			LOG.warn("Emberleaf: Menü-Szene {} konnte nicht geladen werden", name, e);
		}
		return loaded;
	}

	/** The quads of the scene with the current block models; rebuilt after every resource reload (e.g. a texture pack was switched). */
	public BlockMesher.@Nullable Mesh mesh() {
		int generation = PackFeatures.generation();
		if (mesh == null || meshGeneration != generation) {
			if (Minecraft.getInstance().gui.overlay() != null) return mesh;
			long started = System.nanoTime();
			mesh = BlockMesher.build(blocks, this::light, tints);
			meshGeneration = generation;
			LOG.info("Emberleaf: Menü-Szene {} gebaut ({} + {} Ecken, {} ms)", name, mesh.solidVertices(), mesh.translucentVertices(),
				(System.nanoTime() - started) / 1_000_000);
		}
		return mesh;
	}

	private void light(int block, int face, float lx, float ly, float lz, float[] out) {
		if (face >= 0) {
			int off = faceStart[block * 6 + face];
			if (off >= 0) {
				BlockMesher.faceLight(corners, off, face, lx, ly, lz, out);
				return;
			}
		}
		if (!Float.isNaN(centers[block * 5])) {
			System.arraycopy(centers, block * 5, out, 0, 5);
			return;
		}
		// a side without baked light (e.g. the inside of a bushy leaf): the average of the block's open faces
		Arrays.fill(out, 0);
		int n = 0;
		for (int f = 0; f < 6; f++) {
			int off = faceStart[block * 6 + f];
			if (off < 0) continue;
			for (int i = 0; i < 20; i++) out[i % 5] += corners[off + i];
			n += 4;
		}
		if (n > 0) {
			for (int i = 0; i < 5; i++) out[i] /= n;
		} else {
			out[0] = 0.6F;
		}
	}

	private static LiveScene load(String name) throws Exception {
		var rm = Minecraft.getInstance().getResourceManager();
		JsonObject meta;
		try (Reader r = new InputStreamReader(rm.getResourceOrThrow(Identifier.fromNamespaceAndPath("emberleaf", "live/" + name + ".json")).open(),
			StandardCharsets.UTF_8)) {
			meta = JsonParser.parseReader(r).getAsJsonObject();
		}
		List<BlockState> palette = new ArrayList<>();
		for (JsonElement e : meta.getAsJsonArray("palette")) palette.add(BlockMesher.parse(e.getAsString()));
		int[] paletteTint = new int[palette.size()];
		if (meta.has("paletteTint")) {
			JsonArray pt = meta.getAsJsonArray("paletteTint");
			for (int i = 0; i < paletteTint.length && i < pt.size(); i++) paletteTint[i] = pt.get(i).isJsonNull() ? 0 : parseColor(pt.get(i).getAsString());
		}
		byte[] bytes;
		try (InputStream in = rm.getResourceOrThrow(Identifier.fromNamespaceAndPath("emberleaf", "live/" + name + ".bin")).open()) {
			bytes = in.readAllBytes();
		}
		ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		int count = buf.getInt();
		List<BlockMesher.Placed> blocks = new ArrayList<>(count);
		int[] faceStart = new int[count * 6];
		Arrays.fill(faceStart, -1);
		float[] centers = new float[count * 5];
		Arrays.fill(centers, Float.NaN);
		float[] corners = new float[Math.max(16, bytes.length * 2)];
		int used = 0;
		for (int i = 0; i < count; i++) {
			int x = buf.getShort();
			int y = buf.getShort();
			int z = buf.getShort();
			int p = Short.toUnsignedInt(buf.getShort());
			int mask = Byte.toUnsignedInt(buf.get());
			int flags = Byte.toUnsignedInt(buf.get());
			for (int f = 0; f < 6; f++) {
				if ((mask & (1 << f)) == 0) continue;
				faceStart[i * 6 + f] = used;
				for (int k = 0; k < 4; k++) used = readLight(buf, corners, used);
			}
			if ((flags & 1) != 0) readLight(buf, centers, i * 5);
			blocks.add(new BlockMesher.Placed(x, y, z, palette.get(p), mask, paletteTint[p]));
		}
		return new LiveScene(name, meta, List.copyOf(blocks), Arrays.copyOf(corners, used), faceStart, centers);
	}

	/** amb, sun (0..1) and block light r, g, b (0..2), stored as bytes. */
	private static int readLight(ByteBuffer buf, float[] out, int at) {
		out[at] = Byte.toUnsignedInt(buf.get()) / 255F;
		out[at + 1] = Byte.toUnsignedInt(buf.get()) / 255F;
		for (int i = 2; i < 5; i++) out[at + i] = Byte.toUnsignedInt(buf.get()) / 127.5F;
		return at + 5;
	}

	private static int color(JsonObject o, String key, int fallback) {
		return o != null && o.has(key) ? parseColor(o.get(key).getAsString()) : fallback;
	}

	private static int parseColor(String hex) {
		return 0xFF000000 | Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
		return out;
	}

	private static List<float[]> list(JsonArray a) {
		List<float[]> out = new ArrayList<>();
		for (JsonElement e : a) out.add(floats(e.getAsJsonArray()));
		return List.copyOf(out);
	}
}
