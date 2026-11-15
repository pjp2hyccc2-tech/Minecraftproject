package com.eternalrealm.job;

import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.combat.SkillBinding;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The finales of the second classes (秘奥義, owner 2026-11-15: 「各職業のニ次職の最後の最後にlv70で＋1800%の必殺技を
 * それぞれ用意して」…「この必殺技はctを90秒にして」): one per class below its tree, at level 70, for either of the class's
 * two ultimates, exactly 1800 % in all, a 90 second cooldown nothing shortens, and a slot of its own (7, key X).
 */
public class FinaleTest {
    private static SkillDefinition finale(JobClass jobClass) {
        List<SkillDefinition> finales = Jobs.classSkills(jobClass).stream().filter(SkillDefinition::isFinale).toList();
        assertEquals(jobClass.toString(), 1, finales.size());
        return finales.get(0);
    }

    /** What a skill deals in all at level 1: its hits, its damaging zones' pulses and its summons' strikes. */
    static double power(SkillDefinition skill) {
        double total = skill.totalMultiplier();
        for (SkillEffect effect : skill.effects()) {
            if (effect instanceof SkillEffect.Zone zone) {
                total += zone.damageMultiplier() * zone.pulses();
            } else if (effect instanceof SkillEffect.Summon summon) {
                total += summon.totalMultiplier();
            }
        }
        return total;
    }

    @Test
    public void everySecondClassHasOneFinaleBelowItsTree() {
        Set<String> ids = new HashSet<>();
        for (JobClass jobClass : JobClass.values()) {
            SkillDefinition skill = finale(jobClass);
            assertTrue(ids.add(skill.id()));
            assertTrue(skill.id(), skill.id().startsWith(jobClass.id() + "_"));
            assertEquals(skill.id(), SkillKind.ULTIMATE, skill.kind());
            assertEquals(skill.id(), SkillDefinition.CLASS_FINALE_TIER, skill.tier());
            assertEquals(skill.id(), JobClass.FINALE_LEVEL, skill.requiredLevel());
            assertEquals(skill.id(), 70, skill.requiredLevel());
            assertEquals(skill.id(), 1, skill.maxLevel());
            assertEquals(skill.id(), 1, skill.pointsFor(1));
            assertTrue(skill.id(), skill.isUsable() && skill.isClassSkill() && !skill.isInnate());
            assertEquals(skill.id(), jobClass, skill.jobClass());
            assertEquals(skill.id(), 0, skill.branch());
            assertEquals(skill.id(), 0, skill.column());
            assertEquals(JobClass.SKILLS, Jobs.classSkills(jobClass).size());
        }
    }

    /** Either of the class's two ultimates (one per branch) opens it; nothing else does. */
    @Test
    public void aFinaleNeedsEitherOfItsClassesUltimates() {
        for (JobClass jobClass : JobClass.values()) {
            SkillDefinition skill = finale(jobClass);
            assertTrue(skill.id(), skill.prerequisites().isEmpty());
            assertEquals(skill.id(), 2, skill.anyOf().size());
            Set<Integer> branches = new HashSet<>();
            for (Prerequisite alternative : skill.anyOf()) {
                SkillDefinition ultimate = Jobs.skill(alternative.skillId()).orElseThrow();
                assertEquals(skill.id(), SkillKind.ULTIMATE, ultimate.kind());
                assertFalse(ultimate.isFinale());
                assertEquals(skill.id(), jobClass, ultimate.jobClass());
                assertEquals(skill.id(), 1, alternative.level());
                branches.add(ultimate.branch());
            }
            assertEquals(skill.id(), Set.of(0, 1), branches);
            assertEquals(skill.requirements(), skill.anyOf());
            JobPath path = JobPath.of(jobClass.id());
            String first = skill.anyOf().get(0).skillId();
            String second = skill.anyOf().get(1).skillId();
            // the ultimates themselves need their branches: here they are simply taken as learned
            assertEquals(SkillTree.InvestResult.PREREQUISITE, SkillTree.canInvest(path, 70, Map.of(), 1, skill.id()));
            assertEquals(SkillTree.InvestResult.OK, SkillTree.canInvest(path, 70, Map.of(first, 1), 1, skill.id()));
            assertEquals(SkillTree.InvestResult.OK, SkillTree.canInvest(path, 70, Map.of(second, 1), 1, skill.id()));
            assertEquals(SkillTree.InvestResult.LOCKED, SkillTree.canInvest(path, 69, Map.of(first, 1), 1, skill.id()));
            assertEquals(SkillTree.InvestResult.MAX_LEVEL,
                    SkillTree.canInvest(path, 70, Map.of(first, 1, skill.id(), 1), 1, skill.id()));
            assertEquals(SkillTree.InvestResult.WRONG_JOB,
                    SkillTree.canInvest(JobPath.base(jobClass.base()), 70, Map.of(first, 1), 1, skill.id()));
            // stored with the finale but without either ultimate: not valid any more (refunded on load)
            assertFalse(SkillTree.isValid(path, 70, Map.of(skill.id(), 1)));
        }
    }

