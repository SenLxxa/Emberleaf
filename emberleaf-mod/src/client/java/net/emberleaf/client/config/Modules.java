package net.emberleaf.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.emberleaf.client.outfit.OutfitStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every Emberleaf feature that can be switched on and off (and tuned) in the Emberleaf menu.
 * Saved in the Emberleaf data folder as client-settings.json.
 */
public final class Modules {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public enum Category {
		GAME("Spiel"), COMBAT("Kampf & PvP"), COSMETICS("Cosmetics"), GRAPHICS("Grafik & Effekte"), HUD("HUD-Widgets"), CHAT("Chat"),
		PERFORMANCE("Leistung"), MENU("Oberfläche");

		public final String label;

		Category(String label) {
			this.label = label;
		}
	}

	/** A switchable feature. {@code value} is an optional slider (min..max, step), {@code choices} an optional cycle button. */
	public record Module(String id, String name, String description, Category category, String icon, boolean defaultOn, boolean isNew,
		Slider slider, List<String> choices, List<String> choiceLabels) {}

	public record Slider(String label, double min, double max, double step, double defaultValue, String unit) {}

	private static final Map<String, Module> ALL = new LinkedHashMap<>();

	/** Contrail colours: id, label, hours of playtime needed, colour (0 = rainbow). */
	public static final List<String> CONTRAIL_COLORS = List.of("white", "ember", "sky", "rose", "mint", "violet", "gold", "rainbow");
	public static final List<String> CONTRAIL_LABELS = List.of("Weiß", "Glut", "Himmel", "Rosa", "Minze", "Violett", "Gold", "Regenbogen");
	public static final int[] CONTRAIL_HOURS = {0, 1, 3, 5, 10, 25, 50, 100};
	public static final int[] CONTRAIL_RGB = {0xFFFFFF, 0xFF8A3A, 0x7CC8FF, 0xFF8AC8, 0x7CF0B0, 0xB088FF, 0xFFD24A, 0};

	/** Hours of playtime a choice needs (0 = always there). */
	public static final List<String> CROSSHAIR_COLORS = List.of("white", "red", "green", "yellow", "cyan", "pink", "orange");
	public static final List<String> CROSSHAIR_LABELS = List.of("Weiß", "Rot", "Grün", "Gelb", "Türkis", "Pink", "Glut");
	private static final int[] CROSSHAIR_RGB = {0xFFFFFFFF, 0xFFFF4A4A, 0xFF6FE35A, 0xFFFFE04A, 0xFF5AE8F0, 0xFFFF7AD8, 0xFFFF8A3A};

	/** The crosshair colour (ARGB), or 0 for Minecraft's own inverted crosshair. */
	public static int crosshairColor() {
		if (!on("crosshair_color")) return 0;
		int i = CROSSHAIR_COLORS.indexOf(choice("crosshair_color"));
		return CROSSHAIR_RGB[Math.max(0, i)];
	}

	public static int unlockHours(String moduleId, String choice) {
		if (!moduleId.equals("contrail_color")) return 0;
		int i = CONTRAIL_COLORS.indexOf(choice);
		return i < 0 ? 0 : CONTRAIL_HOURS[i];
	}

	private static Module add(String id, String name, String description, Category cat, String icon, boolean on, boolean isNew) {
		return add(new Module(id, name, description, cat, icon, on, isNew, null, List.of(), List.of()));
	}

	private static Module add(Module m) {
		ALL.put(m.id(), m);
		return m;
	}

