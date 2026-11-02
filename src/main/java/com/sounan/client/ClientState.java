package com.sounan.client;

import com.sounan.game.ClientView;

import java.util.HashMap;
import java.util.Map;

/** Everything the client knows about the horror state (set by packets, eased every tick). */
public final class ClientState {
    public static float fear, fearShown, fearPrev;
    public static float danger, dangerShown, dangerPrev;
    public static float stamina = 100, breath = 100;
    public static int bells, phase = 1, deaths;
    public static boolean inGame, hidden, holding, exhausted, captured, inSafe, ritualReady, prologue;

    public static int jumpscareTicks = -1, jumpscareKind;
    public static int deathTicks = -1, deathCount;
    public static int ofudaTicks = -1;
    public static int introTicks = -1;
    public static String introName = "";
    public static int titleTicks = -1, titlePhase;
    public static int spottedTicks = -1, stingerTicks = -1, shadowTicks = -1, flickerTicks = -1;
    public static int shakeTicks, shakeTotal;
    public static float shakeIntensity;
    public static int bellFlash;
    public static boolean ritualActive;
    public static int ritualTicks;
    public static long clientTicks;
    public static final Map<Integer, String> NOTE_EXTRA = new HashMap<>();

    private ClientState() {}

    public static void shake(float intensity, int ticks) {
        if (intensity * ticks >= shakeIntensity * shakeTicks) {
            shakeIntensity = intensity;
            shakeTicks = ticks;
            shakeTotal = ticks;
        }
    }

    public static boolean cinematic() {
        return introTicks >= 0 && introTicks < IntroTiming.END || jumpscareTicks >= 0 || deathTicks >= 0 && deathTicks < 120;
    }

    public static void reset() {
        fear = fearShown = fearPrev = 0;
        danger = dangerShown = dangerPrev = 0;
        stamina = 100;
        breath = 100;
        bells = 0;
        phase = 1;
        inGame = hidden = holding = exhausted = captured = inSafe = ritualReady = prologue = false;
        jumpscareTicks = deathTicks = ofudaTicks = introTicks = titleTicks = -1;
        spottedTicks = stingerTicks = shadowTicks = flickerTicks = -1;
        shakeTicks = 0;
        ritualActive = false;
        ClientView.reset();
    }

    /** Timing of the intro sequence (ticks). */
    public static final class IntroTiming {
        public static final int LINE1 = 20, LINE2 = 90, LINE3 = 160, LOGO = 240, FADE = 330, END = 370;

        private IntroTiming() {}
    }
}
