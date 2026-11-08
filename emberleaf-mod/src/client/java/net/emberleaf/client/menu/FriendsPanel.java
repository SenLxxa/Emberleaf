package net.emberleaf.client.menu;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.emberleaf.client.feature.Badges;
import net.emberleaf.client.friends.FriendsSync;
import net.emberleaf.client.friends.FriendsSync.Friend;
import net.emberleaf.client.friends.FriendsSync.FriendsState;
import net.emberleaf.client.friends.Lagerfeuer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/**
 * The friends list of the main menu (like LabyMod's): everyone from the Lagerfeuer with head, badges and where they play,
 * online friends first. Click a friend on a server to play with them, any other friend to write to them; the header opens
 * the Lagerfeuer chat. Scrolls when there are more friends than fit.
 */
public class FriendsPanel extends AbstractWidget {
	private static final int ROW = 24;
	private static final int HEADER = 22;
	private static final int FOOTER = 18;
	private final Consumer<Friend> onJoin;
	private final int maxBottom;
	private float pulse;
	private int scroll;
	private int listTop;
	private int listHeight;

	/** {@code maxBottom}: the panel never grows below this y (the player figure stands there). */
	public FriendsPanel(int x, int y, int width, int maxBottom, Consumer<Friend> onJoin) {
		super(x, y, width, HEADER + 30, Component.literal("Freunde"));
		this.onJoin = onJoin;
		this.maxBottom = maxBottom;
	}

	private static String uuidWithDashes(String raw) {
		return raw.length() == 32
			? raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-" + raw.substring(20)
			: raw;
	}

	private static String ago(long seconds) {
		if (seconds <= 0) return "offline";
		long d = Math.max(0, System.currentTimeMillis() / 1000 - seconds);
		if (d < 3600) return "vor " + Math.max(1, d / 60) + " Min. online";
		if (d < 86400) return "vor " + d / 3600 + " Std. online";
		long days = d / 86400;
		return days == 1 ? "gestern online" : "vor " + days + " Tagen online";
	}

