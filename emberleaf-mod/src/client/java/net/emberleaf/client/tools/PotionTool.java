package net.emberleaf.client.tools;

import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** Brewing guide: every potion with its ingredient chain, what it does, and the give command. */
public class PotionTool extends ListTool {
	/** id, recipe, effect, colour, can be longer, can be stronger. */
	private record Potion(String id, String recipe, String effect, int color, boolean longer, boolean stronger) {}

	private static final String AWKWARD = "Wasserflasche + Netherwarze → Seltsamer Trank";
	private static final Potion[] POTIONS = {
		new Potion("swiftness", AWKWARD + " + Zucker", "Schneller laufen", 0x33EBFF, true, true),
		new Potion("slowness", "Trank der Schnelligkeit + Fermentiertes Spinnenauge", "Langsamer laufen", 0x8BAFE0, true, true),
		new Potion("leaping", AWKWARD + " + Kaninchenpfote", "Höher springen", 0xFDFF84, true, true),
		new Potion("strength", AWKWARD + " + Lohenstaub", "Mehr Nahkampfschaden", 0xFFC700, true, true),
		new Potion("healing", AWKWARD + " + Glitzernde Melonenscheibe", "Sofort Herzen auffüllen", 0xF82423, false, true),
		new Potion("harming", "Trank der Heilung + Fermentiertes Spinnenauge", "Sofort Schaden", 0xA9656A, false, true),
		new Potion("poison", AWKWARD + " + Spinnenauge", "Vergiftung (nicht unter ein Herz)", 0x87A363, true, true),
		new Potion("regeneration", AWKWARD + " + Ghast-Träne", "Herzen kommen nach und nach zurück", 0xCD5CAB, true, true),
		new Potion("fire_resistance", AWKWARD + " + Magmacreme", "Kein Schaden durch Feuer und Lava", 0xFF9900, true, false),
		new Potion("water_breathing", AWKWARD + " + Kugelfisch", "Unter Wasser atmen", 0x98DAC0, true, false),
		new Potion("night_vision", AWKWARD + " + Goldene Karotte", "Im Dunkeln sehen", 0xC2FF66, true, false),
		new Potion("invisibility", "Trank der Nachtsicht + Fermentiertes Spinnenauge", "Unsichtbar (Rüstung bleibt sichtbar)", 0xF6F6F6, true, false),
		new Potion("weakness", "Wasserflasche + Fermentiertes Spinnenauge", "Weniger Nahkampfschaden", 0x484D48, true, false),
		new Potion("slow_falling", AWKWARD + " + Phantomhaut", "Langsam fallen, kein Fallschaden", 0xF3CFB9, true, false),
		new Potion("turtle_master", AWKWARD + " + Schildkrötenpanzer", "Langsam, aber sehr widerstandsfähig", 0x8BAFE0, true, true),
		new Potion("wind_charged", AWKWARD + " + Böenrute", "Beim Tod eine Windexplosion", 0xBDC9FF, false, false),
		new Potion("weaving", AWKWARD + " + Spinnennetz", "Beim Tod entstehen Spinnennetze", 0x78695A, false, false),
		new Potion("oozing", AWKWARD + " + Schleimblock", "Beim Tod entstehen Schleime", 0x99FFA3, false, false),
		new Potion("infested", AWKWARD + " + Stein", "Silberfische beim Treffer", 0x8C9B8C, false, false)};
	private static final String[] KINDS = {"potion", "splash_potion", "lingering_potion"};
	private static final String[] KIND_NAMES = {"Trank", "Wurftrank (+ Schwarzpulver)", "Verweiltrank (+ Drachenatem)"};
	private static int kind;
	private static int variant;

	public PotionTool(Screen parent) {
		super("Trank-Guide", "Braurezepte, Effekte und Give-Befehle", parent);
	}

	@Override
	protected int size() {
		return POTIONS.length;
	}

	@Override
	protected Component name(int i) {
		return Component.translatable("item.minecraft.potion.effect." + POTIONS[i].id());
	}

	@Override
	protected void build() {
		Potion p = POTIONS[selected];
		int x = detailX();
		int w = px + pw - 12 - x;
		cycle(x, top + 74, Math.min(w, 220), KIND_NAMES, kind, v -> kind = v);
		String[] variants = {"Normal", p.longer() ? "Verlängert (+ Redstone)" : "Verlängert: gibt es nicht",
			p.stronger() ? "Verstärkt (+ Glowstone)" : "Verstärkt: gibt es nicht"};
		if (variant == 1 && !p.longer() || variant == 2 && !p.stronger()) variant = 0;
		cycle(x, top + 96, Math.min(w, 220), variants, variant, v -> variant = v);
	}

	@Override
	protected String compute() {
		Potion p = POTIONS[selected];
		String prefix = variant == 1 && p.longer() ? "long_" : variant == 2 && p.stronger() ? "strong_" : "";
		return "/give @s minecraft:" + KINDS[kind] + "[potion_contents={potion:\"minecraft:" + prefix + p.id() + "\"}]";
	}

	@Override
	protected void drawDetails(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		Potion p = POTIONS[selected];
		g.blit(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("textures/item/potion_overlay.png"), x, y, 0, 0, 32, 32, 16, 16, 16, 16,
			ARGB.opaque(p.color()));
		g.blit(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("textures/item/potion.png"), x, y, 0, 0, 32, 32, 16, 16, 16, 16, -1);
		Ui.text(g, Ui.menu(name(selected)), x + 38, y + 4, 0xFFFFE6C4);
		Ui.text(g, Ui.menu(p.effect()), x + 38, y + 16, 0xFFB8A48E);
		int w = px + pw - 12 - x;
		var lines = this.font.split(Ui.menu("Brauen: " + p.recipe()), w);
		for (int i = 0; i < lines.size(); i++) g.text(this.font, lines.get(i), x, y + 40 + i * 10, 0xFFFFD6A8, false);
		Ui.text(g, Ui.menu("Art und Stärke"), x, y + 64, 0xFFB8A48E);
	}
}
