package net.emberleaf.client.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.feature.Badges;
import net.emberleaf.client.feature.Contrails;
import net.emberleaf.client.feature.Emotes;
import net.emberleaf.client.menu.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * What the Lagerfeuer told the client last: unread messages, little notifications, your party (and following its leader)
 * and the profiles of the Emberleaf players around you. Everything here runs on the game thread.
 */
public final class Lagerfeuer {
	public record Notice(String title, String text, long at, @Nullable String chat) {}

	public record Member(String uuid, String name, boolean online, @Nullable String server) {}

	public record Party(String id, String leader, String chat, List<Member> members) {}

	public record Invite(String party, String leaderName) {}

	/** Another player as the Lagerfeuer reports them (playtime in seconds). */
	public record Profile(String name, long playtime, List<String> badges, String contrail) {}

	private static final long NOTICE_MS = 6500;
	private static final Deque<Notice> NOTICES = new ArrayDeque<>();
	private static final Map<UUID, Profile> PROFILES = new ConcurrentHashMap<>();
	private static final Set<String> announcedInvites = new HashSet<>();
	private static final Set<String> friendIds = ConcurrentHashMap.newKeySet();
	private static long cursor = -1;
	private static int unread;
	private static @Nullable Party party;
	private static List<Invite> invites = List.of();
	private static @Nullable String leaderServer;
	private static @Nullable String openChat;
	private static long changes;

	private Lagerfeuer() {}

	/** The newest message id the client knows (-1 = not yet asked). */
	public static long cursor() {
		return cursor;
	}

	public static int unread() {
		return unread;
	}

	public static @Nullable Party party() {
		return party;
	}

	public static List<Invite> invites() {
		return invites;
	}

	/** Grows whenever something new arrived (screens use it to reload). */
	public static long changes() {
		return changes;
	}

	public static @Nullable Profile profile(UUID uuid) {
		return PROFILES.get(uuid);
	}

	public static boolean isFriend(UUID uuid) {
		return friendIds.contains(uuid.toString().replace("-", ""));
	}

	/** The chat the Lagerfeuer screen shows right now (no notifications for it). */
	public static void setOpenChat(@Nullable String chat) {
		openChat = chat;
	}

	public static UUID uuid(String compact) {
		return UUID.fromString(compact.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5"));
	}

	static void onFriends(JsonObject data) {
		friendIds.clear();
		for (JsonElement el : data.getAsJsonArray("friends")) {
			JsonObject f = el.getAsJsonObject();
			String id = f.get("uuid").getAsString();
			friendIds.add(id);
			// friends' profiles are known even when they are not around (player menu, badges above their names)
			if (f.has("badges")) {
				UUID uuid = uuid(id);
				List<String> badges = new ArrayList<>();
				for (JsonElement b : f.getAsJsonArray("badges")) badges.add(b.getAsString());
				String contrail = f.has("contrail") && !f.get("contrail").isJsonNull() ? f.get("contrail").getAsString() : "white";
				long playtime = f.has("playtime") ? f.get("playtime").getAsLong() : 0;
				Badges.putRemote(uuid, badges);
				Contrails.putRemoteColor(uuid, contrail);
				PROFILES.put(uuid, new Profile(f.get("name").getAsString(), playtime, List.copyOf(badges), contrail));
			}
		}
	}

	/** True while a notification is on screen (for tests). */
	public static boolean hasNotices() {
		long now = System.currentTimeMillis();
		return NOTICES.stream().anyMatch(n -> now - n.at() < NOTICE_MS);
	}

	static void onSync(JsonObject r) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (r.has("emotes")) {
			for (Map.Entry<String, JsonElement> e : r.getAsJsonObject("emotes").entrySet()) {
				JsonObject emote = e.getValue().getAsJsonObject();
				Emotes.playRemote(mc, uuid(e.getKey()), emote.get("id").getAsString(), emote.get("age").getAsLong());
			}
		}
		if (r.has("players")) {
			for (Map.Entry<String, JsonElement> e : r.getAsJsonObject("players").entrySet()) {
				JsonObject p = e.getValue().getAsJsonObject();
				UUID uuid = uuid(e.getKey());
				List<String> badges = new ArrayList<>();
				for (JsonElement b : p.getAsJsonArray("badges")) badges.add(b.getAsString());
				String contrail = p.has("contrail") ? p.get("contrail").getAsString() : "white";
				Badges.putRemote(uuid, badges);
				Contrails.putRemoteColor(uuid, contrail);
				PROFILES.put(uuid, new Profile(p.get("name").getAsString(), p.get("playtime").getAsLong(), List.copyOf(badges), contrail));
				if (p.has("outfit")) FriendsSync.outfitChanged(uuid, p.get("outfit").getAsLong());
			}
		}
		JsonObject chat = r.getAsJsonObject("chat");
		if (chat != null) {
			int before = unread;
			unread = chat.get("unread").getAsInt();
			boolean first = cursor < 0;
			cursor = Math.max(cursor, chat.get("cursor").getAsLong());
			if (!first) {
				for (JsonElement el : chat.getAsJsonArray("messages")) notifyMessage(mc, el.getAsJsonObject());
			}
			if (unread != before) changes++;
		}
		Party old = party;
		party = parseParty(r.get("party"));
		List<Invite> list = new ArrayList<>();
		if (r.has("partyInvites")) {
			for (JsonElement el : r.getAsJsonArray("partyInvites")) {
				JsonObject i = el.getAsJsonObject();
				Invite invite = new Invite(i.get("party").getAsString(), i.get("leaderName").getAsString());
				list.add(invite);
				if (announcedInvites.add(invite.party())) announceInvite(mc, invite);
			}
		}
		invites = List.copyOf(list);
		if (!Objects.equals(old, party)) changes++;
		followLeader(mc);
		while (!NOTICES.isEmpty() && now - NOTICES.peekFirst().at() > NOTICE_MS) NOTICES.pollFirst();
	}

