package com.eternalrealm.ranking;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.eternalrealm.ranking.RankingFixtures.member;
import static com.eternalrealm.ranking.RankingFixtures.uuid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TimeAttackValidatorTest {
    private static final TimeAttackRules RULES = TimeAttackRules.defaults();

    /** A run of the given tick duration and wall-clock duration, with the given members. */
    private static TimeAttackRun run(String bossId, long ticks, long wallMillis, List<TimeAttackMember> members,
                                     boolean creative, boolean admin, boolean changed) {
        long startedAt = RankingFixtures.T0;
        return new TimeAttackRun(bossId, BossDifficulty.NORMAL, uuid(77), 5_000, 5_000 + ticks, startedAt,
                startedAt + wallMillis, members, creative, admin, changed);
    }

    private static TimeAttackRun honest(String bossId, long ticks) {
        return run(bossId, ticks, ticks * 50, List.of(member(1), member(2)), false, false, false);
    }

    private static List<String> reasons(TimeAttackRun run) {
        return TimeAttackValidator.validate(run, RULES).reasons();
    }

    @Test
    public void acceptsAPlausibleRun() {
        TimeAttackValidator.Validation validation = TimeAttackValidator.validate(honest("b3", 2_400), RULES);
        assertTrue(validation.accepted());
        assertEquals(List.of(), validation.reasons());
        assertTrue(TimeAttackValidator.validate(honest(TimeAttackRules.FINAL_BOSS_ID, 12_000), RULES).accepted());
    }

    @Test
    public void durationMustBePositiveAndIsThenNotCheckedFurther() {
        assertEquals(List.of(TimeAttackValidator.INVALID_DURATION), reasons(honest("b1", 0)));
        assertEquals(List.of(TimeAttackValidator.INVALID_DURATION),
                reasons(run("b1", -100, 1_000_000, List.of(member(1)), false, false, false)));
    }

    @Test
    public void runsFasterThanTheBossMinimumAreRejected() {
        assertEquals(List.of(TimeAttackValidator.TOO_FAST), reasons(honest("b1", TimeAttackRules.DEFAULT_MINIMUM_TICKS - 1)));
        assertTrue(reasons(honest("b1", TimeAttackRules.DEFAULT_MINIMUM_TICKS)).isEmpty());

        TimeAttackRules rules = new TimeAttackRules(TimeAttackRules.DEFAULT_BOSS_IDS, 200, Map.of("b1", 1_200L), 90);
        assertEquals(List.of(TimeAttackValidator.TOO_FAST),
                TimeAttackValidator.validate(honest("b1", 1_199), rules).reasons());
        assertTrue(TimeAttackValidator.validate(honest("b1", 1_200), rules).accepted());
        assertTrue("other bosses keep the default", TimeAttackValidator.validate(honest("b2", 200), rules).accepted());
    }

    @Test
    public void wallClockMayBeLongerButNotMuchShorterThanTheTicks() {
        List<TimeAttackMember> solo = List.of(member(1));
        // 1000 ticks are nominally 50 s; 90 % of that is 45 s
        assertTrue(reasons(run("b2", 1_000, 45_000, solo, false, false, false)).isEmpty());
        assertEquals(List.of(TimeAttackValidator.CLOCK_MISMATCH), reasons(run("b2", 1_000, 44_999, solo, false, false, false)));
        assertTrue("lag only makes wall time longer", reasons(run("b2", 1_000, 500_000, solo, false, false, false)).isEmpty());
        assertEquals(List.of(TimeAttackValidator.CLOCK_MISMATCH), reasons(run("b2", 1_000, -1, solo, false, false, false)));
        assertEquals(List.of(TimeAttackValidator.CLOCK_MISMATCH), reasons(run("b2", 1_000, 0, solo, false, false, false)));
    }

    @Test
    public void wallClockComparisonIsExactForHugeValues() {
        long ticks = Long.MAX_VALUE / 100; // ticks × 50 × 90 overflows a long
        assertTrue(TimeAttackValidator.wallClockConsistent(ticks, Long.MAX_VALUE, 90));
        assertFalse(TimeAttackValidator.wallClockConsistent(ticks, Long.MAX_VALUE / 100, 90));
        assertTrue(TimeAttackValidator.wallClockConsistent(1, 50, 100));
        assertFalse(TimeAttackValidator.wallClockConsistent(1, 49, 100));
        assertTrue(TimeAttackValidator.wallClockConsistent(3, 135, 90));
        assertFalse(TimeAttackValidator.wallClockConsistent(3, 134, 90));
    }

    @Test
    public void membersMustBePresentAtMostTwentyAndDistinct() {
        assertEquals(List.of(TimeAttackValidator.NO_MEMBERS), reasons(run("b1", 400, 20_000, List.of(), false, false, false)));
        List<TimeAttackMember> twenty = new ArrayList<>();
        for (int i = 1; i <= PartyBracket.MAX_MEMBERS; i++) {
            twenty.add(member(i));
        }
        assertTrue(reasons(run("b1", 400, 20_000, twenty, false, false, false)).isEmpty());
        List<TimeAttackMember> twentyOne = new ArrayList<>(twenty);
        twentyOne.add(member(21));
        assertEquals(List.of(TimeAttackValidator.TOO_MANY_MEMBERS),
                reasons(run("b1", 400, 20_000, twentyOne, false, false, false)));
        assertEquals(List.of(TimeAttackValidator.DUPLICATE_MEMBER),
                reasons(run("b1", 400, 20_000, List.of(member(1), member(2), member(1)), false, false, false)));
        TimeAttackMember renamed = new TimeAttackMember(uuid(2), "Other name", "mage", 12);
        assertEquals("identity is the id, not the name", List.of(TimeAttackValidator.DUPLICATE_MEMBER),
                reasons(run("b1", 400, 20_000, List.of(member(2), renamed), false, false, false)));
    }

    @Test
    public void everyCheatFlagRejectsTheRun() {
        List<TimeAttackMember> solo = List.of(member(1));
        assertEquals(List.of(TimeAttackValidator.CREATIVE_OR_SPECTATOR), reasons(run("b1", 400, 20_000, solo, true, false, false)));
        assertEquals(List.of(TimeAttackValidator.ADMIN_COMMAND), reasons(run("b1", 400, 20_000, solo, false, true, false)));
        assertEquals(List.of(TimeAttackValidator.MEMBERS_CHANGED), reasons(run("b1", 400, 20_000, solo, false, false, true)));
    }

    @Test
    public void unknownBossesHaveNoBoard() {
        assertEquals(List.of(TimeAttackValidator.UNKNOWN_BOSS), reasons(honest("b9", 400)));
    }

    @Test
    public void reportsEveryApplicableReasonInCheckOrder() {
        TimeAttackRun bad = run("dragon", 10, 1, List.of(), true, true, true);
        assertEquals(List.of(TimeAttackValidator.UNKNOWN_BOSS, TimeAttackValidator.TOO_FAST,
                TimeAttackValidator.CLOCK_MISMATCH, TimeAttackValidator.NO_MEMBERS,
                TimeAttackValidator.CREATIVE_OR_SPECTATOR, TimeAttackValidator.ADMIN_COMMAND,
                TimeAttackValidator.MEMBERS_CHANGED), reasons(bad));
        assertFalse(TimeAttackValidator.validate(bad, RULES).accepted());
    }

    @Test
    public void validationIsAcceptedExactlyWithoutReasons() {
        assertThrows(IllegalArgumentException.class,
                () -> new TimeAttackValidator.Validation(true, List.of(TimeAttackValidator.TOO_FAST)));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackValidator.Validation(false, List.of()));
        for (String reason : TimeAttackValidator.ALL_REASONS) {
            assertTrue(reason, reason.matches("message\\.eternalrealm\\.time_attack\\.rejected\\.[a-z_]+"));
        }
        assertEquals(TimeAttackValidator.ALL_REASONS.size(), new HashSet<>(TimeAttackValidator.ALL_REASONS).size());
    }

    @Test
    public void runRecordsRejectStructuralGarbage() {
        List<TimeAttackMember> solo = List.of(member(1));
        assertThrows(IllegalArgumentException.class, () -> run("B1", 400, 20_000, solo, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> run("", 400, 20_000, solo, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackRun("b1", BossDifficulty.EX, uuid(1), -1, 5,
                0, 0, solo, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackRun("b1", BossDifficulty.EX, uuid(1), 0, 5,
                -5, 0, solo, false, false, false));
        assertThrows(NullPointerException.class, () -> new TimeAttackRun("b1", null, uuid(1), 0, 5, 0, 0, solo,
                false, false, false));
        assertThrows(NullPointerException.class, () -> run("b1", 400, 20_000, Arrays.asList(member(1), null), false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackMember(uuid(1), "", "knight", 1));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackMember(uuid(1), "A", "Knight", 1));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackMember(uuid(1), "A", "knight", 0));
        assertEquals("none", TimeAttackMember.NO_JOB);
        new TimeAttackMember(uuid(1), "A", TimeAttackMember.NO_JOB, 1);
    }

    @Test
    public void rulesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackRules(List.of(), 200, Map.of(), 90));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackRules(List.of("b1", "b1"), 200, Map.of(), 90));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackRules(List.of("b1"), 0, Map.of(), 90));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackRules(List.of("b1"), 200, Map.of("b2", 10L), 90));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackRules(List.of("b1"), 200, Map.of("b1", 0L), 90));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackRules(List.of("b1"), 200, Map.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new TimeAttackRules(List.of("b1"), 200, Map.of(), 101));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeAttackRules(List.of("b1"), TimeAttackRules.MAX_MINIMUM_TICKS + 1, Map.of(), 90));
        TimeAttackRules rules = TimeAttackRules.defaults();
        assertEquals(9, rules.bossIds().size());
        assertTrue(rules.isKnownBoss("final"));
        assertEquals(200, rules.minimumTicks("b4"));
    }

    @Test
    public void partyBracketsCoverOneToTwentyMembers() {
        assertEquals(PartyBracket.SOLO, PartyBracket.of(1));
        assertEquals(PartyBracket.PARTY, PartyBracket.of(2));
        assertEquals(PartyBracket.PARTY, PartyBracket.of(5));
        assertEquals(PartyBracket.RAID, PartyBracket.of(6));
        assertEquals(PartyBracket.RAID, PartyBracket.of(20));
        assertThrows(IllegalArgumentException.class, () -> PartyBracket.of(0));
        assertThrows(IllegalArgumentException.class, () -> PartyBracket.of(21));
        for (PartyBracket bracket : PartyBracket.all()) {
            assertEquals(bracket, PartyBracket.byId(bracket.id()).orElseThrow());
        }
        for (BossDifficulty difficulty : BossDifficulty.all()) {
            assertEquals(difficulty, BossDifficulty.byId(difficulty.id()).orElseThrow());
        }
        assertEquals(List.of("normal", "hard", "ex"), BossDifficulty.all().stream().map(BossDifficulty::id).toList());
    }
}
