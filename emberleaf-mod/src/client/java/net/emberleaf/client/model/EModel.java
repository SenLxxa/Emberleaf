package net.emberleaf.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cosmetic made in Blockbench, in the compact "emodel" form the launcher exports (backend/bbmodel.py).
 * Coordinates are Blockbench pixels: feet at y=0, the wearer looks north (-z).
 * Each cube is pre-meshed into quads in absolute model space; bones only move them at render time.
 */
public final class EModel {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	private static final AtomicInteger COUNTER = new AtomicInteger();
	private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

	/** One bone ("group" in Blockbench). Quads: 4 x (x, y, z, u, v) + nx, ny, nz per quad. */
	public record Bone(String name, String kind, int parent, Vector3f origin, Vector3f rotation, float[] quads, boolean glow, int side) {}

	public record Keyframe(float time, float x, float y, float z) {}

	public record Animation(String name, boolean loop, float length, Map<Integer, Map<String, List<Keyframe>>> bones) {}

	public final List<Bone> bones;
	public final List<Animation> animations;
	private final byte[] textureBytes;
	private @Nullable Identifier texture;

	private EModel(List<Bone> bones, List<Animation> animations, byte[] textureBytes) {
		this.bones = bones;
		this.animations = animations;
		this.textureBytes = textureBytes;
	}

