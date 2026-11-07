package net.emberleaf.client.tools;

import java.util.ArrayList;
import java.util.List;
import net.emberleaf.client.menu.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Write a book with colours (&amp;-codes) and several pages, then copy the give command. */
public class BookTool extends ToolScreen {
	private static final Identifier BOOK = Identifier.withDefaultNamespace("textures/gui/book.png");
	private static String title = "Lagerfeuer-Geschichten";
	private static String author = "SenLxca";
	private static final List<String> PAGES = new ArrayList<>(List.of("&6Kapitel 1\n\n&0Es war einmal ein gemütliches Lagerfeuer …"));
	private static int page;
	private MultiLineEditBox editor;

	public BookTool(Screen parent) {
		super("Buch-Editor", "Bücher mit Farben schreiben", parent);
	}

	@Override
	protected void build() {
		int w = (pw - 24 - 6) / 2;
		field(left, top + 12, w, "Titel", title, v -> title = v);
		field(left + w + 6, top + 12, w, "Autor", author, v -> author = v);
		int editorW = Math.min(240, pw - 24 - 130);
		editor = new MultiLineEditBox.Builder().setX(left).setY(top + 44).setPlaceholder(Component.literal("Text dieser Seite (&-Codes erlaubt)"))
			.build(this.font, editorW, Math.max(60, ph - 34 - 44 - 90), Component.literal("Seite"));
		editor.setCharacterLimit(1024);
		editor.setValue(PAGES.get(page));
		editor.setValueListener(v -> {
			PAGES.set(page, v);
			update();
		});
		addRenderableWidget(editor);
		int bx = left + editorW + 8;
		button(bx, top + 44, 56, "◀", () -> turn(-1));
		button(bx + 60, top + 44, 56, "▶", () -> turn(1));
		button(bx, top + 66, 116, "+ Neue Seite", () -> {
			PAGES.add(page + 1, "");
			turn(1);
		});
		button(bx, top + 88, 116, "− Seite löschen", () -> {
			if (PAGES.size() > 1) {
				PAGES.remove(page);
				page = Math.min(page, PAGES.size() - 1);
				editor.setValue(PAGES.get(page));
			}
		});
	}

	private void turn(int d) {
		page = Math.max(0, Math.min(PAGES.size() - 1, page + d));
		editor.setValue(PAGES.get(page));
	}

	@Override
	protected String compute() {
		StringBuilder pages = new StringBuilder();
		for (int i = 0; i < PAGES.size(); i++) {
			if (i > 0) pages.append(',');
			pages.append(TextCodes.quote(TextCodes.toSection(PAGES.get(i))));
		}
		return "/give @s minecraft:written_book[written_book_content={title:" + TextCodes.quote(title.isBlank() ? "Buch" : title) + ",author:"
			+ TextCodes.quote(author.isBlank() ? "Emberleaf" : author) + ",pages:[" + pages + "]}]";
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		label(g, "Titel und Autor", left, top);
		label(g, "Seite " + (page + 1) + " von " + PAGES.size(), left, top + 32);
		int editorW = Math.min(240, pw - 24 - 130);
		int bx = left + editorW + 8;
		// a small preview of the page in a real book
		int previewY = top + 114;
		if (previewY + 90 < py + ph - 60) {
			// the vanilla book page at half size; text wraps like in the real book (114 px wide)
			g.blit(RenderPipelines.GUI_TEXTURED, BOOK, bx, previewY, 20, 1, 73, 90, 146, 180, 256, 256, -1);
			List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(Component.literal(TextCodes.toSection(PAGES.get(page))), 114);
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(bx + 9, previewY + 8);
			pose.scale(0.5F, 0.5F);
			for (int i = 0; i < Math.min(14, lines.size()); i++) g.text(this.font, lines.get(i), 0, i * 9, 0xFF000000, false);
			pose.popMatrix();
		} else {
			Ui.text(g, Ui.menu(TextCodes.toSection(PAGES.get(page)).length() + " Zeichen"), bx, previewY, 0xFFB8A48E);
		}
	}
}
