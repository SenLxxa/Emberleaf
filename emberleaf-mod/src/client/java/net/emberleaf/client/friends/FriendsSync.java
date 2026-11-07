package net.emberleaf.client.friends;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.emberleaf.client.feature.Badges;
import net.emberleaf.client.feature.Contrails;
import net.emberleaf.client.feature.Emotes;
import net.emberleaf.client.feature.Playtime;
import net.emberleaf.client.outfit.OutfitStore;
import net.emberleaf.client.outfit.Outfits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Talks to the Emberleaf friends server ("Lagerfeuer"): uploads your outfit, reports where you play, and every few seconds
 * syncs emotes, badges, playtime and contrail colours of the Emberleaf players around you, new chat messages and your party.
 * Network calls never run on the game thread.
 */
public final class FriendsSync {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> daemon(r, "Emberleaf Lagerfeuer"));
	/** For the chat screen and buttons, so a click never waits behind the background sync. */
	private static final ExecutorService CALLS = Executors.newFixedThreadPool(2, r -> daemon(r, "Emberleaf Lagerfeuer Chat"));
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final long OUTFIT_REFRESH_MS = 5 * 60_000;
	private static final long PRESENCE_MS = 45_000;
	private static final long WORLD_SYNC_MS = 3000;
	private static final long QUIET_SYNC_MS = 6000;
	private static final long MENU_SYNC_MS = 15_000;
	private static final long STATS_MS = 5 * 60_000;

	private static final AtomicBoolean busy = new AtomicBoolean();
	private static final Map<UUID, Long> fetched = new HashMap<>();
	private static volatile FriendsSession session;
	private static volatile boolean syncSupported = true;
	private static int uploadedGeneration = -1;
	private static long lastPresence;
	private static volatile long lastSync;
	private static volatile long lastStats;
	private static volatile String sentEmote = "";
	private static volatile long backoffUntil;
	private static boolean wasInWorld;
	private static int ticks;

	private record Snapshot(String server, User user, boolean online, @Nullable String address, List<UUID> nearby, int generation,
		@Nullable String outfitJson, @Nullable JsonObject sync, @Nullable String emoteKey, boolean stats) {}

	private FriendsSync() {}

	private static Thread daemon(Runnable r, String name) {
		Thread t = new Thread(r, name);
		t.setDaemon(true);
		return t;
	}

	public static void tick(Minecraft mc) {
		if (++ticks % 20 != 0) return;
		String server = FriendsConfig.serverUrl();
		if (server.isEmpty()) return;
		long now = System.currentTimeMillis();
		boolean inWorld = mc.player != null && mc.level != null;
		if (!inWorld) {
			if (wasInWorld) {
				// leaving a world: tell friends right away
				wasInWorld = false;
				submit(new Snapshot(server, mc.getUser(), false, null, List.of(), -1, null, syncSupported ? syncBody(false, null, List.of(), now) : null,
					null, false));
			} else if (syncSupported && session != null && now - lastSync > MENU_SYNC_MS && now > backoffUntil) {
				// in the menus: only messages and party invites
				submit(new Snapshot(server, mc.getUser(), false, null, List.of(), -1, null, syncBody(false, null, List.of(), now), null, false));
			}
			return;
		}
		wasInWorld = true;
		if (now < backoffUntil) return;
		List<UUID> nearby = new ArrayList<>();
		for (Player player : mc.level.players()) {
			if (player != mc.player) nearby.add(player.getUUID());
			if (nearby.size() >= 100) break;
		}
		ServerData current = mc.getCurrentServer();
		String address = current != null ? current.ip : "Einzelspieler";
		String emoteKey = emoteKey();
		boolean emoteChanged = !emoteKey.equals(sentEmote);
		// faster while other players are around (their emotes), slower when you are alone
		long every = nearby.isEmpty() ? QUIET_SYNC_MS : WORLD_SYNC_MS;
		JsonObject sync = syncSupported && (emoteChanged || now - lastSync >= every) ? syncBody(true, address, nearby, now) : null;
		submit(new Snapshot(server, mc.getUser(), true, address, nearby, OutfitStore.generation(), OutfitStore.currentJson(), sync,
			sync != null ? emoteKey : null, sync != null && sync.has("stats")));
	}

	private static String emoteKey() {
		String emote = Emotes.playing();
		return emote == null ? "" : emote + "@" + Emotes.startedAt();
	}

	/** What the sync sends (built on the game thread, sent in the background). */
	private static JsonObject syncBody(boolean online, @Nullable String address, List<UUID> nearby, long now) {
		JsonObject body = new JsonObject();
		body.addProperty("online", online);
		if (address != null) body.addProperty("server", address);
		JsonArray list = new JsonArray();
		for (UUID uuid : nearby) list.add(uuid.toString().replace("-", ""));
		body.add("nearby", list);
		body.addProperty("chatSince", Lagerfeuer.cursor());
		String key = emoteKey();
		if (online && !key.equals(sentEmote)) {
			String emote = Emotes.playing();
			if (emote == null) {
				body.add("emote", JsonNull.INSTANCE);
			} else {
				JsonObject e = new JsonObject();
				e.addProperty("id", emote);
				e.addProperty("elapsed", Math.max(0, now - Emotes.startedAt()));
				body.add("emote", e);
			}
		}
		if (now - lastStats > STATS_MS) body.add("stats", stats());
		return body;
	}

	/** Your playtime, badges and contrail colour for your friends (the server checks the hours itself). */
	private static JsonObject stats() {
		JsonObject s = new JsonObject();
		s.addProperty("playtime", Playtime.total());
		s.addProperty("today", Playtime.today());
		JsonArray badges = new JsonArray();
		for (String b : Badges.mine()) badges.add(b);
		s.add("badges", badges);
		s.addProperty("contrail", Contrails.myColor());
		return s;
	}

	/** Send your stats with the next sync (e.g. after you picked another contrail colour). */
	public static void statsChanged() {
		lastStats = 0;
	}

	private static void submit(Snapshot snapshot) {
		if (!busy.compareAndSet(false, true)) return;
		IO.execute(() -> {
			try {
				work(snapshot);
			} catch (IOException | RuntimeException e) {
				LOG.debug("Emberleaf Lagerfeuer nicht erreichbar: {}", e.toString());
				backoffUntil = System.currentTimeMillis() + 30_000;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				busy.set(false);
			}
		});
	}

	private static void work(Snapshot s) throws IOException, InterruptedException {
		FriendsSession session = session(s.server, s.user);
		if (session == null) return;
		long now = System.currentTimeMillis();
		if (s.online && s.generation != uploadedGeneration) {
			send(session, HttpRequest.newBuilder(URI.create(s.server + "/v1/outfit"))
				.PUT(HttpRequest.BodyPublishers.ofString(s.outfitJson != null ? s.outfitJson : "{}", StandardCharsets.UTF_8)));
			uploadedGeneration = s.generation;
		}
		if (s.sync != null && syncSupported) {
			try {
				JsonObject response = post(session, "/v1/sync", s.sync.toString());
				lastSync = now;
				lastPresence = now;
				if (s.emoteKey != null) sentEmote = s.emoteKey;
				if (s.stats) lastStats = now;
				Minecraft.getInstance().execute(() -> Lagerfeuer.onSync(response));
			} catch (LagerfeuerError e) {
				if (e.status != 404) throw e;
				// an older Lagerfeuer without chat and sync: keep the simple online status
				LOG.info("Emberleaf Lagerfeuer: Server kennt /v1/sync noch nicht (alte worker.js), nur Online-Status");
				syncSupported = false;
			}
		}
		if (!syncSupported && (!s.online || now - lastPresence > PRESENCE_MS)) {
			JsonObject presence = new JsonObject();
			presence.addProperty("online", s.online);
			if (s.online) presence.addProperty("server", s.address);
			post(session, "/v1/presence", presence.toString());
			lastPresence = now;
		}
		if (!s.online) return;
		List<UUID> due = new ArrayList<>();
		for (UUID uuid : s.nearby) {
			Long last = fetched.get(uuid);
			if (last == null || now - last > OUTFIT_REFRESH_MS) due.add(uuid);
			if (due.size() >= 100) break;
		}
		if (!due.isEmpty()) fetchOutfits(session, due, now);
	}

	private static void fetchOutfits(FriendsSession session, List<UUID> uuids, long now) throws IOException, InterruptedException {
		StringBuilder ids = new StringBuilder();
		for (UUID uuid : uuids) {
			if (!ids.isEmpty()) ids.append(',');
			ids.append(uuid.toString().replace("-", ""));
		}
		JsonObject outfits = send(session, HttpRequest.newBuilder(URI.create(session.server() + "/v1/outfits?uuids=" + ids)).GET())
			.getAsJsonObject("outfits");
		for (UUID uuid : uuids) {
			fetched.put(uuid, now);
			JsonElement entry = outfits.get(uuid.toString().replace("-", ""));
			if (entry != null && entry.getAsJsonObject().has("items")) {
				Outfits.putRemote(uuid, OutfitStore.parse(entry.getAsJsonObject().getAsJsonObject("items")));
			} else {
				Outfits.removeRemote(uuid);
			}
		}
	}

	// ---------- Anmeldung (wie beim Betreten eines Minecraft-Servers) ----------

	private static synchronized @Nullable FriendsSession session(String server, User user) throws IOException, InterruptedException {
		FriendsSession current = session != null ? session : FriendsConfig.loadSession();
		String uuid = user.getProfileId().toString().replace("-", "");
		long nowSec = System.currentTimeMillis() / 1000;
		if (current != null && current.server().equals(server) && current.uuid().equals(uuid) && current.expires() > nowSec + 3600) {
			session = current;
			return current;
		}
		if (System.currentTimeMillis() < loginBlockedUntil) return null;
		String serverId = HexFormat.of().formatHex(new SecureRandom().generateSeed(16));
		try {
			Minecraft.getInstance().services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
		} catch (Exception e) {
			LOG.info("Emberleaf Lagerfeuer: Minecraft-Anmeldung nicht möglich ({}), Freunde sind deaktiviert", e.getMessage());
			loginBlockedUntil = System.currentTimeMillis() + 10 * 60_000;
			backoffUntil = loginBlockedUntil;
			return null;
		}
		JsonObject request = new JsonObject();
		request.addProperty("name", user.getName());
		request.addProperty("serverId", serverId);
		HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(server + "/v1/auth"))
			.timeout(Duration.ofSeconds(15))
			.header("content-type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(request.toString())).build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) throw new IOException("auth " + response.statusCode());
		JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
		session = new FriendsSession(server, data.get("token").getAsString(), data.get("uuid").getAsString(), data.get("expires").getAsLong());
		FriendsConfig.saveSession(session.server(), session.token(), session.uuid(), user.getName(), session.expires());
		return session;
	}

	/** Your own UUID at the Lagerfeuer (no dashes), once logged in. */
	public static @Nullable String myId() {
		FriendsSession s = session;
		return s != null ? s.uuid() : null;
	}

	// ---------- Freundesliste (für das Hauptmenü) ----------

	/** One friend as the Lagerfeuer reports it; {@code server} is null when offline. */
	public record Friend(String uuid, String name, boolean online, @Nullable String server) {
		public boolean canJoin() {
			return online && server != null && !server.equals("Einzelspieler");
		}
	}

	/** What the main menu shows: not set up, loading, an error, or the list. */
	public record FriendsState(Status status, List<Friend> friends, int requests) {
		public enum Status { NOT_CONFIGURED, LOADING, UNAVAILABLE, READY }
	}

	private static volatile FriendsState friendsState = new FriendsState(FriendsState.Status.LOADING, List.of(), 0);
	private static final AtomicBoolean friendsBusy = new AtomicBoolean();
	private static volatile long friendsFetched;
	private static volatile long loginBlockedUntil;

	public static FriendsState friends() {
		if (FriendsConfig.serverUrl().isEmpty()) return new FriendsState(FriendsState.Status.NOT_CONFIGURED, List.of(), 0);
		return friendsState;
	}

	/** Loads the friends list in the background, at most every {@code maxAgeMs}. */
	public static void refreshFriends(long maxAgeMs) {
		String server = FriendsConfig.serverUrl();
		if (server.isEmpty() || System.currentTimeMillis() - friendsFetched < maxAgeMs) return;
		if (!friendsBusy.compareAndSet(false, true)) return;
		User user = Minecraft.getInstance().getUser();
		IO.execute(() -> {
			try {
				FriendsSession s = session(server, user);
				if (s == null) {
					friendsState = new FriendsState(FriendsState.Status.UNAVAILABLE, List.of(), 0);
					return;
				}
				JsonObject data = send(s, HttpRequest.newBuilder(URI.create(s.server() + "/v1/friends")).GET());
				List<Friend> list = new ArrayList<>();
				for (JsonElement el : data.getAsJsonArray("friends")) {
					JsonObject f = el.getAsJsonObject();
					JsonElement srv = f.get("server");
					list.add(new Friend(f.get("uuid").getAsString(), f.get("name").getAsString(), f.get("online").getAsBoolean(),
						srv == null || srv.isJsonNull() ? null : srv.getAsString()));
				}
				list.sort((a, b) -> a.online() != b.online() ? (a.online() ? -1 : 1) : a.name().compareToIgnoreCase(b.name()));
				friendsState = new FriendsState(FriendsState.Status.READY, List.copyOf(list), data.getAsJsonArray("incoming").size());
				Minecraft.getInstance().execute(() -> Lagerfeuer.onFriends(data));
			} catch (IOException | RuntimeException e) {
				LOG.debug("Emberleaf Lagerfeuer: Freundesliste nicht geladen: {}", e.toString());
				friendsState = new FriendsState(FriendsState.Status.UNAVAILABLE, List.of(), 0);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				friendsFetched = System.currentTimeMillis();
				friendsBusy.set(false);
			}
		});
	}

	// ---------- Aufrufe für Chat, Party und Freunde (aus Bildschirmen und Befehlen) ----------

	/** A failed Lagerfeuer call, with the server's own (German) message. */
	public static final class LagerfeuerError extends IOException {
		public final int status;

		LagerfeuerError(int status, String message) {
			super(message);
			this.status = status;
		}
	}

	/**
	 * Calls the Lagerfeuer in the background. The result arrives on the game thread when you use
	 * {@code thenAcceptAsync(..., Minecraft.getInstance())}.
	 */
	public static CompletableFuture<JsonObject> call(String method, String path, @Nullable JsonElement body) {
		String server = FriendsConfig.serverUrl();
		User user = Minecraft.getInstance().getUser();
		return CompletableFuture.supplyAsync(() -> {
			try {
				if (server.isEmpty()) throw new LagerfeuerError(0, "Trag im Emberleaf-Launcher unter Freunde die Adresse deines Lagerfeuers ein.");
				FriendsSession s = session(server, user);
				if (s == null) throw new LagerfeuerError(401, "Die Minecraft-Anmeldung fürs Lagerfeuer hat nicht geklappt.");
				HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(s.server() + path));
				if (body != null) {
					b.header("content-type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
				} else {
					b.method(method, HttpRequest.BodyPublishers.noBody());
				}
				return send(s, b);
			} catch (IOException e) {
				throw new CompletionException(e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CompletionException(e);
			}
		}, CALLS);
	}

	/** The message to show for a failed {@link #call}. */
	public static String errorText(Throwable t) {
		Throwable cause = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
		if (cause instanceof LagerfeuerError e) return e.getMessage();
		return "Das Lagerfeuer ist gerade nicht erreichbar.";
	}

	/** The address of your Lagerfeuer (for links to pictures), or "". */
	public static String serverUrl() {
		return FriendsConfig.serverUrl();
	}

	/** Forget the cached address and login (the data folder changed, e.g. in tests). */
	public static void reloadConfig() {
		FriendsConfig.invalidate();
		session = null;
		friendsFetched = 0;
		lastSync = 0;
	}

	private static JsonObject post(FriendsSession session, String path, String json) throws IOException, InterruptedException {
		return send(session, HttpRequest.newBuilder(URI.create(session.server() + path))
			.header("content-type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
	}

	private static JsonObject send(FriendsSession current, HttpRequest.Builder builder) throws IOException, InterruptedException {
		HttpResponse<String> response = HTTP.send(builder.timeout(Duration.ofSeconds(15))
			.header("authorization", "Bearer " + current.token()).build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 401) {
			session = null;
			FriendsConfig.clearSession();
		}
		JsonObject data;
		try {
			data = JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (RuntimeException e) {
			data = new JsonObject();
		}
		if (response.statusCode() >= 300) {
			String message = data.has("error") ? data.get("error").getAsString() : "Das Lagerfeuer hat einen Fehler gemeldet (" + response.statusCode() + ").";
			throw new LagerfeuerError(response.statusCode(), message);
		}
		return data;
	}
}
