package com.eternalrealm.player;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One connection of a player, identified by UUID (never by name, which can change).
 * The {@link #generation()} distinguishes consecutive sessions of the same player, so that results of
 * asynchronous work started for an old session are discarded instead of being applied to a new one.
 *
 * <p>Confined to the server thread.</p>
 */
public final class PlayerSession {
    private final UUID playerId;
    private final String name;
    private final long generation;
    private final Instant openedAt;
    private SessionState state = SessionState.LOADING;

    PlayerSession(UUID playerId, String name, long generation, Instant openedAt) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.name = Objects.requireNonNull(name, "name");
        this.generation = generation;
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
    }

    void markActive() {
        requireState(SessionState.LOADING);
        state = SessionState.ACTIVE;
    }

    void markFailed() {
        requireState(SessionState.LOADING);
        state = SessionState.FAILED;
    }

    private void requireState(SessionState expected) {
        if (state != expected) {
            throw new IllegalStateException("Session of " + playerId + " is " + state + ", expected " + expected);
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public String name() {
        return name;
    }

    public long generation() {
        return generation;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public SessionState state() {
        return state;
    }
}
