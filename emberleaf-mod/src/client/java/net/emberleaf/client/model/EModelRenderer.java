package net.emberleaf.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
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
		Identifier texture = model.texture();
		if (texture == null) return;
		Matrix4f[] bones = pose(model, motion);
		collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(texture), (p, buf) -> emit(model, bones, p, buf, light, overlay));
	}

	private static void emit(EModel model, Matrix4f[] mats, PoseStack.Pose pose, VertexConsumer buf, int light, int overlay) {
		Vector3f v = new Vector3f();
		Vector3f n = new Vector3f();
		List<EModel.Bone> bones = model.bones;
		for (int b = 0; b < bones.size(); b++) {
			EModel.Bone bone = bones.get(b);
			float[] q = bone.quads();
			Matrix4f m = mats[b];
			int l = bone.glow() ? FULL_BRIGHT : light;
			for (int i = 0; i + 23 <= q.length; i += 23) {
				m.transformDirection(q[i + 20], q[i + 21], q[i + 22], n).normalize();
				for (int k = 0; k < 4; k++) {
					int o = i + k * 5;
					m.transformPosition(q[o], q[o + 1], q[o + 2], v);
					buf.addVertex(pose, v.x, v.y, v.z).setColor(-1).setUv(q[o + 3], q[o + 4]).setOverlay(overlay).setLight(l)
						.setNormal(pose, n.x, n.y, n.z);
				}
			}
		}
	}

	/** Absolute-space matrix for every bone: parent * T(origin + offset) * R * S * T(-origin). */
	private static Matrix4f[] pose(EModel model, Motion motion) {
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
			auto(bone, rot, motion);
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
