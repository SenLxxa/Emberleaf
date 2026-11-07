package net.emberleaf.client.tools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Pixel-exact circles, ellipses and spheres (layer by layer) for building. */
public class CircleTool extends ToolScreen {
	private static final String[] MODES = {"Kreis", "Ellipse", "Kugel"};
	private static final String[] FILL = {"Hohl (dünn)", "Hohl (dick)", "Gefüllt"};
	private static int mode;
	private static int fill;
	private static int w = 21;
	private static int h = 13;
	private static int layer;

	public CircleTool(Screen parent) {
		super("Kreis-Generator", "Kreise, Ellipsen und Kugeln", parent);
	}

	@Override
	protected void build() {
		cycle(left, top + 12, 110, MODES, mode, v -> {
			mode = v;
			rebuildWidgets();
		});
		cycle(left + 114, top + 12, 120, FILL, fill, v -> fill = v);
		field(left + 238, top + 12, 50, mode == 2 ? "Durchm." : "Breite", String.valueOf(w), v -> w = Math.max(1, Math.min(101, parseInt(v, 1))));
		if (mode == 1) field(left + 292, top + 12, 50, "Höhe", String.valueOf(h), v -> h = Math.max(1, Math.min(101, parseInt(v, 1))));
		if (mode == 2) {
			button(left + 292, top + 12, 24, "−", () -> layer = Math.max(0, layer - 1));
			button(left + 320, top + 12, 24, "+", () -> layer = Math.min(w - 1, layer + 1));
		}
	}

	private int width() {
		return w;
	}

	private int height() {
		return mode == 1 ? h : w;
	}

	/** Which cells of the current shape (or sphere layer) are blocks. */
	private boolean[][] grid() {
		int gw = width();
		int gh = height();
		double rx = gw / 2.0;
		double rz = gh / 2.0;
		if (mode == 2) {
			// the circle where the sphere cuts this layer
			double r = w / 2.0;
			double dy = layer + 0.5 - r;
			double layerR = Math.sqrt(Math.max(0, r * r - dy * dy));
			rx = rz = layerR;
		}
		boolean[][] inside = new boolean[gw][gh];
		for (int x = 0; x < gw; x++) {
			for (int z = 0; z < gh; z++) {
				double dx = (x + 0.5 - gw / 2.0) / Math.max(0.01, rx);
				double dz = (z + 0.5 - gh / 2.0) / Math.max(0.01, rz);
				inside[x][z] = dx * dx + dz * dz <= 1.0;
			}
		}
		if (fill == 2) return inside;
		boolean[][] out = new boolean[gw][gh];
		for (int x = 0; x < gw; x++) {
			for (int z = 0; z < gh; z++) {
				if (!inside[x][z]) continue;
				boolean edge = false;
				for (int dx = -1; dx <= 1 && !edge; dx++) {
					for (int dz = -1; dz <= 1 && !edge; dz++) {
						if (fill == 0 && dx != 0 && dz != 0) continue; // thin: only the four sides count
						int nx = x + dx;
						int nz = z + dz;
						edge = nx < 0 || nz < 0 || nx >= gw || nz >= gh || !inside[nx][nz];
					}
				}
				out[x][z] = edge;
			}
		}
		return out;
	}

	@Override
	protected String compute() {
		boolean[][] g = grid();
		int n = 0;
		for (boolean[] col : g) for (boolean b : col) if (b) n++;
		return MODES[mode] + " " + width() + (mode == 1 ? "×" + height() : "") + (mode == 2 ? ", Schicht " + (layer + 1) + " von " + w : "") + ": " + n
			+ " Blöcke";
	}

	@Override
	protected boolean hasResult() {
		return true;
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Form, Füllung und Größe" + (mode == 2 ? " · Schicht " + (layer + 1) + "/" + w : ""), left, top);
		boolean[][] cells = grid();
		int gw = width();
		int gh = height();
		int areaW = pw - 24;
		int areaH = ph - 34 - 40 - 70;
		int cell = Math.max(2, Math.min(12, Math.min(areaW / gw, areaH / gh)));
		int ox = left + (areaW - cell * gw) / 2;
		int oy = top + 40;
		g.fill(ox - 2, oy - 2, ox + cell * gw + 2, oy + cell * gh + 2, 0xFF1A120C);
		for (int x = 0; x < gw; x++) {
			for (int z = 0; z < gh; z++) {
				int c = cells[x][z] ? ((x + z) % 2 == 0 ? 0xFFE8742F : 0xFFD8642A) : ((x + z) % 2 == 0 ? 0xFF2A1E16 : 0xFF261A13);
				g.fill(ox + x * cell, oy + z * cell, ox + x * cell + cell - (cell > 4 ? 1 : 0), oy + z * cell + cell - (cell > 4 ? 1 : 0), c);
			}
		}
		// the middle marked
		g.fill(ox + gw * cell / 2 - 1, oy, ox + gw * cell / 2, oy + gh * cell, 0x60FFFFFF);
		g.fill(ox, oy + gh * cell / 2 - 1, ox + gw * cell, oy + gh * cell / 2, 0x60FFFFFF);
	}
}
