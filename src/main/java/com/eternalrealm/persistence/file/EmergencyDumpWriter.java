package com.eternalrealm.persistence.file;

import com.eternalrealm.character.CharacterRecord;
import com.eternalrealm.player.EmergencyDump;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Writes character data that could not be saved normally to {@code <dir>/<uuid>-<season>-<UTC time>.json}
 * (same format as the regular files). If even that fails, the JSON is written to the error log.
 */
public final class EmergencyDumpWriter implements EmergencyDump {
    private static final Logger LOG = LoggerFactory.getLogger(EmergencyDumpWriter.class);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final int MAX_NAME_ATTEMPTS = 100;

    private final Path directory;
    private final Clock clock;

    public EmergencyDumpWriter(Path directory, Clock clock) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void preserve(CharacterRecord record) {
        String json = JsonCodecs.encodeCharacter(record);
        try {
            Files.createDirectories(directory);
            String base = record.playerId() + "-" + record.seasonId().value() + "-" + STAMP.format(clock.instant());
            for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
                Path file = directory.resolve(attempt == 0 ? base + ".json" : base + "-" + attempt + ".json");
                try {
                    Files.writeString(file, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                    LOG.error("Character {} could not be saved normally and was preserved in {}. While the server is "
                                    + "stopped, copy it to seasons/{}/characters/{}.json to restore it.",
                            record.playerId(), file, record.seasonId().value(), record.playerId());
                    return;
                } catch (FileAlreadyExistsException taken) {
                    // Try the next name.
                }
            }
            throw new IOException("No free emergency file name for " + base);
        } catch (IOException | RuntimeException e) {
            LOG.error("Character {} could not be saved or preserved. UNSAVED STATE (JSON): {}", record.playerId(), json, e);
        }
    }
}
