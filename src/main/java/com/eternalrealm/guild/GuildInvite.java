package com.eternalrealm.guild;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A pending invitation into a guild, valid while {@code now < expiresAt}. Invites are session state and are not
 * persisted. Like party invites, each one ends with exactly one {@link GuildEvent.Joined},
 * {@link GuildEvent.InviteDeclined}, {@link GuildEvent.InviteExpired} or {@link GuildEvent.InviteCancelled}.
 */
public record GuildInvite(UUID guildId, UUID inviter, UUID invitee, Instant createdAt, Instant expiresAt) {
    public GuildInvite {
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(inviter, "inviter");
        Objects.requireNonNull(invitee, "invitee");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (inviter.equals(invitee)) {
            throw new IllegalArgumentException("a player cannot invite themselves");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("invite must expire after it was created");
        }
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
