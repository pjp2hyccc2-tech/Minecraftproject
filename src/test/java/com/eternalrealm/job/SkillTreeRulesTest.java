package com.eternalrealm.job;

import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.SkillBinding;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The skill loadout (six slots, the ultimate slot), passive totals and their effect on the bindings. */
public class SkillTreeRulesTest {
    private static List<String> empty() {
        return Collections.nCopies(SkillTree.LOADOUT_SLOTS, SkillTree.EMPTY);
    }

    @Test
    public void theDefaultLoadoutHoldsTheInnateSkillsInTheirOldSlots() {
        assertEquals(List.of("mage_fireball", "mage_ice_lance", "mage_thunderstorm", "mage_meteor", "", ""),
                SkillTree.defaultLoadout(JobType.MAGE));
        assertEquals(SkillTree.defaultLoadout(JobType.MAGE),
                SkillTree.effectiveLoadout(JobType.MAGE, 1, Map.of(), List.of()));
        assertEquals(empty(), SkillTree.effectiveLoadout(JobType.NONE, 50, Map.of(), List.of("mage_fireball")));
    }

    @Test
    public void assigningChecksTheSkillAndTheSlot() {
        Map<String, Integer> learned = Map.of("mage_fireball", 4, "mage_explosion", 1, "mage_pyromancy", 2);
        assertEquals(SkillTree.LoadoutResult.OK, SkillTree.canAssign(JobType.MAGE, 20, learned, 5, "mage_explosion"));
        assertEquals(SkillTree.LoadoutResult.OK, SkillTree.canAssign(JobType.MAGE, 20, learned, 6, ""));
        assertEquals(SkillTree.LoadoutResult.NOT_LEARNED,
                SkillTree.canAssign(JobType.MAGE, 20, learned, 5, "mage_frost_bolt"));
        assertEquals(SkillTree.LoadoutResult.PASSIVE, SkillTree.canAssign(JobType.MAGE, 20, learned, 5, "mage_pyromancy"));
        assertEquals(SkillTree.LoadoutResult.WRONG_JOB, SkillTree.canAssign(JobType.MAGE, 20, learned, 5, "priest_heal"));
        assertEquals(SkillTree.LoadoutResult.UNKNOWN_SKILL, SkillTree.canAssign(JobType.MAGE, 20, learned, 5, "nope"));
        assertEquals(SkillTree.LoadoutResult.BAD_SLOT, SkillTree.canAssign(JobType.MAGE, 20, learned, 7, "mage_fireball"));
        assertEquals(SkillTree.LoadoutResult.BAD_SLOT, SkillTree.canAssign(JobType.MAGE, 20, learned, 0, "mage_fireball"));
        assertEquals(SkillTree.LoadoutResult.NEEDS_ULTIMATE,
                SkillTree.canAssign(JobType.MAGE, 20, learned, SkillTree.ULTIMATE_SLOT, "mage_explosion"));
        assertEquals(SkillTree.LoadoutResult.ULTIMATE_ONLY,
                SkillTree.canAssign(JobType.MAGE, 40, learned, 1, "mage_meteor"));
        // innate skills may be placed before their level (the slot fills when they unlock)
        assertEquals(SkillTree.LoadoutResult.OK, SkillTree.canAssign(JobType.MAGE, 1, Map.of(), 3, "mage_thunderstorm"));
        assertEquals(SkillTree.LoadoutResult.NO_JOB, SkillTree.canAssign(JobType.NONE, 20, learned, 1, "mage_fireball"));
    }

    @Test
    public void aSkillAlreadyInAnotherSlotSwapsPlaces() {
        List<String> start = SkillTree.defaultLoadout(JobType.MAGE);
        List<String> moved = SkillTree.assign(start, 2, "mage_fireball");
        assertEquals(List.of("mage_ice_lance", "mage_fireball", "mage_thunderstorm", "mage_meteor", "", ""), moved);
        List<String> placed = SkillTree.assign(moved, 6, "mage_explosion");
        assertEquals("mage_explosion", placed.get(5));
        List<String> cleared = SkillTree.assign(placed, 1, "");
        assertEquals("", cleared.get(0));
        assertEquals(List.of("", "mage_fireball", "mage_thunderstorm", "mage_meteor", "", "mage_explosion"), cleared);
    }

