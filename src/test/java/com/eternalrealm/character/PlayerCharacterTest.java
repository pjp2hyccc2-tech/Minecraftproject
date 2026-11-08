package com.eternalrealm.character;

import com.eternalrealm.season.SeasonId;
import org.junit.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PlayerCharacterTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final SeasonId SEASON = SeasonId.of("s1");
    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final CharacterRules RULES = CharacterRules.defaults(); // cap 50, 3 pts/level, base 5, max 10/request

    private static PlayerCharacter fresh() {
        return PlayerCharacter.createNew(PLAYER, SEASON, NOW);
    }

    @Test
    public void newCharacterStartsAtLevelOneAndIsDirty() {
        PlayerCharacter character = fresh();
        assertEquals(1, character.level());
        assertEquals(0, character.experience());
        assertEquals(0, character.unspentStatPoints(RULES));
        assertTrue("a new character must be persisted", character.isDirty());
    }

    @Test
    public void experienceWithinALevel() {
        PlayerCharacter character = fresh();
        ExperienceResult result = character.addExperience(40, RULES);
        assertEquals(40, result.applied());
        assertFalse(result.leveledUp());
        assertEquals(1, character.level());
        assertEquals(40, character.experience());
        assertEquals(40, character.totalExperience());
    }

    @Test
    public void experienceCanGrantSeveralLevelsAtOnce() {
        PlayerCharacter character = fresh();
        // level 1 -> 2 needs 100, level 2 -> 3 needs 283
        ExperienceResult result = character.addExperience(100 + 283 + 5, RULES);
        assertEquals(1, result.previousLevel());
        assertEquals(3, result.newLevel());
        assertEquals(2, result.levelsGained());
        assertEquals(6, result.statPointsGained());
        assertEquals(3, character.level());
        assertEquals(5, character.experience());
        assertEquals(6, character.unspentStatPoints(RULES));
    }

    @Test
    public void experienceStopsAtTheCapAndExcessIsDiscarded() {
        CharacterRules lowCap = new CharacterRules(3, 3, 5, 10, new PowerExperienceCurve(100, 1.5));
        PlayerCharacter character = fresh();
        ExperienceResult result = character.addExperience(1_000_000, lowCap);
        assertEquals(3, character.level());
        assertEquals(0, character.experience());
        assertTrue(result.atCap());
        assertEquals(383, result.applied());
        assertEquals(1_000_000 - 383, result.discarded());

        long revision = character.revision();
        ExperienceResult capped = character.addExperience(50, lowCap);
        assertEquals(0, capped.applied());
        assertEquals(50, capped.discarded());
        assertEquals("nothing changed, so the character must not become dirtier", revision, character.revision());
    }

    @Test
    public void hugeGrantsAreClampedInsteadOfOverflowing() {
        PlayerCharacter character = fresh();
        ExperienceResult result = character.addExperience(Long.MAX_VALUE, RULES);
        assertEquals(50, character.level());
        assertTrue(result.applied() <= PlayerCharacter.MAX_SINGLE_GRANT);
        assertEquals(Long.MAX_VALUE - result.applied(), result.discarded());
    }

    @Test
    public void nonPositiveGrantsAreRejected() {
        PlayerCharacter character = fresh();
        assertThrows(IllegalArgumentException.class, () -> character.addExperience(0, RULES));
        assertThrows(IllegalArgumentException.class, () -> character.addExperience(-5, RULES));
    }

    @Test
    public void allocationIsValidatedServerSide() {
        PlayerCharacter character = fresh();
        assertEquals(AllocationResult.NOT_ENOUGH_POINTS, character.allocate(StatType.STR, 1, RULES));

        character.addExperience(100, RULES); // level 2 -> 3 points
        assertEquals(AllocationResult.INVALID_AMOUNT, character.allocate(StatType.STR, 0, RULES));
        assertEquals(AllocationResult.INVALID_AMOUNT, character.allocate(StatType.STR, -1, RULES));
        assertEquals(AllocationResult.INVALID_AMOUNT, character.allocate(StatType.STR, 11, RULES));
        assertEquals(AllocationResult.NOT_ENOUGH_POINTS, character.allocate(StatType.STR, 4, RULES));

        assertEquals(AllocationResult.SUCCESS, character.allocate(StatType.STR, 2, RULES));
        assertEquals(AllocationResult.SUCCESS, character.allocate(StatType.LUK, 1, RULES));
        assertEquals(0, character.unspentStatPoints(RULES));
        assertEquals(AllocationResult.NOT_ENOUGH_POINTS, character.allocate(StatType.VIT, 1, RULES));

        StatBlock total = character.totalStats(RULES);
        assertEquals(7, total.get(StatType.STR));
        assertEquals(6, total.get(StatType.LUK));
        assertEquals(5, total.get(StatType.VIT));
    }

    @Test
    public void resetRefundsAllPoints() {
        PlayerCharacter character = fresh();
        character.addExperience(100, RULES);
        character.allocate(StatType.INT, 3, RULES);
        assertTrue(character.resetAllocation());
        assertEquals(3, character.unspentStatPoints(RULES));
        assertFalse("nothing left to reset", character.resetAllocation());
    }

    @Test
    public void setLevelRefundsAllocationsThatNoLongerFit() {
        PlayerCharacter character = fresh();
        assertEquals(SetLevelResult.SUCCESS, character.setLevel(10, RULES));
        character.allocate(StatType.DEX, 10, RULES);
        character.allocate(StatType.DEX, 10, RULES);
        assertEquals(7, character.unspentStatPoints(RULES));

        assertEquals(SetLevelResult.SUCCESS, character.setLevel(5, RULES)); // 12 points, 20 allocated -> refund
        assertEquals(0, character.allocatedStats().total());
        assertEquals(12, character.unspentStatPoints(RULES));

        assertEquals(SetLevelResult.OUT_OF_RANGE, character.setLevel(0, RULES));
        assertEquals(SetLevelResult.OUT_OF_RANGE, character.setLevel(51, RULES));
    }

    @Test
    public void reconcileRepairsDataThatViolatesCurrentRules() {
        StatBlock tooMany = StatBlock.ZERO.with(StatType.STR, 100);
        CharacterRecord record = new CharacterRecord(PLAYER, SEASON, 5, 999_999, 1_000, tooMany, NOW, NOW);
        PlayerCharacter character = PlayerCharacter.fromRecord(record, RULES);
        assertEquals(5, character.level());
        assertEquals(RULES.experienceToNext(5) - 1, character.experience());
        assertEquals(StatBlock.ZERO, character.allocatedStats());
        assertTrue("adjusted data must be saved again", character.isDirty());
    }

    @Test
    public void reconcileZeroesExperienceWhenCapWasLowered() {
        CharacterRecord record = new CharacterRecord(PLAYER, SEASON, 40, 10, 1_000, StatBlock.ZERO, NOW, NOW);
        CharacterRules cap30 = new CharacterRules(30, 3, 5, 10, new PowerExperienceCurve(100, 1.5));
        PlayerCharacter character = PlayerCharacter.fromRecord(record, cap30);
        assertEquals("level above a lowered cap is kept (never take progress away)", 40, character.level());
        assertEquals(0, character.experience());
        assertTrue(character.snapshot(cap30).atCap());
    }

    @Test
    public void validRecordLoadsClean() {
        CharacterRecord record = new CharacterRecord(PLAYER, SEASON, 3, 10, 500, StatBlock.ZERO.with(StatType.VIT, 6), NOW, NOW);
        PlayerCharacter character = PlayerCharacter.fromRecord(record, RULES);
        assertFalse(character.isDirty());
        assertEquals(record.allocatedStats(), character.toRecord(NOW).allocatedStats());
        assertEquals(record, character.toRecord(NOW));
    }

    @Test
    public void dirtyTrackingFollowsRevisions() {
        PlayerCharacter character = fresh();
        long revision = character.revision();
        character.markSaved(revision);
        assertFalse(character.isDirty());

        character.addExperience(1, RULES);
        assertTrue(character.isDirty());
        character.markSaved(revision); // an older save completing late must not mark newer changes as saved
        assertTrue(character.isDirty());
        character.markSaved(character.revision());
        assertFalse(character.isDirty());
    }

    @Test
    public void snapshotReflectsServerState() {
        PlayerCharacter character = fresh();
        character.addExperience(150, RULES); // level 2, 50/283
        character.allocate(StatType.AGI, 2, RULES);
        CharacterSnapshot snapshot = character.snapshot(RULES);
        assertEquals(2, snapshot.level());
        assertEquals(50, snapshot.levelCap());
        assertEquals(50, snapshot.experience());
        assertEquals(283, snapshot.experienceToNext());
        assertEquals(1, snapshot.unspentStatPoints());
        assertEquals(2, snapshot.allocatedStats().get(StatType.AGI));
        assertEquals(7, snapshot.totalStats().get(StatType.AGI));
        assertEquals(50.0 / 283.0, snapshot.progress(), 1e-9);
    }

    // ------------------------------------------------------------------ Phase 3: job, skills, gold, extensions

    @Test
    public void skillPointsAreEarnedEveryThirdLevelAndInvestedOneByOne() {
        PlayerCharacter character = fresh();
        assertEquals(0, character.unspentSkillPoints(RULES));
        assertFalse(character.investSkillPoint("mage_fireball", RULES));
        character.setLevel(7, RULES); // (7 - 1) / 3 = 2 points
        assertEquals(2, character.unspentSkillPoints(RULES));
        assertTrue(character.investSkillPoint("mage_fireball", RULES));
        assertTrue(character.investSkillPoint("mage_fireball", RULES));
        assertFalse(character.investSkillPoint("mage_ice_lance", RULES));
        assertEquals(2, character.investment("mage_fireball"));
        assertEquals(0, character.unspentSkillPoints(RULES));
        assertTrue(character.resetSkills());
        assertEquals(2, character.unspentSkillPoints(RULES));
        assertFalse(character.resetSkills());
    }

    @Test
    public void changingJobRefundsSkills() {
        PlayerCharacter character = fresh();
        character.setLevel(10, RULES);
        assertTrue(character.changeJob("knight"));
        character.investSkillPoint("knight_shield_bash", RULES);
        assertFalse(character.changeJob("knight"));
        assertEquals(1, character.investedSkillPoints());
        assertTrue(character.changeJob("mage"));
        assertEquals(0, character.investedSkillPoints());
        assertEquals("mage", character.jobId());
        assertThrows(IllegalArgumentException.class, () -> character.changeJob("Mage!"));
    }

    @Test
    public void loweringTheLevelRefundsSkillsThatNoLongerFit() {
        PlayerCharacter character = fresh();
        character.setLevel(10, RULES);
        character.investSkillPoint("a", RULES);
        character.investSkillPoint("b", RULES);
        character.setLevel(4, RULES); // 1 point only
        assertEquals(0, character.investedSkillPoints());
    }

    @Test
    public void goldNeverGoesNegativeOrOverflows() {
        PlayerCharacter character = fresh();
        assertEquals(500, character.addGold(500));
        assertFalse(character.spendGold(501));
        assertEquals(500, character.gold());
        assertTrue(character.spendGold(200));
        assertEquals(300, character.gold());
        assertEquals(CharacterRecord.MAX_GOLD - 300, character.addGold(Long.MAX_VALUE));
        assertEquals(CharacterRecord.MAX_GOLD, character.gold());
        assertEquals(0, character.addGold(10));
        assertThrows(IllegalArgumentException.class, () -> character.addGold(-1));
        assertThrows(IllegalArgumentException.class, () -> character.spendGold(-1));
    }

    @Test
    public void deathPenaltyNeverCostsALevel() {
        PlayerCharacter character = fresh();
        character.addExperience(60, RULES); // level 1: 60 / 100
        assertEquals(5, character.applyDeathPenalty(0.05, RULES));
        assertEquals(55, character.experience());
        assertEquals(55, character.applyDeathPenalty(1.0, RULES));
        assertEquals(0, character.experience());
        assertEquals(1, character.level());
        assertEquals(0, character.applyDeathPenalty(0.5, RULES));
    }

    @Test
    public void extensionsOnlyDirtyTheCharacterWhenTheyChange() {
        PlayerCharacter character = fresh();
        character.setExtension("quests", "{\"a\":1}");
        long revision = character.revision();
        character.setExtension("quests", "{\"a\":1}");
        assertEquals(revision, character.revision());
        character.setExtension("quests", "{\"a\":2}");
        assertTrue(character.revision() > revision);
        assertEquals("{\"a\":2}", character.extension("quests").orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> character.setExtension("Bad Key", "{}"));
    }

    @Test
    public void phaseThreeDataSurvivesTheRecordRoundTrip() {
        PlayerCharacter character = fresh();
        character.setLevel(20, RULES);
        character.changeJob("priest");
        character.investSkillPoint("priest_heal", RULES);
        character.addGold(777);
        character.setExtension("stats", "{\"kills\":3}");
        PlayerCharacter copy = PlayerCharacter.fromRecord(character.toRecord(NOW), RULES);
        assertEquals("priest", copy.jobId());
        assertEquals(1, copy.investment("priest_heal"));
        assertEquals(777, copy.gold());
        assertEquals("{\"kills\":3}", copy.extension("stats").orElseThrow());
        assertEquals(character.snapshot(RULES), copy.snapshot(RULES));
    }
}
