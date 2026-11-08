package com.eternalrealm.boss;

/**
 * Fixed rules shared by every boss. They are code constants because content (telegraph lengths, arena sizes) is
 * designed against them; tune individual bosses through their definitions instead.
 */
public final class BossRules {
    /**
     * Shortest warning before anything that hurts: human reaction (~5 ticks) plus network latency (~2-3 ticks) still
     * leaves time to start the 8-tick invulnerable dodge or to step out.
     */
    public static final int MIN_TELEGRAPH_TICKS = 10;
    /** Arena-wide attacks can only be dodged at the right moment, so players need a readable countdown. */
    public static final int ARENA_WIDE_MIN_TELEGRAPH_TICKS = 20;
    /** Soak circles need the party to gather on one player. */
    public static final int SOAK_MIN_TELEGRAPH_TICKS = 30;
    /** Invulnerable pause when a new phase starts (PhaseChanged, shield on ... shield off). */
    public static final int PHASE_TRANSITION_TICKS = 60;
    /** Soft enrage: every hit deals this much more ... */
    public static final double ENRAGE_DAMAGE_MULTIPLIER = 1.5;
    /** ... and cooldowns (per ability and global) started after the enrage are this much shorter. */
    public static final double ENRAGE_COOLDOWN_MULTIPLIER = 0.6;
    /** A ground shape hits players whose feet are at most this many blocks above or below the shape. */
    public static final double VERTICAL_TOLERANCE = 3.0;
    /** Adds are summoned on a circle of this radius around the boss. */
    public static final double SUMMON_RADIUS = 4.0;
    /** Radius of the marker shown where adds will appear or where the boss will teleport to (harmless). */
    public static final double MARKER_RADIUS = 1.5;
    /** Range of the cone that telegraphs a projectile volley; projectiles should expire after this distance. */
    public static final double VOLLEY_RANGE = 24.0;
    /** Smallest half angle used to draw the fan of a single-direction volley. */
    public static final double VOLLEY_MIN_HALF_ANGLE = 5.0;
    /** Largest party a brain accepts (health scales linearly with the party size). */
    public static final int MAX_PARTY_SIZE = 40;
    /** Safe zones need time to find a green circle and run there. */
    public static final int SAFE_ZONE_MIN_TELEGRAPH_TICKS = 40;
    /** A lasting hazard hurts everyone inside it this often ... */
    public static final int HAZARD_PULSE_TICKS = 10;
    /** ... with this fraction of the hit that created it. */
    public static final double HAZARD_DAMAGE_FRACTION = 0.25;
    /** A charging boss covers its strip in this many ticks; the next charge is aimed after it arrived. */
    public static final int DASH_TICKS = 4;
    /** Safe zones and scattered hits keep this far from the arena's edge (when the arena is known). */
    public static final double ARENA_MARGIN = 1.5;

    private BossRules() {
    }
}
