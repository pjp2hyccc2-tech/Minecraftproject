package com.eternalrealm.character;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** A server config file of an older balance revision moves to the new defaults, but keeps values chosen by hand. */
public class BalanceRevisionsTest {
    @Test
    public void skillPointsStillAtTheOldDefaultRiseToThree() {
        assertEquals(1, BalanceRevisions.CURRENT);
        assertEquals(3, CharacterRules.DEFAULT_SKILL_POINTS_PER_LEVEL);
        assertEquals(3, CharacterRules.DEFAULT_SKILL_POINT_MILESTONE_BONUS);
        assertEquals(3, BalanceRevisions.skillPointsPerLevel(0, 2));
        assertEquals(3, BalanceRevisions.skillPointMilestoneBonus(0, 2));
        // chosen by hand: kept
        assertEquals(5, BalanceRevisions.skillPointsPerLevel(0, 5));
        assertEquals(1, BalanceRevisions.skillPointMilestoneBonus(0, 1));
        // a file of this revision: nothing moves (a 2 there was chosen after the change)
        assertEquals(2, BalanceRevisions.skillPointsPerLevel(1, 2));
        assertEquals(2, BalanceRevisions.skillPointMilestoneBonus(1, 2));
        // a fresh file already has the new default
        assertEquals(3, BalanceRevisions.skillPointsPerLevel(0, 3));
    }

    @Test
    public void theNewRatesEarnAboutHalfAgainAsManyPoints() {
        CharacterRules rules = CharacterRules.defaults();
        assertEquals(30, rules.earnedSkillPoints(10));
        assertEquals(129, rules.earnedSkillPoints(40));
        assertEquals(327, rules.earnedSkillPoints(100));
    }
}
