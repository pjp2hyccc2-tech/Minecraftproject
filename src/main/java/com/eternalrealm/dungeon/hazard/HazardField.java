package com.eternalrealm.dungeon.hazard;

import com.eternalrealm.combat.Telegraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The traps of one running instance: decides when each one warns and when it hits, and who it hits. Server thread
 * only; time is the server tick. A trap only works while someone stands in its zone, always warns
 * {@link HazardKind#warnTicks()} before it hits, and hits exactly the victims whose feet are inside its marker at that
 * moment ({@link Telegraph#contains}). Once warned, a hit always follows (the marker never lies), even if the zone
 * empties in between. Deterministic for the same inputs.
 */
public final class HazardField {
    /** Someone who can be hurt, at their feet position (world coordinates). */
    public record Victim(UUID id, double x, double y, double z) {
        public Victim {
            Objects.requireNonNull(id, "id");
        }
    }

    private static final class State {
        boolean wasActive;
        long nextAt = Long.MIN_VALUE;
        Telegraph pending;
        long fireAt;
    }

    private final List<HazardSite> sites;
    private final double originX;
    private final double originY;
    private final double originZ;
    private final long seed;
    private final State[] states;

    /**
     * @param sites  the traps ({@link HazardPlanner#plan}), in layout coordinates
     * @param origin world position of the layout origin (the floor of the layout is at {@code originY})
     * @param seed   chooses among victims of targeted traps
     */
    public HazardField(List<HazardSite> sites, double originX, double originY, double originZ, long seed) {
        this.sites = List.copyOf(sites);
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.seed = seed;
        this.states = new State[this.sites.size()];
        for (int i = 0; i < states.length; i++) {
            states[i] = new State();
        }
    }

    public List<HazardSite> sites() {
        return sites;
    }

    /** The marker of a trap that is warning right now, or null. */
    public Telegraph pending(int siteIndex) {
        return states[siteIndex].pending;
    }

    /** Whether any trap is warning (its marker is up). */
    public boolean anyPending() {
        for (State state : states) {
            if (state.pending != null) {
                return true;
            }
        }
        return false;
    }

    /** Forgets every warning and timer (the instance ends): no further events. */
    public void reset() {
        for (State state : states) {
            state.pending = null;
            state.wasActive = false;
            state.nextAt = Long.MIN_VALUE;
        }
    }

    /** Advances to {@code now}. @return the warnings and hits of this tick, hits first */
    public List<HazardEvent> tick(long now, List<Victim> victims) {
        List<HazardEvent> strikes = new ArrayList<>();
        List<HazardEvent> warnings = new ArrayList<>();
        for (int i = 0; i < sites.size(); i++) {
            HazardSite site = sites.get(i);
            State state = states[i];
            if (state.pending != null) {
                if (now >= state.fireAt) {
                    strikes.add(new HazardEvent.Strike(site, state.pending, hits(state.pending, victims)));
                    state.pending = null;
                    state.nextAt = now + site.kind().cooldownTicks();
                }
                continue;
            }
            List<Victim> inZone = inZone(site, victims);
            boolean active = !inZone.isEmpty();
            if (active && !state.wasActive) {
                // someone just came in: give them a moment before the first warning
                state.nextAt = Math.max(state.nextAt, now + site.delay());
            }
            state.wasActive = active;
            if (!active || now < state.nextAt) {
                continue;
            }
            Telegraph marker = switch (site.kind().trigger()) {
                case PERIODIC -> site.telegraph(originX, originY, originZ);
                case PROXIMITY -> {
                    Telegraph fixed = site.telegraph(originX, originY, originZ);
                    yield anyInside(fixed, inZone) ? fixed : null;
                }
                case TARGETED -> {
                    Victim target = inZone.get(pick(site.index(), now, inZone.size()));
                    yield site.markerAt(clamp(target.x(), originX + site.zone().minX() + 0.5,
                                    originX + site.zone().maxX() + 0.5), originY + 1.0,
                            clamp(target.z(), originZ + site.zone().minZ() + 0.5, originZ + site.zone().maxZ() + 0.5));
                }
            };
            if (marker != null) {
                state.pending = marker;
                state.fireAt = now + site.kind().warnTicks();
                warnings.add(new HazardEvent.Warn(site, marker, state.fireAt));
            }
        }
        strikes.addAll(warnings);
        return strikes;
    }

    private List<Victim> inZone(HazardSite site, List<Victim> victims) {
        List<Victim> inside = new ArrayList<>();
        for (Victim victim : victims) {
            if (site.inZone(victim.x() - originX, victim.y() - originY, victim.z() - originZ)) {
                inside.add(victim);
            }
        }
        return inside;
    }

    private static boolean anyInside(Telegraph marker, List<Victim> victims) {
        for (Victim victim : victims) {
            if (marker.contains(victim.x(), victim.y(), victim.z())) {
                return true;
            }
        }
        return false;
    }

    private static List<UUID> hits(Telegraph marker, List<Victim> victims) {
        List<UUID> hit = new ArrayList<>();
        for (Victim victim : victims) {
            if (marker.contains(victim.x(), victim.y(), victim.z()) && !hit.contains(victim.id())) {
                hit.add(victim.id());
            }
        }
        return hit;
    }

    /** Deterministic choice among {@code count} victims for a trap at a tick. */
    private int pick(int siteIndex, long now, int count) {
        long mixed = seed ^ (siteIndex * 0x9E3779B97F4A7C15L) ^ (now * 0xC2B2AE3D27D4EB4FL);
        mixed ^= mixed >>> 31;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 29;
        return (int) Math.floorMod(mixed, (long) count);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
