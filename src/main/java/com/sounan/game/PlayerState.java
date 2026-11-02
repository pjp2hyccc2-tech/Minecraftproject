package com.sounan.game;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Runtime state of one player inside the game (not saved; rebuilt on login). */
public final class PlayerState {
    public float fear;
    public float stamina = 100.0f;
    public float breath = 100.0f;
    public float danger;
    public int closet = -1;
    public boolean inSafeRoom;
    public boolean captured;
    public int captureTicks;
    @Nullable public Vec3 capturePos;
    public int captureKind;
    public int exhausted;
    public boolean holdingBreath;
    public int seenByBride;
    public int brideNearTicks;
    public boolean everLeftSafeRoom;
    public int ritualTicks;
    public int stepNoise;
    public int panicNoise;
    @Nullable public BlockPos lightPos;
    public int lightFlicker;
    public int syncTimer;
    public int hiddenTicks;
    public boolean lanternLit;
    public boolean introSent;
    public boolean ending;
    public boolean endingGood;
    public int endingTicks;
    @Nullable public Vec3 lastPos;
    public long atGate = Long.MIN_VALUE / 2;
    public float forceFear;
    public float forceDanger;
    public int forceTicks;

    // director timers (ticks until next event)
    public int nextMinor = 20 * 20;
    public int nextMajor = 20 * 70;
    public int nextPhantom = 20 * 150;
    public int nextShadow = 20 * 60;
    public int peeksLeft = 2;
    public int calmTicks;
    public int lastChase;

    /** While hiding: true if she can hear you breathing (you are scared and not holding your breath). */
    public boolean breathAudible() {
        return closet >= 0 && !holdingBreath && (fear > 45.0f || breath < 25.0f);
    }

    public void resetAfterDeath() {
        fear = 40.0f;
        stamina = 100.0f;
        breath = 100.0f;
        danger = 0.0f;
        closet = -1;
        captured = false;
        captureTicks = 0;
        capturePos = null;
        exhausted = 0;
        holdingBreath = false;
        seenByBride = 0;
        ritualTicks = 0;
        nextMinor = 20 * 25;
        nextMajor = 20 * 80;
        nextPhantom = 20 * 160;
    }
}
