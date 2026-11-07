package net.emberleaf.client.menu.live;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Perspective camera for the live menu scenes. Projects world points into the menu's GUI space:
 * x/y in GUI pixels around the centre of the view (y down) and the distance from the camera.
 */
public final class LiveCamera {
	public static final float NEAR = 0.2F;
	public final float ex;
	public final float ey;
	public final float ez;
	private final Matrix4f view;
	private final float focal;
	private final Vector3f tmp = new Vector3f();

	public LiveCamera(float ex, float ey, float ez, float tx, float ty, float tz, float fovDeg, float halfHeight) {
		this.ex = ex;
		this.ey = ey;
		this.ez = ez;
		this.view = new Matrix4f().lookAt(ex, ey, ez, tx, ty, tz, 0, 1, 0);
		this.focal = (float) (1 / Math.tan(Math.toRadians(fovDeg) / 2)) * halfHeight;
	}

	/** out = {x, y, depth}; false when the point is behind the camera. */
	public boolean project(float x, float y, float z, float[] out) {
		view.transformPosition(x, y, z, tmp);
		float depth = -tmp.z;
		if (depth < NEAR) return false;
		out[0] = tmp.x * focal / depth;
		out[1] = -tmp.y * focal / depth;
		out[2] = depth;
		return true;
	}

	/** Pixels per block at this distance. */
	public float scaleAt(float depth) {
		return focal / Math.max(depth, NEAR);
	}

	public Matrix4f view() {
		return view;
	}

	/** Depth (blocks) to the menu's z: nearer = smaller, inside the GUI's ±1000/guiScale range. */
	public static float poseZ(float depth) {
		return -230 + Math.min(Math.max(depth, 0), 150) * 3;
	}
}