	static @Nullable Party parseParty(@Nullable JsonElement el) {
		if (el == null || !el.isJsonObject()) return null;
		JsonObject p = el.getAsJsonObject();
		List<Member> members = new ArrayList<>();
		for (JsonElement m : p.getAsJsonArray("members")) members.add(member(m.getAsJsonObject()));
		return new Party(p.get("id").getAsString(), p.get("leader").getAsString(), p.get("chat").getAsString(), List.copyOf(members));
	}

	static Member member(JsonObject m) {
		JsonElement server = m.get("server");
		return new Member(m.get("uuid").getAsString(), m.get("name").getAsString(), m.has("online") && m.get("online").getAsBoolean(),
			server == null || server.isJsonNull() ? null : server.getAsString());
	}

	/** Updates the party right after a party button was used (no waiting for the next sync). */
	public static void setParty(JsonObject state) {
		party = parseParty(state.get("party"));
		changes++;
	}

	// ---------- Benachrichtigungen ----------

	public static String preview(String kind, String text) {
		return switch (kind) {
			case "image" -> "📷 Bild";
			case "invite" -> "🎮 lädt dich auf einen Server ein";
			case "game" -> "🎲 " + text;
			case "deleted" -> "Nachricht gelöscht";
			default -> text;
		};
	}

	private static void notifyMessage(Minecraft mc, JsonObject m) {
		String chat = m.get("chat").getAsString();
		changes++;
		if (chat.equals(openChat) || !Modules.on("lagerfeuer_notify")) return;
		String who = m.get("senderName").getAsString();
		String kind = m.get("chatKind").getAsString();
		JsonElement name = m.get("chatName");
		String where = kind.equals("party") ? "Party" : name != null && !name.isJsonNull() ? name.getAsString() : "Gruppe";
		String title = kind.equals("dm") ? who : where + " · " + who;
		String text = preview(m.get("kind").getAsString(), m.get("text").getAsString());
		notice(title, text, chat);
		if (mc.player != null) {
			MutableComponent line = prefix().append(Component.literal(title + ": ").withStyle(ChatFormatting.GOLD))
				.append(Component.literal(text).withStyle(ChatFormatting.WHITE));
			mc.gui.hud.getChat().addClientSystemMessage(line.withStyle(Style.EMPTY
				.withClickEvent(new ClickEvent.RunCommand("/emberleaf chat " + chat))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Klick: im Lagerfeuer-Chat antworten")))));
		}
	}

	private static void announceInvite(Minecraft mc, Invite invite) {
		notice("Party-Einladung", invite.leaderName() + " lädt dich ein", null);
		if (mc.player == null) return;
		mc.gui.hud.getChat().addClientSystemMessage(prefix()
			.append(Component.literal(invite.leaderName() + " lädt dich in die Party ein ").withStyle(ChatFormatting.WHITE))
			.append(button("Beitreten", "/emberleaf party join " + invite.party(), ChatFormatting.GREEN))
			.append(" ")
			.append(button("Ablehnen", "/emberleaf party decline " + invite.party(), ChatFormatting.RED)));
	}

	public static MutableComponent prefix() {
		return Component.literal("[Lagerfeuer] ").withStyle(ChatFormatting.GOLD);
	}

	public static MutableComponent button(String label, String command, ChatFormatting color) {
		return Component.literal("[" + label + "]").withStyle(Style.EMPTY.withColor(color).withBold(true)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(label))));
	}

