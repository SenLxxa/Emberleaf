package net.emberleaf.client.outfit;

import java.util.List;
import java.util.Map;
import net.emberleaf.client.model.EModel;

/**
 * Everything a player wears, as exported by the Emberleaf launcher (outfit.json) or sent by the friends server.
 *
 * @param voxels      voxel cosmetics by slot: "head", "body", "wings", "pet"
 * @param petOnGround whether the pet walks (true) or floats beside the player (false)
 * @param capeFrames  PNG bytes of each cape frame (empty = no Emberleaf cape)
 * @param capeFps     animation speed of the cape
 * @param aura        particle aura, or null
 * @param emote       id of the chosen emote, or null
 */
public record Outfit(
	Map<String, VoxelModel> voxels, boolean petOnGround, List<byte[]> capeFrames, int capeFps, Aura aura, String emote,
	Map<String, EModel> models
) {
	public static final Outfit EMPTY = new Outfit(Map.of(), false, List.of(), 8, null, null, Map.of());

	/** Particle aura settings from the launcher: mode (swirl, rise, fall, twinkle, wander), sprite and ring flag. */
	public record Aura(String mode, String sprite, boolean ring) {}

	public VoxelModel voxel(String slot) {
		return voxels.get(slot);
	}

	/** Blockbench cosmetic in this slot ("head", "body", "wings", "cape", "pet"), or null. */
	public EModel model(String slot) {
		return models.get(slot);
	}

	public void release() {
		models.values().forEach(EModel::release);
	}

	public boolean isEmpty() {
		return voxels.isEmpty() && capeFrames.isEmpty() && aura == null && models.isEmpty();
	}
}
