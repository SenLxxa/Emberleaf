package net.emberleaf.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.emberleaf.client.pack.PackFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The floating island from tools/make_island.py (the same one the launcher shows), built from real Minecraft blocks:
 * the game's own block models and textures (with the active texture packs), soft corner shadows per face.
 */
public final class IslandData {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	public static final List<String> SCENES = List.of("autumn", "nether", "end", "cave");

	private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
	private static final float[] FACE_SHADE = {0.82F, 0.82F, 1.0F, 0.6F, 0.82F, 0.82F};
	private static final float[] AO = {1.0F, 0.82F, 0.68F, 0.56F};

	/** Island block types -> real blocks (tint 0 = the block's usual colour). */
	private record Kind(String state, int tint, boolean glows, boolean seeThrough) {}

	private static final Map<String, Kind> KINDS = new HashMap<>();

	private static void kind(String name, String state) {
		KINDS.put(name, new Kind(state, 0, false, false));
	}

	static {
		kind("grass", "minecraft:grass_block[snowy=false]");
		kind("dirt", "minecraft:dirt");
		kind("stone", "minecraft:stone");
		kind("stone_dark", "minecraft:deepslate[axis=y]");
		kind("moss", "minecraft:mossy_cobblestone");
		kind("path", "minecraft:dirt_path");
		kind("log", "minecraft:oak_log[axis=y]");
		// autumn trees: real oak leaves, coloured like autumn
		KINDS.put("leaves_orange", new Kind("minecraft:oak_leaves[persistent=true]", 0xFFE3862E, false, true));
		KINDS.put("leaves_red", new Kind("minecraft:oak_leaves[persistent=true]", 0xFFC9472D, false, true));
		KINDS.put("leaves_yellow", new Kind("minecraft:oak_leaves[persistent=true]", 0xFFE4B53A, false, true));
		kind("netherrack", "minecraft:netherrack");
		kind("crimson", "minecraft:crimson_nylium");
		kind("crimson_stem", "minecraft:crimson_stem[axis=y]");
		kind("wart", "minecraft:nether_wart_block");
		KINDS.put("shroomlight", new Kind("minecraft:shroomlight", 0, true, false));
		KINDS.put("lava", new Kind("minecraft:lava[level=0]", 0, true, true));
		kind("blackstone", "minecraft:blackstone");
		KINDS.put("magma", new Kind("minecraft:magma_block", 0, true, false));
		kind("end_stone", "minecraft:end_stone");
		kind("purpur", "minecraft:purpur_block");
		KINDS.put("chorus", new Kind("minecraft:chorus_plant", 0, false, true));
		KINDS.put("chorus_flower", new Kind("minecraft:chorus_flower[age=2]", 0, false, true));
		kind("obsidian", "minecraft:obsidian");
		kind("deepslate", "minecraft:deepslate[axis=y]");
		KINDS.put("amethyst", new Kind("minecraft:amethyst_block", 0, false, false));
		kind("moss_block", "minecraft:moss_block");
		kind("glow_moss", "minecraft:moss_block");
		KINDS.put("dripstone", new Kind("minecraft:pointed_dripstone", 0, false, true));
		// small things standing on the island
		kind("flower_red", "minecraft:poppy");
		kind("flower_yellow", "minecraft:dandelion");
		kind("mushroom", "minecraft:red_mushroom");
		kind("tall_grass", "minecraft:short_grass");
		kind("crimson_roots", "minecraft:crimson_roots");
		KINDS.put("end_rod", new Kind("minecraft:end_rod[facing=up]", 0, true, true));
		kind("glow_berries", "minecraft:flowering_azalea");
		KINDS.put("torch", new Kind("minecraft:torch", 0, true, true));
		KINDS.put("amethyst_bud", new Kind("minecraft:medium_amethyst_bud[facing=up]", 0, true, true));
	}

	private static final Map<String, IslandData> LOADED = new HashMap<>();
	private static final Set<String> FAILED = new HashSet<>();

	public final String scene;
	public final float[] player;
	public final float[] fox;
	public final List<float[]> friends;
	/** End crystals (the foot of the model), drawn with the real end crystal model. */
	public final List<float[]> crystals;

	private final List<BlockMesher.Placed> blocks;
	/** Per block: 6 faces x 4 corners of shade (NaN = no face), then whether it glows. */
	private final float[] shade;
	private final boolean[] glows;
	private final BlockMesher.Tints tints;
	private BlockMesher.@Nullable Mesh mesh;
	private int meshGeneration = Integer.MIN_VALUE;

	private IslandData(String scene, List<BlockMesher.Placed> blocks, float[] shade, boolean[] glows, BlockMesher.Tints tints, float[] player,
		float[] fox, List<float[]> friends, List<float[]> crystals) {
		this.scene = scene;
		this.blocks = blocks;
		this.shade = shade;
		this.glows = glows;
		this.tints = tints;
		this.player = player;
		this.fox = fox;
		this.friends = friends;
		this.crystals = crystals;
	}

