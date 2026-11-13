package com.eternalrealm.dungeon;

import com.eternalrealm.boss.Difficulty;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A party's ready check (出発の準備) before it sets out for a dungeon: the leader chose the dungeon and difficulty at
 * the gate, and every member answers 準備OK or stays 準備中. The leader starts out ready (they asked). Once everyone
 * is ready a short countdown runs ({@link #COUNTDOWN_TICKS}) and the party sets out; anyone going back to 準備中 stops
 * it. If the party is not ready within {@link #TIMEOUT_TICKS} the check ends. Pure; the Forge side
 * ({@code ReadyChecks}) asks, shows and starts the run.
 */
public final class ReadyCheck {
    /** How long the party has to get ready (2 minutes). */
    public static final long TIMEOUT_TICKS = 20L * 120;
    /** From "everyone is ready" to setting out (3 seconds). */
    public static final long COUNTDOWN_TICKS = 20L * 3;

    public enum Phase {
        /** Someone is still 準備中. */
        WAITING,
        /** Everyone is ready: the countdown runs. */
        COUNTDOWN,
        /** The countdown ran out: set out now. */
        GO,
        /** Not everyone was ready in time. */
        TIMED_OUT
    }

    private final String contentId;
    private final Difficulty difficulty;
    private final UUID leader;
    private final List<UUID> members;
    private final Set<UUID> ready = new HashSet<>();
    private final long startedAt;
    private long countdownFrom = -1;
    private int revision;

    /**
     * @param members the party, the leader included (at least two: a player alone sets out at once)
     */
    public ReadyCheck(String contentId, Difficulty difficulty, UUID leader, List<UUID> members, long now) {
        this.contentId = Objects.requireNonNull(contentId, "contentId");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.leader = Objects.requireNonNull(leader, "leader");
        Set<UUID> distinct = new LinkedHashSet<>(members);
        if (distinct.size() != members.size()) {
            throw new IllegalArgumentException("duplicate members: " + members);
        }
        if (!distinct.contains(leader)) {
            throw new IllegalArgumentException("the leader is not a member");
        }
        if (distinct.size() < 2) {
            throw new IllegalArgumentException("a ready check needs a party of two or more");
        }
        this.members = List.copyOf(members);
        this.startedAt = now;
        ready.add(leader);
    }

    /**
     * A member's answer. Everyone being ready starts the countdown; going back to 準備中 stops it.
     *
     * @return whether anything changed (unknown players change nothing)
     */
    public boolean set(UUID member, boolean isReady, long now) {
        if (!members.contains(member)) {
            return false;
        }
        boolean changed = isReady ? ready.add(member) : ready.remove(member);
        if (changed) {
            revision++;
            countdownFrom = allReady() ? now : -1;
        }
        return changed;
    }

    public Phase phase(long now) {
        if (countdownFrom >= 0) {
            return now - countdownFrom >= COUNTDOWN_TICKS ? Phase.GO : Phase.COUNTDOWN;
        }
        return now - startedAt >= TIMEOUT_TICKS ? Phase.TIMED_OUT : Phase.WAITING;
    }

    /** Ticks left of the countdown (while it runs) or of the time to get ready. */
    public long ticksLeft(long now) {
        if (countdownFrom >= 0) {
            return Math.max(0, COUNTDOWN_TICKS - (now - countdownFrom));
        }
        return Math.max(0, TIMEOUT_TICKS - (now - startedAt));
    }

    /** Whether {@code party} is still exactly the people who were asked. */
    public boolean sameMembers(Collection<UUID> party) {
        return party.size() == members.size() && new HashSet<>(party).equals(new HashSet<>(members));
    }

    public boolean isReady(UUID member) {
        return ready.contains(member);
    }

    public boolean allReady() {
        return ready.size() == members.size();
    }

    public int readyCount() {
        return ready.size();
    }

    /** Members still 準備中, in party order. */
    public List<UUID> waitingFor() {
        return members.stream().filter(member -> !ready.contains(member)).toList();
    }

    public String contentId() {
        return contentId;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public UUID leader() {
        return leader;
    }

    public List<UUID> members() {
        return members;
    }

    /** Grows with every answer that changed something. */
    public int revision() {
        return revision;
    }
}
