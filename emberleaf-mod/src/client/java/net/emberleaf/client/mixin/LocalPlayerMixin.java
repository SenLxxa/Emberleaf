package net.emberleaf.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When your Skin-Totem saves you: a burst of autumn leaves and embers and a soft chime on top of the vanilla effect. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "displayItemActivation", at = @At("TAIL"))
	private void emberleaf$totemBurst(ItemStack stack, CallbackInfo ci) {
		if (!stack.is(Items.TOTEM_OF_UNDYING)) return;
		LocalPlayer self = (LocalPlayer) (Object) this;
		RandomSource r = self.getRandom();
		for (int i = 0; i < 60; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double speed = 0.12 + r.nextDouble() * 0.25;
			var particle = switch (i % 4) {
				case 0 -> ParticleTypes.RED_POPLAR_LEAVES;
				case 1 -> ParticleTypes.ORANGE_POPLAR_LEAVES;
				case 2 -> ParticleTypes.YELLOW_POPLAR_LEAVES;
				default -> ParticleTypes.SMALL_FLAME;
			};
			self.level().addParticle(particle, self.getX(), self.getY() + 1.0 + r.nextDouble() * 0.6, self.getZ(),
				Math.cos(a) * speed, 0.05 + r.nextDouble() * 0.2, Math.sin(a) * speed);
		}
		self.level().playLocalSound(self.getX(), self.getY(), self.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.35F, false);
	}
}