	public static @Nullable IslandData get(String scene) {
		// never touch the resource manager while Minecraft is still (re)loading resources: that can lock up the game
		IslandData loaded = LOADED.get(scene);
		if (loaded != null || FAILED.contains(scene) || Minecraft.getInstance().gui.overlay() != null) return loaded;
		try {
			loaded = load(scene);
			LOADED.put(scene, loaded);
		} catch (Exception e) {
			FAILED.add(scene);
			LOG.warn("Emberleaf: Insel {} konnte nicht geladen werden", scene, e);
		}
		return loaded;
	}

	/** The island's quads with the current block models; rebuilt after every resource reload. */
	public BlockMesher.@Nullable Mesh mesh() {
		int generation = PackFeatures.generation();
		if (mesh == null || meshGeneration != generation) {
			if (Minecraft.getInstance().gui.overlay() != null) return mesh;
			mesh = BlockMesher.build(blocks, this::light, tints);
			meshGeneration = generation;
		}
		return mesh;
	}

	/** Island light: ambient = corner shadow x face shade; glowing blocks also give "block light". */
	private void light(int block, int face, float lx, float ly, float lz, float[] out) {
		float amb = 1F;
		if (face >= 0 && !Float.isNaN(shade[block * 24 + face * 4])) {
			float[] c = new float[20];
			for (int k = 0; k < 4; k++) c[k * 5] = shade[block * 24 + face * 4 + k];
			BlockMesher.faceLight(c, 0, face, lx, ly, lz, out);
			amb = out[0];
		}
		out[0] = amb;
		out[1] = 0;
		float g = glows[block] ? 1F : 0F;
		out[2] = g;
		out[3] = g;
		out[4] = g;
	}

