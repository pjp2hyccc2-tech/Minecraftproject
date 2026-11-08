package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.core.data.IncompatibleDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Crash-safe single-file storage with one previous generation.
 *
 * <p><b>Write:</b> the content goes to {@code X.tmp} and is fsynced. The current {@code X} is then <em>copied</em>
 * (not renamed) to {@code X.prev} via {@code X.prev.tmp}, and finally {@code X.tmp} atomically replaces {@code X}.
 * Because {@code X} is never renamed away, it exists at every instant after the first write: a crash leaves either the
 * old or the new {@code X}, and {@code X.prev} is always a complete earlier generation.</p>
 *
 * <p><b>Read:</b> only "no such file" means absent. Any other failure (permissions, I/O errors, a path that cannot
 * be resolved) is a {@link DataAccessException}, so that unavailable storage is never mistaken for a new player.
 * A corrupt {@code X} is moved to the quarantine directory (kept for forensics) and {@code X.prev} is used instead.
 * If both are unusable a {@link CorruptDataException} is thrown — never an empty result, which would make callers
 * create fresh data over the player's real progress.</p>
 */
public final class AtomicFileStore {
    private static final Logger LOG = LoggerFactory.getLogger(AtomicFileStore.class);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT).withZone(ZoneOffset.UTC);

    static final String TMP_SUFFIX = ".tmp";
    static final String PREV_SUFFIX = ".prev";

    private final Path quarantineDir;
    private final Clock clock;

    @FunctionalInterface
    public interface Parser<T> {
        T parse(String content) throws CorruptDataException, IncompatibleDataException;
    }

    public AtomicFileStore(Path quarantineDir, Clock clock) {
        this.quarantineDir = Objects.requireNonNull(quarantineDir, "quarantineDir");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void write(Path target, String content) throws DataAccessException {
        Path dir = target.toAbsolutePath().getParent();
        Path tmp = sibling(target, TMP_SUFFIX);
        Path prev = sibling(target, PREV_SUFFIX);
        Path prevTmp = sibling(target, PREV_SUFFIX + TMP_SUFFIX);
        try {
            Files.createDirectories(dir);
            writeAndSync(tmp, content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.copy(target, prevTmp, StandardCopyOption.REPLACE_EXISTING);
                sync(prevTmp);
                move(prevTmp, prev);
            } catch (NoSuchFileException firstWrite) {
                // There is no current generation yet; nothing to keep.
            }
            move(tmp, target);
            fsyncDirectory(dir);
        } catch (IOException e) {
            throw new DataAccessException("Failed to write " + target, e);
        }
    }

    public <T> Optional<T> read(Path target, Parser<T> parser) throws DataAccessException {
        Path prev = sibling(target, PREV_SUFFIX);
        CorruptDataException primaryFailure = null;
        byte[] primary = readIfPresent(target);
        if (primary != null) {
            try {
                return Optional.of(parser.parse(decodeUtf8(primary, target)));
            } catch (CorruptDataException e) {
                primaryFailure = e;
                LOG.warn("Data file {} is corrupt: {}", target, e.getMessage());
                quarantine(target);
            }
        }
        byte[] previous = readIfPresent(prev);
        if (previous == null) {
            if (primaryFailure == null) {
                return Optional.empty();
            }
            throw new CorruptDataException("Data file " + target + " is corrupt and has no previous generation",
                    primaryFailure);
        }
        if (primaryFailure == null) {
            LOG.warn("Data file {} is missing but its previous generation exists; recovering from it", target);
        }
        try {
            T value = parser.parse(decodeUtf8(previous, prev));
            LOG.warn("Recovered {} from its previous generation {}", target, prev.getFileName());
            return Optional.of(value);
        } catch (CorruptDataException e) {
            CorruptDataException failure = new CorruptDataException(
                    "Data file " + target + " and its previous generation are both unreadable", e);
            if (primaryFailure != null) {
                failure.addSuppressed(primaryFailure);
            }
            throw failure;
        }
    }

    /**
     * @return the file content, or {@code null} only if the file does not exist. Every other failure (including
     *         "existence cannot be determined") is reported, never treated as absent.
     */
    private static byte[] readIfPresent(Path file) throws DataAccessException {
        try {
            return Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            throw new DataAccessException("Failed to read " + file, e);
        }
    }

    /** Invalid encoding means corruption (the caller may fall back to the previous generation). */
    private static String decodeUtf8(byte[] bytes, Path file) throws CorruptDataException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new CorruptDataException("Invalid UTF-8 in " + file, e);
        }
    }

    /**
     * Moves a corrupt file out of the way. If that is impossible we stop: falling back to the previous generation
     * while the corrupt file stays in place would let the next save copy the corrupt file over the good backup.
     */
    private void quarantine(Path file) throws DataAccessException {
        try {
            Files.createDirectories(quarantineDir);
            Path destination = quarantineDir.resolve(file.getFileName() + "." + STAMP.format(clock.instant()) + ".corrupt");
            Files.move(file, destination, StandardCopyOption.REPLACE_EXISTING);
            LOG.warn("Moved corrupt file {} to {}", file, destination);
        } catch (IOException e) {
            throw new DataAccessException("Could not quarantine corrupt file " + file, e);
        }
    }

    static Path sibling(Path target, String suffix) {
        return target.resolveSibling(target.getFileName().toString() + suffix);
    }

    private static void writeAndSync(Path file, byte[] content) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void sync(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Best effort: makes the renames durable on POSIX file systems; not supported (and not needed) on Windows. */
    private static void fsyncDirectory(Path dir) {
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Directory fsync is not available on every platform.
        }
    }
}
