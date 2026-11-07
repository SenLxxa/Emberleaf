package net.emberleaf.client.menu.live;

import java.util.List;
import net.emberleaf.client.menu.IslandRenderState;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jspecify.annotations.Nullable;

/**
 * One frame of a live menu scene. Equal states (window in the background, time held to 15 steps a second)
 * let Minecraft reuse the last picture instead of drawing the scene again.
 */
public record LiveSceneState(
	String scene,
	float time,
	float ex, float ey, float ez,
	float tx, float ty, float tz,
	float fov,
	int ambient,
	int sun,
	float flicker,
	int quality,
	@Nullable Identifier skin,
	PlayerModelType model,
	List<IslandRenderState.Figure> friends,
	int x0, int y0, int x1, int y1,
	float scale,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
	public LiveSceneState(String scene, float time, float[] eye, float[] target, float fov, int ambient, int sun, float flicker, int quality,
		@Nullable Identifier skin, PlayerModelType model, List<IslandRenderState.Figure> friends, int x0, int y0, int x1, int y1) {
		this(scene, time, eye[0], eye[1], eye[2], target[0], target[1], target[2], fov, ambient, sun, flicker, quality, skin, model, friends,
			x0, y0, x1, y1, 1.0F, null, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
	}

	public LiveCamera camera() {
		return new LiveCamera(ex, ey, ez, tx, ty, tz, fov, (y1 - y0) / 2F);
	}
}
