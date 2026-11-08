package com.eternalrealm.party;

import java.util.Objects;

/**
 * Server-configured party limits. Immutable; replaced as a whole when the config is reloaded
 * ({@link PartyService#setRules(PartyRules)}).
 *
 * @param maxPartySize      capacity of a {@link PartyType#NORMAL} party (design: 5)
 * @param maxRaidSize       capacity of a {@link PartyType#RAID} (design: 20, for the final boss 終焉の王)
 * @param inviteExpiryTicks how long an invite stays valid (default 1200 ticks = 60 seconds)
 */
public record PartyRules(int maxPartySize, int maxRaidSize, long inviteExpiryTicks) {
    public static final int MAX_PARTY_SIZE = 10;
    public static final int MAX_RAID_SIZE = 40;
    /** One hour of server ticks. */
    public static final long MAX_INVITE_EXPIRY_TICKS = 72_000L;

    public PartyRules {
        if (maxPartySize < 2 || maxPartySize > MAX_PARTY_SIZE) {
            throw new IllegalArgumentException("maxPartySize out of range [2, " + MAX_PARTY_SIZE + "]: " + maxPartySize);
        }
        if (maxRaidSize < maxPartySize || maxRaidSize > MAX_RAID_SIZE) {
            throw new IllegalArgumentException("maxRaidSize out of range [" + maxPartySize + ", " + MAX_RAID_SIZE
                    + "]: " + maxRaidSize);
        }
        if (inviteExpiryTicks < 1 || inviteExpiryTicks > MAX_INVITE_EXPIRY_TICKS) {
            throw new IllegalArgumentException("inviteExpiryTicks out of range [1, " + MAX_INVITE_EXPIRY_TICKS + "]: "
                    + inviteExpiryTicks);
        }
    }

    /** 5 players, raids of 20, invites valid for 60 seconds (docs/GAME_DESIGN.md, section 9). */
    public static PartyRules defaults() {
        return new PartyRules(5, 20, 1200L);
    }

    public int capacity(PartyType type) {
        Objects.requireNonNull(type, "type");
        return type == PartyType.RAID ? maxRaidSize : maxPartySize;
    }
}