	static {
		add("zoom", "Zoom", "Taste C halten, mit dem Mausrad näher oder weiter", Category.GAME, "item/spyglass", true, false);
		add("emotes", "Emotes", "Taste B spielt dein Emote aus dem Launcher ab", Category.GAME, "item/music_disc_cat", true, false);
		add("fullbright", "Fullbright", "Alles hell, auch in Höhlen und nachts", Category.GAME, "item/glowstone_dust", false, true);
		add("freelook", "Freelook", "Linke Alt-Taste halten: Kamera frei drehen, du läufst weiter", Category.GAME, "item/ender_eye", true, true);
		add("afk_camera", "AFK-Kamera", "Nach einer Minute ohne Eingabe kreist die Kamera um dich", Category.GAME, "item/recovery_compass_00", true, true);
		add("panorama", "Panorama-Screenshots", "F9 macht ein 360°-Panorama von deiner Umgebung", Category.GAME, "item/painting", true, true);
		add("confirm_quit", "Verlassen bestätigen", "Fragt nach, bevor du eine Welt oder einen Server verlässt", Category.GAME, "item/oak_door", true, true);
		add("toggle_sprint", "Sprinten umschalten", "Sprint-Taste einmal drücken statt gedrückt halten", Category.GAME, "item/rabbit_foot", false, true);
		add("toggle_sneak", "Schleichen umschalten", "Schleich-Taste einmal drücken statt gedrückt halten", Category.GAME, "item/leather_boots", false, true);
		add("durability_warning", "Haltbarkeits-Warnung", "Warnt mit Ton, bevor Werkzeug, Waffe oder Rüstung kaputtgeht", Category.GAME,
			"item/iron_pickaxe", true, true);
		add(new Module("hit_particles", "Treffer-Partikel", "Mehr Funken bei jedem Treffer", Category.COMBAT, "item/iron_sword", true, true,
			new Slider("Menge", 1, 10, 1, 1, "×"), List.of(), List.of()));
		add("always_crits", "Immer Krit-Funken", "Jeder Treffer zeigt Krit- und Schärfe-Partikel", Category.COMBAT, "item/diamond_sword", false, true);
		add("hide_offhand_totem", "Totem in der Zweithand verbergen", "Das Totem wirkt weiter, verdeckt aber nicht die Sicht", Category.COMBAT,
			"item/totem_of_undying", false, true);
		add("no_hurtcam", "Kein Kamerawackeln", "Bekommst du Schaden, kippt die Kamera nicht mehr", Category.COMBAT, "item/golden_apple", false, true);
		add("reach_display", "Reichweite & Combo", "Zeigt im HUD, aus welcher Entfernung du getroffen hast und deine Trefferserie",
			Category.COMBAT, "item/bow", false, true);
		add("cosmetics", "Deine Cosmetics", "Hüte, Flügel, Capes und Rücken an deiner Figur", Category.COSMETICS, "item/golden_helmet", true, false);
		add("cosmetics_others", "Cosmetics anderer", "Cosmetics deiner Freunde und anderer Emberleaf-Spieler", Category.COSMETICS, "item/totem_of_undying", true, false);
		add("pets", "Pets", "Dein Begleiter läuft neben dir", Category.COSMETICS, "item/bone", true, false);
		add("auras", "Auren", "Partikel, die um Spieler schweben", Category.COSMETICS, "item/blaze_powder", true, false);
		add("cape_fx", "Cape-Effekte", "Leuchtende Funken in Cape-Farbe, wenn Capes mit Effekten wehen", Category.COSMETICS, "item/glowstone_dust", true, false);
		add(new Module("contrails", "Kondensstreifen", "Streifen an den Flügelspitzen beim Elytra-Fliegen", Category.GRAPHICS, "item/elytra", true, true,
			new Slider("Länge", 1, 8, 1, 3, " s"), List.of(), List.of()));
		add(new Module("contrail_color", "Farbe der Kondensstreifen", "Neue Farben schaltest du mit deiner Spielzeit frei", Category.GRAPHICS,
			"item/firework_star", true, true, null, CONTRAIL_COLORS, CONTRAIL_LABELS));
		add("badges", "Abzeichen über Namen", "Emberleaf-Abzeichen über den Namen der Spieler", Category.GRAPHICS, "item/name_tag", true, true);
		add("own_name", "Eigenen Namen anzeigen", "Deinen Namen und deine Abzeichen in der Außenansicht sehen", Category.GRAPHICS,
			"item/writable_book", false, true);
		add("clear_water", "Klares Wasser", "Unter Wasser siehst du viel weiter", Category.GRAPHICS, "item/water_bucket", false, true);
		add("glowing_trims", "Leuchtende Besätze", "Rüstungsbesätze leuchten in der Nacht und in Höhlen", Category.GRAPHICS, "item/netherite_chestplate", true, true);
		add("low_fire", "Feuer niedriger", "Wenn du brennst, verdeckt das Feuer weniger vom Bild", Category.GRAPHICS, "item/flint_and_steel", true, true);
		add("no_pumpkin", "Kein Kürbis-Rand", "Mit geschnitztem Kürbis auf dem Kopf siehst du trotzdem alles", Category.GRAPHICS,
			"block/carved_pumpkin", true, true);
		add("no_weather", "Wetter ausblenden", "Kein Regen und Schnee vor deinen Augen (nur bei dir, das Wetter bleibt)", Category.GRAPHICS,
			"block/snow", false, true);
		add(new Module("crosshair_color", "Fadenkreuz-Farbe", "Gibt deinem Fadenkreuz eine eigene Farbe", Category.GRAPHICS, "item/red_dye",
			false, true, null, CROSSHAIR_COLORS, CROSSHAIR_LABELS));
		add(new Module("minimap", "Minimap", "Kleine Karte oben rechts, mit Freunden als Punkte", Category.HUD, "item/map", false, true,
			new Slider("Zoom", 1, 4, 1, 1, " Bl./Px"), List.of(), List.of()));
		add("minimap_mobs", "Mobs auf der Minimap", "Tiere und Monster als kleine Bilder auf der Minimap", Category.HUD, "item/rotten_flesh", true, true);
		add("hud_fps", "FPS", "Bilder pro Sekunde", Category.HUD, "item/redstone", true, false);
		add("hud_coords", "Koordinaten", "Wo du gerade bist", Category.HUD, "item/name_tag", true, false);
		add("hud_direction", "Himmelsrichtung", "In welche Richtung du schaust", Category.HUD, "item/compass_00", false, false);
		add("hud_biome", "Biom", "In welchem Biom du stehst", Category.HUD, "block/oak_sapling", false, false);
		add("hud_clock", "Uhrzeit", "Echte Uhrzeit und Tageszeit im Spiel", Category.HUD, "item/clock_00", false, false);
		add("hud_ping", "Ping", "Verbindung zum Server", Category.HUD, "item/ender_pearl", false, false);
		add("hud_speed", "Tempo", "Wie schnell du unterwegs bist", Category.HUD, "item/feather", false, true);
		add("hud_armor", "Rüstung", "Rüstung und Haltbarkeit", Category.HUD, "item/iron_chestplate", true, false);
		add("hud_keys", "Tastendrücke", "WASD, Leertaste und Klicks mit CPS", Category.HUD, "block/tripwire_hook", false, true);
		add("hud_playtime", "Spielzeit", "Wie lange du heute und insgesamt gespielt hast", Category.HUD, "item/clock_00", false, true);
		add("hide_bossbar", "Bossleiste ausblenden", "Keine Bossleisten oben im Bild", Category.HUD, "item/dragon_breath", false, true);
		add("hide_scoreboard", "Scoreboard ausblenden", "Die Punkteliste am rechten Rand verschwindet", Category.HUD, "item/paper", false, true);
		add("hud_effects", "Trank-Effekte", "Deine Effekte mit Stufe und Restzeit", Category.HUD, "item/potion", true, true);
		add("hud_items", "Item-Zähler", "Pfeile, Totems, Enderperlen, Goldäpfel und Raketen im Inventar", Category.HUD, "item/arrow", false, true);
		add("hud_food", "Hunger & Sättigung", "Hunger und die versteckte Sättigung als Zahl", Category.HUD, "item/cooked_beef", false, true);
		add("hud_day", "Minecraft-Tag", "Der wievielte Tag es in deiner Welt ist", Category.HUD, "block/sunflower_front", false, true);
		add("hud_server", "Server-Info", "Serveradresse und wie viele Spieler online sind", Category.HUD, "item/oak_sign", false, true);
		add("speech_bubbles", "Sprechblasen", "Was Spieler schreiben, schwebt kurz über ihrem Kopf", Category.CHAT, "item/paper", true, true);
		add("chat_heads", "Köpfe im Chat", "Vor jeder Nachricht der Kopf des Spielers", Category.CHAT, "item/book", true, true);
		add("chat_peek", "Chat-Vorschau", "Taste Z halten: den ganzen Chat lesen, ohne ihn zu öffnen", Category.CHAT, "item/spyglass", true, true);
		add("chat_symbols", "Chat-Symbole", "Sterne, Herzen, Pfeile … mit einem Klick in den Chat", Category.CHAT, "item/nether_star", true, true);
		add("autotext", "AutoText", "Bis zu 5 Tasten senden vorbereitete Nachrichten oder Befehle", Category.CHAT, "item/writable_book", true, true);
		add("chat_timestamps", "Uhrzeit im Chat", "Vor jeder Nachricht steht, wann sie kam", Category.CHAT, "item/clock_00", false, true);
		add("chat_stack", "Spam zusammenfassen", "Gleiche Nachrichten hintereinander werden eine Zeile mit Zähler (×3)", Category.CHAT,
			"item/slime_ball", true, true);
		add("tab_ping", "Ping als Zahl", "In der Spielerliste (Tab) steht der Ping in Millisekunden statt Balken", Category.CHAT,
			"item/compass_00", true, true);
		add("lagerfeuer_notify", "Lagerfeuer-Nachrichten", "Nachrichten deiner Freunde oben rechts und im Chat (Taste U: Lagerfeuer-Chat)",
			Category.CHAT, "item/campfire", true, true);
		add("party_follow", "Party: Leiter folgen", "Wechselt der Party-Leiter den Server, kommst du automatisch mit", Category.CHAT,
			"item/lead", false, true);
		add("fps_boost", "Leistungsmodus", "Mehr FPS: Sicht, Wolken, Partikel und Schatten sparsamer (alles zurückholbar)",
			Category.PERFORMANCE, "item/sugar", false, true);
		add("menu", "Emberleaf-Hauptmenü", "Die schwebende Insel statt des Vanilla-Menüs", Category.MENU, "item/painting", true, false);
		add(new Module("menu_scene", "Szene im Hauptmenü", "Welche live gerenderte Welt hinter dem Menü liegt", Category.MENU, "item/ender_eye", true, true, null,
			List.of("auto", "cave", "nether", "overworld", "end", "island"),
			List.of("Abwechselnd", "Höhle", "Nether", "Oberwelt", "End", "Schwebende Insel")));
		add(new Module("menu_quality", "Qualität des Hauptmenüs", "Mehr Leuchten, Lichtstrahlen und Partikel", Category.MENU, "item/glowstone_dust", true, true, null,
			List.of("high", "ultra", "low", "medium"), List.of("Hoch", "Ultra", "Niedrig", "Mittel")));
		add("menu_flight", "Kameraflug", "Kurzer Flug in die Szene, wenn das Menü aufgeht", Category.MENU, "item/feather", true, true);
		add("menu_blur", "Hintergrund unscharf", "Die Szene hinter den Knöpfen weichzeichnen", Category.MENU, "item/ghast_tear", false, true);	}

