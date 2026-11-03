package com.reisatsu.client;

import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.GameData;
import com.reisatsu.game.GameManager;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Everything the client knows about the night: the synced state and the local presentation timers. */
public final class ClientState {
    // ---- synced (S2C.State)
    public static float fear, danger, aware, breath = 1.0f;
    public static int flags, film, ofuda, captured, submitted, phase, reishou, circuits;
    /** The night of the week (1..7), its stage (GameData.NS_*), what was witnessed tonight. */
    public static int night = 1, nightStage, signs;

    // ---- smoothed
    public static float fearShown, dangerShown, awareShown, fearPrev, dangerPrev;
    public static int clientTicks;

    // ---- presentation timers (ticks since start, -1 = off)
    public static int introTicks = -1;
    public static boolean introSkippable;
    public static String introName = "";
    public static int jumpscareKind = -1, jumpscareTicks = -1;
    public static int caughtTicks = -1;
    public static int wakeTicks = -1, wakeOfuda;
    public static String areaKey = "";
    public static int areaTicks = -1;
    public static String thought = "";
    public static int thoughtTicks = -1;
    public static int hintTicks = -1;
    public static int closingTicks = -1;
    public static int stingerTicks = -1;
    public static int noticedTicks = -1, noticedKind = -1;
    public static int objectiveTicks = -1;
    public static int fadeTicks = -1, fadeLength = 40;
    public static int dawnTicks = -1;
    public static int screamTicks = -1;
    public static int lockerFacing = -1;
    public static int exorcismStage = -1, exorcismTicks = -1;
    public static String releasedSpirit = "";
    public static int releaseTicks = -1;
    public static int submitTicks = -1, submitKind = -1;
    public static int glitchTicks = -1;
    /** The night's title card (night, ticks), the blackout between nights, the 「記録：…」 line. */
    public static int nightCard = -1, nightCardTicks = -1;
    public static int blackoutTicks = -1, blackoutFade = 30;
    public static String witnessKey = "";
    public static int witnessTicks = -1;
    public static int manifestTicks = -1;
    public static float shake;
    public static int shakeTicks;
    public static int subliminal, subliminalKind;

    // ---- the album
    public static final List<GameData.Photo> PHOTOS = new ArrayList<>();
    public static final List<String> NOTES = new ArrayList<>();

    private ClientState() {}

    /** How long the title card stays (it holds the screen black first, then the lot fades in under it). */
    public static final int NIGHT_CARD_END = 170;

    public static boolean flag(int f) {
        return (flags & f) != 0;
    }

    public static boolean inGame() {
        return flag(GameManager.F_INGAME);
    }

    public static boolean isCaptured(SpiritKind k) {
        return (captured & k.bit()) != 0;
    }

    public static int capturedFive() {
        int n = 0;
        for (SpiritKind k : SpiritKind.FIVE) if (isCaptured(k)) n++;
        return n;
    }

    public static float bpm() {
        return 64.0f + fearShown * 96.0f + dangerShown * 20.0f;
    }

    public static void shake(float amount, int ticks) {
        shake = Math.max(shake, amount);
        shakeTicks = Math.max(shakeTicks, ticks);
    }

    /** Input is taken away (intro, being caught, cinematic moments, the ending). */
    public static boolean locked() {
        return flag(GameManager.F_INTRO) || flag(GameManager.F_CAUGHT) || flag(GameManager.F_LOCKED) || flag(GameManager.F_ENDING)
                || jumpscareTicks >= 0 || caughtTicks >= 0 || (introTicks >= 0 && introTicks < 400) || blackoutTicks >= 0;
    }

    /** The screen is black (between nights, or the first part of a night's card). */
    public static boolean black() {
        return blackoutTicks >= 0 || (nightCardTicks >= 0 && nightCardTicks < 60);
    }

    public static boolean hiding() {
        return flag(GameManager.F_HIDING) || lockerFacing >= 0;
    }

    public static void tick() {
        clientTicks++;
        fearPrev = fearShown;
        dangerPrev = dangerShown;
        fearShown += (fear - fearShown) * 0.08f;
        dangerShown += (danger - dangerShown) * (danger > dangerShown ? 0.25f : 0.05f);
        awareShown += (aware - awareShown) * 0.25f;
        if (jumpscareTicks >= 0 && ++jumpscareTicks > 14) {
            jumpscareTicks = -1;
            if (caughtTicks < 0) caughtTicks = 0;
        }
        if (caughtTicks >= 0 && ++caughtTicks > 90) caughtTicks = -1;
        if (wakeTicks >= 0 && ++wakeTicks > 120) wakeTicks = -1;
        if (introTicks >= 0) introTicks++;
        if (areaTicks >= 0 && ++areaTicks > 110) areaTicks = -1;
        if (thoughtTicks >= 0 && ++thoughtTicks > 110) thoughtTicks = -1;
        if (hintTicks >= 0 && ++hintTicks > 20 * 14) hintTicks = -1;
        if (closingTicks >= 0 && ++closingTicks > 200) closingTicks = -1;
        if (stingerTicks >= 0 && ++stingerTicks > 40) stingerTicks = -1;
        if (noticedTicks >= 0 && ++noticedTicks > 30) noticedTicks = -1;
        if (objectiveTicks >= 0 && ++objectiveTicks > 80) objectiveTicks = -1;
        if (fadeTicks >= 0 && ++fadeTicks > fadeLength * 2) fadeTicks = -1;
        if (dawnTicks >= 0) dawnTicks++;
        if (screamTicks >= 0 && ++screamTicks > 70) screamTicks = -1;
        if (exorcismTicks >= 0) exorcismTicks++;
        if (releaseTicks >= 0 && ++releaseTicks > 80) releaseTicks = -1;
        if (submitTicks >= 0 && ++submitTicks > 90) submitTicks = -1;
        if (glitchTicks >= 0 && ++glitchTicks > 16) glitchTicks = -1;
        if (nightCardTicks >= 0 && ++nightCardTicks > NIGHT_CARD_END) nightCardTicks = -1;
        // the blackout holds until the next card (or gives up after 15 s)
        if (blackoutTicks >= 0 && ++blackoutTicks > 300) blackoutTicks = -1;
        if (witnessTicks >= 0 && ++witnessTicks > 110) witnessTicks = -1;
        if (manifestTicks >= 0 && ++manifestTicks > 80) manifestTicks = -1;
        if (shakeTicks > 0 && --shakeTicks == 0) shake = 0.0f;
    }

    public static void reset() {
        fear = fearShown = fearPrev = 0;
        danger = dangerShown = dangerPrev = 0;
        aware = awareShown = 0;
        flags = 0;
        introTicks = jumpscareTicks = caughtTicks = wakeTicks = areaTicks = thoughtTicks = hintTicks = closingTicks = -1;
        stingerTicks = noticedTicks = objectiveTicks = fadeTicks = dawnTicks = screamTicks = lockerFacing = -1;
        exorcismStage = exorcismTicks = releaseTicks = submitTicks = glitchTicks = -1;
        nightCard = nightCardTicks = blackoutTicks = witnessTicks = manifestTicks = -1;
        night = 1;
        nightStage = signs = 0;
        shake = 0;
        shakeTicks = 0;
        PHOTOS.clear();
        NOTES.clear();
    }

    public static float clamp01(float v) {
        return Mth.clamp(v, 0.0f, 1.0f);
    }
}
