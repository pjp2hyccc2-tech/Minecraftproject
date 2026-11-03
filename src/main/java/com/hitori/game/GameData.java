package com.hitori.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The night's progress (stored with the overworld): the phase, the ritual's steps, the clock, the doll's name and
 * where it is, the cameras, the rules broken, the ending. Save and quit resumes where you were.
 */
public final class GameData extends SavedData {
    private static final String NAME = "hitori_game";

    /** Not started; Phase 0 preparation; Phase 1 the ritual; Phase 2 hiding; Phase 3 ending it; an ending playing. */
    public static final int PHASE_IDLE = 0, PHASE_PREP = 1, PHASE_RITUAL = 2, PHASE_HIDING = 3, PHASE_FINISH = 4, PHASE_ENDING = 5;
    /** 終了 (good), 鬼, 外へ, 未完. */
    public static final int ENDING_NONE = 0, ENDING_GOOD = 1, ENDING_ONI = 2, ENDING_SOTO = 3, ENDING_MIKAN = 4;
    public static final String DEFAULT_NAME = "みーちゃん";

    /** A placed camera: CAM n at a position, looking along yaw / pitch. */
    public record CamSpot(int number, double x, double y, double z, float yaw, float pitch, boolean fallen) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("N", number);
            t.putDouble("X", x);
            t.putDouble("Y", y);
            t.putDouble("Z", z);
            t.putFloat("Yaw", yaw);
            t.putFloat("Pitch", pitch);
            t.putBoolean("Fallen", fallen);
            return t;
        }

        public static CamSpot load(CompoundTag t) {
            return new CamSpot(t.getInt("N"), t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"), t.getFloat("Yaw"), t.getFloat("Pitch"),
                    t.getBoolean("Fallen"));
        }

        public CamSpot withFallen(boolean f) {
            return new CamSpot(number, x, y, z, yaw, pitch, f);
        }
    }

    public boolean started;
    public int phase = PHASE_IDLE;
    /** Steps done (bit = {@link Step#ordinal()}). */
    public long steps;
    /** The in-game clock: seconds since midnight (02:20 = 8400). */
    public double clock = Clock.START;
    public String dollName = DEFAULT_NAME;
    /** Rules broken (keys, each counted once): docs §1 the rules, §3.5. */
    public final Set<String> broken = new LinkedHashSet<>();
    public int ending = ENDING_NONE;
    public final List<CamSpot> cameras = new ArrayList<>();
    public final Set<String> flags = new LinkedHashSet<>();
    public long playTicks;
    public long ritualStartTick = -1, hideStartTick = -1, finishStartTick = -1, endingTick = -1;
    /** 「最初の鬼は…」 and 「私の勝ち」 said so far. */
    public int firstOniSaid, winSaid;
    // ---- the doll, for save and quit
    public boolean dollAlive;
    public double dollX, dollY, dollZ;
    public float dollYaw;
    public String dollState = "";
    public float dollSuspicion;
    public int dollTubTicks;
    // ---- the phone
    public int messages;
    public boolean phoneSilent;
    public boolean introSeen;

    public static GameData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(GameData::load, GameData::new, NAME);
    }

    // ------------------------------------------------------------------------------------------------ steps, flags
    public boolean done(Step s) {
        return (steps & (1L << s.ordinal())) != 0;
    }

    public void setDone(Step s, boolean on) {
        if (on) steps |= 1L << s.ordinal();
        else steps &= ~(1L << s.ordinal());
        setDirty();
    }

    /** True the first time (a one-off flag). */
    public boolean flag(String key) {
        if (flags.contains(key)) return false;
        flags.add(key);
        setDirty();
        return true;
    }

    public boolean has(String key) {
        return flags.contains(key);
    }

    public void unflag(String key) {
        if (flags.remove(key)) setDirty();
    }

    /** A rule broken (counted once per key). */
    public boolean breakRule(String key) {
        if (broken.add(key)) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean ritualStarted() {
        return phase >= PHASE_RITUAL && phase != PHASE_ENDING || (phase == PHASE_ENDING && ritualStartTick >= 0);
    }

    /** Phases 1–3: the ritual runs (the rules hold). */
    public boolean ritualRunning() {
        return phase == PHASE_RITUAL || phase == PHASE_HIDING || phase == PHASE_FINISH;
    }

    public void resetProgress() {
        started = false;
        phase = PHASE_IDLE;
        steps = 0;
        clock = Clock.START;
        dollName = DEFAULT_NAME;
        broken.clear();
        ending = ENDING_NONE;
        cameras.clear();
        flags.clear();
        playTicks = 0;
        ritualStartTick = hideStartTick = finishStartTick = endingTick = -1;
        firstOniSaid = winSaid = 0;
        dollAlive = false;
        dollState = "";
        dollSuspicion = 0;
        dollTubTicks = 0;
        messages = 0;
        phoneSilent = false;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Started", started);
        tag.putInt("Phase", phase);
        tag.putLong("Steps", steps);
        tag.putDouble("Clock", clock);
        tag.putString("DollName", dollName);
        tag.put("Broken", strings(broken));
        tag.putInt("Ending", ending);
        ListTag cams = new ListTag();
        for (CamSpot c : cameras) cams.add(c.save());
        tag.put("Cameras", cams);
        tag.put("Flags", strings(flags));
        tag.putLong("PlayTicks", playTicks);
        tag.putLong("RitualStart", ritualStartTick);
        tag.putLong("HideStart", hideStartTick);
        tag.putLong("FinishStart", finishStartTick);
        tag.putLong("EndingTick", endingTick);
        tag.putInt("FirstOni", firstOniSaid);
        tag.putInt("Win", winSaid);
        tag.putBoolean("DollAlive", dollAlive);
        tag.putDouble("DollX", dollX);
        tag.putDouble("DollY", dollY);
        tag.putDouble("DollZ", dollZ);
        tag.putFloat("DollYaw", dollYaw);
        tag.putString("DollState", dollState);
        tag.putFloat("DollSuspicion", dollSuspicion);
        tag.putInt("DollTub", dollTubTicks);
        tag.putInt("Messages", messages);
        tag.putBoolean("PhoneSilent", phoneSilent);
        tag.putBoolean("IntroSeen", introSeen);
        return tag;
    }

    private static ListTag strings(Set<String> set) {
        ListTag l = new ListTag();
        for (String s : set) l.add(StringTag.valueOf(s));
        return l;
    }

    public static GameData load(CompoundTag tag) {
        GameData d = new GameData();
        d.started = tag.getBoolean("Started");
        d.phase = tag.getInt("Phase");
        d.steps = tag.getLong("Steps");
        d.clock = tag.contains("Clock") ? tag.getDouble("Clock") : Clock.START;
        d.dollName = tag.contains("DollName") ? tag.getString("DollName") : DEFAULT_NAME;
        for (Tag t : tag.getList("Broken", Tag.TAG_STRING)) d.broken.add(t.getAsString());
        d.ending = tag.getInt("Ending");
        for (Tag t : tag.getList("Cameras", Tag.TAG_COMPOUND)) d.cameras.add(CamSpot.load((CompoundTag) t));
        for (Tag t : tag.getList("Flags", Tag.TAG_STRING)) d.flags.add(t.getAsString());
        d.playTicks = tag.getLong("PlayTicks");
        d.ritualStartTick = tag.contains("RitualStart") ? tag.getLong("RitualStart") : -1;
        d.hideStartTick = tag.contains("HideStart") ? tag.getLong("HideStart") : -1;
        d.finishStartTick = tag.contains("FinishStart") ? tag.getLong("FinishStart") : -1;
        d.endingTick = tag.contains("EndingTick") ? tag.getLong("EndingTick") : -1;
        d.firstOniSaid = tag.getInt("FirstOni");
        d.winSaid = tag.getInt("Win");
        d.dollAlive = tag.getBoolean("DollAlive");
        d.dollX = tag.getDouble("DollX");
        d.dollY = tag.getDouble("DollY");
        d.dollZ = tag.getDouble("DollZ");
        d.dollYaw = tag.getFloat("DollYaw");
        d.dollState = tag.getString("DollState");
        d.dollSuspicion = tag.getFloat("DollSuspicion");
        d.dollTubTicks = tag.getInt("DollTub");
        d.messages = tag.getInt("Messages");
        d.phoneSilent = tag.getBoolean("PhoneSilent");
        d.introSeen = tag.getBoolean("IntroSeen");
        return d;
    }
}
