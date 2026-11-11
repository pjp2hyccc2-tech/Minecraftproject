package com.eternalrealm.guild.house;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A note pinned on the guild board.
 *
 * @param authorName the author's name when the note was written (shown even while they are offline)
 */
public record BoardNote(long id, UUID author, String authorName, String text, Instant at) {
    public BoardNote {
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(authorName, "authorName");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(at, "at");
        if (id <= 0) {
            throw new IllegalArgumentException("note id must be positive: " + id);
        }
    }
}
