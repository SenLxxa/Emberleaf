package net.emberleaf.client.mixin;

import net.emberleaf.client.config.Modules;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Treffer-Partikel": crit and sharpness sparks are spawned several times per hit. */
@Mixin(ParticleEngine.class)
public abstract class HitParticlesMixin {
	@Unique
	private boolean emberleaf$repeating;

	@Shadow
	public abstract void createTrackingEmitter(Entity entity, ParticleOptions particle);

	@Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V", at = @At("TAIL"))
	private void emberleaf$moreHitParticles(Entity entity, ParticleOptions particle, CallbackInfo ci) {
		if (emberleaf$repeating || !Modules.on("hit_particles")) return;
		if (particle != ParticleTypes.CRIT && particle != ParticleTypes.ENCHANTED_HIT) return;
		int extra = (int) Math.round(Modules.value("hit_particles")) - 1;
		emberleaf$repeating = true;
		try {
			for (int i = 0; i < extra; i++) this.createTrackingEmitter(entity, particle);
		} finally {
			emberleaf$repeating = false;
		}
	}
}
