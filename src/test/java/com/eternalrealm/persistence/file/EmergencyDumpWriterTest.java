package com.eternalrealm.persistence.file;

import com.eternalrealm.character.CharacterRecord;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.testutil.MutableClock;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class EmergencyDumpWriterTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void preservedFilesUseTheRegularFormatAndNeverOverwriteEachOther() throws Exception {
        Path directory = tmp.getRoot().toPath().resolve("emergency");
        EmergencyDumpWriter writer = new EmergencyDumpWriter(directory, MutableClock.startingAt("2026-11-08T00:00:00Z"));
        UUID player = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        SeasonId season = SeasonId.of("s1");
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        CharacterRecord first = new CharacterRecord(player, season, 3, 5, 400, StatBlock.ZERO.with(StatType.STR, 2), now, now);
        CharacterRecord second = new CharacterRecord(player, season, 4, 0, 900, StatBlock.ZERO, now, now);

        writer.preserve(first);
        writer.preserve(second); // same millisecond: must get its own file

        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream.toList();
        }
        assertEquals(2, files.size());
        Set<CharacterRecord> preserved = new HashSet<>();
        for (Path file : files) {
            preserved.add(JsonCodecs.decodeCharacter(Files.readString(file), player, season));
        }
        assertEquals(Set.of(first, second), preserved);
    }
}
