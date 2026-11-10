package com.eternalrealm.monster;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 気性: the monsters of the habitats leave players alone until provoked (owner's request of 2026-11-11: 「こちらが攻撃
 * するもしくは、長居するまでは攻撃してこないようにして」). A calm monster turns on a player who hurts it, or who stays
 * close to it for a while ({@code lingerTicks} within the platform's linger radius); halfway there it warns (turns to
 * look, growls). It fights only the players who provoked it, and settles down again once it has had nobody to fight
 * for {@link #SETTLE_TICKS}. Pure and per monster; the platform keeps one per living monster.
 */
public final class Temper {
    /** A provoked monster with nobody to fight for this long (ticks) settles down. */
    public static final int SETTLE_TICKS = 200;

    /**
     * What one look around changed.
     *
     * @param provoked a player who has now lingered long enough (the monster turns on them), or {@code null}
     * @param warned   a player who has now lingered half as long (the monster warns them), or {@code null}
     */
    public record Look(UUID provoked, UUID warned) {
        public static final Look NOTHING = new Look(null, null);
    }

    private final int lingerTicks;
    private final Map<UUID, Integer> lingering = new HashMap<>();
    private final Set<UUID> provokers = new HashSet<>();
    private int idleTicks;

    /** @param lingerTicks how long a player may stay close before it attacks (0: lingering never provokes) */
    public Temper(int lingerTicks) {
        if (lingerTicks < 0) {
            throw new IllegalArgumentException("lingerTicks must be >= 0: " + lingerTicks);
        }
        this.lingerTicks = lingerTicks;
    }

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
        lingering.remove(player);
        idleTicks = 0;
        return wasCalm;
    }

    /**
     * One look around, {@code step} ticks after the last one.
     *
     * @param near     players close enough to annoy it right now (others are forgotten)
     * @param fighting whether it has a living target
     */
    public Look look(Collection<UUID> near, int step, boolean fighting) {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be > 0: " + step);
        }
        if (!isCalm()) {
            idleTicks = fighting ? 0 : idleTicks + step;
            if (idleTicks >= SETTLE_TICKS) {
                provokers.clear();
                lingering.clear();
                idleTicks = 0;
            }
        }
        for (Iterator<UUID> it = lingering.keySet().iterator(); it.hasNext(); ) {
            if (!near.contains(it.next())) {
                it.remove();
            }
        }
        if (lingerTicks == 0) {
            return Look.NOTHING;
        }
        UUID provoked = null;
        UUID warned = null;
        for (UUID player : near) {
            if (provokers.contains(player)) {
                continue;
            }
            int before = lingering.getOrDefault(player, 0);
            int after = before + step;
            if (after >= lingerTicks) {
                if (provoked == null) {
                    provoked = player;
                    provoke(player);
                } else {
                    lingering.put(player, after);
                }
            } else {
                lingering.put(player, after);
                int half = (lingerTicks + 1) / 2;
                if (before < half && after >= half && warned == null) {
                    warned = player;
                }
            }
        }
        return provoked == null && warned == null ? Look.NOTHING : new Look(provoked, warned);
    }

    /** Ticks {@code player} has stayed close so far (0 when not lingering). */
    public int lingered(UUID player) {
        return lingering.getOrDefault(player, 0);
    }
}
