package com.eternalrealm.story;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The server's story progress: how many of the main bosses (b1..b8 and the King of the End) have been defeated
 * server-wide, 0..{@link #MAX}. Town NPCs, the journal chapter ({@link Chapter#ofProgress}) and the days follow it.
 * The world state should keep the set of bosses with a first server-wide kill; this class only counts it.
 */
public final class StoryProgress {
    /** Main bosses in story order. */
    public static final List<String> MAIN_BOSSES = List.of("b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "final");
    /** Every main boss defeated: the King of the End has fallen. */
    public static final int MAX = 9;

    private StoryProgress() {
    }

    /** Number of distinct main bosses among {@code defeatedBossIds} (other ids, e.g. EX bosses, are ignored). */
    public static int of(Collection<String> defeatedBossIds) {
        Objects.requireNonNull(defeatedBossIds, "defeatedBossIds");
        Set<String> defeated = new HashSet<>();
        for (String bossId : defeatedBossIds) {
            if (isMainBoss(bossId)) {
                defeated.add(bossId);
            }
        }
        return defeated.size();
    }

    public static boolean isMainBoss(String bossId) {
        return bossId != null && MAIN_BOSSES.contains(bossId);
    }

    /** Whether the King of the End has fallen at this progress. */
    public static boolean isComplete(int storyProgress) {
        return storyProgress >= MAX;
    }
}
