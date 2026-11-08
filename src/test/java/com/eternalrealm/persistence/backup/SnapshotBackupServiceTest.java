package com.eternalrealm.persistence.backup;

import com.eternalrealm.testutil.MutableClock;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SnapshotBackupServiceTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path dataDir;
    private Path backupDir;
    private MutableClock clock;
    private SnapshotBackupService service;

    @Before
    public void setUp() throws Exception {
        Path world = tmp.newFolder("world").toPath();
        dataDir = world.resolve("eternalrealm");
        backupDir = world.resolve("eternalrealm_backups");
        clock = MutableClock.startingAt("2026-11-08T00:00:00Z");
        service = new SnapshotBackupService(dataDir, backupDir, 2, clock);
    }

    @After
    public void tearDown() {
        service.close(Duration.ofSeconds(5));
    }

    @Test
    public void snapshotContainsDataFilesButNotTempFiles() throws Exception {
        Files.createDirectories(dataDir.resolve("accounts"));
        Files.writeString(dataDir.resolve("accounts/a.json"), "{\"a\":1}");
        Files.writeString(dataDir.resolve("accounts/a.json.prev"), "{\"a\":0}");
        Files.writeString(dataDir.resolve("accounts/b.json.tmp"), "half");

        Path snapshot = service.createSnapshot();
        Map<String, String> entries = readZip(snapshot);
        assertEquals(2, entries.size());
        assertEquals("{\"a\":1}", entries.get("accounts/a.json"));
        assertTrue(entries.containsKey("accounts/a.json.prev"));
        assertFalse(entries.containsKey("accounts/b.json.tmp"));
    }

    @Test
    public void oldSnapshotsArePruned() throws Exception {
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("x.json"), "1");
        Path first = service.createSnapshot();
        clock.advance(Duration.ofMinutes(30));
        Path second = service.createSnapshot();
        clock.advance(Duration.ofMinutes(30));
        Path third = service.createSnapshot();

        List<Path> remaining = service.listSnapshots();
        assertEquals(List.of(third, second), remaining);
        assertFalse(Files.exists(first));
    }

    @Test
    public void snapshotOfMissingDataDirIsEmptyButValid() throws Exception {
        Path snapshot = service.createSnapshot();
        assertTrue(Files.exists(snapshot));
        assertEquals(0, readZip(snapshot).size());
    }

    @Test
    public void sameMillisecondSnapshotsDoNotOverwriteEachOther() throws Exception {
        Path a = service.createSnapshot();
        Path b = service.createSnapshot();
        assertFalse(a.equals(b));
        assertTrue(Files.exists(a) && Files.exists(b));
    }

    @Test
    public void asyncSnapshotCompletes() throws Exception {
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("x.json"), "1");
        Path snapshot = service.createSnapshotAsync().get(10, TimeUnit.SECONDS);
        assertEquals(1, readZip(snapshot).size());
    }

    @Test
    public void backupDirectoryInsideDataDirectoryIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SnapshotBackupService(dataDir, dataDir.resolve("backups"), 2, clock));
    }

    private static Map<String, String> readZip(Path zip) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
