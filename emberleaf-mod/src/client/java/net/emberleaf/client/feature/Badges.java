package net.emberleaf.client.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.emberleaf.client.friends.FriendsSync;
import net.emberleaf.client.outfit.OutfitStore;
import net.emberleaf.client.outfit.Outfits;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Player badges (like profile badges on laby.net, but Emberleaf's own): shown above the name of every Emberleaf
 * player. Yours are worked out here; other players' come from the Lagerfeuer.
 * The pictures are glyphs of the font emberleaf:badges (tools/make_badges.py).
 */
public final class Badges {
	public static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("emberleaf", "badges"));

	public record Badge(String id, String name, String description, char glyph) {}

	/** Best first: that is also the order above the name. */
	public static final List<Badge> ALL = List.of(
		new Badge("founder", "Gründer", "Hat das Emberleaf-Lagerfeuer entzündet", ''),
		new Badge("alpha", "Alpha-Tester", "Spielt Emberleaf schon in der Alpha", ''),
		new Badge("h500", "500 Stunden", "500 Stunden Spielzeit mit Emberleaf", ''),
		new Badge("h100", "100 Stunden", "100 Stunden Spielzeit mit Emberleaf", ''),
		new Badge("h50", "50 Stunden", "50 Stunden Spielzeit mit Emberleaf", ''),
		new Badge("h10", "10 Stunden", "10 Stunden Spielzeit mit Emberleaf", ''),
		new Badge("friend", "Lagerfeuer-Freund", "Hat Freunde am Lagerfeuer", ''),
		new Badge("creator", "Cosmetic-Designer", "Hat ein eigenes Cosmetic gebaut", ''),
		new Badge("explorer", "Weltenbummler", "War in der Oberwelt, im Nether und im End", ''),
		new Badge("nightowl", "Nachteule", "Hat nach Mitternacht gespielt", ''));
	private static final int SHOWN = 5;

	private static final Map<UUID, List<String>> REMOTE = new ConcurrentHashMap<>();
	private static List<String> mine = List.of();
	private static long mineAt;

	private Badges() {}

	public static @Nullable Badge byId(String id) {
		for (Badge b : ALL) if (b.id().equals(id)) return b;
		return null;
	}

	/** The badges of a player: yours worked out locally, others' as the Lagerfeuer reported them. */
	public static List<String> of(@Nullable UUID uuid) {
		if (uuid == null) return List.of();
		if (Outfits.isLocal(uuid)) return mine();
		return REMOTE.getOrDefault(uuid, List.of());
	}

	public static void putRemote(UUID uuid, List<String> ids) {
		if (ids.isEmpty()) REMOTE.remove(uuid);
		else REMOTE.put(uuid, List.copyOf(ids));
	}

	/** Your badges (refreshed every few seconds). */
	public static List<String> mine() {
		long now = System.currentTimeMillis();
		if (now - mineAt < 5000) return mine;
		mineAt = now;
		List<String> ids = new ArrayList<>(Playtime.earned());
		if (isOwner()) ids.add("founder");
		double h = Playtime.hours();
		if (h >= 500) ids.add("h500");
		else if (h >= 100) ids.add("h100");
		else if (h >= 50) ids.add("h50");
		else if (h >= 10) ids.add("h10");
		var friends = FriendsSync.friends();
		if (friends.status() == FriendsSync.FriendsState.Status.READY && !friends.friends().isEmpty()) ids.add("friend");
		if (hasOwnCosmetic()) ids.add("creator");
		if (Playtime.visitedAllDimensions()) ids.add("explorer");
		if (Playtime.nightOwl()) ids.add("nightowl");
		mine = sorted(ids);
		return mine;
	}

	private static List<String> sorted(List<String> ids) {
		List<String> out = new ArrayList<>();
		for (Badge b : ALL) if (ids.contains(b.id()) && !out.contains(b.id())) out.add(b.id());
		return List.copyOf(out);
	}

	/** The badge pictures in one line (at most five), or null when there is nothing to show. */
	public static @Nullable Component line(List<String> ids) {
		StringBuilder glyphs = new StringBuilder();
		int n = 0;
		for (String id : sorted(ids)) {
			Badge b = byId(id);
			if (b == null) continue;
			if (n > 0) glyphs.append(' ');
			glyphs.append(b.glyph());
			if (++n >= SHOWN) break;
		}
		if (n == 0) return null;
		MutableComponent text = Component.literal(glyphs.toString());
		return text.withStyle(s -> s.withFont(FONT));
	}

	private static boolean isOwner() {
		JsonObject state = read(OutfitStore.dataDir().resolve("updates").resolve("state.json"));
		return state != null && state.has("owner") && state.get("owner").getAsBoolean();
	}

	private static boolean hasOwnCosmetic() {
		JsonObject cos = read(OutfitStore.dataDir().resolve("cosmetics.json"));
		if (cos == null || !cos.has("custom")) return false;
		JsonElement custom = cos.get("custom");
		return custom.isJsonArray() && !((JsonArray) custom).isEmpty();
	}

	private static @Nullable JsonObject read(Path file) {
		try {
			if (!Files.exists(file)) return null;
			JsonElement el = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			return el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (Exception e) {
			return null;
		}
	}
}
