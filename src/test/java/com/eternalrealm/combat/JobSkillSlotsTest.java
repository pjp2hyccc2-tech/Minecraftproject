package com.eternalrealm.combat;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Job skill slots of {@link Combatant}: learning, weapon restrictions, independent cooldowns, scaling. */
public class JobSkillSlotsTest {
    private static final CombatStats BASE = new CombatStats(1.0, 0.0, 0.0, 1.0, 0.0, 1.5, 100.0, 1.0);
    private static final ActionDefinition BASH = new ActionDefinition("test_bash", ActionKind.SKILL, 16, 12,
            List.of(new HitFrame(4, HitShape.arc(3.0, 60), 1.5, 0.6)), 20.0, 0, 0, false);
    private static final ActionDefinition VOLLEY = new ActionDefinition("test_volley", ActionKind.SKILL, 20, 16,
            List.of(HitFrame.launch(6, ProjectileSpec.arrow(3.0).withFan(5, 30), 1.0, 0.3)), 10.0, 0, 0, false);

    private Combatant fighter;
    private long now;
    private final List<CombatEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        fighter = new Combatant(BASE);
        now = 100;
        fighter.tick(now);
    }

    private RequestOutcome submit(CombatRequest request, WeaponProfile weapon) {
        return fighter.submit(request, now, weapon, false, false);
    }

    private void runTo(long target) {
        for (long t = now + 1; t <= target; t++) {
            events.addAll(fighter.tick(t));
        }
        now = target;
    }

    @Test
    public void emptySlotIsRejectedAsNotLearned() {
        assertEquals(RequestOutcome.REJECTED_NOT_LEARNED, submit(CombatRequest.JOB_SKILL_1, WeaponProfiles.SPEAR));
        assertEquals(Combatant.Phase.IDLE, fighter.phase(now));
    }

    @Test
    public void learnedSkillStartsItsActionWithItsOwnScaleAndCooldown() {
        fighter.setJobSkill(1, new SkillBinding(BASH, Set.of(), 1.3, 120));
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.JOB_SKILL_1, WeaponProfiles.SPEAR));
        assertSame(BASH, fighter.currentAction().orElseThrow());
        assertEquals(80.0, fighter.stamina(), 1e-9);
        assertEquals(120, fighter.skillCooldownRemaining(1, now));
        assertEquals(120, fighter.skillCooldownTotal(1));
        assertEquals(0, fighter.skillCooldownRemaining(now)); // the weapon skill is independent
        runTo(110);
        CombatEvent.Hit hit = (CombatEvent.Hit) events.stream().filter(e -> e instanceof CombatEvent.Hit)
                .findFirst().orElseThrow();
        assertEquals(1.3, hit.damageScale(), 1e-9);
        assertEquals(104, hit.tick());
    }

    @Test
    public void cooldownsOfDifferentSlotsDoNotBlockEachOther() {
        fighter.setJobSkill(1, new SkillBinding(BASH, Set.of(), 1.0, 200));
        fighter.setJobSkill(2, new SkillBinding(VOLLEY, Set.of(), 1.0, 200));
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.JOB_SKILL_1, WeaponProfiles.ONE_HANDED_SWORD));
        runTo(130);
        assertEquals(RequestOutcome.REJECTED_COOLDOWN, submit(CombatRequest.JOB_SKILL_1, WeaponProfiles.ONE_HANDED_SWORD));
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.JOB_SKILL_2, WeaponProfiles.ONE_HANDED_SWORD));
        runTo(160);
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.SKILL, WeaponProfiles.ONE_HANDED_SWORD));
        runTo(300);
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.JOB_SKILL_1, WeaponProfiles.ONE_HANDED_SWORD));
    }

    @Test
    public void weaponRestrictionIsEnforced() {
        fighter.setJobSkill(3, new SkillBinding(VOLLEY, Set.of(WeaponType.BOW), 1.0, 100));
        assertEquals(RequestOutcome.REJECTED_WRONG_WEAPON, submit(CombatRequest.JOB_SKILL_3, WeaponProfiles.STAFF));
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.JOB_SKILL_3, WeaponProfiles.BOW));
    }

    @Test
    public void skillCannotChainIntoAnotherSkillsRecoveryButBuffersUntilTheCancelPoint() {
        fighter.setJobSkill(1, new SkillBinding(BASH, Set.of(), 1.0, 100));
        fighter.setJobSkill(2, new SkillBinding(VOLLEY, Set.of(), 1.0, 100));
        submit(CombatRequest.JOB_SKILL_1, WeaponProfiles.ONE_HANDED_SWORD);
        runTo(106); // after the only hit (104), before the cancel point (112)
        assertEquals(RequestOutcome.BUFFERED, submit(CombatRequest.JOB_SKILL_2, WeaponProfiles.ONE_HANDED_SWORD));
        runTo(112);
        assertSame(VOLLEY, fighter.currentAction().orElseThrow());
    }

    @Test
    public void lightAttackRecoveryCanBeCancelledByAJobSkill() {
        fighter.setJobSkill(1, new SkillBinding(BASH, Set.of(), 1.0, 100));
        submit(CombatRequest.LIGHT, WeaponProfiles.ONE_HANDED_SWORD);
        runTo(104); // hit at 103, cancel point at 106
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.JOB_SKILL_1, WeaponProfiles.ONE_HANDED_SWORD));
        assertSame(BASH, fighter.currentAction().orElseThrow());
    }

    @Test
    public void notEnoughStaminaForTheSkill() {
        fighter.setStamina(5.0);
        fighter.setJobSkill(1, new SkillBinding(BASH, Set.of(), 1.0, 100));
        assertEquals(RequestOutcome.REJECTED_STAMINA, submit(CombatRequest.JOB_SKILL_1, WeaponProfiles.ONE_HANDED_SWORD));
    }

    @Test
    public void emptyingASlotForgetsTheSkillButKeepsItsCooldown() {
        fighter.setJobSkill(1, new SkillBinding(BASH, Set.of(), 1.0, 100));
        submit(CombatRequest.JOB_SKILL_1, WeaponProfiles.ONE_HANDED_SWORD);
        fighter.setJobSkill(1, null);
        assertNull(fighter.jobSkill(1));
        assertEquals(100, fighter.skillCooldownRemaining(1, now));
        assertSame(BASH, fighter.currentAction().orElseThrow()); // the running action is not interrupted
        assertThrows(IllegalArgumentException.class, () -> fighter.setJobSkill(0, null));
        assertThrows(IllegalArgumentException.class, () -> fighter.setJobSkill(5, null));
    }

    @Test
    public void rollbackRestoresEverySlotCooldown() {
        fighter.setJobSkill(4, new SkillBinding(BASH, Set.of(), 1.0, 900));
        Combatant.Snapshot before = fighter.snapshot();
        submit(CombatRequest.JOB_SKILL_4, WeaponProfiles.ONE_HANDED_SWORD);
        assertEquals(900, fighter.skillCooldownRemaining(4, now));
        fighter.restore(before);
        assertEquals(0, fighter.skillCooldownRemaining(4, now));
        assertEquals(100.0, fighter.stamina(), 1e-9);
    }

    @Test
    public void guardBonusAndDodgeCostComeFromTheStats() {
        CombatStats tank = new CombatStats(1.0, 0.0, 0.0, 1.0, 0.0, 1.5, 100.0, 1.0, 1.0, 1.0, 1.0, 0.2, 0.5, 0.0);
        fighter.setStats(tank);
        assertTrue(fighter.startGuard(now, WeaponProfiles.ONE_HANDED_SWORD));
        runTo(now + 20); // past the parry window
        GuardResult guarded = fighter.applyGuard(now, true, 10.0);
        assertEquals(GuardResult.Outcome.GUARDED, guarded.outcome());
        assertEquals(10.0 * (1.0 - 0.8), guarded.amount(), 1e-9); // 0.60 + 0.20
        fighter.stopGuard();
        double before = fighter.stamina();
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.DODGE, WeaponProfiles.ONE_HANDED_SWORD));
        assertEquals(before - 12.5, fighter.stamina(), 1e-9);
    }

    @Test
    public void serviceRemembersJobSkillCooldownsAcrossRelog() {
        CombatService service = new CombatService();
        UUID id = UUID.randomUUID();
        Combatant first = service.register(id, BASE, 0);
        first.setJobSkill(2, new SkillBinding(BASH, Set.of(), 1.0, 500));
        first.submit(CombatRequest.JOB_SKILL_2, 0, WeaponProfiles.SPEAR, false, false);
        service.remove(id, 100);
        Combatant second = service.register(id, BASE, 150);
        assertEquals(350, second.skillCooldownRemaining(2, 150));
        assertEquals(500, second.skillCooldownTotal(2));
        assertEquals(0, second.skillCooldownRemaining(150));
    }

    @Test
    public void bindingValidation() {
        ActionDefinition light = WeaponProfiles.SPEAR.combo().get(0);
        assertThrows(IllegalArgumentException.class, () -> new SkillBinding(light, Set.of(), 1.0, 100));
        assertThrows(IllegalArgumentException.class, () -> new SkillBinding(BASH, Set.of(), 1.0, 0));
        assertTrue(new SkillBinding(BASH, Set.of(), 1.0, 1).allows(WeaponProfiles.BOW));
        assertTrue(!new SkillBinding(BASH, Set.of(WeaponType.STAFF), 1.0, 1).allows(null));
    }

    @Test
    public void projectileSpecFansAreSymmetric() {
        double[] offsets = ProjectileSpec.arrow(3.0).withFan(5, 40).yawOffsets();
        assertEquals(5, offsets.length);
        assertEquals(-20.0, offsets[0], 1e-9);
        assertEquals(0.0, offsets[2], 1e-9);
        assertEquals(20.0, offsets[4], 1e-9);
        assertEquals(0.0, ProjectileSpec.arrow(3.0).yawOffsets()[0], 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new ProjectileSpec("arrow", 3.0, true, 60, 0, 0, 1, 10, false));
    }

    @Test
    public void circleAheadIsCentredInFrontOfTheAttacker() {
        HitShape rain = HitShape.circleAhead(3.0, 10.0);
        AttackOrigin facingSouth = new AttackOrigin(0, 64, 0, 0, 0);
        assertTrue(HitGeometry.contains(rain, facingSouth, new Box(-0.3, 63, 9.7, 0.3, 65, 10.3)));
        assertTrue(!HitGeometry.contains(rain, facingSouth, new Box(-0.3, 63, -0.3, 0.3, 65, 0.3)));
        AttackOrigin facingWest = new AttackOrigin(0, 64, 0, 90, 0);
        assertTrue(HitGeometry.contains(rain, facingWest, new Box(-10.3, 63, -0.3, -9.7, 65, 0.3)));
        assertThrows(IllegalArgumentException.class,
                () -> new HitShape(HitShape.Kind.ARC, 3, 60, 0, 1, 1, 4, 2.0));
    }
}
