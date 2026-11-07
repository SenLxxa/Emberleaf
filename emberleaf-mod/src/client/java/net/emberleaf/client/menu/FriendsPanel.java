package net.emberleaf.client.menu;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.emberleaf.client.friends.FriendsSync;
import net.emberleaf.client.friends.FriendsSync.Friend;
import net.emberleaf.client.friends.FriendsSync.FriendsState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.component.ResolvableProfile;

/** "Lagerfeuer" box in the main menu: who is online, and a click joins their server. */
public class FriendsPanel extends AbstractWidget {
	private static final int ROW = 22;
	private static final int HEADER = 22;
	private static final int MAX_ROWS = 6;
	private final Consumer<Friend> onJoin;
	private float pulse;

	public FriendsPanel(int x, int y, int width, Consumer<Friend> onJoin) {
		super(x, y, width, HEADER + 30, Component.literal("Lagerfeuer"));
		this.onJoin = onJoin;
	}

	private static String uuidWithDashes(String raw) {
		return raw.length() == 32
			? raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-" + raw.substring(20)
			: raw;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		FriendsState state = FriendsSync.friends();
		List<Friend> list = state.friends().subList(0, Math.min(MAX_ROWS, state.friends().size()));
		int bodyH = state.status() == FriendsState.Status.READY && !list.isEmpty() ? list.size() * ROW + 6 : 32;
		this.height = HEADER + bodyH;
		pulse += 0.05F;

		int x = getX();
		int y = getY();
		int w = getWidth();
		float fade = this.alpha;
		Font font = Ui.font();

		Ui.nineSlice(g, Ui.PANEL, x, y, w, height, 6, ARGB.white(fade));
		g.fill(x + 6, y + HEADER - 1, x + w - 6, y + HEADER, ARGB.multiplyAlpha(0x26FFE6C8, fade));

		long online = state.friends().stream().filter(Friend::online).count();
		MenuScene.glow(g, x + 11, y + 11, 14, ARGB.color(fade * (0.55F + 0.15F * (float) Math.sin(pulse)), 0xFF9A40));
		MenuScene.glow(g, x + 11, y + 11, 4, ARGB.color(fade, 0xFFE6A0));
		Ui.text(g, Ui.menu("Lagerfeuer"), x + 20, y + 7, ARGB.multiplyAlpha(0xFFFFE9CC, fade));
		Component count = Ui.menu(switch (state.status()) {
			case READY -> online + " online";
			case LOADING -> "lädt …";
			default -> "";
		});
		Ui.text(g, count, x + w - 8 - font.width(count), y + 7, ARGB.multiplyAlpha(online > 0 ? 0xFF9FE37A : 0xFFB8A48E, fade));
		int rx = x + 26 + font.width(Ui.menu("Lagerfeuer"));
		if (state.requests() > 0) {
			Component req = Ui.menu("+" + state.requests());
			Ui.nineSlice(g, Ui.BUTTON_HOVER, rx - 3, y + 5, font.width(req) + 6, 12, 4, ARGB.white(fade));
			Ui.text(g, req, rx, y + 7, ARGB.white(fade));
			rx += font.width(req) + 10;
		}
		int unread = net.emberleaf.client.friends.Lagerfeuer.unread();
		if (unread > 0) {
			// unread chat messages: a click on the header opens the Lagerfeuer chat
			Component msg = Ui.menu(unread + (unread == 1 ? " Nachricht" : " Nachr."));
			Ui.nineSlice(g, Ui.ROUND, rx - 3, y + 5, font.width(msg) + 6, 12, 4, ARGB.color(fade, 0xE8742F));
			Ui.text(g, msg, rx, y + 7, ARGB.white(fade));
		}
		boolean headerHover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + HEADER;
		if (headerHover && state.status() != FriendsState.Status.NOT_CONFIGURED) {
			g.setTooltipForNextFrame(font, Component.literal("Klick: Lagerfeuer-Chat, Freunde und Party"), mouseX, mouseY);
		}

		int by = y + HEADER + 3;
		switch (state.status()) {
			case NOT_CONFIGURED -> hint(g, by, "Im Emberleaf-Launcher", "unter Freunde einrichten", fade);
			case UNAVAILABLE -> hint(g, by, "Gerade nicht erreichbar", "Melde dich im Launcher an", fade);
			case LOADING -> hint(g, by, "Freunde werden geladen …", "", fade);
			case READY -> {
				if (list.isEmpty()) {
					hint(g, by, "Noch keine Freunde.", "Füg sie im Launcher hinzu", fade);
					break;
				}
				for (int i = 0; i < list.size(); i++) {
					Friend f = list.get(i);
					int ry = by + i * ROW;
					boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW;
					if (hovered && f.canJoin()) Ui.nineSlice(g, Ui.BUTTON_HOVER, x + 3, ry, w - 6, ROW, 5, ARGB.white(fade * 0.8F));
					try {
						PlayerFaceExtractor.extractRenderState(g, ResolvableProfile.createUnresolved(UUID.fromString(uuidWithDashes(f.uuid()))),
							x + 8, ry + 3, 16);
					} catch (IllegalArgumentException ignored) {
						// kaputte UUID: ohne Kopf anzeigen
					}
					MenuScene.glow(g, x + 23, ry + 18, f.online() ? 9 : 0, ARGB.color(fade * 0.7F, 0x7DDC5A));
					MenuScene.glow(g, x + 23, ry + 18, 4.5F, ARGB.multiplyAlpha(f.online() ? 0xFF9FFF7A : 0xFF6B5D52, fade));
					Ui.text(g, Ui.menu(f.name()), x + 30, ry + 2.5F, ARGB.multiplyAlpha(f.online() ? 0xFFFFE9CC : 0xFFA8957F, fade));
					String where = !f.online() ? "offline" : f.canJoin() ? f.server() : "Einzelspieler";
					if (hovered && f.canJoin()) where = "Klick: Mitspielen";
					Ui.text(g, Ui.menu(trim(font, where, w - 38)), x + 30, ry + 11.5F,
						ARGB.multiplyAlpha(f.canJoin() ? 0xFF9FE37A : 0xFF8D7865, fade));
				}
			}
		}
	}

