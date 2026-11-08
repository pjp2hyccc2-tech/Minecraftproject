package com.eternalrealm.ranking;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Decides whether a boss clear may enter the time-attack ranking. Every check uses server-recorded data only.
 *
 * <p>Rejection reasons are translation keys (constants below), so they can be shown to the party and logged as-is.
 * All applicable reasons are reported, except that the duration checks are skipped when the duration itself is
 * invalid (they would only repeat it).</p>
 */
public final class TimeAttackValidator {
    private static final String PREFIX = "message." + ModInfo.MOD_ID + ".time_attack.rejected.";
    /** The boss has no time-attack board ({@link TimeAttackRules#bossIds()}). */
    public static final String UNKNOWN_BOSS = PREFIX + "unknown_boss";
    /** {@code endTick <= startTick}. */
    public static final String INVALID_DURATION = PREFIX + "invalid_duration";
    /** Faster than the boss's minimum plausible clear. */
    public static final String TOO_FAST = PREFIX + "too_fast";
    /** The wall clock ran clearly shorter than the ticks claim (manipulated tick count). */
    public static final String CLOCK_MISMATCH = PREFIX + "clock_mismatch";
    public static final String NO_MEMBERS = PREFIX + "no_members";
    /** More than {@link PartyBracket#MAX_MEMBERS} members. */
    public static final String TOO_MANY_MEMBERS = PREFIX + "too_many_members";
    public static final String DUPLICATE_MEMBER = PREFIX + "duplicate_member";
    public static final String CREATIVE_OR_SPECTATOR = PREFIX + "creative_or_spectator";
    public static final String ADMIN_COMMAND = PREFIX + "admin_command";
    public static final String MEMBERS_CHANGED = PREFIX + "members_changed";
    /** The season's rankings are frozen (reported by {@link RankingBoard#submitTimeAttack}). */
    public static final String SEASON_CLOSED = PREFIX + "season_closed";

    /** Every reason, for translation checks and admin tooling. */
    public static final List<String> ALL_REASONS = List.of(UNKNOWN_BOSS, INVALID_DURATION, TOO_FAST, CLOCK_MISMATCH,
            NO_MEMBERS, TOO_MANY_MEMBERS, DUPLICATE_MEMBER, CREATIVE_OR_SPECTATOR, ADMIN_COMMAND, MEMBERS_CHANGED,
            SEASON_CLOSED);

    private TimeAttackValidator() {
    }

    /**
     * Outcome of a validation.
     *
     * @param accepted {@code true} exactly when there are no reasons
     * @param reasons  translation keys of every failed check, in check order
     */
    public record Validation(boolean accepted, List<String> reasons) {
        public static final Validation ACCEPTED = new Validation(true, List.of());

        public Validation {
            reasons = List.copyOf(reasons);
            if (accepted != reasons.isEmpty()) {
                throw new IllegalArgumentException("A validation is accepted exactly when it has no reasons: "
                        + accepted + " " + reasons);
            }
        }

        public static Validation rejected(List<String> reasons) {
            return new Validation(false, reasons);
        }
    }

    public static Validation validate(TimeAttackRun run, TimeAttackRules rules) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(rules, "rules");
        List<String> reasons = new ArrayList<>();
        if (!rules.isKnownBoss(run.bossId())) {
            reasons.add(UNKNOWN_BOSS);
        }
        long duration = run.durationTicks();
        if (duration <= 0) {
            reasons.add(INVALID_DURATION);
        } else {
            if (duration < rules.minimumTicks(run.bossId())) {
                reasons.add(TOO_FAST);
            }
            if (!wallClockConsistent(duration, run.wallClockMillis(), rules.minWallClockPercent())) {
                reasons.add(CLOCK_MISMATCH);
            }
        }
        addRuleIndependentProblems(run, reasons, false);
        return reasons.isEmpty() ? Validation.ACCEPTED : Validation.rejected(reasons);
    }

    /**
     * The checks that do not depend on {@link TimeAttackRules}: what every stored run must satisfy. Used to reject
     * corrupt stored data (the rules may have changed since the run was accepted, so they are not re-applied).
     */
    static List<String> integrityProblems(TimeAttackRun run) {
        List<String> problems = new ArrayList<>();
        addRuleIndependentProblems(run, problems, true);
        return problems;
    }

    private static void addRuleIndependentProblems(TimeAttackRun run, List<String> reasons, boolean checkDuration) {
        if (checkDuration && run.durationTicks() <= 0) {
            reasons.add(INVALID_DURATION);
        }
        List<TimeAttackMember> members = run.members();
        if (members.isEmpty()) {
            reasons.add(NO_MEMBERS);
        } else if (members.size() > PartyBracket.MAX_MEMBERS) {
            reasons.add(TOO_MANY_MEMBERS);
        }
        Set<UUID> ids = new HashSet<>();
        for (TimeAttackMember member : members) {
            if (!ids.add(member.id())) {
                reasons.add(DUPLICATE_MEMBER);
                break;
            }
        }
        if (run.creativeOrSpectatorUsed()) {
            reasons.add(CREATIVE_OR_SPECTATOR);
        }
        if (run.adminCommandUsed()) {
            reasons.add(ADMIN_COMMAND);
        }
        if (run.membersChangedDuringRun()) {
            reasons.add(MEMBERS_CHANGED);
        }
    }

    /**
     * {@code wall >= ticks × 50 ms × percent / 100}, compared exactly: both sides are multiplied out in 128-bit
     * arithmetic, so neither rounding nor overflow can flip the result. Any longer wall time is fine (lag).
     */
    static boolean wallClockConsistent(long durationTicks, long wallClockMillis, int minPercent) {
        if (wallClockMillis < 0) {
            return false;
        }
        return compareProducts(wallClockMillis, 100, durationTicks, TimeAttackRun.MILLIS_PER_TICK * minPercent) >= 0;
    }

    /** Compares {@code a × b} with {@code c × d} for non-negative operands without overflow. */
    private static int compareProducts(long a, long b, long c, long d) {
        long high1 = Math.multiplyHigh(a, b);
        long high2 = Math.multiplyHigh(c, d);
        if (high1 != high2) {
            return Long.compare(high1, high2);
        }
        return Long.compareUnsigned(a * b, c * d);
    }
}
