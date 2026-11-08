package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.ranking.HallOfFame;
import com.eternalrealm.ranking.RankingBoard;
import com.eternalrealm.ranking.RankingCategory;
import com.eternalrealm.ranking.RankingFixtures;
import com.eternalrealm.ranking.RankingRules;
import com.eternalrealm.ranking.RankingSnapshot;
import com.eternalrealm.ranking.SeasonArchive;
import com.eternalrealm.season.SeasonEvent;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.season.SeasonManager;
import com.eternalrealm.season.SeasonSchedule;
import com.eternalrealm.testutil.MutableClock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SeasonRepositoriesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final SeasonId S1 = SeasonId.of("s1");
    private static final Instant START = Instant.parse("2026-11-14T01:00:00Z");
    private static final SeasonSchedule SCHEDULE = SeasonSchedule.of(S1, START);

    private Path root;
    private FileRankingRepository rankings;
    private FileSeasonStateRepository states;
    private FileHallOfFameRepository hall;

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("eternalrealm").toPath();
        AtomicFileStore store = new AtomicFileStore(root.resolve("quarantine"), MutableClock.startingAt("2026-11-08T00:00:00Z"));
        rankings = new FileRankingRepository(root, store);
        states = new FileSeasonStateRepository(root, store);
        hall = new FileHallOfFameRepository(root, store);
    }

    // ---------------------------------------------------------------------------------------------- rankings

    @Test
    public void rankingsAreStoredInTheSeasonDirectory() throws Exception {
        RankingSnapshot snapshot = SeasonJsonCodecsTest.sampleRankings(false);
        rankings.save(snapshot);
        assertTrue(Files.isRegularFile(root.resolve("seasons/s1/rankings.json")));
        assertEquals(Optional.of(snapshot), rankings.load(S1));
        assertEquals("season data never leaks into another season", Optional.empty(), rankings.load(SeasonId.of("s2")));
    }

    @Test
    public void aRestartRestoresTheSameRankings() throws Exception {
        RankingSnapshot stored = SeasonJsonCodecsTest.sampleRankings(false);
        RankingBoard before = RankingBoard.rebuild(stored, RankingRules.defaults());
        rankings.save(before.snapshot());
        RankingBoard after = RankingBoard.rebuild(rankings.load(S1).orElseThrow(), RankingRules.defaults());
        for (RankingCategory category : RankingCategory.playerCategories()) {
            assertEquals(category.id(), before.top(category, 50), after.top(category, 50));
        }
        assertEquals(before.topGuilds(10), after.topGuilds(10));
        assertEquals(before.timeAttack().bests(), after.timeAttack().bests());
        assertEquals(before.timeAttack().firstClear("final"), after.timeAttack().firstClear("final"));
    }

    @Test
    public void anotherSeasonsFileIsCorruptNotAdopted() throws Exception {
        rankings.save(SeasonJsonCodecsTest.sampleRankings(false));
        Path foreign = root.resolve("seasons/s2/rankings.json");
        Files.createDirectories(foreign.getParent());
        Files.copy(root.resolve("seasons/s1/rankings.json"), foreign);
        assertThrows(CorruptDataException.class, () -> rankings.load(SeasonId.of("s2")));
    }

    @Test
    public void aCorruptRankingFileFallsBackToThePreviousGeneration() throws Exception {
        RankingSnapshot first = SeasonJsonCodecsTest.sampleRankings(false);
        RankingSnapshot second = SeasonJsonCodecsTest.sampleRankings(true);
        rankings.save(first);
        rankings.save(second);
        Files.writeString(root.resolve("seasons/s1/rankings.json"), "{\"schema\": 1, \"seasonId\":", StandardCharsets.UTF_8);
        assertEquals(Optional.of(first), rankings.load(S1));
    }

    // ---------------------------------------------------------------------------------------------- season state

    @Test
    public void theSeasonStateIsStoredAtTheDataRoot() throws Exception {
        assertEquals(Optional.empty(), states.load());
        SeasonManager manager = SeasonManager.atStartup(states.load(), SCHEDULE);
        states.save(manager.state());
        assertTrue(Files.isRegularFile(root.resolve("season_state.json")));
        assertEquals(Optional.of(manager.state()), states.load());
    }

    @Test
    public void eventsAreNotRepeatedAfterARestartThroughTheFile() throws Exception {
        SeasonManager manager = SeasonManager.atStartup(states.load(), SCHEDULE);
        manager.tick(START);
        Instant dayThree = START.plus(Duration.ofDays(2)).plusSeconds(60);
        assertEquals(1, manager.tick(dayThree).size());
        states.save(manager.state());

        SeasonManager restarted = SeasonManager.atStartup(states.load(), SCHEDULE);
        assertEquals(List.of(), restarted.tick(dayThree.plusSeconds(60)));
        assertEquals(List.of(new SeasonEvent(SeasonEvent.Type.DAY_CHANGED, S1, 4)),
                restarted.tick(dayThree.plus(Duration.ofDays(1))));
    }

    @Test
    public void aNewerSeasonStateIsRejectedAndLeftAlone() throws Exception {
        Path file = root.resolve("season_state.json");
        String newer = "{\"schema\": 2, \"seasonId\": \"s9\"}";
        Files.writeString(file, newer, StandardCharsets.UTF_8);
        assertThrows(IncompatibleDataException.class, () -> states.load());
        assertEquals(newer, Files.readString(file, StandardCharsets.UTF_8));
        assertFalse(Files.exists(root.resolve("quarantine")));
    }

    // ---------------------------------------------------------------------------------------------- hall of fame

    @Test
    public void theHallOfFameIsEmptyUntilTheFirstArchive() throws Exception {
        assertEquals(0, hall.load().size());
        assertFalse(Files.exists(root.resolve("hall_of_fame.json")));
    }

    @Test
    public void archivesAreAppendedInOrder() throws Exception {
        assertTrue(hall.append(RankingFixtures.archive("s1")));
        assertTrue(Files.isRegularFile(root.resolve("hall_of_fame.json")));
        FileHallOfFameRepository otherInstance = new FileHallOfFameRepository(root,
                new AtomicFileStore(root.resolve("quarantine"), MutableClock.startingAt("2026-11-08T00:00:00Z")));
        assertTrue("appends to the stored file, not to an in-memory copy", otherInstance.append(RankingFixtures.archive("s2")));
        HallOfFame loaded = hall.load();
        assertEquals(List.of(RankingFixtures.archive("s1"), RankingFixtures.archive("s2")), loaded.archives());
        assertEquals(2, loaded.heroes().size());
    }

    @Test
    public void archivingASeasonAgainWritesNothing() throws Exception {
        hall.append(RankingFixtures.archive("s1"));
        Path file = root.resolve("hall_of_fame.json");
        byte[] before = Files.readAllBytes(file);
        SeasonArchive repeated = new SeasonArchive(S1, Instant.parse("2026-12-01T00:00:00Z"), Map.of(), List.of(), null);
        assertFalse(hall.append(repeated));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertFalse("no second generation was written", Files.exists(root.resolve("hall_of_fame.json.prev")));
        assertEquals(RankingFixtures.archive("s1"), hall.load().archive(S1).orElseThrow());
    }

    @Test
    public void unreadableHistoryIsNeverReplaced() throws Exception {
        Path file = root.resolve("hall_of_fame.json");
        String newer = "{\"schema\": 2, \"seasons\": []}";
        Files.writeString(file, newer, StandardCharsets.UTF_8);
        assertThrows(IncompatibleDataException.class, () -> hall.append(RankingFixtures.archive("s1")));
        assertEquals(newer, Files.readString(file, StandardCharsets.UTF_8));

        String corrupt = "{\"schema\": 1, \"seasons\": [";
        Files.writeString(file, corrupt, StandardCharsets.UTF_8);
        assertThrows(CorruptDataException.class, () -> hall.append(RankingFixtures.archive("s1")));
        try (Stream<Path> quarantined = Files.list(root.resolve("quarantine"))) {
            List<Path> files = quarantined.toList();
            assertEquals("the unreadable history is kept for recovery", 1, files.size());
            assertEquals(corrupt, Files.readString(files.get(0), StandardCharsets.UTF_8));
        }
    }
}