	/** The model's texture, uploaded on first use (render thread). */
	public @Nullable Identifier texture() {
		if (texture == null && textureBytes != null) {
			try {
				NativeImage image = NativeImage.read(textureBytes);
				texture = Identifier.fromNamespaceAndPath("emberleaf", "emodel/" + COUNTER.incrementAndGet());
				Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "Emberleaf cosmetic", image));
			} catch (IOException e) {
				LOG.warn("Emberleaf: Textur eines Cosmetics ist kaputt", e);
				return null;
			}
		}
		return texture;
	}

	/** The texture as PNG bytes (for reading its colours, e.g. cape effects). */
	public byte[] textureBytes() {
		return textureBytes;
	}

	public void release() {
		if (texture != null) Minecraft.getInstance().getTextureManager().release(texture);
		texture = null;
	}

	public @Nullable Animation animation(String... names) {
		for (String wanted : names) {
			for (Animation a : animations) if (a.name.toLowerCase(Locale.ROOT).contains(wanted)) return a;
		}
		return null;
	}

	// ---------- Laden ----------

	public static @Nullable EModel parse(JsonObject json) {
		try {
			return read(json);
		} catch (RuntimeException e) {
			LOG.warn("Emberleaf: Blockbench-Cosmetic konnte nicht gelesen werden", e);
			return null;
		}
	}

	private static EModel read(JsonObject json) {
		JsonArray res = json.getAsJsonArray("resolution");
		float resW = res.get(0).getAsFloat();
		float resH = res.get(1).getAsFloat();
		String tex = json.getAsJsonArray("textures").get(0).getAsString();
		byte[] bytes = Base64.getDecoder().decode(tex.substring(tex.indexOf(',') + 1));

		List<Bone> bones = new ArrayList<>();
		for (JsonElement el : json.getAsJsonArray("bones")) {
			JsonObject b = el.getAsJsonObject();
			String name = b.get("name").getAsString();
			List<Float> quads = new ArrayList<>();
			for (JsonElement c : b.getAsJsonArray("cubes")) mesh(c.getAsJsonObject(), resW, resH, quads);
			float[] q = new float[quads.size()];
			for (int i = 0; i < q.length; i++) q[i] = quads.get(i);
			String lower = name.toLowerCase(Locale.ROOT);
			int side = lower.contains("left") || lower.endsWith("_l") ? -1 : lower.contains("right") || lower.endsWith("_r") ? 1 : 0;
			bones.add(new Bone(name, kind(lower), b.get("parent").getAsInt(), vec(b.getAsJsonArray("origin")), vec(b.getAsJsonArray("rotation")),
				q, lower.contains("glow") || lower.contains("leucht"), side));
		}
		// glowing parents make their children glow too
		for (int i = 0; i < bones.size(); i++) {
			Bone bone = bones.get(i);
			if (!bone.glow && bone.parent >= 0 && bones.get(bone.parent).glow) {
				bones.set(i, new Bone(bone.name, bone.kind, bone.parent, bone.origin, bone.rotation, bone.quads, true, bone.side));
			}
		}

		List<Animation> animations = new ArrayList<>();
		if (json.has("animations")) {
			for (JsonElement el : json.getAsJsonArray("animations")) {
				JsonObject a = el.getAsJsonObject();
				Map<Integer, Map<String, List<Keyframe>>> byBone = new HashMap<>();
				for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("bones").entrySet()) {
					Map<String, List<Keyframe>> channels = new HashMap<>();
					for (Map.Entry<String, JsonElement> ch : e.getValue().getAsJsonObject().entrySet()) {
						List<Keyframe> frames = new ArrayList<>();
						for (JsonElement f : ch.getValue().getAsJsonArray()) {
							JsonArray k = f.getAsJsonArray();
							frames.add(new Keyframe(k.get(0).getAsFloat(), k.get(1).getAsFloat(), k.get(2).getAsFloat(), k.get(3).getAsFloat()));
						}
						channels.put(ch.getKey(), frames);
					}
					byBone.put(Integer.parseInt(e.getKey()), channels);
				}
				animations.add(new Animation(a.get("name").getAsString(), a.get("loop").getAsBoolean(), a.get("length").getAsFloat(), byBone));
			}
		}
		return new EModel(List.copyOf(bones), List.copyOf(animations), bytes);
	}

	/** What Emberleaf animates automatically, from the bone's name. */
	private static String kind(String name) {
		if (name.contains("wing") || name.contains("flügel") || name.contains("fluegel")) return "wing";
		if (name.contains("cape") || name.contains("umhang")) return "cape";
		if (name.contains("tail") || name.contains("schwanz")) return "tail";
		if (name.contains("ear") || name.contains("ohr")) return "ear";
		if (name.contains("leg") || name.contains("bein")) return "leg";
		if (name.equals("head") || name.contains("kopf")) return "head";
		return "";
	}

	private static Vector3f vec(JsonArray a) {
		return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
	}

	private static void mesh(JsonObject cube, float resW, float resH, List<Float> out) {
		Vector3f from = vec(cube.getAsJsonArray("from"));
		Vector3f to = vec(cube.getAsJsonArray("to"));
		float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0;
		float x0 = Math.min(from.x, to.x) - inflate, y0 = Math.min(from.y, to.y) - inflate, z0 = Math.min(from.z, to.z) - inflate;
		float x1 = Math.max(from.x, to.x) + inflate, y1 = Math.max(from.y, to.y) + inflate, z1 = Math.max(from.z, to.z) + inflate;
		Vector3f origin = vec(cube.getAsJsonArray("origin"));
		Vector3f rot = vec(cube.getAsJsonArray("rotation"));
		Matrix4f m = new Matrix4f().translate(origin)
			.rotateZ((float) Math.toRadians(rot.z)).rotateY((float) Math.toRadians(rot.y)).rotateX((float) Math.toRadians(rot.x))
			.translate(-origin.x, -origin.y, -origin.z);
		// corners per face as seen from outside: top-left, top-right, bottom-right, bottom-left
		float[][][] corners = {
			{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}}, // north
			{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}}, // east
			{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}}, // south
			{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}}, // west
			{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}, // up
			{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}, // down
		};
		float[][] normals = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
		JsonObject faces = cube.getAsJsonObject("faces");
		Vector3f p = new Vector3f();
		for (int f = 0; f < 6; f++) {
			if (!faces.has(FACES[f])) continue;
			JsonArray uv = faces.getAsJsonArray(FACES[f]);
			float u0 = uv.get(0).getAsFloat() / resW, v0 = uv.get(1).getAsFloat() / resH;
			float u1 = uv.get(2).getAsFloat() / resW, v1 = uv.get(3).getAsFloat() / resH;
			int turns = Math.floorMod(uv.size() > 5 ? uv.get(5).getAsInt() / 90 : 0, 4);
			float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
			// counter-clockwise from outside: TL, BL, BR, TR
			int[] order = {0, 3, 2, 1};
			for (int k : order) {
				float[] c = corners[f][k];
				m.transformPosition(c[0], c[1], c[2], p);
				float[] t = uvs[(k + 4 - turns) % 4];
				out.add(p.x);
				out.add(p.y);
				out.add(p.z);
				out.add(t[0]);
				out.add(t[1]);
			}
			Vector3f n = m.transformDirection(normals[f][0], normals[f][1], normals[f][2], new Vector3f()).normalize();
			out.add(n.x);
			out.add(n.y);
			out.add(n.z);
		}
	}
}
package net.emberleaf.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cosmetic made in Blockbench, in the compact "emodel" form the launcher exports (backend/bbmodel.py).
 * Coordinates are Blockbench pixels: feet at y=0, the wearer looks north (-z).
 * Each cube is pre-meshed into quads in absolute model space; bones only move them at render time.
 */
