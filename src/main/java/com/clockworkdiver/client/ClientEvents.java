package com.clockworkdiver.client;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.combat.Quality;
import com.clockworkdiver.combat.Tempo;
import com.clockworkdiver.network.C2S;
import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.registry.ModDimensions;
import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ClockworkDiver.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        AutoShots.tick(mc);
        if (mc.level == null || mc.player == null) return;

        if (ClientState.hintTicks > 0) {
            ClientState.hintTicks--;
        } else if (!ClientState.HINTS.isEmpty()) {
            ClientState.currentHint = ClientState.HINTS.poll();
            ClientState.hintTicks = ClientState.HINT_DURATION;
        } else {
            ClientState.currentHint = null;
        }
        if (ClientState.transitionKind >= 0 && ++ClientState.transitionTicks > ClientState.transitionDuration(ClientState.transitionKind)) {
            ClientState.transitionKind = -1;
        }
        if (ClientState.clickTicks > 0) ClientState.clickTicks--;
        if (ClientState.objectiveFlash > 0) ClientState.objectiveFlash--;

        if (ClientState.inDive && !ModDimensions.isInterior(mc.level) && ClientState.transitionKind != 0) {
            // Left the clock some other way (commands, death screen...).
            if (mc.level.getGameTime() % 40 == 0) ClientState.leaveDive();
        }

        if (ClientState.inDive && !mc.isPaused() && ModDimensions.isInterior(mc.level)) {
            long beat = Math.floorDiv(mc.level.getGameTime() - ClientState.anchor, ClientState.period);
            if (beat != ClientState.lastBeat) {
                boolean first = ClientState.lastBeat == Long.MIN_VALUE;
                ClientState.lastBeat = beat;
                if (!first) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(
                            Math.floorMod(beat, 2) == 0 ? ModSounds.TICK.get() : ModSounds.TOCK.get(), 1.0f, 0.45f));
                }
            }
            if (ClientSetup.ABORT_KEY.isDown() && mc.screen == null) {
                if (++ClientState.abortHold == 30) ModNetwork.sendToServer(new C2S.AbortDive());
            } else {
                ClientState.abortHold = 0;
            }
        } else {
            ClientState.abortHold = 0;
        }
    }

    /** Judges attack / use clicks against the beat while inside a clock. */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientState.inDive || mc.player == null || mc.level == null) return;
        if (!event.isAttack() && !event.isUseItem()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!mc.player.getMainHandItem().is(ModItems.TINKER_WRENCH.get())) return;
        long now = mc.level.getGameTime();
        if (now - ClientState.lastBeatInputTick < 3) return;
        ClientState.lastBeatInputTick = now;
        Quality q = Tempo.judge(now, mc.getFrameTime(), ClientState.anchor, ClientState.period, ClientState.lensLevel);
        ModNetwork.sendToServer(new C2S.BeatInput(q, event.isAttack()));
        ClientState.clickQuality = q.ordinal();
        ClientState.clickTicks = 14;
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen title) AutoShots.onTitle(title);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
    }
}
