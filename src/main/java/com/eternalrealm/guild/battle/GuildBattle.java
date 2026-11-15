package com.eternalrealm.guild.battle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One guild battle (ギルド対抗戦, docs/modules/guild_battles.md): the challenger's side (RED) against the challenged
 * guild's (BLUE) in an arena. Pure and single-threaded: the server feeds it the time and what happens, it answers
 * with {@link Event}s.
 *
 * <pre>
 * MUSTER   members of both guilds join (up to maxPerSide each)  -> PREPARE when both sides have minPerSide, else
 *                                                                 CANCELLED
 * PREPARE  the fighters wait at their gates (no one can be hurt) -> FIGHT
 * FIGHT    every knock-out scores for the other side; a fighter knocked out recovers at their gate (cannot hurt or
 *          be hurt for respawnTicks, then cannot be hurt for shieldTicks)  -> ENDED at the time limit, at the
 *          knock-out target, or when a side has nobody left
 * ENDED    the results (more knock-outs wins, the same is a draw) for endTicks -> closed, everyone goes home
 * </pre>
 */
public final class GuildBattle {
    public enum Phase {
        MUSTER, PREPARE, FIGHT, ENDED, CANCELLED;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Side {
        RED, BLUE;

        public Side other() {
            return this == RED ? BLUE : RED;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** A guild on one side. */
    public record Team(UUID guild, String name, String tag) {
        public Team {
            Objects.requireNonNull(guild, "guild");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(tag, "tag");
        }
    }

    public enum JoinResult {
        OK, NOT_MUSTERING, NOT_IN_THESE_GUILDS, ALREADY_IN, SIDE_FULL
    }

    /** What happened, for the server to show and do. */
    public sealed interface Event permits Mustered, Cancelled, Started, KnockedOut, Ended, Closed {
    }

    /** The muster is over: these fighters go to the arena. */
    public record Mustered(List<Fighter> fighters) implements Event {
    }

    /** The battle did not happen ({@code reason}: "no_red", "no_blue", "no_one"). */
    public record Cancelled(String reason) implements Event {
    }

    /** The fight begins. */
    public record Started() implements Event {
    }

    /** {@code victim} fell ({@code attacker} null: by their own misfortune); {@code scored} gets the point. */
    public record KnockedOut(Fighter victim, Fighter attacker, Side scored, int red, int blue) implements Event {
    }

    /** The fight is over: {@code winner} empty for a draw. */
    public record Ended(Optional<Side> winner, int red, int blue, Optional<Fighter> mvp, String why) implements Event {
    }

    /** The results were shown: everyone goes home. */
    public record Closed() implements Event {
    }

    /** One fighter (a snapshot when handed out). */
    public static final class Fighter {
        private final UUID id;
        private final String name;
        private final Side side;
        private final int order;
        private int knockOuts;
        private int falls;
        private boolean present = true;
        private long downUntil = Long.MIN_VALUE;
        private long shieldUntil = Long.MIN_VALUE;

        Fighter(UUID id, String name, Side side, int order) {
            this.id = id;
            this.name = name;
            this.side = side;
            this.order = order;
        }

        private Fighter copy() {
            Fighter copy = new Fighter(id, name, side, order);
            copy.knockOuts = knockOuts;
            copy.falls = falls;
            copy.present = present;
            copy.downUntil = downUntil;
            copy.shieldUntil = shieldUntil;
            return copy;
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public Side side() {
            return side;
        }

        public int knockOuts() {
            return knockOuts;
        }

        public int falls() {
            return falls;
        }

        /** Still in the battle (not gone home or logged out). */
        public boolean present() {
            return present;
        }

        /** Recovering at the gate after a knock-out: cannot hurt or be hurt. */
        public boolean down(long now) {
            return now < downUntil;
        }

        /** Cannot be hurt (recovering, or just back on their feet). */
        public boolean sheltered(long now) {
            return now < shieldUntil;
        }

        public long downUntil() {
            return downUntil;
        }
    }

    private final UUID id;
    private final Team red;
    private final Team blue;
    private final int slot;
    private final BattleRules rules;
    private final Map<UUID, Fighter> fighters = new LinkedHashMap<>();
    private final int[] score = new int[2];
    private Phase phase = Phase.MUSTER;
    private long phaseEnds;
    private long startedAt = -1;
    private Ended result;

    public GuildBattle(UUID id, Team red, Team blue, int slot, BattleRules rules, long now) {
        this.id = Objects.requireNonNull(id, "id");
        this.red = Objects.requireNonNull(red, "red");
        this.blue = Objects.requireNonNull(blue, "blue");
        if (red.guild().equals(blue.guild())) {
            throw new IllegalArgumentException("a guild cannot fight itself");
        }
        this.slot = slot;
        this.rules = Objects.requireNonNull(rules, "rules");
        this.phaseEnds = now + rules.musterTicks();
    }

    // ------------------------------------------------------------------ joining

    /** {@code player} of {@code guild} joins the side of their guild (only while mustering). */
    public JoinResult join(UUID player, String name, UUID guild) {
        if (phase != Phase.MUSTER) {
            return JoinResult.NOT_MUSTERING;
        }
        Optional<Side> side = sideOfGuild(guild);
        if (side.isEmpty()) {
            return JoinResult.NOT_IN_THESE_GUILDS;
        }
        if (fighters.containsKey(player)) {
            return JoinResult.ALREADY_IN;
        }
        if (count(side.get()) >= rules.maxPerSide()) {
            return JoinResult.SIDE_FULL;
        }
        fighters.put(player, new Fighter(player, name == null ? "?" : name, side.get(), fighters.size()));
        return JoinResult.OK;
    }

    /**
     * {@code player} leaves (gone home, logged out, left the arena). While mustering they are simply off the list; later
     * they stay on it for the results, but no longer count, and a side with nobody left loses at once.
     */
    public List<Event> withdraw(UUID player, long now) {
        Fighter fighter = fighters.get(player);
        if (fighter == null) {
            return List.of();
        }
        if (phase == Phase.MUSTER) {
            fighters.remove(player);
            return List.of();
        }
        if (!fighter.present) {
            return List.of();
        }
        fighter.present = false;
        if (phase == Phase.PREPARE || phase == Phase.FIGHT) {
            boolean redLeft = present(Side.RED) > 0;
            boolean blueLeft = present(Side.BLUE) > 0;
            if (!redLeft || !blueLeft) {
                Optional<Side> winner = redLeft ? Optional.of(Side.RED) : blueLeft ? Optional.of(Side.BLUE)
                        : Optional.empty();
                return List.of(end(now, winner, "forfeit"));
            }
        }
        return List.of();
    }

    // ------------------------------------------------------------------ time

    /** The clock moved on to {@code now}. */
    public List<Event> tick(long now) {
        List<Event> events = new ArrayList<>();
        switch (phase) {
            case MUSTER -> {
                if (now >= phaseEnds) {
                    int reds = count(Side.RED);
                    int blues = count(Side.BLUE);
                    if (reds >= rules.minPerSide() && blues >= rules.minPerSide()) {
                        phase = Phase.PREPARE;
                        phaseEnds = now + rules.prepareTicks();
                        events.add(new Mustered(snapshot()));
                    } else {
                        phase = Phase.CANCELLED;
                        events.add(new Cancelled(reds < rules.minPerSide() && blues < rules.minPerSide() ? "no_one"
                                : reds < rules.minPerSide() ? "no_red" : "no_blue"));
                    }
                }
            }
            case PREPARE -> {
                if (now >= phaseEnds) {
                    phase = Phase.FIGHT;
                    startedAt = now;
                    phaseEnds = now + rules.fightTicks();
                    events.add(new Started());
                }
            }
            case FIGHT -> {
                if (now >= phaseEnds) {
                    events.add(end(now, leader(), "time"));
                }
            }
            case ENDED -> {
                if (now >= phaseEnds) {
                    phase = Phase.CANCELLED;          // over: the registry lets it go
                    events.add(new Closed());
                }
            }
            case CANCELLED -> {
            }
        }
        return events;
    }

    /** The muster ends now (everyone who wanted to has joined). Only while mustering. */
    public void closeMusterEarly(long now) {
        if (phase == Phase.MUSTER) {
            phaseEnds = Math.min(phaseEnds, now);
        }
    }

    // ------------------------------------------------------------------ fighting

    /** Whether {@code attacker} may hurt {@code victim} now: opponents in the fight, neither down, the victim not
     * sheltered. */
    public boolean mayHurt(UUID attacker, UUID victim, long now) {
        if (phase != Phase.FIGHT) {
            return false;
        }
        Fighter a = fighters.get(attacker);
        Fighter v = fighters.get(victim);
        return a != null && v != null && a.present && v.present && a.side != v.side && !a.down(now)
                && !v.sheltered(now);
    }

    /**
     * {@code victim} fell in the fight ({@code attacker}: who struck the last blow, null or not an opponent for a
     * fall of their own): a point for the other side, the victim recovers at their gate. Nothing happens to someone
     * already down or outside the fight.
     */
    public List<Event> knockOut(UUID victim, UUID attacker, long now) {
        Fighter v = fighters.get(victim);
        if (phase != Phase.FIGHT || v == null || !v.present || v.down(now)) {
            return List.of();
        }
        Fighter a = attacker == null ? null : fighters.get(attacker);
        if (a != null && (a.side == v.side || !a.present)) {
            a = null;
        }
        v.falls++;
        v.downUntil = now + rules.respawnTicks();
        v.shieldUntil = v.downUntil + rules.shieldTicks();
        if (a != null) {
            a.knockOuts++;
        }
        Side scored = v.side.other();
        score[scored.ordinal()]++;
        List<Event> events = new ArrayList<>();
        events.add(new KnockedOut(v.copy(), a == null ? null : a.copy(), scored, score[0], score[1]));
        if (score[scored.ordinal()] >= rules.knockOutTarget()) {
            events.add(end(now, Optional.of(scored), "target"));
        }
        return events;
    }

    private Ended end(long now, Optional<Side> winner, String why) {
        phase = Phase.ENDED;
        phaseEnds = now + rules.endTicks();
        result = new Ended(winner, score[0], score[1], mvp(), why);
        return result;
    }

    /** The side ahead, empty when level. */
    public Optional<Side> leader() {
        return score[0] == score[1] ? Optional.empty() : Optional.of(score[0] > score[1] ? Side.RED : Side.BLUE);
    }

    /** The fighter with the most knock-outs (fewest falls, then who joined first, break ties); none without any. */
    public Optional<Fighter> mvp() {
        return fighters.values().stream().filter(f -> f.knockOuts > 0)
                .min(Comparator.comparingInt((Fighter f) -> -f.knockOuts).thenComparingInt(f -> f.falls)
                        .thenComparingInt(f -> f.order))
                .map(Fighter::copy);
    }

    // ------------------------------------------------------------------ reading

    public UUID id() {
        return id;
    }

    public Team red() {
        return red;
    }

    public Team blue() {
        return blue;
    }

    public Team team(Side side) {
        return side == Side.RED ? red : blue;
    }

    public int slot() {
        return slot;
    }

    public BattleRules rules() {
        return rules;
    }

    public Phase phase() {
        return phase;
    }

    /** When the current phase ends (tick). */
    public long phaseEnds() {
        return phaseEnds;
    }

    public long startedAt() {
        return startedAt;
    }

    public int score(Side side) {
        return score[side.ordinal()];
    }

    /** The result once the fight is over. */
    public Optional<Ended> result() {
        return Optional.ofNullable(result);
    }

    /** Whether the battle is over (ended and shown, or cancelled). */
    public boolean over() {
        return phase == Phase.CANCELLED;
    }

    public boolean involves(UUID guild) {
        return red.guild().equals(guild) || blue.guild().equals(guild);
    }

    public Optional<Side> sideOfGuild(UUID guild) {
        return red.guild().equals(guild) ? Optional.of(Side.RED)
                : blue.guild().equals(guild) ? Optional.of(Side.BLUE) : Optional.empty();
    }

    /** The side of a fighter (also one who has left). */
    public Optional<Side> sideOf(UUID player) {
        Fighter fighter = fighters.get(player);
        return fighter == null ? Optional.empty() : Optional.of(fighter.side);
    }

    /** A copy of a fighter. */
    public Optional<Fighter> fighter(UUID player) {
        Fighter fighter = fighters.get(player);
        return fighter == null ? Optional.empty() : Optional.of(fighter.copy());
    }

    /** Whether {@code player} is a fighter still in the battle. */
    public boolean isPresent(UUID player) {
        Fighter fighter = fighters.get(player);
        return fighter != null && fighter.present;
    }

    /** Copies of every fighter, in joining order. */
    public List<Fighter> fighters() {
        return snapshot();
    }

    public int count(Side side) {
        int n = 0;
        for (Fighter fighter : fighters.values()) {
            if (fighter.side == side) {
                n++;
            }
        }
        return n;
    }

    /** Fighters of {@code side} still in the battle. */
    public int present(Side side) {
        int n = 0;
        for (Fighter fighter : fighters.values()) {
            if (fighter.side == side && fighter.present) {
                n++;
            }
        }
        return n;
    }

    private List<Fighter> snapshot() {
        List<Fighter> copies = new ArrayList<>();
        for (Fighter fighter : fighters.values()) {
            copies.add(fighter.copy());
        }
        return Collections.unmodifiableList(copies);
    }

    /** Fighters of a side (copies). */
    public List<Fighter> fighters(Side side) {
        List<Fighter> out = new ArrayList<>();
        for (Fighter fighter : fighters.values()) {
            if (fighter.side == side) {
                out.add(fighter.copy());
            }
        }
        return out;
    }

    /** The ids of everyone on the list. */
    public Collection<UUID> fighterIds() {
        return List.copyOf(fighters.keySet());
    }
}
