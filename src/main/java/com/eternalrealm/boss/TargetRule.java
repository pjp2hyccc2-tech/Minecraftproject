package com.eternalrealm.boss;

/**
 * Who an ability aims at, decided once when its telegraph starts. Distances are horizontal (XZ) from the boss; ties
 * are broken by the target UUID so the choice never depends on the order in which the platform lists the players.
 */
public enum TargetRule {
    /** Closest player: punishes melee range. */
    NEAREST,
    /** Uniformly random player (uses the brain's random generator). */
    RANDOM,
    /** Most distant player: stops ranged players from idling at the edge. */
    FARTHEST,
    /** Player with the highest threat value supplied by the platform (tank-buster attacks); ties: the nearest. */
    HIGHEST_THREAT
}
