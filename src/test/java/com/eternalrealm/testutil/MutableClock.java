package com.eternalrealm.testutil;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Deterministic, manually advanced clock for tests. Thread-safe. */
public final class MutableClock extends Clock {
    private volatile Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    public static MutableClock startingAt(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant));
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
