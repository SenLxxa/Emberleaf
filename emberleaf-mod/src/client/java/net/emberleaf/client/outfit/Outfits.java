package net.emberleaf.client.outfit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Finds the outfit for a player: the local one from the launcher, or another player's from the friends server. */
public final class Outfits {
	private static final Map<UUID, Outfit> REMOTE = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> REMOTE_GENERATION = new ConcurrentHashMap<>();

	private Outfits() {}

	public static UUID uuidOf(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		Entity entity = mc.level.getEntity(entityId);
		return entity != null ? entity.getUUID() : null;
	}

	public static boolean isLocal(UUID uuid) {
		Minecraft mc = Minecraft.getInstance();
		return uuid != null && mc.player != null && uuid.equals(mc.player.getUUID());
	}

	public static Outfit forPlayer(UUID uuid) {
		if (uuid == null) return Outfit.EMPTY;
		if (isLocal(uuid)) return OutfitStore.current();
		return REMOTE.getOrDefault(uuid, Outfit.EMPTY);
	}

	/** Changes whenever the player's outfit changes, so cached textures can be rebuilt. */
	public static int generationOf(UUID uuid) {
		if (isLocal(uuid)) return OutfitStore.generation();
		return REMOTE_GENERATION.getOrDefault(uuid, 0);
	}

	public static void putRemote(UUID uuid, Outfit outfit) {
		Outfit old = REMOTE.put(uuid, outfit);
		if (old != outfit) {
			REMOTE_GENERATION.merge(uuid, 1, Integer::sum);
			if (old != null) Minecraft.getInstance().execute(old::release); // textures belong to the render thread
		}
	}

	public static void removeRemote(UUID uuid) {
		Outfit old = REMOTE.remove(uuid);
		REMOTE_GENERATION.merge(uuid, 1, Integer::sum);
		if (old != null) Minecraft.getInstance().execute(old::release);
	}
}
