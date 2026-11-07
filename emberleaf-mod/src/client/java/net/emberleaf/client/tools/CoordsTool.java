package net.emberleaf.client.tools;

import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2fStack;

/** Nether ↔ Overworld coordinates (1 block in the Nether = 8 in the Overworld). */
public class CoordsTool extends ToolScreen {
	private static boolean toNether = true;
	private static String x = "800";
	private static String y = "64";
	private static String z = "-240";

	public CoordsTool(Screen parent) {
		super("Koordinaten", "Nether und Oberwelt umrechnen", parent);
	}

	@Override
	protected void build() {
		cycle(left, top + 12, 200, new String[] {"Oberwelt → Nether", "Nether → Oberwelt"}, toNether ? 0 : 1, v -> toNether = v == 0);
		button(left + 206, top + 12, 140, "Meine Position", () -> {
			if (this.minecraft.player == null) {
				status("Nur in einer Welt möglich");
				return;
			}
			x = String.valueOf(this.minecraft.player.getBlockX());
			y = String.valueOf(this.minecraft.player.getBlockY());
			z = String.valueOf(this.minecraft.player.getBlockZ());
			toNether = this.minecraft.level.dimension() != Level.NETHER;
			rebuildWidgets();
		});
		int w = (pw - 24 - 8) / 3;
		field(left, top + 48, w, "X", x, v -> x = v);
		field(left + w + 4, top + 48, w, "Y", y, v -> y = v);
		field(left + 2 * (w + 4), top + 48, w, "Z", z, v -> z = v);
	}

	private long[] result() {
		double k = toNether ? 1 / 8.0 : 8;
		return new long[] {Math.round(Math.floor(parseDouble(x, 0) * k)), Math.round(parseDouble(y, 64)), Math.round(Math.floor(parseDouble(z, 0) * k))};
	}

	@Override
	protected String compute() {
		long[] r = result();
		return r[0] + " " + r[1] + " " + r[2];
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Richtung", left, top);
		label(g, toNether ? "Koordinaten in der Oberwelt" : "Koordinaten im Nether", left, top + 38);
		long[] r = result();
		label(g, toNether ? "Dein Portal im Nether:" : "Dein Portal in der Oberwelt:", left, top + 80);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(left, top + 94);
		pose.scale(2, 2);
		Ui.text(g, Ui.menu("X " + r[0] + "   Y " + r[1] + "   Z " + r[2]), 0, 0, 0xFFFFE6C4);
		pose.popMatrix();
		Ui.text(g, Ui.menu("Tipp: Baue das Portal genau dort, dann landest du beim passenden Portal."), left, top + 124, 0xFFB8A48E);
	}
}
