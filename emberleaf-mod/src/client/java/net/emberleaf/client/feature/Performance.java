package net.emberleaf.client.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.outfit.OutfitStore;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Leistungsmodus" (FPS boost): one switch that turns the expensive vanilla settings down (view distance, clouds,
 * particles, shadows, biome blending) and Emberleaf's own effects to their light versions. Switching it off brings
 * your old settings back exactly. The launcher also installs speed mods (Sodium, Lithium, FerriteCore …).
 */
public final class Performance {
	private static final Logger LOG = LoggerFactory.getLogger("emberleaf");
	private static Boolean applied;

	private Performance() {}

	private static Path backup() {
		return OutfitStore.dataDir().resolve("performance-backup.json");
	}

	public static void tick(Minecraft mc) {
		boolean on = Modules.on("fps_boost");
		if (applied == null) applied = Files.exists(backup());
		if (on == applied) return;
		try {
			if (on) apply(mc.options);
			else restore(mc.options);
			applied = on;
			mc.options.save();
		} catch (Exception e) {
			LOG.warn("Emberleaf: Leistungsmodus konnte nicht umgeschaltet werden", e);
			applied = on;
		}
	}

	private static void apply(Options o) throws Exception {
		JsonObject old = new JsonObject();
		old.addProperty("renderDistance", o.renderDistance().get());
		old.addProperty("simulationDistance", o.simulationDistance().get());
		old.addProperty("clouds", o.cloudStatus().get().name());
		old.addProperty("particles", o.particles().get().name());
		old.addProperty("entityShadows", o.entityShadows().get());
		old.addProperty("biomeBlend", o.biomeBlendRadius().get());
		old.addProperty("entityDistance", o.entityDistanceScaling().get());
		old.addProperty("hitParticles", Modules.value("hit_particles"));
		old.addProperty("menuQuality", Modules.choice("menu_quality"));
		Files.createDirectories(backup().getParent());
		Files.writeString(backup(), old.toString(), StandardCharsets.UTF_8);
		o.renderDistance().set(Math.min(o.renderDistance().get(), 10));
		o.simulationDistance().set(Math.min(o.simulationDistance().get(), 8));
		o.cloudStatus().set(CloudStatus.FAST);
		o.particles().set(ParticleStatus.DECREASED);
		o.entityShadows().set(false);
		o.biomeBlendRadius().set(1);
		o.entityDistanceScaling().set(Math.min(o.entityDistanceScaling().get(), 0.75));
		Modules.setValue("hit_particles", 1);
		Modules.setChoice("menu_quality", "low");
	}

	private static void restore(Options o) throws Exception {
		if (!Files.exists(backup())) return;
		JsonObject old = JsonParser.parseString(Files.readString(backup(), StandardCharsets.UTF_8)).getAsJsonObject();
		o.renderDistance().set(old.get("renderDistance").getAsInt());
		o.simulationDistance().set(old.get("simulationDistance").getAsInt());
		o.cloudStatus().set(CloudStatus.valueOf(old.get("clouds").getAsString()));
		o.particles().set(ParticleStatus.valueOf(old.get("particles").getAsString()));
		o.entityShadows().set(old.get("entityShadows").getAsBoolean());
		o.biomeBlendRadius().set(old.get("biomeBlend").getAsInt());
		o.entityDistanceScaling().set(old.get("entityDistance").getAsDouble());
		Modules.setValue("hit_particles", old.get("hitParticles").getAsDouble());
		if (old.has("menuQuality") && !old.get("menuQuality").isJsonNull()) Modules.setChoice("menu_quality", old.get("menuQuality").getAsString());
		Files.deleteIfExists(backup());
	}
}
