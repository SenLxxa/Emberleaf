package net.emberleaf.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.emberleaf.client.outfit.VoxelModel;

/** Writes the visible faces of a voxel cosmetic into a vertex buffer. */
public final class VoxelRenderer {
	/** Maps a point from launcher coordinates (y up, front +z) into the model part's pixel space. */
	@FunctionalInterface
	public interface PointMap {
		void map(float x, float y, float z, float[] out);
	}

	/** Head part: launcher head-local → Minecraft head part (y down, front -z). */
	public static final PointMap HEAD = (x, y, z, out) -> {
		out[0] = x;
		out[1] = -y;
		out[2] = -z;
	};

	/** Body part: launcher body-local (centered, y -6..6) → Minecraft body part (pivot at the neck). */
	public static final PointMap BODY = (x, y, z, out) -> {
		out[0] = x;
		out[1] = 6 - y;
		out[2] = -z;
	};

	private static final float[][] UV = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};

	private VoxelRenderer() {}

	public static void emit(VertexConsumer buffer, PoseStack.Pose pose, VoxelModel model, PointMap map, boolean mirrored, int light, int overlay) {
		float[] corners = new float[12];
		float[] tmp = new float[3];
		int[] order = mirrored ? new int[] {3, 2, 1, 0} : new int[] {0, 1, 2, 3};
		for (VoxelModel.Face face : model.faces()) {
			float[][] c = VoxelModel.CORNERS[face.dir()];
			for (int i = 0; i < 4; i++) {
				int k = order[i];
				map.map(face.x() + c[k][0], face.y() + c[k][1], face.z() + c[k][2], tmp);
				corners[i * 3] = tmp[0];
				corners[i * 3 + 1] = tmp[1];
				corners[i * 3 + 2] = tmp[2];
			}
			// Normal from the transformed corners, so it is right for every mapping (including mirrored wings)
			float ax = corners[3] - corners[0], ay = corners[4] - corners[1], az = corners[5] - corners[2];
			float bx = corners[6] - corners[0], by = corners[7] - corners[1], bz = corners[8] - corners[2];
			float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len > 0) {
				nx /= len;
				ny /= len;
				nz /= len;
			}
			int color = 0xFF000000 | face.rgb();
			for (int i = 0; i < 4; i++) {
				buffer.addVertex(pose, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2])
					.setColor(color)
					.setUv(UV[i][0], UV[i][1])
					.setOverlay(overlay)
					.setLight(light)
					.setNormal(pose, nx, ny, nz);
			}
		}
	}
}
