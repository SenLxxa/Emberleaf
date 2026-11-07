package net.emberleaf.client.feature;

import net.emberleaf.client.outfit.Outfit;
import net.emberleaf.client.outfit.Outfits;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/** Shows Emberleaf auras in the world with Minecraft's own particles. */
public final class Auras {
	private static final RandomSource RANDOM = RandomSource.create();

	private Auras() {}

	public static void tick(Minecraft mc) {
		if (!net.emberleaf.client.config.Modules.on("auras")) return;
		if (mc.level == null || mc.player == null || mc.isPaused()) return;
		for (Player player : mc.level.players()) {
			if (player.isInvisible() || player.distanceToSqr(mc.player) > 48 * 48) continue;
			Outfit.Aura aura = Outfits.forPlayer(player.getUUID()).aura();
			if (aura == null) continue;
			// In first person your own aura would only be in the way
			if (player == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) continue;
			spawn(mc, player, aura);
		}
	}

	private static void spawn(Minecraft mc, Player player, Outfit.Aura aura) {
		float chance = switch (aura.sprite()) {
			case "ember" -> 0.6F;
			case "leaf", "snow" -> 0.35F;
			case "spark" -> 0.25F;
			default -> 0.1F;
		};
		if (RANDOM.nextFloat() > chance) return;
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		double radius = aura.ring() ? 1.05 : 0.5 + RANDOM.nextDouble() * 0.45;
		double x = player.getX() + Math.cos(angle) * radius;
		double z = player.getZ() + Math.sin(angle) * radius;
		double y = switch (aura.mode()) {
			case "fall" -> player.getY() + 2.3;
			case "rise" -> player.getY() + (aura.ring() ? 0.05 : RANDOM.nextDouble() * 0.6);
			default -> player.getY() + RANDOM.nextDouble() * 2.0;
		};
		double rise = "rise".equals(aura.mode()) ? 0.03 : 0.0;
		mc.level.addParticle(particle(aura.sprite()), x, y, z, 0.0, rise, 0.0);
	}

	private static ParticleOptions particle(String sprite) {
		return switch (sprite) {
			case "leaf" -> switch (RANDOM.nextInt(3)) {
				case 0 -> ParticleTypes.RED_POPLAR_LEAVES;
				case 1 -> ParticleTypes.ORANGE_POPLAR_LEAVES;
				default -> ParticleTypes.YELLOW_POPLAR_LEAVES;
			};
			case "ember" -> ParticleTypes.SMALL_FLAME;
			case "heart" -> ParticleTypes.HEART;
			case "snow" -> ParticleTypes.SNOWFLAKE;
			case "note" -> ParticleTypes.NOTE;
			case "dot" -> ParticleTypes.FIREFLY;
			default -> ParticleTypes.END_ROD;
		};
	}
}
