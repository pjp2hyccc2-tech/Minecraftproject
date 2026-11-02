package com.sounan.client;

import com.sounan.game.GameManager;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/** Everything the client knows about the night (synced state + local presentation timers). */
public final class ClientState {
    private static final RandomSource RAND = RandomSource.create();

    // ---- synced
    public static float fear, danger, stamina = 100, flash = 100, phone = 31;
    public static int flags, captures, area, minutes = GameManager.START_MINUTES, days;
    public static boolean inGame;

    // ---- smoothed
    public static float fearShown, dangerShown, fearPrev, dangerPrev;
    public static int clientTicks;

    // ---- presentation timers (ticks since start, -1 = off)
    public static int jumpscareKind = -1, jumpscareTicks = -1;
    public static int wakeTicks = -1;
    public static int introTicks = -1;
    public static String introName = "";
    public static int areaTitle = -1, areaTicks = -1;
    public static String thought = "";
    public static int thoughtTicks = -1;
    public static String subtitle = "";
    public static int subtitleTicks = -1;
    public static int loopTicks = -1;
    public static int checkpointTicks = -1;
    public static int flashTicks = -1;
    public static int omamoriTicks = -1;
    public static int fogLiftTicks = -1;
    public static int signalTicks = -1;
    public static int silenceTicks = -1;
    public static int glitchTicks = -1;
    public static int stingerTicks = -1;
    public static int subliminal, subliminalKind;
    public static float shake;
    public static int shakeTicks;
    public static int buzzTicks = -1;
    public static String buzzText = "";

    // ---- the phone
    public static int notesRead;
    public static boolean messagesArrived;
    public static int unreadMessages;
    public static final List<double[]> TRAIL = new ArrayList<>();
    public static int callKind = -2, callTicks = -1;
    public static String callExtra = "";

    private ClientState() {}

    public static boolean flag(int f) {
        return (flags & f) != 0;
    }

    public static float bpm() {
        return 68.0f + fearShown * 1.12f;
    }

    public static void shake(float amount, int ticks) {
        shake = Math.max(shake, amount);
        shakeTicks = Math.max(shakeTicks, ticks);
    }

    public static boolean cinematic() {
        return Boolean.getBoolean("sounan.trailer");
    }

    public static void tick() {
        clientTicks++;
        fearPrev = fearShown;
        dangerPrev = dangerShown;
        fearShown += (fear - fearShown) * 0.08f;
        dangerShown += (danger - dangerShown) * (danger > dangerShown ? 0.2f : 0.05f);
        if (jumpscareTicks >= 0 && ++jumpscareTicks > 60) jumpscareTicks = -1;
        if (wakeTicks >= 0 && ++wakeTicks > 120) wakeTicks = -1;
        if (introTicks >= 0) introTicks++;
        if (areaTicks >= 0 && ++areaTicks > 140) areaTicks = -1;
        if (thoughtTicks >= 0 && ++thoughtTicks > 110) thoughtTicks = -1;
        if (subtitleTicks >= 0 && ++subtitleTicks > 120) subtitleTicks = -1;
        if (loopTicks >= 0 && ++loopTicks > 60) loopTicks = -1;
        if (checkpointTicks >= 0 && ++checkpointTicks > 100) checkpointTicks = -1;
        if (flashTicks >= 0 && ++flashTicks > 12) flashTicks = -1;
        if (omamoriTicks >= 0 && ++omamoriTicks > 50) omamoriTicks = -1;
        if (fogLiftTicks >= 0 && ++fogLiftTicks > 140) fogLiftTicks = -1;
        if (signalTicks >= 0 && ++signalTicks > 200) signalTicks = -1;
        if (silenceTicks >= 0 && ++silenceTicks > 160) silenceTicks = -1;
        if (glitchTicks >= 0 && ++glitchTicks > 16) glitchTicks = -1;
        if (stingerTicks >= 0 && ++stingerTicks > 40) stingerTicks = -1;
        if (buzzTicks >= 0 && ++buzzTicks > 100) buzzTicks = -1;
        if (callTicks >= 0) {
            callTicks++;
            int length = callKind == -1 ? 50 : callKind == 900 ? 20 * 22 : 20 * 29;
            if (callTicks > length) {
                callKind = -2;
                callTicks = -1;
            }
        }
        if (shakeTicks > 0) {
            shakeTicks--;
            if (shakeTicks == 0) shake = 0.0f;
        }
    }

    /** Camera shake offset this frame (degrees). */
    public static float shakeYaw(float partial) {
        float s = shake * (shakeTicks / 20.0f);
        float tremble = fearShown > 70 ? (fearShown - 70) / 30.0f * 0.12f : 0.0f;
        return (RAND.nextFloat() - 0.5f) * (s + tremble);
    }

    public static float shakePitch(float partial) {
        float s = shake * (shakeTicks / 20.0f);
        return (RAND.nextFloat() - 0.5f) * s * 0.7f;
    }

    public static void reset() {
        fear = fearShown = 0;
        danger = dangerShown = 0;
        jumpscareTicks = wakeTicks = introTicks = areaTicks = thoughtTicks = subtitleTicks = loopTicks = -1;
        checkpointTicks = flashTicks = omamoriTicks = fogLiftTicks = signalTicks = silenceTicks = glitchTicks = -1;
        stingerTicks = buzzTicks = callTicks = -1;
        shake = 0;
        shakeTicks = 0;
        TRAIL.clear();
        inGame = false;
    }

    /** "2:47" style phone clock. */
    public static String clock() {
        int m = Math.floorMod(minutes, 24 * 60);
        return (m / 60) + ":" + String.format("%02d", m % 60);
    }

    /** Month / day of the phone calendar (the night starts on 11/3). */
    public static int[] date() {
        return dateAfter(days + Math.max(0, minutes - 0) / (24 * 60));
    }

    public static int[] dateAfter(int dayOffset) {
        java.time.LocalDate d = java.time.LocalDate.of(2026, 11, 3).plusDays(dayOffset);
        return new int[]{d.getYear(), d.getMonthValue(), d.getDayOfMonth(), d.getDayOfWeek().getValue() % 7};
    }

    public static float clamp01(float v) {
        return Mth.clamp(v, 0.0f, 1.0f);
    }
}
