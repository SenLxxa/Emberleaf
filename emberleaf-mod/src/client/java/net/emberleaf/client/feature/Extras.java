package net.emberleaf.client.feature;

import java.util.HashMap;
import java.util.Map;
import net.emberleaf.client.config.Modules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Small everyday helpers: toggle sprint/sneak (Minecraft's own switches, set from the Emberleaf modules), a warning before
 * tools and armour break, and the reach and combo of your hits for the HUD.
 */
public final class Extras {
	private static @Nullable Boolean appliedSprint;
	private static @Nullable Boolean appliedSneak;
	private static final Map<EquipmentSlot, Integer> warned = new HashMap<>();
	private static double lastReach = -1;
	private static long lastHitAt;
	private static int combo;
	private static int lastHurtTime;

	private Extras() {}

	public static void tick(Minecraft mc) {
		applyToggles(mc);
		if (mc.player == null) {
			warned.clear();
			combo = 0;
			return;
		}
		if (Modules.on("durability_warning") && mc.player.tickCount % 10 == 0) checkDurability(mc);
		// the combo ends when you are hit or after three seconds without a hit
		if (mc.player.hurtTime > 0 && lastHurtTime == 0) combo = 0;
		lastHurtTime = mc.player.hurtTime;
		if (System.currentTimeMillis() - lastHitAt > 3000) combo = 0;
	}

	/** The Emberleaf switches set Minecraft's own toggle options; when a module is off, your own choice stays. */
	private static void applyToggles(Minecraft mc) {
		boolean sprint = Modules.on("toggle_sprint");
		boolean sneak = Modules.on("toggle_sneak");
		boolean changed = false;
		if (appliedSprint == null ? sprint : appliedSprint != sprint) {
			mc.options.toggleSprint().set(sprint);
			changed = true;
		}
		if (appliedSneak == null ? sneak : appliedSneak != sneak) {
			mc.options.toggleCrouch().set(sneak);
			changed = true;
		}
		appliedSprint = sprint;
		appliedSneak = sneak;
		if (changed) mc.options.save();
	}

	private static void checkDurability(Minecraft mc) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = mc.player.getItemBySlot(slot);
			if (stack.isEmpty() || !stack.isDamageableItem()) {
				warned.remove(slot);
				continue;
			}
			int left = stack.getMaxDamage() - stack.getDamageValue();
			int limit = Math.max(5, stack.getMaxDamage() / 20);
			if (left > limit) {
				warned.remove(slot);
				continue;
			}
			Integer before = warned.get(slot);
			// once when it gets low, and again shortly before it breaks
			if (before == null || before > 3 && left <= 3) {
				warned.put(slot, left);
				mc.gui.hud.setOverlayMessage(Component.literal("⚠ " + stack.getHoverName().getString() + " ist fast kaputt: noch " + left
					+ (left == 1 ? " Benutzung" : " Benutzungen")).withStyle(ChatFormatting.GOLD), false);
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK.value(), 1.6F, 0.5F));
			}
		}
	}

	/** Called when you hit something (AttackMixin): how far away it was, and the hit streak. */
	public static void onAttack(Player player, Entity target) {
		lastReach = Math.sqrt(target.getBoundingBox().distanceToSqr(player.getEyePosition()));
		long now = System.currentTimeMillis();
		combo = now - lastHitAt <= 3000 ? combo + 1 : 1;
		lastHitAt = now;
	}

	/** "Reichweite 3,12 · Combo 4" for the HUD, or null when you have not hit anything for a while. */
	public static @Nullable String reachLine() {
		if (lastReach < 0 || System.currentTimeMillis() - lastHitAt > 10_000) return null;
		return String.format("Reichweite %.2f", lastReach).replace('.', ',') + (combo > 1 ? "  ·  Combo " + combo : "");
	}
}
