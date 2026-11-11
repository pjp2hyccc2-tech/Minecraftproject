package com.eternalrealm.estate;

/**
 * Where an estate stands, for screens and callers of the service.
 *
 * @param phase   see {@link Phase}
 * @param percent progress of the running build (0..100; 100 when ready)
 */
public record EstateStatus(Phase phase, int percent) {
    public enum Phase {
        /** No such estate. */
        NONE,
        /** The first build is under way: nobody can enter yet. */
        BUILDING,
        /** Built: it can be entered. */
        READY,
        /** A redesign (or a refurbishment to a newer blueprint) is being built; the current build is still used. */
        REBUILDING
    }

    public EstateStatus {
        if (phase == null) {
            throw new IllegalArgumentException("phase");
        }
        percent = Math.max(0, Math.min(100, percent));
    }

    public static final EstateStatus NONE = new EstateStatus(Phase.NONE, 0);

    /** Whether players can enter now. */
    public boolean enterable() {
        return phase == Phase.READY || phase == Phase.REBUILDING;
    }
}
