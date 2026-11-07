package net.emberleaf.client.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.emberleaf.client.outfit.OutfitStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The skins saved in the launcher (Emberleaf data folder: skins/library.json + one PNG each).
 * The in-game switcher shows them and uploads one to the Minecraft account, like the launcher does.
 */
public final class SkinLibrary {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	private static final String UPLOAD = "https://api.minecraftservices.com/minecraft/profile/skins";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	public record Entry(String id, String name, boolean slim, Path file, Identifier texture, int texHeight) {}

	private static final List<Entry> LOADED = new ArrayList<>();
	/** The skin the local player wears since switching in game (Mojang's copy refreshes on the next login). */
	private static volatile @Nullable PlayerSkin override;

	private SkinLibrary() {}

	public static Path folder() {
		return OutfitStore.dataDir().resolve("skins");
	}

	/** (Re)loads all saved skins as textures. Call on the render thread. */
	public static List<Entry> load() {
		release();
		Path index = folder().resolve("library.json");
		if (!Files.isRegularFile(index)) return LOADED;
		try {
			JsonElement root = JsonParser.parseString(Files.readString(index, StandardCharsets.UTF_8));
			JsonArray entries = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
			int n = 0;
			for (JsonElement e : entries) {
				if (!e.isJsonObject() || n >= 120) continue;
				JsonObject o = e.getAsJsonObject();
				String id = o.has("id") ? o.get("id").getAsString() : "";
				if (!id.matches("[a-f0-9]{6,32}")) continue;
				Path png = folder().resolve(id + ".png");
				if (!Files.isRegularFile(png)) continue;
				try {
					NativeImage image = NativeImage.read(Files.readAllBytes(png));
					if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
						image.close();
						continue;
					}
					int h = image.getHeight();
					Identifier tex = Identifier.fromNamespaceAndPath("emberleaf", "skins/" + id);
					Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "Emberleaf skin " + id, image));
					String name = o.has("name") ? o.get("name").getAsString() : "Skin";
					boolean slim = o.has("model") && "slim".equals(o.get("model").getAsString());
					LOADED.add(new Entry(id, name, slim, png, tex, h));
					n++;
				} catch (IOException ex) {
					LOG.warn("Emberleaf: Skin {} ist kaputt", id, ex);
				}
			}
		} catch (Exception e) {
			LOG.warn("Emberleaf: Skin-Sammlung konnte nicht gelesen werden", e);
		}
		return LOADED;
	}

	public static void release() {
		PlayerSkin current = override;
		for (Entry e : LOADED) {
			// keep the texture the player is wearing right now
			if (current == null || !current.body().texturePath().equals(e.texture())) {
				Minecraft.getInstance().getTextureManager().release(e.texture());
			}
		}
		LOADED.clear();
	}

	public static @Nullable PlayerSkin override() {
		return override;
	}

	/** Applies the override to a skin the game looked up (keeps cape and elytra). */
	public static PlayerSkin apply(PlayerSkin original) {
		PlayerSkin o = override;
		if (o == null) return original;
		return new PlayerSkin(o.body(), original.cape(), original.elytra(), o.model(), original.secure());
	}

	public static void clearOverride() {
		override = null;
	}

	public static void wearLocally(Entry e) {
		override = new PlayerSkin(new ClientAsset.ResourceTexture(e.texture(), e.texture()), null, null,
			e.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE, false);
	}

	/** Uploads the skin to the Minecraft account. Completes with null on success, or a German error message. */
	public static CompletableFuture<@Nullable String> upload(Entry e) {
		String token = Minecraft.getInstance().getUser().getAccessToken();
		if (token == null || token.isBlank() || token.length() < 20) {
			return CompletableFuture.completedFuture("Zum Skin-Wechseln musst du mit Microsoft angemeldet sein.");
		}
		byte[] png;
		try {
			png = Files.readAllBytes(e.file());
		} catch (IOException ex) {
			return CompletableFuture.completedFuture("Die Skin-Datei fehlt.");
		}
		String boundary = "emberleaf" + UUID.randomUUID().toString().replace("-", "");
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		try {
			body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n"
				+ (e.slim() ? "slim" : "classic") + "\r\n").getBytes(StandardCharsets.UTF_8));
			body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
				+ "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
			body.write(png);
			body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		} catch (IOException ex) {
			return CompletableFuture.completedFuture("Upload konnte nicht vorbereitet werden.");
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(UPLOAD))
			.timeout(Duration.ofSeconds(30))
			.header("Authorization", "Bearer " + token)
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
			.build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).handle((res, err) -> {
			if (err != null) return "Mojang ist gerade nicht erreichbar.";
			if (res.statusCode() == 401) return "Deine Anmeldung ist abgelaufen. Starte über Emberleaf neu.";
			if (res.statusCode() >= 300) return "Mojang hat den Skin abgelehnt (" + res.statusCode() + ").";
			return null;
		});
	}
}
