package com.eternalrealm.guild.battle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The guild battles of a server (docs/modules/guild_battles.md): open challenges, the battles under way (one per
 * guild, at most one per arena) and the guilds resting after a battle. Pure and single-threaded.
 */
public final class GuildBattles {
    public enum Answer {
        OK, SELF, CHALLENGER_BUSY, TARGET_BUSY, CHALLENGER_RESTING, TARGET_RESTING, ALREADY_CHALLENGED, NO_CHALLENGE,
        NO_ARENA;

        public boolean ok() {
            return this == OK;
        }

        public String messageKey() {
            return "message.eternalrealm.guild_battle." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** A challenge from one guild to another, waiting for an answer until {@code expiresAt}. */
    public record Challenge(GuildBattle.Team from, GuildBattle.Team to, long expiresAt) {
    }

    /** The outcome of accepting: the battle, or why not. */
    public record Accepted(Answer answer, GuildBattle battle) {
    }

    private final BattleRules rules;
    private final int arenas;
    /** Open challenges by the challenging guild (one each). */
    private final Map<UUID, Challenge> challenges = new LinkedHashMap<>();
    private final List<GuildBattle> battles = new ArrayList<>();
    /** Guild -> the tick until which it rests after a battle. */
    private final Map<UUID, Long> resting = new LinkedHashMap<>();

    public GuildBattles(BattleRules rules, int arenas) {
        this.rules = Objects.requireNonNull(rules, "rules");
        if (arenas < 1) {
            throw new IllegalArgumentException("arenas must be positive: " + arenas);
        }
        this.arenas = arenas;
    }

    public BattleRules rules() {
        return rules;
    }

    // ------------------------------------------------------------------ challenges

    /** {@code from} challenges {@code to}; a guild has one open challenge at a time. */
    public Answer challenge(GuildBattle.Team from, GuildBattle.Team to, long now) {
        if (from.guild().equals(to.guild())) {
            return Answer.SELF;
        }
        if (battleOfGuild(from.guild()).isPresent()) {
            return Answer.CHALLENGER_BUSY;
        }
        if (battleOfGuild(to.guild()).isPresent()) {
            return Answer.TARGET_BUSY;
        }
        if (restingUntil(from.guild()) > now) {
            return Answer.CHALLENGER_RESTING;
        }
        if (restingUntil(to.guild()) > now) {
            return Answer.TARGET_RESTING;
        }
        Challenge open = challenges.get(from.guild());
        if (open != null && open.expiresAt() > now && open.to().guild().equals(to.guild())) {
            return Answer.ALREADY_CHALLENGED;
        }
        challenges.put(from.guild(), new Challenge(from, to, now + rules.challengeTicks()));
        return Answer.OK;
    }

    /** The open challenges to {@code guild}, oldest first. */
    public List<Challenge> challengesTo(UUID guild, long now) {
        List<Challenge> found = new ArrayList<>();
        for (Challenge challenge : challenges.values()) {
            if (challenge.to().guild().equals(guild) && challenge.expiresAt() > now) {
                found.add(challenge);
            }
        }
        return found;
    }

    /** The open challenge {@code guild} made, if any. */
    public Optional<Challenge> challengeFrom(UUID guild, long now) {
        Challenge challenge = challenges.get(guild);
        return challenge != null && challenge.expiresAt() > now ? Optional.of(challenge) : Optional.empty();
    }

    /**
     * {@code to} accepts the challenge of {@code from} (null: the oldest one): the battle starts mustering in a free
     * arena.
     */
    public Accepted accept(UUID to, UUID from, UUID battleId, long now) {
        return accept(to, from, battleId, now, slot -> true);
    }

    /** {@link #accept} in the lowest free arena that is also {@code usable} (built and ready). */
    public Accepted accept(UUID to, UUID from, UUID battleId, long now, java.util.function.IntPredicate usable) {
        Challenge challenge = null;
        for (Challenge open : challengesTo(to, now)) {
            if (from == null || open.from().guild().equals(from)) {
                challenge = open;
                break;
            }
        }
        if (challenge == null) {
            return new Accepted(Answer.NO_CHALLENGE, null);
        }
        if (battleOfGuild(challenge.from().guild()).isPresent()) {
            return new Accepted(Answer.CHALLENGER_BUSY, null);
        }
        if (battleOfGuild(to).isPresent()) {
            return new Accepted(Answer.TARGET_BUSY, null);
        }
        int slot = freeArena(usable);
        if (slot < 0) {
            return new Accepted(Answer.NO_ARENA, null);
        }
        // this challenge and every other one to or from the two guilds is settled now
        UUID challenger = challenge.from().guild();
        challenges.values().removeIf(c -> c.to().guild().equals(to) || c.from().guild().equals(to)
                || c.to().guild().equals(challenger) || c.from().guild().equals(challenger));
        GuildBattle battle = new GuildBattle(battleId, challenge.from(), challenge.to(), slot, rules, now);
        battles.add(battle);
        return new Accepted(Answer.OK, battle);
    }

    /** {@code to} turns down the challenge of {@code from} (null: all of them). @return the declined challenges */
    public List<Challenge> decline(UUID to, UUID from, long now) {
        List<Challenge> declined = new ArrayList<>();
        Iterator<Challenge> it = challenges.values().iterator();
        while (it.hasNext()) {
            Challenge challenge = it.next();
            if (challenge.to().guild().equals(to) && (from == null || challenge.from().guild().equals(from))) {
                declined.add(challenge);
                it.remove();
            }
        }
        return declined;
    }

    /** {@code from} takes its challenge back. */
    public Optional<Challenge> withdraw(UUID from) {
        return Optional.ofNullable(challenges.remove(from));
    }

    /** Challenges whose time ran out (removed). */
    public List<Challenge> expire(long now) {
        List<Challenge> expired = new ArrayList<>();
        Iterator<Challenge> it = challenges.values().iterator();
        while (it.hasNext()) {
            Challenge challenge = it.next();
            if (challenge.expiresAt() <= now) {
                expired.add(challenge);
                it.remove();
            }
        }
        resting.values().removeIf(until -> until <= now);
        return expired;
    }

    // ------------------------------------------------------------------ battles

    public List<GuildBattle> battles() {
        return List.copyOf(battles);
    }

    public Optional<GuildBattle> battleOfGuild(UUID guild) {
        for (GuildBattle battle : battles) {
            if (battle.involves(guild) && !battle.over()) {
                return Optional.of(battle);
            }
        }
        return Optional.empty();
    }

    /** The battle {@code player} fights in (also after they left it, until it is over). */
    public Optional<GuildBattle> battleOfPlayer(UUID player) {
        for (GuildBattle battle : battles) {
            if (!battle.over() && battle.sideOf(player).isPresent()) {
                return Optional.of(battle);
            }
        }
        return Optional.empty();
    }

    /** The battle in arena {@code slot}. */
    public Optional<GuildBattle> inArena(int slot) {
        for (GuildBattle battle : battles) {
            if (!battle.over() && battle.slot() == slot) {
                return Optional.of(battle);
            }
        }
        return Optional.empty();
    }

    /** A battle is over: it leaves the list and both guilds rest (only a battle that was fought). */
    public void close(GuildBattle battle, long now) {
        battles.remove(battle);
        if (battle.result().isPresent()) {
            resting.put(battle.red().guild(), now + rules.cooldownTicks());
            resting.put(battle.blue().guild(), now + rules.cooldownTicks());
        }
    }

    /** The tick until which {@code guild} rests (0 when it does not). */
    public long restingUntil(UUID guild) {
        return resting.getOrDefault(guild, 0L);
    }

    /** The lowest arena number no battle uses and {@code usable} allows, or -1. */
    int freeArena(java.util.function.IntPredicate usable) {
        for (int slot = 0; slot < arenas; slot++) {
            if (inArena(slot).isEmpty() && usable.test(slot)) {
                return slot;
            }
        }
        return -1;
    }

    public int arenas() {
        return arenas;
    }
}
