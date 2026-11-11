package com.eternalrealm.world.field;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Which ヌシ are out in a field (the owner, 2026-11-11: 「各フィールドに同時湧きするのは2体まで」「20分毎に倒されていないヌシは
 * 消えて新しく違うヌシを出現させて」). At most {@value #PER_FIELD} of a field's lords are out at a time; every
 * {@value #ROTATION_MINUTES} minutes the line-up changes: the lords that were resting come out first and the rest of the
 * places go to a random pick of those that were out, so every change brings a lord that was not there (all of them
 * when a field has enough lords). Those that go and were not beaten leave the field; a lord beaten during a turn stays
 * down until the next change. A lord still fighting players when its turn ends leaves when the fight is over
 * ({@value #ENGAGED_SECONDS} seconds without a player's blow). Pure.
 */
public final class LordRoster {
    /** Lords of one field out at the same time. */
    public static final int PER_FIELD = 2;
    public static final int ROTATION_MINUTES = 20;
    /** Ticks between two changes of the line-up. */
    public static final long ROTATION_TICKS = ROTATION_MINUTES * 60L * 20L;
    public static final int ENGAGED_SECONDS = 60;
    /** A lord hit by a player this recently is in a fight: it does not leave before the fight is over. */
    public static final long ENGAGED_TICKS = ENGAGED_SECONDS * 20L;

    private LordRoster() {
    }

    /** The first line-up of a field: {@value #PER_FIELD} of its lords (or all, if it has fewer), at random. */
    public static List<String> first(List<String> fieldLords, RandomGenerator random) {
        List<String> pool = shuffled(fieldLords, random);
        return List.copyOf(pool.subList(0, Math.min(PER_FIELD, pool.size())));
    }

    /**
     * The next line-up of a field whose lords are {@code fieldLords} and whose line-up was {@code out}: those that
     * were resting first (in random order), then a random pick of those that were out, {@value #PER_FIELD} at most.
     */
    public static List<String> next(List<String> fieldLords, List<String> out, RandomGenerator random) {
        List<String> resting = new ArrayList<>();
        for (String id : fieldLords) {
            if (!out.contains(id)) {
                resting.add(id);
            }
        }
        List<String> going = new ArrayList<>();
        for (String id : out) {
            if (fieldLords.contains(id)) {
                going.add(id);
            }
        }
        List<String> next = new ArrayList<>();
        for (String id : shuffled(resting, random)) {
            if (next.size() < PER_FIELD) {
                next.add(id);
            }
        }
        for (String id : shuffled(going, random)) {
            if (next.size() < PER_FIELD) {
                next.add(id);
            }
        }
        return List.copyOf(next);
    }

    /** Whether a lord last hit by a player at {@code lastHit} (a tick, or a negative number for never) still fights. */
    public static boolean engaged(long lastHit, long now) {
        return lastHit >= 0 && now - lastHit < ENGAGED_TICKS;
    }

    private static List<String> shuffled(List<String> ids, RandomGenerator random) {
        List<String> copy = new ArrayList<>(ids);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            String swap = copy.get(i);
            copy.set(i, copy.get(j));
            copy.set(j, swap);
        }
        return copy;
    }
}
