package com.eternalrealm.persistence.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Point-in-time zip snapshots of the mod data directory, with retention.
 *
 * <p>Every data file is replaced atomically, so each file inside a snapshot is internally consistent. Files of
 * different players may come from slightly different moments; Phase 1 has no cross-player data, so this is
 * acceptable (the database backend will use transactional backups instead).</p>
 *
 * <p>Restore (server stopped): move the data directory aside and unzip a snapshot in its place.
 * See docs/TECHNICAL_DESIGN.md "バックアップと復旧".</p>
 */
public final class SnapshotBackupService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SnapshotBackupService.class);
    static final String PREFIX = "eternalrealm-";
    static final String SUFFIX = ".zip";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final int READ_ATTEMPTS = 3;

    private final Path dataDir;
    private final Path backupDir;
    private final int keep;
    private final Clock clock;
    private final Object snapshotLock = new Object();
    private final ExecutorService worker;
    private CompletableFuture<Path> running;

    public SnapshotBackupService(Path dataDir, Path backupDir, int keep, Clock clock) {
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir").toAbsolutePath().normalize();
        this.backupDir = Objects.requireNonNull(backupDir, "backupDir").toAbsolutePath().normalize();
        if (keep < 1) {
            throw new IllegalArgumentException("keep must be >= 1: " + keep);
        }
        if (this.backupDir.startsWith(this.dataDir)) {
            throw new IllegalArgumentException("backupDir must not be inside dataDir");
        }
        this.keep = keep;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "EternalRealm-Backup");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Starts a snapshot in the background; joins the running one if a snapshot is already in progress. */
    public synchronized CompletableFuture<Path> createSnapshotAsync() {
        if (running != null && !running.isDone()) {
            return running;
        }
        running = CompletableFuture.supplyAsync(() -> {
            try {
                return createSnapshot();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, worker);
        return running;
    }

    /** Creates a snapshot synchronously (used at shutdown and by tests). */
    public Path createSnapshot() throws IOException {
        synchronized (snapshotLock) {
            Files.createDirectories(backupDir);
            String baseName = PREFIX + STAMP.format(clock.instant());
            Path target = backupDir.resolve(baseName + SUFFIX);
            for (int n = 1; Files.exists(target); n++) {
                target = backupDir.resolve(baseName + "-" + n + SUFFIX);
            }
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            int files = 0;
            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                 ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Path file : listDataFiles()) {
                    byte[] content = readWithRetry(file);
                    if (content == null) {
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry(dataDir.relativize(file).toString().replace('\\', '/')));
                    zip.write(content);
                    zip.closeEntry();
                    files++;
                }
            } catch (IOException e) {
                Files.deleteIfExists(tmp);
                throw e;
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            prune();
            LOG.info("Backup snapshot {} created ({} files)", target.getFileName(), files);
            return target;
        }
    }

    /** @return snapshots, newest first */
    public List<Path> listSnapshots() throws IOException {
        if (!Files.isDirectory(backupDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(backupDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
                    })
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
    }

    public Path backupDir() {
        return backupDir;
    }

    private List<Path> listDataFiles() throws IOException {
        if (!Files.isDirectory(dataDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dataDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().endsWith(".tmp"))
                    .sorted()
                    .toList();
        }
    }

    /** @return file content, or {@code null} if the file disappeared (e.g. quarantined) while snapshotting */
    private static byte[] readWithRetry(Path file) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                return Files.readAllBytes(file);
            } catch (NoSuchFileException e) {
                // A file can be briefly absent while it is being replaced (X -> X.prev, X.tmp -> X).
                if (attempt >= READ_ATTEMPTS) {
                    LOG.debug("Skipping {}: it no longer exists", file);
                    return null;
                }
                sleepQuietly(20L * attempt);
            } catch (IOException e) {
                if (attempt >= READ_ATTEMPTS) {
                    throw e;
                }
                sleepQuietly(20L * attempt);
            }
        }
    }

    private void prune() throws IOException {
        List<Path> snapshots = new ArrayList<>(listSnapshots());
        for (int i = keep; i < snapshots.size(); i++) {
            Files.deleteIfExists(snapshots.get(i));
            LOG.debug("Pruned old backup snapshot {}", snapshots.get(i).getFileName());
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void close(Duration timeout) {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warn("Backup worker did not finish within {}", timeout);
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }

    @Override
    public void close() {
        close(Duration.ofSeconds(60));
    }
}
