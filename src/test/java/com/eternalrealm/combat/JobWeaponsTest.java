package com.eternalrealm.combat;

import com.eternalrealm.item.EquipmentBase;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentTooltip;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.TooltipLine;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillTree;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Weapons by job: the combat state machine (same code on the server and in the client's prediction) refuses attacks,
 * skills and the guard with a weapon the job cannot use, and the equipment tooltip says who can use a weapon.
 */
public class JobWeaponsTest {
    private static final CombatStats BASE = new CombatStats(1.0, 0.0, 0.0, 1.0, 0.0, 1.5, 100.0, 1.0);
    private Combatant mage;
    private long now;

    @Before
    public void setUp() {
        mage = new Combatant(BASE);
        mage.setAllowedWeapons(Jobs.get(JobType.MAGE).weapons());
        SkillBinding[] bindings = SkillTree.bindings(JobType.MAGE, 50, Map.of());
        for (int slot = 1; slot < Combatant.SKILL_SLOTS; slot++) {
            mage.setJobSkill(slot, bindings[slot]);
        }
        now = 100;
        mage.tick(now);
    }

    @Test
    public void everyAttackWithAForbiddenWeaponIsRefused() {
        for (CombatRequest request : List.of(CombatRequest.LIGHT, CombatRequest.HEAVY_START, CombatRequest.SKILL,
                CombatRequest.JOB_SKILL_1, CombatRequest.JOB_SKILL_4)) {
            assertEquals(request.toString(), RequestOutcome.REJECTED_JOB_WEAPON,
                    mage.submit(request, now, WeaponProfiles.BOW, false, false));
        }
        assertEquals(Combatant.Phase.IDLE, mage.phase(now));
        assertEquals(100.0, mage.stamina(), 1e-9);
        assertFalse(mage.canStartGuard(now, WeaponProfiles.TWO_HANDED_SWORD));
        assertFalse(mage.allowsWeapon(WeaponProfiles.SPEAR));
        assertFalse(mage.allowsWeapon(null));
    }

    @Test
    public void dodgingStaysPossibleAndTheJobsOwnWeaponWorks() {
        assertEquals(RequestOutcome.ACCEPTED, mage.submit(CombatRequest.DODGE, now, WeaponProfiles.BOW, false, false));
        mage.tick(now + 40);
        assertEquals(RequestOutcome.ACCEPTED, mage.submit(CombatRequest.LIGHT, now + 40, WeaponProfiles.STAFF, false,
                false));
        mage.tick(now + 80);
        assertTrue(mage.canStartGuard(now + 80, WeaponProfiles.STAFF));
    }

    @Test
    public void withoutAJobEveryWeaponWorks() {
        Combatant fighter = new Combatant(BASE);
        fighter.tick(now);
        assertTrue(fighter.allowedWeapons().isEmpty());
        assertEquals(RequestOutcome.ACCEPTED, fighter.submit(CombatRequest.LIGHT, now, WeaponProfiles.BOW, false, false));
        fighter.setAllowedWeapons(Set.of());
        assertTrue(fighter.allowsWeapon(WeaponProfiles.SPEAR));
    }

    @Test
    public void aBufferedAttackIsDroppedWhenTheJobNoLongerAllowsTheWeapon() {
        Combatant knight = new Combatant(BASE);
        knight.setAllowedWeapons(Jobs.get(JobType.KNIGHT).weapons());
        knight.tick(now);
        assertEquals(RequestOutcome.ACCEPTED, knight.submit(CombatRequest.LIGHT, now, WeaponProfiles.ONE_HANDED_SWORD,
                false, false));
        knight.tick(now); // the first swing's start event
        assertEquals(RequestOutcome.BUFFERED, knight.submit(CombatRequest.LIGHT, now + 1,
                WeaponProfiles.ONE_HANDED_SWORD, false, false));
        knight.setAllowedWeapons(Set.of(WeaponType.STAFF)); // job changed while the input was buffered
        long t = now + 1;
        int lights = 0;
        for (; t < now + 40; t++) {
            for (CombatEvent event : knight.tick(t)) {
                if (event instanceof CombatEvent.ActionStarted) {
                    lights++;
                }
            }
        }
        assertEquals(0, lights);
    }

    @Test
    public void theTooltipNamesTheJobsThatCanUseAWeapon() {
        EquipmentData bow = EquipmentData.of(EquipmentBase.weapon(WeaponType.BOW, ItemTier.IRON), ItemRarity.COMMON,
                List.of());
        TooltipLine forRanger = usableBy(EquipmentTooltip.lines(bow, 10, JobType.RANGER));
        assertEquals(TooltipLine.COLOR_TEXT, forRanger.color());
        TooltipLine.Join jobs = (TooltipLine.Join) forRanger.args().get(0);
        assertEquals(List.of(new TooltipLine.Key(JobType.RANGER.translationKey())), jobs.parts());
        assertEquals(TooltipLine.COLOR_WARNING, usableBy(EquipmentTooltip.lines(bow, 10, JobType.MAGE)).color());
        assertEquals(TooltipLine.COLOR_TEXT, usableBy(EquipmentTooltip.lines(bow, 10, JobType.NONE)).color());
        EquipmentData sword = EquipmentData.of(EquipmentBase.weapon(WeaponType.ONE_HANDED_SWORD, ItemTier.IRON),
                ItemRarity.COMMON, List.of());
        assertEquals(4, ((TooltipLine.Join) usableBy(EquipmentTooltip.lines(sword)).args().get(0)).parts().size());
        EquipmentData helmet = EquipmentData.of(EquipmentBase.armor(com.eternalrealm.item.EquipmentSlot.HEAD,
                ItemTier.IRON), ItemRarity.COMMON, List.of());
        assertTrue(EquipmentTooltip.lines(helmet, 10, JobType.MAGE).stream()
                .noneMatch(line -> line.kind() == TooltipLine.Kind.USABLE_BY));
    }

    private static TooltipLine usableBy(List<TooltipLine> lines) {
        return lines.stream().filter(line -> line.kind() == TooltipLine.Kind.USABLE_BY).findFirst().orElseThrow();
    }
}
