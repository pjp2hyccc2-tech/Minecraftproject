package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.ui.hud.DamageNumberRenderer;
import com.eternalrealm.ui.screen.CharacterScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only game-bus handlers: key input, combat input, world rendering and clearing state when leaving a
 * world/server.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            // Before vanilla processes key presses this tick, so weapon clicks never reach the vanilla attack.
            ClientCombatController.onTickStart();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (ClientKeyMappings.OPEN_CHARACTER.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new CharacterScreen());
            }
        }
    }

    /** No vanilla attack or block breaking while holding a weapon (e.g. holding the button on a block). */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && ClientCombatController.holdsWeapon()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            DamageNumberRenderer.render(event);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Never show one world's/server's character in the next one.
        ClientCharacterState.clear();
        ClientCombatState.clear();
    }
}
