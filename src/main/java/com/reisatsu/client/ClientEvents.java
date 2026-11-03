package com.reisatsu.client;

import com.reisatsu.Reisatsu;
import com.reisatsu.ReisatsuConfig;
import com.reisatsu.client.gui.AlbumScreen;
import com.reisatsu.game.GameManager;
import com.reisatsu.network.C2S;
import com.reisatsu.network.ModNetwork;
import com.reisatsu.registry.ModKeys;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Layout;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Forge-bus client events: the clock, input (shutter, zoom, breath, album), the view, fog, shader channels, HUD, music. */
@Mod.EventBusSubscriber(modid = Reisatsu.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean lastShaders;
    private static int shaderResend;
    private static boolean breathDown;

    private ClientEvents() {}

    static boolean inRestaurant() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && ClientState.inGame();
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        AutoShots.tick(mc);
        if (mc.level == null || mc.player == null) return;
        if (!mc.isPaused()) {
            ClientState.tick();
            Dialogue.tick();
            PhotoView.tick();
        }
        Finder.tick(mc);
        if (!inRestaurant()) {
            HorrorAudio.tick(mc);
            return;
        }
        // fear and danger reach the shader pack as rainStrength / thunderStrength (nothing falls here)
        boolean shaders = ShaderCompat.active();
        mc.level.setRainLevel(shaders ? Mth.clamp(ClientState.fearShown, 0.0f, 1.0f) : 0.0f);
        mc.level.setThunderLevel(shaders ? Mth.clamp(ClientState.dangerShown, 0.0f, 1.0f) : 0.0f);
        if (shaders != lastShaders || ++shaderResend > 200) {
            lastShaders = shaders;
            shaderResend = 0;
            ModNetwork.sendToServer(new C2S.Action(C2S.SHADERS, shaders ? 1 : 0));
        }
        // first person only: nobody looks at themselves here
        if (!mc.player.isCreative() && !mc.player.isSpectator() && mc.options.getCameraType() != CameraType.FIRST_PERSON && !AutoShots.active()) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        // hold your breath (V)
        boolean down = ModKeys.BREATH.isDown() && !ClientState.locked();
        if (down != breathDown) {
            breathDown = down;
            ModNetwork.sendToServer(new C2S.Action(C2S.BREATH, down ? 1 : 0));
            if (down) ClientPacketHandler.ui(ModSounds.PLAYER_BREATH_HOLD.get(), 0.7f, 1.0f);
        }
        while (ModKeys.ALBUM.consumeClick()) {
            if (mc.screen == null && !ClientState.locked()) mc.setScreen(new AlbumScreen());
        }
        // inside a locker you can only look out through the slits
        if (ClientState.lockerFacing >= 0) {
            float base = Direction.from2DDataValue(ClientState.lockerFacing).toYRot();
            float d = Mth.wrapDegrees(mc.player.getYRot() - base);
            if (Math.abs(d) > 62.0f) mc.player.setYRot(base + Math.signum(d) * 62.0f);
            mc.player.setXRot(Mth.clamp(mc.player.getXRot(), -30.0f, 35.0f));
        }
        // your breath clouds in the walk-in fridge
        if (ClientState.clientTicks % 45 == 0 && !ClientState.flag(GameManager.F_BREATH)) {
            Layout.Room r = Layout.roomAt(mc.player.blockPosition());
            if (r != null && r.key().equals("fridge")) {
                var look = mc.player.getViewVector(1.0f);
                mc.level.addParticle(com.reisatsu.registry.ModParticles.FROST_BREATH.get(), mc.player.getX() + look.x * 0.35,
                        mc.player.getEyeY() - 0.1, mc.player.getZ() + look.z * 0.35, look.x * 0.03, 0.005, look.z * 0.03);
            }
        }
        HorrorAudio.tick(mc);
    }

    // ================================================================================================ input
    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!inRestaurant()) return;
        if (ClientState.locked()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (ClientState.hiding() && event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            ModNetwork.sendToServer(new C2S.Action(C2S.UNHIDE, 0));
            return;
        }
        if (event.isAttack() && Finder.active(mc)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            Finder.shutter(mc);
            return;
        }
        if (event.isAttack() && PhotoView.active()) PhotoView.dismiss();
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !inRestaurant()) return;
        if (Finder.active(mc)) {
            Finder.scroll(event.getScrollDelta());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || event.getAction() != GLFW.GLFW_PRESS) return;
        if (ClientState.introTicks >= 0 && ClientState.introSkippable
                && (event.getKey() == GLFW.GLFW_KEY_SPACE || event.getKey() == GLFW.GLFW_KEY_ENTER)) {
            ModNetwork.sendToServer(new C2S.Action(C2S.INTRO_SKIP, 0));
        }
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent event) {
        if (!inRestaurant()) return;
        var in = event.getInput();
        if (ClientState.locked() || ClientState.hiding()) {
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            if (ClientState.locked()) in.shiftKeyDown = false;
        }
    }

    // ================================================================================================ the view
    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!inRestaurant()) return;
        if (Finder.active(mc) || ClientState.hiding() || ClientState.locked()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void blockOverlay(RenderBlockScreenEffectEvent event) {
        if (inRestaurant() && ClientState.hiding()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (!inRestaurant() || !event.usedConfiguredFov() || AutoShots.overridesFov()) return;
        if (Finder.active(mc)) {
            event.setFOV(Finder.fov((float) event.getPartialTick()));
            return;
        }
        double mul = 1.0 - 0.06 * ClientState.dangerShown - 0.04 * Math.max(0.0f, ClientState.fearShown - 0.7f) / 0.3f;
        event.setFOV(event.getFOV() * mul);
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (!inRestaurant() || AutoShots.active()) return;
        float strength = (float) ReisatsuConfig.screenEffects() * (ReisatsuConfig.flashReduction() ? 0.4f : 1.0f);
        float t = (float) (ClientState.clientTicks + event.getPartialTick());
        float yaw = 0, pitch = 0, roll = 0;
        if (ClientState.shakeTicks > 0) {
            float f = ClientState.shake * Math.min(1.0f, ClientState.shakeTicks / 20.0f);
            yaw += Mth.sin(t * 2.7f) * f * 1.2f;
            pitch += Mth.cos(t * 3.3f) * f * 1.2f;
            roll += Mth.sin(t * 1.9f) * f * 1.6f;
        }
        float fear = ClientState.fearShown;
        if (fear > 0.7f) {
            float k = (fear - 0.7f) / 0.3f;
            roll += Mth.sin(t * 0.37f) * 0.45f * k + Mth.sin(t * 7.1f) * 0.08f * k;
            pitch += Mth.sin(t * 0.23f) * 0.25f * k;
        }
        if (Finder.active(Minecraft.getInstance())) {
            // the camera is heavy: a slow handheld sway, focus breathing
            yaw += Mth.sin(t * 0.07f) * 0.25f;
            pitch += Mth.sin(t * 0.11f + 1.0f) * 0.18f;
        }
        event.setYaw(event.getYaw() + yaw * strength);
        event.setPitch(event.getPitch() + pitch * strength);
        event.setRoll(event.getRoll() + roll * strength);
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!inRestaurant() || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float d = ClientState.dangerShown;
        event.setRed(0.030f + 0.03f * d);
        event.setGreen(0.036f);
        event.setBlue(0.050f);
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (!inRestaurant() || event.getType() != FogType.NONE || ShaderCompat.active()) return;
        if (mc.player != null && mc.player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)) return;
        boolean outside = Layout.roomAt(mc.player.blockPosition()) == null;
        float far = (outside ? 56.0f : 40.0f) - 14.0f * ClientState.fearShown - 6.0f * ClientState.dangerShown;
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) far *= 1.5f;
        event.setNearPlaneDistance(far * 0.12f);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    // ================================================================================================ HUD and the frame grab
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void guiPre(RenderGuiEvent.Pre event) {
        PhotoCapture.onRenderGuiPre(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void hideVanillaHud(RenderGuiOverlayEvent.Pre event) {
        if (!inRestaurant()) return;
        Minecraft mc = Minecraft.getInstance();
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id()) || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())) {
            event.setCanceled(true);
            return;
        }
        boolean hideBar = ClientState.locked() || ClientState.hiding() || Finder.active(mc) || ClientState.exorcismStage >= 0;
        if (hideBar && (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.ITEM_NAME.id())
                || id.equals(VanillaGuiOverlay.RECORD_OVERLAY.id()))) {
            event.setCanceled(true);
        }
        if ((hideBar || Finder.active(mc)) && id.equals(VanillaGuiOverlay.CROSSHAIR.id())) event.setCanceled(true);
    }

    // ================================================================================================ music, menus, connection
    private static net.minecraft.client.resources.sounds.SoundInstance titleMusic;

    /** No vanilla music at HIDAMARI; the menus get the title theme. */
    @SubscribeEvent
    public static void music(PlaySoundEvent event) {
        if (event.getSound() == null || event.getSound().getSource() != SoundSource.MUSIC) return;
        if (event.getSound().getLocation().getNamespace().equals(Reisatsu.MODID)) return;
        Minecraft mc = Minecraft.getInstance();
        if (inRestaurant()) {
            event.setSound(null);
        } else if (mc.level == null) {
            if (titleMusic != null && mc.getSoundManager().isActive(titleMusic)) {
                event.setSound(null);
            } else {
                titleMusic = net.minecraft.client.resources.sounds.SimpleSoundInstance.forMusic(ModSounds.MUSIC_TITLE.get());
                event.setSound(titleMusic);
            }
        }
    }

    @SubscribeEvent
    public static void screenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen title) AutoShots.onTitle(title);
    }

    @SubscribeEvent
    public static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        lastShaders = !ShaderCompat.active();
        Photos.forgetMissing();
        String key = ShaderPackInstaller.takePendingMessage();
        if (key != null) event.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable(key), false);
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
        Dialogue.clear();
        Finder.reset();
        HorrorAudio.stopAll();
    }

    /** True while a game-wide flag is set (debug overlays). */
    static boolean peaceful() {
        return ClientState.flag(GameManager.F_PEACEFUL);
    }
}
