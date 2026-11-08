package net.emberleaf.client.menu;

import com.mojang.realmsclient.RealmsMainScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import net.emberleaf.client.friends.FriendsSync;
import net.emberleaf.client.friends.FriendsSync.Friend;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * Emberleaf's own main menu: an autumn forest at the campfire, your own skin standing by the fire,
 * warm wooden buttons and your friends from the Lagerfeuer. Extends TitleScreen so the game and
 * other mods still recognise it as the title screen.
 */
public class EmberleafTitleScreen extends TitleScreen {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("emberleaf", "textures/gui/menu/logo.png");
	private static final int LOGO_TEX_W = 1030;
	private static final int LOGO_TEX_H = 258;
	private static final float LOGO_W = 206;
	private static final float LOGO_H = LOGO_W * LOGO_TEX_H / LOGO_TEX_W;
	private static final String[] SPLASHES = {
		"Mit heißem Kakao!", "Jetzt mit 100 % mehr Laub!", "Das Lagerfeuer brennt!", "Garantiert gemütlich!",
		"Pilze lieber nicht essen!", "Kuschelsocken empfohlen!", "Glühwürmchen inklusive!", "Shrimp war hier!",
		"Jetzt in der Alpha!", "Blätter rascheln lassen!", "Herbst ist die beste Jahreszeit!", "Auch im Winter warm!",
		"Marshmallows nicht vergessen!", "Leise knistert das Feuer …",
	};
	private static boolean firstOpen = true;

	private final List<AbstractWidget> widgets = new ArrayList<>();
	private final String splash = SPLASHES[new Random().nextInt(SPLASHES.length)];
	private final long openedAt = System.currentTimeMillis();
	private final boolean fadeIn;
	private float logoScale = 1;
	private int logoX;
	private int logoY;

	public EmberleafTitleScreen() {
		super(false);
		this.fadeIn = firstOpen;
		firstOpen = false;
	}

	private <T extends AbstractWidget> T add(T widget) {
		widgets.add(widget);
		return this.addRenderableWidget(widget);
	}

