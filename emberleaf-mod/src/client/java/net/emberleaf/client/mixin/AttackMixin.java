package net.emberleaf.client.mixin;

import net.emberleaf.client.config.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Immer Krit-Funken": every hit on a creature shows crit and sharpness particles. */
@Mixin(MultiPlayerGameMode.class)
public abstract class AttackMixin {
	@Inject(method = "attack", at = @At("TAIL"))
	private void emberleaf$alwaysCrits(Player player, Entity entity, CallbackInfo ci) {
		net.emberleaf.client.feature.Extras.onAttack(player, entity);
		if (!Modules.on("always_crits") || !(entity instanceof LivingEntity)) return;
		Minecraft mc = Minecraft.getInstance();
		mc.particleEngine.createTrackingEmitter(entity, ParticleTypes.CRIT);
		mc.particleEngine.createTrackingEmitter(entity, ParticleTypes.ENCHANTED_HIT);
	}
}
