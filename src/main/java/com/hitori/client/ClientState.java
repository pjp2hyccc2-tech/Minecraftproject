package com.hitori.client;

import com.hitori.game.GameManager;
import com.hitori.game.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the client knows about the night: the synced state, the local presentation timers — and the small API
 * other client code reads (the acoustics' closet and camera-microphone filters, the shader channels):
 * {@link #inCloset()}, {@link #hidingSpot()}, {@link #cameraView()}, {@link #cameraNumber()}, {@link #peeking()},
 * {@link #eyesClosed()}, {@link #mouth()}, {@link #breathHeld()}, {@link #bodyPos()}, {@link #fear()}, {@link #danger()}.
 */
public final class ClientState {
    // ---- synced (S2C.State)
    public static float fear, danger, mouth, breath = 1.0f;
    public static int flags, phase, hud = -1, broken, cameras;
    public static long steps;
    public static float clock = (float) com.hitori.game.Clock.START;
    public static String action = "", dollName = "みーちゃん";
    public static float actionProgress;

    // ---- smoothed
    public static float fearShown, dangerShown, fearPrev, dangerPrev, mouthShown;
    public static int clientTicks;

    // ---- presentation timers (ticks since start, -1 = off)
    public static int introTicks = -1;
    public static boolean introShort;
    public static int stepFlash = -1, stepFlashIndex = -1;
    public static int threeTicks = -1;
    public static int eyesTicks = -1;
    public static int jumpscareKind = -1, jumpscareTicks = -1;
    public static int caughtTicks = -1;
    public static int fadeTicks = -1, fadeLength = 40;
    public static int morningTicks = -1, morningKind;
    public static int stingerTicks = -1;
    public static int scareTicks = -1, scareKind;
    public static int ruleTicks = -1;
    public static String ruleKey = "";
    public static String thought = "";
    public static int thoughtTicks = -1;
    public static int hintTicks = -1, hintKind;
    public static int hideKind;
    public static int mapTicks = -1;
    public static float shake;
    public static int shakeTicks;
    public static int subliminal;

    private ClientState() {}

    public static boolean flag(int f) {
        return (flags & f) != 0;
    }

    public static boolean inGame() {
        return flag(GameManager.F_INGAME);
    }

    // ================================================================================================ the API
    /** Hiding in the 押し入れ (the acoustics' closet filter: everything outside low-passed, your own sounds dry and close). */
    public static boolean inCloset() {
        return inGame() && (flag(GameManager.F_CLOSET) || hideKind == 1);
    }

    /** "oshiire", "western" or "" (not hiding). */
    public static String hidingSpot() {
        if (!inGame()) return "";
        if (flag(GameManager.F_CLOSET) || hideKind == 1) return "oshiire";
        if (flag(GameManager.F_HIDING) || hideKind == 2) return "western";
        return "";
    }

    /** The camera app is open: the listener is the camera (the acoustics' band-limited microphone + mic hiss). */
    public static boolean cameraView() {
        return CameraApp.viewing();
    }

    /** CAM n shown in the app (0 = closed). */
    public static int cameraNumber() {
        return CameraApp.viewing() ? CameraApp.number() : 0;
    }

    public static boolean peeking() {
        return Peek.active();
    }

    /** Counting with your eyes closed (the shader's blindness channel). */
    public static boolean eyesClosed() {
        return flag(GameManager.F_EYES) || eyesTicks >= 0;
    }

    /** Salt water in the mouth, 0..1 (your breathing is muffled). */
    public static float mouth() {
        return mouth;
    }

    public static boolean breathHeld() {
        return flag(GameManager.F_BREATH);
    }

    /** Where your own body is (while the listener is a camera: the muffled mix of what is near you). */
    public static Vec3 bodyPos() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
    }

    public static float fear() {
        return fearShown;
    }

    public static float danger() {
        return dangerShown;
    }

    // ================================================================================================ helpers
    public static boolean done(Step s) {
        return (steps & (1L << s.ordinal())) != 0;
    }

    public static float bpm() {
        return 62.0f + fearShown * 96.0f + dangerShown * 22.0f;
    }

    public static void shake(float amount, int ticks) {
        shake = Math.max(shake, amount);
        shakeTicks = Math.max(shakeTicks, ticks);
    }

    /** Input is taken away (cinematic moments, a timed action, the ending). */
    public static boolean locked() {
        return flag(GameManager.F_LOCKED) || flag(GameManager.F_CAUGHT) || flag(GameManager.F_ENDING) || flag(GameManager.F_BUSY)
                || jumpscareTicks >= 0 || caughtTicks >= 0 || (introTicks >= 0 && introTicks < introLength() - 30) || eyesClosed();
    }

    public static int introLength() {
        return introShort ? 90 : 260;
    }

    public static boolean hiding() {
        return inGame() && (flag(GameManager.F_HIDING) || hideKind > 0);
    }

    public static void tick() {
        clientTicks++;
        fearPrev = fearShown;
        dangerPrev = dangerShown;
        fearShown += (fear - fearShown) * 0.08f;
        dangerShown += (danger - dangerShown) * (danger > dangerShown ? 0.25f : 0.05f);
        mouthShown += (mouth - mouthShown) * 0.2f;
        if (introTicks >= 0 && ++introTicks > introLength()) introTicks = -1;
        if (stepFlash >= 0 && ++stepFlash > 70) stepFlash = -1;
        if (threeTicks >= 0 && ++threeTicks > 160) threeTicks = -1;
        if (eyesTicks >= 0) eyesTicks++;
        if (jumpscareTicks >= 0 && ++jumpscareTicks > 16) {
            jumpscareTicks = -1;
            if (caughtTicks < 0) caughtTicks = 0;
        }
        if (caughtTicks >= 0) caughtTicks++;
        if (fadeTicks >= 0 && ++fadeTicks > fadeLength * 2 + 20) fadeTicks = -1;
        if (morningTicks >= 0) morningTicks++;
        if (stingerTicks >= 0 && ++stingerTicks > 40) stingerTicks = -1;
        if (scareTicks >= 0 && ++scareTicks > 24) scareTicks = -1;
        if (ruleTicks >= 0 && ++ruleTicks > 120) ruleTicks = -1;
        if (thoughtTicks >= 0 && ++thoughtTicks > 120) thoughtTicks = -1;
        if (hintTicks >= 0 && ++hintTicks > 20 * 16) hintTicks = -1;
        if (mapTicks >= 0) mapTicks++;
        if (shakeTicks > 0 && --shakeTicks == 0) shake = 0.0f;
    }

    public static void reset() {
        fear = fearShown = fearPrev = 0;
        danger = dangerShown = dangerPrev = 0;
        mouth = mouthShown = 0;
        flags = 0;
        hud = -1;
        steps = 0;
        introTicks = stepFlash = threeTicks = eyesTicks = jumpscareTicks = caughtTicks = fadeTicks = morningTicks = -1;
        stingerTicks = scareTicks = ruleTicks = thoughtTicks = hintTicks = mapTicks = -1;
        hideKind = 0;
        shake = 0;
        shakeTicks = 0;
    }

    public static float clamp01(float v) {
        return Mth.clamp(v, 0.0f, 1.0f);
    }
}