    @Test
    public void finalesDealExactly1800PercentAndWaitNinetySeconds() {
        for (JobClass jobClass : JobClass.values()) {
            SkillDefinition skill = finale(jobClass);
            assertEquals(skill.id() + " in all", 18.0, power(skill), 1e-9);
            assertEquals(skill.id(), 90 * 20, skill.cooldownTicks(1));
            // no passive shortens it, whatever the character has learned
            PassiveTotals quick = new PassiveTotals(PassiveTotals.NONE.modifiers(), 0.0,
                    PassiveTotals.MAX_COOLDOWN_REDUCTION, 0.0, 0.0, List.of(), 0.0, 0.0, 0.0, 0.0, 0.0);
            assertEquals(skill.id(), 90 * 20, skill.binding(1, quick).cooldownTicks());
            assertTrue(skill.id(), skill.action().staminaCost() <= 45.0);
            // every finale lasts three to five seconds
            assertTrue(skill.id(), skill.action().durationTicks() >= 60 && skill.action().durationTicks() <= 90);
        }
    }

    /** Owner: the inquisitor's verdict strikes every foe around for 900 % (twice) and hands each its own sentence. */
    @Test
    public void theInquisitorsVerdictStrikesEveryFoeTwiceForNineHundredPercent() {
        SkillDefinition verdict = Jobs.skill("inquisitor_divine_verdict").orElseThrow();
        List<Double> hits = new ArrayList<>();
        verdict.action().frames().stream().filter(com.eternalrealm.combat.HitFrame::damaging)
                .forEach(frame -> hits.add(frame.multiplier()));
        assertEquals(List.of(9.0, 9.0), hits);
        assertTrue(verdict.effects().stream().anyMatch(effect -> effect instanceof SkillEffect.Verdict));
    }

    @Test
    public void sentencesComeInTurnAndEachWeakensItsOwnWay() {
        SkillEffect.Sentence[] all = SkillEffect.Sentence.values();
        assertEquals(6, all.length);
        Set<String> keys = new HashSet<>();
        Set<String> kinds = new HashSet<>();
        for (int i = 0; i < 18; i++) {
            assertEquals(all[i % all.length], SkillEffect.Sentence.of(i));
        }
        assertEquals(SkillEffect.Sentence.FROST, SkillEffect.Sentence.of(-1));
        for (SkillEffect.Sentence sentence : all) {
            assertTrue(keys.add(sentence.translationKey()));
            List<SkillEffect.Afflict> afflictions = sentence.afflictions(1);
            assertFalse(sentence.toString(), afflictions.isEmpty());
            StringBuilder kind = new StringBuilder();
            for (SkillEffect.Afflict afflict : afflictions) {
                assertEquals(1, afflict.frame());
                assertTrue(sentence + " lasts", afflict.durationTicks() >= 60);
                kind.append(afflict.kind()).append(',');
            }
            assertTrue(sentence + " is like another", kinds.add(kind.toString()));
        }
    }

    @Test
    public void theFinaleSlotTakesOnlyAFinale() {
        assertEquals(Combatant.FINALE_SLOT, SkillTree.FINALE_SLOT);
        assertEquals(7, SkillTree.FINALE_SLOT);
        assertEquals(SkillTree.FINALE_SLOT, CombatRequest.JOB_SKILL_7.skillSlot());
        SkillDefinition finale = finale(JobClass.GUARDIAN);
        JobPath guardian = JobPath.of("guardian");
        String ultimate = finale.anyOf().get(0).skillId();
        Map<String, Integer> learned = new HashMap<>(Map.of(ultimate, 1, finale.id(), 1, "knight_provoke", 1));
        assertEquals(SkillTree.LoadoutResult.OK, SkillTree.canAssign(guardian, 70, learned, 7, finale.id()));
        assertEquals(SkillTree.LoadoutResult.FINALE_ONLY, SkillTree.canAssign(guardian, 70, learned, 1, finale.id()));
        assertEquals(SkillTree.LoadoutResult.FINALE_ONLY,
                SkillTree.canAssign(guardian, 70, learned, SkillTree.ULTIMATE_SLOT, finale.id()));
        assertEquals(SkillTree.LoadoutResult.NEEDS_FINALE, SkillTree.canAssign(guardian, 70, learned, 7, ultimate));
        assertEquals(SkillTree.LoadoutResult.NEEDS_FINALE,
                SkillTree.canAssign(guardian, 70, learned, 7, "knight_provoke"));
        assertEquals(SkillTree.LoadoutResult.OK, SkillTree.canAssign(guardian, 70, learned, 7, ""));
        assertEquals(SkillTree.LoadoutResult.WRONG_JOB,
                SkillTree.canAssign(JobPath.of("paladin"), 70, learned, 7, finale.id()));
        // learned, it finds its slot by itself and binds there
        List<String> loadout = SkillTree.defaultLoadout(JobType.KNIGHT);
        assertEquals(SkillTree.LOADOUT_SLOTS, loadout.size());
        assertEquals(7, SkillTree.autoSlot(loadout, finale));
        List<String> placed = SkillTree.assign(loadout, 7, finale.id());
        assertEquals(finale.id(), SkillTree.effectiveLoadout(guardian, 70, learned, placed).get(6));
        SkillBinding[] bindings = SkillTree.bindings(guardian, 70, learned, placed);
        assertNotNull(bindings[7]);
        assertEquals(finale.id(), bindings[7].action().id());
        // below level 70 or without the finale learned it stays out of the fight
        assertNull(SkillTree.bindings(guardian, 69, learned, placed)[7]);
        Map<String, Integer> without = new HashMap<>(learned);
        without.remove(finale.id());
        assertEquals("", SkillTree.effectiveLoadout(guardian, 70, without, placed).get(6));
        // the last free slot it does not take from anything else
        assertEquals(-1, SkillTree.autoSlot(placed, finale));
    }

