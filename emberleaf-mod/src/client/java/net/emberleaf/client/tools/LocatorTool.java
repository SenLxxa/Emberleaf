package net.emberleaf.client.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ARGB;

/** The colour each player gets on the locator bar (same formula as the game: from the UUID). */
public class LocatorTool extends ToolScreen {
	private static String input = "";

	public LocatorTool(Screen parent) {
		super("Locator-Bar-Farbe", "Markerfarbe jedes Spielers", parent);
	}

	static int color(UUID uuid) {
		return ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9F);
	}

	@Override
	protected void build() {
		field(left, top + 12, pw - 24, "UUID, oder Name eines Spielers in deiner Welt", input, v -> input = v.strip());
	}

	private UUID resolve() {
		if (input.isEmpty()) return this.minecraft.player != null ? this.minecraft.player.getUUID() : this.minecraft.getUser().getProfileId();
		try {
			String t = input.replace("-", "");
			if (t.length() == 32) {
				return new UUID(Long.parseUnsignedLong(t.substring(0, 16), 16), Long.parseUnsignedLong(t.substring(16), 16));
			}
		} catch (NumberFormatException ignored) {
			// a name then
		}
		for (PlayerInfo info : players()) {
			if (info.getProfile().name().equalsIgnoreCase(input)) return info.getProfile().id();
		}
		return null;
	}

	private List<PlayerInfo> players() {
		return this.minecraft.getConnection() == null ? List.of() : new ArrayList<>(this.minecraft.getConnection().getOnlinePlayers());
	}

	@Override
	protected String compute() {
		UUID uuid = resolve();
		return uuid == null ? "" : TextCodes.hex(color(uuid));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Leer lassen = du selbst", left, top);
		UUID uuid = resolve();
		if (uuid != null) {
			int c = color(uuid);
			g.fill(left, top + 40, left + 40, top + 80, ARGB.opaque(c));
			Ui.text(g, Ui.menu(TextCodes.hex(c)), left + 48, top + 46, 0xFFFFE6C4);
			Ui.text(g, Ui.menu(uuid.toString()), left + 48, top + 60, 0xFFB8A48E);
		} else {
			Ui.text(g, Ui.menu("Spieler nicht in dieser Welt – gib seine UUID ein (Offline-Server: "
				+ UUIDUtil.createOfflinePlayerUUID(input.isEmpty() ? "Steve" : input) + ")"), left, top + 46, 0xFFB8A48E);
		}
		List<PlayerInfo> list = players();
		if (!list.isEmpty()) {
			label(g, "Spieler in dieser Welt", left, top + 92);
			for (int i = 0; i < Math.min(8, list.size()); i++) {
				PlayerInfo p = list.get(i);
				int y = top + 106 + i * 12;
				g.fill(left, y, left + 9, y + 9, ARGB.opaque(color(p.getProfile().id())));
				Ui.text(g, Ui.menu(p.getProfile().name()), left + 14, y, 0xFFFFEBD4);
			}
		}
	}
}
