package net.emberleaf.client.feature;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.emberleaf.client.config.Modules;
import net.emberleaf.client.menu.Ui;
import net.emberleaf.client.pack.PackFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.joml.Matrix3x2fStack;

/**
 * A small north-up map in the top right corner, drawn from the map colours of the blocks around the player
 * (like a held map, but live). Zoom = blocks per pixel, set in the Emberleaf menu.
 */
public final class Minimap {
	private static final int SIZE = 64;
	/** Mob pictures on the map, in GUI pixels. */
	private static final int ICON = 9;
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("emberleaf", "minimap");
	private static DynamicTexture texture;
	private static int timer;
	private static int centerX;
	private static int centerZ;

	private Minimap() {}

	public static void tick(Minecraft mc) {
		if (!Modules.on("minimap") || mc.level == null || mc.player == null) return;
		if (--timer > 0) return;
		timer = 10;
		if (texture == null) {
			texture = new DynamicTexture(() -> "Emberleaf minimap", SIZE, SIZE, true);
			mc.getTextureManager().register(TEXTURE, texture);
		}
		NativeImage img = texture.getPixels();
		if (img == null) return;
		ClientLevel level = mc.level;
		int scale = Math.max(1, (int) Math.round(Modules.value("minimap")));
		centerX = mc.player.getBlockX();
		centerZ = mc.player.getBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int py = 0; py < SIZE; py++) {
			int z = centerZ + (py - SIZE / 2) * scale;
			for (int px = 0; px < SIZE; px++) {
				int x = centerX + (px - SIZE / 2) * scale;
				int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - scale) - 1;
				pos.set(x, h, z);
				BlockState state = level.getBlockState(pos);
				int color = 0;
				if (h > level.getMinY() && !state.isAir()) {
					MapColor map = state.getMapColor(level, pos);
					MapColor.Brightness b = h > north ? MapColor.Brightness.HIGH : h < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
					if (state.getFluidState().is(FluidTags.WATER)) {
						int depth = 0;
						while (depth < 10 && level.getBlockState(pos.move(0, -1, 0)).getFluidState().is(FluidTags.WATER)) depth++;
						b = depth > 6 ? MapColor.Brightness.LOW : depth > 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
					}
					color = map == MapColor.NONE ? 0 : map.calculateARGBColor(b);
				}
				img.setPixel(px, py, color);
			}
		}
		texture.upload();
	}

	public static void render(GuiGraphicsExtractor g, Minecraft mc) {
		if (!Modules.on("minimap") || texture == null || mc.player == null || mc.level == null) return;
		int size = 74;
		int x = g.guiWidth() - size - 6;
		int y = 6;
		Ui.nineSlice(g, Ui.PANEL, x - 3, y - 3, size + 6, size + 6, 6, 0xF0FFFFFF);
		// the map shifts smoothly between updates so it does not jump block by block
		int scale = Math.max(1, (int) Math.round(Modules.value("minimap")));
		float k = size / (float) SIZE;
		float dx = (float) (mc.player.getX() - centerX - 0.5) / scale * k;
		float dz = (float) (mc.player.getZ() - centerZ - 0.5) / scale * k;
		g.enableScissor(x, y, x + size, y + size);
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, Math.round(x - dx), Math.round(y - dz), 0, 0, size, size, SIZE, SIZE, SIZE, SIZE, -1);
		if (Modules.on("minimap_mobs")) mobs(g, mc, x, y, size, scale, k);
		// other players as small dots
		for (Player p : mc.level.players()) {
			if (p == mc.player) continue;
			float ox = (float) (p.getX() - mc.player.getX()) / scale * k;
			float oz = (float) (p.getZ() - mc.player.getZ()) / scale * k;
			if (Math.abs(ox) < size / 2F && Math.abs(oz) < size / 2F) {
				int px = Math.round(x + size / 2F + ox);
				int pz = Math.round(y + size / 2F + oz);
				g.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFF2A1A10);
				g.fill(px - 1, pz - 1, px + 1, pz + 1, 0xFF7DDC5A);
			}
		}
		g.disableScissor();
		// the player: an ember arrow pointing where you look
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + size / 2F, y + size / 2F);
		pose.rotate((mc.player.getYRot() + 180F) * Mth.DEG_TO_RAD);
		for (int i = 0; i < 5; i++) {
			int w = i < 2 ? 1 : i < 4 ? 2 : 3;
			g.fill(-w - 1, -4 + i * 2 - 1, w + 1, -4 + i * 2 + 2, 0xFF2A1A10);
		}
		for (int i = 0; i < 5; i++) {
			int w = i < 2 ? 0 : i < 4 ? 1 : 2;
			g.fill(-w, -4 + i * 2, w + 1, -4 + i * 2 + 1, ARGB.color(255, 255, 154, 74));
		}
		pose.popMatrix();
		// N marker and coordinates
		Ui.text(g, Ui.menu("N"), x + size / 2F - 3, y + 1, 0xFFFFEBD4);
		String coords = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
		var label = Ui.menu(coords);
		Ui.text(g, label, x + (size - Ui.font().width(label)) / 2F, y + size + 5, 0xFFFFEBD4);
	}

	private record Seen(LivingEntity entity, double distance) {}

	/** Animals and monsters around you: their picture from the texture pack (Icon Fresh), otherwise a small dot (red = hostile). */
	private static void mobs(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int size, int scale, float k) {
		Player me = mc.player;
		double range = size / 2.0 / k * scale;
		List<Seen> seen = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || e instanceof Player || e instanceof ArmorStand || !e.isAlive() || e.isInvisible()) continue;
			double ddx = e.getX() - me.getX();
			double ddz = e.getZ() - me.getZ();
			if (Math.abs(ddx) > range || Math.abs(ddz) > range) continue;
			seen.add(new Seen(living, ddx * ddx + ddz * ddz));
		}
		// the nearest are drawn last (on top); at most 48 so a mob farm does not cost frames
		seen.sort(Comparator.comparingDouble(Seen::distance).reversed());
		if (seen.size() > 48) seen = seen.subList(seen.size() - 48, seen.size());
		for (Seen s : seen) {
			LivingEntity e = s.entity();
			float ox = (float) (e.getX() - me.getX()) / scale * k;
			float oz = (float) (e.getZ() - me.getZ()) / scale * k;
			int px = Math.round(x + size / 2F + ox);
			int pz = Math.round(y + size / 2F + oz);
			// mobs far above or below you fade out a little
			double dy = Math.abs(e.getY() - me.getY());
			int alpha = dy < 8 ? 255 : dy > 32 ? 110 : (int) (255 - (dy - 8) / 24 * 145);
			Identifier icon = PackFeatures.mobIcon(e.getType());
			if (icon != null) {
				int s2 = e.isBaby() ? ICON - 2 : ICON;
				g.blit(RenderPipelines.GUI_TEXTURED, icon, px - s2 / 2, pz - s2 / 2, 0, 0, s2, s2,
					PackFeatures.ICON_SIZE, PackFeatures.ICON_SIZE, PackFeatures.ICON_SIZE, PackFeatures.ICON_SIZE, ARGB.color(alpha, 255, 255, 255));
			} else {
				int color = e instanceof Enemy ? ARGB.color(alpha, 230, 70, 50) : ARGB.color(alpha, 245, 236, 214);
				g.fill(px - 2, pz - 2, px + 2, pz + 2, ARGB.color(alpha, 42, 26, 16));
				g.fill(px - 1, pz - 1, px + 1, pz + 1, color);
			}
		}
	}
}
