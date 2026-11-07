package net.emberleaf.client.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.emberleaf.client.outfit.OutfitStore;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Counts how long you play (time in a world, not paused) and remembers a few milestones for badges.
 * Saved as playtime.json in the Emberleaf data folder, where the launcher and the Lagerfeuer read it too.
 */
public final class Playtime {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int KEEP_DAYS = 120;

	/** What is stored on disk. */
	private static final class Data {
		long total;
		long sessions;
		String since;
		Map<String, Long> days = new TreeMap<>();
		Set<String> dimensions = new LinkedHashSet<>();
		Set<String> earned = new LinkedHashSet<>();
		boolean nightOwl;
	}

	private static Data data;
	private static long lastMs;
	private static long carryMs;
	private static long sessionSeconds;
	private static boolean inWorld;
	private static int sinceSave;

	private Playtime() {}

	private static Path file() {
		return OutfitStore.dataDir().resolve("playtime.json");
	}

	private static Path loadedFrom;

	private static Data data() {
		if (data != null && !file().equals(loadedFrom)) data = null; // the data folder changed (tests, launcher switch)
		if (data == null) {
			loadedFrom = file();
			try {
				if (Files.exists(file())) data = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Data.class);
			} catch (IOException | RuntimeException e) {
				LOG.warn("Emberleaf: playtime.json konnte nicht gelesen werden", e);
			}
			if (data == null) data = new Data();
			if (data.days == null) data.days = new TreeMap<>();
			if (data.dimensions == null) data.dimensions = new LinkedHashSet<>();
			if (data.earned == null) data.earned = new LinkedHashSet<>();
			if (data.since == null) data.since = LocalDate.now().toString();
			// everyone who plays the alpha keeps the alpha badge for good
			data.earned.add("alpha");
		}
		return data;
	}

	public static void tick(Minecraft mc) {
		Data d = data();
		long now = System.currentTimeMillis();
		boolean playing = mc.level != null && mc.player != null && !mc.isPaused();
		if (mc.level != null && !inWorld) {
			inWorld = true;
			d.sessions++;
			sessionSeconds = 0;
		}
		if (mc.level == null && inWorld) {
			inWorld = false;
			save();
		}
		if (playing && lastMs != 0) {
			long delta = now - lastMs;
			if (delta > 0 && delta < 5000) carryMs += delta;
		}
		lastMs = playing ? now : 0;
		if (carryMs >= 1000) {
			long seconds = carryMs / 1000;
			carryMs -= seconds * 1000;
			d.total += seconds;
			sessionSeconds += seconds;
			d.days.merge(LocalDate.now().toString(), seconds, Long::sum);
		}
		if (playing) {
			d.dimensions.add(mc.level.dimension().identifier().getPath());
			if (LocalTime.now().getHour() < 4) d.nightOwl = true;
		}
		// only save while there is something new (time in a world), every minute
		if (playing && ++sinceSave >= 20 * 60) save();
	}

	public static void save() {
		sinceSave = 0;
		if (data == null) return; // nothing loaded, nothing to write
		Data d = data();
		while (d.days.size() > KEEP_DAYS) d.days.remove(d.days.keySet().iterator().next());
		try {
			Files.createDirectories(file().getParent());
			Path tmp = file().resolveSibling("playtime.json.tmp");
			Files.writeString(tmp, GSON.toJson(d), StandardCharsets.UTF_8);
			Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOG.warn("Emberleaf: playtime.json konnte nicht gespeichert werden", e);
		}
	}

	/** Total seconds in worlds. */
	public static long total() {
		return data().total;
	}

	public static long today() {
		return data().days.getOrDefault(LocalDate.now().toString(), 0L);
	}

	public static long session() {
		return sessionSeconds;
	}

	public static double hours() {
		return total() / 3600.0;
	}

	public static boolean visitedAllDimensions() {
		Set<String> dims = data().dimensions;
		return dims.contains("overworld") && dims.contains("the_nether") && dims.contains("the_end");
	}

	public static boolean nightOwl() {
		return data().nightOwl;
	}

	/** Badges that were earned once and are kept (e.g. "alpha"). */
	public static List<String> earned() {
		return new ArrayList<>(data().earned);
	}

	/** "12 h 34 min", "34 min", "0 min". */
	public static String format(long seconds) {
		long h = seconds / 3600;
		long m = seconds % 3600 / 60;
		if (h == 0) return m + " min";
		return h + " h " + m + " min";
	}

	/** Hours as a short label for unlock hints: "1 h", "25 h". */
	public static String hoursLabel(int hours) {
		return hours + " h";
	}
}
