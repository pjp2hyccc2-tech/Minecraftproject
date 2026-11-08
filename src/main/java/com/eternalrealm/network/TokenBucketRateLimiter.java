package com.eternalrealm.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Per-key token bucket. Each key may perform {@code capacity} actions in a burst and then
 * {@code refillPerSecond} actions per second on average.
 */
public final class TokenBucketRateLimiter<K> {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final int capacity;
    private final double refillPerNano;
    private final LongSupplier nanoClock;
    private final Map<K, Bucket> buckets = new HashMap<>();

    public TokenBucketRateLimiter(int capacity, double refillPerSecond, LongSupplier nanoClock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1: " + capacity);
        }
        if (!(refillPerSecond > 0) || Double.isInfinite(refillPerSecond)) {
            throw new IllegalArgumentException("refillPerSecond must be positive: " + refillPerSecond);
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / NANOS_PER_SECOND;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    public static <K> TokenBucketRateLimiter<K> systemClock(int capacity, double refillPerSecond) {
        return new TokenBucketRateLimiter<>(capacity, refillPerSecond, System::nanoTime);
    }

    /** @return {@code true} if the action is allowed (one token consumed) */
    public synchronized boolean tryAcquire(K key) {
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, now));
        long elapsed = Math.max(0L, now - bucket.lastRefillNanos);
        bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * refillPerNano);
        bucket.lastRefillNanos = now;
        if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0;
            return true;
        }
        return false;
    }

    public synchronized void forget(K key) {
        buckets.remove(key);
    }

    public synchronized int trackedKeys() {
        return buckets.size();
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;

        private Bucket(double tokens, long lastRefillNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }
    }
}
