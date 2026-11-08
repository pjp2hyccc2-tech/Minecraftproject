package com.eternalrealm.server;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Players whose character changed during the current tick. Flushed once at the end of the tick, so several changes
 * in one tick (e.g. an EXP grant with three level-ups) produce a single snapshot packet. Server thread only.
 */
public final class SyncQueue {
    private final Set<UUID> pending = new LinkedHashSet<>();

    public void mark(UUID playerId) {
        pending.add(playerId);
    }

    public void forget(UUID playerId) {
        pending.remove(playerId);
    }

    public List<UUID> drain() {
        if (pending.isEmpty()) {
            return List.of();
        }
        List<UUID> drained = List.copyOf(pending);
        pending.clear();
        return drained;
    }
}