	private Lagerfeuer.@Nullable Party party() {
		return Lagerfeuer.party();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		FriendsState state = FriendsSync.friends();
		List<Friend> list = state.friends();
		boolean ready = state.status() == FriendsState.Status.READY && !list.isEmpty();
		Lagerfeuer.Party party = ready ? party() : null;
		int partyH = party != null ? ROW : 0;
		List<net.minecraft.util.FormattedCharSequence> hintLines = ready ? List.of() : hintLines(state.status());
		int wanted = ready ? partyH + list.size() * ROW + 4 : 8 + hintLines.size() * 11;
		int room = Math.max(ROW * 2, maxBottom - getY() - HEADER - FOOTER);
		listHeight = Math.min(wanted, room);
		this.height = HEADER + listHeight + (state.status() == FriendsState.Status.READY ? FOOTER : 0);
		listTop = getY() + HEADER + 2;
		scroll = Mth.clamp(scroll, 0, Math.max(0, wanted - listHeight));
		pulse += 0.05F;

		int x = getX();
		int y = getY();
		int w = getWidth();
		float fade = this.alpha;
		Font font = Ui.font();

		Ui.nineSlice(g, Ui.PANEL, x, y, w, height, 6, ARGB.white(fade));
		g.fill(x + 6, y + HEADER - 1, x + w - 6, y + HEADER, ARGB.multiplyAlpha(0x26FFE6C8, fade));

		// header: campfire glow, title, unread messages and requests, online count
		long online = list.stream().filter(Friend::online).count();
		MenuScene.glow(g, x + 11, y + 11, 14, ARGB.color(fade * (0.55F + 0.15F * (float) Math.sin(pulse)), 0xFF9A40));
		MenuScene.glow(g, x + 11, y + 11, 4, ARGB.color(fade, 0xFFE6A0));
		Component title = Ui.menu("Freunde");
		Ui.text(g, title, x + 20, y + 7, ARGB.multiplyAlpha(0xFFFFE9CC, fade));
		int rx = x + 26 + font.width(title);
		int unread = Lagerfeuer.unread();
		if (unread > 0) {
			Component msg = Ui.menu("✉ " + unread);
			Ui.nineSlice(g, Ui.ROUND, rx - 3, y + 5, font.width(msg) + 6, 12, 4, ARGB.color(fade, 0xE8742F));
			Ui.text(g, msg, rx, y + 7, ARGB.white(fade));
			rx += font.width(msg) + 10;
		}
		if (state.requests() > 0) {
			Component req = Ui.menu("+" + state.requests());
			Ui.nineSlice(g, Ui.BUTTON_HOVER, rx - 3, y + 5, font.width(req) + 6, 12, 4, ARGB.white(fade));
			Ui.text(g, req, rx, y + 7, ARGB.white(fade));
			rx += font.width(req) + 10;
		}
		// the online count on the right; short ("● 3") or left out when the badges need the room
		Component count = Ui.menu(switch (state.status()) {
			case READY -> online + " online";
			case LOADING -> "lädt …";
			default -> "";
		});
		if (rx > x + w - 8 - font.width(count) && state.status() == FriendsState.Status.READY) count = Ui.menu("● " + online);
		if (rx <= x + w - 8 - font.width(count)) {
			Ui.text(g, count, x + w - 8 - font.width(count), y + 7, ARGB.multiplyAlpha(online > 0 ? 0xFF9FE37A : 0xFFB8A48E, fade));
		}
		boolean headerHover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + HEADER;
		if (headerHover && state.status() != FriendsState.Status.NOT_CONFIGURED) {
			g.setTooltipForNextFrame(font, Component.literal("Klick: Lagerfeuer öffnen (Chat, Freunde, Party) – auch mit Taste U"), mouseX, mouseY);
		}

		int by = listTop;
		if (!ready) {
			for (int i = 0; i < hintLines.size(); i++) {
				g.text(font, hintLines.get(i), x + 9, by + 4 + i * 11, ARGB.multiplyAlpha(0xFFCDB89F, fade), false);
			}
		}
		if (ready) {
			g.enableScissor(x + 2, listTop, x + w - 2, listTop + listHeight);
			int ry = by - scroll;
			if (party != null) {
				drawParty(g, party, x, ry, w, mouseX, mouseY, fade);
				ry += ROW;
			}
			for (Friend f : list) {
				if (ry + ROW > listTop && ry < listTop + listHeight) drawFriend(g, f, x, ry, w, mouseX, mouseY, fade);
				ry += ROW;
			}
			g.disableScissor();
			if (wanted > listHeight) {
				int bar = Math.max(10, listHeight * listHeight / wanted);
				int barY = listTop + (listHeight - bar) * scroll / Math.max(1, wanted - listHeight);
				g.fill(x + w - 4, barY, x + w - 2, barY + bar, ARGB.color(fade * 0.5F, 0xFFE6C8));
			}
		}
		if (state.status() == FriendsState.Status.READY) {
			// footer: add friends (also while the list is still empty)
			int fy = y + height - FOOTER + 2;
			boolean footerHover = mouseX >= x && mouseX < x + w && mouseY >= fy && mouseY < y + height;
			g.fill(x + 6, fy - 2, x + w - 6, fy - 1, ARGB.multiplyAlpha(0x26FFE6C8, fade));
			Component add = Ui.menu("+ Freund hinzufügen");
			Ui.text(g, add, x + (w - font.width(add)) / 2F, fy + 2, ARGB.multiplyAlpha(footerHover ? 0xFFFFFFFF : 0xFFD8C2A8, fade));
		}
	}

	/** What the panel says while there is no list to show, wrapped to the panel's width. */
	private List<net.minecraft.util.FormattedCharSequence> hintLines(FriendsState.Status status) {
		String text = switch (status) {
			case NOT_CONFIGURED -> "Noch kein Lagerfeuer verbunden. Es kommt mit einem Emberleaf-Update von selbst.";
			case UNAVAILABLE -> "Das Lagerfeuer ist gerade nicht erreichbar. Einmal im Launcher anmelden hilft meistens.";
			case LOADING -> "Freunde werden geladen …";
			case READY -> "Noch keine Freunde. Füg unten jemanden mit seinem Minecraft-Namen hinzu.";
		};
		List<net.minecraft.util.FormattedCharSequence> lines = Ui.font().split(Ui.menu(text), getWidth() - 16);
		return lines.size() > 6 ? lines.subList(0, 6) : lines;
	}

	private void drawParty(GuiGraphicsExtractor g, Lagerfeuer.Party party, int x, int ry, int w, int mouseX, int mouseY, float fade) {
		boolean hovered = isHovered(x, ry, w, mouseX, mouseY);
		Ui.nineSlice(g, hovered ? Ui.BUTTON_HOVER : Ui.ROUND, x + 3, ry + 1, w - 6, ROW - 2, 5,
			hovered ? ARGB.white(fade * 0.8F) : ARGB.color(fade * 0.35F, 0xE8742F));
		String leader = party.members().stream().filter(m -> m.uuid().equals(party.leader())).map(Lagerfeuer.Member::name).findFirst().orElse("?");
		Ui.text(g, Ui.menu("Party · " + party.members().size() + (party.members().size() == 1 ? " Person" : " Leute")), x + 10, ry + 3,
			ARGB.multiplyAlpha(0xFFFFE9CC, fade));
		Ui.text(g, Ui.menu(trim(Ui.font(), "★ " + leader + (hovered ? " · Klick: Party-Chat" : ""), w - 20)), x + 10, ry + 13,
			ARGB.multiplyAlpha(0xFFFFD6A8, fade));
	}

