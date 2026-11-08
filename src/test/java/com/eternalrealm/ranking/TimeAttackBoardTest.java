package com.eternalrealm.ranking;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.eternalrealm.ranking.BossDifficulty.EX;
import static com.eternalrealm.ranking.BossDifficulty.HARD;
import static com.eternalrealm.ranking.BossDifficulty.NORMAL;
import static com.eternalrealm.ranking.RankingFixtures.run;
import static com.eternalrealm.ranking.RankingFixtures.uuid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TimeAttackBoardTest {
    private static final TimeAttackRules RULES = TimeAttackRules.defaults();

    private static TimeAttackRun withAdminCommand(TimeAttackRun run) {
        return new TimeAttackRun(run.bossId(), run.difficulty(), run.runId(), run.startTick(), run.endTick(),
                run.startedAtMillis(), run.clearedAtMillis(), run.members(), false, true, false);
    }

    @Test
    public void anAcceptedRunEntersItsBoardWithEveryRecordFlag() {
        TimeAttackBoard board = new TimeAttackBoard();
        TimeAttackRun first = run(1, "b1", NORMAL, 2_400, 60_000, 1, 2, 3);
        TimeAttackSubmission submission = board.submit(first, RULES);
        assertTrue(submission.accepted());
        assertTrue(submission.bracketRecord());
        assertTrue(submission.partyBest());
        assertTrue(submission.firstClear());
        assertEquals(List.of(uuid(1), uuid(2), uuid(3)), submission.personalBests());
        assertEquals(Optional.of(first), board.best("b1", NORMAL, PartyBracket.PARTY));
        assertEquals(List.of(new TimeAttackEntry(1, first)), board.top("b1", NORMAL, PartyBracket.PARTY, 10));
        assertEquals(OptionalInt.of(1), board.rankOf(first.runId()));
        assertEquals(1, board.size());
        assertTrue(board.best("b1", NORMAL, PartyBracket.SOLO).isEmpty());
        assertTrue(board.best("b1", HARD, PartyBracket.PARTY).isEmpty());
        assertEquals(List.of(), board.top("b2", NORMAL, PartyBracket.PARTY, 10));
    }

    @Test
    public void aRejectedRunChangesNothing() {
        TimeAttackBoard board = new TimeAttackBoard();
        TimeAttackSubmission submission = board.submit(withAdminCommand(run(1, "b1", NORMAL, 2_400, 0, 1)), RULES);
        assertFalse(submission.accepted());
        assertEquals(List.of(TimeAttackValidator.ADMIN_COMMAND), submission.validation().reasons());
        assertFalse(submission.partyBest() || submission.bracketRecord() || submission.firstClear());
        assertEquals(List.of(), submission.personalBests());
        assertEquals(0, board.size());
        assertTrue("a cheated clear is never a first clear", board.firstClear("b1").isEmpty());
        assertTrue(board.personalBest(uuid(1), "b1", NORMAL).isEmpty());
    }

    @Test
    public void bracketsAndDifficultiesHaveTheirOwnBoards() {
        TimeAttackBoard board = new TimeAttackBoard();
        TimeAttackRun solo = run(1, "b1", NORMAL, 3_000, 0, 1);
        TimeAttackRun party = run(2, "b1", NORMAL, 2_000, 1, 1, 2);
        TimeAttackRun raid = run(3, "b1", NORMAL, 5_000, 2, 1, 2, 3, 4, 5, 6);
        TimeAttackRun hard = run(4, "b1", HARD, 4_000, 3, 1);
        for (TimeAttackRun run : List.of(solo, party, raid, hard)) {
            assertTrue(board.submit(run, RULES).bracketRecord());
        }
        assertEquals(Optional.of(solo), board.best("b1", NORMAL, PartyBracket.SOLO));
        assertEquals(Optional.of(party), board.best("b1", NORMAL, PartyBracket.PARTY));
        assertEquals(Optional.of(raid), board.best("b1", NORMAL, PartyBracket.RAID));
        assertEquals(Optional.of(hard), board.best("b1", HARD, PartyBracket.SOLO));
        assertEquals(List.of(solo, party, raid, hard).size(), board.bests().size());
        assertEquals(List.of(party, raid, solo, hard).stream().sorted(TimeAttackBoard.STORAGE_ORDER).toList(),
                board.bests());
    }

    @Test
    public void aPartyIsRankedOnceWithItsBestRun() {
        TimeAttackBoard board = new TimeAttackBoard();
        TimeAttackRun first = run(1, "b2", NORMAL, 3_000, 10_000, 1, 2);
        TimeAttackRun slower = run(2, "b2", NORMAL, 3_500, 20_000, 2, 1);
        TimeAttackRun faster = run(3, "b2", NORMAL, 2_800, 30_000, 1, 2);
        board.submit(first, RULES);

        TimeAttackSubmission notBetter = board.submit(slower, RULES);
        assertTrue(notBetter.accepted());
        assertFalse("member order does not make another party", notBetter.partyBest());
        assertFalse(notBetter.bracketRecord());
        assertEquals(List.of(), notBetter.personalBests());

        TimeAttackSubmission better = board.submit(faster, RULES);
        assertTrue(better.partyBest() && better.bracketRecord());
        assertEquals(List.of(uuid(1), uuid(2)), better.personalBests());
        assertEquals(1, board.size());
        assertEquals(List.of(new TimeAttackEntry(1, faster)), board.top("b2", NORMAL, PartyBracket.PARTY, 10));
        assertEquals(OptionalInt.empty(), board.rankOf(first.runId()));
    }

    @Test
    public void rankingIsByDurationThenClearTimeThenRunId() {
        TimeAttackBoard board = new TimeAttackBoard();
        TimeAttackRun slowLate = run(1, "b3", HARD, 3_000, 50_000, 1, 2);
        TimeAttackRun fastest = run(2, "b3", HARD, 2_500, 60_000, 3, 4);
        TimeAttackRun tieEarly = run(3, "b3", HARD, 3_000, 40_000, 5, 6);
        TimeAttackRun tieEarlyHigherId = run(5, "b3", HARD, 3_000, 40_000, 7, 8);
        for (TimeAttackRun run : List.of(slowLate, tieEarlyHigherId, fastest, tieEarly)) {
            assertTrue(board.submit(run, RULES).partyBest());
        }
        List<TimeAttackEntry> top = board.top("b3", HARD, PartyBracket.PARTY, 10);
        assertEquals(List.of(fastest, tieEarly, tieEarlyHigherId, slowLate), top.stream().map(TimeAttackEntry::run).toList());
        assertEquals(List.of(1, 2, 3, 4), top.stream().map(TimeAttackEntry::rank).toList());
        assertEquals(2_500, top.get(0).durationTicks());
        assertEquals(2, board.top("b3", HARD, PartyBracket.PARTY, 2).size());
        assertEquals(List.of(), board.top("b3", HARD, PartyBracket.PARTY, 0));
        assertEquals(OptionalInt.of(3), board.rankOf(tieEarlyHigherId.runId()));
        assertThrows(IllegalArgumentException.class, () -> board.top("b3", HARD, PartyBracket.PARTY, -1));
    }

    @Test
    public void personalBestsSpanBracketsButNotBossesOrDifficulties() {
        TimeAttackBoard board = new TimeAttackBoard();
        TimeAttackRun solo = run(1, "b4", NORMAL, 3_000, 0, 1);
        TimeAttackRun duo = run(2, "b4", NORMAL, 2_500, 1_000, 1, 2);
        TimeAttackRun otherDuo = run(3, "b4", NORMAL, 2_700, 2_000, 1, 3);
        board.submit(solo, RULES);
        assertEquals(List.of(uuid(1), uuid(2)), board.submit(duo, RULES).personalBests());
        TimeAttackSubmission third = board.submit(otherDuo, RULES);
        assertTrue("a new party is ranked", third.partyBest());
        assertEquals("only the member without a better time improved", List.of(uuid(3)), third.personalBests());

        assertEquals(Optional.of(duo), board.personalBest(uuid(1), "b4", NORMAL));
        assertEquals(Optional.of(duo), board.personalBest(uuid(2), "b4", NORMAL));
        assertEquals(Optional.of(otherDuo), board.personalBest(uuid(3), "b4", NORMAL));
        assertTrue(board.personalBest(uuid(1), "b4", HARD).isEmpty());
        assertTrue(board.personalBest(uuid(9), "b4", NORMAL).isEmpty());

        TimeAttackRun otherBoss = run(4, "b5", NORMAL, 4_000, 3_000, 1);
        board.submit(otherBoss, RULES);
        assertEquals(List.of(duo, otherBoss), board.personalBests(uuid(1)));
    }

    @Test
    public void theFirstClearIsTheEarliestAcceptedClearAndIsKeptWhenThePartyImproves() {
        TimeAttackBoard board = new TimeAttackBoard();
        TimeAttackRun first = run(1, "final", HARD, 20_000, 100_000, 1, 2, 3, 4, 5, 6);
        TimeAttackRun laterOtherDifficulty = run(2, "final", NORMAL, 15_000, 200_000, 7);
        TimeAttackRun improved = run(3, "final", HARD, 18_000, 300_000, 1, 2, 3, 4, 5, 6);
        assertTrue(board.submit(first, RULES).firstClear());
        assertFalse(board.submit(laterOtherDifficulty, RULES).firstClear());
        TimeAttackSubmission improvement = board.submit(improved, RULES);
        assertTrue(improvement.partyBest());
        assertFalse(improvement.firstClear());
        assertEquals(Optional.of(first), board.firstClear("final"));
        TimeAttackSnapshot snapshot = board.snapshot();
        assertFalse("replaced as party best", snapshot.bestRuns().contains(first));
        assertEquals(List.of(first), snapshot.firstClears());
        assertTrue(board.firstClear("b1").isEmpty());
    }

    @Test
    public void resubmittingTheSameRunChangesNothing() {
        TimeAttackBoard board = new TimeAttackBoard();
        TimeAttackRun run = run(1, "b1", NORMAL, 2_000, 0, 1);
        board.submit(run, RULES);
        TimeAttackSnapshot before = board.snapshot();
        TimeAttackSubmission again = board.submit(run, RULES);
        assertTrue(again.accepted());
        assertFalse(again.partyBest() || again.bracketRecord() || again.firstClear());
        assertEquals(before, board.snapshot());
    }

    @Test
    public void reusingARunIdForAnotherRunIsAnError() {
        TimeAttackBoard board = new TimeAttackBoard();
        board.submit(run(1, "b1", NORMAL, 2_000, 0, 1), RULES);
        assertThrows(IllegalArgumentException.class, () -> board.submit(run(1, "b2", NORMAL, 2_000, 0, 1), RULES));
        assertEquals(1, board.size());
    }

    @Test
    public void removingARunRecomputesPersonalBestsAndTheFirstClear() {
        TimeAttackBoard board = new TimeAttackBoard();
        TimeAttackRun solo = run(1, "b6", NORMAL, 3_000, 1_000, 1);
        TimeAttackRun duo = run(2, "b6", NORMAL, 2_000, 2_000, 1, 2);
        TimeAttackRun lateSolo = run(3, "b6", NORMAL, 2_500, 3_000, 3);
        TimeAttackRun earlierSolo = run(4, "b6", NORMAL, 2_600, 2_500, 4);
        for (TimeAttackRun run : List.of(solo, duo, lateSolo, earlierSolo)) {
            board.submit(run, RULES);
        }
        assertTrue(board.removeRun(duo.runId()));
        assertEquals(Optional.of(solo), board.personalBest(uuid(1), "b6", NORMAL));
        assertTrue(board.personalBest(uuid(2), "b6", NORMAL).isEmpty());
        assertTrue(board.best("b6", NORMAL, PartyBracket.PARTY).isEmpty());

        assertEquals(Optional.of(solo), board.firstClear("b6"));
        assertTrue(board.removeRun(solo.runId()));
        assertEquals("the earliest kept clear takes over", Optional.of(earlierSolo), board.firstClear("b6"));
        assertTrue(board.personalBest(uuid(1), "b6", NORMAL).isEmpty());
        assertFalse(board.removeRun(solo.runId()));
        assertFalse(board.removeRun(uuid(999)));
        assertEquals(2, board.size());
    }

    @Test
    public void restoringASnapshotRebuildsRankingsAndPersonalBests() {
        TimeAttackBoard board = new TimeAttackBoard();
        board.submit(run(1, "b1", NORMAL, 3_000, 0, 1), RULES);
        board.submit(run(2, "b1", NORMAL, 2_000, 1, 1, 2), RULES);
        board.submit(run(3, "b1", NORMAL, 2_100, 2, 3, 4), RULES);
        board.submit(run(4, "final", EX, 30_000, 3, 1, 2, 3, 4, 5, 6, 7), RULES);
        board.submit(run(5, "final", EX, 29_000, 4, 1, 2, 3, 4, 5, 6, 7), RULES);
        TimeAttackSnapshot snapshot = board.snapshot();

        TimeAttackBoard restored = new TimeAttackBoard();
        restored.restore(snapshot);
        assertEquals(snapshot, restored.snapshot());
        assertEquals(board.top("b1", NORMAL, PartyBracket.PARTY, 10), restored.top("b1", NORMAL, PartyBracket.PARTY, 10));
        assertEquals(board.personalBests(uuid(1)), restored.personalBests(uuid(1)));
        assertEquals(board.firstClear("final"), restored.firstClear("final"));
        assertEquals(board.bests(), restored.bests());
        restored.restore(TimeAttackSnapshot.EMPTY);
        assertEquals(0, restored.size());
        assertTrue(restored.firstClear("final").isEmpty());
    }

    @Test
    public void snapshotsRejectDataNoBoardCanProduce() {
        TimeAttackRun a = run(1, "b1", NORMAL, 2_000, 0, 1);
        TimeAttackRun samePartyOtherRun = run(2, "b1", NORMAL, 2_500, 0, 1);
        TimeAttackRun sameIdOtherRun = run(1, "b2", NORMAL, 2_000, 0, 2);
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackSnapshot(List.of(a, a), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackSnapshot(List.of(a, samePartyOtherRun), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeAttackSnapshot(List.of(withAdminCommand(a)), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeAttackSnapshot(List.of(), List.of(a, samePartyOtherRun)));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackSnapshot(List.of(a), List.of(sameIdOtherRun)));
        TimeAttackRun empty = new TimeAttackRun("b1", NORMAL, uuid(5), 0, 100, 0, 5_000, List.of(), false, false, false);
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackSnapshot(List.of(empty), List.of()));
        assertEquals(List.of(a), new TimeAttackSnapshot(List.of(a), List.of(a)).firstClears());
    }
}
