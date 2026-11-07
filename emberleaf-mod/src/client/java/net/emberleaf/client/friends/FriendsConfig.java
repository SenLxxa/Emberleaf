package net.emberleaf.client.friends;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.emberleaf.client.outfit.OutfitStore;

/**
 * Settings shared with the launcher in the Emberleaf data folder:
 * friends.json ({"server": "https://…"}) and friends-session.json (login token for the friends server).
 */
final class FriendsConfig {
	private static String cachedServer = "";
	private static long cachedAt;

	private FriendsConfig() {}

	/** The friends server URL without trailing slash, or "" when none is set. Re-read once a minute. */
	static synchronized String serverUrl() {
		long now = System.currentTimeMillis();
		if (now - cachedAt > 60_000) {
			cachedAt = now;
			cachedServer = "";
			Path file = OutfitStore.dataDir().resolve("friends.json");
			try {
				if (Files.exists(file)) {
					JsonObject data = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
					String url = data.has("server") ? data.get("server").getAsString().trim() : "";
					if (url.startsWith("https://") || url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
						cachedServer = url.replaceAll("/+$", "");
					}
				}
			} catch (IOException | RuntimeException ignored) {
				// keine oder kaputte Einstellung: Freunde bleiben aus
			}
		}
		return cachedServer;
	}

	/** Read friends.json again on the next use (the data folder or the address changed). */
	static synchronized void invalidate() {
		cachedAt = 0;
	}

	static FriendsSession loadSession() {
		Path file = OutfitStore.dataDir().resolve("friends-session.json");
		try {
			if (!Files.exists(file)) return null;
			JsonObject data = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			return new FriendsSession(data.get("server").getAsString(), data.get("token").getAsString(),
				data.get("uuid").getAsString(), data.get("expires").getAsLong());
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	static void saveSession(String server, String token, String uuid, String name, long expires) {
		JsonObject data = new JsonObject();
		data.addProperty("server", server);
		data.addProperty("token", token);
		data.addProperty("uuid", uuid);
		data.addProperty("name", name);
		data.addProperty("expires", expires);
		try {
			Path file = OutfitStore.dataDir().resolve("friends-session.json");
			Files.createDirectories(file.getParent());
			Files.writeString(file, data.toString(), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// dann meldet sich der Launcher eben selbst an
		}
	}

	static void clearSession() {
		try {
			Files.deleteIfExists(OutfitStore.dataDir().resolve("friends-session.json"));
		} catch (IOException ignored) {
			// egal, die Sitzung ist ohnehin ungültig
		}
	}
}
