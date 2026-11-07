package net.emberleaf.client.menu;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.emberleaf.client.friends.Lagerfeuer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Matrix3x2fStack;

/** Player heads (face + hat) in menus, drawn as the game's own head text sprite so skins load by themselves. */
public final class Heads {
	private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

	private Heads() {}

	/** The head of a player as an 8x8 text sprite; {@code uuid} with or without dashes. */
	public static Component of(String uuid) {
		return CACHE.computeIfAbsent(uuid.replace("-", ""), id -> MutableComponent.create(new ObjectContents(
			new PlayerSprite(ResolvableProfile.createUnresolved(Lagerfeuer.uuid(id)), true), Optional.empty())));
	}

	public static void draw(GuiGraphicsExtractor g, Font font, String uuid, int x, int y, int size) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / 8F, size / 8F);
		g.text(font, of(uuid), 0, 0, 0xFFFFFFFF, false);
		pose.popMatrix();
	}
}
