package com.eternalrealm.network;

import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RateLimitAndViolationTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    public void bucketAllowsBurstThenRefills() {
        AtomicLong nanos = new AtomicLong();
        TokenBucketRateLimiter<String> limiter = new TokenBucketRateLimiter<>(3, 2.0, nanos::get);
        assertTrue(limiter.tryAcquire("p"));
        assertTrue(limiter.tryAcquire("p"));
        assertTrue(limiter.tryAcquire("p"));
        assertFalse("burst exhausted", limiter.tryAcquire("p"));

        nanos.addAndGet(SECOND); // +2 tokens
        assertTrue(limiter.tryAcquire("p"));
        assertTrue(limiter.tryAcquire("p"));
        assertFalse(limiter.tryAcquire("p"));

        nanos.addAndGet(100 * SECOND); // refill never exceeds capacity
        assertTrue(limiter.tryAcquire("p"));
        assertTrue(limiter.tryAcquire("p"));
        assertTrue(limiter.tryAcquire("p"));
        assertFalse(limiter.tryAcquire("p"));
    }

    @Test
    public void bucketsAreIndependentPerKeyAndCanBeForgotten() {
        AtomicLong nanos = new AtomicLong();
        TokenBucketRateLimiter<String> limiter = new TokenBucketRateLimiter<>(1, 1.0, nanos::get);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
        assertEquals(2, limiter.trackedKeys());
        limiter.forget("a");
        assertEquals(1, limiter.trackedKeys());
        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    public void bucketValidatesConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter<String>(0, 1.0, System::nanoTime));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter<String>(1, 0.0, System::nanoTime));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter<String>(1, Double.NaN, System::nanoTime));
    }

    @Test
    public void violationsTriggerKickWithinWindow() {
        AtomicLong millis = new AtomicLong();
        ViolationTracker tracker = new ViolationTracker(3, 1_000, millis::get);
        UUID player = UUID.randomUUID();
        assertFalse(tracker.record(player).shouldKick());
        assertFalse(tracker.record(player).shouldKick());
        ViolationTracker.Verdict third = tracker.record(player);
        assertEquals(3, third.countInWindow());
        assertTrue(third.shouldKick());
    }

    @Test
    public void violationWindowResets() {
        AtomicLong millis = new AtomicLong();
        ViolationTracker tracker = new ViolationTracker(2, 1_000, millis::get);
        UUID player = UUID.randomUUID();
        tracker.record(player);
        millis.addAndGet(1_000);
        ViolationTracker.Verdict verdict = tracker.record(player);
        assertEquals(1, verdict.countInWindow());
        assertFalse(verdict.shouldKick());
    }

    @Test
    public void zeroThresholdNeverKicks() {
        ViolationTracker tracker = new ViolationTracker(0, 1_000, () -> 0L);
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 100; i++) {
            assertFalse(tracker.record(player).shouldKick());
        }
    }
}
