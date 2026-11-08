package com.eternalrealm.guild;

import com.eternalrealm.core.ModInfo;

import java.util.Optional;

/**
 * Rank inside a guild. {@link #id()} is the stable persistence key: never rename or reuse it.
 *
 * <p>Permissions: officers and the master invite; the master kicks anyone, an officer only members; only the master
 * changes ranks, hands over the guild or disbands it. There is exactly one master per guild.</p>
 */
public enum GuildRank {
    MASTER("master"),
    OFFICER("officer"),
    MEMBER("member");

    private final String id;

    GuildRank(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "guild_rank." + ModInfo.MOD_ID + "." + id;
    }

    public boolean canInvite() {
        return this != MEMBER;
    }

    /** Whether this rank may remove a member of rank {@code target}. */
    public boolean canKick(GuildRank target) {
        return switch (this) {
            case MASTER -> target != MASTER;
            case OFFICER -> target == MEMBER;
            case MEMBER -> false;
        };
    }

    /** Promote, demote, transfer the master role and disband. */
    public boolean canManageRanks() {
        return this == MASTER;
    }

    public static Optional<GuildRank> byId(String id) {
        for (GuildRank rank : values()) {
            if (rank.id.equals(id)) {
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }
}
