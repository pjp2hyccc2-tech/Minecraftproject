package com.eternalrealm.testkit.bots;

import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillTree;
import com.eternalrealm.world.LevelRange;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.Habitats;
import org.junit.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotPlansTest {
    @Test
    public void thirtyBotsHoldFiveOfEveryJob() {
        Map<JobType, Integer> count = new EnumMap<>(JobType.class);
        for (int number = 1; number <= 30; number++) {
            count.merge(BotPlans.jobOf(number), 1, Integer::sum);
        }
        assertEquals(6, count.size());
        for (JobType job : BotPlans.JOBS) {
            assertTrue(job.isChosen());
            assertEquals(job + " five times", 5, (int) count.get(job));
        }
        assertEquals(JobType.KNIGHT, BotPlans.jobOf(1));
        assertEquals(JobType.KNIGHT, BotPlans.jobOf(7));
    }

    @Test
    public void statPointsFollowTheJobsGrowthWithAPointMoreForVit() {
        StatBlock growth = StatBlock.ZERO.with(StatType.STR, 3);
        Map<StatType, Integer> plan = BotPlans.allocation(growth, StatBlock.ZERO, 10);
        assertEquals("STR weighs 3, VIT 0 + 1 for survival: 3 to 1", Map.of(StatType.STR, 8, StatType.VIT, 2), plan);
        Map<StatType, Integer> catchUp = BotPlans.allocation(growth, StatBlock.ZERO.with(StatType.VIT, 10), 3);
        assertEquals("points go to the stat furthest behind its share", Map.of(StatType.STR, 3), catchUp);
        assertTrue(BotPlans.allocation(growth, StatBlock.ZERO, 0).isEmpty());
    }

    @Test
    public void everyJobSpendsAllItsStatPointsOnItsOwnStats() {
        for (JobType job : BotPlans.JOBS) {
            StatBlock growth = Jobs.get(job).growthPerLevel();
            Map<StatType, Integer> plan = BotPlans.allocation(growth, StatBlock.ZERO, 300);
            assertEquals(job + ": every point spent", 300, plan.values().stream().mapToInt(Integer::intValue).sum());
            StatType main = StatType.STR;
            for (StatType stat : StatType.values()) {
                if (growth.get(stat) > growth.get(main)) {
                    main = stat;
                }
                if (growth.get(stat) <= 0 && stat != StatType.VIT) {
                    assertFalse(job + ": nothing into " + stat, plan.containsKey(stat));
                }
            }
            for (StatType stat : StatType.values()) {
                if (stat != main && stat != StatType.VIT) {
                    assertTrue(job + ": the main stat " + main + " gets the most",
                            plan.getOrDefault(main, 0) >= plan.getOrDefault(stat, 0));
                }
            }
        }
    }

    /** A bot spending one point at a time never gets stuck and in the end masters its whole tree. */
    @Test
    public void skillPointsMasterTheWholeTreeOneByOne() {
        for (JobType job : BotPlans.JOBS) {
            int total = SkillTree.pointsToMaster(job);
            Map<String, Integer> investments = new HashMap<>();
            int level = CharacterRules.MAX_LEVEL;
            int unspent = total;
            int firstUsableLearned = -1;
            for (int spent = 0; spent < total; spent++) {
                Optional<String> next = BotPlans.nextSkill(job, level, investments, unspent);
                assertTrue(job + ": stuck after " + spent + " of " + total + " points", next.isPresent());
                assertEquals(SkillTree.InvestResult.OK,
                        SkillTree.canInvest(job, level, investments, unspent, next.get()));
                SkillDefinition skill = Jobs.skill(next.get()).orElseThrow();
                if (firstUsableLearned < 0 && skill.isUsable()) {
                    firstUsableLearned = spent;
                }
                investments.merge(next.get(), 1, Integer::sum);
                unspent--;
            }
            assertTrue(job + ": everything mastered", BotPlans.nextSkill(job, level, investments, 5).isEmpty());
            assertEquals(job + ": skills to use come first", 0, firstUsableLearned);
        }
    }

    @Test
    public void noSkillWithoutPointsOrJob() {
        assertTrue(BotPlans.nextSkill(JobType.KNIGHT, 10, Map.of(), 0).isEmpty());
        assertTrue(BotPlans.nextSkill(JobType.NONE, 10, Map.of(), 5).isEmpty());
    }

    @Test
    public void huntingGroundsSuitTheLevel() {
        Habitats.Habitat low = Habitats.all().get(0);
        Habitats.Habitat mid = Habitats.all().get(1);
        Habitats.Habitat high = Habitats.all().get(2);
        Map<String, LevelRange> ranges = Map.of(low.monster(), new LevelRange(1, 5), mid.monster(),
                new LevelRange(10, 15), high.monster(), new LevelRange(40, 50));
        List<Habitats.Habitat> three = List.of(low, mid, high);
        assertEquals(List.of(low), BotPlans.huntingGrounds(1, three, id -> Optional.ofNullable(ranges.get(id))));
        assertEquals("one above the level is fine", List.of(mid),
                BotPlans.huntingGrounds(9, three, id -> Optional.ofNullable(ranges.get(id))));
        assertEquals("nothing fits level 30: the nearest in level", List.of(high),
                BotPlans.huntingGrounds(30, three, id -> Optional.ofNullable(ranges.get(id))));
        assertEquals("past every range: the strongest", List.of(high),
                BotPlans.huntingGrounds(99, three, id -> Optional.ofNullable(ranges.get(id))));
    }

    @Test
    public void everyLevelFindsAHuntingGroundInTheRealWorld() {
        for (int level = 1; level <= CharacterRules.MAX_LEVEL; level++) {
            List<Habitats.Habitat> grounds = BotPlans.huntingGrounds(level, Habitats.all(),
                    id -> com.eternalrealm.monster.Monsters.byId(id).map(m -> m.levels()));
            assertFalse("level " + level, grounds.isEmpty());
        }
    }

    @Test
    public void theWaystoneOfTheGroundsOwnFieldComesFirst() {
        FieldLayout.Waystone near = new FieldLayout.Waystone("near", 2, 100, 100);
        FieldLayout.Waystone own = new FieldLayout.Waystone("own", 1, 400, 400);
        FieldLayout.Waystone ownCloser = new FieldLayout.Waystone("own_closer", 1, 200, 200);
        assertEquals(own, BotPlans.waystoneFor(1, 90, 90, List.of(near, own)).orElseThrow());
        assertEquals(ownCloser, BotPlans.waystoneFor(1, 90, 90, List.of(near, own, ownCloser)).orElseThrow());
        assertEquals("no stone in the field: the nearest of all", near,
                BotPlans.waystoneFor(3, 90, 90, List.of(near, own)).orElseThrow());
        assertTrue(BotPlans.waystoneFor(1, 0, 0, List.of()).isEmpty());
        for (Habitats.Habitat habitat : Habitats.all()) {
            assertTrue(habitat.id(), BotPlans.waystoneFor(habitat.field(), habitat.x(), habitat.z(),
                    com.eternalrealm.world.field.Waystones.all()).isPresent());
        }
    }

    @Test
    public void potionsAndShopping() {
        assertEquals(Consumable.HEALTH_POTION_SMALL, BotPlans.bestPotion(1));
        assertEquals(Consumable.HEALTH_POTION_MEDIUM,
                BotPlans.bestPotion(Consumable.HEALTH_POTION_MEDIUM.requiredLevel()));
        assertEquals(Consumable.HEALTH_POTION_LARGE, BotPlans.bestPotion(CharacterRules.MAX_LEVEL));
        assertEquals("up to eight carried", 5, BotPlans.potionsToBuy(3, 100_000, 25));
        assertEquals("at most half the gold", 2, BotPlans.potionsToBuy(0, 100, 25));
        assertEquals(0, BotPlans.potionsToBuy(0, 40, 25));
        assertEquals(0, BotPlans.potionsToBuy(BotPlans.POTIONS_WANTED, 100_000, 25));
    }

    @Test
    public void errandsInTown() {
        assertEquals("a full bag goes to the shop", BotPlans.Errand.SHOP,
                BotPlans.nextErrand(8, 2, 0, 25, 0, 0.9));
        assertEquals("out of potions and able to pay", BotPlans.Errand.SHOP,
                BotPlans.nextErrand(0, 30, 1000, 25, 0, 0.9));
        assertEquals("out of potions but broke: hunting anyway", BotPlans.Errand.HUNT,
                BotPlans.nextErrand(0, 30, 10, 25, 0, 0.1));
        assertEquals("a rest after two hunts", BotPlans.Errand.INN, BotPlans.nextErrand(8, 30, 1000, 25, 2, 0.3));
        assertEquals(BotPlans.Errand.WANDER, BotPlans.nextErrand(8, 30, 1000, 25, 0, 0.8));
        assertEquals(BotPlans.Errand.HUNT, BotPlans.nextErrand(8, 30, 1000, 25, 0, 0.5));
    }

    @Test
    public void fightingDistances() {
        assertEquals(BotPlans.RANGED_DISTANCE, BotPlans.engageDistance(WeaponType.BOW, 2.0), 1e-9);
        assertEquals(BotPlans.RANGED_DISTANCE, BotPlans.engageDistance(WeaponType.STAFF, 2.0), 1e-9);
        double sword = BotPlans.engageDistance(WeaponType.ONE_HANDED_SWORD,
                BotPlans.meleeReach(WeaponProfiles.of(WeaponType.ONE_HANDED_SWORD)));
        assertTrue("a sword fights within its reach: " + sword, sword >= 1.5 && sword
                < BotPlans.meleeReach(WeaponProfiles.of(WeaponType.ONE_HANDED_SWORD)));
        double spear = BotPlans.meleeReach(WeaponProfiles.of(WeaponType.SPEAR));
        assertTrue("a spear reaches farther than dual blades",
                spear > BotPlans.meleeReach(WeaponProfiles.of(WeaponType.DUAL_BLADES)));
        assertEquals(2.5, BotPlans.meleeReach(null), 1e-9);
    }
}
