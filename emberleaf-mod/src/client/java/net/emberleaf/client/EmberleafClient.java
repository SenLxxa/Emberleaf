package net.emberleaf.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.emberleaf.client.feature.Auras;
import net.emberleaf.client.feature.Contrails;
import net.emberleaf.client.feature.Emotes;
import net.emberleaf.client.feature.Pets;
import net.emberleaf.client.feature.Zoom;
import net.emberleaf.client.friends.FriendsSync;
import net.emberleaf.client.hud.EmberHud;
import net.emberleaf.client.menu.EmberButton;
import net.emberleaf.client.menu.EmberleafSettingsScreen;
import net.emberleaf.client.menu.IslandRenderer;
import net.emberleaf.client.outfit.OutfitStore;
import net.emberleaf.client.render.CosmeticsLayer;
import net.emberleaf.client.totem.SkinTotemRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class EmberleafClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("emberleaf", "main"));

	private int ticks;

	@Override
	public void onInitializeClient() {
		KeyMapping zoom = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.zoom", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, CATEGORY));
		Zoom.init(zoom);
		Emotes.init(KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.emote", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY)));
		KeyMapping menu = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, CATEGORY));
		net.emberleaf.client.feature.Freelook.init(KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.freelook", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CATEGORY)));
		KeyMapping panorama = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.panorama", InputConstants.Type.KEYBOARD, InputConstants.KEY_F9, CATEGORY));
		KeyMapping tools = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.tools", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		KeyMapping lagerfeuer = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.party", InputConstants.Type.KEYBOARD, InputConstants.KEY_U, CATEGORY));
		KeyMapping playerMenu = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.player_menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY));
		net.emberleaf.client.feature.SpeechBubbles.register();
		net.emberleaf.client.feature.ChatExtras.register();
		net.emberleaf.client.friends.LagerfeuerCommands.register();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(
			mc -> net.emberleaf.client.feature.Playtime.save());

		// named elytras + minimap mob pictures from the Emberleaf texture pack (things 26.3 has no built-in support for)
		net.emberleaf.client.pack.PackFeatures.register();
		// Skin-Totem: the totem's item model (assets/minecraft/items/totem_of_undying.json) uses this renderer
		SpecialModelRenderers.ID_MAPPER.put(SkinTotemRenderer.ID, SkinTotemRenderer.Unbaked.MAP_CODEC);
		// the floating 3D island behind the main menu
		PictureInPictureRendererRegistry.register(ctx -> new IslandRenderer());
		PictureInPictureRendererRegistry.register(ctx -> new net.emberleaf.client.menu.live.LiveSceneRenderer());
		// pets walking next to their owners, elytra contrails
		LevelRenderEvents.COLLECT_SUBMITS.register(Pets::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(Contrails::render);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("emberleaf", "hud"), EmberHud::render);
		// messages and party news from the Lagerfeuer, top right (also over menus)
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("emberleaf", "lagerfeuer"),
			(g, delta) -> net.emberleaf.client.friends.Lagerfeuer.renderNotices(g));
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> ScreenEvents.afterExtract(screen).register(
			(s, g, mouseX, mouseY, delta) -> net.emberleaf.client.friends.Lagerfeuer.renderNotices(g)));

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new CosmeticsLayer(avatar));
			}
		});

		// leaf button in the pause menu opens the Emberleaf menu, the tools sit next to it
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof PauseScreen pause) {
				Screens.getWidgets(screen).add(new EmberButton(6, 6, 96, 20, Component.literal("Emberleaf"),
					b -> mc.gui.setScreen(new EmberleafSettingsScreen(screen))));
				Screens.getWidgets(screen).add(new EmberButton(106, 6, 96, 20, Component.literal("Werkzeuge"),
					b -> mc.gui.setScreen(new net.emberleaf.client.tools.ToolsScreen(screen))));
				int unread = net.emberleaf.client.friends.Lagerfeuer.unread();
				Screens.getWidgets(screen).add(new EmberButton(206, 6, 96, 20, Component.literal(unread > 0 ? "Lagerfeuer (" + unread + ")" : "Lagerfeuer"),
					b -> mc.gui.setScreen(new net.emberleaf.client.menu.LagerfeuerScreen(screen, null))));
				confirmQuit(mc, pause);
			}
		});

		OutfitStore.reloadIfChanged();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (++ticks % 20 == 0) OutfitStore.reloadIfChanged();
			while (menu.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new EmberleafSettingsScreen(null));
			}
			while (tools.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new net.emberleaf.client.tools.ToolsScreen(null));
			}
			while (lagerfeuer.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new net.emberleaf.client.menu.LagerfeuerScreen(null, null));
			}
			while (playerMenu.consumeClick()) {
				// the player you look at (up to a few blocks away)
				if (mc.gui.screen() == null && mc.crosshairPickEntity instanceof net.minecraft.world.entity.player.Player target && target != mc.player) {
					mc.gui.setScreen(new net.emberleaf.client.menu.PlayerMenuScreen(target.getUUID(), target.getGameProfile().name(), playerMenu));
				}
			}
			while (panorama.consumeClick()) {
				if (mc.gui.screen() == null && mc.player != null && net.emberleaf.client.config.Modules.on("panorama")) {
					mc.gui.hud.getChat().addClientSystemMessage(mc.grabPanoramixScreenshot(mc.gameDirectory));
				}
			}
			net.emberleaf.client.feature.Playtime.tick(mc);
			net.emberleaf.client.feature.Freelook.tick(mc);
			net.emberleaf.client.feature.ChatExtras.tick(mc);
			net.emberleaf.client.feature.Performance.tick(mc);
			Zoom.tick(mc);
			Emotes.tick(mc);
			Pets.tick(mc);
			Auras.tick(mc);
			net.emberleaf.client.feature.CapeFx.tick(mc);
			Contrails.tick(mc);
			EmberHud.tick(mc);
			net.emberleaf.client.feature.Minimap.tick(mc);
			FriendsSync.tick(mc);
		});
	}

	/** "Verlassen bestätigen": the pause menu's disconnect button asks first. */
	private static void confirmQuit(net.minecraft.client.Minecraft mc, PauseScreen pause) {
		if (!net.emberleaf.client.config.Modules.on("confirm_quit")) return;
		net.minecraft.client.gui.components.Button original = ((net.emberleaf.client.mixin.PauseScreenAccessor) pause).emberleaf$disconnectButton();
		if (original == null) return;
		original.visible = false;
		var replacement = net.minecraft.client.gui.components.Button.builder(original.getMessage(), b ->
			mc.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
				if (yes) {
					original.visible = true;
					original.onPress(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0));
				} else {
					mc.gui.setScreen(pause);
				}
			}, Component.literal("Wirklich verlassen?"), Component.literal(mc.isLocalServer()
				? "Die Welt wird gespeichert und du kehrst ins Hauptmenü zurück." : "Du trennst die Verbindung zum Server."))))
			.bounds(original.getX(), original.getY(), original.getWidth(), original.getHeight()).build();
		Screens.getWidgets(pause).add(replacement);
	}
}
package net.emberleaf.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.emberleaf.client.feature.Auras;
import net.emberleaf.client.feature.Contrails;
import net.emberleaf.client.feature.Emotes;
import net.emberleaf.client.feature.Pets;
import net.emberleaf.client.feature.Zoom;
import net.emberleaf.client.friends.FriendsSync;
import net.emberleaf.client.hud.EmberHud;
import net.emberleaf.client.menu.EmberButton;
import net.emberleaf.client.menu.EmberleafSettingsScreen;
import net.emberleaf.client.menu.IslandRenderer;
import net.emberleaf.client.outfit.OutfitStore;
import net.emberleaf.client.render.CosmeticsLayer;
import net.emberleaf.client.totem.SkinTotemRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class EmberleafClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("emberleaf", "main"));

	private int ticks;

	@Override
	public void onInitializeClient() {
		KeyMapping zoom = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.zoom", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, CATEGORY));
		Zoom.init(zoom);
		Emotes.init(KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.emote", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY)));
		KeyMapping menu = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, CATEGORY));
		net.emberleaf.client.feature.Freelook.init(KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.freelook", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CATEGORY)));
		KeyMapping panorama = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.panorama", InputConstants.Type.KEYBOARD, InputConstants.KEY_F9, CATEGORY));
		KeyMapping tools = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.tools", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		KeyMapping lagerfeuer = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.party", InputConstants.Type.KEYBOARD, InputConstants.KEY_U, CATEGORY));
		KeyMapping playerMenu = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.emberleaf.player_menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY));
		net.emberleaf.client.feature.SpeechBubbles.register();
		net.emberleaf.client.feature.ChatExtras.register();
		net.emberleaf.client.friends.LagerfeuerCommands.register();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(
			mc -> net.emberleaf.client.feature.Playtime.save());

		// named elytras + minimap mob pictures from the Emberleaf texture pack (things 26.3 has no built-in support for)
		net.emberleaf.client.pack.PackFeatures.register();
		// Skin-Totem: the totem's item model (assets/minecraft/items/totem_of_undying.json) uses this renderer
		SpecialModelRenderers.ID_MAPPER.put(SkinTotemRenderer.ID, SkinTotemRenderer.Unbaked.MAP_CODEC);
		// the floating 3D island behind the main menu
		PictureInPictureRendererRegistry.register(ctx -> new IslandRenderer());
		PictureInPictureRendererRegistry.register(ctx -> new net.emberleaf.client.menu.live.LiveSceneRenderer());
		// pets walking next to their owners, elytra contrails
		LevelRenderEvents.COLLECT_SUBMITS.register(Pets::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(Contrails::render);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("emberleaf", "hud"), EmberHud::render);
		// messages and party news from the Lagerfeuer, top right (also over menus)
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("emberleaf", "lagerfeuer"),
			(g, delta) -> net.emberleaf.client.friends.Lagerfeuer.renderNotices(g));
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> ScreenEvents.afterExtract(screen).register(
			(s, g, mouseX, mouseY, delta) -> net.emberleaf.client.friends.Lagerfeuer.renderNotices(g)));

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new CosmeticsLayer(avatar));
			}
		});

		// leaf button in the pause menu opens the Emberleaf menu, the tools sit next to it
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof PauseScreen pause) {
				Screens.getWidgets(screen).add(new EmberButton(6, 6, 96, 20, Component.literal("Emberleaf"),
					b -> mc.gui.setScreen(new EmberleafSettingsScreen(screen))));
				Screens.getWidgets(screen).add(new EmberButton(106, 6, 96, 20, Component.literal("Werkzeuge"),
					b -> mc.gui.setScreen(new net.emberleaf.client.tools.ToolsScreen(screen))));
				int unread = net.emberleaf.client.friends.Lagerfeuer.unread();
				Screens.getWidgets(screen).add(new EmberButton(206, 6, 96, 20, Component.literal(unread > 0 ? "Lagerfeuer (" + unread + ")" : "Lagerfeuer"),
					b -> mc.gui.setScreen(new net.emberleaf.client.menu.LagerfeuerScreen(screen, null))));
				confirmQuit(mc, pause);
			}
		});

		OutfitStore.reloadIfChanged();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (++ticks % 20 == 0) OutfitStore.reloadIfChanged();
			while (menu.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new EmberleafSettingsScreen(null));
			}
			while (tools.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new net.emberleaf.client.tools.ToolsScreen(null));
			}
			while (lagerfeuer.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new net.emberleaf.client.menu.LagerfeuerScreen(null, null));
			}
			while (playerMenu.consumeClick()) {
				// the player you look at (up to a few blocks away)
				if (mc.gui.screen() == null && mc.crosshairPickEntity instanceof net.minecraft.world.entity.player.Player target && target != mc.player) {
					mc.gui.setScreen(new net.emberleaf.client.menu.PlayerMenuScreen(target.getUUID(), target.getGameProfile().name(), playerMenu));
				}
			}
			while (panorama.consumeClick()) {
				if (mc.gui.screen() == null && mc.player != null && net.emberleaf.client.config.Modules.on("panorama")) {
					mc.gui.hud.getChat().addClientSystemMessage(mc.grabPanoramixScreenshot(mc.gameDirectory));
				}
			}
			net.emberleaf.client.feature.Playtime.tick(mc);
			net.emberleaf.client.feature.Freelook.tick(mc);
			net.emberleaf.client.feature.ChatExtras.tick(mc);
			net.emberleaf.client.feature.Performance.tick(mc);
			Zoom.tick(mc);
			Emotes.tick(mc);
			Pets.tick(mc);
			Auras.tick(mc);
			Contrails.tick(mc);
			EmberHud.tick(mc);
			net.emberleaf.client.feature.Minimap.tick(mc);
			FriendsSync.tick(mc);
		});
	}

	/** "Verlassen bestätigen": the pause menu's disconnect button asks first. */
	private static void confirmQuit(net.minecraft.client.Minecraft mc, PauseScreen pause) {
		if (!net.emberleaf.client.config.Modules.on("confirm_quit")) return;
		net.minecraft.client.gui.components.Button original = ((net.emberleaf.client.mixin.PauseScreenAccessor) pause).emberleaf$disconnectButton();
		if (original == null) return;
		original.visible = false;
		var replacement = net.minecraft.client.gui.components.Button.builder(original.getMessage(), b ->
			mc.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
				if (yes) {
					original.visible = true;
					original.onPress(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0));
				} else {
					mc.gui.setScreen(pause);
				}
			}, Component.literal("Wirklich verlassen?"), Component.literal(mc.isLocalServer()
				? "Die Welt wird gespeichert und du kehrst ins Hauptmenü zurück." : "Du trennst die Verbindung zum Server."))))
			.bounds(original.getX(), original.getY(), original.getWidth(), original.getHeight()).build();
		Screens.getWidgets(pause).add(replacement);
	}
}
