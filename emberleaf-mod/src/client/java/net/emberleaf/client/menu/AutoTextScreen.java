package net.emberleaf.client.menu;

import net.emberleaf.client.feature.ChatExtras;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** AutoText: five prepared chat messages or commands; each one gets a key under Steuerung → Emberleaf. */
public class AutoTextScreen extends Screen {
	private final @Nullable Screen parent;
	private final EditBox[] boxes = new EditBox[ChatExtras.AUTOTEXT_SLOTS];

	public AutoTextScreen(@Nullable Screen parent) {
		super(Component.literal("AutoText"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = Math.min(360, this.width - 40);
		int x = (this.width - w) / 2;
		int y = 52;
		for (int i = 0; i < boxes.length; i++) {
			EditBox box = new EditBox(this.font, x + 90, y + i * 28, w - 90, 18, Component.literal("AutoText " + (i + 1)));
			box.setMaxLength(256);
			box.setValue(ChatExtras.autoText(i));
			box.setHint(Component.literal(i == 0 ? "z. B. gg oder /home" : ""));
			boxes[i] = addRenderableWidget(box);
		}
		int by = y + boxes.length * 28 + 8;
		addRenderableWidget(new EmberButton(x, by, (w - 6) / 2, 20, Component.literal("Tasten festlegen"),
			b -> this.minecraft.gui.setScreen(new KeyBindsScreen(this, this.minecraft.options))));
		addRenderableWidget(new EmberButton(x + (w + 6) / 2, by, (w - 6) / 2, 20, Component.literal("Speichern"), b -> onClose()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, 0xE0140C08);
		int w = Math.min(360, this.width - 40);
		int x = (this.width - w) / 2;
		Ui.text(g, Ui.title("AutoText"), x, 14, 0xFFFFE6C4);
		Ui.text(g, Ui.menu("Nachrichten oder Befehle (mit /) per Taste senden"), x, 32, 0xFFB8A48E);
		for (int i = 0; i < boxes.length; i++) {
			Component key = ChatExtras.autoTextKey(i).getTranslatedKeyMessage();
			String label = "Taste: " + (ChatExtras.autoTextKey(i).isUnbound() ? "–" : key.getString());
			Ui.text(g, Ui.menu(label), x, 57 + i * 28, 0xFFFFD6A8);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	@Override
	public void onClose() {
		for (int i = 0; i < boxes.length; i++) ChatExtras.setAutoText(i, boxes[i].getValue());
		this.minecraft.gui.setScreen(parent);
	}
}
