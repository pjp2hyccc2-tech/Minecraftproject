package com.eternalrealm.ranking;

import com.eternalrealm.season.SeasonId;
import org.junit.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.eternalrealm.ranking.RankingFixtures.NOW;
import static com.eternalrealm.ranking.RankingFixtures.run;
import static com.eternalrealm.ranking.RankingFixtures.uuid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HallOfFameTest {
    private static RankingEntry<String> player(int rank, int id, long score) {
        return RankingFixtures.archivedPlayer(rank, id, score);
    }

    private static SeasonArchive archive(String seasonId) {
        return RankingFixtures.archive(seasonId);
    }

    @Test
    public void appendOnlyAndTheFirstArchiveOfASeasonIsFinal() {
        HallOfFame hall = new HallOfFame();
        SeasonArchive s1 = archive("s1");
        assertTrue(hall.append(s1));
        SeasonArchive s1Again = new SeasonArchive(SeasonId.of("s1"), NOW.plusSeconds(60), Map.of(), List.of(), null);
        assertFalse("a repeated archive request changes nothing", hall.append(s1Again));
        assertEquals(s1, hall.archive(SeasonId.of("s1")).orElseThrow());
        assertTrue(hall.append(archive("s2")));
        assertEquals(List.of(SeasonId.of("s1"), SeasonId.of("s2")),
                hall.archives().stream().map(SeasonArchive::seasonId).toList());
        assertEquals(2, hall.size());
        assertTrue(hall.contains(SeasonId.of("s2")));
        assertFalse(hall.contains(SeasonId.of("s3")));
        assertTrue(hall.archive(SeasonId.of("s3")).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> hall.archives().clear());
    }

    @Test
    public void storedDataWithASeasonTwiceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HallOfFame(List.of(archive("s1"), archive("s1"))));
        assertEquals(2, new HallOfFame(List.of(archive("s1"), archive("s2"))).size());
    }

    @Test
    public void heroesAreThePodiumsAndTheFinalBossFirstClear() {
        SeasonArchive archive = archive("s1");
        HeroRecord heroes = archive.heroes();
        assertEquals(SeasonId.of("s1"), heroes.seasonId());
        assertEquals(List.of(player(1, 4, 900), player(2, 3, 450), player(3, 2, 300)), heroes.podium(RankingCategory.LEVEL));
        assertEquals(List.of(player(1, 4, 5)), heroes.podium(RankingCategory.BOSS));
        assertEquals(List.of(), heroes.podium(RankingCategory.CRAFTING));
        assertEquals(archive.finalBossFirstClear(), heroes.finalBossFirstClear());
        assertEquals(6, heroes.finalBossFirstClear().members().size());
        assertEquals(List.of(heroes), new HallOfFame(List.of(archive)).heroes());

        Map<RankingCategory, List<RankingEntry<String>>> tooMany = new EnumMap<>(RankingCategory.class);
        tooMany.put(RankingCategory.LEVEL, archive.top(RankingCategory.LEVEL));
        assertThrows(IllegalArgumentException.class, () -> new HeroRecord(SeasonId.of("s1"), tooMany, null));
    }

    @Test
    public void archivesRejectRankingsNoBoardCanProduce() {
        assertInvalid(RankingCategory.LEVEL, List.of(player(2, 1, 10)));
        assertInvalid(RankingCategory.LEVEL, List.of(player(1, 1, 10), player(3, 2, 5)));
        assertInvalid(RankingCategory.LEVEL, List.of(player(1, 1, 10), player(2, 2, 50)));
        assertInvalid(RankingCategory.LEVEL, List.of(player(1, 1, 10), player(2, 1, 5)));
        assertInvalid(RankingCategory.LEVEL, List.of(new RankingEntry<>(1, "not-a-uuid", "X", 1)));
        assertInvalid(RankingCategory.GUILD, List.of(new RankingEntry<>(1, "bad guild", "X", 1)));
        assertInvalid(RankingCategory.BOSS_TIME_ATTACK, List.of(player(1, 1, 10)));

        TimeAttackRun notFinal = run(3, "b8", BossDifficulty.EX, 5_000, 0, 1);
        assertThrows(IllegalArgumentException.class,
                () -> new SeasonArchive(SeasonId.of("s1"), NOW, Map.of(), List.of(), notFinal));
        TimeAttackRun best = run(2, "b1", BossDifficulty.NORMAL, 2_000, 20, 1, 2);
        TimeAttackRun sameBracket = run(4, "b1", BossDifficulty.NORMAL, 2_500, 20, 3, 4);
        assertThrows(IllegalArgumentException.class,
                () -> new SeasonArchive(SeasonId.of("s1"), NOW, Map.of(), List.of(best, sameBracket), null));
        assertThrows(NullPointerException.class, () -> new SeasonArchive(null, NOW, Map.of(), List.of(), null));
        SeasonArchive empty = new SeasonArchive(SeasonId.of("s1"), NOW, Map.of(), List.of(), null);
        assertEquals(List.of(), empty.top(RankingCategory.OVERALL));
        assertEquals(null, empty.heroes().finalBossFirstClear());
    }

    private static void assertInvalid(RankingCategory category, List<RankingEntry<String>> entries) {
        Map<RankingCategory, List<RankingEntry<String>>> rankings = new EnumMap<>(RankingCategory.class);
        rankings.put(category, entries);
        assertThrows(category.id() + " " + entries, IllegalArgumentException.class,
                () -> new SeasonArchive(SeasonId.of("s1"), NOW, rankings, List.of(), null));
    }

    @Test
    public void idsOfPlayersAreUuidStrings() {
        UUID id = UUID.fromString(archive("s1").top(RankingCategory.LEVEL).get(0).id());
        assertEquals(uuid(4), id);
    }
}
