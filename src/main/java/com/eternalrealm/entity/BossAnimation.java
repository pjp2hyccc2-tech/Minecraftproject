package com.eternalrealm.entity;

import java.util.Locale;

/**
 * Animations of the boss rigs, in the slot order of {@link RigData} and of the generated client animation tables.
 *
 * <p>{@link #IDLE} and {@link #WALK} run by themselves; playing them through {@link ERBoss#playAnimation} stops the
 * current action. {@link #ROAR} is the telegraph wind-up loop: {@link ERBoss#setTelegraphing(boolean)} holds it,
 * playing it plays two cycles. Every other animation plays once.</p>
 */
public enum BossAnimation {
    IDLE, WALK, MELEE_SLAM, MELEE_SWEEP, CAST, ROAR, CHARGE, SUMMON, PHASE_CHANGE, STAGGER, DEATH;

    /** First entity event id used by bosses (enemies use 64..79, vanilla up to 63). */
    public static final int EVENT_BASE = 80;

    public byte eventId() {
        return (byte) (EVENT_BASE + ordinal());
    }

    public static BossAnimation byEvent(byte id) {
        int index = id - EVENT_BASE;
        BossAnimation[] values = values();
        return index >= 0 && index < values.length ? values[index] : null;
    }

    /** Lowercase id, e.g. "melee_slam". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether this is a one-shot or held action (everything except IDLE and WALK). */
    public boolean isAction() {
        return this != IDLE && this != WALK;
    }
}
