package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.testutil.MutableClock;
import com.eternalrealm.world.WorldProgress;
import com.eternalrealm.world.WorldProgressRecord;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FileWorldProgressRepositoryTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final SeasonId S1 = SeasonId.of("s1");
    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    private Path root;
    private FileWorldProgressRepository repository;

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("eternalrealm").toPath();
        AtomicFileStore store = new AtomicFileStore(root.resolve("quarantine"), MutableClock.startingAt("2026-11-09T00:00:00Z"));
        repository = new FileWorldProgressRepository(root, store);
    }

    private static WorldProgressRecord progressWith(String... bosses) {
        WorldProgress progress = WorldProgress.fresh(S1);
        for (String boss : bosses) {
            progress.recordBossDefeat(boss, NOW);
        }
        return progress.toRecord();
    }

    @Test
    public void progressIsStoredPerSeasonAndStartsOverWithANewSeason() throws Exception {
        assertEquals(Optional.empty(), repository.load(S1));
        WorldProgressRecord record = progressWith("b1", "b2");
        repository.save(record);

        assertTrue(Files.exists(root.resolve("seasons/s1/world_progress.json")));
        assertEquals(Optional.of(record), repository.load(S1));
        assertEquals(Set.of(1, 2, 3), WorldProgress.fromRecord(repository.load(S1).orElseThrow()).unlockedAreaIds());
        assertEquals("a new season starts fresh", Optional.empty(), repository.load(SeasonId.of("s2")));
    }

    @Test
    public void aCorruptFileFallsBackToThePreviousGeneration() throws Exception {
        WorldProgressRecord older = progressWith("b1");
        repository.save(older);
        repository.save(progressWith("b1", "b2"));
        Path file = root.resolve("seasons/s1/world_progress.json");
        Files.writeString(file, "{\"schema\": 1, \"seasonId\": \"s1\", \"bossFirstDe", StandardCharsets.UTF_8);

        assertEquals(Optional.of(older), repository.load(S1));
        try (Stream<Path> quarantined = Files.list(root.resolve("quarantine"))) {
            assertTrue("the corrupt file is kept for forensics", quarantined.findAny().isPresent());
        }
    }

    @Test
    public void unreadableDataIsNeverReportedAsAbsent() throws Exception {
        Path file = root.resolve("seasons/s1/world_progress.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"schema\": 1, \"seasonId\": \"s2\", \"bossFirstDefeats\": {}}", StandardCharsets.UTF_8);
        assertThrows("a file of another season is corrupt", CorruptDataException.class, () -> repository.load(S1));

        Files.writeString(file, "{\"schema\": 9, \"seasonId\": \"s1\", \"bossFirstDefeats\": {}}", StandardCharsets.UTF_8);
        assertThrows(IncompatibleDataException.class, () -> repository.load(S1));
        assertTrue("newer data is left untouched", Files.exists(file));
    }
}
