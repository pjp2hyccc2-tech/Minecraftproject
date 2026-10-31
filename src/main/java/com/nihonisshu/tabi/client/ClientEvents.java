package com.nihonisshu.tabi.client;

import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.client.screen.JournalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only game events on the Forge bus. */
@Mod.EventBusSubscriber(modid = TabiMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Bubbles.tick();
        AmbientSounds.tick(mc);
        Grading.tick(mc);
        Hud.tick(mc);
        ShotTour.tick(mc);
        while (ClientSetup.KEY_JOURNAL.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new JournalScreen());
        }
        while (ClientSetup.KEY_GRADE.consumeClick()) Grading.cycle(mc);
    }

    @SubscribeEvent
    public static void camera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        CameraSway.apply(e);
    }

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) Grading.apply();
    }

    @SubscribeEvent
    public static void screen(ScreenEvent.Opening e) {
        ShotTour.onScreen(e.getScreen());
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientState.data = new CompoundTag();
    }
}
