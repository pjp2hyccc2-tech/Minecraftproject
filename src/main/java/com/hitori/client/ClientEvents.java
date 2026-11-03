package com.hitori.client;

import com.hitori.Hitori;
import com.hitori.HitoriConfig;
import com.hitori.client.gui.PrintoutScreen;
import com.hitori.entity.FixedCamera;
import com.hitori.game.GameManager;
import com.hitori.network.C2S;
import com.hitori.network.ModNetwork;
import com.hitori.registry.ModKeys;
import com.hitori.registry.ModSounds;
import com.hitori.world.Layout;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.FogRenderer;
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

/**
 * Forge-bus client events: the clock, input (breath V, the action key R, the camera app C / 1–4 / A–D / ESC, the
 * printout J, peeking), the view (first person, the closet's crouch, a fallen camera's tilt, fear), fog, the shader
 * channels (fear / danger as rain / thunder under a pack), the HUD, music.
 */
@Mod.EventBusSubscriber(modid = Hitori.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean lastShaders;
    private static int shaderResend;
    private static boolean breathDown;

    private ClientEvents() {}

    static boolean inGame() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && ClientState.inGame();
    }

    @SubscribeEvent
    public static void clientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        // the camera app eats the hotbar keys (1–4 choose a camera) and A / D (cycle)
        if (CameraApp.isOpen()) {
            for (int i = 0; i < mc.options.keyHotbarSlots.length; i++) {
                while (mc.options.keyHotbarSlots[i].consumeClick()) if (i < 4) CameraApp.select(i + 1);
            }
            while (mc.options.keyLeft.consumeClick()) CameraApp.cycle(-1);
            while (mc.options.keyRight.consumeClick()) CameraApp.cycle(1);
            while (mc.options.keyInventory.consumeClick()) {
            }
            while (mc.options.keyDrop.consumeClick()) {
            }
            while (mc.options.keySwapOffhand.consumeClick()) {
            }
        }
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
            PhoneUi.tick();
        }
        CameraApp.tick(mc);
        FrameCapture.tick(mc);
        Peek.tick(mc);
        if (!inGame()) {
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
        if (!mc.player.isCreative() && !mc.player.isSpectator() && mc.options.getCameraType() != CameraType.FIRST_PERSON && !AutoShots.active()) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        // hold your breath (V)
        boolean down = ModKeys.BREATH.isDown() && !ClientState.locked() && mc.screen == null;
        if (down != breathDown) {
            breathDown = down;
            ModNetwork.sendToServer(new C2S.Action(C2S.BREATH, down ? 1 : 0));
            if (down) ClientPacketHandler.ui(ModSounds.PLAYER_BREATH_HOLD.get(), 0.6f, 1.0f);
        }
        while (ModKeys.ACTION.consumeClick()) {
            if (mc.screen == null && !ClientState.locked() && !CameraApp.isOpen()) ModNetwork.sendToServer(new C2S.Action(C2S.ACTION, 0));
        }
        while (ModKeys.CAMERA.consumeClick()) {
            if (mc.screen != null) continue;
            if (CameraApp.isOpen()) CameraApp.close();
            else {
                int dev = Peek.heldScreen(mc);
                if (dev < 0) dev = has(mc, com.hitori.registry.ModItems.LAPTOP.get()) ? 0 : has(mc, com.hitori.registry.ModItems.PHONE.get())
                        || has(mc, com.hitori.registry.ModItems.PHONE_LIGHT.get()) ? 1 : -1;
                if (dev >= 0) CameraApp.open(dev);
                else ClientPacketHandler.thought("thought.hitori.need_screen");
            }
        }
        while (ModKeys.PRINTOUT.consumeClick()) {
            if (mc.screen == null && !CameraApp.isOpen()) mc.setScreen(new PrintoutScreen());
        }
        HorrorAudio.tick(mc);
    }

    private static boolean has(Minecraft mc, net.minecraft.world.item.Item item) {
        if (mc.player == null) return false;
        for (var s : mc.player.getInventory().items) if (s.is(item)) return true;
        return mc.player.getOffhandItem().is(item);
    }

    // ================================================================================================ input
    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!inGame()) return;
        if (ClientState.locked()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (CameraApp.isOpen()) {
            // the app: right click closes it; nothing reaches the world through the camera's eye
            event.setCanceled(true);
            event.setSwingHand(false);
            if (event.isUseItem()) CameraApp.close();
            return;
        }
        if (ClientState.hiding() && (event.isUseItem() || event.isAttack())) {
            // inside: Peek reads the held key (a click leaves, holding peeks)
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** ESC in the camera app or while peeking closes it instead of pausing. */
    @SubscribeEvent
    public static void screenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof PauseScreen) || !inGame()) return;
        Minecraft mc = Minecraft.getInstance();
        if (CameraApp.isOpen()) {
            CameraApp.close();
            event.setCanceled(true);
        } else if (Peek.active()) {
            Peek.stop(mc);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent event) {
        if (!inGame()) return;
        var in = event.getInput();
        if (ClientState.locked() || ClientState.hiding() || CameraApp.isOpen() || FrameCapture.capturing()) {
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            if (ClientState.locked()) in.shiftKeyDown = false;
        }
        // the closet's lower level: crouched
        if (ClientState.inCloset()) in.shiftKeyDown = true;
    }

    // ================================================================================================ the view
    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if (!inGame()) return;
        if (CameraApp.isOpen() || Peek.active() || ClientState.eyesClosed() || ClientState.jumpscareTicks >= 0 || FrameCapture.capturing()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void blockOverlay(RenderBlockScreenEffectEvent event) {
        if (inGame() && (ClientState.hiding() || CameraApp.isOpen())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fov(ViewportEvent.ComputeFov event) {
        if (AutoShots.overridesFov()) {
            if (event.usedConfiguredFov()) event.setFOV(AutoShots.fovOverride());
            return;
        }
        if (!inGame() || !event.usedConfiguredFov()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() instanceof FixedCamera) {
            // an action cam: wide
            event.setFOV(Peek.active() ? 62.0 : 78.0);
            return;
        }
        double mul = 1.0 - 0.06 * ClientState.dangerShown - 0.04 * Math.max(0.0f, ClientState.fearShown - 0.7f) / 0.3f;
        event.setFOV(event.getFOV() * mul);
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (!inGame() || AutoShots.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() instanceof FixedCamera cam) {
            // a camera does not shake with you; a fallen one lies on its side
            if (cam.fallen()) event.setRoll(event.getRoll() + cam.feedRoll((float) event.getPartialTick()));
            return;
        }
        float strength = (float) HitoriConfig.screenEffects() * (HitoriConfig.flashReduction() ? 0.4f : 1.0f);
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
        if (ClientState.breathHeld()) pitch += Mth.sin(t * 0.5f) * 0.12f;
        event.setYaw(event.getYaw() + yaw * strength);
        event.setPitch(event.getPitch() + pitch * strength);
        event.setRoll(event.getRoll() + roll * strength);
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!inGame() || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float d = ClientState.dangerShown;
        event.setRed(0.020f + 0.03f * d);
        event.setGreen(0.022f);
        event.setBlue(0.030f);
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void fog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (!inGame() || event.getType() != FogType.NONE || ShaderCompat.active()) return;
        if (mc.player != null && (mc.player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)
                || mc.player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS))) return;
        boolean outside = Layout.roomAt(mc.player.blockPosition()) == null;
        float far = (outside ? 48.0f : 32.0f) - 10.0f * ClientState.fearShown - 6.0f * ClientState.dangerShown;
        if (CameraApp.viewing()) far = 26.0f;
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) far *= 1.5f;
        far = Math.min(far, event.getFarPlaneDistance());
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.15f));
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void nameTag(net.minecraftforge.client.event.RenderNameTagEvent event) {
        if (FrameCapture.isTwin(event.getEntity())) event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    // ================================================================================================ HUD and the frame grab
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void guiPre(RenderGuiEvent.Pre event) {
        FrameCapture.onRenderGuiPre(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void hideVanillaHud(RenderGuiOverlayEvent.Pre event) {
        if (!inGame()) return;
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id()) || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())) {
            event.setCanceled(true);
            return;
        }
        boolean black = ClientState.jumpscareTicks >= 0 || ClientState.caughtTicks >= 0 || ClientState.introTicks >= 0 || ClientState.eyesClosed()
                || ClientState.flag(GameManager.F_ENDING);
        boolean hideBar = black || CameraApp.isOpen() || Peek.active() || FrameCapture.capturing();
        if (hideBar && (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.ITEM_NAME.id())
                || id.equals(VanillaGuiOverlay.RECORD_OVERLAY.id()) || id.equals(VanillaGuiOverlay.CROSSHAIR.id()))) {
            event.setCanceled(true);
        }
        if (ClientState.hiding() && id.equals(VanillaGuiOverlay.CROSSHAIR.id())) event.setCanceled(true);
    }

    // ================================================================================================ sounds, music, menus, connection
    private static net.minecraft.client.resources.sounds.SoundInstance titleMusic;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void sound(PlaySoundEvent event) {
        if (event.getSound() == null) return;
        if (event.getSound().getSource() == SoundSource.MUSIC && !event.getSound().getLocation().getNamespace().equals(Hitori.MODID)) {
            Minecraft mc = Minecraft.getInstance();
            if (inGame()) {
                event.setSound(null);
            } else if (mc.level == null) {
                if (titleMusic != null && mc.getSoundManager().isActive(titleMusic)) {
                    event.setSound(null);
                } else {
                    titleMusic = net.minecraft.client.resources.sounds.SimpleSoundInstance.forMusic(ModSounds.MUSIC_TITLE.get());
                    event.setSound(titleMusic);
                }
            }
            return;
        }
        HorrorAudio.observe(event.getSound());
    }

    @SubscribeEvent
    public static void screenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen title) AutoShots.onTitle(title);
    }

    @SubscribeEvent
    public static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        lastShaders = !ShaderCompat.active();
        FrameCapture.reset();
        String key = ShaderPackInstaller.takePendingMessage();
        if (key != null) event.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable(key), false);
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
        Dialogue.clear();
        CameraApp.reset();
        Peek.reset();
        PhoneUi.reset();
        HorrorAudio.stopAll();
    }
}
