package net.emberleaf.client.tools;

import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Matrix3x2fStack;

/** The give command for the head of any player (with a preview of the head). */
public class HeadTool extends ToolScreen {
	private static String name = "SenLxca";
	private static int count = 1;
	private String shown = "";
	private MutableComponent head;

	public HeadTool(Screen parent) {
		super("Spielerköpfe", "Hol dir den Kopf jedes Spielers", parent);
	}

	@Override
	protected void build() {
		field(left, top + 12, Math.min(220, pw - 24), "Minecraft-Name", name, v -> name = v.strip());
		field(left + Math.min(220, pw - 24) + 6, top + 12, 50, "Anzahl", String.valueOf(count), v -> count = Math.max(1, Math.min(64, parseInt(v, 1))));
	}

	@Override
	protected String compute() {
		String n = name.isBlank() ? "Steve" : name;
		return "/give @s minecraft:player_head[profile={name:" + TextCodes.quote(n) + "}]" + (count > 1 ? " " + count : "");
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Name und Anzahl", left, top);
		String n = name.isBlank() ? "Steve" : name;
		if (!n.equals(shown) && n.matches("[A-Za-z0-9_]{1,16}")) {
			shown = n;
			head = MutableComponent.create(new ObjectContents(new PlayerSprite(ResolvableProfile.createUnresolved(n), true), Optional.empty()));
		}
		if (head != null) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(left + 8, top + 44);
			pose.scale(8, 8);
			g.text(this.font, head, 0, 0, 0xFFFFFFFF, false);
			pose.popMatrix();
			g.text(this.font, Component.literal(shown), left + 8, top + 124, 0xFFFFEBD4, true);
		}
	}
}
