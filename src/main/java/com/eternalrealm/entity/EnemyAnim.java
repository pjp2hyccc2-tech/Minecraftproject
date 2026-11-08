package com.eternalrealm.entity;

/**
 * Animation slots of the enemy rigs, in the slot order of {@link RigData} and of the generated client animation
 * tables. ATTACK1, ATTACK2, CAST and SPAWN are started on clients by entity events; IDLE and WALK run continuously,
 * HURT follows the vanilla hurt flash and DEATH follows the health.
 */
public enum EnemyAnim {
    IDLE, WALK, ATTACK1, ATTACK2, CAST, HURT, SPAWN, DEATH;

    /** First entity event id used by enemies (vanilla uses up to 63). */
    public static final int EVENT_BASE = 64;

    /** Entity event id that starts this animation on clients. */
    public byte eventId() {
        return (byte) (EVENT_BASE + ordinal());
    }

    /** The animation started by an entity event, or {@code null} if the id is not one of ours. */
    public static EnemyAnim byEvent(byte id) {
        int index = id - EVENT_BASE;
        EnemyAnim[] values = values();
        return index >= 0 && index < values.length ? values[index] : null;
    }

    /** Lowercase id, e.g. "attack1". */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