	public static void notice(String title, String text, @Nullable String chat) {
		NOTICES.addLast(new Notice(title, text, System.currentTimeMillis(), chat));
		while (NOTICES.size() > 3) NOTICES.pollFirst();
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.5F, 0.35F));
	}

	/**
	 * The newest notifications, sliding in and fading out: in the game top right (below the effect icons),
	 * in the menus bottom right (the friends list is at the top there).
	 */
	public static void renderNotices(GuiGraphicsExtractor g) {
		if (NOTICES.isEmpty()) return;
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		boolean inWorld = mc.level != null && mc.player != null;
		int shown = 0;
		for (Notice n : NOTICES) if (now - n.at() < NOTICE_MS) shown++;
		int y = inWorld ? 6 + (mc.player.getActiveEffects().isEmpty() ? 0 : 52) : g.guiHeight() - 18 - shown * 34;
		for (Notice n : NOTICES) {
			float age = (now - n.at()) / (float) NOTICE_MS;
			if (age >= 1) continue;
			float fade = Mth.clamp(Math.min(age * 8, (1 - age) * 6), 0, 1);
			Component title = Ui.menu(n.title());
			Component text = Ui.menu(cut(n.text(), 190));
			int w = Math.max(Ui.font().width(title), Ui.font().width(text)) + 30;
			int x = g.guiWidth() - 6 - w + Math.round((1 - Math.min(1, age * 8)) * 30);
			Ui.nineSlice(g, Ui.PANEL, x, y, w, 30, 6, ARGB.white(0.95F * fade));
			g.fill(x + 7, y + 8, x + 11, y + 22, ARGB.color(fade, 0xE8742F));
			Ui.text(g, title, x + 16, y + 5, ARGB.multiplyAlpha(0xFFFFD6A8, fade));
			Ui.text(g, text, x + 16, y + 17, ARGB.multiplyAlpha(0xFFFFEBD4, fade));
			y += 34;
		}
	}

	private static String cut(String text, int max) {
		if (Ui.font().width(Ui.menu(text)) <= max) return text;
		while (!text.isEmpty() && Ui.font().width(Ui.menu(text + "…")) > max) text = text.substring(0, text.length() - 1);
		return text + "…";
	}

	// ---------- Party: dem Leiter folgen ----------

	private static void followLeader(Minecraft mc) {
		String me = FriendsSync.myId();
		if (party == null || me == null || party.leader().equals(me)) {
			leaderServer = null;
			return;
		}
		Member leader = party.members().stream().filter(m -> m.uuid().equals(party.leader())).findFirst().orElse(null);
		String server = leader != null && leader.online() ? leader.server() : null;
		if (Objects.equals(server, leaderServer)) return;
		boolean firstLook = leaderServer == null && server != null && mc.getCurrentServer() != null;
		leaderServer = server;
		if (server == null || server.equals("Einzelspieler")) return;
		ServerData current = mc.getCurrentServer();
		if (current != null && current.ip.equalsIgnoreCase(server)) return;
		if (Modules.on("party_follow") && mc.gui.screen() == null && !firstLook) {
			notice("Party", "Du folgst " + leader.name() + " auf " + server, null);
			joinServer(mc, server);
			return;
		}
		notice("Party", leader.name() + " spielt auf " + server, null);
		if (mc.player != null) {
			mc.gui.hud.getChat().addClientSystemMessage(prefix()
				.append(Component.literal(leader.name() + " spielt jetzt auf " + server + " ").withStyle(ChatFormatting.WHITE))
				.append(button("Hinterher", "/emberleaf party follow", ChatFormatting.GREEN)));
		}
	}

	/** The server the party leader plays on, if you can follow them there. */
	public static @Nullable String leaderServer() {
		return leaderServer;
	}

	/** Leaves the current world (if any) and connects to a server. */
	public static void joinServer(Minecraft mc, String address) {
		if (mc.level != null) mc.disconnectFromWorld(Component.literal("Unterwegs zu " + address));
		ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(address),
			new ServerData(address, address, ServerData.Type.OTHER), false, null);
	}

	/** Badge pictures + names for tooltips (e.g. in the player menu). */
	public static List<Component> badgeLines(List<String> ids) {
		List<Component> lines = new ArrayList<>();
		for (String id : ids) {
			Badges.Badge b = Badges.byId(id);
			if (b != null) lines.add(Component.literal(String.valueOf(b.glyph())).withStyle(s -> s.withFont(Badges.FONT)).append(" " + b.name()));
		}
		return lines;
	}

	static JsonArray array(List<String> values) {
		JsonArray a = new JsonArray();
		for (String v : values) a.add(v);
		return a;
	}
}
