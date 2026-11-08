package net.emberleaf.client.hud;

import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.menu.Ui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Emberleaf's HUD widgets in warm rounded panels: FPS, coordinates, direction, biome, clock, ping, speed, armour and keys. */
public final class EmberHud {
	private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
	private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();
	private static boolean leftWasDown;
	private static boolean rightWasDown;
	private static double lastX;
	private static double lastZ;
	private static double speed;

	private EmberHud() {}

	/** Once per tick: clicks per second and walking speed. */
	public static void tick(Minecraft mc) {
		if (mc.player == null) return;
		boolean left = mc.options.keyAttack.isDown();
		boolean right = mc.options.keyUse.isDown();
		long now = System.currentTimeMillis();
		if (left && !leftWasDown) LEFT_CLICKS.add(now);
		if (right && !rightWasDown) RIGHT_CLICKS.add(now);
		leftWasDown = left;
		rightWasDown = right;
		while (!LEFT_CLICKS.isEmpty() && now - LEFT_CLICKS.peekFirst() > 1000) LEFT_CLICKS.pollFirst();
		while (!RIGHT_CLICKS.isEmpty() && now - RIGHT_CLICKS.peekFirst() > 1000) RIGHT_CLICKS.pollFirst();
		double dx = mc.player.getX() - lastX;
		double dz = mc.player.getZ() - lastZ;
		speed = speed * 0.7 + Math.sqrt(dx * dx + dz * dz) * 20 * 0.3;
		lastX = mc.player.getX();
		lastZ = mc.player.getZ();
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) return;
		List<String> lines = new ArrayList<>();
		if (Modules.on("hud_fps")) lines.add(mc.getFps() + " FPS");
		BlockPos pos = mc.player.blockPosition();
		if (Modules.on("hud_coords")) lines.add("X " + pos.getX() + "  ·  Y " + pos.getY() + "  ·  Z " + pos.getZ());
		if (Modules.on("hud_direction")) lines.add(direction(mc.player.getYRot()));
		if (Modules.on("hud_biome") && mc.level != null) {
			String biome = mc.level.getBiome(pos).unwrapKey().map(k -> Component.translatable("biome." + k.identifier().getNamespace() + "." + k.identifier().getPath()).getString()).orElse("?");
			lines.add("Biom: " + biome);
		}
		if (Modules.on("hud_clock")) {
			LocalTime now = LocalTime.now();
			long day = mc.level != null ? (mc.level.getOverworldClockTime() + 6000) % 24000 : 0;
			lines.add(String.format("%02d:%02d Uhr  ·  im Spiel %02d:%02d", now.getHour(), now.getMinute(), day / 1000, day % 1000 * 60 / 1000));
		}
		if (Modules.on("hud_ping") && mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (info != null) lines.add("Ping " + info.getLatency() + " ms");
		}
		if (Modules.on("hud_speed")) lines.add(String.format("%.1f Blöcke/s", speed));
		if (Modules.on("hud_playtime")) {
			lines.add("Spielzeit " + net.emberleaf.client.feature.Playtime.format(net.emberleaf.client.feature.Playtime.total())
				+ "  ·  heute " + net.emberleaf.client.feature.Playtime.format(net.emberleaf.client.feature.Playtime.today()));
		}
		if (Modules.on("hud_food")) {
			var food = mc.player.getFoodData();
			lines.add(String.format("Hunger %d/20  ·  Sättigung %.1f", food.getFoodLevel(), food.getSaturationLevel()).replace('.', ','));
		}
		if (Modules.on("hud_day") && mc.level != null) lines.add("Tag " + (mc.level.getOverworldClockTime() / 24000L + 1));
		if (Modules.on("hud_server")) lines.add(serverLine(mc));
		if (Modules.on("reach_display")) {
			String reach = net.emberleaf.client.feature.Extras.reachLine();
			if (reach != null) lines.add(reach);
		}

