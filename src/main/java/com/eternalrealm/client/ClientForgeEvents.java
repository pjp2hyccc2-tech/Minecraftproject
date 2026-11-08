package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.ui.hud.DamageNumberRenderer;
import com.eternalrealm.ui.screen.JournalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
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
            CameraShake.tick();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (ClientKeyMappings.OPEN_CHARACTER.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                JournalScreen.open();
            }
        }
        com.eternalrealm.ui.dialogue.ClientDialogue.tick();
        KeyConflicts.resolveOnce();
    }

    /**
     * No vanilla attack or block breaking while holding a weapon (e.g. holding the button on a block), and no off-hand
     * use when right click belongs to the guard (otherwise a refused guard would fall through to e.g. an off-hand
     * shield). Sneaking + right click still uses the off-hand item.
     */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        boolean cancel = (event.isAttack() && ClientCombatController.holdsWeapon())
                || (event.isUseItem() && event.getHand() == InteractionHand.OFF_HAND
                && ClientCombatController.blocksOffHandUse());
        if (cancel) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        CameraShake.apply(event);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            DamageNumberRenderer.render(event);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
        DualWield.onRenderHand(event);
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        DualWield.onRenderPlayerPre(event);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Never show one world's/server's character in the next one.
        ClientCharacterState.clear();
        ClientCombatState.clear();
        ClientUiState.clear();
        CombatAnimations.clear();
        CameraShake.clear();
        com.eternalrealm.ui.dialogue.ClientDialogue.clear();
    }
}
