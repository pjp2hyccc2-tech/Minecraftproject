package com.eternalrealm.guild;

import com.eternalrealm.season.SeasonId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable snapshot of a guild, also its persistence form. {@link GuildService} replaces the snapshot on every change,
 * so a list of snapshots can be handed to the I/O thread as is.
 *
 * <p>Guilds are permanent: they survive season resets. Contribution is kept per season (for the seasonal guild
 * ranking); the guild level follows the total over all seasons ({@link GuildRules}).</p>
 *
 * <p>Invariants: valid name and tag ({@link GuildNames}), at least one member, exactly one {@link GuildRank#MASTER},
 * contributions {@code >= 0}. Member-cap and level rules are <em>not</em> checked here because they depend on the
 * (reloadable) rules: a guild may legitimately be above a lowered cap.</p>
 *
 * @param members       player UUID to membership, in join order (the founder first)
 * @param contributions season to contribution points earned in that season
 */
public record Guild(UUID id, String name, String tag, Instant createdAt, Map<UUID, GuildMember> members,
                    Map<SeasonId, Long> contributions) {
    public Guild {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!GuildNames.isValidName(name)) {
            throw new IllegalArgumentException("invalid guild name '" + name + "'");
        }
        if (!GuildNames.isValidTag(tag)) {
            throw new IllegalArgumentException("invalid guild tag '" + tag + "'");
        }
        Map<UUID, GuildMember> memberCopy = new LinkedHashMap<>();
        int masters = 0;
        for (Map.Entry<UUID, GuildMember> entry : members.entrySet()) {
            GuildMember member = Objects.requireNonNull(entry.getValue(), "member");
            memberCopy.put(Objects.requireNonNull(entry.getKey(), "member id"), member);
            if (member.rank() == GuildRank.MASTER) {
                masters++;
            }
        }
        if (memberCopy.isEmpty() || masters != 1) {
            throw new IllegalArgumentException("guild " + id + " needs members with exactly one master, found "
                    + masters + " masters in " + memberCopy.size() + " members");
        }
        Map<SeasonId, Long> contributionCopy = new LinkedHashMap<>();
        for (Map.Entry<SeasonId, Long> entry : contributions.entrySet()) {
            long points = Objects.requireNonNull(entry.getValue(), "points");
            if (points < 0) {
                throw new IllegalArgumentException("negative contribution for season " + entry.getKey());
            }
            contributionCopy.put(Objects.requireNonNull(entry.getKey(), "season"), points);
        }
        members = Collections.unmodifiableMap(memberCopy);
        contributions = Collections.unmodifiableMap(contributionCopy);
    }

    /** A new guild with its founder as master and no contribution yet. */
    static Guild found(UUID id, String name, String tag, UUID founder, Instant now) {
        return new Guild(id, name, tag, now, Map.of(founder, new GuildMember(GuildRank.MASTER, now)), Map.of());
    }

    public UUID master() {
        for (Map.Entry<UUID, GuildMember> entry : members.entrySet()) {
            if (entry.getValue().rank() == GuildRank.MASTER) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("guild without master: " + id);
    }

    public Optional<GuildMember> member(UUID playerId) {
        return Optional.ofNullable(members.get(playerId));
    }

    public Optional<GuildRank> rankOf(UUID playerId) {
        return member(playerId).map(GuildMember::rank);
    }

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public int size() {
        return members.size();
    }

    /** Points earned in {@code season}, 0 if none. */
    public long contribution(SeasonId season) {
        return contributions.getOrDefault(season, 0L);
    }

    /** Points over all seasons (saturating). */
    public long totalContribution() {
        long total = 0;
        for (long points : contributions.values()) {
            total = saturatedAdd(total, points);
        }
        return total;
    }

    public int level(GuildRules rules) {
        return rules.levelFor(totalContribution());
    }

    public int memberCap(GuildRules rules) {
        return rules.memberCap(level(rules));
    }

    Guild withMember(UUID playerId, GuildMember member) {
        Map<UUID, GuildMember> copy = new LinkedHashMap<>(members);
        copy.put(playerId, member);
        return new Guild(id, name, tag, createdAt, copy, contributions);
    }

    Guild withoutMember(UUID playerId) {
        Map<UUID, GuildMember> copy = new LinkedHashMap<>(members);
        copy.remove(playerId);
        return new Guild(id, name, tag, createdAt, copy, contributions);
    }

    Guild withContribution(SeasonId season, long points) {
        Map<SeasonId, Long> copy = new LinkedHashMap<>(contributions);
        copy.put(season, points);
        return new Guild(id, name, tag, createdAt, members, copy);
    }

    static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }
}
