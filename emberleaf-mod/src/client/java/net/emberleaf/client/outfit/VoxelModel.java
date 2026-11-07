package net.emberleaf.client.outfit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A voxel cosmetic as designed in the launcher (1 voxel = 1 skin pixel, y up, front = +z).
 * Only faces that touch air are kept, so drawing it is cheap.
 */
public final class VoxelModel {
	/** Outward normals of the six faces, in launcher coordinates. */
	public static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
	/** The four corners of each face (unit cube 0..1), counter-clockwise seen from outside. */
	public static final float[][][] CORNERS = {
		{{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},
		{{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}},
		{{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
		{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
		{{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
		{{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}},
	};

	/** One visible face: voxel position, direction index into {@link #DIRS} and its color (0xRRGGBB). */
	public record Face(int x, int y, int z, int dir, int rgb) {}

	private final List<Face> faces;

	private VoxelModel(List<Face> faces) {
		this.faces = faces;
	}

	public List<Face> faces() {
		return faces;
	}

	public static VoxelModel of(List<int[]> voxels) {
		Set<Long> filled = new HashSet<>();
		for (int[] v : voxels) filled.add(key(v[0], v[1], v[2]));
		List<Face> faces = new ArrayList<>();
		for (int[] v : voxels) {
			for (int d = 0; d < 6; d++) {
				int[] n = DIRS[d];
				if (!filled.contains(key(v[0] + n[0], v[1] + n[1], v[2] + n[2]))) {
					faces.add(new Face(v[0], v[1], v[2], d, shade(v[0], v[1], v[2], v[3])));
				}
			}
		}
		return new VoxelModel(List.copyOf(faces));
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
	}

	/** Same tiny brightness noise per voxel as the launcher preview, so both look alike. */
	private static int shade(int x, int y, int z, int rgb) {
		double n = Math.sin(x * 12.9898 + y * 78.233 + z * 37.719) * 43758.5453;
		double k = 0.92 + (n - Math.floor(n)) * 0.12;
		int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k));
		int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k));
		int b = Math.min(255, (int) ((rgb & 0xFF) * k));
		return (r << 16) | (g << 8) | b;
	}
}
