package net.emberleaf.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.emberleaf.client.outfit.Outfit;
import net.emberleaf.client.outfit.Outfits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Uploads Emberleaf capes (one texture per animation frame) and picks the current frame. Render thread only. */
public final class CapeTextures {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

	private record Entry(int generation, List<Identifier> frames, int fps) {}

	private CapeTextures() {}

	/** The texture of the cape frame to show now, or null when the player has no Emberleaf cape. */
	public static Identifier frameFor(UUID uuid, float ageInTicks) {
		if (uuid == null) return null;
		Outfit outfit = Outfits.forPlayer(uuid);
		int generation = Outfits.generationOf(uuid);
		Entry entry = ENTRIES.get(uuid);
		if (entry == null || entry.generation != generation) {
			release(entry);
			entry = upload(uuid, outfit, generation);
			ENTRIES.put(uuid, entry);
		}
		if (entry.frames.isEmpty()) return null;
		int index = (int) (ageInTicks / 20.0F * entry.fps) % entry.frames.size();
		return entry.frames.get(Math.max(0, index));
	}

	private static Entry upload(UUID uuid, Outfit outfit, int generation) {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		List<Identifier> frames = new ArrayList<>();
		for (int i = 0; i < outfit.capeFrames().size(); i++) {
			try {
				NativeImage image = NativeImage.read(outfit.capeFrames().get(i));
				Identifier id = Identifier.fromNamespaceAndPath("emberleaf", "cape/" + uuid + "/" + generation + "/" + i);
				textures.register(id, new DynamicTexture(() -> "Emberleaf cape " + uuid, image));
				frames.add(id);
			} catch (IOException e) {
				LOG.warn("Emberleaf cape frame {} could not be read", i, e);
			}
		}
		return new Entry(generation, List.copyOf(frames), outfit.capeFps());
	}

	private static void release(Entry entry) {
		if (entry == null) return;
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		entry.frames.forEach(textures::release);
	}
}
