package com.clockworkdiver.interior;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.clock.DefectType;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.UUID;

/** One player's stay inside a clock while repairing one part. Lives only in memory. */
public final class DiveSession {
    public static final int INTRO = 0;
    public static final int ACTIVE = 1;
    public static final int COMPLETE = 2;

    public final UUID playerId;
    public final int defectId;
    public final DefectType type;
    public final String part;
    public final boolean tutorial;
    public final int difficulty;
    public final BlockPos origin;
    public final ZoneLayout layout;

    public int period;
    public long anchor;
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
    public int lastObjective = Integer.MIN_VALUE;
    public int warnCooldown;
    /** Debug dives use a throw-away commission. */
    public boolean debug;

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
    }

    public String objectiveKey() {
        return phase == COMPLETE ? "objective.clockworkdiver.dive.complete" : type.objectiveKey();
    }
}
