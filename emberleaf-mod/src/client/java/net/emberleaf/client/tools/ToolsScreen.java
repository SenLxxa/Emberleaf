package net.emberleaf.client.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.emberleaf.client.menu.EmberButton;
import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** All Emberleaf tools (generators, calculators, guides), from the title screen and in the game. */
public class ToolsScreen extends Screen {
	private record Tool(String section, String name, String description, String icon, @Nullable Function<Screen, Screen> open) {}

	private static final List<Tool> TOOLS = List.of(
		new Tool("Generatoren", "Farbcodes", "Farb- und Formatierungscodes mit Live-Vorschau", "item/red_dye", ColorCodesTool::new),
		new Tool("Generatoren", "Farbverlauf", "Weicher Farbverlauf über einen Text, fertige Codes", "item/magenta_dye", GradientTool::new),
		new Tool("Generatoren", "MOTD-Generator", "Server-MOTD mit Farben und Serverlisten-Vorschau", "item/oak_sign", MotdTool::new),
		new Tool("Generatoren", "Tellraw & Title", "Tellraw-, Title- und Actionbar-Befehle bauen", "item/name_tag", TellrawTool::new),
		new Tool("Generatoren", "Spielerköpfe", "Give-Befehl für den Kopf jedes Spielers", "item/bone", HeadTool::new),
		new Tool("Generatoren", "Buch-Editor", "Bücher mit Farben schreiben, Give-Befehl kopieren", "item/writable_book", BookTool::new),
		new Tool("Generatoren", "Banner-Designer", "Muster und Farben zu einem eigenen Banner kombinieren", "item/flower_banner_pattern", BannerTool::new),
		new Tool("Generatoren", "Feuerwerk", "Raketen mit Effekten, Farben und Give-Befehl", "item/firework_rocket", FireworkTool::new),
		new Tool("Generatoren", "Rüstung färben", "Farbstoffe mischen wie beim Herstellen im Spiel", "item/leather_chestplate", DyeTool::new),
		new Tool("Generatoren", "Rüstungsständer", "Posen und Ausrüstung, Summon-Befehl kopieren", "item/armor_stand", ArmorStandTool::new),
		new Tool("Generatoren", "Display Studio", "Text-, Block- und Gegenstandsanzeigen beschwören", "item/glow_item_frame", DisplayTool::new),
		new Tool("Rechner", "Koordinaten", "Nether- und Oberwelt-Koordinaten umrechnen", "item/compass_00", CoordsTool::new),
		new Tool("Rechner", "Kreis-Generator", "Pixelgenaue Kreise, Ellipsen und Kugeln", "item/snowball", CircleTool::new),
		new Tool("Rechner", "XP-Rechner", "Erfahrungslevel und -punkte umrechnen", "item/experience_bottle", XpTool::new),
		new Tool("Rechner", "Tick-Umrechner", "Echtzeit, Minecraft-Zeit und Ticks", "item/clock_00", TickTool::new),
		new Tool("Rechner", "Locator-Bar-Farbe", "Die Markerfarbe jedes Spielers auf der Locator-Bar", "item/recovery_compass_00", LocatorTool::new),
		new Tool("Guides & Referenz", "Trank-Guide", "Braurezepte, Effekte und Give-Befehle", "item/potion", PotionTool::new),
		new Tool("Guides & Referenz", "Verzauberungen", "Alle Verzauberungen, Gegenstände und Unverträglichkeiten", "item/enchanted_book", EnchantTool::new),
		new Tool("Im Launcher", "Skin-Editor", "Skins am 3D-Modell bemalen: Emberleaf-Launcher → Werkzeuge", "item/brush", null),
		new Tool("Im Launcher", "Render Studio", "3D-Bilder deines Skins mit Posen und Licht: im Launcher", "item/painting", null),
		new Tool("Im Launcher", "Wallpaper", "Hintergründe mit deiner Figur für Handy und PC: im Launcher", "item/filled_map", null),
		new Tool("Im Launcher", "Pack-Generator", "Totem- und Gemälde-Pakete aus eigenen Bildern: im Launcher", "item/totem_of_undying", null));

	private static final int CARD_H = 34;
	private final @Nullable Screen parent;
	private final List<int[]> cards = new ArrayList<>(); // x, y, tool index
	private int scroll;
	private int contentH;
	private int columns;
	private int cardW;
	private int gridX;
	private @Nullable String hint;
	private long hintAt;

