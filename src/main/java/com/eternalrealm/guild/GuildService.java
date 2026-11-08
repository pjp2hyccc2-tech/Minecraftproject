package com.eternalrealm.guild;

import com.eternalrealm.season.SeasonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * The permanent guild directory of one logical server, plus the pending guild invites. Server thread only.
 *
 * <p><b>Persistence:</b> every change of persistent state increments {@link #revision()}. To save, take
 * {@link #guilds()} (immutable snapshots) and {@link #revision()} on the server thread, write them on the I/O thread
 * with a {@link GuildRepository} and report success back with {@link #markSaved(long)}. Invites are not persisted.</p>
 *
 * <p>Invariants: a player is in at most one guild; names (by {@link GuildNames#nameKey}) and tags are unique; pending
 * invites only exist for players in no guild, at most one per guild and player, and only for existing guilds.</p>
 */
public final class GuildService {
    private final RandomGenerator random;
    private GuildRules rules;
    private final Map<UUID, Guild> guilds = new LinkedHashMap<>();
    private final Map<String, UUID> idsByNameKey = new HashMap<>();
    private final Map<String, UUID> idsByTag = new HashMap<>();
    private final Map<UUID, UUID> guildIdsByPlayer = new HashMap<>();
    private final Map<InviteKey, GuildInvite> invites = new LinkedHashMap<>();
    private long revision;
    private long savedRevision;

    private record InviteKey(UUID guildId, UUID invitee) {
    }

    /**
     * @param stored guilds loaded from the {@link GuildRepository}
     * @throws IllegalArgumentException if the stored guilds are inconsistent ({@link #checkConsistency})
     */
    public GuildService(GuildRules rules, RandomGenerator random, Collection<Guild> stored) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.random = Objects.requireNonNull(random, "random");
        checkConsistency(stored);
        for (Guild guild : stored) {
            index(guild);
        }
    }

    /**
     * Cross-guild invariants of a stored directory: unique ids, names, tags, and no player in two guilds.
     *
     * @throws IllegalArgumentException describing the first violation
     */
    public static void checkConsistency(Collection<Guild> guilds) {
        Set<UUID> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<String> tags = new HashSet<>();
        Map<UUID, UUID> players = new HashMap<>();
        for (Guild guild : guilds) {
            Objects.requireNonNull(guild, "guild");
            if (!ids.add(guild.id())) {
                throw new IllegalArgumentException("duplicate guild id " + guild.id());
            }
            if (!names.add(GuildNames.nameKey(guild.name()))) {
                throw new IllegalArgumentException("duplicate guild name '" + guild.name() + "'");
            }
            if (!tags.add(guild.tag())) {
                throw new IllegalArgumentException("duplicate guild tag " + guild.tag());
            }
            for (UUID player : guild.members().keySet()) {
                UUID other = players.put(player, guild.id());
                if (other != null) {
                    throw new IllegalArgumentException("player " + player + " is in guilds " + other + " and " + guild.id());
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ founding

    /**
     * Checks whether {@link #create} would succeed, without changing anything. Lets the Forge layer charge a founding
     * fee only when the guild will really be created (both calls happen in the same server tick).
     * Order: {@code ALREADY_IN_GUILD}, {@code INVALID_NAME}, {@code INVALID_TAG}, {@code NAME_TAKEN}, {@code TAG_TAKEN}.
     */
    public GuildOutcome checkCreate(UUID founder, String name, String tag) {
        Objects.requireNonNull(founder, "founder");
        if (guildIdsByPlayer.containsKey(founder)) {
            return GuildOutcome.ALREADY_IN_GUILD;
        }
        if (name == null || !GuildNames.isValidName(GuildNames.normalizeName(name))) {
            return GuildOutcome.INVALID_NAME;
        }
        Optional<String> normalizedTag = GuildNames.normalizeTag(tag);
        if (normalizedTag.isEmpty()) {
            return GuildOutcome.INVALID_TAG;
        }
        if (idsByNameKey.containsKey(GuildNames.nameKey(name))) {
            return GuildOutcome.NAME_TAKEN;
        }
        if (idsByTag.containsKey(normalizedTag.get())) {
            return GuildOutcome.TAG_TAKEN;
        }
        return GuildOutcome.OK;
    }

    /** Founds a guild with {@code founder} as master. Cancels the founder's pending invites from other guilds. */
    public GuildResult create(UUID founder, String name, String tag, Instant now) {
        Objects.requireNonNull(now, "now");
        GuildOutcome check = checkCreate(founder, name, tag);
        if (!check.isSuccess()) {
            return GuildResult.failure(check);
        }
        Guild guild = Guild.found(newGuildId(), GuildNames.normalizeName(name),
                GuildNames.normalizeTag(tag).orElseThrow(), founder, now);
        index(guild);
        revision++;
        List<GuildEvent> events = new ArrayList<>();
        events.add(new GuildEvent.Created(guild));
        cancelInvitesTo(founder, events);
        return GuildResult.ok(guild, events);
    }

    // ------------------------------------------------------------------------------------------------ invites

    /**
     * An officer or the master invites a player who is in no guild. Order: {@code SELF}, {@code NOT_IN_GUILD},
     * {@code NO_PERMISSION}, {@code GUILD_FULL}, {@code TARGET_IN_GUILD}, {@code ALREADY_INVITED}.
     */
    public GuildResult invite(UUID inviter, UUID invitee, Instant now) {
        Objects.requireNonNull(inviter, "inviter");
        Objects.requireNonNull(invitee, "invitee");
        Objects.requireNonNull(now, "now");
        if (inviter.equals(invitee)) {
            return GuildResult.failure(GuildOutcome.SELF);
        }
        Guild guild = find(inviter);
        if (guild == null) {
            return GuildResult.failure(GuildOutcome.NOT_IN_GUILD);
        }
        if (!guild.members().get(inviter).rank().canInvite()) {
            return GuildResult.failure(GuildOutcome.NO_PERMISSION);
        }
        if (isFull(guild)) {
            return GuildResult.failure(GuildOutcome.GUILD_FULL);
        }
        if (guildIdsByPlayer.containsKey(invitee)) {
            return GuildResult.failure(GuildOutcome.TARGET_IN_GUILD);
        }
        List<GuildEvent> events = new ArrayList<>();
        if (liveInvite(guild.id(), invitee, now, events) != null) {
            return GuildResult.failure(GuildOutcome.ALREADY_INVITED);
        }
        GuildInvite invite = new GuildInvite(guild.id(), inviter, invitee, now, now.plus(rules.inviteExpiry()));
        invites.put(new InviteKey(guild.id(), invitee), invite);
        events.add(new GuildEvent.Invited(invite));
        return GuildResult.ok(guild, events);
    }

    /** Joins as {@link GuildRank#MEMBER}. A full guild keeps the invite (retry possible while it is valid). */
    public GuildResult accept(UUID invitee, UUID guildId, Instant now) {
        Objects.requireNonNull(invitee, "invitee");
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(now, "now");
        if (guildIdsByPlayer.containsKey(invitee)) {
            return GuildResult.failure(GuildOutcome.ALREADY_IN_GUILD);
        }
        List<GuildEvent> events = new ArrayList<>();
        GuildInvite invite = liveInvite(guildId, invitee, now, events);
        if (invite == null) {
            return GuildResult.failure(GuildOutcome.NO_INVITE, events);
        }
        Guild guild = guilds.get(guildId);
        if (isFull(guild)) {
            return GuildResult.failure(GuildOutcome.GUILD_FULL, events);
        }
        invites.remove(new InviteKey(guildId, invitee));
        Guild joined = guild.withMember(invitee, new GuildMember(GuildRank.MEMBER, now));
        guilds.put(guildId, joined);
        guildIdsByPlayer.put(invitee, guildId);
        revision++;
        events.add(new GuildEvent.Joined(joined, invitee));
        cancelInvitesTo(invitee, events);
        return GuildResult.ok(joined, events);
    }

    public GuildResult decline(UUID invitee, UUID guildId, Instant now) {
        Objects.requireNonNull(invitee, "invitee");
        Objects.requireNonNull(guildId, "guildId");
        Objects.requireNonNull(now, "now");
        List<GuildEvent> events = new ArrayList<>();
        GuildInvite invite = liveInvite(guildId, invitee, now, events);
        if (invite == null) {
            return GuildResult.failure(GuildOutcome.NO_INVITE, events);
        }
        invites.remove(new InviteKey(guildId, invitee));
        events.add(new GuildEvent.InviteDeclined(invite));
        return GuildResult.ok(guilds.get(guildId), events);
    }

    /** Removes every invite that has expired at {@code now}. Call periodically (e.g. once per second). */
    public List<GuildEvent> expireInvites(Instant now) {
        Objects.requireNonNull(now, "now");
        List<GuildEvent> events = new ArrayList<>();
        Iterator<GuildInvite> iterator = invites.values().iterator();
        while (iterator.hasNext()) {
            GuildInvite invite = iterator.next();
            if (invite.isExpired(now)) {
                iterator.remove();
                events.add(new GuildEvent.InviteExpired(invite));
            }
        }
        return events.isEmpty() ? List.of() : List.copyOf(events);
    }

    // ------------------------------------------------------------------------------------------------ membership

    /** Leaves the guild. The master must {@link #transferMaster} or {@link #disband} first. */
    public GuildResult leave(UUID member) {
        Objects.requireNonNull(member, "member");
        Guild guild = find(member);
        if (guild == null) {
            return GuildResult.failure(GuildOutcome.NOT_IN_GUILD);
        }
        if (guild.members().get(member).rank() == GuildRank.MASTER) {
            return GuildResult.failure(GuildOutcome.MASTER_MUST_TRANSFER);
        }
        Guild remaining = removeMember(guild, member);
        return GuildResult.ok(remaining, List.of(new GuildEvent.Left(remaining, member)));
    }

    /**
     * Removes a member: the master may kick anyone, an officer only members. Order: {@code SELF},
     * {@code NOT_IN_GUILD}, {@code NOT_A_MEMBER}, {@code NO_PERMISSION}.
     */
    public GuildResult kick(UUID actor, UUID target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (actor.equals(target)) {
            return GuildResult.failure(GuildOutcome.SELF);
        }
        Guild guild = find(actor);
        if (guild == null) {
            return GuildResult.failure(GuildOutcome.NOT_IN_GUILD);
        }
        GuildMember targetMember = guild.members().get(target);
        if (targetMember == null) {
            return GuildResult.failure(GuildOutcome.NOT_A_MEMBER);
        }
        if (!guild.members().get(actor).rank().canKick(targetMember.rank())) {
            return GuildResult.failure(GuildOutcome.NO_PERMISSION);
        }
        Guild remaining = removeMember(guild, target);
        return GuildResult.ok(remaining, List.of(new GuildEvent.Kicked(remaining, target, actor)));
    }

    /** MEMBER to OFFICER (master only). */
    public GuildResult promote(UUID master, UUID target) {
        return changeRank(master, target, GuildRank.MEMBER, GuildRank.OFFICER, GuildOutcome.ALREADY_OFFICER);
    }

    /** OFFICER to MEMBER (master only). */
    public GuildResult demote(UUID master, UUID target) {
        return changeRank(master, target, GuildRank.OFFICER, GuildRank.MEMBER, GuildOutcome.ALREADY_MEMBER);
    }

    private GuildResult changeRank(UUID master, UUID target, GuildRank from, GuildRank to, GuildOutcome unchanged) {
        Guild guild = findManagedGuild(master, target);
        GuildOutcome denied = denyRankChange(guild, master, target);
        if (denied != null) {
            return GuildResult.failure(denied);
        }
        GuildMember member = guild.members().get(target);
        if (member.rank() != from) {
            return GuildResult.failure(unchanged);
        }
        Guild updated = guild.withMember(target, member.withRank(to));
        guilds.put(updated.id(), updated);
        revision++;
        return GuildResult.ok(updated, List.of(new GuildEvent.RankChanged(updated, target, from, to)));
    }

    /** Makes {@code target} the master; the previous master becomes an officer. */
    public GuildResult transferMaster(UUID master, UUID target) {
        Guild guild = findManagedGuild(master, target);
        GuildOutcome denied = denyRankChange(guild, master, target);
        if (denied != null) {
            return GuildResult.failure(denied);
        }
        Map<UUID, GuildMember> members = new LinkedHashMap<>(guild.members());
        members.put(master, members.get(master).withRank(GuildRank.OFFICER));
        members.put(target, members.get(target).withRank(GuildRank.MASTER));
        Guild updated = new Guild(guild.id(), guild.name(), guild.tag(), guild.createdAt(), members,
                guild.contributions());
        guilds.put(updated.id(), updated);
        revision++;
        return GuildResult.ok(updated, List.of(new GuildEvent.MasterTransferred(updated, master, target)));
    }

    /** Deletes the guild (master only) and cancels its invites. Disbanded guilds are not kept. */
    public GuildResult disband(UUID master) {
        Objects.requireNonNull(master, "master");
        Guild guild = find(master);
        if (guild == null) {
            return GuildResult.failure(GuildOutcome.NOT_IN_GUILD);
        }
        if (!guild.members().get(master).rank().canManageRanks()) {
            return GuildResult.failure(GuildOutcome.NO_PERMISSION);
        }
        unindex(guild);
        revision++;
        List<GuildEvent> events = new ArrayList<>();
        events.add(new GuildEvent.Disbanded(guild));
        Iterator<GuildInvite> iterator = invites.values().iterator();
        while (iterator.hasNext()) {
            GuildInvite invite = iterator.next();
            if (invite.guildId().equals(guild.id())) {
                iterator.remove();
                events.add(new GuildEvent.InviteCancelled(invite));
            }
        }
        return GuildResult.ok(null, events);
    }

    // ------------------------------------------------------------------------------------------------ contribution

    /**
     * Credits contribution earned by {@code member} to their guild for {@code season} (saturating at
     * {@link Long#MAX_VALUE}). Emits {@link GuildEvent.LevelUp} when the guild reaches a new level.
     *
     * @param points server-computed points, {@code > 0}
     */
    public GuildResult addContribution(UUID member, SeasonId season, long points) {
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(season, "season");
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive: " + points);
        }
        Guild guild = find(member);
        if (guild == null) {
            return GuildResult.failure(GuildOutcome.NOT_IN_GUILD);
        }
        int previousLevel = guild.level(rules);
        Guild updated = guild.withContribution(season, Guild.saturatedAdd(guild.contribution(season), points));
        guilds.put(updated.id(), updated);
        revision++;
        int newLevel = updated.level(rules);
        return GuildResult.ok(updated, newLevel > previousLevel
                ? List.of(new GuildEvent.LevelUp(updated, previousLevel, newLevel))
                : List.of());
    }

    // ------------------------------------------------------------------------------------------------ queries

    public Optional<Guild> guildOf(UUID player) {
        return Optional.ofNullable(find(player));
    }

    public Optional<Guild> guild(UUID guildId) {
        return Optional.ofNullable(guilds.get(guildId));
    }

    /** Looks a guild up by name, ignoring case and full-width/half-width differences. */
    public Optional<Guild> byName(String name) {
        Objects.requireNonNull(name, "name");
        UUID id = idsByNameKey.get(GuildNames.nameKey(name));
        return id == null ? Optional.empty() : Optional.of(guilds.get(id));
    }

    /** Looks a guild up by tag, ignoring case. */
    public Optional<Guild> byTag(String tag) {
        return GuildNames.normalizeTag(tag).map(idsByTag::get).map(guilds::get);
    }

    /** Immutable snapshots of all guilds in founding order (the form to persist). */
    public List<Guild> guilds() {
        return List.copyOf(guilds.values());
    }

    public int size() {
        return guilds.size();
    }

    /** Valid invites the player can still accept, oldest first. */
    public List<GuildInvite> invitesFor(UUID invitee, Instant now) {
        List<GuildInvite> result = new ArrayList<>();
        for (GuildInvite invite : invites.values()) {
            if (invite.invitee().equals(invitee) && !invite.isExpired(now)) {
                result.add(invite);
            }
        }
        return List.copyOf(result);
    }

    public int level(Guild guild) {
        return guild.level(rules);
    }

    public int memberCap(Guild guild) {
        return guild.memberCap(rules);
    }

    public GuildRules rules() {
        return rules;
    }

    /**
     * Applies reloaded rules. Levels are derived, so they follow at once; a guild above a lowered member cap keeps all
     * members but accepts nobody new.
     */
    public void setRules(GuildRules newRules) {
        this.rules = Objects.requireNonNull(newRules, "newRules");
    }

    // ------------------------------------------------------------------------------------------------ persistence

    /** Incremented by every change of persistent guild data. */
    public long revision() {
        return revision;
    }

    public boolean isDirty() {
        return revision > savedRevision;
    }

    /** Records that the snapshot taken at {@code persistedRevision} has been stored. Never moves backwards. */
    public void markSaved(long persistedRevision) {
        if (persistedRevision > savedRevision) {
            savedRevision = persistedRevision;
        }
    }

    // ------------------------------------------------------------------------------------------------ internals

    private Guild find(UUID player) {
        UUID id = guildIdsByPlayer.get(player);
        return id == null ? null : guilds.get(id);
    }

    private Guild findManagedGuild(UUID master, UUID target) {
        Objects.requireNonNull(master, "master");
        Objects.requireNonNull(target, "target");
        return find(master);
    }

    /** Order: {@code SELF}, {@code NOT_IN_GUILD}, {@code NO_PERMISSION}, {@code NOT_A_MEMBER}. */
    private static GuildOutcome denyRankChange(Guild guild, UUID master, UUID target) {
        if (master.equals(target)) {
            return GuildOutcome.SELF;
        }
        if (guild == null) {
            return GuildOutcome.NOT_IN_GUILD;
        }
        if (!guild.members().get(master).rank().canManageRanks()) {
            return GuildOutcome.NO_PERMISSION;
        }
        return guild.isMember(target) ? null : GuildOutcome.NOT_A_MEMBER;
    }

    private boolean isFull(Guild guild) {
        return guild.size() >= guild.memberCap(rules);
    }

    private Guild removeMember(Guild guild, UUID member) {
        Guild remaining = guild.withoutMember(member);
        guilds.put(remaining.id(), remaining);
        guildIdsByPlayer.remove(member);
        revision++;
        return remaining;
    }

    private GuildInvite liveInvite(UUID guildId, UUID invitee, Instant now, List<GuildEvent> events) {
        InviteKey key = new InviteKey(guildId, invitee);
        GuildInvite invite = invites.get(key);
        if (invite != null && invite.isExpired(now)) {
            invites.remove(key);
            events.add(new GuildEvent.InviteExpired(invite));
            return null;
        }
        return invite;
    }

    private void cancelInvitesTo(UUID invitee, List<GuildEvent> events) {
        Iterator<GuildInvite> iterator = invites.values().iterator();
        while (iterator.hasNext()) {
            GuildInvite invite = iterator.next();
            if (invite.invitee().equals(invitee)) {
                iterator.remove();
                events.add(new GuildEvent.InviteCancelled(invite));
            }
        }
    }

    private void index(Guild guild) {
        guilds.put(guild.id(), guild);
        idsByNameKey.put(GuildNames.nameKey(guild.name()), guild.id());
        idsByTag.put(guild.tag(), guild.id());
        for (UUID player : guild.members().keySet()) {
            guildIdsByPlayer.put(player, guild.id());
        }
    }

    private void unindex(Guild guild) {
        guilds.remove(guild.id());
        idsByNameKey.remove(GuildNames.nameKey(guild.name()));
        idsByTag.remove(guild.tag());
        for (UUID player : guild.members().keySet()) {
            guildIdsByPlayer.remove(player);
        }
    }

    /** Random version-4 UUID from the injected generator, never one already in use. */
    private UUID newGuildId() {
        while (true) {
            long most = (random.nextLong() & ~0xF000L) | 0x4000L;
            long least = (random.nextLong() & ~(0x3L << 62)) | (0x2L << 62);
            UUID id = new UUID(most, least);
            if (!guilds.containsKey(id)) {
                return id;
            }
        }
    }
}
