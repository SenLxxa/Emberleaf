package net.emberleaf.client.tools;

import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Every enchantment: what it does, on which items, the highest level and what it does not go with. */
public class EnchantTool extends ListTool {
	private record Enchant(String id, int max, String items, String item, String excludes) {}

	private static final Enchant[] ALL = {
		new Enchant("protection", 4, "Rüstung", "diamond_chestplate", "Feuer-, Explosions-, Schusssicher"),
		new Enchant("fire_protection", 4, "Rüstung", "diamond_chestplate", "Schutz, Explosions-, Schusssicher"),
		new Enchant("blast_protection", 4, "Rüstung", "diamond_chestplate", "Schutz, Feuer-, Schusssicher"),
		new Enchant("projectile_protection", 4, "Rüstung", "diamond_chestplate", "Schutz, Feuer-, Explosionsschutz"),
		new Enchant("feather_falling", 4, "Stiefel", "diamond_boots", ""),
		new Enchant("respiration", 3, "Helm", "diamond_helmet", ""),
		new Enchant("aqua_affinity", 1, "Helm", "diamond_helmet", ""),
		new Enchant("thorns", 3, "Rüstung", "diamond_chestplate", ""),
		new Enchant("depth_strider", 3, "Stiefel", "diamond_boots", "Eisläufer"),
		new Enchant("frost_walker", 2, "Stiefel", "diamond_boots", "Wasserläufer"),
		new Enchant("soul_speed", 3, "Stiefel", "diamond_boots", ""),
		new Enchant("swift_sneak", 3, "Hose", "diamond_leggings", ""),
		new Enchant("sharpness", 5, "Schwert, Axt", "diamond_sword", "Bann, Nemesis der Gliederfüßer, Dichte, Durchbruch"),
		new Enchant("smite", 5, "Schwert, Axt, Streitkolben", "diamond_sword", "Schärfe, Nemesis der Gliederfüßer"),
		new Enchant("bane_of_arthropods", 5, "Schwert, Axt, Streitkolben", "diamond_sword", "Schärfe, Bann"),
		new Enchant("knockback", 2, "Schwert", "diamond_sword", ""),
		new Enchant("fire_aspect", 2, "Schwert, Streitkolben", "diamond_sword", ""),
		new Enchant("looting", 3, "Schwert", "diamond_sword", ""),
		new Enchant("sweeping_edge", 3, "Schwert", "diamond_sword", ""),
		new Enchant("efficiency", 5, "Werkzeuge", "diamond_pickaxe", ""),
		new Enchant("silk_touch", 1, "Werkzeuge", "diamond_pickaxe", "Glück"),
		new Enchant("fortune", 3, "Werkzeuge", "diamond_pickaxe", "Behutsamkeit"),
		new Enchant("unbreaking", 3, "Alles mit Haltbarkeit", "diamond_pickaxe", ""),
		new Enchant("mending", 1, "Alles mit Haltbarkeit", "diamond_pickaxe", "Unendlichkeit"),
		new Enchant("power", 5, "Bogen", "bow", ""),
		new Enchant("punch", 2, "Bogen", "bow", ""),
		new Enchant("flame", 1, "Bogen", "bow", ""),
		new Enchant("infinity", 1, "Bogen", "bow", "Reparatur"),
		new Enchant("multishot", 1, "Armbrust", "crossbow", "Durchschuss"),
		new Enchant("quick_charge", 3, "Armbrust", "crossbow", ""),
		new Enchant("piercing", 4, "Armbrust", "crossbow", "Mehrfachschuss"),
		new Enchant("loyalty", 3, "Dreizack", "trident", "Sog"),
		new Enchant("impaling", 5, "Dreizack", "trident", ""),
		new Enchant("riptide", 3, "Dreizack", "trident", "Treue, Entladung"),
		new Enchant("channeling", 1, "Dreizack", "trident", "Sog"),
		new Enchant("luck_of_the_sea", 3, "Angel", "fishing_rod", ""),
		new Enchant("lure", 3, "Angel", "fishing_rod", ""),
		new Enchant("density", 5, "Streitkolben", "mace", "Durchbruch, Bann, Nemesis der Gliederfüßer"),
		new Enchant("breach", 4, "Streitkolben", "mace", "Dichte, Schärfe"),
		new Enchant("wind_burst", 3, "Streitkolben", "mace", ""),
		new Enchant("lunge", 3, "Speer", "diamond_spear", ""),
		new Enchant("binding_curse", 1, "Rüstung (Fluch)", "diamond_chestplate", ""),
		new Enchant("vanishing_curse", 1, "Alles (Fluch)", "diamond_sword", "")};
	private static int level = -1;
	private static boolean book;

	public EnchantTool(Screen parent) {
		super("Verzauberungen", "Alle Verzauberungen auf einen Blick", parent);
	}

	@Override
	protected int size() {
		return ALL.length;
	}

	@Override
	protected Component name(int i) {
		return Component.translatable("enchantment.minecraft." + ALL[i].id());
	}

	@Override
	protected void build() {
		Enchant e = ALL[selected];
		if (level < 1 || level > e.max()) level = e.max();
		int x = detailX();
		int w = (px + pw - 12 - x - 4) / 2;
		String[] levels = new String[e.max()];
		for (int i = 0; i < levels.length; i++) levels[i] = "Stufe " + (i + 1);
		cycle(x, buttonsY(), Math.min(110, w), levels, level - 1, v -> level = v + 1);
		cycle(x + Math.min(110, w) + 4, buttonsY(), Math.min(150, w), new String[] {"Auf Gegenstand", "Als Buch"}, book ? 1 : 0, v -> book = v == 1);
	}

	/** The "not together with" line can wrap; the buttons go right below it. */
	private int buttonsY() {
		return top + 38 + 12 * Math.min(3, excludesLines().size()) + 16;
	}

	private java.util.List<net.minecraft.util.FormattedCharSequence> excludesLines() {
		Enchant e = ALL[selected];
		return this.font.split(Ui.menu("Nicht zusammen mit: " + (e.excludes().isEmpty() ? "–" : e.excludes())), px + pw - 12 - detailX());
	}

	@Override
	protected void selectionChanged() {
		level = -1;
		super.selectionChanged();
	}

	@Override
	protected String compute() {
		Enchant e = ALL[selected];
		String ench = "{\"minecraft:" + e.id() + "\":" + Math.max(1, level) + "}";
		return book ? "/give @s minecraft:enchanted_book[stored_enchantments=" + ench + "]"
			: "/give @s minecraft:" + e.item() + "[enchantments=" + ench + "]";
	}

	@Override
	protected void drawDetails(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		Enchant e = ALL[selected];
		Ui.text(g, Ui.menu(name(selected)), x, y, 0xFFFFE6C4);
		Ui.text(g, Ui.menu("Höchste Stufe: " + e.max()), x, y + 14, 0xFFB8A48E);
		Ui.text(g, Ui.menu("Für: " + e.items()), x, y + 26, 0xFFFFD6A8);
		var lines = excludesLines();
		for (int i = 0; i < Math.min(3, lines.size()); i++) g.text(this.font, lines.get(i), x, y + 38 + i * 12, 0xFFB8A48E, false);
		Ui.text(g, Ui.menu("Give-Befehl"), x, buttonsY() - 11, 0xFFB8A48E);
	}
}
