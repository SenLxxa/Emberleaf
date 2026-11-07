package net.emberleaf.client.tools;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.emberleaf.client.menu.EmberButton;
import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Base of every Emberleaf tool: a warm panel with a title, the tool's own fields and a result line at the bottom
 * (copy it, or run it right away when it is a command and you are in a world).
 */
public abstract class ToolScreen extends Screen {
	protected final @Nullable Screen parent;
	private final String subtitle;
	protected int px;
	protected int py;
	protected int pw;
	protected int ph;
	/** Top-left of the tool's own area. */
	protected int left;
	protected int top;
	private @Nullable EditBox result;
	private String output = "";
	private String status = "";
	private long statusAt;

	protected ToolScreen(String title, String subtitle, @Nullable Screen parent) {
		super(Component.literal(title));
		this.subtitle = subtitle;
		this.parent = parent;
	}

	@Override
	protected final void init() {
		pw = Math.min(480, this.width - 16);
		ph = this.height - 16;
		px = (this.width - pw) / 2;
		py = 8;
		left = px + 12;
		top = py + 34;
		build();
		int by = py + ph - 28;
		if (hasResult()) {
			result = new EditBox(this.font, px + 12, by - 22, pw - 24, 16, Component.literal("Ergebnis"));
			result.setMaxLength(32767);
			result.setEditable(false);
			addRenderableWidget(result);
		}
		int bw = (pw - 24 - 8) / 3;
		if (hasResult()) {
			addRenderableWidget(new EmberButton(px + 12, by, bw, 20, Component.literal("Kopieren"), b -> {
				this.minecraft.keyboardHandler.setClipboard(output);
				status("In die Zwischenablage kopiert");
			}));
			EmberButton run = addRenderableWidget(new EmberButton(px + 12 + bw + 4, by, bw, 20, Component.literal("Ausführen"), b -> runOutput()));
			run.active = this.minecraft.player != null;
		}
		addRenderableWidget(new EmberButton(px + pw - 12 - bw, by, bw, 20, Component.literal("Zurück"), b -> onClose()));
		update();
	}

	/** Adds the tool's widgets (use {@link #left}, {@link #top}). Called again when the window size changes. */
	protected abstract void build();

	/** The result line (a command, code or text), or "" for nothing. */
	protected String compute() {
		return "";
	}

	protected boolean hasResult() {
		return true;
	}

	/** Recomputes the result; call after every change. */
	protected final void update() {
		output = compute();
		if (result != null) {
			result.setValue(output);
			result.moveCursorToStart(false);
		}
	}

	protected final void status(String text) {
		status = text;
		statusAt = System.currentTimeMillis();
	}