	@Override
	protected void init() {
		widgets.clear();
		if (MenuScene.skinLookup == null) {
			// resolved in the background; getGameProfile() would block the game until Mojang answers
			var profile = net.minecraft.world.item.component.ResolvableProfile.createUnresolved(this.minecraft.getUser().getProfileId());
			MenuScene.skinLookup = () -> this.minecraft.playerSkinRenderCache().getOrDefault(profile).playerSkin();
		}

		int left = Math.max(14, Math.round(this.width * 0.065F));
		logoScale = Mth.clamp(this.width * 0.33F / LOGO_W, 0.7F, 1.8F);
		logoX = left - 4;
		logoY = Math.max(10, Math.round(this.height * 0.1F));
		int buttonW = Math.min(170, Math.max(130, Math.round(this.width * 0.26F)));
		int y = logoY + Math.round(LOGO_H * logoScale) + 12;

		add(new EmberButton(left, y, buttonW, 20, Component.translatable("menu.singleplayer"),
			b -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))));
		y += 24;
		EmberButton multiplayer = add(new EmberButton(left, y, buttonW, 20, Component.translatable("menu.multiplayer"),
			b -> this.minecraft.gui.setScreen(this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))));
		y += 24;
		EmberButton realms = add(new EmberButton(left, y, buttonW, 20, Component.translatable("menu.online"),
			b -> this.minecraft.gui.setScreen(new RealmsMainScreen(this))));
		if (!this.minecraft.allowsMultiplayer()) {
			Tooltip why = Tooltip.create(Component.translatable("title.multiplayer.disabled"));
			multiplayer.active = false;
			multiplayer.setTooltip(why);
			realms.active = false;
			realms.setTooltip(why);
		}
		y += 24;
		int half = (buttonW - 4) / 2;
		add(new EmberButton(left, y, half, 20, Component.literal("Emberleaf"),
			b -> this.minecraft.gui.setScreen(new EmberleafSettingsScreen(this))));
		add(new EmberButton(left + half + 4, y, buttonW - half - 4, 20, Component.literal("Werkzeuge"),
			b -> this.minecraft.gui.setScreen(new net.emberleaf.client.tools.ToolsScreen(this))));
		y += 24;
		add(new EmberButton(left, y, buttonW, 20, Component.translatable("menu.options"),
			b -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options))));
		y += 28;
		EmberButton language = add(new EmberButton(left, y, 20, 20, Component.translatable("options.language"),
			Identifier.withDefaultNamespace("icon/language"),
			b -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager()))));
		language.setTooltip(Tooltip.create(Component.translatable("options.language.tooltip")));
		EmberButton access = add(new EmberButton(left + 24, y, 20, 20, Component.translatable("options.accessibility"),
			Identifier.withDefaultNamespace("icon/accessibility"),
			b -> this.minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options))));
		access.setTooltip(Tooltip.create(Component.translatable("options.accessibility.tooltip")));
		add(new EmberButton(left + 48, y, buttonW - 48, 20, Component.translatable("menu.quit"), b -> this.minecraft.stop()));

		// the friends list on the right, above the player figure
		int panelW = Math.min(180, Math.max(136, Math.round(this.width * 0.25F)));
		add(new FriendsPanel(this.width - panelW - 12, 12, panelW, Math.round(this.height * 0.6F), this::joinFriend));

		Component credits = Ui.menu(Component.translatable("title.credits"));
		int cw = this.font.width(credits);
		add(new PlainTextButton(this.width - cw - 4, this.height - 12, cw, 10, credits,
			b -> this.minecraft.gui.setScreen(new CreditsAndAttributionScreen(this)), this.font));

		FriendsSync.refreshFriends(15_000);
	}

	private void joinFriend(Friend friend) {
		String address = friend.server();
		ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(address),
			new ServerData(friend.name(), address, ServerData.Type.OTHER), false, null);
	}

	@Override
	public void tick() {
		FriendsSync.refreshFriends(30_000);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		MenuScene.draw(g, this.width, this.height, mouseX, mouseY, Math.round(this.width * 0.34F));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float age = (System.currentTimeMillis() - openedAt) / 1000F;
		float fade = fadeIn ? Mth.clamp((age - 0.25F) / 1.2F, 0, 1) : 1;

		MenuScene.drawFront(g, this.width, this.height);

		// soft dark band behind the menu so the buttons read well on every time of day
		int band = Math.round(this.width * 0.42F);
		for (int i = 0; i < band; i += 4) {
			float k = 1 - i / (float) band;
			g.fill(i, 0, i + 4, this.height, ARGB.color(0.38F * k * k, 0x0B0604));
		}
		g.fillGradient(0, this.height - 40, this.width, this.height, 0x00000000, 0x90000000);

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		float bob = Mth.sin(age * 1.4F) * 1.0F;
		pose.translate(logoX, logoY + bob);
		pose.scale(logoScale * LOGO_W / LOGO_TEX_W, logoScale * LOGO_W / LOGO_TEX_W);
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, 0, 0, 0, 0, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H, ARGB.white(fade));
		pose.popMatrix();
		Ui.text(g, Ui.menu("Minecraft-Client  ·  " + SharedConstants.getCurrentVersion().name() + "  ·  Alpha"),
			logoX + 50 * logoScale, logoY + LOGO_H * logoScale - 6 * logoScale, ARGB.color(fade * 0.9F, 0xF1D9BC));

		pose.pushMatrix();
		pose.translate(logoX + LOGO_W * logoScale - 26 * logoScale, logoY + 17 * logoScale);
		pose.rotate((float) Math.toRadians(-12));
		float pulse = 1.0F + 0.05F * Mth.sin(age * 5.0F);
		pose.scale(pulse, pulse);
		Ui.text(g, Ui.title(splash), 0, -4, ARGB.color(fade, 0xFFE07A));
		pose.popMatrix();

		for (AbstractWidget widget : widgets) {
			widget.setAlpha(fade);
			widget.extractRenderState(g, mouseX, mouseY, a);
		}

		String version = "Emberleaf " + FabricLoader.getInstance().getModContainer("emberleaf")
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("") + " Alpha  ·  Fabric";
		Ui.text(g, Ui.menu(version), 6, this.height - 13, ARGB.color(fade * 0.85F, 0xE8D8C4));
	}
}
