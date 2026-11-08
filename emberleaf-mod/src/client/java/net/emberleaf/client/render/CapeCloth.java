package net.emberleaf.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.emberleaf.client.model.EModelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Cloth physics for capes, the same as in the Emberleaf cape studio: the cape hangs on a spine of segments that
 * lag behind the wearer's movement (the hem more than the shoulders), swing in a travelling wave and ripple across
 * their width. Running lifts the cape, jumping pushes it down, falling blows it up.
 * Coordinates are Blockbench pixels: y up, the wearer looks north (-z), so the back is +z.
 */
public final class CapeCloth {
	public static final int SEGMENTS = 12;
	/** Key for callers without a stable entity (pets, previews): the cape follows the target shape directly. */
	public static final long NO_SIM = Long.MIN_VALUE;
	private static final Map<Long, State> STATES = new HashMap<>();
	private static final long FORGET_NANOS = 10_000_000_000L;

	private static final class State {
		final float[] theta = new float[SEGMENTS];
		long lastNanos;
		boolean fresh = true;
	}

	/** The cape's shape for this frame, relative to its top edge (the pivot). */
	public static final class Shape {
		final float[] theta = new float[SEGMENTS];
		final float[] sin = new float[SEGMENTS];
		final float[] cos = new float[SEGMENTS];
		final float[] py = new float[SEGMENTS + 1];
		final float[] pz = new float[SEGMENTS + 1];
		float length;
		float seg;
		float time;
		float rippleAmp;
		float rippleFreq;

		/**
		 * Moves a rest-pose point (and its normal) onto the bent cape. origin = the cape's pivot, p and n are changed in place.
		 * The distance below the pivot picks the place on the spine, the distance behind it stays the cloth's thickness.
		 */
		public void apply(Vector3f origin, Vector3f p, Vector3f n) {
			float d = origin.y - p.y;
			float off = p.z - origin.z;
			float fk = d / seg;
			int k = Mth.clamp(Mth.floor(fk), 0, SEGMENTS - 1);
			float along = d - k * seg;
			float sy = py[k] - along * cos[k];
			float sz = pz[k] + along * sin[k];
			// the cloth's direction blends between neighbouring segments so the bend looks round
			float c = fk - 0.5F;
			int k0 = Mth.clamp(Mth.floor(c), 0, SEGMENTS - 1);
			int k1 = Math.min(k0 + 1, SEGMENTS - 1);
			float f = c < 0 ? 0 : Mth.clamp(c - Mth.floor(c), 0, 1);
			float th = Mth.lerp(f, theta[k0], theta[k1]);
			float s01 = Mth.clamp(d / length, 0, 1);
			off += rippleAmp * s01 * Mth.sin(p.x * 0.55F + time * rippleFreq - d * 0.3F);
			float st = Mth.sin(th);
			float ct = Mth.cos(th);
			p.set(p.x, origin.y + sy + off * st, origin.z + sz + off * ct);
			float ny = n.y * ct + n.z * st;
			float nz = -n.y * st + n.z * ct;
			n.set(n.x, ny, nz);
		}
	}

	private CapeCloth() {}

