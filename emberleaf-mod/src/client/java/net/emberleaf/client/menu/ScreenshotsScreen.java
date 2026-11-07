package net.emberleaf.client.menu;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Screenshot browser: your screenshots as pictures, newest first; open one big, open the folder or delete it. */
public class ScreenshotsScreen extends Screen {
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm").withZone(ZoneId.systemDefault());
	private static final int THUMB_W = 128;
	private static final int THUMB_H = 72;
	private final @Nullable Screen parent;
	private final List<Path> files = new ArrayList<>();
	private final Map<Path, Thumb> thumbs = new HashMap<>();
	private int scroll;
	private @Nullable Path open;
	private int columns = 1;
	private int gridX;

	private record Thumb(Identifier id, int w, int h) {}

	public ScreenshotsScreen(@Nullable Screen parent) {
		super(Component.literal("Screenshots"));
		this.parent = parent;
	}

	private Path folder() {
		return this.minecraft.gameDirectory.toPath().resolve("screenshots");
	}

	@Override
	protected void init() {
		files.clear();
		if (Files.isDirectory(folder())) {
			try (Stream<Path> list = Files.list(folder())) {
				list.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
					.sorted((a, b) -> Long.compare(modified(b), modified(a))).forEach(files::add);
			} catch (IOException ignored) {
				// keeps the list empty
			}
		}
		int bw = 110;
		int y = this.height - 28;
		addRenderableWidget(new EmberButton(this.width / 2 - bw - 60, y, bw, 20, Component.literal("Ordner öffnen"),
			b -> com.mojang.blaze3d.Blaze3D.openPath(folder())));
		addRenderableWidget(new EmberButton(this.width / 2 - 55, y, bw, 20, Component.literal(open != null ? "Löschen" : "Zurück"), b -> {
			if (open != null) askDelete(open);
			else onClose();
		}));
		addRenderableWidget(new EmberButton(this.width / 2 + 60, y, bw, 20, Component.literal(open != null ? "Zurück zur Liste" : "Fertig"), b -> {
			if (open != null) {
				open = null;
				rebuildWidgets();
			} else {
				onClose();
			}
		}));
		columns = Math.max(1, (this.width - 40) / (THUMB_W + 10));
		gridX = (this.width - columns * (THUMB_W + 10) + 10) / 2;
	}

	private static long modified(Path p) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	private void askDelete(Path file) {
		this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				try {
					Files.deleteIfExists(file);
				} catch (IOException ignored) {
					// nothing else to do
				}
				release(file);
				open = null;
			}
			this.minecraft.gui.setScreen(this);
		}, Component.literal("Screenshot löschen?"), Component.literal(file.getFileName().toString())));
	}

	private @Nullable Thumb thumb(Path file) {
		Thumb t = thumbs.get(file);
		if (t != null || thumbs.size() > 400) return t;
		try (InputStream in = Files.newInputStream(file); NativeImage full = NativeImage.read(in)) {
			int w = THUMB_W * 2;
			int h = Math.max(1, Math.round(w * full.getHeight() / (float) full.getWidth()));
			NativeImage small = new NativeImage(w, h, false);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) small.setPixel(x, y, full.getPixel(x * full.getWidth() / w, y * full.getHeight() / h));
			}
			Identifier id = Identifier.fromNamespaceAndPath("emberleaf", "screenshot/" + Integer.toHexString(file.hashCode()));
			DynamicTexture tex = new DynamicTexture(() -> "Emberleaf screenshot " + file.getFileName(), small);
			tex.upload();
			this.minecraft.getTextureManager().register(id, tex);
			t = new Thumb(id, w, h);
		} catch (IOException | RuntimeException e) {
			t = null;
		}
		thumbs.put(file, t);
		return t;
	}

	private void release(Path file) {
		Thumb t = thumbs.remove(file);
		if (t != null) this.minecraft.getTextureManager().release(t.id());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, 0xE0140C08);
		Ui.text(g, Ui.title("Screenshots"), 16, 12, 0xFFFFE6C4);
		Ui.text(g, Ui.menu(files.size() + " Bilder  ·  F2 macht einen Screenshot, F9 ein Panorama"), 16 + Ui.font().width(Ui.title("Screenshots")) + 10, 13,
			0xFFB8A48E);
		if (open != null) {
			Thumb t = thumb(open);
			if (t != null) {
				int maxW = this.width - 40;
				int maxH = this.height - 80;
				float k = Math.min(maxW / (float) t.w(), maxH / (float) t.h());
				int w = Math.round(t.w() * k);
				int h = Math.round(t.h() * k);
				g.blit(RenderPipelines.GUI_TEXTURED, t.id(), (this.width - w) / 2, 34, 0, 0, w, h, t.w(), t.h(), t.w(), t.h(), -1);
			}
			Component name = Ui.menu(open.getFileName() + "  ·  " + WHEN.format(Instant.ofEpochMilli(modified(open))));
			Ui.text(g, name, (this.width - Ui.font().width(name)) / 2F, this.height - 44, 0xFFFFEBD4);
		} else if (files.isEmpty()) {
			Component none = Ui.menu("Noch keine Screenshots – drück im Spiel F2.");
			Ui.text(g, none, (this.width - Ui.font().width(none)) / 2F, this.height / 2F - 4, 0xFFB8A48E);
		} else {
			int top = 34;
			int bottom = this.height - 36;
			g.enableScissor(0, top, this.width, bottom);
			for (int i = 0; i < files.size(); i++) {
				int x = gridX + (i % columns) * (THUMB_W + 10);
				int y = top + (i / columns) * (THUMB_H + 22) - scroll;
				if (y + THUMB_H + 20 < top || y > bottom) continue;
				boolean hover = mouseX >= x && mouseX < x + THUMB_W && mouseY >= y && mouseY < y + THUMB_H && mouseY > top && mouseY < bottom;
				Ui.nineSlice(g, hover ? Ui.BUTTON_HOVER : Ui.PANEL, x - 3, y - 3, THUMB_W + 6, THUMB_H + 20, 5, 0xF0FFFFFF);
				Thumb t = thumb(files.get(i));
				if (t != null) {
					int h = Math.min(THUMB_H, Math.round(THUMB_W * t.h() / (float) t.w()));
					g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y + (THUMB_H - h) / 2, 0, 0, THUMB_W, h, t.w(), t.h(), t.w(), t.h(), -1);
				}
				String when = WHEN.format(Instant.ofEpochMilli(modified(files.get(i))));
				Ui.text(g, Ui.menu(when), x + 2, y + THUMB_H + 4, ARGB.color(1F, hover ? 0xFFFFFF : 0xD8C2A8));
			}
			g.disableScissor();
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (open == null) {
			int top = 34;
			for (int i = 0; i < files.size(); i++) {
				int x = gridX + (i % columns) * (THUMB_W + 10);
				int y = top + (i / columns) * (THUMB_H + 22) - scroll;
				if (event.x() >= x && event.x() < x + THUMB_W && event.y() >= Math.max(y, top) && event.y() < y + THUMB_H && event.y() < this.height - 36) {
					open = files.get(i);
					rebuildWidgets();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int rows = (files.size() + columns - 1) / columns;
		int max = Math.max(0, rows * (THUMB_H + 22) - (this.height - 70));
		scroll = Mth.clamp(scroll - (int) (scrollY * 30), 0, max);
		return true;
	}

	@Override
	public void removed() {
		for (Thumb t : thumbs.values()) if (t != null) this.minecraft.getTextureManager().release(t.id());
		thumbs.clear();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
