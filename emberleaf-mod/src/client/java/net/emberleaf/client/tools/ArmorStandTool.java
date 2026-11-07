package net.emberleaf.client.tools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;

/** Armor stand generator: options, a pose per body part and equipment, as a summon command with a little preview. */
public class ArmorStandTool extends ToolScreen {
	private static final String[] PARTS = {"Kopf", "Körper", "Linker Arm", "Rechter Arm", "Linkes Bein", "Rechtes Bein"};
	private static final String[] NBT_PARTS = {"Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg"};
	private static final String[] OPTIONS = {"Arme", "Klein", "Ohne Platte", "Unsichtbar", "Schwebt", "Leuchten"};
	private static final String[] NBT_OPTIONS = {"ShowArms", "Small", "NoBasePlate", "Invisible", "NoGravity", "Glowing"};
	private static final boolean[] OPTION = {true, false, true, false, true, false};
	private static final float[][] POSE = {{0, 0, 0}, {0, 0, 0}, {-10, 0, -10}, {-60, 20, 10}, {-1, 0, -1}, {1, 0, 1}};
	private static final String[] SLOTS = {"head", "chest", "legs", "feet", "mainhand", "offhand"};
	private static final String[] SLOT_NAMES = {"Kopf", "Brust", "Beine", "Füße", "Haupthand", "Zweithand"};
	private static final String[] EQUIP = {"golden_helmet", "iron_chestplate", "", "leather_boots", "diamond_sword", ""};
	private static String name = "";
	private static int part = 3;

	public ArmorStandTool(Screen parent) {
		super("Rüstungsständer", "Posen und Ausrüstung", parent);
	}

	// layout: options (3 per row) on top, then name | body part, then equipment (left) | angles and the little figure (right)
	private int half() {
		return (pw - 24 - 8) / 2;
	}

	private int rightX() {
		return left + half() + 8;
	}

	@Override
	protected void build() {
		int ow = (pw - 24 - 8) / 3;
		for (int i = 0; i < OPTIONS.length; i++) {
			int idx = i;
			cycle(left + (i % 3) * (ow + 4), top + (i / 3) * 20, ow, new String[] {OPTIONS[i] + ": aus", OPTIONS[i] + ": an"}, OPTION[i] ? 1 : 0,
				v -> OPTION[idx] = v == 1);
		}
		int half = half();
		int rx = rightX();
		field(left, top + 44, half, "Name über dem Ständer (optional)", name, v -> name = v);
		String[] parts = new String[PARTS.length];
		for (int i = 0; i < parts.length; i++) parts[i] = "Pose: " + PARTS[i];
		cycle(rx, top + 44, half, parts, part, v -> {
			part = v;
			rebuildWidgets();
		});
		String[] axes = {"X", "Y", "Z"};
		int aw = (half - 8) / 3;
		for (int a = 0; a < 3; a++) {
			int axis = a;
			field(rx + a * (aw + 4), top + 76, aw, axes[a] + " in Grad", String.valueOf((int) POSE[part][a]),
				v -> POSE[part][axis] = Mth.clamp((float) parseDouble(v, 0), -180, 180));
		}
		int ew = (half - 4) / 2;
		for (int s = 0; s < SLOTS.length; s++) {
			int slot = s;
			field(left + (s % 2) * (ew + 4), top + 76 + (s / 2) * 19, ew, SLOT_NAMES[s], EQUIP[s], v -> EQUIP[slot] = v.strip());
		}
	}

	@Override
	protected String compute() {
		StringBuilder b = new StringBuilder("/summon armor_stand ~ ~ ~ {");
		boolean first = true;
		for (int i = 0; i < OPTIONS.length; i++) {
			if (!OPTION[i]) continue;
			b.append(first ? "" : ",").append(NBT_OPTIONS[i]).append(":1b");
			first = false;
		}
		if (!name.isBlank()) {
			b.append(first ? "" : ",").append("CustomName:").append(TextCodes.quote(name)).append(",CustomNameVisible:1b");
			first = false;
		}
		StringBuilder pose = new StringBuilder();
		for (int i = 0; i < PARTS.length; i++) {
			float[] p = POSE[i];
			if (p[0] == 0 && p[1] == 0 && p[2] == 0) continue;
			pose.append(pose.isEmpty() ? "" : ",").append(NBT_PARTS[i]).append(":[").append(p[0]).append("f,").append(p[1]).append("f,").append(p[2])
				.append("f]");
		}
		if (!pose.isEmpty()) {
			b.append(first ? "" : ",").append("Pose:{").append(pose).append('}');
			first = false;
		}
		StringBuilder eq = new StringBuilder();
		for (int s = 0; s < SLOTS.length; s++) {
			if (EQUIP[s].isBlank()) continue;
			String id = EQUIP[s].contains(":") ? EQUIP[s] : "minecraft:" + EQUIP[s];
			eq.append(eq.isEmpty() ? "" : ",").append(SLOTS[s]).append(":{id:\"").append(id).append("\"}");
		}
		if (!eq.isEmpty()) b.append(first ? "" : ",").append("equipment:{").append(eq).append('}');
		return b.append('}').toString();
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int half = half();
		label(g, fit("Winkel X · Y · Z in Grad", half), rightX(), top + 65);
		label(g, fit("Ausrüstung (z. B. diamond_helmet)", half), left, top + 65);
		// a stick figure seen from the front: limbs swing sideways by their Z angle; as big as the room below the angles allows
		int cx = rightX() + half / 2;
		int cy = top + 100;
		float s = Mth.clamp((contentBottom() - cy - 2) / 30F, 0.9F, 2.2F);
		boolean small = OPTION[1];
		if (small) s *= 0.6F;
		int col = 0xFFE8C08A;
		int neckY = Math.round(cy + 4 * s);
		int hipY = Math.round(cy + 16 * s);
		g.fill(Math.round(cx - 4 * s), cy, Math.round(cx + 4 * s), neckY, col);
		line(g, cx, neckY, cx, hipY, col);
		if (OPTION[0]) {
			limb(g, cx - Math.round(5 * s), neckY + 1, POSE[2], 12 * s, -1, col);
			limb(g, cx + Math.round(5 * s), neckY + 1, POSE[3], 12 * s, 1, col);
		}
		limb(g, cx - Math.round(2 * s), hipY, POSE[4], 12 * s, -1, col);
		limb(g, cx + Math.round(2 * s), hipY, POSE[5], 12 * s, 1, col);
		if (!OPTION[2]) g.fill(Math.round(cx - 8 * s), Math.round(hipY + 12 * s), Math.round(cx + 8 * s), Math.round(hipY + 13 * s), 0xFF9A9A9A);
	}

	private static void limb(GuiGraphicsExtractor g, int x, int y, float[] pose, float length, int side, int col) {
		double z = Math.toRadians(pose[2]) * side;
		double fore = Math.cos(Math.toRadians(pose[0]));
		int ex = x + (int) Math.round(Math.sin(z) * length * side);
		int ey = y + (int) Math.round(Math.cos(z) * length * fore);
		line(g, x, y, ex, ey, col);
	}

	private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int col) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		for (int i = 0; i <= steps; i++) {
			int x = x0 + (steps == 0 ? 0 : (x1 - x0) * i / steps);
			int y = y0 + (steps == 0 ? 0 : (y1 - y0) * i / steps);
			g.fill(x - 1, y - 1, x + 1, y + 1, col);
		}
	}
}
