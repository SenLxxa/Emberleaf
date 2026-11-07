package net.emberleaf.client.tools;

import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** A guide tool: a scrolling list on the left, the details of the chosen entry on the right. */
public abstract class ListTool extends ToolScreen {
	private static final int ROW = 12;
	protected int selected;
	private int scroll;

	protected ListTool(String title, String subtitle, Screen parent) {
		super(title, subtitle, parent);
	}

	protected abstract int size();

	protected abstract Component name(int i);

	protected int listW() {
		return Math.min(170, pw / 3);
	}

	private int listH() {
		return ph - 34 - 74;
	}

	/** Called after the selection changed (rebuild per-entry widgets). */
	protected void selectionChanged() {
		rebuildWidgets();
	}

	/** Left edge of the details area. */
	protected int detailX() {
		return left + listW() + 10;
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int lw = listW();
		int lh = listH();
		g.fill(left - 2, top - 2, left + lw, top + lh, 0x50000000);
		g.enableScissor(left - 2, top - 2, left + lw, top + lh);
		for (int i = 0; i < size(); i++) {
			int y = top + i * ROW - scroll;
			if (y + ROW < top || y > top + lh) continue;
			boolean hover = mouseX >= left && mouseX < left + lw && mouseY >= y && mouseY < y + ROW && mouseY >= top && mouseY < top + lh;
			if (i == selected) g.fill(left - 2, y - 1, left + lw, y + ROW - 1, 0x90E8742F);
			else if (hover) g.fill(left - 2, y - 1, left + lw, y + ROW - 1, 0x40FFFFFF);
			Ui.text(g, Ui.menu(name(i)), left + 2, y + 1, 0xFFFFEBD4);
		}
		g.disableScissor();
		drawDetails(g, detailX(), top, mouseX, mouseY);
	}

	protected abstract void drawDetails(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY);

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.x() >= left && event.x() < left + listW() && event.y() >= top && event.y() < top + listH()) {
			int i = (int) ((event.y() - top + scroll) / ROW);
			if (i >= 0 && i < size()) {
				selected = i;
				selectionChanged();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < left + listW()) {
			scroll = Mth.clamp(scroll - (int) (scrollY * ROW * 2), 0, Math.max(0, size() * ROW - listH()));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
}
