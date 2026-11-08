package com.eternalrealm.guild;

import java.util.Objects;
import java.util.UUID;

/**
 * Something that happened to a guild, returned by {@link GuildService} operations in the order it happened. Snapshots
 * show the guild after the change, except {@link Disbanded}, which carries its last state.
 */
public sealed interface GuildEvent permits GuildEvent.Created, GuildEvent.Invited, GuildEvent.InviteExpired,
        GuildEvent.InviteDeclined, GuildEvent.InviteCancelled, GuildEvent.Joined, GuildEvent.Left, GuildEvent.Kicked,
        GuildEvent.RankChanged, GuildEvent.MasterTransferred, GuildEvent.LevelUp, GuildEvent.Disbanded {
    UUID guildId();

    record Created(Guild guild) implements GuildEvent {
        public Created {
            Objects.requireNonNull(guild, "guild");
        }

        @Override
        public UUID guildId() {
            return guild.id();
        }
    }

    record Invited(GuildInvite invite) implements GuildEvent {
        public Invited {
            Objects.requireNonNull(invite, "invite");
        }

        @Override
        public UUID guildId() {
            return invite.guildId();
        }
    }

    record InviteExpired(GuildInvite invite) implements GuildEvent {
        public InviteExpired {
            Objects.requireNonNull(invite, "invite");
        }

        @Override
        public UUID guildId() {
            return invite.guildId();
        }
    }

    record InviteDeclined(GuildInvite invite) implements GuildEvent {
        public InviteDeclined {
            Objects.requireNonNull(invite, "invite");
        }

        @Override
        public UUID guildId() {
            return invite.guildId();
        }
    }

    /** The invite became void: the guild was disbanded, or the invitee joined or founded another guild. */
    record InviteCancelled(GuildInvite invite) implements GuildEvent {
        public InviteCancelled {
            Objects.requireNonNull(invite, "invite");
        }

        @Override
        public UUID guildId() {
            return invite.guildId();
        }
    }

    record Joined(Guild guild, UUID member) implements GuildEvent {
        public Joined {
            Objects.requireNonNull(guild, "guild");
            Objects.requireNonNull(member, "member");
        }

        @Override
        public UUID guildId() {
            return guild.id();
        }
    }

    record Left(Guild guild, UUID member) implements GuildEvent {
        public Left {
            Objects.requireNonNull(guild, "guild");
            Objects.requireNonNull(member, "member");
        }

        @Override
        public UUID guildId() {
            return guild.id();
        }
    }

    record Kicked(Guild guild, UUID member, UUID kickedBy) implements GuildEvent {
        public Kicked {
            Objects.requireNonNull(guild, "guild");
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(kickedBy, "kickedBy");
        }

        @Override
        public UUID guildId() {
            return guild.id();
        }
    }

    /** Promotion or demotion between {@link GuildRank#OFFICER} and {@link GuildRank#MEMBER}. */
    record RankChanged(Guild guild, UUID member, GuildRank previousRank, GuildRank newRank) implements GuildEvent {
        public RankChanged {
            Objects.requireNonNull(guild, "guild");
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(previousRank, "previousRank");
            Objects.requireNonNull(newRank, "newRank");
        }

        @Override
        public UUID guildId() {
            return guild.id();
        }
    }

    /** The master role moved; the previous master is now an officer. */
    record MasterTransferred(Guild guild, UUID previousMaster, UUID newMaster) implements GuildEvent {
        public MasterTransferred {
            Objects.requireNonNull(guild, "guild");
            Objects.requireNonNull(previousMaster, "previousMaster");
            Objects.requireNonNull(newMaster, "newMaster");
        }

        @Override
        public UUID guildId() {
            return guild.id();
        }
    }

    /** The total contribution reached a new level (the member cap may have grown). */
    record LevelUp(Guild guild, int previousLevel, int newLevel) implements GuildEvent {
        public LevelUp {
            Objects.requireNonNull(guild, "guild");
            if (newLevel <= previousLevel) {
                throw new IllegalArgumentException("level must rise: " + previousLevel + " -> " + newLevel);
            }
        }

        @Override
        public UUID guildId() {
            return guild.id();
        }
    }

    /** @param guild the last state, with every member at the time it ended */
    record Disbanded(Guild guild) implements GuildEvent {
        public Disbanded {
            Objects.requireNonNull(guild, "guild");
        }

        @Override
        public UUID guildId() {
            return guild.id();
        }
    }
}
