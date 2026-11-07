package net.emberleaf.client.feature;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.emberleaf.client.EmberleafClient;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.menu.Ui;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * Chat helpers: chat peek (hold Z to read the whole chat while playing), a symbol picker next to the chat input
 * (recently used first) and AutoText (up to five keys that send a prepared message or command).
 */
public final class ChatExtras {
	public static final String[] SYMBOLS = {
		"★", "☆", "✦", "✧", "♥", "❤", "☺", "☹", "✔", "✘", "➜", "←", "↑", "→", "↓", "♪", "♫", "☀", "☁", "☂",
		"❄", "⚡", "☕", "⚔", "☠", "♛", "♚", "✿", "❀", "☘", "⌚", "⌛", "✉", "☎", "✎", "∞", "≈", "≠", "±", "×",
		"÷", "°", "❖", "☮", "☯", "✌", "☝", "♠", "♣", "♦"};
	public static final int AUTOTEXT_SLOTS = 5;
	private static @Nullable KeyMapping peek;
	private static final KeyMapping[] AUTOTEXT = new KeyMapping[AUTOTEXT_SLOTS];

	private ChatExtras() {}

	public static void register() {
		peek = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.emberleaf.chat_peek", InputConstants.Type.KEYBOARD, InputConstants.KEY_Z,
			EmberleafClient.CATEGORY));
		for (int i = 0; i < AUTOTEXT_SLOTS; i++) {
			AUTOTEXT[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.emberleaf.autotext_" + (i + 1), InputConstants.Type.KEYBOARD,
				InputConstants.UNKNOWN.getValue(), EmberleafClient.CATEGORY));
		}
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof ChatScreen && Modules.on("chat_symbols")) addSymbolPicker(screen, w, h);
		});
	}

	/** Chat peek: the chat shows everything (like when it is open) while the key is held. */
	public static boolean peeking() {
		Minecraft mc = Minecraft.getInstance();
		return peek != null && peek.isDown() && mc.gui.screen() == null && mc.player != null && Modules.on("chat_peek");
	}

	public static void tick(Minecraft mc) {
		for (int i = 0; i < AUTOTEXT_SLOTS; i++) {
			while (AUTOTEXT[i].consumeClick()) send(mc, autoText(i));
		}
	}

	public static KeyMapping autoTextKey(int slot) {
		return AUTOTEXT[slot];
	}

	public static String autoText(int slot) {
		String text = Modules.choice("autotext_" + (slot + 1));
		return text == null ? "" : text;
	}

	public static void setAutoText(int slot, String text) {
		Modules.setChoice("autotext_" + (slot + 1), text.strip());
	}

	private static void send(Minecraft mc, String text) {
		if (!Modules.on("autotext") || text.isBlank() || mc.player == null || mc.getConnection() == null || mc.gui.screen() != null) return;
		if (text.startsWith("/")) mc.getConnection().sendCommand(text.substring(1));
		else mc.getConnection().sendChat(text);
	}

	// ---------- Symbole ----------

	private static List<String> ordered() {
		Set<String> out = new LinkedHashSet<>();
		String recent = Modules.choice("chat_symbols_recent");
		if (recent != null) recent.codePoints().forEach(c -> out.add(new String(Character.toChars(c))));
		for (String s : SYMBOLS) out.add(s);
		return new ArrayList<>(out);
	}

	private static void used(String symbol) {
		String recent = Modules.choice("chat_symbols_recent");
		StringBuilder b = new StringBuilder(symbol);
		if (recent != null) {
			recent.codePoints().filter(c -> !new String(Character.toChars(c)).equals(symbol)).limit(9).forEach(b::appendCodePoint);
		}
		Modules.setChoice("chat_symbols_recent", b.toString());
	}

	private static void addSymbolPicker(Screen screen, int w, int h) {
		EditBox input = null;
		for (var widget : Screens.getWidgets(screen)) if (widget instanceof EditBox box) input = box;
		if (input == null) return;
		SymbolPanel panel = new SymbolPanel(w - 2 - 10 * 15, h - 30 - 5 * 15, input);
		panel.visible = false;
		Screens.getWidgets(screen).add(panel);
		Screens.getWidgets(screen).add(new Toggle(w - 22, h - 28, panel));
	}

	private static final class Toggle extends AbstractWidget {
		private final SymbolPanel panel;

		Toggle(int x, int y, SymbolPanel panel) {
			super(x, y, 20, 13, Component.literal("★"));
			this.panel = panel;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() || panel.visible ? 0xC0E8742F : 0x90000000);
			g.text(Minecraft.getInstance().font, "★", getX() + 6, getY() + 3, 0xFFFFE6C4, false);
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			panel.visible = !panel.visible;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	private static final class SymbolPanel extends AbstractWidget {
		private static final int CELL = 15;
		private final EditBox input;

		SymbolPanel(int x, int y, EditBox input) {
			super(x, y, CELL * 10, CELL * 5, Component.literal("Symbole"));
			this.input = input;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			Ui.nineSlice(g, Ui.PANEL, getX() - 3, getY() - 3, width + 6, height + 6, 5, 0xF0FFFFFF);
			List<String> symbols = ordered();
			for (int i = 0; i < Math.min(50, symbols.size()); i++) {
				int cx = getX() + (i % 10) * CELL;
				int cy = getY() + (i / 10) * CELL;
				boolean hover = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
				if (hover) g.fill(cx, cy, cx + CELL, cy + CELL, 0x80E8742F);
				int color = i < 10 && Modules.choice("chat_symbols_recent") != null ? 0xFFFFD6A8 : 0xFFFFFFFF;
				g.text(Minecraft.getInstance().font, symbols.get(i), cx + 4, cy + 4, ARGB.opaque(color), false);
			}
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			int col = (int) ((event.x() - getX()) / CELL);
			int row = (int) ((event.y() - getY()) / CELL);
			List<String> symbols = ordered();
			int i = row * 10 + col;
			if (col < 0 || col >= 10 || row < 0 || i >= symbols.size()) return;
			input.insertText(symbols.get(i));
			used(symbols.get(i));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
