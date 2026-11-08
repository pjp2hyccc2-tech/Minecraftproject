package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.network.forge.ClientboundHandlers;
import com.eternalrealm.ui.hud.CharacterHudOverlay;
import com.eternalrealm.ui.hud.CombatHudOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only mod-bus wiring. {@code value = Dist.CLIENT} guarantees this class (and everything it references) is never
 * loaded on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientboundHandlers.setCharacterSnapshotHandler(ClientCharacterState::apply);
        ClientboundHandlers.setCombatStatsHandler(ClientCombatState::onStats);
        ClientboundHandlers.setCombatStatusHandler(ClientCombatState::onStatus);
        ClientboundHandlers.setDamageNumberHandler(ClientCombatState::onDamageNumber);
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "character_hud", new CharacterHudOverlay());
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "combat_hud", new CombatHudOverlay());
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientKeyMappings.OPEN_CHARACTER);
        event.register(ClientKeyMappings.HEAVY_ATTACK);
        event.register(ClientKeyMappings.DODGE);
        event.register(ClientKeyMappings.WEAPON_SKILL);
    }
}
