package com.hitori.client;

import com.hitori.entity.FixedCamera;
import com.hitori.game.Hiding;
import com.hitori.network.C2S;
import com.hitori.network.ModNetwork;
import com.hitori.registry.ModEntities;
import com.hitori.world.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Peeking through the fusuma's gap from inside the 押し入れ (docs/DESIGN.md §3.2): hold right mouse facing the
 * fusuma — after a moment the view moves to the eye at the crack (a client-only eye, {@code setCameraEntity}) and the
 * slit overlay closes in around a narrow vertical gap; letting go, you sit back. A quick click instead opens the fusuma
 * and you leave the closet.
 */
public final class Peek {
    private static final int HOLD = 5;
    private static int holdTicks;
    private static boolean active;
    private static boolean wasDown;
    @Nullable private static FixedCamera eye;
    private static int activeTicks;

    private Peek() {}

    public static boolean active() {
        return active;
    }

    public static int activeTicks() {
        return activeTicks;
    }

    /** Facing the fusuma (within 70° of the closet's outward facing)? */
    static boolean facingFusuma(Minecraft mc) {
        if (mc.player == null || !Layout.has("closet_peek")) return false;
        float base = Layout.marker("closet_peek").yaw();
        return Math.abs(Mth.wrapDegrees(mc.player.getYRot() - base)) < 70.0f;
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        boolean hiding = ClientState.hiding();
        boolean down = mc.options.keyUse.isDown() && mc.screen == null && hiding && !CameraApp.isOpen() && !ClientState.locked();
        if (!hiding) {
            if (active) stop(mc);
            holdTicks = 0;
            wasDown = false;
            return;
        }
        if (down) {
            holdTicks++;
            if (!active && holdTicks >= HOLD && ClientState.inCloset() && facingFusuma(mc)) start(mc);
        } else if (wasDown) {
            if (active) {
                stop(mc);
            } else if (holdTicks > 0 && holdTicks < HOLD && facingFusuma(mc)) {
                // a click: out of the closet
                ModNetwork.sendToServer(new C2S.Action(C2S.UNHIDE, 0));
            } else if (holdTicks > 0 && holdTicks < HOLD && heldScreen(mc) >= 0) {
                CameraApp.open(heldScreen(mc));
            }
            holdTicks = 0;
        }
        wasDown = down;
        if (active) {
            activeTicks++;
            place(mc);
        }
    }

    /** The laptop (0) or the phone (1) in a hand, or -1. */
    static int heldScreen(Minecraft mc) {
        if (mc.player == null) return -1;
        var k = com.hitori.item.GameItem.kindOf(mc.player.getMainHandItem());
        if (k == null) k = com.hitori.item.GameItem.kindOf(mc.player.getOffhandItem());
        if (k == com.hitori.item.GameItem.Kind.LAPTOP) return 0;
        if (k != null && k.phone()) return 1;
        return -1;
    }

    private static void start(Minecraft mc) {
        if (eye == null || eye.level() != mc.level) {
            eye = ModEntities.CAMERA.get().create(mc.level);
            if (eye == null) return;
            eye.clientOnly = true;
        }
        active = true;
        activeTicks = 0;
        place(mc);
        mc.setCameraEntity(eye);
        ModNetwork.sendToServer(new C2S.Action(C2S.PEEK, 1));
    }

    /** CI: peek now (as if the right mouse were held). */
    public static void startForTest(Minecraft mc) {
        if (!active && ClientState.inCloset()) start(mc);
    }

    public static void stop(Minecraft mc) {
        if (!active) return;
        active = false;
        if (mc.player != null && mc.getCameraEntity() == eye) mc.setCameraEntity(mc.player);
        ModNetwork.sendToServer(new C2S.Action(C2S.PEEK, 0));
    }

    private static void place(Minecraft mc) {
        if (eye == null || mc.player == null) return;
        Vec3 at = Hiding.peekEye();
        float base = Hiding.peekYaw();
        float yaw = base + Mth.clamp(Mth.wrapDegrees(mc.player.getYRot() - base), -22.0f, 22.0f);
        float pitch = Mth.clamp(mc.player.getXRot(), -25.0f, 30.0f);
        eye.place(at, yaw, pitch);
    }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        stop(mc);
        holdTicks = 0;
        wasDown = false;
    }
}
