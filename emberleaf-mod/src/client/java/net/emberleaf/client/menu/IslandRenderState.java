package net.emberleaf.client.menu;

import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jspecify.annotations.Nullable;

/** One frame of the 3D island in the menu: scene, where it sits on screen, how the camera leans and who stands on it. */
public record IslandRenderState(
	String scene,
	float yaw,
	float pitch,
	float time,
	int tint,
	@Nullable Identifier skin,
	PlayerModelType model,
	float lookYaw,
	List<Figure> friends,
	int x0,
	int y0,
	int x1,
	int y1,
	float scale,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
	/** A friend standing on the island. */
	public record Figure(Identifier skin, PlayerModelType model) {}

	public IslandRenderState(String scene, float yaw, float pitch, float time, int tint, @Nullable Identifier skin, PlayerModelType model,
		float lookYaw, List<Figure> friends, int x0, int y0, int x1, int y1, float scale) {
		this(scene, yaw, pitch, time, tint, skin, model, lookYaw, friends, x0, y0, x1, y1, scale, null,
			PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
	}
}