    @Test
    public void invalidStoredEntriesCountAsEmptyAndAreSanitized() {
        List<String> stored = List.of("mage_explosion", "priest_heal", "mage_fireball", "mage_fireball", "nope",
                "mage_meteor");
        Map<String, Integer> learned = Map.of("mage_fireball", 4, "mage_explosion", 2);
        // priest skill, a duplicate, an unknown id and an ultimate outside its slot are dropped
        assertEquals(List.of("mage_explosion", "", "mage_fireball", "", "", ""),
                SkillTree.effectiveLoadout(JobType.MAGE, 40, learned, stored));
        assertEquals(List.of("mage_explosion", "", "mage_fireball", "", "", ""),
                SkillTree.sanitize(JobType.MAGE, 40, learned, stored));
        // a forgotten skill (after a reset) leaves its slot empty
        assertEquals("", SkillTree.effectiveLoadout(JobType.MAGE, 40, Map.of(), stored).get(0));
        List<String> clean = SkillTree.assign(SkillTree.defaultLoadout(JobType.MAGE), 5, "mage_explosion");
        assertSame(clean, SkillTree.sanitize(JobType.MAGE, 40, learned, clean));
        assertSame(List.of(), SkillTree.sanitize(JobType.MAGE, 40, learned, List.of()));
    }

    @Test
    public void newlyLearnedActivesFindAFreeSlot() {
        List<String> loadout = SkillTree.defaultLoadout(JobType.MAGE);
        SkillDefinition explosion = Jobs.skill("mage_explosion").orElseThrow();
        assertEquals(5, SkillTree.autoSlot(loadout, explosion));
        List<String> full = SkillTree.assign(SkillTree.assign(loadout, 5, "mage_frost_bolt"), 6, "mage_spark");
        assertEquals(-1, SkillTree.autoSlot(full, explosion));
        assertEquals(-1, SkillTree.autoSlot(loadout, Jobs.skill("mage_pyromancy").orElseThrow()));
        assertEquals(-1, SkillTree.autoSlot(loadout, Jobs.skill("mage_fireball").orElseThrow()));
        List<String> noUltimate = SkillTree.assign(loadout, 4, "");
        assertEquals(4, SkillTree.autoSlot(noUltimate, Jobs.skill("mage_armageddon").orElseThrow()));
    }

    @Test
    public void bindingsFollowTheStoredLoadout() {
        Map<String, Integer> learned = new HashMap<>(Map.of("mage_fireball", 4, "mage_explosion", 2));
        List<String> loadout = SkillTree.assign(SkillTree.defaultLoadout(JobType.MAGE), 5, "mage_explosion");
        SkillBinding[] bindings = SkillTree.bindings(JobType.MAGE, 20, learned, loadout);
        assertEquals(Combatant.SKILL_SLOTS, bindings.length);
        assertEquals("mage_fireball", bindings[1].action().id());
        assertEquals("mage_explosion", bindings[5].action().id());
        assertEquals(1.1, bindings[5].damageScale(), 1e-9);
        assertNotNull(bindings[3]);
        assertNull(bindings[4]); // the ultimate unlocks at 30
        assertNull(bindings[6]);
    }

    @Test
    public void passivesAddUpAndShapeTheBindings() {
        Map<String, Integer> learned = Map.of("mage_fireball", 0, "mage_pyromancy", 5, "mage_arcane_mind", 1,
                "mage_focus", 5, "mage_arcane_flow", 5, "mage_overload", 4);
        PassiveTotals totals = SkillTree.passives(JobType.MAGE, 30, learned);
        assertEquals(0.15, totals.modifiers().magicBonus(), 1e-9);
        assertEquals(6.0, totals.modifiers().staminaBonus(), 1e-9);
        assertEquals(0.10, totals.cooldownReduction(), 1e-9);
        assertEquals(0.15, totals.staminaReduction(), 1e-9);
        assertEquals(0.12, totals.skillPower(), 1e-9);
        SkillBinding fireball = SkillTree.bindings(JobType.MAGE, 30, learned)[1];
        assertEquals(1.12, fireball.damageScale(), 1e-9);
        assertEquals(90, fireball.cooldownTicks());
        assertEquals(15.0 * 0.85, fireball.staminaCost(), 1e-9);
        assertSame(PassiveTotals.NONE, SkillTree.passives(JobType.NONE, 50, learned));
        assertSame(PassiveTotals.NONE, SkillTree.passives(JobType.MAGE, 30, Map.of()));
    }

    @Test
    public void passiveTotalsAreCapped() {
        Map<String, Integer> learned = new HashMap<>();
        for (SkillDefinition skill : Jobs.get(JobType.ASSASSIN).skills()) {
            learned.put(skill.id(), skill.pointsFor(skill.maxLevel()));
        }
        PassiveTotals totals = SkillTree.passives(JobType.ASSASSIN, 50, learned);
        assertTrue(totals.evasion() <= PassiveTotals.MAX_EVASION);
        assertTrue(totals.cooldownReduction() <= PassiveTotals.MAX_COOLDOWN_REDUCTION);
        assertTrue(totals.lifesteal() > 0.0);
        assertTrue(totals.procs().size() >= 2);
        assertTrue(totals.healOnKill() > 0.0);
    }
}
