package net.emberleaf.client.tools;

import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Server MOTD with colours and a preview like in the server list; the result goes into server.properties. */
public class MotdTool extends ToolScreen {
	private static String name = "Mein Lagerfeuer";
	private static String line1 = "&6&lEmberleaf &7» &eGemütlich zusammen spielen";
	private static String line2 = "&aJetzt online &8| &b26.3";

	public MotdTool(Screen parent) {
		super("MOTD-Generator", "Nachricht in der Serverliste", parent);
	}

	@Override
	protected void build() {
		field(left, top + 12, pw - 24, "Servername (nur für die Vorschau)", name, v -> name = v);
		field(left, top + 42, pw - 24, "Zeile 1 mit &-Codes", line1, v -> line1 = v);
		field(left, top + 60, pw - 24, "Zeile 2", line2, v -> line2 = v);
	}

	@Override
	protected String compute() {
		String motd = TextCodes.toSection(line1) + (line2.isBlank() ? "" : "\n" + TextCodes.toSection(line2));
		return "motd=" + TextCodes.toProperties(motd).replace("\n", "\\n");
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Servername", left, top);
		label(g, "MOTD (zwei Zeilen, &-Codes)", left, top + 31);
		label(g, "Vorschau in der Serverliste", left, top + 81);
		int x = left;
		int y = top + 93;
		int w = Math.min(305, pw - 24);
		g.fill(x - 2, y - 2, x + w + 2, y + 36, 0xFF000000);
		g.fill(x - 1, y - 1, x + w + 1, y + 35, 0xFF303030);
		g.blit(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("textures/block/grass_block_side.png"), x, y, 0, 0, 32, 32, 16, 16, 16, 16, -1);
		g.text(this.font, Component.literal(name), x + 35, y + 1, 0xFFFFFFFF, false);
		g.text(this.font, Component.literal(TextCodes.toSection(line1)), x + 35, y + 12, 0xFF808080, false);
		g.text(this.font, Component.literal(TextCodes.toSection(line2)), x + 35, y + 22, 0xFF808080, false);
		String players = "12/20";
		g.text(this.font, Component.literal(players), x + w - this.font.width(players) - 15, y + 1, 0xFF808080, false);
		for (int i = 0; i < 5; i++) g.fill(x + w - 11 + i * 2, y + 7 - i, x + w - 10 + i * 2, y + 8, 0xFF55FF55);
		Component hint = Ui.menu("In die server.properties deines Servers einfügen (ersetzt die alte motd-Zeile).");
		if (y + 41 + 9 <= contentBottom() && Ui.font().width(hint) <= pw - 24) Ui.text(g, hint, left, y + 41, 0xFFB8A48E);
		else if (y + 41 + 9 <= contentBottom()) Ui.text(g, Ui.menu("Ergebnis in die server.properties einfügen."), left, y + 41, 0xFFB8A48E);
	}
}
