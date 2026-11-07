package net.emberleaf.client.menu;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.emberleaf.client.feature.Badges;
import net.emberleaf.client.feature.Playtime;
import net.emberleaf.client.friends.FriendsSync;
import net.emberleaf.client.friends.Lagerfeuer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * The player menu (look at a player, press G): their Emberleaf profile in the middle and a ring of actions around it,
 * like adding them as a friend, writing to them or inviting them into your party.
 */
public class PlayerMenuScreen extends Screen {
	private record Entry(String label, Runnable action) {}

	private final UUID uuid;
	private final String name;
	private final @Nullable KeyMapping key;
	private final List<Entry> entries = new ArrayList<>();
	private int selected = -1;
	private float open;
	private boolean done;

	public PlayerMenuScreen(UUID uuid, String name, @Nullable KeyMapping key) {
		super(Component.literal(name));
		this.uuid = uuid;
		this.name = name;
		this.key = key;
	}

	@Override
	protected void init() {
		entries.clear();
		String compact = uuid.toString().replace("-", "");
		boolean lagerfeuer = !FriendsSync.serverUrl().isEmpty();
		boolean friend = Lagerfeuer.isFriend(uuid);
		if (lagerfeuer && !friend) entries.add(new Entry("Freund hinzufügen", () -> call("/v1/friends/add", "name", name, name + ": Anfrage geschickt")));
		if (lagerfeuer && friend) {
			entries.add(new Entry("Nachricht schreiben", () -> {
				LagerfeuerScreen screen = new LagerfeuerScreen(null, null);
				this.minecraft.gui.setScreen(screen);
				screen.startDirect(compact, name);
			}));
			entries.add(new Entry("In Party einladen", () -> call("/v1/party/invite", "uuid", compact, name + " ist in die Party eingeladen")));
		}
		entries.add(new Entry("Name kopieren", () -> {
			this.minecraft.keyboardHandler.setClipboard(name);
			toast(name + " kopiert");
		}));
		entries.add(new Entry("Zurück", () -> {}));
		if (lagerfeuer) FriendsSync.refreshFriends(60_000);
	}

	private void call(String path, String field, String value, String done) {
		JsonObject body = new JsonObject();
		body.addProperty(field, value);
		FriendsSync.call("POST", path, body).whenCompleteAsync((r, e) -> toast(e != null ? FriendsSync.errorText(e) : done), this.minecraft);
	}

	private void toast(String text) {
		Lagerfeuer.notice(name, text, null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		open = Math.min(1, open + 0.2F);
		int cx = this.width / 2;
		int cy = this.height / 2;
		float k = 0.6F + 0.4F * open;
		float ry = Math.max(50, Math.min(this.height / 2F - 24, 86));
		float rx = Math.max(90, Math.min(this.width / 2F - 70, ry * 1.9F));
		int n = entries.size();
		double step = Math.PI * 2 / n;
		double start = -Math.PI / 2;
		double dx = (mouseX - cx) / rx;
		double dy = (mouseY - cy) / ry;
		selected = dx * dx + dy * dy > 0.3 * 0.3 ? Math.floorMod((int) Math.round((Math.atan2(dy, dx) - start) / step), n) : -1;
		MenuScene.glow(g, cx, cy, ry * k * 1.4F, ARGB.color(0.4F * open, 0x0B0604));
		for (int i = 0; i < n; i++) {
			double angle = start + i * step;
			float x = cx + (float) Math.cos(angle) * rx * k;
			float y = cy + (float) Math.sin(angle) * ry * k;
			Component label = Ui.menu(entries.get(i).label());
			int w = Ui.font().width(label) + 16;
			boolean on = i == selected;
			Ui.nineSlice(g, on ? Ui.BUTTON_HOVER : Ui.BUTTON, Math.round(x - w / 2F), Math.round(y - 9), w, 18, 6, ARGB.white(open));
			Ui.text(g, label, x - w / 2F + 8, y - 4, ARGB.multiplyAlpha(on ? 0xFFFFFFFF : 0xFFEBDCC8, open));
		}
		// the player in the middle: head, name, badges and (for Emberleaf players) playtime
		Heads.draw(g, this.font, uuid.toString(), cx - 12, cy - 30, 24);
		Component title = Ui.menu(name);
		Ui.text(g, title, cx - Ui.font().width(title) / 2F, cy - 2, ARGB.multiplyAlpha(0xFFFFE6C4, open));
		Lagerfeuer.Profile profile = Lagerfeuer.profile(uuid);
		List<String> badges = profile != null ? profile.badges() : Badges.of(uuid);
		Component line = Badges.line(badges);
		if (line != null) g.text(this.font, line, cx - this.font.width(line) / 2, cy + 10, 0xFFFFFFFF, false);
		String info = profile != null ? "Spielzeit " + Playtime.format(profile.playtime()) : Lagerfeuer.isFriend(uuid) ? "Dein Freund" : "";
		if (!info.isEmpty()) {
			Component c = Ui.menu(info);
			Ui.text(g, c, cx - Ui.font().width(c) / 2F, cy + (line != null ? 22 : 12), ARGB.multiplyAlpha(0xFFB8A48E, open));
		}
	}

	private void choose() {
		if (done) return;
		done = true;
		int s = selected;
		onClose();
		if (s >= 0 && s < entries.size()) entries.get(s).action().run();
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (key != null && key.matches(event) && selected >= 0) {
			choose();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		choose();
		return true;
	}
}