public final class EModel {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	private static final AtomicInteger COUNTER = new AtomicInteger();
	private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

	/** One bone ("group" in Blockbench). Quads: 4 x (x, y, z, u, v) + nx, ny, nz per quad. */
	public record Bone(String name, String kind, int parent, Vector3f origin, Vector3f rotation, float[] quads, boolean glow, int side) {}

	public record Keyframe(float time, float x, float y, float z) {}

	public record Animation(String name, boolean loop, float length, Map<Integer, Map<String, List<Keyframe>>> bones) {}

	public final List<Bone> bones;
	public final List<Animation> animations;
	private final byte[] textureBytes;
	private @Nullable Identifier texture;

	private EModel(List<Bone> bones, List<Animation> animations, byte[] textureBytes) {
		this.bones = bones;
		this.animations = animations;
		this.textureBytes = textureBytes;
	}

	/** The model's texture, uploaded on first use (render thread). */
	public @Nullable Identifier texture() {
		if (texture == null && textureBytes != null) {
			try {
				NativeImage image = NativeImage.read(textureBytes);
				texture = Identifier.fromNamespaceAndPath("emberleaf", "emodel/" + COUNTER.incrementAndGet());
				Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "Emberleaf cosmetic", image));
			} catch (IOException e) {
				LOG.warn("Emberleaf: Textur eines Cosmetics ist kaputt", e);
				return null;
			}
		}
		return texture;
	}

	public void release() {
		if (texture != null) Minecraft.getInstance().getTextureManager().release(texture);
		texture = null;
	}

	public @Nullable Animation animation(String... names) {
		for (String wanted : names) {
			for (Animation a : animations) if (a.name.toLowerCase(Locale.ROOT).contains(wanted)) return a;
		}
		return null;
	}

	// ---------- Laden ----------

	public static @Nullable EModel parse(JsonObject json) {
		try {
			return read(json);
		} catch (RuntimeException e) {
			LOG.warn("Emberleaf: Blockbench-Cosmetic konnte nicht gelesen werden", e);
			return null;
		}
	}

	private static EModel read(JsonObject json) {
		JsonArray res = json.getAsJsonArray("resolution");
		float resW = res.get(0).getAsFloat();
		float resH = res.get(1).getAsFloat();
		String tex = json.getAsJsonArray("textures").get(0).getAsString();
		byte[] bytes = Base64.getDecoder().decode(tex.substring(tex.indexOf(',') + 1));

		List<Bone> bones = new ArrayList<>();
		for (JsonElement el : json.getAsJsonArray("bones")) {
			JsonObject b = el.getAsJsonObject();
			String name = b.get("name").getAsString();
			List<Float> quads = new ArrayList<>();
			for (JsonElement c : b.getAsJsonArray("cubes")) mesh(c.getAsJsonObject(), resW, resH, quads);
			float[] q = new float[quads.size()];
			for (int i = 0; i < q.length; i++) q[i] = quads.get(i);
			String lower = name.toLowerCase(Locale.ROOT);
			int side = lower.contains("left") || lower.endsWith("_l") ? -1 : lower.contains("right") || lower.endsWith("_r") ? 1 : 0;
			bones.add(new Bone(name, kind(lower), b.get("parent").getAsInt(), vec(b.getAsJsonArray("origin")), vec(b.getAsJsonArray("rotation")),
				q, lower.contains("glow") || lower.contains("leucht"), side));
		}
		// glowing parents make their children glow too
		for (int i = 0; i < bones.size(); i++) {
			Bone bone = bones.get(i);
			if (!bone.glow && bone.parent >= 0 && bones.get(bone.parent).glow) {
				bones.set(i, new Bone(bone.name, bone.kind, bone.parent, bone.origin, bone.rotation, bone.quads, true, bone.side));
			}
		}

		List<Animation> animations = new ArrayList<>();
		if (json.has("animations")) {
			for (JsonElement el : json.getAsJsonArray("animations")) {
				JsonObject a = el.getAsJsonObject();
				Map<Integer, Map<String, List<Keyframe>>> byBone = new HashMap<>();
				for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("bones").entrySet()) {
					Map<String, List<Keyframe>> channels = new HashMap<>();
					for (Map.Entry<String, JsonElement> ch : e.getValue().getAsJsonObject().entrySet()) {
						List<Keyframe> frames = new ArrayList<>();
						for (JsonElement f : ch.getValue().getAsJsonArray()) {
							JsonArray k = f.getAsJsonArray();
							frames.add(new Keyframe(k.get(0).getAsFloat(), k.get(1).getAsFloat(), k.get(2).getAsFloat(), k.get(3).getAsFloat()));
						}
						channels.put(ch.getKey(), frames);
					}
					byBone.put(Integer.parseInt(e.getKey()), channels);
				}
				animations.add(new Animation(a.get("name").getAsString(), a.get("loop").getAsBoolean(), a.get("length").getAsFloat(), byBone));
			}
		}
		return new EModel(List.copyOf(bones), List.copyOf(animations), bytes);
	}

	/** What Emberleaf animates automatically, from the bone's name. */
	private static String kind(String name) {
		if (name.contains("wing") || name.contains("flügel") || name.contains("fluegel")) return "wing";
		if (name.contains("cape") || name.contains("umhang")) return "cape";
		if (name.contains("tail") || name.contains("schwanz")) return "tail";
		if (name.contains("ear") || name.contains("ohr")) return "ear";
		if (name.contains("leg") || name.contains("bein")) return "leg";
		if (name.equals("head") || name.contains("kopf")) return "head";
		return "";
	}

	private static Vector3f vec(JsonArray a) {
		return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
	}

	private static void mesh(JsonObject cube, float resW, float resH, List<Float> out) {
		Vector3f from = vec(cube.getAsJsonArray("from"));
		Vector3f to = vec(cube.getAsJsonArray("to"));
		float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0;
		float x0 = Math.min(from.x, to.x) - inflate, y0 = Math.min(from.y, to.y) - inflate, z0 = Math.min(from.z, to.z) - inflate;
		float x1 = Math.max(from.x, to.x) + inflate, y1 = Math.max(from.y, to.y) + inflate, z1 = Math.max(from.z, to.z) + inflate;
		Vector3f origin = vec(cube.getAsJsonArray("origin"));
		Vector3f rot = vec(cube.getAsJsonArray("rotation"));
		Matrix4f m = new Matrix4f().translate(origin)
			.rotateZ((float) Math.toRadians(rot.z)).rotateY((float) Math.toRadians(rot.y)).rotateX((float) Math.toRadians(rot.x))
			.translate(-origin.x, -origin.y, -origin.z);
		// corners per face as seen from outside: top-left, top-right, bottom-right, bottom-left
		float[][][] corners = {
			{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}}, // north
			{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}}, // east
			{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}}, // south
			{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}}, // west
			{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}, // up
			{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}, // down
		};
		float[][] normals = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
		JsonObject faces = cube.getAsJsonObject("faces");
		Vector3f p = new Vector3f();
		for (int f = 0; f < 6; f++) {
			if (!faces.has(FACES[f])) continue;
			JsonArray uv = faces.getAsJsonArray(FACES[f]);
			float u0 = uv.get(0).getAsFloat() / resW, v0 = uv.get(1).getAsFloat() / resH;
			float u1 = uv.get(2).getAsFloat() / resW, v1 = uv.get(3).getAsFloat() / resH;
			int turns = Math.floorMod(uv.size() > 5 ? uv.get(5).getAsInt() / 90 : 0, 4);
			float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
			// counter-clockwise from outside: TL, BL, BR, TR
			int[] order = {0, 3, 2, 1};
			for (int k : order) {
				float[] c = corners[f][k];
				m.transformPosition(c[0], c[1], c[2], p);
				float[] t = uvs[(k + 4 - turns) % 4];
				out.add(p.x);
				out.add(p.y);
				out.add(p.z);
				out.add(t[0]);
				out.add(t[1]);
			}
			Vector3f n = m.transformDirection(normals[f][0], normals[f][1], normals[f][2], new Vector3f()).normalize();
			out.add(n.x);
			out.add(n.y);
			out.add(n.z);
		}
	}
}
