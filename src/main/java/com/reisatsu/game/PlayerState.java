package com.reisatsu.game;

import com.reisatsu.entity.SpiritKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/** The state of one player in the restaurant. The rooms already seen persist in the player's data. */
public final class PlayerState {
    // ---- persistent
    public final Set<String> roomsSeen = new HashSet<>();

    // ---- nerves (0..1)
    public float fear = 0.2f;
    public float danger;
    /** Highest awareness any spirit has of you (the HUD eye). */
    public float aware;
    public int syncTimer;
    public int saveTimer;

    // ---- story
    /** Ticks into the intro (server timeline), -1 = not in the intro. */
    public int introTicks = -1;
    public int hintIn;
    /** Cinematic: movement locked (exorcism, endings). */
    public boolean locked;
    @Nullable public Vec3 lockPos;
    public int endingTicks = -1;

    // ---- caught
    public int caughtTicks = -1;
    @Nullable public SpiritKind caughtBy;
    @Nullable public Vec3 caughtPos;

    // ---- where
    public String room = "";
    public int outsideTicks;

    // ---- body
    public double stepAccum;
    public double lastX, lastZ;
    public boolean wasOnGround = true;
    public int airTicks;
    public double airTopY;
    public boolean breathHolding;
    public int breathTicks;
    public float breathGauge = 1.0f;
    public int heavyBreathIn = 40;

    // ---- hiding (lockers)
    @Nullable public BlockPos hiddenAt;
    public Direction hiddenFacing = Direction.EAST;
    public float lastYaw, lastPitch;
    public int rattleCooldown;

    // ---- camera, flashlight
    public int shutterCooldown;
    public boolean shaders;
    public int flickerTicks;
    public boolean finderUp;

    // ---- debug
    public float forceFear = -1.0f;
    public float forceDanger = -1.0f;
    public int forceTicks;

    public boolean breathHeld() {
        return breathHolding;
    }

    public boolean hiding() {
        return hiddenAt != null;
    }

    @Nullable
    public BlockPos hiddenAt() {
        return hiddenAt;
    }

    public boolean caught() {
        return caughtTicks >= 0;
    }

    public void resetAfterWake() {
        fear = 0.45f;
        danger = 0.0f;
        aware = 0.0f;
        caughtTicks = -1;
        caughtBy = null;
        caughtPos = null;
        breathHolding = false;
        breathTicks = 0;
        breathGauge = 1.0f;
        hiddenAt = null;
    }

    /** A new night begins: calm again, nothing pending (the rooms seen are remembered). */
    public void resetForNight() {
        resetAfterWake();
        fear = 0.2f;
        endingTicks = -1;
        introTicks = -1;
        room = "";
        outsideTicks = 0;
        flickerTicks = 0;
        forceTicks = 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag rooms = new ListTag();
        for (String r : roomsSeen) rooms.add(StringTag.valueOf(r));
        t.put("Rooms", rooms);
        return t;
    }

    public void load(CompoundTag t) {
        for (Tag r : t.getList("Rooms", Tag.TAG_STRING)) roomsSeen.add(r.getAsString());
    }
}
