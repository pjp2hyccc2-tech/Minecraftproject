package com.eternalrealm.player;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Online sessions keyed by player UUID. Confined to the server thread.
 */
public final class PlayerSessionRegistry {
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();
    private long nextGeneration = 1;

    /** Opens a new session in {@link SessionState#LOADING}. Any existing session must be removed first. */
    public PlayerSession open(UUID playerId, String name, Instant now) {
        if (sessions.containsKey(playerId)) {
            throw new IllegalStateException("Session already open for " + playerId);
        }
        PlayerSession session = new PlayerSession(playerId, name, nextGeneration++, now);
        sessions.put(playerId, session);
        return session;
    }

    public PlayerSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    public boolean contains(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public PlayerSession remove(UUID playerId) {
        return sessions.remove(playerId);
    }

    public int size() {
        return sessions.size();
    }

    public long count(SessionState state) {
        return sessions.values().stream().filter(session -> session.state() == state).count();
    }

    public Collection<PlayerSession> all() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public void clear() {
        sessions.clear();
    }
}
