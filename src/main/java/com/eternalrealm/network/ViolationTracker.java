package com.eternalrealm.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Counts protocol violations (impossible requests, rate-limit hits) per player inside a fixed time window.
 * When a player reaches the threshold within one window they should be disconnected.
 */
public final class ViolationTracker {
    private final int kickThreshold;
    private final long windowMillis;
    private final LongSupplier millisClock;
    private final Map<UUID, Window> windows = new HashMap<>();

    /**
     * @param kickThreshold violations per window that trigger a kick; 0 disables kicking (violations are still counted)
     */
    public ViolationTracker(int kickThreshold, long windowMillis, LongSupplier millisClock) {
        if (kickThreshold < 0) {
            throw new IllegalArgumentException("kickThreshold must be >= 0: " + kickThreshold);
        }
        if (windowMillis < 1) {
            throw new IllegalArgumentException("windowMillis must be >= 1: " + windowMillis);
        }
        this.kickThreshold = kickThreshold;
        this.windowMillis = windowMillis;
        this.millisClock = Objects.requireNonNull(millisClock, "millisClock");
    }

    public synchronized Verdict record(UUID playerId) {
        long now = millisClock.getAsLong();
        Window window = windows.get(playerId);
        if (window == null || now - window.startMillis >= windowMillis) {
            window = new Window(now);
            windows.put(playerId, window);
        }
        window.count++;
        boolean kick = kickThreshold > 0 && window.count >= kickThreshold;
        return new Verdict(window.count, kick);
    }

    public synchronized void forget(UUID playerId) {
        windows.remove(playerId);
    }

    /** @param countInWindow violations in the current window including this one */
    public record Verdict(int countInWindow, boolean shouldKick) {
    }

    private static final class Window {
        private final long startMillis;
        private int count;

        private Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}
