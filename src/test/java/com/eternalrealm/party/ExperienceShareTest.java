package com.eternalrealm.party;

import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ExperienceShareTest {
    private static final UUID A = new UUID(7L, 1L);
    private static final UUID B = new UUID(7L, 2L);
    private static final UUID C = new UUID(7L, 3L);
    private static final ShareRules RULES = ShareRules.defaults();

    private static List<UUID> members(int count) {
        List<UUID> members = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            members.add(new UUID(7L, i));
        }
        return members;
    }

    @Test
    public void aSoloKillerGetsEverythingWithoutBonus() {
        ExperienceSplit split = ExperienceShare.split(123, List.of(A), A, RULES);
        assertEquals(0, split.bonusPercent());
        assertEquals(123, split.totalExperience());
        assertEquals(Map.of(A, 123L), split.shares());
    }

    @Test
    public void eachExtraMemberAddsTenPercentSharedEvenly() {
        ExperienceSplit two = ExperienceShare.split(100, List.of(A, B), B, RULES);
        assertEquals(10, two.bonusPercent());
        assertEquals(110, two.totalExperience());
        assertEquals(55, two.shareOf(A));
        assertEquals(55, two.shareOf(B));

        ExperienceSplit three = ExperienceShare.split(100, List.of(A, B, C), A, RULES);
        assertEquals(20, three.bonusPercent());
        assertEquals(List.of(40L, 40L, 40L), new ArrayList<>(three.shares().values()));
        assertEquals("shares keep the order of the eligible members", List.of(A, B, C),
                new ArrayList<>(three.shares().keySet()));
        assertEquals(0, three.shareOf(new UUID(9L, 9L)));
    }

    @Test
    public void theRemainderGoesToTheKiller() {
        // 101 + floor(101 × 20%) = 121 = 3 × 40 + 1
        ExperienceSplit split = ExperienceShare.split(101, List.of(A, B, C), C, RULES);
        assertEquals(121, split.totalExperience());
        assertEquals(40, split.shareOf(A));
        assertEquals(40, split.shareOf(B));
        assertEquals(41, split.shareOf(C));
    }

    @Test
    public void theBonusIsCappedAtFortyPercent() {
        assertEquals(40, RULES.bonusPercent(5));
        assertEquals(40, RULES.bonusPercent(20));
        ExperienceSplit raid = ExperienceShare.split(1000, members(20), members(20).get(0), RULES);
        assertEquals(40, raid.bonusPercent());
        assertEquals(1400, raid.totalExperience());
        assertEquals(70, raid.shareOf(members(20).get(19)));
    }

    @Test
    public void zeroExperienceGivesZeroShares() {
        ExperienceSplit split = ExperienceShare.split(0, List.of(A, B), A, RULES);
        assertEquals(0, split.totalExperience());
        assertEquals(0, split.shareOf(A) + split.shareOf(B));
    }

    @Test
    public void hugeAmountsSaturateInsteadOfOverflowing() {
        ExperienceSplit split = ExperienceShare.split(Long.MAX_VALUE, List.of(A, B), A, RULES);
        assertEquals(Long.MAX_VALUE, split.totalExperience());
        assertEquals(Long.MAX_VALUE, split.shareOf(A) + split.shareOf(B));
    }

    @Test
    public void sharesAlwaysAddUpToTheExactTotal() {
        Random random = new Random(42);
        ShareRules odd = new ShareRules(7, 33);
        for (int i = 0; i < 2_000; i++) {
            long experience = random.nextInt(4) == 0 ? random.nextLong() >>> 1 : random.nextInt(100_000);
            List<UUID> members = members(1 + random.nextInt(20));
            UUID killer = members.get(random.nextInt(members.size()));
            ExperienceSplit split = ExperienceShare.split(experience, members, killer, odd);
            BigInteger exact = BigInteger.valueOf(experience)
                    .add(BigInteger.valueOf(experience).multiply(BigInteger.valueOf(split.bonusPercent()))
                            .divide(BigInteger.valueOf(100)))
                    .min(BigInteger.valueOf(Long.MAX_VALUE));
            assertEquals(exact.longValueExact(), split.totalExperience());
            long sum = 0;
            for (UUID member : members) {
                long share = split.shareOf(member);
                long expected = split.totalExperience() / members.size();
                assertEquals(member.equals(killer) ? expected + split.totalExperience() % members.size() : expected,
                        share);
                sum += share;
            }
            assertEquals(split.totalExperience(), sum);
        }
    }

    @Test
    public void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ExperienceShare.split(-1, List.of(A), A, RULES));
        assertThrows(IllegalArgumentException.class, () -> ExperienceShare.split(10, List.of(A, B), C, RULES));
        assertThrows(IllegalArgumentException.class, () -> ExperienceShare.split(10, List.of(), A, RULES));
        assertThrows(IllegalArgumentException.class, () -> ExperienceShare.split(10, List.of(A, B, A), A, RULES));
        assertThrows(NullPointerException.class, () -> ExperienceShare.split(10, List.of(A), null, RULES));
        assertThrows(IllegalArgumentException.class, () -> new ShareRules(-1, 40));
        assertThrows(IllegalArgumentException.class, () -> new ShareRules(10, 101));
        assertThrows(IllegalArgumentException.class, () -> RULES.bonusPercent(0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExperienceSplit(10, 0, 10, Map.of(A, 6L, B, 5L)));
        assertThrows(IllegalArgumentException.class,
                () -> new ExperienceSplit(10, 0, 10, Map.of(A, Long.MAX_VALUE, B, Long.MAX_VALUE)));
    }

    @Test
    public void customRulesAndPercentArithmetic() {
        ShareRules none = new ShareRules(0, 0);
        assertEquals(100, ExperienceShare.split(100, List.of(A, B), A, none).totalExperience());
        assertEquals(0, ExperienceShare.percentOf(99, 1));
        assertEquals(1, ExperienceShare.percentOf(100, 1));
        assertEquals(Long.MAX_VALUE, ExperienceShare.percentOf(Long.MAX_VALUE, 100));
        assertEquals(922337203685477580L, ExperienceShare.percentOf(Long.MAX_VALUE, 10));
    }
}