	private static final class Saved {
		Map<String, Boolean> enabled = new HashMap<>();
		Map<String, Double> values = new HashMap<>();
		Map<String, String> choices = new HashMap<>();
	}

	private static Saved saved;

	private Modules() {}

	public static List<Module> all() {
		return List.copyOf(ALL.values());
	}

	private static Path file() {
		return OutfitStore.dataDir().resolve("client-settings.json");
	}

	private static Saved saved() {
		if (saved == null) {
			saved = new Saved();
			try {
				if (Files.exists(file())) {
					Saved read = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Saved.class);
					if (read != null) {
						if (read.enabled != null) saved.enabled.putAll(read.enabled);
						if (read.values != null) saved.values.putAll(read.values);
						if (read.choices != null) saved.choices.putAll(read.choices);
					}
				}
			} catch (IOException | RuntimeException e) {
				LOG.warn("Emberleaf: client-settings.json konnte nicht gelesen werden", e);
			}
		}
		return saved;
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(saved()), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.warn("Emberleaf: client-settings.json konnte nicht gespeichert werden", e);
		}
	}

	public static boolean on(String id) {
		Module m = ALL.get(id);
		return saved().enabled.getOrDefault(id, m == null || m.defaultOn());
	}

	public static void set(String id, boolean on) {
		saved().enabled.put(id, on);
		save();
	}

	public static double value(String id) {
		Module m = ALL.get(id);
		double def = m != null && m.slider() != null ? m.slider().defaultValue() : 1;
		return saved().values.getOrDefault(id, def);
	}

	public static void setValue(String id, double value) {
		saved().values.put(id, value);
		save();
	}

	/** The chosen option of a cycle module, or null when the player never changed it. */
	public static String choice(String id) {
		return saved().choices.get(id);
	}

	public static void setChoice(String id, String value) {
		saved().choices.put(id, value);
		save();
		// friends should see a new contrail colour right away, not only with the next stats update
		if (id.equals("contrail_color")) net.emberleaf.client.friends.FriendsSync.statsChanged();
	}
}
