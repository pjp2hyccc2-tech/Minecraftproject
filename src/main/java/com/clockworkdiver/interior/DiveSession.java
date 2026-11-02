package com.clockworkdiver.interior;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.clock.DefectType;
import com.clockworkdiver.clock.DiveMods;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One player's stay inside a clock while repairing one part. Lives only in memory. */
public final class DiveSession {
    public static final int INTRO = 0;
    public static final int ACTIVE = 1;
    public static final int COMPLETE = 2;

    public static final int TUNE_PAUSE = 0;
    public static final int TUNE_PLAY = 1;
    public static final int TUNE_INPUT = 2;

    public final UUID playerId;
    public final int defectId;
    public final DefectType type;
    public final String part;
    public final boolean tutorial;
    public final int difficulty;
    public final BlockPos origin;
    public final ZoneLayout layout;
    /** {@link DiveMods} bits. */
    public final int mods;
    public final int fragStart;
    public final int fragCount;
    public final int boss;
    /** Story quest id or empty. */
    public final String story;

    public int period;
    public long anchor;
    /** Ticks added to the anchor for judging (off-beat dives judge half a beat late). */
    public int offset;
    /** Game time at which a timed repair fails, 0 = no limit. */
    public long deadline;
    public int phase = INTRO;
    public int phaseTicks;
    public int ticks;

    /** Objective counters: done / total (spring uses percent). */
    public int done;
    public int total;
    public float progress;
    public int spawnTimer = 60;
    public int waveIndex;
    public int screwHeight;
    public int screwPhase;
    public int actionCooldown;
    @Nullable
    public UUID bossId;
    public boolean bossSpawned;
    public boolean enraged;
    public int bossPhase = 1;
    public int lastObjective = Integer.MIN_VALUE;
    public int warnCooldown;
    /** Debug dives use a throw-away commission. */
    public boolean debug;

    /** Memory fragments picked up so far. */
    public int fragCollected;
    /** The repair itself is finished but memory fragments are still lying around. */
    public boolean pendingComplete;
    /** Dust heaps put back by dust moths (capped). */
    public int dustRegrown;

    /** Tuning: the melody to repeat (bell notes), playback / input position and timers. */
    public final List<Integer> melody = new ArrayList<>();
    public int tuneState = TUNE_PAUSE;
    public int tuneIndex;
    public int tuneWait = 2;
    public long tuneBeat = Long.MIN_VALUE;
    public int tuneTimeout;
    @Nullable
    public BlockPos litBell;
    public int litTicks;

    public DiveSession(UUID playerId, Defect defect, ClockData clock, BlockPos origin, ZoneLayout layout) {
        this.playerId = playerId;
        this.defectId = defect.id;
        this.type = defect.type;
        this.part = defect.part;
        this.tutorial = clock.tutorial;
        this.difficulty = clock.difficulty();
        this.origin = origin;
        this.layout = layout;
        this.period = clock.type.period;
        this.screwHeight = layout.screwHeight;
        this.mods = defect.mods;
        this.fragStart = defect.fragStart;
        this.fragCount = Math.min(defect.fragCount, layout.fragments.size());
        this.boss = defect.boss;
        this.story = clock.story;
    }

    public boolean has(int mod) {
        return DiveMods.has(mods, mod);
    }

    public String objectiveKey() {
        if (phase == COMPLETE) return "objective.clockworkdiver.dive.complete";
        if (pendingComplete) return "objective.clockworkdiver.dive.fragments_left";
        return type.objectiveKey();
    }

    /** Language key of the text of the fragment picked up {@code n}-th (1-based). */
    public String fragmentKey(int n) {
        if (story.isEmpty()) return "story.clockworkdiver.debug.frag";
        return "story.clockworkdiver." + story + ".frag." + (fragStart + n - 1);
    }
}
