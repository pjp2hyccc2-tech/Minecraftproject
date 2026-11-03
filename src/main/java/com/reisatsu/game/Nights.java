package com.reisatsu.game;

import com.reisatsu.Reisatsu;
import com.reisatsu.entity.Child;
import com.reisatsu.entity.Manager;
import com.reisatsu.entity.Medium;
import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.item.GameItem;
import com.reisatsu.registry.ModItems;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The seven nights (docs/DESIGN.md §1 Flow). Each night: the restaurant is reset, the player wakes by the medium's car
 * with the camera's film topped up, a title card (「二日目の夜」「十一月四日（水）」), the medium's opening lines; then
 * the player goes in and works. Night 1 only watches the 霊障 (five kinds witnessed, then back to her). Nights 2–7 have
 * one spirit, dormant until the player has been inside a while and has witnessed two of its signs; its photo is taken
 * back to her, she reads the name and tells the story, says goodnight — black — the next night's card. Night 7 begins
 * with closing time and ends with the exorcism. Save and quit: the current night starts again.
 */
public final class Nights {
    /** The night running in this server session (-1: none yet: a loaded world resumes at the start of its night). */
    private static int liveNight = -1;
    /** The card and the medium's opening lines (ticks, -1 = none) and when they end. */
    private static int openingTicks = -1, openingEnd;
    /** The medium's lines at the end of a night (ticks, -1 = none), the blackout, the next night. */
    private static int endTicks = -1, endBlack, endNext;

    private Nights() {}

    public static void reset() {
        liveNight = -1;
        openingTicks = -1;
        endTicks = -1;
    }

    /** Is a night of the week going on (not the intro, the exorcism or an ending)? */
    public static boolean nightPhase(GameData d) {
        return d.started && (d.phase == GameData.PHASE_INVESTIGATE || d.phase == GameData.PHASE_CLOSING || d.phase == GameData.PHASE_RETURN);
    }

    public static boolean inOpening() {
        return openingTicks >= 0;
    }

    public static boolean inEnding() {
        return endTicks >= 0;
    }

    /** May the player skip what is playing (the medium's lines at the start or the end of a night)? */
    public static boolean skippable() {
        return (openingTicks >= 40) || (endTicks >= 20 && endTicks < endBlack);
    }

    /** The intro is over: the first night is under way (no card: the intro was it). */
    public static void beginFirst(GameData data) {
        liveNight = 1;
        openingTicks = -1;
        endTicks = -1;
        data.night = 1;
        data.nightStage = GameData.NS_WORK;
        data.clearNight();
        data.setDirty();
        Director.reset();
    }

    /** A world loaded during a night (or a player joining one this session has not started): back to its start. */
    public static boolean needsResume(GameData data) {
        return nightPhase(data) && liveNight != data.night && openingTicks < 0 && endTicks < 0;
    }

    // ================================================================================================ the start of a night
    /** Night n begins for everyone: the restaurant reset, the player by the car, the title card, the medium. */
    public static void start(ServerLevel level, GameData data, int night, boolean resumed) {
        night = Mth.clamp(night, 1, Week.NIGHTS);
        Story.reset();
        Director.reset();
        data.night = night;
        data.phase = GameData.PHASE_INVESTIGATE;
        data.nightStage = GameData.NS_OPENING;
        data.clearNight();
        data.manifestAt = 20 * (60 + level.random.nextInt(31));
        data.setDirty();
        liveNight = night;
        endTicks = -1;
        // the restaurant as the closing crew left it; nobody inside
        Fixtures.ensureBuilt(level);
        Fixtures.resetAll(level);
        for (ServerPlayer p : GameManager.players(level)) {
            if (GameManager.inGame(p)) Hiding.exit(p, false);
        }
        Spirits.removeAll(level);
        Story.resetWorld(level, data);
        Spirits.ensure(level, data);
        for (ServerPlayer p : GameManager.players(level)) {
            if (GameManager.inGame(p)) prepare(level, p, data);
        }
        openingTicks = 0;
        openingEnd = Week.CARD_TICKS + Week.openingLength(night) + 6;
        Reisatsu.LOGGER.info("[reisatsu] night {} begins{}", night, resumed ? " (resumed)" : "");
    }

