package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.testutil.MutableClock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AtomicFileStoreTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path dir;
    private Path quarantine;
    private Path file;
    private AtomicFileStore store;

    /** "OK:..." parses, "NEW" is a newer schema, anything else is corrupt. */
    private static final AtomicFileStore.Parser<String> PARSER = content -> {
        if (content.startsWith("OK:")) {
            return content.substring(3);
        }
        if (content.equals("NEW")) {
            throw new IncompatibleDataException("newer");
        }
        throw new CorruptDataException("bad content");
    };

    @Before
    public void setUp() throws Exception {
        dir = tmp.newFolder("data").toPath();
        quarantine = dir.resolve("quarantine");
        file = dir.resolve("sub").resolve("record.json");
        store = new AtomicFileStore(quarantine, MutableClock.startingAt("2026-11-08T00:00:00Z"));
    }

    @Test
    public void missingFileReadsAsEmpty() throws Exception {
        assertEquals(Optional.empty(), store.read(file, PARSER));
    }

    @Test
    public void writeThenRead() throws Exception {
        store.write(file, "OK:v1");
        assertEquals(Optional.of("v1"), store.read(file, PARSER));
        assertFalse("no temp file may be left behind", Files.exists(AtomicFileStore.sibling(file, ".tmp")));
    }

    @Test
    public void secondWriteKeepsPreviousGenerationAsACopy() throws Exception {
        store.write(file, "OK:v1");
        store.write(file, "OK:v2");
        assertEquals("OK:v2", Files.readString(file));
        assertEquals("OK:v1", Files.readString(AtomicFileStore.sibling(file, ".prev")));
        assertFalse(Files.exists(AtomicFileStore.sibling(file, ".tmp")));
        assertFalse(Files.exists(AtomicFileStore.sibling(file, ".prev.tmp")));
    }

    /**
     * Regression test for the independent review finding: "existence cannot be determined" (here: a parent path that
     * is a regular file) must be an error, never "no data" — otherwise a fresh character would be created and later
     * written over the real one.
     */
    @Test
    public void unresolvablePathIsAnErrorNotMissingData() throws Exception {
        Path notADirectory = dir.resolve("not-a-directory");
        Files.writeString(notADirectory, "plain file");
        Path unresolvable = notADirectory.resolve("record.json");
        DataAccessException error = assertThrows(DataAccessException.class, () -> store.read(unresolvable, PARSER));
        assertFalse("must not be reported as corruption either", error instanceof CorruptDataException);
    }

    @Test
    public void corruptPrimaryFallsBackToPreviousAndIsQuarantined() throws Exception {
        store.write(file, "OK:v1");
        store.write(file, "OK:v2");
        Files.writeString(file, "garbage");

        assertEquals(Optional.of("v1"), store.read(file, PARSER));
        assertFalse(Files.exists(file));
        assertEquals(1, countFiles(quarantine));
    }

    @Test
    public void corruptPrimaryWithoutPreviousThrowsInsteadOfReturningEmpty() throws Exception {
        store.write(file, "OK:v1");
        Files.writeString(file, "garbage");
        assertThrows(CorruptDataException.class, () -> store.read(file, PARSER));
        assertEquals(1, countFiles(quarantine));
    }

    @Test
    public void bothGenerationsCorruptThrows() throws Exception {
        store.write(file, "OK:v1");
        store.write(file, "OK:v2");
        Files.writeString(file, "garbage");
        Files.writeString(AtomicFileStore.sibling(file, ".prev"), "garbage too");
        assertThrows(CorruptDataException.class, () -> store.read(file, PARSER));
        assertTrue("the previous generation is kept for manual recovery", Files.exists(AtomicFileStore.sibling(file, ".prev")));
    }

    @Test
    public void missingPrimaryRecoversFromPrevious() throws Exception {
        store.write(file, "OK:v1");
        store.write(file, "OK:v2");
        Files.delete(file); // e.g. crash between the two renames
        assertEquals(Optional.of("v1"), store.read(file, PARSER));
    }

    @Test
    public void newerSchemaIsNeitherQuarantinedNorReplaced() throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "NEW");
        assertThrows(IncompatibleDataException.class, () -> store.read(file, PARSER));
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(quarantine));
    }

    @Test
    public void invalidUtf8IsTreatedAsCorruption() throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{'O', 'K', ':', (byte) 0xC3, (byte) 0x28});
        assertThrows(CorruptDataException.class, () -> store.read(file, PARSER));
    }

    @Test
    public void leftoverTempFileIsIgnoredAndOverwritten() throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(AtomicFileStore.sibling(file, ".tmp"), "half written");
        assertEquals(Optional.empty(), store.read(file, PARSER));
        store.write(file, "OK:v1");
        assertEquals(Optional.of("v1"), store.read(file, PARSER));
    }

    @Test
    public void unicodeContentSurvives() throws Exception {
        store.write(file, "OK:冒険者アストリア");
        assertEquals(Optional.of("冒険者アストリア"), store.read(file, PARSER));
        assertArrayEquals("OK:冒険者アストリア".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
    }

    @Test
    public void writeFailureIsReportedAsDataAccessException() throws Exception {
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "I am a file, not a directory");
        assertThrows(DataAccessException.class, () -> store.write(blocker.resolve("child.json"), "OK:x"));
    }

    private static long countFiles(Path directory) throws Exception {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.count();
        }
    }
}
