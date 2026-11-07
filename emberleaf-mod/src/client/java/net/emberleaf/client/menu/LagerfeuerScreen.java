package net.emberleaf.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.feature.Badges;
import net.emberleaf.client.feature.Playtime;
import net.emberleaf.client.friends.FriendsSync;
import net.emberleaf.client.friends.Lagerfeuer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The Lagerfeuer inside the game, like Laby-Chat: chats with friends and groups (reactions, replies, read receipts,
 * Tic-Tac-Toe and Vier gewinnt, server invites), your friends with their playtime and badges, and the party.
 * Draws everything itself (immediate mode): every frame collects the clickable areas in {@link #hits}.
 */
public class LagerfeuerScreen extends Screen {
	private enum Tab { CHATS, FRIENDS, PARTY }

	private static final String[] REACTIONS = {"heart", "laugh", "like", "fire", "wow", "sad"};
	private static final String[] REACTION_NAMES = {"Herz", "Lachen", "Daumen hoch", "Feuer", "Wow", "Traurig"};
	private static final String[] CONTRAIL_NAMES = {"Weiß", "Glut", "Himmel", "Rosa", "Minze", "Violett", "Gold", "Regenbogen"};
	private static final int POLL_MS = 3000;
	private static final int BG = 0xC8100A06;
	private static final int TEXT = 0xFFFFEBD4;
	private static final int MUTED = 0xFFB8A48E;
	private static final int ACCENT = 0xFFFFD6A8;
	private static final int GREEN = 0xFF9FE37A;

	private record Hit(int x, int y, int w, int h, Runnable action, boolean menu) {
		Hit(int x, int y, int w, int h, Runnable action) {
			this(x, y, w, h, action, drawingMenu);
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	/** True while the little menus are drawn: their clickable areas keep the menus open. */
	private static boolean drawingMenu;

	private final @Nullable Screen parent;
	private Tab tab = Tab.CHATS;
	private List<JsonObject> chats = List.of();
	private @Nullable JsonObject friends;
	private @Nullable JsonObject party;
	private @Nullable String chatId;
	private @Nullable JsonObject chat;
	private @Nullable String dmPartner;
	private @Nullable String dmPartnerName;
	private @Nullable String friendId;
	private boolean inviting;
	private @Nullable JsonObject replyTo;
	private @Nullable JsonObject actionsFor;
	private boolean plusMenu;
	private boolean outdated;
	private boolean notConfigured;
	private boolean chatsLoaded;
	private int limit = 50;
	private int listScroll;
	private int listContentH;
	private int msgScroll;
	private int msgContentH;
	private String status = "";
	private boolean statusError;
	private long statusAt;
	private long lastChat;
	private long lastChats;
	private long lastSlow;
	private long seenChanges = -1;
	private boolean busy;
	private String chatDraft = "";
	private String friendDraft = "";
	private @Nullable EditBox input;
	private final List<Hit> hits = new ArrayList<>();
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int lx;
	private int ly;
	private int lw;
	private int lh;
	private int mx;
	private int my;
	private int mw;
	private int mh;

	public LagerfeuerScreen(@Nullable Screen parent, @Nullable String chat) {
		super(Component.literal("Lagerfeuer"));
		this.parent = parent;
		this.chatId = chat;
	}

	@Override
	protected void init() {
		pw = Math.min(720, this.width - 16);
		ph = this.height - 16;
		px = (this.width - pw) / 2;
		py = 8;
		lw = Mth.clamp(pw / 3, 120, 190);
		lx = px + 10;
		ly = py + 32;
		lh = ph - 32 - 10;
		mx = lx + lw + 10;
		my = ly;
		mw = px + pw - 10 - mx;
		mh = lh;
		input = new EditBox(this.font, mx + 24, my + mh - 18, mw - 50, 16, Component.literal("Nachricht"));
		input.setMaxLength(1000);
		addRenderableWidget(input);
		notConfigured = FriendsSync.serverUrl().isEmpty();
		showTab(tab);
		if (!chatsLoaded) {
			loadChats();
			loadFriends();
			loadParty();
			if (chatId != null) openChat(chatId);
		}
	}

	private void showTab(Tab t) {
		if (input != null) {
			if (tab == Tab.CHATS) chatDraft = input.getValue();
			else if (tab == Tab.FRIENDS) friendDraft = input.getValue();
		}
		tab = t;
		listScroll = 0;
		actionsFor = null;
		plusMenu = false;
		if (input == null) return;
		if (t == Tab.CHATS) {
			input.setValue(chatDraft);
			input.setX(mx + 24);
			input.setY(my + mh - 18);
			input.setWidth(mw - 50);
		} else if (t == Tab.FRIENDS) {
			input.setValue(friendDraft);
			input.setX(lx);
			input.setY(ly);
			input.setWidth(lw - 22);
		}
		updateInput();
	}

	private void updateInput() {
		if (input == null) return;
		input.visible = !notConfigured && !outdated && (tab == Tab.FRIENDS || tab == Tab.CHATS && (chatId != null || dmPartner != null));
		if (tab == Tab.FRIENDS) input.setHint(Component.literal("Minecraft-Name"));
		else input.setHint(Component.literal(replyTo != null ? "Antwort schreiben …" : "Nachricht schreiben …"));
	}

	// ---------- Laden ----------

	private @Nullable String me() {
		return FriendsSync.myId();
	}

	private static int statusOf(Throwable t) {
		Throwable cause = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
		return cause instanceof FriendsSync.LagerfeuerError e ? e.status : 0;
	}

	private void error(Throwable t) {
		if (statusOf(t) == 404 && !chatsLoaded) outdated = true;
		status(FriendsSync.errorText(t), true);
	}

	private void status(String text, boolean error) {
		status = text;
		statusError = error;
		statusAt = System.currentTimeMillis();
	}

	private void get(String path, Consumer<JsonObject> then, @Nullable Consumer<Throwable> failed) {
		FriendsSync.call("GET", path, null).whenCompleteAsync((r, e) -> {
			if (e != null) {
				if (failed != null) failed.accept(e);
				else error(e);
			} else {
				then.accept(r);
			}
		}, this.minecraft);
	}

	private void post(String path, JsonObject body, Consumer<JsonObject> then) {
		busy = true;
		FriendsSync.call("POST", path, body).whenCompleteAsync((r, e) -> {
			busy = false;
			if (e != null) error(e);
			else then.accept(r);
		}, this.minecraft);
	}

	private void loadChats() {
		lastChats = System.currentTimeMillis();
		get("/v1/chats", r -> {
			List<JsonObject> list = new ArrayList<>();
			for (JsonElement el : r.getAsJsonArray("chats")) list.add(el.getAsJsonObject());
			chats = list;
			boolean first = !chatsLoaded;
			chatsLoaded = true;
			outdated = false;
			if (pendingDirect != null && me() != null) openDirect(pendingDirect[0], pendingDirect[1]);
			else if (first && chatId == null && dmPartner == null && !list.isEmpty()) openChat(str(list.get(0), "id"));
			updateInput();
		}, e -> {
			if (statusOf(e) == 404) outdated = true;
			error(e);
			updateInput();
		});
	}

	private void loadChat() {
		String id = chatId;
		if (id == null) return;
		lastChat = System.currentTimeMillis();
		get("/v1/chat/messages?chat=" + id + "&limit=" + limit, r -> {
			if (!id.equals(chatId)) return;
			chat = r;
			dmPartner = null;
			markRead();
		}, e -> {
			if (!id.equals(chatId)) return;
			if (statusOf(e) == 404 && dmPartner != null) {
				chat = null; // a new direct chat: the first message creates it
				return;
			}
			error(e);
		});
	}

	private void loadFriends() {
		get("/v1/friends", r -> friends = r, null);
	}

	private void loadParty() {
		get("/v1/party", r -> party = r, null);
	}

	private void markRead() {
		if (chat == null || me() == null) return;
		JsonArray messages = chat.getAsJsonArray("messages");
		if (messages.isEmpty()) return;
		long last = messages.get(messages.size() - 1).getAsJsonObject().get("id").getAsLong();
		for (JsonElement el : chat.getAsJsonArray("members")) {
			JsonObject m = el.getAsJsonObject();
			if (str(m, "uuid").equals(me()) && m.get("read").getAsLong() < last) {
				m.addProperty("read", last);
				JsonObject body = new JsonObject();
				body.addProperty("chat", chatId);
				body.addProperty("message", last);
				FriendsSync.call("POST", "/v1/chat/read", body);
				for (JsonObject c : chats) if (str(c, "id").equals(chatId)) c.addProperty("unread", 0);
			}
		}
	}

	private void poll() {
		if (notConfigured || outdated) return;
		long now = System.currentTimeMillis();
		boolean changed = Lagerfeuer.changes() != seenChanges;
		seenChanges = Lagerfeuer.changes();
		if (chatId != null && (now - lastChat > POLL_MS || changed)) loadChat();
		if (now - lastChats > POLL_MS * 2 || changed) loadChats();
		if (now - lastSlow > 10_000 || changed) {
			lastSlow = now;
			loadFriends();
			loadParty();
		}
	}

	private void openChat(String id) {
		chatId = id;
		chat = null;
		dmPartner = null;
		replyTo = null;
		actionsFor = null;
		plusMenu = false;
		msgScroll = 0;
		limit = 50;
		Lagerfeuer.setOpenChat(id);
		if (tab != Tab.CHATS) showTab(Tab.CHATS);
		loadChat();
		updateInput();
	}

	/** Shows a tab ("CHATS", "FRIENDS", "PARTY"), e.g. from a button elsewhere. */
	public void select(String name) {
		showTab(Tab.valueOf(name));
	}

	/** Shows a friend's profile in the friends tab. */
	public void selectFriend(String uuid) {
		showTab(Tab.FRIENDS);
		friendId = uuid;
	}

	private @Nullable String[] pendingDirect;

	/** Opens the direct chat with a friend (e.g. from the player menu), as soon as the login is known. */
	public void startDirect(String uuid, String name) {
		if (me() == null) pendingDirect = new String[] {uuid, name};
		else openDirect(uuid, name);
	}

	private void openDirect(String uuid, String name) {
		String me = me();
		if (me == null) {
			pendingDirect = new String[] {uuid, name};
			return;
		}
		pendingDirect = null;
		String id = "d" + (me.compareTo(uuid) < 0 ? me + uuid : uuid + me);
		openChat(id);
		dmPartner = uuid;
		dmPartnerName = name;
		updateInput();
		if (input != null) setFocused(input);
	}

	// ---------- Aktionen ----------

	private void send() {
		if (input == null || busy) return;
		String text = input.getValue().strip();
		if (text.isEmpty()) return;
		JsonObject body = new JsonObject();
		body.addProperty("text", text);
		sendMessage(body, () -> input.setValue(""));
	}

	private void sendMessage(JsonObject body, Runnable done) {
		if (dmPartner != null && (chat == null || chat.getAsJsonArray("messages").isEmpty())) body.addProperty("to", dmPartner);
		else body.addProperty("chat", chatId);
		if (replyTo != null) body.addProperty("reply", replyTo.get("id").getAsLong());
		post("/v1/chat/send", body, r -> {
			done.run();
			replyTo = null;
			msgScroll = 0;
			if (r.has("chat")) {
				chatId = r.get("chat").getAsString();
				Lagerfeuer.setOpenChat(chatId);
			}
			dmPartner = null;
			updateInput();
			loadChat();
			loadChats();
		});
	}

	private void game(String kind) {
		plusMenu = false;
		Runnable start = () -> {
			JsonObject body = new JsonObject();
			body.addProperty("chat", chatId);
			body.addProperty("game", kind);
			post("/v1/chat/game", body, r -> {
				msgScroll = 0;
				loadChat();
			});
		};
		if (dmPartner != null && chat == null) {
			// a new direct chat needs its first message before a game can start in it
			JsonObject hello = new JsonObject();
			hello.addProperty("text", "Lust auf eine Runde?");
			sendMessage(hello, start);
		} else {
			start.run();
		}
	}

	private void inviteToServer() {
		plusMenu = false;
		var server = this.minecraft.getCurrentServer();
		if (server == null) {
			status("Du bist gerade auf keinem Server", true);
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("kind", "invite");
		JsonObject data = new JsonObject();
		data.addProperty("server", server.ip);
		body.add("data", data);
		sendMessage(body, () -> {});
	}

	private void react(JsonObject m, String emoji) {
		actionsFor = null;
		JsonObject body = new JsonObject();
		body.addProperty("message", m.get("id").getAsLong());
		body.addProperty("emoji", emoji);
		post("/v1/chat/react", body, r -> loadChat());
	}

	private void move(JsonObject m, int cell) {
		JsonObject body = new JsonObject();
		body.addProperty("message", m.get("id").getAsLong());
		body.addProperty("cell", cell);
		post("/v1/chat/game/move", body, r -> loadChat());
	}

	private void delete(JsonObject m) {
		actionsFor = null;
		this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			this.minecraft.gui.setScreen(this);
			if (!yes) return;
			JsonObject body = new JsonObject();
			body.addProperty("message", m.get("id").getAsLong());
			post("/v1/chat/delete", body, r -> loadChat());
		}, Component.literal("Nachricht löschen?"), Component.literal("Sie verschwindet für alle im Chat.")));
	}

	private void join(String server) {
		if (this.minecraft.level == null) {
			Lagerfeuer.joinServer(this.minecraft, server);
			return;
		}
		this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) Lagerfeuer.joinServer(this.minecraft, server);
			else this.minecraft.gui.setScreen(this);
		}, Component.literal("Auf " + server + " wechseln?"), Component.literal("Du verlässt dafür die Welt, in der du gerade bist.")));
	}

	private void addFriend() {
		if (input == null) return;
		String name = input.getValue().strip();
		if (name.isEmpty()) return;
		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		post("/v1/friends/add", body, r -> {
			input.setValue("");
			String other = r.has("friend") ? str(r.getAsJsonObject("friend"), "name") : name;
			status("friends".equals(str(r, "status")) ? other + " ist jetzt dein Freund" : "Anfrage an " + other + " geschickt", false);
			loadFriends();
		});
	}

	private void friendAction(String path, String uuid, String done) {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid);
		post(path, body, r -> {
			if (!done.isEmpty()) status(done, false);
			if (r.has("party")) {
				party = r;
				Lagerfeuer.setParty(r);
			}
			loadFriends();
			loadParty();
		});
	}

	private void partyAction(String path, @Nullable String partyId, String done) {
		JsonObject body = new JsonObject();
		if (partyId != null) body.addProperty("party", partyId);
		post(path, body, r -> {
			if (!done.isEmpty()) status(done, false);
			if (r.has("party")) {
				party = r;
				Lagerfeuer.setParty(r);
			}
			loadParty();
			loadChats();
		});
	}

	// ---------- Zeichnen ----------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		hits.clear();
		poll();
		g.fill(0, 0, this.width, this.height, BG);
		Ui.nineSlice(g, Ui.PANEL, px, py, pw, ph, 8, 0xF4FFFFFF);
		Component title = Ui.title("Lagerfeuer");
		Ui.text(g, title, px + 12, py + 9, 0xFFFFE6C4);
		int tx = px + 12 + this.font.width(title) + 12;
		int unread = 0;
		for (JsonObject c : chats) unread += c.get("unread").getAsInt();
		int requests = friends != null ? friends.getAsJsonArray("incoming").size() : 0;
		int invites = party != null && party.has("partyInvites") ? party.getAsJsonArray("partyInvites").size() : 0;
		tx = tabButton(g, tx, Tab.CHATS, "Chats" + (unread > 0 ? " (" + unread + ")" : ""), mouseX, mouseY);
		tx = tabButton(g, tx, Tab.FRIENDS, "Freunde" + (requests > 0 ? " (" + requests + ")" : ""), mouseX, mouseY);
		tabButton(g, tx, Tab.PARTY, "Party" + (invites > 0 ? " (" + invites + ")" : ""), mouseX, mouseY);
		button(g, px + pw - 10 - 64, py + 7, 64, 18, "Zurück", mouseX, mouseY, this::onClose);

		if (notConfigured || outdated) {
			String line1 = notConfigured ? "Dein Lagerfeuer ist noch nicht eingerichtet." : "Dein Lagerfeuer ist zu alt für Chat und Party.";
			String line2 = notConfigured ? "Öffne den Emberleaf-Launcher → Freunde und trag die Adresse ein."
				: "Füg die neue worker.js aus dem Emberleaf-Ordner „lagerfeuer“ bei Cloudflare ein (siehe ANLEITUNG.md).";
			centered(g, line1, this.height / 2 - 12, ACCENT);
			centered(g, line2, this.height / 2 + 2, MUTED);
		} else {
			g.fill(lx - 3, ly - 3, lx + lw + 3, ly + lh + 3, 0x38000000);
			switch (tab) {
				case CHATS -> {
					drawChatList(g, mouseX, mouseY);
					drawChat(g, mouseX, mouseY);
				}
				case FRIENDS -> {
					drawFriendList(g, mouseX, mouseY);
					drawFriend(g, mouseX, mouseY);
				}
				case PARTY -> {
					drawPartyList(g, mouseX, mouseY);
					drawParty(g, mouseX, mouseY);
				}
			}
		}
		if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 4000) {
			Component s = Ui.menu(status);
			int w = this.font.width(s) + 16;
			Ui.nineSlice(g, Ui.PANEL, this.width / 2 - w / 2, py + ph - 30, w, 18, 6, 0xF8FFFFFF);
			Ui.text(g, s, this.width / 2F - this.font.width(s) / 2F, py + ph - 25, statusError ? 0xFFFF9A8A : GREEN);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		// little menus over everything else
		drawingMenu = true;
		if (tab == Tab.CHATS && actionsFor != null) drawActions(g, mouseX, mouseY);
		if (tab == Tab.CHATS && plusMenu) drawPlusMenu(g, mouseX, mouseY);
		drawingMenu = false;
	}

	private int tabButton(GuiGraphicsExtractor g, int x, Tab t, String label, int mouseX, int mouseY) {
		Component c = Ui.menu(label);
		int w = this.font.width(c) + 18;
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= py + 7 && mouseY < py + 25;
		Ui.nineSlice(g, t == tab || hover ? Ui.BUTTON_HOVER : Ui.BUTTON, x, py + 7, w, 18, 5, 0xFFFFFFFF);
		Ui.text(g, c, x + 9, py + 11.5F, t == tab ? 0xFFFFFFFF : 0xFFEBDCC8);
		hits.add(new Hit(x, py + 7, w, 18, () -> showTab(t)));
		return x + w + 4;
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mouseX, int mouseY, Runnable action) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		Ui.nineSlice(g, hover ? Ui.BUTTON_HOVER : Ui.BUTTON, x, y, w, h, 5, 0xFFFFFFFF);
		Component c = Ui.menu(fit(label, w - 8));
		Ui.text(g, c, x + (w - this.font.width(c)) / 2F, y + (h - 8) / 2F, hover ? 0xFFFFFFFF : 0xFFEBDCC8);
		hits.add(new Hit(x, y, w, h, action));
	}

	private void centered(GuiGraphicsExtractor g, String text, int y, int color) {
		Component c = Ui.menu(text);
		Ui.text(g, c, this.width / 2F - this.font.width(c) / 2F, y, color);
	}

	private String fit(String text, int max) {
		if (this.font.width(Ui.menu(text)) <= max) return text;
		while (!text.isEmpty() && this.font.width(Ui.menu(text + "…")) > max) text = text.substring(0, text.length() - 1);
		return text + "…";
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && !e.isJsonNull() && e.getAsBoolean();
	}

	private static String time(long ms) {
		LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
		String clock = String.format("%02d:%02d", t.getHour(), t.getMinute());
		if (t.toLocalDate().equals(LocalDate.now())) return clock;
		if (t.toLocalDate().equals(LocalDate.now().minusDays(1))) return "gestern " + clock;
		return String.format("%02d.%02d. %s", t.getDayOfMonth(), t.getMonthValue(), clock);
	}

	private static String where(JsonObject p) {
		if (!bool(p, "online")) {
			if (p.has("lastSeen") && !p.get("lastSeen").isJsonNull()) {
				long ago = System.currentTimeMillis() / 1000 - p.get("lastSeen").getAsLong();
				if (ago < 3600) return "vor " + Math.max(1, ago / 60) + " Min. online";
				if (ago < 86400) return "vor " + ago / 3600 + " Std. online";
				return "vor " + ago / 86400 + " Tagen online";
			}
			return "offline";
		}
		String server = str(p, "server");
		return server.isEmpty() || server.equals("Einzelspieler") ? "spielt Einzelspieler" : "auf " + server;
	}

	private void dot(GuiGraphicsExtractor g, int x, int y, boolean online) {
		g.fill(x, y, x + 5, y + 5, online ? 0xFF6FD25A : 0xFF6A5A4E);
	}

	private Component glyphs(String text) {
		return Component.literal(text).withStyle(s -> s.withFont(Badges.FONT));
	}

	private void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int content, int offsetFromTop) {
		if (content <= h) return;
		int bar = Math.max(12, h * h / content);
		int by = y + (h - bar) * offsetFromTop / Math.max(1, content - h);
		g.fill(x, by, x + 2, by + bar, 0x80FFE6C8);
	}

	// ----- Chats -----

	private void drawChatList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int rowH = 28;
		listContentH = chats.size() * rowH;
		listScroll = Mth.clamp(listScroll, 0, Math.max(0, listContentH - lh));
		if (chats.isEmpty()) {
			Ui.text(g, Ui.menu(chatsLoaded ? "Noch keine Chats" : "Lädt …"), lx + 6, ly + 6, ACCENT);
			if (chatsLoaded) {
				for (int i = 0; i < 3; i++) {
					String line = new String[] {"Schreib einem Freund", "unter „Freunde“ oder", "starte eine Party."}[i];
					Ui.text(g, Ui.menu(line), lx + 6, ly + 20 + i * 11, MUTED);
				}
			}
			return;
		}
		g.enableScissor(lx, ly, lx + lw, ly + lh);
		int y = ly - listScroll;
		String me = me();
		for (JsonObject c : chats) {
			if (y + rowH > ly && y < ly + lh) {
				String id = str(c, "id");
				boolean sel = id.equals(chatId);
				boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= Math.max(y, ly) && mouseY < Math.min(y + rowH - 2, ly + lh);
				g.fill(lx, y, lx + lw, y + rowH - 2, sel ? 0x90E8742F : hover ? 0x40FFFFFF : 0x24000000);
				String kind = str(c, "kind");
				if (kind.equals("dm") && c.has("partner") && !c.get("partner").isJsonNull()) {
					Heads.draw(g, this.font, str(c, "partner"), lx + 4, y + 5, 16);
				} else {
					Ui.nineSlice(g, Ui.ROUND, lx + 4, y + 5, 16, 16, 5, kind.equals("party") ? 0xFFE8742F : 0xFF6A8AC8);
					Component letter = Ui.menu(kind.equals("party") ? "P" : str(c, "name").isEmpty() ? "G" : str(c, "name").substring(0, 1).toUpperCase());
					Ui.text(g, letter, lx + 12 - this.font.width(letter) / 2F, y + 9, 0xFFFFFFFF);
				}
				int unread = c.get("unread").getAsInt();
				int nameW = lw - 30 - (unread > 0 ? 18 : 0);
				Ui.text(g, Ui.menu(fit(str(c, "name"), nameW)), lx + 25, y + 4, TEXT);
				JsonElement lastEl = c.get("lastMessage");
				if (lastEl != null && lastEl.isJsonObject()) {
					JsonObject last = lastEl.getAsJsonObject();
					String who = str(last, "sender").equals(me) ? "Du: " : "";
					String preview = who + Lagerfeuer.preview(str(last, "kind"), str(last, "text"));
					Ui.text(g, Ui.menu(fit(plain(preview), lw - 30)), lx + 25, y + 15, MUTED);
				}
				if (unread > 0) {
					Component n = Ui.menu(unread > 99 ? "99+" : String.valueOf(unread));
					int w = Math.max(12, this.font.width(n) + 6);
					Ui.nineSlice(g, Ui.ROUND, lx + lw - w - 4, y + 4, w, 11, 5, 0xFFE8742F);
					Ui.text(g, n, lx + lw - w - 4 + (w - this.font.width(n)) / 2F, y + 5.5F, 0xFFFFFFFF);
				}
				int top = Math.max(y, ly);
				hits.add(new Hit(lx, top, lw, Math.min(y + rowH - 2, ly + lh) - top, () -> openChat(id)));
			}
			y += rowH;
		}
		g.disableScissor();
		scrollbar(g, lx + lw + 1, ly, lh, listContentH, listScroll);
	}

	/** Emoji the game font cannot show are replaced by words for the game. */
	private static String plain(String text) {
		return text.replace("📷 ", "").replace("🎮 ", "").replace("🎲 ", "Spiel: ");
	}

	private String chatTitle() {
		if (chat != null) return str(chat.getAsJsonObject("chat"), "name");
		if (dmPartnerName != null && dmPartner != null) return dmPartnerName;
		for (JsonObject c : chats) if (str(c, "id").equals(chatId)) return str(c, "name");
		return "Chat";
	}

	private void drawChat(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (chatId == null) {
			Ui.text(g, Ui.menu("Wähle links einen Chat aus."), mx + 6, my + 6, MUTED);
			return;
		}
		// header
		String kind = chat != null ? str(chat.getAsJsonObject("chat"), "kind") : "dm";
		JsonArray members = chat != null ? chat.getAsJsonArray("members") : new JsonArray();
		String me = me();
		int hx = mx;
		if (kind.equals("dm")) {
			String partner = dmPartner;
			JsonObject other = null;
			for (JsonElement el : members) if (!str(el.getAsJsonObject(), "uuid").equals(me)) other = el.getAsJsonObject();
			if (other != null) partner = str(other, "uuid");
			if (partner != null) {
				Heads.draw(g, this.font, partner, mx, my, 18);
				hx += 24;
			}
			Ui.text(g, Ui.menu(chatTitle()), hx, my + 1, 0xFFFFE6C4);
			if (other != null) {
				dot(g, hx, my + 13, bool(other, "online"));
				Ui.text(g, Ui.menu(where(other)), hx + 8, my + 11, MUTED);
			}
		} else {
			Ui.text(g, Ui.menu(chatTitle()), hx, my + 1, 0xFFFFE6C4);
			int online = 0;
			for (JsonElement el : members) if (bool(el.getAsJsonObject(), "online")) online++;
			Ui.text(g, Ui.menu((kind.equals("party") ? "Party-Chat · " : "") + members.size() + " Mitglieder · " + online + " online"), hx, my + 11, MUTED);
		}
		int top = my + 24;
		int bottomBar = replyTo != null ? 14 : 0;
		int bottom = my + mh - 22 - bottomBar;
		g.fill(mx, top - 2, mx + mw, top - 1, 0x30FFE6C8);
		// the input row: "+" for games and invites, the box (a widget), send
		button(g, mx, my + mh - 19, 20, 18, "+", mouseX, mouseY, () -> {
			plusMenu = !plusMenu;
			actionsFor = null;
		});
		button(g, mx + mw - 22, my + mh - 19, 22, 18, "»", mouseX, mouseY, this::send);
		if (replyTo != null) {
			String who = nameOf(members, str(replyTo, "sender"));
			Ui.text(g, Ui.menu(fit("Antwort an " + who + ": " + bodyPreview(replyTo), mw - 30)), mx + 4, my + mh - 33, ACCENT);
			button(g, mx + mw - 14, my + mh - 35, 14, 12, "x", mouseX, mouseY, () -> {
				replyTo = null;
				updateInput();
			});
		}
		if (chat == null) {
			Ui.text(g, Ui.menu(dmPartner != null ? "Schreib " + chatTitle() + " die erste Nachricht!" : "Lädt …"), mx + 6, top + 6, MUTED);
			return;
		}
		drawMessages(g, mouseX, mouseY, top, bottom, members, kind);
	}

	private static String nameOf(JsonArray members, String uuid) {
		for (JsonElement el : members) if (str(el.getAsJsonObject(), "uuid").equals(uuid)) return str(el.getAsJsonObject(), "name");
		return "?";
	}

	private static String bodyPreview(JsonObject m) {
		return switch (str(m, "kind")) {
			case "image" -> "Bild";
			case "invite" -> "Server-Einladung";
			case "game" -> "Spiel: " + str(m, "text");
			case "deleted" -> "gelöschte Nachricht";
			default -> str(m, "text");
		};
	}

	/** One message laid out: its height and how to draw it at a y position. */
	private interface Drawer {
		void draw(GuiGraphicsExtractor g, int y, int mouseX, int mouseY);
	}

	private record Item(int h, Drawer drawer) {}

	private void drawMessages(GuiGraphicsExtractor g, int mouseX, int mouseY, int top, int bottom, JsonArray members, String kind) {
		JsonArray messages = chat.getAsJsonArray("messages");
		String me = me();
		List<Item> items = new ArrayList<>();
		if (bool(chat, "more")) {
			items.add(new Item(18, (gg, y, mX, mY) -> {
				int w = 140;
				button(gg, mx + (mw - w) / 2, y, w, 14, "Ältere Nachrichten laden", mX, mY, () -> {
					limit = 100;
					loadChat();
				});
			}));
		}
		String lastSender = null;
		LocalDate lastDay = null;
		for (int i = 0; i < messages.size(); i++) {
			JsonObject m = messages.get(i).getAsJsonObject();
			LocalDate day = LocalDateTime.ofInstant(Instant.ofEpochMilli(m.get("sent").getAsLong()), ZoneId.systemDefault()).toLocalDate();
			if (!day.equals(lastDay)) {
				lastDay = day;
				lastSender = null;
				String label = day.equals(LocalDate.now()) ? "Heute" : day.equals(LocalDate.now().minusDays(1)) ? "Gestern"
					: String.format("%02d.%02d.%d", day.getDayOfMonth(), day.getMonthValue(), day.getYear());
				items.add(new Item(14, (gg, y, mX, mY) -> {
					Component c = Ui.menu(label);
					Ui.text(gg, c, mx + (mw - this.font.width(c)) / 2F, y + 3, 0xFF8A7868);
				}));
			}
			if (str(m, "kind").equals("system")) {
				lastSender = null;
				List<FormattedCharSequence> lines = this.font.split(Ui.menu(str(m, "text")), mw - 20);
				items.add(new Item(lines.size() * 10 + 6, (gg, y, mX, mY) -> {
					for (int l = 0; l < lines.size(); l++) {
						int w = this.font.width(lines.get(l));
						gg.text(this.font, lines.get(l), mx + (mw - w) / 2, y + 3 + l * 10, 0xFF9A8878, false);
					}
				}));
				continue;
			}
			boolean mine = str(m, "sender").equals(me);
			boolean showName = !mine && !kind.equals("dm") && !str(m, "sender").equals(lastSender);
			lastSender = str(m, "sender");
			items.add(bubble(m, mine, showName, members));
		}
		// read receipt under your last message
		if (!messages.isEmpty()) {
			JsonObject last = messages.get(messages.size() - 1).getAsJsonObject();
			if (str(last, "sender").equals(me) && !str(last, "kind").equals("system")) {
				long id = last.get("id").getAsLong();
				int readers = 0;
				int others = 0;
				for (JsonElement el : members) {
					JsonObject member = el.getAsJsonObject();
					if (str(member, "uuid").equals(me)) continue;
					others++;
					if (member.get("read").getAsLong() >= id) readers++;
				}
				String receipt = readers == 0 ? "Gesendet" : kind.equals("dm") || readers == others ? "Gelesen" : "Gelesen von " + readers;
				int color = readers == 0 ? 0xFF8A7868 : 0xFF7CC8FF;
				items.add(new Item(11, (gg, y, mX, mY) -> {
					Component c = Ui.menu(receipt);
					Ui.text(gg, c, mx + mw - 4 - this.font.width(c), y, color);
				}));
			}
		}
		int total = 0;
		for (Item item : items) total += item.h();
		msgContentH = total;
		int area = bottom - top;
		msgScroll = Mth.clamp(msgScroll, 0, Math.max(0, total - area));
		// the newest message sits at the bottom; scrolling moves everything down
		int y = bottom - total + msgScroll;
		g.enableScissor(mx, top, mx + mw, bottom);
		int hitsBefore = hits.size();
		for (Item item : items) {
			if (y + item.h() > top && y < bottom) item.drawer().draw(g, y, mouseX, mouseY);
			y += item.h();
		}
		g.disableScissor();
		// clicks only count inside the visible message area
		for (int i = hits.size() - 1; i >= hitsBefore; i--) {
			Hit h = hits.get(i);
			int y0 = Math.max(h.y(), top);
			int y1 = Math.min(h.y() + h.h(), bottom);
			if (y1 <= y0) hits.remove(i);
			else hits.set(i, new Hit(h.x(), y0, h.w(), y1 - y0, h.action()));
		}
		scrollbar(g, mx + mw - 2, top, area, total, Math.max(0, total - area - msgScroll));
	}

	private Item bubble(JsonObject m, boolean mine, boolean showName, JsonArray members) {
		int maxW = Math.max(120, (int) (mw * 0.72F));
		String kind = str(m, "kind");
		List<FormattedCharSequence> lines = new ArrayList<>();
		int extraH = 0;
		int contentW;
		if (kind.equals("text")) {
			lines = this.font.split(Ui.menu(str(m, "text")), maxW - 12);
		} else if (kind.equals("deleted")) {
			lines = this.font.split(Ui.menu("Nachricht gelöscht"), maxW - 12);
		} else if (kind.equals("image")) {
			lines = this.font.split(Ui.menu("Bild" + (str(m, "text").isEmpty() ? "" : ": " + str(m, "text"))), maxW - 12);
			extraH = 18;
		} else if (kind.equals("invite")) {
			JsonObject data = m.getAsJsonObject("data");
			lines = new ArrayList<>(this.font.split(Ui.menu("Einladung auf " + (data != null ? str(data, "server") : "?")), maxW - 12));
			if (!str(m, "text").isEmpty()) lines.addAll(this.font.split(Ui.menu(str(m, "text")), maxW - 12));
			extraH = mine ? 0 : 18;
		} else if (kind.equals("game")) {
			JsonObject data = m.getAsJsonObject("data");
			boolean ttt = data != null && str(data, "game").equals("ttt");
			lines = new ArrayList<>(this.font.split(Ui.menu(ttt ? "Tic-Tac-Toe" : "Vier gewinnt"), maxW - 12));
			extraH = 12 + (ttt ? 3 * 16 + 2 * 2 : 6 * 10 + 5);
		}
		int textW = 0;
		for (FormattedCharSequence l : lines) textW = Math.max(textW, this.font.width(l));
		String name = showName ? nameOf(members, str(m, "sender")) : null;
		JsonObject reply = m.has("reply") && m.get("reply").isJsonObject() ? m.getAsJsonObject("reply") : null;
		String quote = reply == null ? null : nameOf(members, str(reply, "sender")) + ": "
			+ (bool(reply, "missing") ? "nicht mehr da" : bodyPreview(reply));
		contentW = textW;
		if (name != null) contentW = Math.max(contentW, this.font.width(Ui.menu(name)));
		if (quote != null) contentW = Math.min(maxW - 12, Math.max(contentW, this.font.width(Ui.menu(quote)) + 6));
		if (kind.equals("game")) contentW = Math.max(contentW, str(m.getAsJsonObject("data"), "game").equals("ttt") ? 3 * 16 + 4 : 7 * 10 + 6);
		if (kind.equals("image") || kind.equals("invite") && !mine) contentW = Math.max(contentW, 70);
		int bw = Math.min(maxW, contentW + 12);
		int nameH = name != null ? 10 : 0;
		int quoteH = quote != null ? 13 : 0;
		int bh = 6 + nameH + quoteH + lines.size() * 10 + extraH;
		JsonObject reactions = m.has("reactions") && m.get("reactions").isJsonObject() ? m.getAsJsonObject("reactions") : new JsonObject();
		int reactionH = reactions.size() > 0 ? 13 : 0;
		List<FormattedCharSequence> finalLines = lines;
		int finalBw = bw;
		return new Item(bh + reactionH + 4, (g, y, mouseX, mouseY) -> {
			int bx = mine ? mx + mw - 8 - finalBw : mx + 4;
			boolean hover = mouseX >= bx && mouseX < bx + finalBw && mouseY >= y && mouseY < y + bh;
			int color = kind.equals("deleted") ? 0x80302018 : mine ? 0xF0A2502A : 0xE0382618;
			Ui.nineSlice(g, Ui.ROUND, bx, y, finalBw, bh, 6, color);
			if (hover && !kind.equals("deleted")) Ui.nineSlice(g, Ui.ROUND, bx, y, finalBw, bh, 6, 0x18FFFFFF);
			hits.add(new Hit(bx, y, finalBw, bh, () -> {
				if (kind.equals("deleted")) return;
				actionsFor = actionsFor == m ? null : m;
				plusMenu = false;
			}));
			int ty = y + 3;
			if (name != null) {
				Ui.text(g, Ui.menu(name), bx + 6, ty, 0xFFFFB36B);
				ty += nameH;
			}
			if (quote != null) {
				g.fill(bx + 5, ty, bx + 7, ty + 10, 0xFFFFB36B);
				Ui.text(g, Ui.menu(fit(quote, finalBw - 16)), bx + 10, ty + 1, 0xFFE8D2B8);
				ty += quoteH;
			}
			int textColor = kind.equals("deleted") ? 0xFF9A8878 : TEXT;
			for (FormattedCharSequence line : finalLines) {
				g.text(this.font, line, bx + 6, ty, textColor, false);
				ty += 10;
			}
			if (kind.equals("image")) {
				JsonObject data = m.getAsJsonObject("data");
				String image = data != null ? str(data, "image") : "";
				button(g, bx + 6, ty + 2, 64, 14, "Ansehen", mouseX, mouseY, () -> openImage(image));
			} else if (kind.equals("invite") && !mine) {
				JsonObject data = m.getAsJsonObject("data");
				String server = data != null ? str(data, "server") : "";
				button(g, bx + 6, ty + 2, 64, 14, "Beitreten", mouseX, mouseY, () -> join(server));
			} else if (kind.equals("game")) {
				drawGame(g, m, bx + 6, ty, mouseX, mouseY, members);
			}
			// time next to the bubble
			Component when = Ui.menu(time(m.get("sent").getAsLong()));
			int tw = this.font.width(when);
			if (mine) Ui.text(g, when, bx - tw - 4, y + bh - 10, 0xFF7A6858);
			else Ui.text(g, when, bx + finalBw + 4, y + bh - 10, 0xFF7A6858);
			// reactions under the bubble
			if (reactionH > 0) {
				int rx = mine ? bx + finalBw : bx;
				List<int[]> chips = new ArrayList<>();
				int total = 0;
				for (int r = 0; r < REACTIONS.length; r++) {
					JsonElement users = reactions.get(REACTIONS[r]);
					if (users == null || !users.isJsonArray() || users.getAsJsonArray().isEmpty()) continue;
					String count = String.valueOf(users.getAsJsonArray().size());
					int w = 10 + 3 + this.font.width(Ui.menu(count)) + 6;
					chips.add(new int[] {r, w});
					total += w + 2;
				}
				int cx = mine ? rx - total : rx;
				for (int[] chip : chips) {
					int r = chip[0];
					JsonArray users = reactions.getAsJsonArray(REACTIONS[r]);
					boolean minePicked = false;
					List<FormattedCharSequence> who = new ArrayList<>();
					for (JsonElement u : users) {
						if (u.getAsString().equals(me())) minePicked = true;
						who.add(Component.literal(nameOf(members, u.getAsString())).getVisualOrderText());
					}
					int cy = y + bh + 1;
					Ui.nineSlice(g, Ui.ROUND, cx, cy, chip[1], 11, 5, minePicked ? 0xFF7A3A1C : 0xFF2E2018);
					g.text(this.font, glyphs(String.valueOf((char) (0xE120 + r))), cx + 3, cy + 1, 0xFFFFFFFF, false);
					Ui.text(g, Ui.menu(String.valueOf(users.size())), cx + 15, cy + 1.5F, TEXT);
					String emoji = REACTIONS[r];
					hits.add(new Hit(cx, cy, chip[1], 11, () -> react(m, emoji)));
					if (mouseX >= cx && mouseX < cx + chip[1] && mouseY >= cy && mouseY < cy + 11) {
						who.add(0, Component.literal(REACTION_NAMES[r]).getVisualOrderText());
						g.setTooltipForNextFrame(this.font, who, mouseX, mouseY);
					}
					cx += chip[1] + 2;
				}
			}
		});
	}

	private void drawGame(GuiGraphicsExtractor g, JsonObject m, int x, int y, int mouseX, int mouseY, JsonArray members) {
		JsonObject d = m.getAsJsonObject("data");
		if (d == null) return;
		String me = me();
		JsonArray players = d.getAsJsonArray("players");
		String p0 = players.get(0).isJsonNull() ? null : players.get(0).getAsString();
		String p1 = players.get(1).isJsonNull() ? null : players.get(1).getAsString();
		int turn = d.get("turn").getAsInt();
		String winner = str(d, "winner");
		int seat = Objects.equals(me, p0) ? 0 : Objects.equals(me, p1) ? 1 : -1;
		boolean canJoin = p1 == null && !Objects.equals(me, p0);
		boolean myTurn = winner.isEmpty() && (seat >= 0 && seat == turn || canJoin && turn == 1);
		String statusLine;
		if (winner.equals("draw")) statusLine = "Unentschieden";
		else if (!winner.isEmpty()) statusLine = winner.equals(me) ? "Du hast gewonnen!" : nameOf(members, winner) + " hat gewonnen";
		else if (myTurn) statusLine = "Du bist dran";
		else if ((turn == 0 ? p0 : p1) != null) statusLine = nameOf(members, turn == 0 ? p0 : p1) + " ist dran";
		else statusLine = "Wer spielt mit? Einfach ziehen";
		Ui.text(g, Ui.menu(statusLine), x, y, myTurn ? GREEN : ACCENT);
		int by = y + 12;
		String board = str(d, "board");
		boolean ttt = str(d, "game").equals("ttt");
		if (ttt) {
			for (int i = 0; i < 9; i++) {
				int cx = x + (i % 3) * 18;
				int cy = by + (i / 3) * 18;
				char v = board.charAt(i);
				boolean hover = myTurn && v == '.' && mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16;
				g.fill(cx, cy, cx + 16, cy + 16, hover ? 0x60FFFFFF : 0x40000000);
				if (v != '.') {
					Component mark = Ui.menu(v == 'x' ? "X" : "O");
					Ui.text(g, mark, cx + 8 - this.font.width(mark) / 2F, cy + 4, v == 'x' ? 0xFFFFB36B : 0xFF7CC8FF);
				}
				if (myTurn && v == '.') {
					int cell = i;
					hits.add(new Hit(cx, cy, 16, 16, () -> move(m, cell)));
				}
			}
		} else {
			g.fill(x - 1, by - 1, x + 7 * 10 + 1, by + 6 * 10 + 1, 0xFF2F4F7A);
			int last = d.has("last") ? d.get("last").getAsInt() : -1;
			for (int i = 0; i < 42; i++) {
				int cx = x + (i % 7) * 10;
				int cy = by + (i / 7) * 10;
				char v = board.charAt(i);
				int color = v == 'x' ? 0xFFF08A45 : v == 'o' ? 0xFFFFD24A : 0xFF13253D;
				g.fill(cx + 1, cy + 1, cx + 9, cy + 9, color);
				if (i == last) g.fill(cx + 4, cy + 4, cx + 6, cy + 6, 0xFFFFFFFF);
			}
			if (myTurn) {
				for (int col = 0; col < 7; col++) {
					int cx = x + col * 10;
					int column = col;
					if (mouseX >= cx && mouseX < cx + 10 && mouseY >= by && mouseY < by + 60) g.fill(cx, by, cx + 10, by + 60, 0x30FFFFFF);
					hits.add(new Hit(cx, by, 10, 60, () -> move(m, column)));
				}
			}
		}
	}

	private void openImage(String id) {
		String server = FriendsSync.serverUrl();
		if (server.isEmpty() || id.isEmpty()) return;
		ConfirmLinkScreen.confirmLinkNow(this, URI.create(server + "/v1/image/" + id), true);
	}

	private void drawActions(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		JsonObject m = actionsFor;
		if (m == null) return;
		boolean mine = str(m, "sender").equals(me());
		boolean text = str(m, "kind").equals("text");
		int w = 6 * 18 + 4 + 70 + (text ? 64 : 0) + (mine ? 64 : 0) + 8;
		w = Math.min(w, mw);
		int x = mx + (mw - w) / 2;
		int y = my + mh - 44;
		Ui.nineSlice(g, Ui.PANEL, x, y, w, 22, 6, 0xFCFFFFFF);
		int bx = x + 4;
		for (int r = 0; r < REACTIONS.length; r++) {
			boolean hover = mouseX >= bx && mouseX < bx + 16 && mouseY >= y + 3 && mouseY < y + 19;
			if (hover) {
				g.fill(bx, y + 3, bx + 16, y + 19, 0x40FFFFFF);
				g.setTooltipForNextFrame(this.font, Component.literal(REACTION_NAMES[r]), mouseX, mouseY);
			}
			g.text(this.font, glyphs(String.valueOf((char) (0xE120 + r))), bx + 3, y + 7, 0xFFFFFFFF, false);
			String emoji = REACTIONS[r];
			hits.add(new Hit(bx, y + 3, 16, 16, () -> react(m, emoji)));
			bx += 18;
		}
		bx += 4;
		button(g, bx, y + 3, 66, 16, "Antworten", mouseX, mouseY, () -> {
			replyTo = m;
			actionsFor = null;
			updateInput();
			if (input != null) setFocused(input);
		});
		bx += 70;
		if (text) {
			button(g, bx, y + 3, 60, 16, "Kopieren", mouseX, mouseY, () -> {
				this.minecraft.keyboardHandler.setClipboard(str(m, "text"));
				actionsFor = null;
				status("Kopiert", false);
			});
			bx += 64;
		}
		if (mine) button(g, bx, y + 3, 60, 16, "Löschen", mouseX, mouseY, () -> delete(m));
	}

	private void drawPlusMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int w = 150;
		boolean onServer = this.minecraft.getCurrentServer() != null;
		int rows = onServer ? 3 : 2;
		int x = mx;
		int y = my + mh - 22 - rows * 20 - 6;
		Ui.nineSlice(g, Ui.PANEL, x, y, w, rows * 20 + 6, 6, 0xFCFFFFFF);
		button(g, x + 3, y + 3, w - 6, 18, "Tic-Tac-Toe spielen", mouseX, mouseY, () -> game("ttt"));
		button(g, x + 3, y + 23, w - 6, 18, "Vier gewinnt spielen", mouseX, mouseY, () -> game("c4"));
		if (onServer) button(g, x + 3, y + 43, w - 6, 18, "Auf diesen Server einladen", mouseX, mouseY, this::inviteToServer);
	}

	// ----- Freunde -----

	private void drawFriendList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		button(g, lx + lw - 20, ly - 1, 20, 18, "+", mouseX, mouseY, this::addFriend);
		int top = ly + 22;
		int height = lh - 22;
		if (friends == null) {
			Ui.text(g, Ui.menu("Lädt …"), lx + 6, top + 4, MUTED);
			return;
		}
		JsonArray incoming = friends.getAsJsonArray("incoming");
		JsonArray list = friends.getAsJsonArray("friends");
		List<JsonObject> sorted = new ArrayList<>();
		for (JsonElement el : list) sorted.add(el.getAsJsonObject());
		sorted.sort((a, b) -> bool(a, "online") != bool(b, "online") ? (bool(a, "online") ? -1 : 1) : str(a, "name").compareToIgnoreCase(str(b, "name")));
		int rowH = 24;
		listContentH = incoming.size() * rowH + (incoming.isEmpty() ? 0 : 12) + sorted.size() * rowH;
		listScroll = Mth.clamp(listScroll, 0, Math.max(0, listContentH - height));
		g.enableScissor(lx, top, lx + lw, top + height);
		int y = top - listScroll;
		int hitsBefore = hits.size();
		if (!incoming.isEmpty()) {
			Ui.text(g, Ui.menu("Anfragen"), lx + 4, y + 1, ACCENT);
			y += 12;
			for (JsonElement el : incoming) {
				JsonObject r = el.getAsJsonObject();
				String uuid = str(r, "uuid");
				g.fill(lx, y, lx + lw, y + rowH - 2, 0x40E8742F);
				Heads.draw(g, this.font, uuid, lx + 4, y + 3, 16);
				Ui.text(g, Ui.menu(fit(str(r, "name"), lw - 70)), lx + 24, y + 7, TEXT);
				button(g, lx + lw - 42, y + 3, 18, 16, "✓", mouseX, mouseY, () -> friendAction("/v1/friends/accept", uuid, "Ihr seid jetzt Freunde"));
				button(g, lx + lw - 21, y + 3, 18, 16, "x", mouseX, mouseY, () -> friendAction("/v1/friends/remove", uuid, ""));
				y += rowH;
			}
		}
		if (sorted.isEmpty()) Ui.text(g, Ui.menu("Noch keine Freunde"), lx + 6, y + 4, MUTED);
		for (JsonObject f : sorted) {
			String uuid = str(f, "uuid");
			boolean sel = uuid.equals(friendId);
			boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= y && mouseY < y + rowH - 2 && mouseY >= top && mouseY < top + height;
			g.fill(lx, y, lx + lw, y + rowH - 2, sel ? 0x90E8742F : hover ? 0x40FFFFFF : 0x24000000);
			Heads.draw(g, this.font, uuid, lx + 4, y + 3, 16);
			dot(g, lx + 17, y + 15, bool(f, "online"));
			Ui.text(g, Ui.menu(fit(str(f, "name"), lw - 30)), lx + 25, y + 2, TEXT);
			Ui.text(g, Ui.menu(fit(where(f), lw - 30)), lx + 25, y + 12, bool(f, "online") ? GREEN : MUTED);
			hits.add(new Hit(lx, y, lw, rowH - 2, () -> {
				friendId = uuid;
				inviting = false;
			}));
			y += rowH;
		}
		g.disableScissor();
		for (int i = hits.size() - 1; i >= hitsBefore; i--) {
			Hit h = hits.get(i);
			int y0 = Math.max(h.y(), top);
			int y1 = Math.min(h.y() + h.h(), top + height);
			if (y1 <= y0) hits.remove(i);
			else hits.set(i, new Hit(h.x(), y0, h.w(), y1 - y0, h.action()));
		}
		scrollbar(g, lx + lw + 1, top, height, listContentH, listScroll);
	}

	private @Nullable JsonObject friend(String uuid) {
		if (friends == null) return null;
		for (JsonElement el : friends.getAsJsonArray("friends")) if (str(el.getAsJsonObject(), "uuid").equals(uuid)) return el.getAsJsonObject();
		return null;
	}

	private void drawFriend(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		JsonObject f = friendId != null ? friend(friendId) : null;
		if (f == null) {
			// your own little profile
			Ui.text(g, Ui.menu("Du"), mx, my, 0xFFFFE6C4);
			Ui.text(g, Ui.menu("Spielzeit: " + Playtime.format(Playtime.total()) + " · heute " + Playtime.format(Playtime.today())), mx, my + 14, TEXT);
			drawBadges(g, Badges.mine(), mx, my + 30, mouseX, mouseY);
			Ui.text(g, Ui.menu("Wähle links einen Freund für sein Profil."), mx, my + 66, MUTED);
			Ui.text(g, Ui.menu("Neue Freunde: oben links den Minecraft-Namen eintragen."), mx, my + 78, MUTED);
			return;
		}
		String uuid = str(f, "uuid");
		Heads.draw(g, this.font, uuid, mx, my, 32);
		Ui.text(g, Ui.title(str(f, "name")), mx + 40, my + 2, 0xFFFFE6C4);
		dot(g, mx + 40, my + 20, bool(f, "online"));
		Ui.text(g, Ui.menu(where(f)), mx + 48, my + 18, bool(f, "online") ? GREEN : MUTED);
		long playtime = f.has("playtime") ? f.get("playtime").getAsLong() : 0;
		long today = f.has("today") ? f.get("today").getAsLong() : 0;
		Ui.text(g, Ui.menu("Spielzeit: " + Playtime.format(playtime) + " · heute " + Playtime.format(today)), mx, my + 40, TEXT);
		List<String> badges = new ArrayList<>();
		if (f.has("badges")) for (JsonElement b : f.getAsJsonArray("badges")) badges.add(b.getAsString());
		drawBadges(g, badges, mx, my + 54, mouseX, mouseY);
		String contrail = str(f, "contrail");
		int ci = Math.max(0, Modules.CONTRAIL_COLORS.indexOf(contrail));
		int rgb = ci == 7 ? java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 3000) / 3000F, 0.6F, 1F) : Modules.CONTRAIL_RGB[ci];
		g.fill(mx, my + 86, mx + 10, my + 96, 0xFF000000 | rgb);
		Ui.text(g, Ui.menu("Kondensstreifen: " + CONTRAIL_NAMES[ci]), mx + 14, my + 87, TEXT);
		int by = my + 106;
		int bw = Math.min(150, (mw - 6) / 2);
		String name = str(f, "name");
		button(g, mx, by, bw, 18, "Nachricht schreiben", mouseX, mouseY, () -> openDirect(uuid, name));
		button(g, mx + bw + 6, by, bw, 18, "In Party einladen", mouseX, mouseY,
			() -> friendAction("/v1/party/invite", uuid, name + " ist eingeladen"));
		String server = str(f, "server");
		if (bool(f, "online") && !server.isEmpty() && !server.equals("Einzelspieler")) {
			button(g, mx, by + 22, bw, 18, "Mitspielen", mouseX, mouseY, () -> join(server));
		}
		button(g, mx + bw + 6, by + 22, bw, 18, "Freund entfernen", mouseX, mouseY, () ->
			this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
				this.minecraft.gui.setScreen(this);
				if (yes) {
					friendId = null;
					friendAction("/v1/friends/remove", uuid, name + " entfernt");
				}
			}, Component.literal(name + " entfernen?"), Component.literal(name + " verschwindet aus deiner Freundesliste und du aus seiner."))));
	}

	private void drawBadges(GuiGraphicsExtractor g, List<String> ids, int x, int y, int mouseX, int mouseY) {
		if (ids.isEmpty()) {
			Ui.text(g, Ui.menu("Noch keine Abzeichen"), x, y, MUTED);
			return;
		}
		int bx = x;
		int row = 0;
		for (String id : ids) {
			Badges.Badge b = Badges.byId(id);
			if (b == null) continue;
			Component name = Ui.menu(b.name());
			int w = 12 + this.font.width(name) + 8;
			if (bx + w > mx + mw) {
				bx = x;
				row++;
				if (row > 1) break;
			}
			int by = y + row * 14;
			g.text(this.font, glyphs(String.valueOf(b.glyph())), bx, by, 0xFFFFFFFF, false);
			Ui.text(g, name, bx + 12, by + 0.5F, ACCENT);
			if (mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + 10) {
				g.setTooltipForNextFrame(this.font, Component.literal(b.description()), mouseX, mouseY);
			}
			bx += w;
		}
	}

	// ----- Party -----

	private @Nullable JsonObject partyObject() {
		return party != null && party.has("party") && party.get("party").isJsonObject() ? party.getAsJsonObject("party") : null;
	}

	private void drawPartyList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		JsonObject p = partyObject();
		int y = ly;
		JsonArray invites = party != null && party.has("partyInvites") ? party.getAsJsonArray("partyInvites") : new JsonArray();
		for (JsonElement el : invites) {
			JsonObject inv = el.getAsJsonObject();
			String id = str(inv, "party");
			g.fill(lx, y, lx + lw, y + 40, 0x40E8742F);
			Ui.text(g, Ui.menu(fit(str(inv, "leaderName") + " lädt dich ein", lw - 8)), lx + 4, y + 4, TEXT);
			int half = (lw - 12) / 2;
			button(g, lx + 4, y + 18, half, 16, "Beitreten", mouseX, mouseY, () -> partyAction("/v1/party/join", id, "Du bist in der Party"));
			button(g, lx + 8 + half, y + 18, half, 16, "Ablehnen", mouseX, mouseY, () -> partyAction("/v1/party/decline", id, ""));
			y += 44;
		}
		if (p == null) {
			Ui.text(g, Ui.menu("Du bist in keiner Party."), lx + 4, y + 4, MUTED);
			return;
		}
		Ui.text(g, Ui.menu("In der Party"), lx + 4, y + 2, ACCENT);
		y += 14;
		String me = me();
		String leader = str(p, "leader");
		for (JsonElement el : p.getAsJsonArray("members")) {
			JsonObject m = el.getAsJsonObject();
			String uuid = str(m, "uuid");
			g.fill(lx, y, lx + lw, y + 22, 0x24000000);
			Heads.draw(g, this.font, uuid, lx + 4, y + 3, 16);
			dot(g, lx + 17, y + 15, bool(m, "online"));
			String label = (uuid.equals(leader) ? "★ " : "") + str(m, "name") + (uuid.equals(me) ? " (du)" : "");
			boolean canKick = Objects.equals(me, leader) && !uuid.equals(me);
			Ui.text(g, Ui.menu(fit(label, lw - 30 - (canKick ? 20 : 0))), lx + 25, y + 2, TEXT);
			Ui.text(g, Ui.menu(fit(where(m), lw - 30)), lx + 25, y + 12, bool(m, "online") ? GREEN : MUTED);
			if (canKick) {
				String name = str(m, "name");
				button(g, lx + lw - 20, y + 3, 16, 16, "x", mouseX, mouseY, () -> friendAction("/v1/party/kick", uuid, name + " ist raus"));
			}
			y += 24;
		}
	}

	private void drawParty(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		JsonObject p = partyObject();
		int bw = Math.min(170, (mw - 6) / 2);
		if (inviting) {
			Ui.text(g, Ui.menu("Freunde einladen"), mx, my, 0xFFFFE6C4);
			button(g, mx + mw - 64, my - 1, 64, 16, "Fertig", mouseX, mouseY, () -> inviting = false);
			int y = my + 18;
			if (friends == null) return;
			for (JsonElement el : friends.getAsJsonArray("friends")) {
				JsonObject f = el.getAsJsonObject();
				String uuid = str(f, "uuid");
				boolean member = false;
				if (p != null) for (JsonElement m : p.getAsJsonArray("members")) if (str(m.getAsJsonObject(), "uuid").equals(uuid)) member = true;
				if (member) continue;
				if (y > my + mh - 22) break;
				Heads.draw(g, this.font, uuid, mx, y + 2, 16);
				dot(g, mx + 13, y + 14, bool(f, "online"));
				Ui.text(g, Ui.menu(str(f, "name")), mx + 22, y + 2, TEXT);
				Ui.text(g, Ui.menu(fit(where(f), mw - 110)), mx + 22, y + 11, MUTED);
				String name = str(f, "name");
				button(g, mx + mw - 80, y + 2, 80, 16, "Einladen", mouseX, mouseY, () -> friendAction("/v1/party/invite", uuid, name + " ist eingeladen"));
				y += 22;
			}
			return;
		}
		if (p == null) {
			Ui.text(g, Ui.menu("Zusammen von Server zu Server"), mx, my, 0xFFFFE6C4);
			String[] lines = {"In einer Party seht ihr, wo der Leiter spielt.", "Wechselt er den Server, kommst du mit einem Klick hinterher",
				"(oder automatisch, siehe unten). Dazu gibt es einen Party-Chat."};
			for (int i = 0; i < lines.length; i++) Ui.text(g, Ui.menu(lines[i]), mx, my + 16 + i * 11, MUTED);
			button(g, mx, my + 56, bw, 18, "Party starten", mouseX, mouseY, () -> partyAction("/v1/party/create", null, "Party gestartet"));
			button(g, mx + bw + 6, my + 56, bw, 18, "Freunde einladen", mouseX, mouseY, () -> inviting = true);
		} else {
			String me = me();
			String leader = str(p, "leader");
			boolean lead = Objects.equals(me, leader);
			String leaderName = "";
			JsonObject leaderObj = null;
			for (JsonElement el : p.getAsJsonArray("members")) {
				if (str(el.getAsJsonObject(), "uuid").equals(leader)) leaderObj = el.getAsJsonObject();
			}
			if (leaderObj != null) leaderName = str(leaderObj, "name");
			Ui.text(g, Ui.menu(lead ? "Du leitest die Party" : "Party von " + leaderName), mx, my, 0xFFFFE6C4);
			if (leaderObj != null && !lead) Ui.text(g, Ui.menu(where(leaderObj)), mx, my + 13, MUTED);
			button(g, mx, my + 32, bw, 18, "Party-Chat öffnen", mouseX, mouseY, () -> openChat(str(p, "chat")));
			button(g, mx + bw + 6, my + 32, bw, 18, "Freunde einladen", mouseX, mouseY, () -> inviting = true);
			String target = leaderObj != null && bool(leaderObj, "online") ? str(leaderObj, "server") : "";
			boolean canFollow = !lead && !target.isEmpty() && !target.equals("Einzelspieler");
			if (canFollow) button(g, mx, my + 54, bw, 18, "Hinterher: " + target, mouseX, mouseY, () -> join(target));
			button(g, mx + bw + 6, my + 54, bw, 18, "Party verlassen", mouseX, mouseY, () -> partyAction("/v1/party/leave", null, "Du hast die Party verlassen"));
		}
		// following the leader automatically is an option of its own
		boolean follow = Modules.on("party_follow");
		button(g, mx, my + mh - 20, Math.min(260, mw), 18, "Leiter automatisch folgen: " + (follow ? "an" : "aus"), mouseX, mouseY,
			() -> Modules.set("party_follow", !follow));
	}

	// ---------- Eingaben ----------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.contains(event.x(), event.y())) {
				JsonObject actionsBefore = actionsFor;
				boolean plusBefore = plusMenu;
				h.action().run();
				// a click on anything else closes the little menus (unless that click opened them)
				if (!h.menu()) {
					if (actionsFor == actionsBefore) actionsFor = null;
					if (plusMenu == plusBefore) plusMenu = false;
				}
				return true;
			}
		}
		actionsFor = null;
		plusMenu = false;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < mx - 4) {
			listScroll = Mth.clamp(listScroll - (int) (scrollY * 20), 0, Math.max(0, listContentH - lh));
		} else {
			msgScroll = Math.max(0, msgScroll + (int) (scrollY * 20));
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
		if (enter && input != null && input.isFocused() && input.visible) {
			if (tab == Tab.CHATS) send();
			else if (tab == Tab.FRIENDS) addFriend();
			return true;
		}
		if (event.key() == InputConstants.KEY_ESCAPE && (actionsFor != null || plusMenu || replyTo != null)) {
			actionsFor = null;
			plusMenu = false;
			replyTo = null;
			updateInput();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		Lagerfeuer.setOpenChat(null);
		super.removed();
	}

	@Override
	public void onClose() {
		Lagerfeuer.setOpenChat(null);
		this.minecraft.gui.setScreen(parent);
	}
}
