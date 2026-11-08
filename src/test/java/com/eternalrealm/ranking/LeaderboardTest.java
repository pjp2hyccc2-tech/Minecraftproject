package com.eternalrealm.ranking;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LeaderboardTest {
    private static final UUID A = RankingFixtures.uuid(1);
    private static final UUID B = RankingFixtures.uuid(2);
    private static final UUID C = RankingFixtures.uuid(3);

    @Test
    public void highestScoreFirstWithDistinctRanks() {
        Leaderboard<UUID> board = new Leaderboard<>();
        board.update(A, "Alice", 10, 1);
        board.update(B, "Bob", 30, 2);
        board.update(C, "Carol", 20, 3);
        assertEquals(List.of(new RankingEntry<>(1, B, "Bob", 30), new RankingEntry<>(2, C, "Carol", 20),
                new RankingEntry<>(3, A, "Alice", 10)), board.top(10));
        assertEquals(OptionalInt.of(3), board.rankOf(A));
        assertEquals(new RankingEntry<>(2, C, "Carol", 20), board.entryOf(C).orElseThrow());
        assertEquals(3, board.size());
    }

    @Test
    public void tiesGoToWhoeverReachedTheScoreFirstThenToTheLowerId() {
        Leaderboard<UUID> board = new Leaderboard<>();
        board.update(C, "Carol", 50, 100);
        board.update(B, "Bob", 50, 200);
        board.update(A, "Alice", 50, 200);
        assertEquals(List.of(C, A, B), ids(board.top(3)));
    }

    @Test
    public void refreshingAnUnchangedScoreKeepsTheTieTime() {
        Leaderboard<UUID> board = new Leaderboard<>();
        board.update(B, "Bob", 100, 5);
        board.update(A, "Alice", 100, 10);
        assertFalse("same score and name: no change", board.update(B, "Bob", 100, 50));
        assertEquals(List.of(B, A), ids(board.top(2)));
        assertEquals(5, board.updatedAtOf(B).orElseThrow());

        board.update(B, "Bob", 90, 60);
        board.update(B, "Bob", 100, 70);
        assertEquals("reaching the score again counts from the new time", List.of(A, B), ids(board.top(2)));
    }

    @Test
    public void renameKeepsThePosition() {
        Leaderboard<UUID> board = new Leaderboard<>();
        board.update(A, "Alice", 100, 5);
        board.update(B, "Bob", 100, 10);
        assertTrue(board.update(A, "Alicia", 100, 99));
        assertEquals(new RankingEntry<>(1, A, "Alicia", 100), board.top(1).get(0));
        assertEquals(5, board.updatedAtOf(A).orElseThrow());
    }

    @Test
    public void scoresMayGoDownAndEntriesMayBeRemoved() {
        Leaderboard<UUID> board = new Leaderboard<>();
        board.update(A, "Alice", 100, 1);
        board.update(B, "Bob", 50, 1);
        board.update(A, "Alice", 10, 2);
        assertEquals(List.of(B, A), ids(board.top(5)));
        assertTrue(board.remove(B));
        assertFalse(board.remove(B));
        assertEquals(OptionalInt.empty(), board.rankOf(B));
        assertTrue(board.entryOf(B).isEmpty());
        assertTrue(board.updatedAtOf(B).isEmpty());
        assertEquals(OptionalInt.of(1), board.rankOf(A));
        board.clear();
        assertEquals(0, board.size());
        assertEquals(List.of(), board.top(3));
    }

    @Test
    public void topReturnsAtMostTheRequestedCount() {
        Leaderboard<UUID> board = new Leaderboard<>();
        assertEquals(List.of(), board.top(10));
        board.update(A, "Alice", 1, 1);
        board.update(B, "Bob", 2, 1);
        assertEquals(List.of(), board.top(0));
        assertEquals(List.of(B), ids(board.top(1)));
        assertEquals(2, board.top(Integer.MAX_VALUE).size());
        assertThrows(IllegalArgumentException.class, () -> board.top(-1));
        assertThrows(UnsupportedOperationException.class, () -> board.top(2).clear());
    }

    @Test
    public void invalidUpdatesAreRejectedWithoutChanges() {
        Leaderboard<UUID> board = new Leaderboard<>();
        board.update(A, "Alice", 10, 1);
        assertThrows(NullPointerException.class, () -> board.update(null, "X", 1, 1));
        assertThrows(NullPointerException.class, () -> board.update(B, null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> board.update(B, "", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> board.update(B, "x".repeat(65), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> board.update(A, "Alice", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> board.update(A, "Alice", 5, -1));
        assertEquals(List.of(new RankingEntry<>(1, A, "Alice", 10)), board.top(5));
        board.update(B, "x".repeat(64), 0, 0);
        assertEquals(2, board.size());
    }

    @Test
    public void guildBoardsUseStringIds() {
        Leaderboard<String> board = new Leaderboard<>();
        board.update("guild_b", "Knights", 7, 1);
        board.update("guild_a", "Mages", 7, 1);
        assertEquals(List.of("guild_a", "guild_b"), board.top(2).stream().map(RankingEntry::id).toList());
    }

    /**
     * Thousands of random updates, renames and removals: the board must always equal a straightforward sort of a
     * reference map that applies the documented rules, and every rank lookup must agree with it.
     */
    @Test
    public void matchesAReferenceSortUnderRandomUpdates() {
        Random random = new Random(20261108L);
        Leaderboard<UUID> board = new Leaderboard<>();
        Map<UUID, long[]> reference = new HashMap<>(); // id -> {score, reachedAt}
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 3_000; i++) {
            ids.add(new UUID(random.nextLong(), random.nextLong()));
        }
        for (int step = 0; step < 30_000; step++) {
            UUID id = ids.get(random.nextInt(ids.size()));
            if (random.nextInt(10) == 0) {
                assertEquals(reference.remove(id) != null, board.remove(id));
                continue;
            }
            long score = random.nextInt(200); // few distinct scores: many ties
            long time = step;
            long[] current = reference.get(id);
            if (current == null || current[0] != score) {
                reference.put(id, new long[]{score, time});
            }
            board.update(id, "P" + random.nextInt(5), score, time);
        }
        List<UUID> expected = new ArrayList<>(reference.keySet());
        expected.sort(Comparator.<UUID>comparingLong(id -> -reference.get(id)[0])
                .thenComparingLong(id -> reference.get(id)[1])
                .thenComparing(Comparator.naturalOrder()));
        List<RankingEntry<UUID>> top = board.top(Integer.MAX_VALUE);
        assertEquals(expected, ids(top));
        assertEquals(expected.size(), board.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(OptionalInt.of(i + 1), board.rankOf(expected.get(i)));
            assertEquals(reference.get(expected.get(i))[0], top.get(i).score());
        }
    }

    private static <K> List<K> ids(List<RankingEntry<K>> entries) {
        return entries.stream().map(RankingEntry::id).toList();
    }
}
