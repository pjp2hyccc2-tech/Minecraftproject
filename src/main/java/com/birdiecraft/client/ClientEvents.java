package com.birdiecraft.client;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.config.GolfConfig;
import com.birdiecraft.match.MatchState;
import com.birdiecraft.match.MatchView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client game events: input, ticking, rendering hooks. */
@Mod.EventBusSubscriber(modid = BirdieCraft.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (BallCam.isActive()) BallCam.stop();
            return;
        }
        ClientGolfState.tick();
        SwingController.tick();
        LandingPreview.tick();
        BallCam.tick();
        handleKeys(mc);
    }

    private static void handleKeys(Minecraft mc) {
        while (Keybinds.MENU.consumeClick()) ClientHooks.openGolfMenu();
        while (Keybinds.SCORECARD.consumeClick()) {
            if (ClientGolfState.inMatch()) ClientGolfState.scorecardToggled = !ClientGolfState.scorecardToggled;
        }
        while (Keybinds.BALL_CAM.consumeClick()) {
            GolfConfig.BallCamMode next = GolfConfig.BALL_CAM.get().next();
            GolfConfig.BALL_CAM.set(next);
            mc.player.displayClientMessage(Component.translatable("msg.birdiecraft.ballcam_mode",
                    Component.translatable("ballcam.birdiecraft." + next.name().toLowerCase(java.util.Locale.ROOT))), true);
            if (next == GolfConfig.BallCamMode.OFF) BallCam.stop();
        }
        boolean club = SwingController.holdingClub();
        while (Keybinds.CLUB_PREV.consumeClick()) if (club) SwingController.cycleClub(-1);
        while (Keybinds.CLUB_NEXT.consumeClick()) if (club) SwingController.cycleClub(1);
        while (Keybinds.MODE.consumeClick()) if (club) SwingController.cycleMode();
        while (Keybinds.SPIN_UP.consumeClick()) if (club) SwingController.spin(0, 1);
        while (Keybinds.SPIN_DOWN.consumeClick()) if (club) SwingController.spin(0, -1);
        while (Keybinds.SPIN_LEFT.consumeClick()) if (club) SwingController.spin(-1, 0);
        while (Keybinds.SPIN_RIGHT.consumeClick()) if (club) SwingController.spin(1, 0);
        while (Keybinds.AIM_PIN.consumeClick()) SwingController.aimAtPin();
        while (Keybinds.CANCEL.consumeClick()) SwingController.cancel();
        // the shot key itself is handled in onKey for frame-accurate timing
        while (Keybinds.SHOT.consumeClick()) {
            // consumed here so it does not pile up
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getAction() != GLFW.GLFW_PRESS || mc.screen != null || mc.player == null) return;
        boolean useButton = event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        if ((useButton && SwingController.holdingClub()) || Keybinds.SHOT.matchesMouse(event.getButton())) {
            if (SwingController.canSwing() || SwingController.isActive()) {
                SwingController.click();
                event.setCanceled(true);
            } else if (useButton && SwingController.holdingClub() && ClientGolfState.inMatch()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getAction() != GLFW.GLFW_PRESS || mc.screen != null || mc.player == null) return;
        if (Keybinds.SHOT.matches(event.getKey(), event.getScanCode())) {
            if (SwingController.canSwing() || SwingController.isActive()) SwingController.click();
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || !SwingController.holdingClub()) return;
        if (SwingController.isActive()) {
            event.setCanceled(true);
            return;
        }
        if (!SwingController.canSwing()) return;
        double d = event.getScrollDelta();
        if (d == 0) return;
        SwingController.cycleClub(d > 0 ? -1 : 1);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!SwingController.isActive()) return;
        Input in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        WorldRenderer.render(event);
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (BallCam.isActive()) {
            if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()
                    || event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()
                    || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()
                    || event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()
                    || event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()) {
                event.setCanceled(true);
            }
            return;
        }
        MatchView m = ClientGolfState.match;
        if (m != null && (m.state == MatchState.HOLE_END || m.state == MatchState.FINISHED)) {
            if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (BallCam.isActive()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BallCam.stop();
        ClientGolfState.reset();
        SwingController.cancel();
        LandingPreview.clear();
        HudMessages.clear();
    }
}