	private static IslandData load(String scene) throws Exception {
		JsonObject data;
		Identifier json = Identifier.fromNamespaceAndPath("emberleaf", "island/" + scene + ".json");
		try (Reader r = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResourceOrThrow(json).open(), StandardCharsets.UTF_8)) {
			data = JsonParser.parseReader(r).getAsJsonObject();
		}
		List<BlockMesher.Placed> blocks = new ArrayList<>();
		List<Float> shade = new ArrayList<>();
		List<Boolean> glows = new ArrayList<>();
		List<float[]> crystals = new ArrayList<>();
		addIsland(data.getAsJsonArray("blocks"), data.getAsJsonArray("props"), 0, 0, 0, blocks, shade, glows, crystals);
		for (JsonElement el : data.getAsJsonArray("small_islands")) {
			JsonObject s = el.getAsJsonObject();
			JsonArray o = s.getAsJsonArray("offset");
			addIsland(s.getAsJsonArray("blocks"), s.getAsJsonArray("props"), o.get(0).getAsInt(), o.get(1).getAsInt(), o.get(2).getAsInt(),
				blocks, shade, glows, crystals);
		}
		float[] shadeArr = new float[shade.size()];
		for (int i = 0; i < shadeArr.length; i++) shadeArr[i] = shade.get(i);
		boolean[] glowArr = new boolean[glows.size()];
		for (int i = 0; i < glowArr.length; i++) glowArr[i] = glows.get(i);
		List<float[]> friends = new ArrayList<>();
		if (data.has("friends")) for (JsonElement f : data.getAsJsonArray("friends")) friends.add(ints(f.getAsJsonArray()));
		float[] fox = data.has("fox") && data.get("fox").isJsonArray() ? ints(data.getAsJsonArray("fox")) : null;
		// autumn: warm, a little golden grass; the other scenes keep the usual colours
		BlockMesher.Tints tints = scene.equals("autumn") ? new BlockMesher.Tints(0xFF9DB653, 0xFFE3862E, 0xFF3F76E4)
			: new BlockMesher.Tints(0xFF79C05A, 0xFF59AE30, 0xFF3F76E4);
		return new IslandData(scene, List.copyOf(blocks), shadeArr, glowArr, tints, ints(data.getAsJsonArray("player")), fox, List.copyOf(friends),
			List.copyOf(crystals));
	}

	private static void addIsland(JsonArray blockList, JsonArray propList, int ox, int oy, int oz, List<BlockMesher.Placed> out, List<Float> shade,
		List<Boolean> glows, List<float[]> crystals) {
		Map<Long, String> kinds = new HashMap<>();
		for (JsonElement el : blockList) {
			JsonArray b = el.getAsJsonArray();
			kinds.put(key(b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt()), b.get(3).getAsString());
		}
		// the glowing crystal cluster on the end pillar becomes a real end crystal
		List<int[]> crystal = new ArrayList<>();
		for (JsonElement el : blockList) {
			JsonArray b = el.getAsJsonArray();
			if (b.get(3).getAsString().equals("crystal")) crystal.add(new int[] {b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt()});
		}
		if (!crystal.isEmpty()) {
			float cx = 0;
			float cz = 0;
			int minY = Integer.MAX_VALUE;
			for (int[] c : crystal) {
				cx += c[0];
				cz += c[2];
				minY = Math.min(minY, c[1]);
			}
			crystals.add(new float[] {cx / crystal.size() + 0.5F + ox, minY - 1 + oy, cz / crystal.size() + 0.5F + oz});
		}
		for (JsonElement el : blockList) {
			JsonArray b = el.getAsJsonArray();
			int x = b.get(0).getAsInt();
			int y = b.get(1).getAsInt();
			int z = b.get(2).getAsInt();
			String name = b.get(3).getAsString();
			Kind kind = KINDS.get(name);
			if (kind == null) continue;
			int mask = 0;
			float[] faceShade = new float[24];
			java.util.Arrays.fill(faceShade, Float.NaN);
			for (int f = 0; f < 6; f++) {
				int[] d = DIRS[f];
				String nb = kinds.get(key(x + d[0], y + d[1], z + d[2]));
				Kind nk = nb == null ? null : KINDS.get(nb);
				boolean open;
				if (nk == null) open = true;
				else if (nb.equals(name)) open = kind.seeThrough() && !name.startsWith("leaves") && !name.equals("lava"); // thin plants keep their sides
				else open = nk.seeThrough() || kind.seeThrough();
				if (!open) continue;
				mask |= 1 << f;
				for (int k = 0; k < 4; k++) {
					int[] c = BlockMesher.CORNERS[f][k];
					int sx = c[0] == 1 ? 1 : -1;
					int sy = c[1] == 1 ? 1 : -1;
					int sz = c[2] == 1 ? 1 : -1;
					int occ = 0;
					if (d[0] != 0) occ = count(kinds, x + d[0], y + sy, z, x + d[0], y, z + sz, x + d[0], y + sy, z + sz);
					if (d[1] != 0) occ = count(kinds, x + sx, y + d[1], z, x, y + d[1], z + sz, x + sx, y + d[1], z + sz);
					if (d[2] != 0) occ = count(kinds, x + sx, y, z + d[2], x, y + sy, z + d[2], x + sx, y + sy, z + d[2]);
					faceShade[f * 4 + k] = AO[occ] * FACE_SHADE[f];
				}
			}
			if (mask == 0) continue;
			BlockState state = BlockMesher.parse(stateFor(name, kind, kinds, x, y, z));
			out.add(new BlockMesher.Placed(x + ox, y + oy, z + oz, state, mask, kind.tint()));
			for (float v : faceShade) shade.add(v);
			glows.add(kind.glows());
		}
		for (JsonElement el : propList) {
			JsonArray p = el.getAsJsonArray();
			Kind kind = KINDS.get(p.get(3).getAsString());
			if (kind == null) continue;
			out.add(new BlockMesher.Placed(p.get(0).getAsInt() + ox, p.get(1).getAsInt() + oy, p.get(2).getAsInt() + oz,
				BlockMesher.parse(kind.state()), 0, kind.tint()));
			for (int i = 0; i < 24; i++) shade.add(Float.NaN);
			glows.add(kind.glows());
		}
	}

	/** Chorus plants connect to their neighbours; dripstone below the island gets thicker towards the top. */
	private static String stateFor(String name, Kind kind, Map<Long, String> kinds, int x, int y, int z) {
		if (name.equals("chorus")) {
			return "minecraft:chorus_plant[up=" + chorus(kinds, x, y + 1, z) + ",down=" + (chorus(kinds, x, y - 1, z) || kinds.containsKey(key(x, y - 1, z)))
				+ ",north=" + chorus(kinds, x, y, z - 1) + ",south=" + chorus(kinds, x, y, z + 1) + ",east=" + chorus(kinds, x + 1, y, z)
				+ ",west=" + chorus(kinds, x - 1, y, z) + "]";
		}
		if (name.equals("dripstone")) {
			boolean below = "dripstone".equals(kinds.get(key(x, y - 1, z)));
			boolean above = "dripstone".equals(kinds.get(key(x, y + 1, z)));
			String thickness = !below ? "tip" : "dripstone".equals(kinds.get(key(x, y - 2, z))) ? (above ? "middle" : "base") : "frustum";
			return "minecraft:pointed_dripstone[vertical_direction=down,thickness=" + thickness + "]";
		}
		return kind.state();
	}

	private static boolean chorus(Map<Long, String> kinds, int x, int y, int z) {
		String k = kinds.get(key(x, y, z));
		return "chorus".equals(k) || "chorus_flower".equals(k);
	}

	private static int count(Map<Long, String> kinds, int ax, int ay, int az, int bx, int by, int bz, int cx, int cy, int cz) {
		return (solid(kinds, ax, ay, az) ? 1 : 0) + (solid(kinds, bx, by, bz) ? 1 : 0) + (solid(kinds, cx, cy, cz) ? 1 : 0);
	}

	private static boolean solid(Map<Long, String> kinds, int x, int y, int z) {
		String k = kinds.get(key(x, y, z));
		if (k == null) return false;
		Kind kind = KINDS.get(k);
		return kind != null && !kind.seeThrough();
	}

	private static long key(int x, int y, int z) {
		return ((long) (x + 512) << 22) | ((long) (y + 512) << 11) | (z + 512);
	}

	private static float[] ints(JsonArray a) {
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}
}
