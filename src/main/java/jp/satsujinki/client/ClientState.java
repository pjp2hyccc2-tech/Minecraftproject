package jp.satsujinki.client;

import jp.satsujinki.SatsujinkiConfig;
import jp.satsujinki.client.sound.MusicSound;
import jp.satsujinki.game.KillStyle;
import jp.satsujinki.network.HostSyncPacket;
import jp.satsujinki.network.RoundPacket;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Client-side mirror of the host's score, HUD animations, screen effects and round banners. */
public final class ClientState {
    // ---- host stats
    public static int points;
    public static float shownPoints;
    public static int totalKills;
    public static int combo;
    public static int rank;
    public static float gauge;
    public static int maxCombo;
    public static int bestRank;
    public static int rounds;
    public static int perfect;
    public static int[] styleKills = new int[KillStyle.values().length];
    public static boolean synced;
    public static int hudVisibleTicks;

    // ---- animations
    public static final List<Popup> POPUPS = new ArrayList<>();
    public static int rankFlash;
    public static int comboPop;
    public static int pointsFlash;

    // ---- screen effects
    public static float screenBlood;
    public static int screenBloodVariant;
    public static float shake;
    public static int shakeTicks;
    public static int shakeMax = 1;
    public static int clientTicks;

    // ---- round
    public static boolean roundActive;
    public static int roundAlive;
    public static int roundTotal;
    public static int roundTicksLeft;
    public static int bannerType = -1;
    public static int bannerValue;
    public static int bannerTicks;
    public static RoundPacket result;
    public static int resultTicks;
    private static MusicSound music;

    // ---- R18 gate
    public static boolean r18Accepted;

    public static final class Popup {
        public final KillStyle style;
        public final int points;
        public final int combo;
        public final float tilt;
        public final float offX;
        public int age;

        Popup(KillStyle style, int points, int combo, RandomSource r) {
            this.style = style;
            this.points = points;
            this.combo = combo;
            this.tilt = (r.nextFloat() - 0.5F) * 16.0F;
            this.offX = (r.nextFloat() - 0.5F) * 60.0F;
        }
    }

    private ClientState() {
    }

    public static void onSync(HostSyncPacket p) {
        if (p.points != points) pointsFlash = 12;
        points = p.points;
        if (!synced) shownPoints = p.points;
        synced = true;
        totalKills = p.totalKills;
        if (p.combo > combo && p.combo >= 2) comboPop = 10;
        combo = p.combo;
        rank = p.rank;
        gauge = p.gauge;
        maxCombo = p.maxCombo;
        bestRank = p.bestRank;
        rounds = p.rounds;
        perfect = p.perfect;
        if (p.styleKills.length == styleKills.length) styleKills = p.styleKills;
        if (p.popupStyle >= 0) {
            RandomSource r = RandomSource.create();
            POPUPS.add(new Popup(KillStyle.byId(p.popupStyle), p.popupPoints, p.combo, r));
            if (POPUPS.size() > 5) POPUPS.remove(0);
            playUi(ModSounds.UI_KILL.get(), 1.0F, 0.9F);
            if (p.combo >= 2) playUi(ModSounds.UI_COMBO.get(), Math.min(2.0F, 0.8F + p.combo * 0.1F), 0.8F);
            hudVisibleTicks = 400;
        }
        if (p.rankUp) {
            rankFlash = 30;
            playUi(ModSounds.UI_RANKUP.get(), 0.9F + p.rank * 0.05F, 1.0F);
        }
    }

    public static void onRound(RoundPacket p) {
        switch (p.type) {
            case RoundPacket.INTRO -> {
                bannerType = RoundPacket.INTRO;
                bannerValue = p.a;
                bannerTicks = 110;
                roundActive = false;
                roundTotal = p.a;
                roundAlive = p.a;
                result = null;
            }
            case RoundPacket.COUNT -> {
                bannerType = RoundPacket.COUNT;
                bannerValue = p.a;
                bannerTicks = 20;
            }
            case RoundPacket.GO -> {
                bannerType = RoundPacket.GO;
                bannerTicks = 40;
                roundActive = true;
                roundAlive = p.a;
                roundTotal = p.b;
                roundTicksLeft = p.c;
                startMusic();
                shake(0.5F, 10);
            }
            case RoundPacket.UPDATE -> {
                roundActive = true;
                roundAlive = p.a;
                roundTotal = p.b;
                roundTicksLeft = p.c;
            }
            case RoundPacket.END -> {
                roundActive = false;
                result = p;
                resultTicks = 200;
                bannerType = RoundPacket.END;
                bannerTicks = 200;
                stopMusic();
            }
            default -> {
            }
        }
        hudVisibleTicks = 400;
    }

    public static void tick() {
        clientTicks++;
        shownPoints += (points - shownPoints) * 0.15F;
        if (Math.abs(points - shownPoints) < 1.0F) shownPoints = points;
        for (Iterator<Popup> it = POPUPS.iterator(); it.hasNext(); ) {
            Popup pp = it.next();
            if (++pp.age > 50) it.remove();
        }
        if (rankFlash > 0) rankFlash--;
        if (comboPop > 0) comboPop--;
        if (pointsFlash > 0) pointsFlash--;
        if (hudVisibleTicks > 0) hudVisibleTicks--;
        if (screenBlood > 0.0F) screenBlood = Math.max(0.0F, screenBlood - 0.012F);
        if (shakeTicks > 0) shakeTicks--;
        if (bannerTicks > 0) bannerTicks--;
        if (resultTicks > 0) resultTicks--;
        if (roundActive && roundTicksLeft > 0) roundTicksLeft--;
        if (combo > 0 || rank > 0 || gauge > 0.0F || roundActive) hudVisibleTicks = Math.max(hudVisibleTicks, 60);
    }

    public static void splashScreen(int strength, float closeness) {
        if (!SatsujinkiConfig.screenBlood()) return;
        float add = 0.35F + 0.3F * strength * closeness;
        screenBlood = Math.min(1.0F, Math.max(screenBlood, add));
        screenBloodVariant = RandomSource.create().nextInt(4);
    }

    public static void shake(float intensity, int ticks) {
        if (!SatsujinkiConfig.cameraShake()) return;
        if (shakeTicks <= 0 || intensity >= shake) {
            shake = intensity;
            shakeTicks = ticks;
            shakeMax = Math.max(1, ticks);
        }
    }

    public static void playUi(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    private static void startMusic() {
        if (!SatsujinkiConfig.roundMusic()) return;
        stopMusic();
        Minecraft mc = Minecraft.getInstance();
        mc.getMusicManager().stopPlaying();
        music = new MusicSound(ModSounds.MUSIC_DEATHGAME.get());
        mc.getSoundManager().play(music);
    }

    private static void stopMusic() {
        if (music != null) music.fadeOut();
        music = null;
    }

    public static boolean isRoundMusicWanted() {
        return roundActive;
    }

    public static void reset() {
        stopMusic();
        synced = false;
        points = 0;
        shownPoints = 0;
        combo = 0;
        rank = 0;
        gauge = 0.0F;
        POPUPS.clear();
        roundActive = false;
        bannerTicks = 0;
        resultTicks = 0;
        result = null;
        screenBlood = 0.0F;
        shakeTicks = 0;
    }
}
