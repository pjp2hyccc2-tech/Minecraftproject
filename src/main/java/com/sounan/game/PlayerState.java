package com.sounan.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** State of one player on the mountain. Batteries and the loop-back point persist in the player's data. */
public final class PlayerState {
    // ---- persistent
    public float flash = 100.0f;
    public float phone = 31.0f;
    @Nullable public Vec3 safePos;
    public float safeYaw;

    // ---- runtime
    public float fear = 25.0f;
    public float danger;
    public float stamina = 100.0f;
    public int exhausted;
    public int area = -1;
    public int outTicks;
    public int fogTicks;
    public boolean phoneOpen;
    public boolean shaders;
    public boolean captured;
    public int captureTicks;
    public int captureKind;
    @Nullable public Vec3 capturePos;
    public int syncTimer;
    public int saveTimer;
    public int introTicks = -1;
    /** Ticks until the controls hint is shown (after the intro). */
    public int hintIn;
    public int endingTicks = -1;
    public int stillTicks;
    public int bellCooldown;
    public int photoCooldown;
    public int lowWarned;
    public float lastYaw;
    public double lastX, lastZ;
    public boolean wasSprinting;
    public int breathNoise;
    /** Fear / danger forced by debug commands and the screenshot tour. */
    public float forceFear = -1.0f;
    public float forceDanger = -1.0f;
    public int forceTicks;

    // ---- director
    public int nextMinor = 20 * 18;
    public int nextMajor = 20 * 75;
    public int nextMonster = 20 * 50;
    public int calmTicks;
    public int litTicks;
    public int lookTicks;
    public long lastScare;

    public float bpm() {
        return 68.0f + fear * 1.12f;
    }

    public void resetAfterCapture() {
        fear = 45.0f;
        danger = 0.0f;
        stamina = 100.0f;
        exhausted = 0;
        captured = false;
        captureTicks = 0;
        capturePos = null;
        outTicks = 0;
        fogTicks = 0;
        flash = Math.max(flash, 30.0f);
        nextMinor = 20 * 25;
        nextMajor = 20 * 90;
        nextMonster = 20 * 60;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("Flash", flash);
        t.putFloat("Phone", phone);
        if (safePos != null) {
            t.putDouble("SafeX", safePos.x);
            t.putDouble("SafeY", safePos.y);
            t.putDouble("SafeZ", safePos.z);
            t.putFloat("SafeYaw", safeYaw);
        }
        return t;
    }

    public void load(CompoundTag t) {
        if (t.contains("Flash")) flash = t.getFloat("Flash");
        if (t.contains("Phone")) phone = t.getFloat("Phone");
        if (t.contains("SafeX")) {
            safePos = new Vec3(t.getDouble("SafeX"), t.getDouble("SafeY"), t.getDouble("SafeZ"));
            safeYaw = t.getFloat("SafeYaw");
        }
    }
}
