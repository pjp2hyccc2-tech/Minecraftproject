package com.eternalrealm.revival;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RevivalRulesTest {
    @Test
    public void returnUnlocksAfterTwoSeconds() {
        assertFalse(RevivalRules.canReturn(0));
        assertFalse(RevivalRules.canReturn(39));
        assertTrue(RevivalRules.canReturn(40));
    }

    @Test
    public void countdownDependsOnThePlace() {
        assertEquals(10, RevivalRules.secondsLeft(RevivalRules.Place.CAPITAL, 0));
        assertEquals(60, RevivalRules.secondsLeft(RevivalRules.Place.FIELD, 0));
        assertEquals(30, RevivalRules.secondsLeft(RevivalRules.Place.DUNGEON, 0));
        assertEquals(1, RevivalRules.secondsLeft(RevivalRules.Place.DUNGEON, 30 * 20 - 1));
        assertEquals(0, RevivalRules.secondsLeft(RevivalRules.Place.DUNGEON, 30 * 20));
        assertFalse(RevivalRules.autoReturnDue(RevivalRules.Place.FIELD, 60 * 20 - 1));
        assertTrue(RevivalRules.autoReturnDue(RevivalRules.Place.FIELD, 60 * 20));
    }

    @Test
    public void onlyFieldsCostExperience() {
        assertTrue(RevivalRules.costsExperience(RevivalRules.Place.FIELD));
        assertFalse(RevivalRules.costsExperience(RevivalRules.Place.CAPITAL));
        assertFalse(RevivalRules.costsExperience(RevivalRules.Place.DUNGEON));
    }

    @Test
    public void healthAfterEachWay() {
        assertEquals(0.5, RevivalRules.healthAfter(RevivalRules.Way.LEAF_SELF, 0), 1e-9);
        assertEquals(0.5, RevivalRules.healthAfter(RevivalRules.Way.LEAF_ALLY, 0), 1e-9);
        assertEquals(1.0, RevivalRules.healthAfter(RevivalRules.Way.RETURN, 0), 1e-9);
        assertEquals(1.0, RevivalRules.healthAfter(RevivalRules.Way.AUTO_RETURN, 0), 1e-9);
        assertEquals(0.3, RevivalRules.healthAfter(RevivalRules.Way.RESURRECTION, 0.3), 1e-9);
        assertEquals(0.1, RevivalRules.healthAfter(RevivalRules.Way.RESURRECTION, 0.0), 1e-9);
        assertTrue(RevivalRules.Way.LEAF_ALLY.inPlace());
        assertFalse(RevivalRules.Way.AUTO_RETURN.inPlace());
    }

    @Test
    public void placeIdsRoundTrip() {
        for (RevivalRules.Place place : RevivalRules.Place.values()) {
            assertEquals(place, RevivalRules.Place.byId(place.id()));
        }
        assertEquals(RevivalRules.Place.FIELD, RevivalRules.Place.byId("nonsense"));
    }

    @Test
    public void tipsPointAtWhatCanBeImproved() {
        List<RevivalTips.Tip> tips = RevivalTips.choose(new RevivalTips.Situation(8, 15, 3, 2, 5, 0, 1,
                RevivalRules.Place.FIELD, false));
        assertEquals(RevivalTips.MAX, tips.size());
        assertEquals(RevivalTips.Kind.LEVEL, tips.get(0).kind());
        assertEquals(List.of("15", "8"), tips.get(0).args());
        assertEquals(RevivalTips.Kind.STAT_POINTS, tips.get(1).kind());
        assertEquals(RevivalTips.Kind.SKILL_POINTS, tips.get(2).kind());
    }

    @Test
    public void wellPreparedPlayersGetCombatHints() {
        List<RevivalTips.Tip> field = RevivalTips.choose(new RevivalTips.Situation(20, 15, 0, 0, 5, 0, 3,
                RevivalRules.Place.FIELD, false));
        assertEquals(List.of(RevivalTips.Kind.DODGE), field.stream().map(RevivalTips.Tip::kind).toList());
        List<RevivalTips.Tip> boss = RevivalTips.choose(new RevivalTips.Situation(20, 15, 0, 0, 0, 3, 1,
                RevivalRules.Place.DUNGEON, true));
        assertEquals(List.of(RevivalTips.Kind.POTIONS, RevivalTips.Kind.ENHANCE, RevivalTips.Kind.PARTY),
                boss.stream().map(RevivalTips.Tip::kind).toList());
        List<RevivalTips.Tip> soloBoss = RevivalTips.choose(new RevivalTips.Situation(20, 15, 0, 0, 4, 0, 4,
                RevivalRules.Place.DUNGEON, true));
        assertEquals(List.of(RevivalTips.Kind.BOSS_PATTERNS, RevivalTips.Kind.DODGE),
                soloBoss.stream().map(RevivalTips.Tip::kind).toList());
    }
}
