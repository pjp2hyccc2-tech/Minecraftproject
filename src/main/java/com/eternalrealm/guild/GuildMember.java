package com.eternalrealm.guild;

import java.time.Instant;
import java.util.Objects;

/**
 * Membership of one player (the key in {@link Guild#members()}).
 */
public record GuildMember(GuildRank rank, Instant joinedAt) {
    public GuildMember {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }

    GuildMember withRank(GuildRank newRank) {
        return new GuildMember(newRank, joinedAt);
    }
}
