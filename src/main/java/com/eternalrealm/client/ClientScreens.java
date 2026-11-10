package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.ui.hud.HudFrame;
import com.eternalrealm.ui.screen.JournalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Supplier;

/**
 * The bag and equipment screens behind the two HUD icons (and their keys: vanilla's inventory key for the bag,
 * {@link ClientKeyMappings#OPEN_EQUIPMENT} for the equipment). Until the game's own screens are installed
 * ({@link #setBagScreen}, {@link #setEquipmentScreen}) the bag is vanilla's inventory and the equipment opens the
 * character journal. Once a bag screen is installed, vanilla's inventory opens it instead (outside creative mode).
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientScreens {
    private static volatile Supplier<Screen> bag;
    private static volatile Supplier<Screen> equipment;

    private ClientScreens() {
    }

    /** Installs the game's bag screen (client setup). */
    public static void setBagScreen(Supplier<Screen> screen) {
        bag = screen;
    }

    /** Installs the game's equipment screen (client setup). */
    public static void setEquipmentScreen(Supplier<Screen> screen) {
        equipment = screen;
    }

    public static void openBag() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Supplier<Screen> factory = bag;
        minecraft.setScreen(factory != null ? factory.get() : new InventoryScreen(minecraft.player));
    }

    public static void openEquipment() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Supplier<Screen> factory = equipment;
        if (factory != null) {
            minecraft.setScreen(factory.get());
        } else {
            JournalScreen.open();
        }
    }

    /** Vanilla's inventory (E) opens the game's bag once one is installed. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Supplier<Screen> factory = bag;
        if (factory != null && event.getNewScreen() instanceof InventoryScreen
                && HudFrame.mmo(Minecraft.getInstance().player)) {
            Screen replacement = factory.get();
            if (replacement != null && !(replacement instanceof InventoryScreen)) {
                event.setNewScreen(replacement);
            }
        }
    }
}