    @Test
    public void theNewEffectsAreBounded() {
        SkillEffect.Summon king = new SkillEffect.Summon(1, "bone_king", 400, 6, 2.5);
        assertEquals(15.0, king.totalMultiplier(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new SkillEffect.Summon(1, "bone_king", 10, 6, 2.5));
        assertThrows(IllegalArgumentException.class, () -> new SkillEffect.Summon(1, "bone_king", 400, 0, 2.5));
        assertThrows(IllegalArgumentException.class, () -> new SkillEffect.Summon(1, "Bone King", 400, 6, 2.5));
        assertThrows(IllegalArgumentException.class, () -> new SkillEffect.Engrave(1, 40.0, 20));
        assertThrows(IllegalArgumentException.class, () -> new SkillEffect.Engrave(1, 6.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SkillEffect.Reckon(-1, 10.0));
        assertThrows(IllegalArgumentException.class, () -> new SkillEffect.Reckon(1, 99.0));
        assertThrows(IllegalArgumentException.class, () -> new SkillEffect.Verdict(-1));
        assertThrows(IllegalArgumentException.class, () -> new SkillEffect.Critical(-1));
        assertFalse(SkillEffect.BuffKind.INVULNERABLE.toString().isEmpty());
        assertTrue(EnumSet.allOf(SkillEffect.AfflictionKind.class).contains(SkillEffect.AfflictionKind.CURSE));
    }

    /** The shinobi's eight flashes only mark; the sheathing settles every mark at once, within its range. */
    @Test
    public void theEightFlashesEngraveAndTheSheathingReckons() {
        SkillDefinition flash = Jobs.skill("shinobi_eightfold_flash").orElseThrow();
        long engraves = flash.effects().stream().filter(effect -> effect instanceof SkillEffect.Engrave).count();
        assertEquals(8, engraves);
        SkillEffect.Reckon reckon = flash.effects().stream().filter(effect -> effect instanceof SkillEffect.Reckon)
                .map(effect -> (SkillEffect.Reckon) effect).findFirst().orElseThrow();
        assertTrue(flash.action().frames().get(reckon.frame()).damaging());
        assertEquals(18.0, flash.action().frames().get(reckon.frame()).multiplier(), 1e-9);
        for (SkillEffect effect : flash.effects()) {
            if (effect instanceof SkillEffect.Engrave engrave) {
                assertFalse("a flash only marks", flash.action().frames().get(engrave.frame()).damaging());
                assertTrue(engrave.frame() < reckon.frame());
            }
        }
    }

    /** The sniper's rifle fires three piercing rounds, the guardian's aegis keeps its allies whole for 10 seconds. */
    @Test
    public void theRifleFiresThreeRoundsAndTheAegisHoldsTenSeconds() {
        SkillDefinition rifle = Jobs.skill("sniper_three_magic_bullets").orElseThrow();
        List<com.eternalrealm.combat.HitFrame> rounds = rifle.action().frames().stream()
                .filter(com.eternalrealm.combat.HitFrame::isProjectile).toList();
        assertEquals(3, rounds.size());
        for (com.eternalrealm.combat.HitFrame round : rounds) {
            assertTrue(round.projectile().pierce() >= 8);
        }
        SkillDefinition aegis = Jobs.skill("guardian_adamant_bulwark").orElseThrow();
        long invulnerable = aegis.effects().stream().filter(effect -> effect instanceof SkillEffect.Buff buff
                && buff.kind() == SkillEffect.BuffKind.INVULNERABLE && buff.durationTicks() == 200).count();
        assertEquals("on itself and its allies", 2, invulnerable);
    }
}