	private boolean isHovered(int x, int ry, int w, int mouseX, int mouseY) {
		return mouseX >= x && mouseX < x + w && mouseY >= Math.max(ry, listTop) && mouseY < Math.min(ry + ROW, listTop + listHeight);
	}

	private void drawFriend(GuiGraphicsExtractor g, Friend f, int x, int ry, int w, int mouseX, int mouseY, float fade) {
		Font font = Ui.font();
		boolean hovered = isHovered(x, ry, w, mouseX, mouseY);
		if (hovered) Ui.nineSlice(g, Ui.BUTTON_HOVER, x + 3, ry + 1, w - 6, ROW - 2, 5, ARGB.white(fade * 0.8F));
		try {
			PlayerFaceExtractor.extractRenderState(g, ResolvableProfile.createUnresolved(UUID.fromString(uuidWithDashes(f.uuid()))), x + 8, ry + 4, 16);
		} catch (IllegalArgumentException ignored) {
			// kaputte UUID: ohne Kopf anzeigen
		}
		MenuScene.glow(g, x + 23, ry + 19, f.online() ? 9 : 0, ARGB.color(fade * 0.7F, 0x7DDC5A));
		MenuScene.glow(g, x + 23, ry + 19, 4.5F, ARGB.multiplyAlpha(f.online() ? 0xFF9FFF7A : 0xFF6B5D52, fade));
		// badges on the right of the name (as many as fit)
		Lagerfeuer.Profile profile;
		try {
			profile = Lagerfeuer.profile(UUID.fromString(uuidWithDashes(f.uuid())));
		} catch (IllegalArgumentException e) {
			profile = null;
		}
		Component badges = profile != null ? Badges.line(profile.badges()) : null;
		int badgesW = badges != null ? font.width(badges) + 4 : 0;
		Ui.text(g, Ui.menu(trim(font, f.name(), w - 40 - badgesW)), x + 30, ry + 3.5F, ARGB.multiplyAlpha(f.online() ? 0xFFFFE9CC : 0xFFA8957F, fade));
		if (badges != null) g.text(font, badges, x + w - 8 - font.width(badges), ry + 3, ARGB.white(fade), false);
		String where = !f.online() ? ago(f.lastSeen()) : f.canJoin() ? f.server() : "spielt Einzelspieler";
		if (hovered) where = f.canJoin() ? "Klick: Mitspielen" : "Klick: Nachricht schreiben";
		Ui.text(g, Ui.menu(trim(font, where, w - 38)), x + 30, ry + 13, ARGB.multiplyAlpha(f.canJoin() ? 0xFF9FE37A : 0xFF8D7865, fade));
	}

	private static String trim(Font font, String text, int max) {
		if (font.width(Ui.menu(text)) <= max) return text;
		while (!text.isEmpty() && font.width(Ui.menu(text + "…")) > max) text = text.substring(0, text.length() - 1);
		return text + "…";
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!isMouseOver(mouseX, mouseY)) return false;
		scroll -= (int) (scrollY * ROW);
		return true;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		FriendsState state = FriendsSync.friends();
		Minecraft mc = Minecraft.getInstance();
		if (state.status() == FriendsState.Status.NOT_CONFIGURED) return;
		if (event.y() < getY() + HEADER) {
			mc.gui.setScreen(new LagerfeuerScreen(mc.gui.screen(), null));
			return;
		}
		if (state.status() != FriendsState.Status.READY) return;
		if (event.y() >= getY() + height - FOOTER) {
			LagerfeuerScreen screen = new LagerfeuerScreen(mc.gui.screen(), null);
			mc.gui.setScreen(screen);
			screen.select("FRIENDS");
			return;
		}
		if (event.y() < listTop || event.y() >= listTop + listHeight) return;
		int index = (int) ((event.y() - listTop + scroll) / ROW);
		Lagerfeuer.Party party = party();
		if (party != null) {
			if (index == 0) {
				mc.gui.setScreen(new LagerfeuerScreen(mc.gui.screen(), party.chat()));
				return;
			}
			index--;
		}
		List<Friend> list = state.friends();
		if (index < 0 || index >= list.size()) return;
		Friend f = list.get(index);
		if (f.canJoin()) {
			onJoin.accept(f);
		} else {
			// not on a server: write to them instead
			LagerfeuerScreen screen = new LagerfeuerScreen(mc.gui.screen(), null);
			mc.gui.setScreen(screen);
			screen.startDirect(f.uuid(), f.name());
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
