package net.emberleaf.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A tiny stand-in for the Lagerfeuer (the real one is the Cloudflare worker, tested in emberleaf-server/test):
 * answers like the worker with a few friends, two chats, a game and a party, so the in-game screens can be photographed.
 */
final class MockLagerfeuer {
	static final String PERRY = "b".repeat(32);
	static final String LXCA = "c".repeat(32);
	static final String MIA = "d".repeat(32);

	private final HttpServer server;
	private final String me;
	private final String dm;
	private final List<JsonObject> dmMessages = new ArrayList<>();
	private final List<JsonObject> groupMessages = new ArrayList<>();
	private int nextId = 20;
	private volatile boolean announce;

	/** The next sync brings a new message from Perry. */
	void announceNext() {
		announce = true;
	}

	MockLagerfeuer(String me) throws IOException {
		this.me = me;
		this.dm = "d" + (me.compareTo(PERRY) < 0 ? me + PERRY : PERRY + me);
		long now = System.currentTimeMillis();
		dmMessages.add(message(1, dm, PERRY, now - 3_600_000, "text", "Hey! Bist du heute Abend am Lagerfeuer?", null));
		dmMessages.add(message(2, dm, me, now - 3_500_000, "text", "Klar, ab 19 Uhr bin ich da", null));
		JsonObject gold = message(3, dm, PERRY, now - 600_000, "text", "Ich hab die neue Elytra-Farbe freigespielt: Gold!", null);
		JsonObject reactions = new JsonObject();
		JsonArray fire = new JsonArray();
		fire.add(me);
		reactions.add("fire", fire);
		gold.add("reactions", reactions);
		dmMessages.add(gold);
		JsonObject invite = message(4, dm, PERRY, now - 500_000, "invite", "Komm rein, wir bauen am Hafen", null);
		JsonObject inviteData = new JsonObject();
		inviteData.addProperty("server", "eu8813304.g-portal.game");
		invite.add("data", inviteData);
		dmMessages.add(invite);
		JsonObject game = message(5, dm, PERRY, now - 400_000, "game", "Tic-Tac-Toe", null);
		JsonObject gameData = new JsonObject();
		gameData.addProperty("game", "ttt");
		gameData.addProperty("board", "o.x.x....");
		JsonArray players = new JsonArray();
		players.add(PERRY);
		players.add(me);
		gameData.add("players", players);
		gameData.addProperty("turn", 1);
		game.add("data", gameData);
		dmMessages.add(game);
		JsonObject reply = new JsonObject();
		reply.addProperty("id", 3);
		reply.addProperty("sender", PERRY);
		reply.addProperty("kind", "text");
		reply.addProperty("text", "Ich hab die neue Elytra-Farbe freigespielt: Gold!");
		dmMessages.add(message(6, dm, PERRY, now - 60_000, "text", "Du bist dran beim Tic-Tac-Toe 😄", reply));

		groupMessages.add(message(7, "g0123456789abcdef", me, now - 7_200_000, "system", "SenLxca hat die Gruppe „Die Bande“ erstellt", null));
		groupMessages.add(message(8, "g0123456789abcdef", MIA, now - 7_000_000, "text", "Wer baut mit am Leuchtturm?", null));
		groupMessages.add(message(9, "g0123456789abcdef", LXCA, now - 6_900_000, "text", "Ich! Bring Glas mit", null));
		JsonObject c4 = message(10, "g0123456789abcdef", LXCA, now - 6_800_000, "game", "Vier gewinnt", null);
		JsonObject c4Data = new JsonObject();
		c4Data.addProperty("game", "c4");
		c4Data.addProperty("board", ".".repeat(35) + ".xxo...");
		JsonArray c4Players = new JsonArray();
		c4Players.add(LXCA);
		c4Players.add(MIA);
		c4Data.add("players", c4Players);
		c4Data.addProperty("turn", 0);
		c4Data.addProperty("last", 38);
		c4.add("data", c4Data);
		groupMessages.add(c4);

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::handle);
		server.start();
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	void stop() {
		server.stop(0);
	}

