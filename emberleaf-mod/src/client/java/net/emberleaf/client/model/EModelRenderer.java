package net.emberleaf.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.emberleaf.client.render.CapeCloth;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws an {@link EModel}: plays its own Blockbench animation if it has one, and adds Emberleaf's automatic
 * movement by bone name (wings flap, capes swing, tails wag, ears twitch, pet legs walk, "glow" parts shine).
 */
public final class EModelRenderer {
	private static final int FULL_BRIGHT = 0xF000F0;

	/** How the wearer moves right now. walk 0..1, cape angles in degrees. */
	public record Motion(float time, float walk, float walkPos, boolean airborne, boolean crouching, float capeLean, float capeSide,
		boolean sitting) {
		public static Motion idle(float time) {
			return new Motion(time, 0, 0, false, false, 0, 0, false);
		}
	}

	private EModelRenderer() {}

	/** Blockbench pixels (feet at 0, front -z) into a vanilla model part's space (pivot at the neck, y down). */
	public static void toPartSpace(PoseStack pose) {
		pose.scale(-1 / 16F, -1 / 16F, 1 / 16F);
		pose.translate(0, -24, 0);
	}

	public static void submit(EModel model, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, Motion motion) {
		submit(model, pose, collector, light, overlay, motion, CapeCloth.NO_SIM);
	}