	/**
	 * Solves the cape for this frame. key identifies the wearer and slot (or {@link #NO_SIM}); length is the cape's
	 * height in pixels. motion.capeLean (degrees) is how far the movement pushes the cape back.
	 */
	public static Shape solve(long key, float length, EModelRenderer.Motion m) {
		Shape s = new Shape();
		s.length = Math.max(1, length);
		s.seg = s.length / SEGMENTS;
		s.time = m.time();
		float walk = Mth.clamp(m.walk(), 0, 1);
		float lean = Mth.clamp(6 + m.capeLean(), 2, 82);
		float base = (float) Math.toRadians(lean) + (m.crouching() ? 0.3F : 0);
		float waveA = (float) Math.toRadians(3 + 9 * walk + (m.airborne() ? 7 : 0));
		float waveF = 1.6F + 5.5F * walk + (m.airborne() ? 3 : 0);
		s.rippleAmp = 0.16F + 0.42F * walk + (m.airborne() ? 0.3F : 0);
		s.rippleFreq = 2.2F + 6 * walk + (m.airborne() ? 2 : 0);

		long now = System.nanoTime();
		State st = null;
		float dt = -1;
		if (key != NO_SIM) {
			st = STATES.computeIfAbsent(key, k -> new State());
			if (!st.fresh) dt = Math.min(0.1F, Math.max(0, (now - st.lastNanos) / 1e9F));
		}
		for (int k = 0; k < SEGMENTS; k++) {
			float s01 = (k + 0.5F) / SEGMENTS;
			float target = base * (0.5F + 0.9F * s01) + waveA * s01 * Mth.sin(s.time * waveF - k * 0.42F);
			target = Mth.clamp(target, 0.02F, 1.45F);
			float theta;
			if (st == null) {
				theta = target;
			} else {
				if (dt < 0) st.theta[k] = target;
				else st.theta[k] += (target - st.theta[k]) * (1 - (float) Math.exp(-dt * (12 - 7 * s01)));
				theta = st.theta[k];
			}
			s.theta[k] = theta;
			s.sin[k] = Mth.sin(theta);
			s.cos[k] = Mth.cos(theta);
			s.py[k + 1] = s.py[k] - s.seg * s.cos[k];
			s.pz[k + 1] = s.pz[k] + s.seg * s.sin[k];
		}
		if (st != null) {
			st.lastNanos = now;
			st.fresh = false;
			if (STATES.size() > 64) forgetOld(now);
		}
		return s;
	}

	private static void forgetOld(long now) {
		Iterator<State> it = STATES.values().iterator();
		while (it.hasNext()) if (now - it.next().lastNanos > FORGET_NANOS) it.remove();
	}

	/**
	 * Emits one quad, cut into a grid fine enough to bend, onto the cape.
	 * c = 4 corners (x, y, z, u, v) in rest space, ordered top-left, bottom-left, bottom-right, top-right as seen from outside.
	 * toWorld turns the bent rest space into the space of pose (null = identity).
	 */
	public static void emitGrid(VertexConsumer buf, PoseStack.Pose pose, Matrix4f toWorld, Shape s, Vector3f origin, float[] c,
		float nx, float ny, float nz, int color, int light, int overlay) {
		// a runs top-left -> top-right, b runs top-left -> bottom-left
		int na = cuts(c[15] - c[0], c[16] - c[1], s.seg);
		int nb = cuts(c[5] - c[0], c[6] - c[1], s.seg);
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		for (int j = 0; j < nb; j++) {
			float b0 = (float) j / nb;
			float b1 = (float) (j + 1) / nb;
			for (int i = 0; i < na; i++) {
				float a0 = (float) i / na;
				float a1 = (float) (i + 1) / na;
				vertex(buf, pose, toWorld, s, origin, c, a0, b0, nx, ny, nz, color, light, overlay, p, n);
				vertex(buf, pose, toWorld, s, origin, c, a0, b1, nx, ny, nz, color, light, overlay, p, n);
				vertex(buf, pose, toWorld, s, origin, c, a1, b1, nx, ny, nz, color, light, overlay, p, n);
				vertex(buf, pose, toWorld, s, origin, c, a1, b0, nx, ny, nz, color, light, overlay, p, n);
			}
		}
	}

	private static int cuts(float dx, float dy, float seg) {
		return Mth.clamp((int) Math.ceil(Math.max(Math.abs(dy) / seg, Math.abs(dx) / 2.5F) - 1e-3F), 1, 16);
	}

