package com.eternalrealm.ranking;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One boss clear, recorded entirely by the server: the fight start (arena sealed / first hit) and the boss death, both
 * as server ticks and as server wall-clock time, plus the party that started the fight.
 *
 * <p>The constructor only checks the structure. Whether the run counts is decided by
 * {@link TimeAttackValidator#validate(TimeAttackRun, TimeAttackRules)}, so a suspicious run can be represented,
 * rejected with reasons and logged instead of throwing.</p>
 *
 * <p>The ranked duration is {@link #durationTicks()}: server ticks measure game time, so server lag (fewer ticks per
 * second) does not slow a party down. The wall clock is only used to detect manipulated tick counts, and
 * {@link #clearedAtMillis()} orders equal times and first clears (ticks restart at 0 with the server).</p>
 *
 * @param bossId                  boss id, {@code b1} to {@code b8} or {@code final} (see {@link TimeAttackRules})
 * @param runId                   unique id of this run
 * @param startTick               server tick at the fight start
 * @param endTick                 server tick at the boss death
 * @param startedAtMillis         server wall clock at the fight start (epoch millis)
 * @param clearedAtMillis         server wall clock at the boss death (epoch millis)
 * @param members                 the party at the fight start
 * @param creativeOrSpectatorUsed a member was in creative or spectator mode during the run
 * @param adminCommandUsed        an operator command affected the run (teleport, heal, kill, ...)
 * @param membersChangedDuringRun somebody joined or left the party during the run
 */
public record TimeAttackRun(String bossId, BossDifficulty difficulty, UUID runId, long startTick, long endTick,
                            long startedAtMillis, long clearedAtMillis, List<TimeAttackMember> members,
                            boolean creativeOrSpectatorUsed, boolean adminCommandUsed,
                            boolean membersChangedDuringRun) {
    /** Duration of a server tick at the nominal 20 ticks per second. */
    public static final long MILLIS_PER_TICK = 50;

    public TimeAttackRun {
        Check.id("bossId", bossId);
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(runId, "runId");
        Check.nonNegative("startTick", startTick);
        Check.nonNegative("endTick", endTick);
        Check.nonNegative("startedAtMillis", startedAtMillis);
        Check.nonNegative("clearedAtMillis", clearedAtMillis);
        members = List.copyOf(members);
    }

    /** Ranked duration; may be zero or negative for an invalid run (both ticks are non-negative, so no overflow). */
    public long durationTicks() {
        return endTick - startTick;
    }

    public long wallClockMillis() {
        return clearedAtMillis - startedAtMillis;
    }

    /** @throws IllegalArgumentException if the member count fits no bracket (an invalid run) */
    public PartyBracket bracket() {
        return PartyBracket.of(members.size());
    }

    /** The distinct member ids (identifies "the same party" across runs). */
    public Set<UUID> memberIds() {
        Set<UUID> ids = new HashSet<>();
        for (TimeAttackMember member : members) {
            ids.add(member.id());
        }
        return Set.copyOf(ids);
    }

    public boolean hasMember(UUID playerId) {
        for (TimeAttackMember member : members) {
            if (member.id().equals(playerId)) {
                return true;
            }
        }
        return false;
    }
}