	private JsonObject message(int id, String chat, String sender, long sent, String kind, String text, JsonObject reply) {
		JsonObject m = new JsonObject();
		m.addProperty("id", id);
		m.addProperty("chat", chat);
		m.addProperty("sender", sender);
		m.addProperty("sent", sent);
		m.addProperty("kind", kind);
		m.addProperty("text", text);
		m.add("reply", reply);
		m.add("reactions", new JsonObject());
		return m;
	}

	private static JsonObject member(String uuid, String name, boolean online, String server, long read) {
		JsonObject m = new JsonObject();
		m.addProperty("uuid", uuid);
		m.addProperty("name", name);
		m.addProperty("online", online);
		m.addProperty("server", server);
		m.addProperty("read", read);
		m.addProperty("lastSeen", System.currentTimeMillis() / 1000 - 600);
		return m;
	}

	private static JsonObject friend(String uuid, String name, boolean online, String server, long playtime, String contrail, String... badges) {
		JsonObject f = member(uuid, name, online, server, 0);
		f.addProperty("playtime", playtime);
		f.addProperty("today", 5400);
		f.addProperty("contrail", contrail);
		JsonArray list = new JsonArray();
		for (String b : badges) list.add(b);
		f.add("badges", list);
		return f;
	}

	private JsonObject chats() {
		JsonArray list = new JsonArray();
		JsonObject a = new JsonObject();
		a.addProperty("id", dm);
		a.addProperty("kind", "dm");
		a.addProperty("name", "PerryDasHuhn");
		a.addProperty("partner", PERRY);
		a.addProperty("unread", 2);
		a.add("lastMessage", dmMessages.get(dmMessages.size() - 1));
		list.add(a);
		JsonObject b = new JsonObject();
		b.addProperty("id", "g0123456789abcdef");
		b.addProperty("kind", "group");
		b.addProperty("name", "Die Bande");
		b.addProperty("unread", 0);
		b.add("lastMessage", groupMessages.get(groupMessages.size() - 1));
		list.add(b);
		JsonObject party = new JsonObject();
		party.addProperty("id", "p00aa11bb22cc33dd");
		party.addProperty("kind", "party");
		party.addProperty("name", "Party");
		party.addProperty("unread", 0);
		list.add(party);
		JsonObject r = new JsonObject();
		r.add("chats", list);
		return r;
	}

	private JsonObject messages(String chat) {
		JsonObject r = new JsonObject();
		JsonObject info = new JsonObject();
		boolean direct = chat.equals(dm);
		info.addProperty("id", chat);
		info.addProperty("kind", direct ? "dm" : chat.startsWith("p") ? "party" : "group");
		info.addProperty("name", direct ? "PerryDasHuhn" : chat.startsWith("p") ? "Party" : "Die Bande");
		r.add("chat", info);
		JsonArray members = new JsonArray();
		members.add(member(me, "Player0", true, "Einzelspieler", 99));
		members.add(member(PERRY, "PerryDasHuhn", true, "eu8813304.g-portal.game", direct ? 2 : 0));
		if (!direct) {
			members.add(member(LXCA, "Lxca_Test", false, null, 10));
			members.add(member(MIA, "Mia_Baut", true, "mc.hypixel.net", 10));
		}
		r.add("members", members);
		JsonArray list = new JsonArray();
		for (JsonObject m : direct ? dmMessages : chat.startsWith("g") ? groupMessages : List.<JsonObject>of()) list.add(m);
		r.add("messages", list);
		r.addProperty("more", false);
		return r;
	}