    /** By the car, the camera topped up, calm, held still for the card. */
    private static void prepare(ServerLevel level, ServerPlayer p, GameData data) {
        PlayerState ps = GameManager.state(p);
        GameManager.lowerCamera(p);
        Entity v = p.getVehicle();
        p.stopRiding();
        if (v != null && v.getTags().contains(Hiding.SEAT_TAG)) v.discard();
        ps.resetForNight();
        // the medium tops up the film (「フィルムは足しておいたわ」)
        ItemStack cam = GameManager.camera(p);
        if (cam.isEmpty()) {
            cam = new ItemStack(ModItems.REISHA_CAMERA.get());
            GameItem.setFilm(cam, GameItem.START_FILM);
            GameManager.give(p, cam);
        } else {
            GameItem.setFilm(cam, Math.max(GameItem.film(cam), GameItem.START_FILM));
        }
        if (!GameManager.has(p, ModItems.FLASHLIGHT.get()) && !GameManager.has(p, ModItems.FLASHLIGHT_ON.get())) {
            GameManager.give(p, new ItemStack(ModItems.FLASHLIGHT.get()));
        }
        if (!GameManager.has(p, ModItems.SHOP_KEY.get())) GameManager.give(p, new ItemStack(ModItems.SHOP_KEY.get()));
        p.removeAllEffects();
        Layout.Marker spawn = Layout.marker("spawn");
        Vec3 med = Layout.marker("medium").center();
        float face = (float) (Mth.atan2(med.z - spawn.center().z, med.x - spawn.center().x) * Mth.RAD_TO_DEG) - 90.0f;
        GameManager.teleport(p, level, spawn.center(), face, 6.0f);
        ps.locked = true;
        ps.lockPos = spawn.center();
        GameManager.event(p, GameManager.EV_NIGHT, data.night, "");
        GameManager.syncState(p, ps, data);
        Camera.syncAlbum(p);
    }

    private static void speakOpening(ServerLevel level, GameData data) {
        int[] t = Week.openingTicks(data.night);
        List<String> keys = new ArrayList<>();
        List<Integer> ticks = new ArrayList<>();
        for (int i = 0; i < t.length; i++) {
            keys.add("dialogue.reisatsu.open." + data.night + "." + (i + 1));
            ticks.add(t[i]);
        }
        int total = 0;
        for (ServerPlayer p : GameManager.players(level)) {
            if (GameManager.inGame(p)) total = Story.say(p, "medium", keys, ticks);
        }
        Medium m = Spirits.medium(level);
        if (m != null) m.talk(total);
    }

