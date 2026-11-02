package com.meikon.client;

import com.meikon.Meikon;
import com.meikon.MeikonConfig;
import com.meikon.client.gui.JournalScreen;
import com.meikon.registry.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client events: easing, fog, camera, hiding the vanilla HUD, silencing vanilla music. */
@Mod.EventBusSubscriber(modid = Meikon.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    private static boolean inKakuriyo() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && ModDimensions.isKakuriyo(mc.level);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        AutoShots.tick(mc);
        ClientState.clientTicks++;
        if (mc.level == null || mc.player == null) return;
        ClientState.fearPrev = ClientState.fearShown;
        ClientState.dangerPrev = ClientState.dangerShown;
        ClientState.fearShown += (ClientState.fear - ClientState.fearShown) * 0.12f;
        ClientState.dangerShown += (ClientState.danger - ClientState.dangerShown) * 0.2f;
        if (!mc.isPaused()) {
            if (ClientState.jumpscareTicks >= 0 && ++ClientState.jumpscareTicks > 60) ClientState.jumpscareTicks = -1;
            if (ClientState.deathTicks >= 0 && ++ClientState.deathTicks > 160) ClientState.deathTicks = -1;
            if (ClientState.ofudaTicks >= 0 && ++ClientState.ofudaTicks > 40) ClientState.ofudaTicks = -1;
            if (ClientState.introTicks >= 0 && ++ClientState.introTicks > ClientState.IntroTiming.END) ClientState.introTicks = -1;
            if (ClientState.titleTicks >= 0 && ++ClientState.titleTicks > 140) ClientState.titleTicks = -1;
            if (ClientState.spottedTicks >= 0 && ++ClientState.spottedTicks > 12) ClientState.spottedTicks = -1;
            if (ClientState.stingerTicks >= 0 && ++ClientState.stingerTicks > 14) ClientState.stingerTicks = -1;
            if (ClientState.shadowTicks >= 0 && ++ClientState.shadowTicks > 24) ClientState.shadowTicks = -1;
            if (ClientState.flickerTicks >= 0 && ++ClientState.flickerTicks > 22) ClientState.flickerTicks = -1;
            if (ClientState.shakeTicks > 0) ClientState.shakeTicks--;
            if (ClientState.bellFlash > 0) ClientState.bellFlash--;
        }
        boolean here = ModDimensions.isKakuriyo(mc.level);
        if (here) {
            // carry fear and danger to shader packs (rainStrength / thunderStrength); the biome has no rain
            mc.level.setRainLevel(Mth.clamp(ClientState.fearShown / 100.0f, 0.0f, 1.0f));
            mc.level.setThunderLevel(Mth.clamp(ClientState.dangerShown, 0.0f, 1.0f));
            if (ClientState.exhausted && mc.player.isSprinting()) mc.player.setSprinting(false);
            if (ClientState.exhausted) mc.options.keySprint.setDown(false);
            if (ClientState.captured) mc.options.keyUp.setDown(false);
            while (ClientSetup.JOURNAL_KEY.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new JournalScreen());
            }
        }
        HorrorAudio.tick(mc);
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!inKakuriyo() || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float d = ClientState.dangerShown;
        event.setRed(0.030f + 0.05f * d);
        event.setGreen(0.010f);
        event.setBlue(0.014f);
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog event) {
        if (!inKakuriyo() || event.getType() != FogType.NONE || ShaderCompat.active()) return;
        float fear = ClientState.fearShown / 100.0f;
        float far = Mth.lerp(fear, 30.0f, 14.0f) - 4.0f * ClientState.dangerShown;
        if (ClientState.hidden) far = Math.min(far, 12.0f);
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) far *= 1.6f;
        event.setNearPlaneDistance(far * 0.04f);
        event.setFarPlaneDistance(far);
        event.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (!inKakuriyo()) return;
        float strength = (float) MeikonConfig.screenEffects() * (MeikonConfig.flashReduction() ? 0.4f : 1.0f);
        float t = (float) (ClientState.clientTicks + event.getPartialTick());
        float yaw = 0, pitch = 0, roll = 0;
        if (ClientState.shakeTicks > 0) {
            float f = ClientState.shakeIntensity * ClientState.shakeTicks / (float) Math.max(1, ClientState.shakeTotal);
            yaw += Mth.sin(t * 2.7f) * f * 1.4f;
            pitch += Mth.cos(t * 3.3f) * f * 1.4f;
            roll += Mth.sin(t * 1.9f) * f * 2.0f;
        }
        float fear = ClientState.fearShown / 100.0f;
        if (fear > 0.72f) {
            float k = (fear - 0.72f) / 0.28f;
            roll += Mth.sin(t * 0.37f) * 0.6f * k + Mth.sin(t * 7.1f) * 0.12f * k;
            pitch += Mth.sin(t * 0.23f) * 0.35f * k;
        }
        if (ClientState.dangerShown > 0.5f) roll += Mth.sin(t * 9.0f) * 0.25f * (ClientState.dangerShown - 0.5f) * 2.0f;
        event.setYaw(event.getYaw() + yaw * strength);
        event.setPitch(event.getPitch() + pitch * strength);
        event.setRoll(event.getRoll() + roll * strength);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (!inKakuriyo() || !event.usedConfiguredFov()) return;
        float fear = ClientState.fearShown / 100.0f;
        double mul = 1.0 - 0.07 * ClientState.dangerShown - 0.05 * Math.max(0.0f, fear - 0.7f) / 0.3f;
        event.setFOV(event.getFOV() * mul);
    }

    @SubscribeEvent
    public static void hideVanillaHud(RenderGuiOverlayEvent.Pre event) {
        if (!inKakuriyo()) return;
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())) {
            event.setCanceled(true);
            return;
        }
        if (ClientState.cinematic() && (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.CROSSHAIR.id())
                || id.equals(VanillaGuiOverlay.ITEM_NAME.id()) || id.equals(VanillaGuiOverlay.RECORD_OVERLAY.id()))) {
            event.setCanceled(true);
        }
    }

    /** Vanilla background music has no place in the mansion. */
    @SubscribeEvent
    public static void music(PlaySoundEvent event) {
        if (!inKakuriyo() || event.getSound() == null) return;
        if (event.getSound().getSource() == SoundSource.MUSIC && !event.getSound().getLocation().getNamespace().equals(Meikon.MODID)) {
            event.setSound(null);
        }
    }

    @SubscribeEvent
    public static void screenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen title) AutoShots.onTitle(title);
    }

    @SubscribeEvent
    public static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        String key = ShaderPackInstaller.takePendingMessage();
        if (key != null) event.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable(key), false);
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
        HorrorAudio.stopAll();
    }
}