	private JsonObject friends() {
		JsonObject r = new JsonObject();
		JsonObject meObj = new JsonObject();
		meObj.addProperty("uuid", me);
		meObj.addProperty("name", "Player0");
		r.add("me", meObj);
		JsonArray list = new JsonArray();
		list.add(friend(PERRY, "PerryDasHuhn", true, "eu8813304.g-portal.game", 181 * 3600, "gold", "alpha", "h100", "friend", "creator"));
		list.add(friend(MIA, "Mia_Baut", true, "mc.hypixel.net", 52 * 3600, "violet", "h50", "friend", "explorer"));
		list.add(friend(LXCA, "Lxca_Test", false, null, 3 * 3600, "white", "alpha", "friend"));
		r.add("friends", list);
		JsonArray incoming = new JsonArray();
		JsonObject req = new JsonObject();
		req.addProperty("uuid", "e".repeat(32));
		req.addProperty("name", "Neuling42");
		incoming.add(req);
		r.add("incoming", incoming);
		r.add("outgoing", new JsonArray());
		return r;
	}

	private JsonObject party() {
		JsonObject r = new JsonObject();
		JsonObject p = new JsonObject();
		p.addProperty("id", "00aa11bb22cc33dd");
		p.addProperty("leader", PERRY);
		p.addProperty("chat", "p00aa11bb22cc33dd");
		JsonArray members = new JsonArray();
		members.add(member(PERRY, "PerryDasHuhn", true, "eu8813304.g-portal.game", 0));
		members.add(member(me, "Player0", true, "Einzelspieler", 0));
		p.add("members", members);
		r.add("party", p);
		r.add("partyInvites", new JsonArray());
		return r;
	}

	private JsonObject sync(JsonObject body) {
		JsonObject r = party();
		r.addProperty("now", System.currentTimeMillis());
		r.add("emotes", new JsonObject());
		r.add("players", new JsonObject());
		JsonObject chat = new JsonObject();
		long since = body.has("chatSince") ? body.get("chatSince").getAsLong() : -1;
		JsonArray messages = new JsonArray();
		if (since >= 0 && announce) {
			// when the test asks for it: one new message for the notification
			announce = false;
			JsonObject m = message(nextId++, dm, PERRY, System.currentTimeMillis(), "text", "Bin am Hafen, kommst du?", null);
			m.addProperty("chatKind", "dm");
			m.addProperty("senderName", "PerryDasHuhn");
			messages.add(m);
		}
		chat.addProperty("unread", 2);
		chat.addProperty("cursor", Math.max(6, since + messages.size()));
		chat.add("messages", messages);
		r.add("chat", chat);
		return r;
	}

	private void handle(HttpExchange ex) throws IOException {
		String path = ex.getRequestURI().getPath();
		String query = ex.getRequestURI().getQuery();
		String method = ex.getRequestMethod();
		String text = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		JsonObject body = text.isBlank() ? new JsonObject() : JsonParser.parseString(text).getAsJsonObject();
		JsonObject out;
		switch (method + " " + path) {
			case "POST /v1/sync" -> out = sync(body);
			case "GET /v1/chats" -> out = chats();
			case "GET /v1/chat/messages" -> out = messages(query.replaceAll(".*chat=([^&]+).*", "$1"));
			case "GET /v1/friends" -> out = friends();
			case "GET /v1/party" -> out = party();
			case "GET /v1/outfits" -> {
				out = new JsonObject();
				out.add("outfits", new JsonObject());
			}
			case "POST /v1/chat/send" -> {
				JsonObject m = message(nextId++, dm, me, System.currentTimeMillis(), "text", body.has("text") ? body.get("text").getAsString() : "", null);
				dmMessages.add(m);
				out = new JsonObject();
				out.add("message", m);
				out.addProperty("chat", dm);
			}
			default -> {
				out = new JsonObject();
				out.addProperty("ok", true);
			}
		}
		byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("content-type", "application/json; charset=utf-8");
		ex.sendResponseHeaders(200, bytes.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(bytes);
		}
	}
}
