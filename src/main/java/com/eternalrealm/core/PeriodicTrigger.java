package com.eternalrealm.core;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Wall-clock based periodic trigger polled from the server tick (autosave, backups).
 * A zero or negative interval disables the trigger. Not thread-safe: poll from a single thread.
 */
public final class PeriodicTrigger {
    private final Clock clock;
    private long intervalMillis;
    private long nextAtMillis;

    public PeriodicTrigger(Duration interval, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        setInterval(interval);
    }

    /** @return {@code true} at most once per interval, when the interval has elapsed */
    public boolean poll() {
        if (intervalMillis <= 0) {
            return false;
        }
        long now = clock.millis();
        if (now >= nextAtMillis) {
            nextAtMillis = now + intervalMillis;
            return true;
        }
        return false;
    }

    /** Changes the interval and restarts the countdown from now. */
    public void setInterval(Duration interval) {
        Objects.requireNonNull(interval, "interval");
        this.intervalMillis = interval.toMillis();
        this.nextAtMillis = clock.millis() + Math.max(0L, intervalMillis);
    }

    public boolean isEnabled() {
        return intervalMillis > 0;
    }
}