	public ToolsScreen(@Nullable Screen parent) {
		super(Component.literal("Werkzeuge"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = Math.min(760, this.width - 24);
		gridX = (this.width - w) / 2;
		columns = w > 520 ? 3 : w > 330 ? 2 : 1;
		cardW = (w - (columns - 1) * 6) / columns;
		cards.clear();
		int y = 40;
		String section = null;
		int col = 0;
		for (int i = 0; i < TOOLS.size(); i++) {
			Tool t = TOOLS.get(i);
			if (!t.section().equals(section)) {
				if (col != 0) y += CARD_H + 6;
				col = 0;
				y += section == null ? 0 : 10;
				y += 16;
				section = t.section();
			}
			cards.add(new int[] {gridX + col * (cardW + 6), y, i});
			if (++col >= columns) {
				col = 0;
				y += CARD_H + 6;
			}
		}
		contentH = y + (col != 0 ? CARD_H : 0) - 40;
		addRenderableWidget(new EmberButton(this.width / 2 - 60, this.height - 26, 120, 20, Component.literal("Fertig"), b -> onClose()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, 0xD8120A06);
		Ui.text(g, Ui.title("Werkzeuge"), gridX, 12, 0xFFFFE6C4);
		Ui.text(g, Ui.menu("Generatoren, Rechner und Guides für Minecraft – direkt im Spiel"), gridX + Ui.font().width(Ui.title("Werkzeuge")) + 10, 13,
			0xFFB8A48E);
		int top = 34;
		int bottom = this.height - 32;
		g.enableScissor(0, top, this.width, bottom);
		String section = null;
		for (int[] c : cards) {
			Tool t = TOOLS.get(c[2]);
			int y = c[1] - scroll;
			if (!t.section().equals(section)) {
				section = t.section();
				if (y - 14 > top - 14 && y - 14 < bottom) {
					Ui.text(g, Ui.menu(section), gridX + 2, y - 13, 0xFFFFD6A8);
					g.fill(gridX + 8 + Ui.font().width(Ui.menu(section)), y - 8, gridX + columns * (cardW + 6) - 6, y - 7, 0x30FFE6C8);
				}
			}
			if (y + CARD_H < top || y > bottom) continue;
			boolean hover = mouseX >= c[0] && mouseX < c[0] + cardW && mouseY >= y && mouseY < y + CARD_H && mouseY >= top && mouseY < bottom;
			Ui.nineSlice(g, hover ? Ui.BUTTON_HOVER : Ui.PANEL, c[0], y, cardW, CARD_H, 6, 0xF0FFFFFF);
			Ui.nineSlice(g, Ui.ROUND, c[0] + 6, y + 7, 20, 20, 6, ARGB.color(0.35F, 0x000000));
			g.blit(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("textures/" + t.icon() + ".png"), c[0] + 8, y + 9, 0, 0, 16, 16, 16, 16,
				16, 16, -1);
			Ui.text(g, Ui.menu(t.name()), c[0] + 32, y + 6, t.open() != null ? 0xFFFFEBD4 : 0xFFD8C2A8);
			Ui.text(g, Ui.menu(trim(t.description(), cardW - 40)), c[0] + 32, y + 18, 0xFFB8A48E);
		}
		g.disableScissor();
		if (hint != null && System.currentTimeMillis() - hintAt < 4000) {
			Component h = Ui.menu(hint);
			Ui.nineSlice(g, Ui.PANEL, this.width / 2 - Ui.font().width(h) / 2 - 8, this.height - 54, Ui.font().width(h) + 16, 18, 6, 0xF8FFFFFF);
			Ui.text(g, h, this.width / 2F - Ui.font().width(h) / 2F, this.height - 49, 0xFFFFE6C4);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private static String trim(String text, int max) {
		if (Ui.font().width(Ui.menu(text)) <= max) return text;
		while (!text.isEmpty() && Ui.font().width(Ui.menu(text + "…")) > max) text = text.substring(0, text.length() - 1);
		return text + "…";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.y() < 34 || event.y() > this.height - 32) return false;
		for (int[] c : cards) {
			int y = c[1] - scroll;
			if (event.x() >= c[0] && event.x() < c[0] + cardW && event.y() >= y && event.y() < y + CARD_H) {
				Tool t = TOOLS.get(c[2]);
				if (t.open() != null) {
					this.minecraft.gui.setScreen(t.open().apply(this));
				} else {
					hint = t.name() + " findest du im Emberleaf-Launcher unter Werkzeuge";
					hintAt = System.currentTimeMillis();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Mth.clamp(scroll - (int) (scrollY * 24), 0, Math.max(0, contentH - (this.height - 72)));
		return true;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