	private static void vertex(VertexConsumer buf, PoseStack.Pose pose, Matrix4f toWorld, Shape s, Vector3f origin, float[] c,
		float a, float b, float nx, float ny, float nz, int color, int light, int overlay, Vector3f p, Vector3f n) {
		// corners: 0 = TL, 1 = BL, 2 = BR, 3 = TR
		float[] v = new float[5];
		for (int e = 0; e < 5; e++) {
			float top = c[e] + (c[15 + e] - c[e]) * a;
			float bottom = c[5 + e] + (c[10 + e] - c[5 + e]) * a;
			v[e] = top + (bottom - top) * b;
		}
		p.set(v[0], v[1], v[2]);
		n.set(nx, ny, nz);
		s.apply(origin, p, n);
		if (toWorld != null) {
			toWorld.transformPosition(p);
			toWorld.transformDirection(n);
		}
		n.normalize();
		buf.addVertex(pose, p.x, p.y, p.z).setColor(color).setUv(v[3], v[4]).setOverlay(overlay).setLight(light).setNormal(pose, n.x, n.y, n.z);
	}

	// ---------- Bild-Capes (64x32, wie die Vanilla-Cape-Textur) ----------

	private static final Vector3f IMAGE_PIVOT = new Vector3f(0, 24, 2);

	/** Vanilla cape box (10x16x1) in Blockbench space behind the shoulders, with the standard 64x32 cape layout. */
	private static final float[][] IMAGE_FACES = {
		// outside (seen from behind): texture (1,1)-(11,17)
		{-5, 24, 3, 1, 1, -5, 8, 3, 1, 17, 5, 8, 3, 11, 17, 5, 24, 3, 11, 1, 0, 0, 1},
		// inside: (12,1)-(22,17)
		{5, 24, 2, 12, 1, 5, 8, 2, 12, 17, -5, 8, 2, 22, 17, -5, 24, 2, 22, 1, 0, 0, -1},
		// side edges and hem
		{5, 24, 3, 11, 1, 5, 8, 3, 11, 17, 5, 8, 2, 12, 17, 5, 24, 2, 12, 1, 1, 0, 0},
		{-5, 24, 2, 0, 1, -5, 8, 2, 0, 17, -5, 8, 3, 1, 17, -5, 24, 3, 1, 1, -1, 0, 0},
		{-5, 8, 3, 11, 0, -5, 8, 2, 11, 1, 5, 8, 2, 21, 1, 5, 8, 3, 21, 0, 0, -1, 0},
		{-5, 24, 2, 1, 0, -5, 24, 3, 1, 1, 5, 24, 3, 11, 1, 5, 24, 2, 11, 0, 0, 1, 0},
	};

	/**
	 * Draws an image cape as bending cloth. pose must already be in Blockbench space of the body
	 * (body.translateAndRotate + {@link EModelRenderer#toPartSpace}).
	 */
	public static void submitImageCape(PoseStack pose, SubmitNodeCollector collector, Identifier texture, int light, long key,
		EModelRenderer.Motion motion) {
		Shape shape = solve(key, 16, motion);
		Matrix4f sway = new Matrix4f().translate(IMAGE_PIVOT).rotateZ((float) Math.toRadians(motion.capeSide()))
			.translate(-IMAGE_PIVOT.x, -IMAGE_PIVOT.y, -IMAGE_PIVOT.z);
		collector.submitCustomGeometry(pose, RenderTypes.entitySolid(texture), (p, buf) -> {
			float[] c = new float[20];
			for (float[] f : IMAGE_FACES) {
				for (int k = 0; k < 4; k++) {
					c[k * 5] = f[k * 5];
					c[k * 5 + 1] = f[k * 5 + 1];
					c[k * 5 + 2] = f[k * 5 + 2];
					c[k * 5 + 3] = f[k * 5 + 3] / 64F;
					c[k * 5 + 4] = f[k * 5 + 4] / 32F;
				}
				emitGrid(buf, p, sway, shape, IMAGE_PIVOT, c, f[20], f[21], f[22], -1, light, OverlayTexture.NO_OVERLAY);
			}
		});
	}
}
