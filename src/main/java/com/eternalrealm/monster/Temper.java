package com.eternalrealm.monster;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 気性: field monsters leave players alone until provoked. Owner, 2026-11-12: 「フィールドの敵は周囲5ブロック以内に
 * 入ったら攻撃してくるようにして」 (before, 2026-11-11: only when attacked or when a player stayed close for a while).
 * A calm monster turns on a player who hurts it or who comes within the platform's aggro radius (in sight); a player a
 * little farther away is warned once per approach (it turns to look and growls), so the line can be seen before it is
 * crossed. It fights only the players who provoked it, and settles down again once it has had nobody to fight for
 * {@link #SETTLE_TICKS}. Pure and per monster; the platform keeps one per living monster.
 */
public final class Temper {
    /** A provoked monster with nobody to fight for this long (ticks) settles down. */
    public static final int SETTLE_TICKS = 200;

    /**
     * What one look around changed.
     *
     * @param provoked the first player who came close enough this look (it turns on them; every other close player is
     *                 provoked too), or {@code null}
     * @param warned   the first player who came within the warning distance this look, or {@code null}
     */
    public record Look(UUID provoked, UUID warned) {
        public static final Look NOTHING = new Look(null, null);
    }

    private final Set<UUID> provokers = new HashSet<>();
    /** Players warned during their current approach (forgotten once they walk away). */
    private final Set<UUID> warned = new HashSet<>();
    private int idleTicks;

    /** Nobody has provoked it (or it has settled down since). */
    public boolean isCalm() {
        return provokers.isEmpty();
    }

    /** Whether it may fight {@code player}: only those who provoked it. */
    public boolean mayTarget(UUID player) {
        return provokers.contains(player);
    }

    /** @return whether this made a calm monster angry */
    public boolean provoke(UUID player) {
        Objects.requireNonNull(player, "player");
        boolean wasCalm = isCalm();
        provokers.add(player);
        warned.remove(player);
        idleTicks = 0;
        return wasCalm;
    }

    /**
     * One look around, {@code step} ticks after the last one.
     *
     * @param close    players within the aggro radius and in sight: each provokes it at once
     * @param near     players within the warning distance and in sight (may include the close ones)
     * @param fighting whether it has a living target
     */
    public Look look(Collection<UUID> close, Collection<UUID> near, int step, boolean fighting) {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be > 0: " + step);
        }
        if (!isCalm()) {
            idleTicks = fighting ? 0 : idleTicks + step;
            if (idleTicks >= SETTLE_TICKS) {
                provokers.clear();
                warned.clear();
                idleTicks = 0;
            }
        }
        warned.removeIf(player -> !near.contains(player) && !close.contains(player));
        UUID provoked = null;
        for (UUID player : close) {
            if (!provokers.contains(player)) {
                provoke(player);
                if (provoked == null) {
                    provoked = player;
                }
            }
        }
        UUID newlyWarned = null;
        for (UUID player : near) {
            if (!provokers.contains(player) && warned.add(player) && newlyWarned == null) {
                newlyWarned = player;
            }
        }
        return provoked == null && newlyWarned == null ? Look.NOTHING : new Look(provoked, newlyWarned);
    }

    /** Whether {@code player} has been warned during the current approach. */
    public boolean hasWarned(UUID player) {
        return warned.contains(player);
    }
}
