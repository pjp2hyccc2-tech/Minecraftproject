package com.eternalrealm.ui.settings;

import com.eternalrealm.core.ModInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Where the game's settings ({@link SettingsScreen}) are opened: a button in the top-left corner of the pause menu,
 * and the Config button of the Mods list.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SettingsAccess {
    private SettingsAccess() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen pause) {
            event.addListener(Button.builder(Component.translatable("menu.eternalrealm.settings"),
                    button -> Minecraft.getInstance().setScreen(new SettingsScreen(pause)))
                    .bounds(4, 4, 150, 20).build());
        }
    }

    /** The Mods list's Config button opens the settings (client setup, on the main thread). */
    public static void registerConfigScreen() {
        ModList.get().getModContainerById(ModInfo.MOD_ID).ifPresent(container -> container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new SettingsScreen(parent))));
    }
}
