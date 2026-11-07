package net.emberleaf.client.menu;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Turns menu blocks into quads with the game's own block models and textures: whatever texture packs are active
 * (3D leaves, flowers, torches …) show up in the main menu exactly like in the world.
 *
 * <p>Vertices come out as {@link #STRIDE} floats: x, y, z, u, v (block atlas), ambient, sun, block light r, g, b,
 * with one ARGB tint per vertex in a parallel int array.
 */
public final class BlockMesher {
	public static final int STRIDE = 10;
	/** Face order of the scene files: +x, -x, +y, -y, +z, -z. */
	public static final Direction[] FACES = {Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH};
	/** The 4 corners of each face (same order as the scene files and tools/make_live_scenes.py). */
	public static final int[][][] CORNERS = {
		{{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}},
		{{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
		{{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
		{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
		{{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
		{{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}},
	};
	/** Minecraft's own face shading (top bright, bottom dark), for light that was not baked per face. */
	private static final float[] SHADE = {0.8F, 0.8F, 1.0F, 0.55F, 0.9F, 0.7F};
	private static final Direction[] QUAD_SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

	private BlockMesher() {}

	/** Where the light of a block comes from: per face corner (baked or computed) and/or one value for the whole block. */
	public interface Light {
		/** amb, sun, r, g, b at a point of block {@code block}; {@code face} is the scene face index (0..5) or -1. */
		void sample(int block, int face, float lx, float ly, float lz, float[] out);
	}

	/** Scene-wide colours for grass, leaves and water (the menu has no biomes). */
	public record Tints(int grass, int foliage, int water) {}

	public static final class Mesh {
		public final float[] solid;
		public final int[] solidTint;
		/** Per quad: true = a small part of a very detailed model (may be left out far away). */
		public final boolean[] solidDetail;
		public final float[] translucent;
		public final int[] translucentTint;
		public final boolean[] translucentDetail;

		Mesh(Builder solid, Builder translucent) {
			this.solid = Arrays.copyOf(solid.v, solid.n * STRIDE);
			this.solidTint = Arrays.copyOf(solid.t, solid.n);
			this.solidDetail = Arrays.copyOf(solid.d, solid.n / 4);
			this.translucent = Arrays.copyOf(translucent.v, translucent.n * STRIDE);
			this.translucentTint = Arrays.copyOf(translucent.t, translucent.n);
			this.translucentDetail = Arrays.copyOf(translucent.d, translucent.n / 4);
		}

		public int solidVertices() {
			return solidTint.length;
		}

		public int translucentVertices() {
			return translucentTint.length;
		}
	}

	private static final class Builder {
		float[] v = new float[4096 * STRIDE];
		int[] t = new int[4096];
		boolean[] d = new boolean[1024];
		int n;

		/** Starts a quad (its 4 vertices follow). */
		void quad(boolean detail) {
			int q = n / 4;
			if (q == d.length) d = Arrays.copyOf(d, d.length * 2);
			d[q] = detail;
		}

		void add(float x, float y, float z, float u, float vv, float[] light, int tint) {
			if (n == t.length) {
				v = Arrays.copyOf(v, v.length * 2);
				t = Arrays.copyOf(t, t.length * 2);
			}
			int o = n * STRIDE;
			v[o] = x;
			v[o + 1] = y;
			v[o + 2] = z;
			v[o + 3] = u;
			v[o + 4] = vv;
			System.arraycopy(light, 0, v, o + 5, 5);
			t[n++] = tint;
		}
	}

	/** One block of a menu scene. {@code faces}: bit i = scene face i is open (and seen). {@code tint}: 0 = the usual colour. */
	public record Placed(int x, int y, int z, BlockState state, int faces, int tint) {}

	/** Parses "minecraft:oak_leaves[persistent=true]" (falls back to air for unknown blocks of other versions). */
	public static BlockState parse(String text) {
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text, false).blockState();
		} catch (CommandSyntaxException | RuntimeException e) {
			return Blocks.AIR.defaultBlockState();
		}
	}

	public static Mesh build(List<Placed> blocks, Light light, Tints tints) {
		Minecraft mc = Minecraft.getInstance();
		BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
		Builder solid = new Builder();
		Builder translucent = new Builder();
		List<BlockStateModelPart> parts = new ArrayList<>();
		List<BakedQuad> quads = new ArrayList<>();
		Set<QuadKey> seen = new HashSet<>();
		float[] l = new float[5];
		STATS = Boolean.getBoolean("emberleaf.meshStats"); // -Demberleaf.meshStats=true: which blocks make the most quads
		for (int i = 0; i < blocks.size(); i++) {
			Placed b = blocks.get(i);
			BlockState state = b.state();
			if (state.isAir()) continue;
			if (state.getBlock() instanceof LiquidBlock) {
				fluid(b, i, light, tints, solid, translucent, l);
				continue;
			}
			BlockStateModel model = models.get(state);
			parts.clear();
			model.collectParts(RandomSource.create(Mth.getSeed(b.x(), b.y(), b.z())), parts);
			seen.clear();
			int[] stat = STATS ? STAT_MAP.computeIfAbsent(state + " / " + model.getClass().getName(), k -> new int[3]) : null;
			if (stat != null) {
				stat[0]++;
				stat[2] += parts.size();
			}
			quads.clear();
			for (BlockStateModelPart part : parts) {
				for (Direction side : QUAD_SIDES) {
					for (BakedQuad q : part.getQuads(side)) {
						// some mods (connected textures) hand out every quad for every side: keep each quad once,
						// and leave out the ones lying flat on a side of the block that is covered
						if (seen.add(key(q)) && !hidden(q, b.faces())) quads.add(q);
					}
				}
			}
			if (stat != null) stat[1] += quads.size();
			// very detailed 3D models (3D flowers, roots …): far away only their biggest parts are drawn
			float detailBelow = quads.size() > DETAIL_QUADS ? detailThreshold(quads) : -1;
			for (BakedQuad q : quads) {
				BakedQuad.MaterialInfo info = q.materialInfo();
				int tint = info.isTinted() ? tint(state, info.tintIndex(), b.tint(), tints) : -1;
				Builder out = info.layer() == ChunkSectionLayer.TRANSLUCENT ? translucent : solid;
				int f = face(q.direction());
				boolean baked = f >= 0 && (b.faces() & (1 << f)) != 0;
				out.quad(detailBelow >= 0 && area(q) < detailBelow);
				for (int k = 0; k < 4; k++) {
					Vector3fc p = q.position(k);
					light.sample(i, baked ? f : -1, p.x(), p.y(), p.z(), l);
					if (!baked && f >= 0) scale(l, SHADE[f]);
					if (info.lightEmission() > 0) glow(l, info.lightEmission() / 15F);
					long uv = q.packedUV(k);
					out.add(b.x() + p.x(), b.y() + p.y(), b.z() + p.z(), UVPair.unpackU(uv), UVPair.unpackV(uv), l, tint);
				}
			}
		}
		if (STATS) {
			STAT_MAP.entrySet().stream().sorted((a, c) -> Integer.compare(c.getValue()[1], a.getValue()[1])).limit(10)
				.forEach(e -> LOG.info("Emberleaf mesh: {} x{} -> {} quads, {} parts", e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]));
			STAT_MAP.clear();
		}
		return new Mesh(solid, translucent);
	}

	/** Models with more quads than this are "detailed": their small parts are left out far away. */
	private static final int DETAIL_QUADS = 40;
	/** How many of the biggest quads of a detailed model are always drawn. */
	private static final int DETAIL_KEEP = 14;

	private static float detailThreshold(List<BakedQuad> quads) {
		float[] areas = new float[quads.size()];
		for (int i = 0; i < areas.length; i++) areas[i] = area(quads.get(i));
		Arrays.sort(areas);
		return areas[areas.length - DETAIL_KEEP];
	}

	private static float area(BakedQuad q) {
		Vector3fc a = q.position(0);
		Vector3fc b = q.position(1);
		Vector3fc c = q.position(2);
		Vector3fc d = q.position(3);
		float ux = c.x() - a.x();
		float uy = c.y() - a.y();
		float uz = c.z() - a.z();
		float vx = d.x() - b.x();
		float vy = d.y() - b.y();
		float vz = d.z() - b.z();
		float cx = uy * vz - uz * vy;
		float cy = uz * vx - ux * vz;
		float cz = ux * vy - uy * vx;
		return (float) Math.sqrt(cx * cx + cy * cy + cz * cz) / 2;
	}

	private static boolean STATS;
	private static final java.util.Map<String, int[]> STAT_MAP = new java.util.HashMap<>();
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("emberleaf");

	/** Water and lava have no block model: their open faces get the fluid's still texture, a little lower on top. */
	private static void fluid(Placed b, int index, Light light, Tints tints, Builder solid, Builder translucent, float[] l) {
		FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(b.state().getFluidState());
		TextureAtlasSprite sprite = model.stillMaterial().sprite();
		boolean water = b.state().is(Blocks.WATER);
		int tint = water ? (b.tint() != 0 ? b.tint() : tints.water()) : -1;
		Builder out = water ? translucent : solid;
		float top = (b.faces() & (1 << 2)) != 0 ? 0.875F : 1F;
		for (int f = 0; f < 6; f++) {
			if ((b.faces() & (1 << f)) == 0) continue;
			int[][] c = CORNERS[f];
			float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
			out.quad(false);
			for (int k = 0; k < 4; k++) {
				float y = c[k][1] == 1 ? top : 0;
				light.sample(index, f, c[k][0], c[k][1], c[k][2], l);
				if (!water) glow(l, 1F);
				float v = f == 2 || f == 3 ? uv[k][1] : (c[k][1] == 1 ? 1 - top : 1);
				out.add(b.x() + c[k][0], b.y() + y, b.z() + c[k][2], sprite.getU(uv[k][0]), sprite.getV(v), l, tint);
			}
		}
	}

	private static int tint(BlockState state, int index, int override, Tints tints) {
		if (override != 0) return override;
		Block block = state.getBlock();
		if (block == Blocks.GRASS_BLOCK || block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS || block == Blocks.FERN
			|| block == Blocks.LARGE_FERN || block == Blocks.SUGAR_CANE || block == Blocks.POTTED_FERN) return tints.grass();
		if (block == Blocks.VINE || (state.is(BlockTags.LEAVES) && block != Blocks.BIRCH_LEAVES && block != Blocks.SPRUCE_LEAVES)) return tints.foliage();
		BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, index);
		return source == null ? -1 : 0xFF000000 | source.color(state);
	}

	/** Position and texture of a quad: two equal keys draw the same thing. */
	private record QuadKey(float x0, float y0, float z0, float x2, float y2, float z2, long uv0, long uv2, TextureAtlasSprite sprite) {}

	private static QuadKey key(BakedQuad q) {
		Vector3fc a = q.position(0);
		Vector3fc c = q.position(2);
		return new QuadKey(a.x(), a.y(), a.z(), c.x(), c.y(), c.z(), q.packedUV(0), q.packedUV(2), q.materialInfo().sprite());
	}

	/** A quad lying flat on a side of the block (like a cube face) whose side is covered by a neighbour or not seen. */
	private static boolean hidden(BakedQuad q, int faces) {
		int f = face(q.direction());
		if (f < 0 || (faces & (1 << f)) != 0) return false;
		Direction d = q.direction();
		float plane = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1F : 0F;
		for (int k = 0; k < 4; k++) {
			Vector3fc p = q.position(k);
			float v = switch (d.getAxis()) {
				case X -> p.x();
				case Y -> p.y();
				case Z -> p.z();
			};
			if (Math.abs(v - plane) > 1.0E-4F) return false;
		}
		return true;
	}

	/** Scene face index (0..5) of a direction, -1 for none. */
	public static int face(@Nullable Direction d) {
		if (d == null) return -1;
		return switch (d) {
			case EAST -> 0;
			case WEST -> 1;
			case UP -> 2;
			case DOWN -> 3;
			case SOUTH -> 4;
			case NORTH -> 5;
		};
	}

	private static void scale(float[] l, float k) {
		for (int i = 0; i < 5; i++) l[i] *= k;
	}

	/** Light-giving parts (lava, glowstone, the flame of a torch …) are lit by themselves. */
	private static void glow(float[] l, float amount) {
		for (int i = 2; i < 5; i++) l[i] = Math.max(l[i], amount);
	}

	/** Bilinear light on a cube face from its 4 corner values ({@code corners}: 4 x amb, sun, r, g, b). */
	public static void faceLight(float[] corners, int offset, int face, float lx, float ly, float lz, float[] out) {
		float x = Mth.clamp(lx, 0, 1);
		float y = Mth.clamp(ly, 0, 1);
		float z = Mth.clamp(lz, 0, 1);
		Arrays.fill(out, 0);
		int[][] c = CORNERS[face];
		for (int k = 0; k < 4; k++) {
			float w = 1;
			if (face != 0 && face != 1) w *= c[k][0] == 1 ? x : 1 - x;
			if (face != 2 && face != 3) w *= c[k][1] == 1 ? y : 1 - y;
			if (face != 4 && face != 5) w *= c[k][2] == 1 ? z : 1 - z;
			for (int i = 0; i < 5; i++) out[i] += corners[offset + k * 5 + i] * w;
		}
	}
}
