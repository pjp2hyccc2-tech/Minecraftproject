package com.eternalrealm.ranking;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static com.eternalrealm.ranking.RankingFixtures.NOW;
import static com.eternalrealm.ranking.RankingFixtures.SEASON;
import static com.eternalrealm.ranking.RankingFixtures.T0;
import static com.eternalrealm.ranking.RankingFixtures.run;
import static com.eternalrealm.ranking.RankingFixtures.statsWith;
import static com.eternalrealm.ranking.RankingFixtures.uuid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RankingBoardTest {
    private static final RankingRules RULES = RankingRules.defaults();
    /** COMBAT: 20 points per monster, PvP wins do not count. */
    private static final RankingRules CHANGED_RULES = new RankingRules(20, 50, 10, 0, 100, 100, RULES.overallWeights());
    private static final TimeAttackRules TIME_ATTACK = TimeAttackRules.defaults();
    private static final UUID A = uuid(1);
    private static final UUID B = uuid(2);
    private static final UUID C = uuid(3);
    private static final UUID D = uuid(4);

    private static RankingBoard board() {
        return new RankingBoard(SEASON, RULES);
    }

    private static List<UUID> ids(List<RankingEntry<UUID>> entries) {
        return entries.stream().map(RankingEntry::id).toList();
    }

    @Test
    public void everyPlayerCategoryIsRankedWithTheCalculatorScore() {
        RankingBoard board = board();
        SeasonStats stats = RankingFixtures.distinctStats(10);
        assertTrue(board.updatePlayer(A, "Alice", stats, T0));
        for (RankingCategory category : RankingCategory.playerCategories()) {
            assertEquals(category.id(), new RankingEntry<>(1, A, "Alice", RankingCalculator.score(category, stats, RULES)),
                    board.entryOf(category, A).orElseThrow());
            assertEquals(1, board.size(category));
        }
        assertEquals(stats, board.statsOf(A).orElseThrow());
        assertTrue(board.statsOf(B).isEmpty());
    }

    @Test
    public void aZeroScoreIsNotRanked() {
        RankingBoard board = board();
        board.updatePlayer(A, "Alice", statsWith(3, 500, SeasonCounter.ITEMS_CRAFTED, 2), T0);
        assertEquals(OptionalInt.of(1), board.rankOf(RankingCategory.LEVEL, A));
        assertEquals(OptionalInt.of(1), board.rankOf(RankingCategory.CRAFTING, A));
        assertEquals(OptionalInt.of(1), board.rankOf(RankingCategory.OVERALL, A));
        for (RankingCategory category : List.of(RankingCategory.COMBAT, RankingCategory.EXPLORATION,
                RankingCategory.BOSS, RankingCategory.PARTY)) {
            assertEquals(category.id(), OptionalInt.empty(), board.rankOf(category, A));
            assertEquals(0, board.size(category));
        }
        board.updatePlayer(A, "Alice", statsWith(3, 0, SeasonCounter.ITEMS_CRAFTED, 2), T0 + 1);
        assertEquals("an operator correction to 0 unranks", OptionalInt.empty(), board.rankOf(RankingCategory.LEVEL, A));
    }

    @Test
    public void unchangedStatsAreNoChangeButARenameIs() {
        RankingBoard board = board();
        SeasonStats stats = statsWith(5, 1_000);
        assertTrue(board.updatePlayer(A, "Alice", stats, T0));
        long revision = board.revision();
        assertFalse(board.updatePlayer(A, "Alice", stats, T0 + 5));
        assertEquals(revision, board.revision());
        assertTrue(board.updatePlayer(A, "Alicia", stats, T0 + 6));
        assertEquals("Alicia", board.entryOf(RankingCategory.LEVEL, A).orElseThrow().name());
    }

    @Test
    public void tieOrderSurvivesUpdatesOfOtherCategories() {
        RankingBoard board = board();
        board.updatePlayer(A, "Alice", statsWith(1, 0, SeasonCounter.BOSSES_KILLED, 3), T0 + 1);
        board.updatePlayer(B, "Bob", statsWith(1, 0, SeasonCounter.BOSSES_KILLED, 3), T0 + 2);
        assertEquals(List.of(A, B), ids(board.top(RankingCategory.BOSS, 10)));
        board.updatePlayer(A, "Alice", statsWith(1, 0, SeasonCounter.BOSSES_KILLED, 3,
                SeasonCounter.MONSTERS_KILLED, 50), T0 + 3);
        assertEquals("Alice reached 3 boss kills first", List.of(A, B), ids(board.top(RankingCategory.BOSS, 10)));
        board.updatePlayer(B, "Bob", statsWith(1, 0, SeasonCounter.BOSSES_KILLED, 4), T0 + 4);
        assertEquals(List.of(B, A), ids(board.top(RankingCategory.BOSS, 10)));
    }

    @Test
    public void aFrozenBoardIgnoresPlayActionsButAllowsOperatorRemovals() {
        RankingBoard board = board();
        board.updatePlayer(A, "Alice", statsWith(5, 1_000), T0);
        board.updateGuild("g1", "Knights", 10, T0);
        TimeAttackRun run = run(1, "b1", BossDifficulty.NORMAL, 2_000, 0, 1);
        assertTrue(board.submitTimeAttack(run, TIME_ATTACK).accepted());
        assertTrue(board.freeze());
        assertFalse(board.freeze());
        assertTrue(board.isFrozen());

        assertFalse(board.updatePlayer(B, "Bob", statsWith(9, 9_000), T0 + 1));
        assertFalse(board.updatePlayer(A, "Alice", statsWith(9, 9_000), T0 + 1));
        assertEquals(1_000, board.entryOf(RankingCategory.LEVEL, A).orElseThrow().score());
        assertEquals(1, board.size(RankingCategory.LEVEL));
        assertFalse(board.updateGuild("g2", "Mages", 50, T0 + 1));
        TimeAttackSubmission late = board.submitTimeAttack(run(2, "b1", BossDifficulty.NORMAL, 1_000, 5, 2), TIME_ATTACK);
        assertEquals(List.of(TimeAttackValidator.SEASON_CLOSED), late.validation().reasons());
        assertEquals(1, board.timeAttack().size());

        assertTrue(board.removeTimeAttackRun(run.runId()));
        assertTrue(board.removeGuild("g1"));
        assertTrue(board.removePlayer(A));
        assertFalse(board.removePlayer(A));
        assertEquals(0, board.size(RankingCategory.LEVEL));
        assertTrue(board.statsOf(A).isEmpty());
    }

    @Test
    public void newRulesRecomputeAnOpenBoardAndKeepTieTimes() {
        RankingBoard board = board();
        board.updatePlayer(A, "Alice", statsWith(1, 0, SeasonCounter.MONSTERS_KILLED, 10), T0 + 1);
        board.updatePlayer(B, "Bob", statsWith(1, 0, SeasonCounter.PVP_WINS, 4), T0 + 2);
        board.updatePlayer(C, "Carol", statsWith(1, 0, SeasonCounter.MONSTERS_KILLED, 10), T0 + 3);
        assertEquals(List.of(B, A, C), ids(board.top(RankingCategory.COMBAT, 10)));
        long revision = board.revision();

        board.setRules(CHANGED_RULES);
        assertEquals(CHANGED_RULES, board.rules());
        assertEquals(List.of(new RankingEntry<>(1, A, "Alice", 200), new RankingEntry<>(2, C, "Carol", 200)),
                board.top(RankingCategory.COMBAT, 10));
        assertEquals(OptionalInt.empty(), board.rankOf(RankingCategory.COMBAT, B));
        assertTrue(board.revision() > revision);
        revision = board.revision();
        board.setRules(CHANGED_RULES);
        assertEquals("same rules: nothing to do", revision, board.revision());
    }

    @Test
    public void newRulesDoNotChangeTheFinalScoresOfAFrozenBoard() {
        RankingBoard board = board();
        board.updatePlayer(B, "Bob", statsWith(1, 0, SeasonCounter.PVP_WINS, 4), T0);
        board.freeze();
        board.setRules(CHANGED_RULES);
        assertEquals(CHANGED_RULES, board.rules());
        assertEquals(120, board.entryOf(RankingCategory.COMBAT, B).orElseThrow().score());
    }

    @Test
    public void aSnapshotRebuildsTheSameBoard() {
        RankingBoard board = populated();
        RankingSnapshot snapshot = board.snapshot();
        RankingBoard rebuilt = RankingBoard.rebuild(snapshot, RULES);
        assertEquals(snapshot, rebuilt.snapshot());
        assertFalse("a restored board has nothing new to save", rebuilt.isDirty());
        for (RankingCategory category : RankingCategory.playerCategories()) {
            assertEquals(category.id(), board.top(category, 100), rebuilt.top(category, 100));
        }
        assertEquals(board.topGuilds(10), rebuilt.topGuilds(10));
        assertEquals(board.timeAttack().bests(), rebuilt.timeAttack().bests());
        assertEquals(board.timeAttack().firstClear("final"), rebuilt.timeAttack().firstClear("final"));
        assertEquals(board.statsOf(C), rebuilt.statsOf(C));
        assertFalse(rebuilt.isFrozen());
    }

    @Test
    public void anOpenBoardIsRebuiltWithTheCurrentRules() {
        RankingBoard board = board();
        board.updatePlayer(A, "Alice", statsWith(1, 0, SeasonCounter.MONSTERS_KILLED, 10), T0 + 1);
        board.updatePlayer(B, "Bob", statsWith(1, 0, SeasonCounter.PVP_WINS, 4), T0 + 2);
        board.updatePlayer(C, "Carol", statsWith(1, 0, SeasonCounter.MONSTERS_KILLED, 10), T0 + 3);
        RankingBoard rebuilt = RankingBoard.rebuild(board.snapshot(), CHANGED_RULES);
        assertEquals(List.of(new RankingEntry<>(1, A, "Alice", 200), new RankingEntry<>(2, C, "Carol", 200)),
                rebuilt.top(RankingCategory.COMBAT, 10));
    }

    @Test
    public void aFrozenBoardIsRebuiltWithItsFinalScores() {
        RankingBoard board = populated();
        board.freeze();
        RankingSnapshot snapshot = board.snapshot();
        assertTrue(snapshot.frozen());
        RankingBoard rebuilt = RankingBoard.rebuild(snapshot, CHANGED_RULES);
        assertTrue(rebuilt.isFrozen());
        for (RankingCategory category : RankingCategory.playerCategories()) {
            assertEquals(category.id(), board.top(category, 100), rebuilt.top(category, 100));
        }
    }

    @Test
    public void standingsWithoutStoredScoresUseTheirUpdateTimeForTies() {
        SeasonStats same = statsWith(4, 777);
        RankingSnapshot fromCharacterFiles = new RankingSnapshot(SEASON, false, List.of(
                new PlayerStanding(A, "Alice", same, T0 + 10, Map.of()),
                new PlayerStanding(B, "Bob", same, T0 + 5, Map.of())), List.of(), TimeAttackSnapshot.EMPTY);
        RankingBoard rebuilt = RankingBoard.rebuild(fromCharacterFiles, RULES);
        assertEquals(List.of(B, A), ids(rebuilt.top(RankingCategory.LEVEL, 10)));
    }

    @Test
    public void guildsAreRankedByTheirScore() {
        RankingBoard board = board();
        SeasonStats member1 = statsWith(1, 0, SeasonCounter.GUILD_CONTRIBUTION, 60);
        SeasonStats member2 = statsWith(1, 0, SeasonCounter.GUILD_CONTRIBUTION, 40);
        assertTrue(board.updateGuild("g1", "Knights", RankingCalculator.guildScore(List.of(member1, member2)), T0));
        assertTrue(board.updateGuild("g2", "Mages", 100, T0 + 1));
        assertTrue(board.updateGuild("2f0c1e0a-0000-4000-8000-000000000001", "Rangers", 30, T0));
        assertEquals(List.of(new RankingEntry<>(1, "g1", "Knights", 100), new RankingEntry<>(2, "g2", "Mages", 100),
                new RankingEntry<>(3, "2f0c1e0a-0000-4000-8000-000000000001", "Rangers", 30)), board.topGuilds(10));
        assertFalse(board.updateGuild("g1", "Knights", 100, T0 + 9));
        assertTrue("a score of 0 unranks the guild", board.updateGuild("g1", "Knights", 0, T0 + 2));
        assertEquals(OptionalInt.empty(), board.guildRankOf("g1"));
        assertEquals(OptionalInt.of(1), board.guildRankOf("g2"));
        assertEquals("Mages", board.guildEntryOf("g2").orElseThrow().name());
        assertTrue(board.removeGuild("g2"));
        assertFalse(board.removeGuild("g2"));
        assertEquals(1, board.guildCount());
        assertThrows(IllegalArgumentException.class, () -> board.updateGuild("bad id", "X", 1, T0));
        assertThrows(IllegalArgumentException.class, () -> board.updateGuild("../g", "X", 1, T0));
        assertThrows(IllegalArgumentException.class, () -> board.updateGuild("g3", "X", -1, T0));
    }

    @Test
    public void playerQueriesRejectNonPlayerCategories() {
        RankingBoard board = board();
        assertThrows(IllegalArgumentException.class, () -> board.top(RankingCategory.GUILD, 10));
        assertThrows(IllegalArgumentException.class, () -> board.top(RankingCategory.BOSS_TIME_ATTACK, 10));
        assertThrows(IllegalArgumentException.class, () -> board.rankOf(RankingCategory.GUILD, A));
        assertThrows(IllegalArgumentException.class, () -> board.size(RankingCategory.BOSS_TIME_ATTACK));
    }

    @Test
    public void theArchiveHoldsTheTopOfEveryCategoryAndTheRecords() {
        RankingBoard board = populated();
        board.freeze();
        SeasonArchive archive = board.archive(NOW, 2);
        assertEquals(SEASON, archive.seasonId());
        assertEquals(NOW, archive.archivedAt());
        for (RankingCategory category : RankingCategory.playerCategories()) {
            List<String> expected = board.top(category, 2).stream().map(entry -> entry.id().toString()).toList();
            assertEquals(category.id(), expected, archive.top(category).stream().map(RankingEntry::id).toList());
        }
        assertEquals(board.topGuilds(2), archive.top(RankingCategory.GUILD));
        assertEquals(List.of(), archive.top(RankingCategory.BOSS_TIME_ATTACK));
        assertEquals(board.timeAttack().bests(), archive.timeAttackBests());
        assertEquals(board.timeAttack().firstClear("final").orElseThrow(), archive.finalBossFirstClear());

        HeroRecord heroes = board.archive(NOW, RankingBoard.DEFAULT_ARCHIVE_SIZE).heroes();
        assertEquals(SEASON, heroes.seasonId());
        assertEquals(List.of(D, C, B).stream().map(UUID::toString).toList(),
                heroes.podium(RankingCategory.LEVEL).stream().map(RankingEntry::id).toList());
        assertEquals(archive.finalBossFirstClear(), heroes.finalBossFirstClear());
        assertThrows(IllegalArgumentException.class, () -> board.archive(NOW, 0));
    }

    @Test
    public void changesAreTrackedForSaving() {
        RankingBoard board = board();
        assertFalse(board.isDirty());
        board.updatePlayer(A, "Alice", statsWith(2, 300), T0);
        assertTrue(board.isDirty());
        long saved = board.revision();
        board.markSaved(saved);
        assertFalse(board.isDirty());
        board.markSaved(saved - 1);
        assertFalse("saved revisions never move backwards", board.isDirty());

        TimeAttackRun cheated = new TimeAttackRun("b1", BossDifficulty.NORMAL, uuid(50), 0, 1_000, T0, T0 + 50_000,
                List.of(RankingFixtures.member(1)), true, false, false);
        assertFalse(board.submitTimeAttack(cheated, TIME_ATTACK).accepted());
        assertFalse(board.isDirty());
        assertTrue(board.submitTimeAttack(run(1, "b1", BossDifficulty.NORMAL, 2_000, 0, 1), TIME_ATTACK).partyBest());
        assertTrue(board.isDirty());
    }

    @Test
    public void snapshotsRejectInconsistentData() {
        SeasonStats stats = statsWith(1, 10);
        PlayerStanding alice = new PlayerStanding(A, "Alice", stats, T0, Map.of());
        assertThrows(IllegalArgumentException.class, () -> new RankingSnapshot(SEASON, false, List.of(alice, alice),
                List.of(), TimeAttackSnapshot.EMPTY));
        GuildStanding guild = new GuildStanding("g1", "Knights", 5, T0);
        assertThrows(IllegalArgumentException.class, () -> new RankingSnapshot(SEASON, false, List.of(),
                List.of(guild, guild), TimeAttackSnapshot.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new PlayerStanding(A, "Alice", stats, T0,
                Map.of(RankingCategory.GUILD, new PlayerStanding.RankedScore(1, T0))));
        assertThrows(IllegalArgumentException.class, () -> new PlayerStanding.RankedScore(0, T0));
        assertThrows(IllegalArgumentException.class, () -> new GuildStanding("g1", "Knights", 0, T0));
        assertEquals(RankingSnapshot.empty(SEASON), new RankingBoard(SEASON, RULES).snapshot());
    }

    /** Four players (with ties), three guilds and time-attack runs including the final boss. */
    private static RankingBoard populated() {
        RankingBoard board = board();
        board.updatePlayer(A, "Alice", statsWith(10, 20_000, SeasonCounter.MONSTERS_KILLED, 300,
                SeasonCounter.BOSSES_KILLED, 2, SeasonCounter.ITEMS_CRAFTED, 15), T0 + 1);
        board.updatePlayer(B, "Bob", statsWith(12, 30_000, SeasonCounter.MONSTERS_KILLED, 300,
                SeasonCounter.BOSSES_KILLED, 2, SeasonCounter.AREAS_DISCOVERED, 9), T0 + 2);
        board.updatePlayer(C, "Carol", statsWith(15, 45_000, SeasonCounter.PARTY_DUNGEON_CLEARS, 3,
                SeasonCounter.DUNGEON_CLEARS, 4, SeasonCounter.DISTANCE_TRAVELLED, 25_000), T0 + 3);
        board.updatePlayer(D, "Dave", statsWith(20, 90_000, SeasonCounter.BOSSES_KILLED, 5,
                SeasonCounter.GUILD_CONTRIBUTION, 70), T0 + 4);
        board.updateGuild("g1", "Knights", 70, T0 + 5);
        board.updateGuild("g2", "Mages", 70, T0 + 6);
        board.updateGuild("g3", "Rangers", 10, T0 + 7);
        board.submitTimeAttack(run(1, "b1", BossDifficulty.NORMAL, 3_000, 10, 1), TIME_ATTACK);
        board.submitTimeAttack(run(2, "b1", BossDifficulty.NORMAL, 2_000, 20, 1, 2), TIME_ATTACK);
        board.submitTimeAttack(run(3, "final", BossDifficulty.HARD, 24_000, 30, 1, 2, 3, 4, 5, 6), TIME_ATTACK);
        board.submitTimeAttack(run(4, "final", BossDifficulty.HARD, 22_000, 40, 1, 2, 3, 4, 5, 6), TIME_ATTACK);
        return board;
    }
}
