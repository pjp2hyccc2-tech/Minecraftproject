package com.eternalrealm.guild.house;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a guild has of the guild houses (immutable snapshot, also the persistence form): the design it owns (none
 * before buying or after giving the house up), when it got it, the house fund, the board and the storage log.
 *
 * @param design  owned design id, or {@code null}
 * @param since   when the current design was bought or switched to ({@code null} without a house)
 * @param fund    gold paid into the house fund and not yet spent, {@code 0..}{@link GuildHouseRules#MAX_FUND}
 * @param motd    message of the day ({@code ""} for none)
 * @param notes   the board's notes, oldest first
 * @param log     storage log, oldest first, at most {@link GuildHouseRules#LOG_SIZE}
 * @param nextId  id for the next note
 */
public record GuildHouse(UUID guildId, String design, Instant since, long fund, String motd, List<BoardNote> notes,
                         List<StorageLogEntry> log, long nextId) {
    public GuildHouse {
        Objects.requireNonNull(guildId, "guildId");
        if ((design == null) != (since == null)) {
            throw new IllegalArgumentException("design and since go together");
        }
        if (fund < 0 || fund > GuildHouseRules.MAX_FUND) {
            throw new IllegalArgumentException("fund out of range: " + fund);
        }
        motd = motd == null ? "" : motd;
        notes = List.copyOf(notes);
        log = List.copyOf(log);
        if (log.size() > GuildHouseRules.LOG_SIZE) {
            log = List.copyOf(log.subList(log.size() - GuildHouseRules.LOG_SIZE, log.size()));
        }
        for (BoardNote note : notes) {
            if (note.id() >= nextId) {
                throw new IllegalArgumentException("note id " + note.id() + " >= nextId " + nextId);
            }
        }
    }

    public static GuildHouse empty(UUID guildId) {
        return new GuildHouse(guildId, null, null, 0L, "", List.of(), List.of(), 1L);
    }

    public Optional<String> designId() {
        return Optional.ofNullable(design);
    }

    public boolean hasHouse() {
        return design != null;
    }

    /** Whether nothing worth keeping is left (no house, no fund, no board, no log). */
    public boolean isBlank() {
        return design == null && fund == 0 && motd.isEmpty() && notes.isEmpty() && log.isEmpty();
    }

    GuildHouse withDesign(String newDesign, Instant at) {
        return new GuildHouse(guildId, newDesign, newDesign == null ? null : at, fund, motd, notes, log, nextId);
    }

    GuildHouse withFund(long newFund) {
        return new GuildHouse(guildId, design, since, newFund, motd, notes, log, nextId);
    }

    GuildHouse withMotd(String text) {
        return new GuildHouse(guildId, design, since, fund, text, notes, log, nextId);
    }

    GuildHouse withNotes(List<BoardNote> newNotes, long newNextId) {
        return new GuildHouse(guildId, design, since, fund, motd, newNotes, log, newNextId);
    }

    GuildHouse withLog(StorageLogEntry entry) {
        List<StorageLogEntry> copy = new ArrayList<>(log);
        copy.add(entry);
        return new GuildHouse(guildId, design, since, fund, motd, notes, copy, nextId);
    }
}