    /** The card and her lines are over (or skipped): the night is the player's. */
    private static void finishOpening(ServerLevel level, GameData data) {
        openingTicks = -1;
        if (data.nightStage == GameData.NS_OPENING) {
            SpiritKind k = Week.spirit(data.night);
            boolean done = k != null && data.isCaptured(k);
            data.nightStage = done ? GameData.NS_RETURN : GameData.NS_WORK;
            if (done && k == SpiritKind.MANAGER) data.phase = GameData.PHASE_RETURN;
            data.setDirty();
        }
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            PlayerState ps = GameManager.state(p);
            ps.locked = false;
            ps.lockPos = null;
            GameManager.event(p, GameManager.EV_NIGHT, 0, "");
            GameManager.event(p, GameManager.EV_OBJECTIVE, 0, "");
            GameManager.syncState(p, ps, data);
        }
        Medium m = Spirits.medium(level);
        if (m != null) m.talk(0);
    }

    /** SPACE during her lines (C2S.INTRO_SKIP). */
    public static boolean skip(ServerPlayer p) {
        return skip(p, false);
    }

    /** Skips her lines (force: also the first moments of the card — debug, the self test, the tour). */
    public static boolean skip(ServerPlayer p, boolean force) {
        ServerLevel level = GameManager.level(p.server);
        if (level == null) return false;
        GameData data = GameData.get(p.server);
        if (openingTicks >= 0) {
            if (openingTicks >= 40 || force) finishOpening(level, data);
            return true;
        }
        if (endTicks >= 0) {
            if ((endTicks >= 20 || force) && endTicks < endBlack - 1) {
                endTicks = endBlack - 1;
                for (ServerPlayer o : GameManager.players(level)) if (GameManager.inGame(o)) GameManager.event(o, GameManager.EV_NIGHT, 0, "");
            }
            return true;
        }
        return false;
    }

    // ================================================================================================ ticking
    public static void tick(ServerLevel level, GameData data) {
        if (!data.started) return;
        if (openingTicks >= 0) {
            int t = ++openingTicks;
            if (t == Week.CARD_TICKS) speakOpening(level, data);
            if (t >= openingEnd) finishOpening(level, data);
            return;
        }
        if (endTicks >= 0) {
            int t = ++endTicks;
            if (t == endBlack) {
                for (ServerPlayer p : GameManager.players(level)) if (GameManager.inGame(p)) GameManager.event(p, GameManager.EV_BLACKOUT, 30, "");
            }
            if (t >= endNext) {
                endTicks = -1;
                if (data.night < Week.NIGHTS) start(level, data, data.night + 1, false);
            }
            return;
        }
        if (!nightPhase(data)) return;
        if (data.nightStage != GameData.NS_WORK && data.nightStage != GameData.NS_HUNT) return;
        // the first step inside starts the night's clock
        if (!data.entered()) {
            for (ServerPlayer p : GameManager.players(level)) {
                if (!GameManager.inGame(p)) continue;
                PlayerState ps = GameManager.state(p);
                if (!ps.room.isEmpty() && !ps.room.equals("outside") && !ps.locked) {
                    data.enteredAt = data.playTicks;
                    data.setDirty();
                    break;
                }
            }
            if (!data.entered()) return;
        }
        long since = data.insideTicks();
        // the last night: closing time a few steps after you come in
        if (data.night == 7 && data.nightStage == GameData.NS_WORK && !data.happenedTonight("closing") && since >= 20 * 7) {
            Story.startClosing(level, data);
        }
        if (level.getGameTime() % 10 == 0) checkManifest(level, data, since);
    }

    /** Its signs witnessed tonight. */
    public static int signsOf(GameData data, SpiritKind k) {
        int n = 0;
        for (String s : data.signs) if (Week.isSign(k, s)) n++;
        return n;
    }

    /**
     * Nights 2–7: the spirit shows once the player has been inside a while (60–90 s) and has witnessed two of its
     * signs (the woman in the mirror: one of them the water); after four minutes one sign is enough, after six none.
     */
    private static void checkManifest(ServerLevel level, GameData data, long since) {
        if (data.nightStage != GameData.NS_WORK) return;
        SpiritKind k = Week.spirit(data.night);
        if (k == null || k == SpiritKind.VISITOR || data.isCaptured(k)) return;
        if (k == SpiritKind.MANAGER && (!data.happenedTonight("closing") || Story.closingRunning())) return;
        int seen = signsOf(data, k);
        boolean ready = since >= data.manifestAt && seen >= Week.MANIFEST_SIGNS;
        if (k == SpiritKind.MIRROR_WOMAN && !data.signs.contains("taps")) ready = false;
        if (since >= 20 * 240 && seen >= 1) ready = true;
        if (since >= 20 * 360) ready = true;
        if (ready) manifest(level, data);
    }

    /** The night's spirit shows itself (debug: /reisatsu debug manifest). */
    public static void manifest(ServerLevel level, GameData data) {
        SpiritKind k = Week.spirit(data.night);
        if (k == null || data.isCaptured(k) || !nightPhase(data)) return;
        Spirit s = Spirits.get(level, k);
        if (s == null && k != SpiritKind.VISITOR) s = Spirits.spawnAtHome(level, k);
        if (k == SpiritKind.VISITOR && s == null) {
            // he comes now: the cue, then the door
            data.visitorNext = data.playTicks + 20 * 13;
            data.setDirty();
            return;
        }
        if (s == null) return;
        s.manifest();
        switch (k) {
            case WAITRESS -> s.level().playSound(null, s.getX(), s.getEyeY(), s.getZ(), ModSounds.WAITRESS_HUM.get(), SoundSource.HOSTILE, 0.8f, 1.0f);
            case CHILD -> {
                for (ServerPlayer p : GameManager.players(level)) {
                    if (!GameManager.inGame(p)) continue;
                    // 「もういいかい」 from nowhere: her game begins
                    GameManager.privateSound(p, ModSounds.CHILD_MOUI.get(), p.getEyePosition().add(level.random.nextGaussian() * 3, 1.5,
                            level.random.nextGaussian() * 3), 0.9f, 1.0f);
                    Story.say(p, "child", 60, "dialogue.reisatsu.child.moui");
                    GameManager.addFear(p, 0.12f);
                }
                if (s instanceof Child c) c.startGame(null);
            }
            case MIRROR_WOMAN -> s.level().playSound(null, s.getX(), s.getEyeY(), s.getZ(), ModSounds.MIRROR_SOB.get(), SoundSource.HOSTILE, 1.2f, 0.95f);
            case CHEF -> s.level().playSound(null, s.getX(), s.getEyeY(), s.getZ(), ModSounds.CHEF_BREATH.get(), SoundSource.HOSTILE, 0.9f, 0.9f);
            case MANAGER -> {
                if (s instanceof Manager m) m.closingTime();
            }
            default -> {
            }
        }
        shown(level, data, k);
    }

    /** The spirit is there now (the visitor: his arrival). */
    public static void shown(ServerLevel level, GameData data, SpiritKind k) {
        if (data.nightStage != GameData.NS_WORK) return;
        data.nightStage = GameData.NS_HUNT;
        data.setDirty();
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            GameManager.event(p, GameManager.EV_MANIFEST, k.ordinal(), "");
            GameManager.event(p, GameManager.EV_OBJECTIVE, 0, "");
        }
        Reisatsu.LOGGER.info("[reisatsu] night {}: the {} is there ({} signs witnessed)", data.night, k.id, signsOf(data, k));
    }

    /** Something was witnessed that counts tonight. */
    static void onSign(ServerLevel level, ServerPlayer p, GameData data, String kind) {
        if (data.night == 1 && data.nightStage == GameData.NS_WORK && data.signs.size() >= Week.OBSERVE_GOAL) {
            data.nightStage = GameData.NS_RETURN;
            data.setDirty();
            for (ServerPlayer o : GameManager.players(level)) {
                if (!GameManager.inGame(o)) continue;
                GameManager.event(o, GameManager.EV_OBJECTIVE, 0, "");
                GameManager.event(o, GameManager.EV_THOUGHT, 0, "thought.reisatsu.observed");
            }
            Reisatsu.LOGGER.info("[reisatsu] night 1: five phenomena witnessed ({})", String.join(", ", data.signs));
        }
    }

    /** A valid photo of the night's spirit: back to the medium. */
    static void onCaptured(ServerLevel level, GameData data, SpiritKind kind) {
        if (kind != Week.spirit(data.night)) return;
        data.nightStage = GameData.NS_RETURN;
        data.setDirty();
    }

    // ================================================================================================ the end of a night
    /** She has spoken (story, goodnight): when her lines are over, black, then the next night. */
    public static void end(ServerLevel level, GameData data, int talkTicks) {
        data.nightStage = GameData.NS_ENDING;
        data.setDirty();
        endTicks = 0;
        endBlack = talkTicks + 12;
        endNext = endBlack + 44;
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            PlayerState ps = GameManager.state(p);
            Hiding.exit(p, false);
            GameManager.lowerCamera(p);
            p.removeEffect(MobEffects.NIGHT_VISION);
            ps.locked = true;
            ps.lockPos = p.position();
            GameManager.event(p, GameManager.EV_OBJECTIVE, 0, "");
            GameManager.syncState(p, ps, data);
        }
        Reisatsu.LOGGER.info("[reisatsu] night {} is over", data.night);
    }

    // ================================================================================================ debug
    /**
     * /reisatsu debug night n: the week as if nights 1..n-1 were done (their spirits photographed and sent on), then the
     * start of night n.
     */
    public static void jump(ServerLevel level, GameData data, int night) {
        night = Mth.clamp(night, 1, Week.NIGHTS);
        data.captured = 0;
        data.submitted = 0;
        for (int n = 2; n < night; n++) {
            SpiritKind k = Week.spirit(n);
            if (k != null) {
                data.captured |= k.bit();
                data.submitted |= k.bit();
            }
        }
        for (String w : Week.OBSERVE) {
            if (night > 1 && data.witnessed.size() < Week.OBSERVE_GOAL) data.witnessed.add(w);
        }
        data.started = true;
        data.introSeen = true;
        data.ending = GameData.ENDING_NONE;
        data.notes.add(Notes.REPORT);
        data.notes.add(Notes.MEDIUM);
        data.setDirty();
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            PlayerState ps = GameManager.state(p);
            ps.introTicks = -1;
            ps.endingTicks = -1;
            ps.caughtTicks = -1;
            GameManager.event(p, GameManager.EV_INTRO, -1, "");
        }
        start(level, data, night, false);
    }

    /** /reisatsu debug closing: the last night with the five before it done, its closing time now, where you are. */
    public static void debugClosing(ServerLevel level, GameData data) {
        for (SpiritKind k : SpiritKind.FIVE) {
            data.captured |= k.bit();
            data.submitted |= k.bit();
        }
        data.captured &= ~SpiritKind.MANAGER.bit();
        data.submitted &= ~SpiritKind.MANAGER.bit();
        data.started = true;
        data.night = 7;
        data.phase = GameData.PHASE_INVESTIGATE;
        data.clearNight();
        data.nightStage = GameData.NS_WORK;
        // as if inside for a while: he wakes as soon as the closing is over
        data.enteredAt = data.playTicks - 20L * 200;
        data.manifestAt = 0;
        data.setDirty();
        liveNight = 7;
        openingTicks = -1;
        endTicks = -1;
        for (Spirit s : Spirits.all(level)) s.discard();
        Spirits.invalidate();
        Story.reset();
        Story.startClosing(level, data);
    }

    /** /reisatsu debug exorcism: the week is at its last night's end. */
    public static void debugLastNight(GameData data) {
        data.started = true;
        data.night = 7;
        data.nightStage = GameData.NS_RETURN;
        data.setDirty();
        liveNight = 7;
        openingTicks = -1;
        endTicks = -1;
    }

    /** The live night of this session (-1 none) — self test. */
    public static int liveNight() {
        return liveNight;
    }

    /** Forget the session (self test: a server restart). */
    public static void forgetSession() {
        reset();
    }
}