	private static String trim(Font font, String text, int max) {
		if (font.width(Ui.menu(text)) <= max) return text;
		while (!text.isEmpty() && font.width(Ui.menu(text + "…")) > max) text = text.substring(0, text.length() - 1);
		return text + "…";
	}

	private void hint(GuiGraphicsExtractor g, int y, String line1, String line2, float fade) {
		Ui.text(g, Ui.menu(line1), getX() + 9, y + 2, ARGB.multiplyAlpha(0xFFD8C2A8, fade));
		Ui.text(g, Ui.menu(line2), getX() + 9, y + 13, ARGB.multiplyAlpha(0xFF9A8672, fade));
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		FriendsState state = FriendsSync.friends();
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (event.y() < getY() + HEADER) {
			if (state.status() != FriendsState.Status.NOT_CONFIGURED) mc.gui.setScreen(new LagerfeuerScreen(mc.gui.screen(), null));
			return;
		}
		if (state.status() != FriendsState.Status.READY) return;
		int index = (int) ((event.y() - getY() - HEADER - 3) / ROW);
		if (index >= 0 && index < Math.min(MAX_ROWS, state.friends().size())) {
			Friend f = state.friends().get(index);
			if (f.canJoin()) {
				onJoin.accept(f);
			} else {
				// not on a server: write to them instead
				LagerfeuerScreen screen = new LagerfeuerScreen(mc.gui.screen(), null);
				mc.gui.setScreen(screen);
				screen.startDirect(f.uuid(), f.name());
			}
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
