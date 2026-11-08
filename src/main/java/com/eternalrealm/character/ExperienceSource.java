package com.eternalrealm.character;

/**
 * Where granted experience came from. Logged with every grant; later phases use it for
 * audit logs and ranking/anti-abuse statistics.
 */
public enum ExperienceSource {
    /** Operator command. */
    ADMIN,
    /** Killing a hostile mob (Phase 1: interim vanilla-mob source, replaced by mob definitions in the monster phase). */
    MOB_KILL,
    /** Internal/system grants (tests, migrations). */
    SYSTEM,
    /** Turning in a quest. */
    QUEST,
    /** Defeating a dungeon boss or the final boss. */
    BOSS
}
