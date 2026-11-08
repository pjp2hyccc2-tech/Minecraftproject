package com.eternalrealm.core;

import com.eternalrealm.testutil.MutableClock;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PeriodicTriggerTest {
    @Test
    public void firesOncePerInterval() {
        MutableClock clock = MutableClock.startingAt("2026-01-01T00:00:00Z");
        PeriodicTrigger trigger = new PeriodicTrigger(Duration.ofSeconds(10), clock);

        assertFalse(trigger.poll());
        clock.advance(Duration.ofSeconds(9));
        assertFalse(trigger.poll());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(trigger.poll());
        assertFalse("must not fire twice for the same interval", trigger.poll());
        clock.advance(Duration.ofSeconds(10));
        assertTrue(trigger.poll());
    }

    @Test
    public void zeroIntervalDisablesTrigger() {
        MutableClock clock = MutableClock.startingAt("2026-01-01T00:00:00Z");
        PeriodicTrigger trigger = new PeriodicTrigger(Duration.ZERO, clock);
        clock.advance(Duration.ofDays(1));
        assertFalse(trigger.poll());
        assertFalse(trigger.isEnabled());
    }

    @Test
    public void changingIntervalRestartsCountdown() {
        MutableClock clock = MutableClock.startingAt("2026-01-01T00:00:00Z");
        PeriodicTrigger trigger = new PeriodicTrigger(Duration.ofSeconds(10), clock);
        clock.advance(Duration.ofSeconds(8));
        trigger.setInterval(Duration.ofSeconds(5));
        clock.advance(Duration.ofSeconds(4));
        assertFalse(trigger.poll());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(trigger.poll());
    }
}