	private void runOutput() {
		if (this.minecraft.player == null || this.minecraft.getConnection() == null) return;
		if (!output.startsWith("/")) {
			status("Das ist kein Befehl – nutze Kopieren");
			return;
		}
		this.minecraft.getConnection().sendCommand(output.substring(1));
		status("Befehl gesendet (in Einzelspieler-Welten brauchst du Cheats)");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, 0xC8100A06);
		Ui.nineSlice(g, Ui.PANEL, px, py, pw, ph, 8, 0xF4FFFFFF);
		Ui.text(g, Ui.title(this.title.getString()), px + 12, py + 9, 0xFFFFE6C4);
		Component sub = Ui.menu(subtitle);
		int sx = px + 12 + Ui.font().width(Ui.title(this.title.getString())) + 8;
		if (sx + Ui.font().width(sub) < px + pw - 10) Ui.text(g, sub, sx, py + 10, 0xFFB8A48E);
		drawContent(g, mouseX, mouseY);
		if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 3500) {
			Component s = Ui.menu(status);
			Ui.text(g, s, px + pw / 2F - Ui.font().width(s) / 2F, py + ph - 62, 0xFF9FE37A);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	/** The tool's own drawing (previews, labels). */
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

	protected void label(GuiGraphicsExtractor g, String text, int x, int y) {
		Ui.text(g, Ui.menu(text), x, y, 0xFFFFD6A8);
	}

	protected EditBox field(int x, int y, int w, String hint, String value, Consumer<String> onChange) {
		EditBox box = new EditBox(this.font, x, y, w, 16, Component.literal(hint));
		box.setMaxLength(1024);
		box.setValue(value);
		box.setHint(Component.literal(hint));
		box.setResponder(v -> {
			onChange.accept(v);
			update();
		});
		return addRenderableWidget(box);
	}

	/** Lowest y the tool's own content may use: below it come the result line and the buttons. */
	protected int contentBottom() {
		return py + ph - 54;
	}

	protected EmberButton button(int x, int y, int w, String label, Runnable action) {
		EmberButton b = addRenderableWidget(new EmberButton(x, y, w, 18, Component.literal(fit(label, w - 20)), btn -> {
			action.run();
			update();
		}));
		if (!fit(label, w - 20).equals(label)) b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label)));
		return b;
	}

	/** A label that cycles through options on click (labels that are too long for the button are shortened with "…"). */
	protected EmberButton cycle(int x, int y, int w, String[] labels, int index, IntConsumer onChange) {
		int[] i = {index};
		EmberButton[] self = new EmberButton[1];
		self[0] = addRenderableWidget(new EmberButton(x, y, w, 18, Component.literal(fit(labels[index], w - 30) + "  ›"), b -> {
			i[0] = (i[0] + 1) % labels.length;
			b.setMessage(Component.literal(fit(labels[i[0]], w - 30) + "  ›"));
			onChange.accept(i[0]);
			update();
		}));
		return self[0];
	}

	/** A row of colour squares (any 16 colours, e.g. the chat colours): click one to pick it. */
	public static class Swatches extends AbstractWidget {
		private final int[] rgb;
		private final String[] names;
		private final int cell;
		private final IntConsumer onPick;
		private int selected;

		public Swatches(int x, int y, int cell, int[] rgb, String[] names, int selected, IntConsumer onPick) {
			super(x, y, cell * rgb.length, cell, Component.literal("Farben"));
			this.rgb = rgb;
			this.names = names;
			this.cell = cell;
			this.selected = selected;
			this.onPick = onPick;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			for (int i = 0; i < rgb.length; i++) {
				int x = getX() + i * cell;
				g.fill(x, getY(), x + cell - 1, getY() + cell - 1, 0xFF000000 | rgb[i]);
				if (i == selected) g.fill(x, getY() + cell - 3, x + cell - 1, getY() + cell - 1, 0xFFFFFFFF);
				if (mouseX >= x && mouseX < x + cell && mouseY >= getY() && mouseY < getY() + cell) {
					g.fill(x, getY(), x + cell - 1, getY() + 1, 0xFFFFFFFF);
					g.setTooltipForNextFrame(Ui.font(), Component.literal(names[i]), mouseX, mouseY);
				}
			}
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			int i = (int) ((event.x() - getX()) / cell);
			if (i >= 0 && i < rgb.length) {
				selected = i;
				onPick.accept(i);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	/** Shortens a text with "…" until it fits into max pixels (menu font). */
	protected static String fit(String text, int max) {
		if (Ui.font().width(Ui.menu(text)) <= max) return text;
		while (!text.isEmpty() && Ui.font().width(Ui.menu(text + "…")) > max) text = text.substring(0, text.length() - 1);
		return text + "…";
	}

	protected static int parseInt(String text, int fallback) {
		try {
			return Integer.parseInt(text.strip());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	protected static double parseDouble(String text, double fallback) {
		try {
			return Double.parseDouble(text.strip().replace(',', '.'));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** German names of the 16 dye colours. */
	public static String dyeName(DyeColor c) {
		return switch (c) {
			case WHITE -> "Weiß";
			case ORANGE -> "Orange";
			case MAGENTA -> "Magenta";
			case LIGHT_BLUE -> "Hellblau";
			case YELLOW -> "Gelb";
			case LIME -> "Hellgrün";
			case PINK -> "Rosa";
			case GRAY -> "Grau";
			case LIGHT_GRAY -> "Hellgrau";
			case CYAN -> "Türkis";
			case PURPLE -> "Violett";
			case BLUE -> "Blau";
			case BROWN -> "Braun";
			case GREEN -> "Grün";
			case RED -> "Rot";
			case BLACK -> "Schwarz";
		};
	}

	/** The 16 dye colours as clickable squares. */
	public static class ColorChips extends AbstractWidget {
		private final Consumer<DyeColor> onPick;
		private @Nullable DyeColor selected;
		private final int cell;

		public ColorChips(int x, int y, int cell, @Nullable DyeColor selected, Consumer<DyeColor> onPick) {
			super(x, y, cell * 16, cell, Component.literal("Farben"));
			this.cell = cell;
			this.selected = selected;
			this.onPick = onPick;
		}

		public void select(@Nullable DyeColor c) {
			selected = c;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			for (DyeColor c : DyeColor.values()) {
				int x = getX() + c.getId() * cell;
				g.fill(x, getY(), x + cell - 1, getY() + cell - 1, 0xFF000000 | c.getTextureDiffuseColor());
				if (c == selected) {
					g.fill(x, getY() + cell - 3, x + cell - 1, getY() + cell - 1, 0xFFFFFFFF);
				}
				if (mouseX >= x && mouseX < x + cell && mouseY >= getY() && mouseY < getY() + cell) {
					g.fill(x, getY(), x + cell - 1, getY() + 1, 0xFFFFFFFF);
					g.setTooltipForNextFrame(Ui.font(), Component.literal(dyeName(c)), mouseX, mouseY);
				}
			}
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			int i = (int) ((event.x() - getX()) / cell);
			if (i >= 0 && i < 16) {
				selected = DyeColor.byId(i);
				onPick.accept(selected);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	/** Helper colour for previews. */
	protected static int opaque(int rgb) {
		return ARGB.opaque(rgb);
	}
}
