package com.eternalrealm.social.phone;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How fast a player may use the 魔導スマホ (pure): per action a small burst that refills over time, and a daily cap for
 * the heavy ones (posts, photos). Not saved: a restart gives everyone a fresh burst.
 */
public final class PhoneRates {
    private static final long DAY = 86_400_000L;

    public enum Action {
        POST(2, 30_000L, 40),
        COMMENT(4, 5_000L, 300),
        MESSAGE(6, 1_000L, 3_000),
        LIKE(10, 500L, 3_000),
        FRIEND(5, 12_000L, 100),
        PHOTO(2, 15_000L, 30);

        private final int burst;
        private final long refillMillis;
        private final int perDay;

        Action(int burst, long refillMillis, int perDay) {
            this.burst = burst;
            this.refillMillis = refillMillis;
            this.perDay = perDay;
        }

        public int burst() {
            return burst;
        }

        public int perDay() {
            return perDay;
        }
    }

    private static final class Bucket {
        double tokens;
        long at;
        long day;
        int usedToday;
    }

    private final Map<UUID, EnumMap<Action, Bucket>> buckets = new HashMap<>();

    /** Uses one of the player's {@code action}s now, if they have one left. */
    public boolean tryUse(UUID player, Action action, long nowMillis) {
        Bucket bucket = buckets.computeIfAbsent(player, id -> new EnumMap<>(Action.class)).computeIfAbsent(action,
                a -> {
                    Bucket fresh = new Bucket();
                    fresh.tokens = a.burst;
                    fresh.at = nowMillis;
                    fresh.day = nowMillis / DAY;
                    return fresh;
                });
        long elapsed = Math.max(0L, nowMillis - bucket.at);
        bucket.tokens = Math.min(action.burst, bucket.tokens + elapsed / (double) action.refillMillis);
        bucket.at = Math.max(bucket.at, nowMillis);
        long day = nowMillis / DAY;
        if (day != bucket.day) {
            bucket.day = day;
            bucket.usedToday = 0;
        }
        if (bucket.tokens < 1.0 || bucket.usedToday >= action.perDay) {
            return false;
        }
        bucket.tokens -= 1.0;
        bucket.usedToday++;
        return true;
    }

    /** Whether the day's cap (not just the burst) stops the player. */
    public boolean dailyCapReached(UUID player, Action action, long nowMillis) {
        EnumMap<Action, Bucket> mine = buckets.get(player);
        Bucket bucket = mine == null ? null : mine.get(action);
        return bucket != null && bucket.day == nowMillis / DAY && bucket.usedToday >= action.perDay;
    }

    public void forget(UUID player) {
        buckets.remove(player);
    }
}