		int x = 6;
		int y = 6;
		for (String line : lines) {
			Component text = Ui.menu(line);
			int w = Ui.font().width(text) + 12;
			Ui.nineSlice(g, Ui.PANEL, x, y, w, 15, 5, 0xE6FFFFFF);
			Ui.text(g, text, x + 6, y + 3.5F, 0xFFFFEBD4);
			y += 18;
		}
		if (Modules.on("hud_armor")) y = armor(g, mc, x, y);
		if (Modules.on("hud_items")) y = items(g, mc, x, y);
		if (Modules.on("hud_effects")) effects(g, mc, x, y);
		if (Modules.on("hud_keys")) keys(g, mc);
		net.emberleaf.client.feature.Minimap.render(g, mc);
	}

	private static String serverLine(Minecraft mc) {
		if (mc.isLocalServer() || mc.getCurrentServer() == null) return "Einzelspieler";
		int players = mc.getConnection() != null ? mc.getConnection().getListedOnlinePlayers().size() : 0;
		return mc.getCurrentServer().ip + "  ·  " + players + " Spieler";
	}

	private static ItemStack[] counterIcons;

	/** Arrows, totems, ender pearls, golden apples and rockets you carry (only what you have). */
	private static int items(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
		if (counterIcons == null) {
			counterIcons = new ItemStack[] {new ItemStack(Items.ARROW), new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.ENDER_PEARL),
				new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.FIREWORK_ROCKET)};
		}
		int[] counts = new int[counterIcons.length];
		var inventory = mc.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack s = inventory.getItem(i);
			if (s.isEmpty()) continue;
			if (s.is(Items.ARROW) || s.is(Items.SPECTRAL_ARROW) || s.is(Items.TIPPED_ARROW)) counts[0] += s.getCount();
			else if (s.is(Items.TOTEM_OF_UNDYING)) counts[1] += s.getCount();
			else if (s.is(Items.ENDER_PEARL)) counts[2] += s.getCount();
			else if (s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)) counts[3] += s.getCount();
			else if (s.is(Items.FIREWORK_ROCKET)) counts[4] += s.getCount();
		}
		int shown = 0;
		int width = 4;
		for (int count : counts) {
			if (count <= 0) continue;
			shown++;
			width += 22 + Ui.font().width(Ui.menu(String.valueOf(count)));
		}
		if (shown == 0) return y;
		Ui.nineSlice(g, Ui.PANEL, x, y, width, 20, 5, 0xE6FFFFFF);
		int cx = x + 3;
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] <= 0) continue;
			g.item(counterIcons[i], cx, y + 2);
			Component n = Ui.menu(String.valueOf(counts[i]));
			Ui.text(g, n, cx + 18, y + 6, 0xFFFFEBD4);
			cx += 22 + Ui.font().width(n);
		}
		return y + 24;
	}

	/** Your effects with level and time left (blinks shortly before it ends). */
	private static void effects(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
		List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
		effects.sort((a, b) -> Integer.compare(b.isInfiniteDuration() ? Integer.MAX_VALUE : b.getDuration(),
			a.isInfiniteDuration() ? Integer.MAX_VALUE : a.getDuration()));
		for (MobEffectInstance e : effects) {
			if (!e.showIcon()) continue;
			String name = e.getEffect().value().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
			String time = e.isInfiniteDuration() ? "∞" : ticks(e.getDuration());
			Component text = Ui.menu(name + "  " + time);
			int w = Ui.font().width(text) + 30;
			Ui.nineSlice(g, Ui.PANEL, x, y, w, 18, 5, 0xE6FFFFFF);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.getEffect()), x + 3, y + 1, 16, 16);
			int color = e.getEffect().value().isBeneficial() ? 0xFFFFEBD4 : 0xFFFF9A8A;
			if (!e.isInfiniteDuration() && e.getDuration() < 200 && System.currentTimeMillis() / 400 % 2 == 0) color = 0xFFFFD24A;
			Ui.text(g, text, x + 23, y + 5, color);
			y += 21;
		}
	}

	private static String ticks(int ticks) {
		int seconds = ticks / 20;
		return seconds >= 3600 ? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
			: String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	private static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
	}

	private static String direction(float yaw) {
		String[] names = {"Süden (+Z)", "Südwesten", "Westen (−X)", "Nordwesten", "Norden (−Z)", "Nordosten", "Osten (+X)", "Südosten"};
		int i = Math.floorMod(Math.round(yaw / 45F), 8);
		return names[i];
	}

	private static int armor(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
		List<ItemStack> items = new ArrayList<>();
		for (EquipmentSlot slot : slots) {
			ItemStack stack = mc.player.getItemBySlot(slot);
			if (!stack.isEmpty() && (slot != EquipmentSlot.MAINHAND || stack.isDamageableItem())) items.add(stack);
		}
		if (items.isEmpty()) return y;
		Ui.nineSlice(g, Ui.PANEL, x, y, 64, items.size() * 18 + 4, 5, 0xE6FFFFFF);
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			int iy = y + 2 + i * 18;
			g.item(stack, x + 3, iy);
			if (stack.isDamageableItem()) {
				int left = stack.getMaxDamage() - stack.getDamageValue();
				float k = left / (float) stack.getMaxDamage();
				int color = k > 0.5F ? 0xFF9FE37A : k > 0.2F ? 0xFFF2C14E : 0xFFFF6A4A;
				Ui.text(g, Ui.menu(String.valueOf(left)), x + 22, iy + 4, color);
			} else {
				Ui.text(g, Ui.menu("×" + stack.getCount()), x + 22, iy + 4, 0xFFFFEBD4);
			}
		}
		return y + items.size() * 18 + 8;
	}

	private static void keys(GuiGraphicsExtractor g, Minecraft mc) {
		int size = 18;
		int gap = 2;
		// bottom right: the chat lives bottom left
		int x0 = g.guiWidth() - 6 - (size * 3 + gap * 2);
		int y0 = g.guiHeight() - 6 - (size + gap) * 4 + gap + 6;
		key(g, "W", mc.options.keyUp.isDown(), x0 + size + gap, y0, size, size);
		key(g, "A", mc.options.keyLeft.isDown(), x0, y0 + size + gap, size, size);
		key(g, "S", mc.options.keyDown.isDown(), x0 + size + gap, y0 + size + gap, size, size);
		key(g, "D", mc.options.keyRight.isDown(), x0 + (size + gap) * 2, y0 + size + gap, size, size);
		int wide = size * 3 + gap * 2;
		int half = (wide - gap) / 2;
		key(g, LEFT_CLICKS.size() + " CPS", mc.options.keyAttack.isDown(), x0, y0 + (size + gap) * 2, half, size);
		key(g, RIGHT_CLICKS.size() + " CPS", mc.options.keyUse.isDown(), x0 + half + gap, y0 + (size + gap) * 2, half, size);
		boolean jump = mc.options.keyJump.isDown();
		int sy = y0 + (size + gap) * 3;
		key(g, "", jump, x0, sy, wide, size - 6);
		g.fill(x0 + wide / 2 - 9, sy + 5, x0 + wide / 2 + 9, sy + 7, jump ? 0xFFFFFFFF : 0xFFD8C2A8);
	}

	private static void key(GuiGraphicsExtractor g, String label, boolean down, int x, int y, int w, int h) {
		Ui.nineSlice(g, down ? Ui.BUTTON_HOVER : Ui.PANEL, x, y, w, h, 5, 0xEEFFFFFF);
		Component text = Ui.menu(label);
		Ui.text(g, text, x + (w - Ui.font().width(text)) / 2F, y + (h - 8) / 2F, down ? 0xFFFFFFFF : 0xFFD8C2A8);
	}

}