	/**
	 * simKey identifies the wearer and slot, so a cape keeps its swing from frame to frame ({@link CapeCloth#NO_SIM} = no memory).
	 * Cape bones (and everything hanging on them) bend like cloth; glowing parts are drawn emissive and shimmer softly.
	 */
	public static void submit(EModel model, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, Motion motion, long simKey) {
		Identifier texture = model.texture();
		if (texture == null) return;
		CapeInfo cape = capeInfo(model);
		Matrix4f[] bones = pose(model, motion, cape);
		CapeCloth.Shape cloth = cape.hasCape() ? CapeCloth.solve(simKey, cape.length(), motion) : null;
		float shimmer = 0.84F + 0.16F * Mth.sin(motion.time() * 3.1F);
		int g = (int) (shimmer * 255);
		int glowColor = ARGB.color(255, g, g, g);
		collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(texture),
			(p, buf) -> emit(model, bones, cape, cloth, p, buf, light, overlay, false, -1));
		if (cape.hasGlow()) {
			collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(texture),
				(p, buf) -> emit(model, bones, cape, cloth, p, buf, FULL_BRIGHT, overlay, true, glowColor));
		}
	}

	/** Which bones hang on a cape (index of the topmost cape bone above them, or -1), how long the cape is, whether anything glows. */
	private record CapeInfo(int[] roots, float length, boolean hasCape, boolean hasGlow) {}

	private static final Map<EModel, CapeInfo> CAPE_INFO = new java.util.WeakHashMap<>();

	private static CapeInfo capeInfo(EModel model) {
		return CAPE_INFO.computeIfAbsent(model, m -> {
			List<EModel.Bone> bones = m.bones;
			int[] roots = new int[bones.size()];
			boolean hasCape = false;
			boolean hasGlow = false;
			for (int i = 0; i < bones.size(); i++) {
				EModel.Bone bone = bones.get(i);
				int parent = bone.parent() >= 0 && bone.parent() < i ? bone.parent() : -1;
				roots[i] = parent >= 0 && roots[parent] >= 0 ? roots[parent] : bone.kind().equals("cape") ? i : -1;
				hasCape |= roots[i] >= 0;
				hasGlow |= bone.glow();
			}
			// height of the cape below its pivot, from the rest pose
			float length = 1;
			for (int i = 0; i < bones.size(); i++) {
				if (roots[i] < 0) continue;
				float top = bones.get(roots[i]).origin().y;
				float[] q = bones.get(i).quads();
				for (int j = 0; j + 23 <= q.length; j += 23) {
					for (int k = 0; k < 4; k++) length = Math.max(length, top - q[j + k * 5 + 1]);
				}
			}
			return new CapeInfo(roots, length, hasCape, hasGlow);
		});
	}

	private static void emit(EModel model, Matrix4f[] mats, CapeInfo cape, CapeCloth.Shape cloth, PoseStack.Pose pose, VertexConsumer buf,
		int light, int overlay, boolean glowPass, int color) {
		Vector3f v = new Vector3f();
		Vector3f n = new Vector3f();
		float[] c = new float[20];
		List<EModel.Bone> bones = model.bones;
		for (int b = 0; b < bones.size(); b++) {
			EModel.Bone bone = bones.get(b);
			if (bone.glow() != glowPass) continue;
			float[] q = bone.quads();
			Matrix4f m = mats[b];
			int l = bone.glow() ? FULL_BRIGHT : light;
			int root = cape.roots()[b];
			Matrix4f toWorld = null;
			Matrix4f toRest = null;
			if (cloth != null && root >= 0 && Math.abs(mats[root].determinant()) > 1e-6F) {
				toWorld = mats[root];
				toRest = new Matrix4f(mats[root]).invert().mul(m);
			}
			for (int i = 0; i + 23 <= q.length; i += 23) {
				if (toRest != null) {
					// bend in the cape's own (unrotated) space, then put it where the cape bone is
					for (int k = 0; k < 4; k++) {
						int o = i + k * 5;
						toRest.transformPosition(q[o], q[o + 1], q[o + 2], v);
						c[k * 5] = v.x;
						c[k * 5 + 1] = v.y;
						c[k * 5 + 2] = v.z;
						c[k * 5 + 3] = q[o + 3];
						c[k * 5 + 4] = q[o + 4];
					}
					toRest.transformDirection(q[i + 20], q[i + 21], q[i + 22], n).normalize();
					CapeCloth.emitGrid(buf, pose, toWorld, cloth, bones.get(root).origin(), c, n.x, n.y, n.z, color, l, overlay);
					continue;
				}
				m.transformDirection(q[i + 20], q[i + 21], q[i + 22], n).normalize();
				for (int k = 0; k < 4; k++) {
					int o = i + k * 5;
					m.transformPosition(q[o], q[o + 1], q[o + 2], v);
					buf.addVertex(pose, v.x, v.y, v.z).setColor(color).setUv(q[o + 3], q[o + 4]).setOverlay(overlay).setLight(l)
						.setNormal(pose, n.x, n.y, n.z);
				}
			}
		}
	}

	/** Absolute-space matrix for every bone: parent * T(origin + offset) * R * S * T(-origin). */
	private static Matrix4f[] pose(EModel model, Motion motion, CapeInfo cape) {
		List<EModel.Bone> bones = model.bones;
		Matrix4f[] out = new Matrix4f[bones.size()];
		EModel.Animation clip = motion.sitting() ? model.animation("sit", "idle")
			: motion.walk() > 0.15F ? model.animation("walk", "run", "lauf", "idle") : model.animation("idle", "loop", "");
		float clipTime = clip == null ? 0 : clip.loop() ? motion.time() % clip.length() : Math.min(motion.time(), clip.length());
		Vector3f rot = new Vector3f();
		Vector3f pos = new Vector3f();
		Vector3f scale = new Vector3f();
		for (int i = 0; i < bones.size(); i++) {
			EModel.Bone bone = bones.get(i);
			rot.set(bone.rotation());
			pos.zero();
			scale.set(1);
			if (clip != null) {
				Map<String, List<EModel.Keyframe>> ch = clip.bones().get(i);
				if (ch != null) {
					add(rot, ch.get("rotation"), clipTime);
					add(pos, ch.get("position"), clipTime);
					List<EModel.Keyframe> s = ch.get("scale");
					if (s != null && !s.isEmpty()) sample(scale, s, clipTime);
				}
			}
			if (cape.roots()[i] >= 0) {
				// capes bend as cloth (CapeCloth); only the top cape bone sways sideways
				if (cape.roots()[i] == i) rot.z += motion.capeSide();
			} else {
				auto(bone, rot, motion);
			}
			Vector3f o = bone.origin();
			Matrix4f m = new Matrix4f(bone.parent() >= 0 && bone.parent() < i ? out[bone.parent()] : new Matrix4f());
			m.translate(o.x + pos.x, o.y + pos.y, o.z + pos.z)
				.rotateZ((float) Math.toRadians(rot.z)).rotateY((float) Math.toRadians(rot.y)).rotateX((float) Math.toRadians(rot.x))
				.scale(scale.x, scale.y, scale.z)
				.translate(-o.x, -o.y, -o.z);
			out[i] = m;
		}
		return out;
	}

	private static void auto(EModel.Bone bone, Vector3f rot, Motion m) {
		float t = m.time();
		int s = bone.side();
		switch (bone.kind()) {
			case "wing" -> {
				if (s == 0) return;
				float speed = m.airborne() ? 4.6F : 1.5F + m.walk() * 1.4F;
				float amp = m.airborne() ? 38 : 9 + m.walk() * 12;
				float sweep = m.crouching() ? 62 : 18 + Mth.sin(t * speed * Mth.TWO_PI / 2) * amp;
				float lift = m.crouching() ? 0 : (m.airborne() ? 14 : 4) + Mth.cos(t * speed * Mth.TWO_PI / 2) * (m.airborne() ? 16 : 4);
				rot.y += -s * sweep;
				rot.z += s * lift;
			}
			case "cape" -> {
				rot.x -= 6 + m.capeLean();
				rot.z += m.capeSide();
			}
			case "tail" -> rot.y += Mth.sin(t * (m.walk() > 0.15F ? 9 : 4.5F)) * (m.walk() > 0.15F ? 12 : 18);
			case "ear" -> rot.z += s * 8 * (float) Math.pow(Math.max(0, Mth.sin(t * 1.3F + s)), 10);
			case "leg" -> {
				if (m.sitting()) return;
				boolean front = bone.name().toLowerCase().contains("front") || bone.name().toLowerCase().contains("vorn");
				float phase = (front == (s < 0)) ? 1 : -1;
				rot.x += Mth.sin(m.walkPos() * 0.9F) * 32 * Math.min(1, m.walk() * 1.5F) * phase;
			}
			case "head" -> rot.y += Mth.sin(t * 0.45F) * 12 * (1 - Math.min(1, m.walk()));
			default -> {
			}
		}
	}

	private static void add(Vector3f into, List<EModel.Keyframe> frames, float t) {
		if (frames == null || frames.isEmpty()) return;
		Vector3f v = new Vector3f();
		sample(v, frames, t);
		into.add(v);
	}

	private static void sample(Vector3f out, List<EModel.Keyframe> frames, float t) {
		EModel.Keyframe a = frames.get(0);
		if (t <= a.time() || frames.size() == 1) {
			out.set(a.x(), a.y(), a.z());
			return;
		}
		for (int i = 1; i < frames.size(); i++) {
			EModel.Keyframe b = frames.get(i);
			if (t <= b.time()) {
				float k = (t - a.time()) / Math.max(1e-4F, b.time() - a.time());
				k = k * k * (3 - 2 * k); // smooth in and out
				out.set(Mth.lerp(k, a.x(), b.x()), Mth.lerp(k, a.y(), b.y()), Mth.lerp(k, a.z(), b.z()));
				return;
			}
			a = b;
		}
		out.set(a.x(), a.y(), a.z());
	}
}
