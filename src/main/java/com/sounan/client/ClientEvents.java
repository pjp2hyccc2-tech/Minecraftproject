package com.sounan.client;

import com.sounan.Sounan;
import com.sounan.SounanConfig;
import com.sounan.client.gui.PhoneScreen;
import com.sounan.game.GameManager;
import com.sounan.item.GameItem;
import com.sounan.network.C2S;
import com.sounan.network.ModNetwork;
import com.sounan.registry.ModDimensions;
import com.sounan.world.TerrainData;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client events: the state clock, shader uniforms, fog, camera, the vanilla HUD and music, input locks. */
@Mod.EventBusSubscriber(modid = Sounan.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean lastShaders;
    private static int shaderResend;

    private ClientEvents() {}

    private static boolean onMountain() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && ModDimensions.isMountain(mc.level);
    }

    /** Input is taken away during the intro, while being taken, and in the ending. */
    public static boolean locked() {
        return (ClientState.introTicks >= 0 && ClientState.introTicks < 20 * 15) || ClientState.flag(GameManager.F_CAPTURED)
                || ClientState.jumpscareTicks >= 0 || ClientState.flag(GameManager.F_ENDING);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        AutoShots.tick(mc);
        com.sounan.client.cine.Trailer.tick(mc);
        if (mc.level == null || mc.player == null) return;
        if (!mc.isPaused()) ClientState.tick();
        boolean here = ModDimensions.isMountain(mc.level);
        if (here) {
            // fear and danger reach the shader pack as rainStrength / thunderStrength (the biome has no rain)
            boolean shaders = ShaderCompat.active();
            mc.level.setRainLevel(shaders ? Mth.clamp(ClientState.fearShown / 100.0f, 0.0f, 1.0f) : 0.0f);
            mc.level.setThunderLevel(shaders ? Mth.clamp(ClientState.dangerShown, 0.0f, 1.0f) : 0.0f);
            if (shaders != lastShaders || ++shaderResend > 200) {
                lastShaders = shaders;
                shaderResend = 0;
                ModNetwork.sendToServer(new C2S.Action(C2S.SHADERS, shaders ? 1 : 0));
            }
            if (ClientState.flag(GameManager.F_EXHAUSTED)) {
                if (mc.player.isSprinting()) mc.player.setSprinting(false);
                mc.options.keySprint.setDown(false);
            }
            // nobody looks behind them for free here
            if (!mc.player.isCreative() && !mc.player.isSpectator() && mc.options.getCameraType() != CameraType.FIRST_PERSON
                    && !ClientState.cinematic()) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            // the GPS breadcrumb trail
            if (mc.player.tickCount % 20 == 0) {
                double x = mc.player.getX(), z = mc.player.getZ();
                double[] last = ClientState.TRAIL.isEmpty() ? null : ClientState.TRAIL.get(ClientState.TRAIL.size() - 1);
                if (last == null || (last[0] - x) * (last[0] - x) + (last[1] - z) * (last[1] - z) > 16.0) {
                    ClientState.TRAIL.add(new double[]{x, z});
                    if (ClientState.TRAIL.size() > 1500) ClientState.TRAIL.remove(0);
                }
            }
            while (ClientSetup.PHONE_KEY.consumeClick()) {
                if (mc.screen == null && hasPhone(mc)) mc.setScreen(new PhoneScreen());
            }
            while (ClientSetup.MEMO_KEY.consumeClick()) {
                if (mc.screen == null && hasPhone(mc)) mc.setScreen(new PhoneScreen().memo());
            }
            if (ClientState.checkpointTicks == 1) HorrorAudio.onCheckpoint();
        }
        HorrorAudio.tick(mc);
    }

    private static boolean hasPhone(Minecraft mc) {
        for (var s : mc.player.getInventory().items) {
            GameItem.Kind k = GameItem.kindOf(s);
            if (k != null && k.phone()) return true;
        }
        GameItem.Kind k = GameItem.kindOf(mc.player.getOffhandItem());
        return k != null && k.phone();
    }

    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (!onMountain() || !locked()) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!onMountain() || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float d = ClientState.dangerShown;
        boolean tunnel = ClientState.area == TerrainData.AREA_TUNNEL;
        event.setRed(tunnel ? 0.008f : 0.026f + 0.03f * d);
        event.setGreen(tunnel ? 0.008f : 0.032f);
        event.setBlue(tunnel ? 0.010f : 0.040f);
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog event) {
        if (!onMountain() || event.getType() != FogType.NONE || ShaderCompat.active()) return;
        float fear = ClientState.fearShown / 100.0f;
        float far = Mth.lerp(fear, 34.0f, 18.0f) - 6.0f * ClientState.dangerShown;
        if (ClientState.area == TerrainData.AREA_TUNNEL) far = 22.0f;
        if (ClientState.loopTicks >= 0) far = Math.min(far, 6.0f + ClientState.loopTicks * 0.5f);
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) far *= 1.5f;
        event.setNearPlaneDistance(far * 0.05f);
        event.setFarPlaneDistance(far);
        event.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (!onMountain() || com.sounan.client.cine.Trailer.active()) return;
        float strength = (float) SounanConfig.screenEffects() * (SounanConfig.flashReduction() ? 0.4f : 1.0f);
        float t = (float) (ClientState.clientTicks + event.getPartialTick());
        float yaw = 0, pitch = 0, roll = 0;
        if (ClientState.shakeTicks > 0) {
            float f = ClientState.shake * Math.min(1.0f, ClientState.shakeTicks / 20.0f);
            yaw += Mth.sin(t * 2.7f) * f * 1.4f;
            pitch += Mth.cos(t * 3.3f) * f * 1.4f;
            roll += Mth.sin(t * 1.9f) * f * 2.0f;
        }
        float fear = ClientState.fearShown / 100.0f;
        if (fear > 0.7f) {
            float k = (fear - 0.7f) / 0.3f;
            roll += Mth.sin(t * 0.37f) * 0.5f * k + Mth.sin(t * 7.1f) * 0.1f * k;
            pitch += Mth.sin(t * 0.23f) * 0.3f * k;
        }
        if (ClientState.dangerShown > 0.5f) roll += Mth.sin(t * 9.0f) * 0.22f * (ClientState.dangerShown - 0.5f) * 2.0f;
        if (ClientState.introTicks >= 0 && ClientState.introTicks < 20 * 16) {
            // waking up on the ground: the head lolls
            float k = Math.max(0.0f, 1.0f - (ClientState.introTicks - 220) / 100.0f);
            roll += 24.0f * k;
            pitch += 30.0f * k;
        }
        event.setYaw(event.getYaw() + yaw * strength);
        event.setPitch(event.getPitch() + pitch * strength);
        event.setRoll(event.getRoll() + roll * strength);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (!onMountain() || !event.usedConfiguredFov() || com.sounan.client.cine.Trailer.active()) return;
        float fear = ClientState.fearShown / 100.0f;
        double mul = 1.0 - 0.07 * ClientState.dangerShown - 0.05 * Math.max(0.0f, fear - 0.7f) / 0.3f;
        if (HorrorAudio.isCamera(Minecraft.getInstance())) mul *= 0.88;
        event.setFOV(event.getFOV() * mul);
    }

    @SubscribeEvent
    public static void hideVanillaHud(RenderGuiOverlayEvent.Pre event) {
        if (!onMountain()) return;
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())) {
            event.setCanceled(true);
            return;
        }
        if ((ClientState.cinematic() || locked()) && (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.CROSSHAIR.id())
                || id.equals(VanillaGuiOverlay.ITEM_NAME.id()))) {
            event.setCanceled(true);
        }
        if (HorrorAudio.isCamera(Minecraft.getInstance()) && id.equals(VanillaGuiOverlay.CROSSHAIR.id())) event.setCanceled(true);
    }

    private static net.minecraft.client.resources.sounds.SoundInstance titleMusic;

    /** Vanilla background music has no place on the mountain; the menus get the title theme instead. */
    @SubscribeEvent
    public static void music(PlaySoundEvent event) {
        if (event.getSound() == null || event.getSound().getSource() != SoundSource.MUSIC) return;
        if (event.getSound().getLocation().getNamespace().equals(Sounan.MODID)) return;
        Minecraft mc = Minecraft.getInstance();
        if (onMountain()) {
            event.setSound(null);
        } else if (mc.level == null) {
            if (titleMusic != null && mc.getSoundManager().isActive(titleMusic)) {
                event.setSound(null);
            } else {
                titleMusic = net.minecraft.client.resources.sounds.SimpleSoundInstance.forMusic(com.sounan.registry.ModSounds.MUSIC_TITLE.get());
                event.setSound(titleMusic);
            }
        }
    }

    @SubscribeEvent
    public static void screenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen title) {
            AutoShots.onTitle(title);
            com.sounan.client.cine.Trailer.onTitle();
        }
    }

    @SubscribeEvent
    public static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        lastShaders = !ShaderCompat.active();
        String key = ShaderPackInstaller.takePendingMessage();
        if (key != null) event.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable(key), false);
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
        HorrorAudio.stopAll();
    }
}
