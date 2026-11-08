package com.eternalrealm.combat;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The action state machine. Calls follow the server's order: a request is submitted after {@code tick(now)} ran for
 * the current tick, then the following ticks are run one by one.
 */
public class CombatantTest {
    /** Neutral stats: no stat scaling, no crits, 100 stamina regenerating 1/tick. */
    private static final CombatStats BASE = new CombatStats(1.0, 0.0, 0.0, 1.0, 0.0, 1.5, 100.0, 1.0);
    private static final WeaponProfile SWORD = WeaponProfiles.ONE_HANDED_SWORD;
    private static final double EPS = 1.0e-9;

    private Combatant fighter;
    private long now;
    private final List<CombatEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        fighter = new Combatant(BASE);
        now = 100;
        fighter.tick(now);
    }

    private RequestOutcome submit(CombatRequest request) {
        return fighter.submit(request, now, SWORD, false);
    }

    /** Runs ticks now+1 .. target, collecting events. */
    private void runTo(long target) {
        for (long t = now + 1; t <= target; t++) {
            events.addAll(fighter.tick(t));
        }
        now = target;
    }

    private List<CombatEvent.Hit> hits() {
        List<CombatEvent.Hit> hits = new ArrayList<>();
        for (CombatEvent event : events) {
            if (event instanceof CombatEvent.Hit hit) {
                hits.add(hit);
            }
        }
        return hits;
    }

    private List<CombatEvent.ActionStarted> starts() {
        List<CombatEvent.ActionStarted> starts = new ArrayList<>();
        for (CombatEvent event : events) {
            if (event instanceof CombatEvent.ActionStarted start) {
                starts.add(start);
            }
        }
        return starts;
    }

    // ------------------------------------------------------------------ light combo

    @Test
    public void lightComboProgressesThroughAllStepsAndWraps() {
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.LIGHT));
        runTo(106); // cancel point of step 1
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.LIGHT));
        runTo(112); // cancel point of step 2 (106 + 6)
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.LIGHT));
        runTo(124); // cancel point of the finisher (112 + 12)
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.LIGHT));
        runTo(140);

        List<CombatEvent.ActionStarted> starts = starts();
        assertEquals(4, starts.size());
        assertEquals(0, starts.get(0).comboStep());
        assertEquals(1, starts.get(1).comboStep());
        assertEquals(2, starts.get(2).comboStep());
        assertEquals("after the finisher the combo starts over", 0, starts.get(3).comboStep());

        List<CombatEvent.Hit> hits = hits();
        assertEquals(4, hits.size());
        assertEquals(103, hits.get(0).tick());
        assertEquals(109, hits.get(1).tick());
        assertEquals(117, hits.get(2).tick());
        assertEquals(127, hits.get(3).tick());
        assertEquals(1.0, hits.get(0).frame().multiplier(), EPS);
        assertEquals(1.1, hits.get(1).frame().multiplier(), EPS);
        assertEquals(1.6, hits.get(2).frame().multiplier(), EPS);
    }

    @Test
    public void comboContinuesWithinTheResetWindowAndRestartsAfterIt() {
        submit(CombatRequest.LIGHT); // ends at 110, combo kept until 122
        runTo(122);
        submit(CombatRequest.LIGHT);
        runTo(160);
        submit(CombatRequest.LIGHT);
        runTo(170);
        List<CombatEvent.ActionStarted> starts = starts();
        assertEquals(1, starts.get(1).comboStep());
        assertEquals("a pause longer than the reset window restarts the combo", 0, starts.get(2).comboStep());
    }

    @Test
    public void inputDuringAnActionIsBufferedAndStartsExactlyAtTheCancelPoint() {
        submit(CombatRequest.LIGHT);
        runTo(101);
        assertEquals(RequestOutcome.BUFFERED, submit(CombatRequest.LIGHT));
        assertEquals("only one input is buffered", RequestOutcome.REJECTED_BUSY, submit(CombatRequest.LIGHT));
        runTo(120);
        List<CombatEvent.ActionStarted> starts = starts();
        assertEquals(2, starts.size());
        assertEquals(106, starts.get(1).tick());
        assertEquals(1, starts.get(1).comboStep());
        assertEquals(List.of(103L, 109L), hits().stream().map(CombatEvent::tick).toList());
    }

    @Test
    public void everyHitFrameFiresExactlyOnceInOrder() {
        submit(CombatRequest.SKILL); // whirlwind: frames at +7 and +13
        runTo(200);
        List<CombatEvent.Hit> hits = hits();
        assertEquals(2, hits.size());
        assertEquals(0, hits.get(0).frameIndex());
        assertEquals(1, hits.get(1).frameIndex());
        assertEquals(107, hits.get(0).tick());
        assertEquals(113, hits.get(1).tick());
    }

    @Test
    public void switchingWeaponsCancelsTheActionAndItsRemainingHits() {
        submit(CombatRequest.LIGHT);
        runTo(101);
        fighter.updateWeapon(WeaponProfiles.SPEAR);
        runTo(120);
        assertTrue(hits().isEmpty());
        assertEquals(Combatant.Phase.IDLE, fighter.phase(now));
    }

    @Test
    public void noWeaponMeansNoActions() {
        assertEquals(RequestOutcome.REJECTED_NO_WEAPON, fighter.submit(CombatRequest.LIGHT, now, null, false));
        assertEquals(RequestOutcome.REJECTED_NO_WEAPON, fighter.submit(CombatRequest.SKILL, now, null, false));
    }

    @Test
    public void attackSpeedShortensLightAttacksButNotSkills() {
        fighter = new Combatant(new CombatStats(1.0, 0.0, 0.0, 0.6, 0.0, 1.5, 100.0, 1.0));
        fighter.tick(now);
        submit(CombatRequest.LIGHT); // 10/6/hit 3 -> 6/4/hit 2
        runTo(104);
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.LIGHT));
        runTo(150);
        submit(CombatRequest.SKILL);
        runTo(200);
        List<CombatEvent.Hit> hits = hits();
        assertEquals(102, hits.get(0).tick());
        assertEquals(106, hits.get(1).tick());
        assertEquals("skills keep their fixed timing", 157, hits.get(2).tick());
    }

    // ------------------------------------------------------------------ heavy attack

    @Test
    public void earlyReleaseStrikesAtMinimumCharge() {
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.HEAVY_START));
        runTo(104);
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.HEAVY_RELEASE));
        assertEquals(Combatant.Phase.CHARGING, fighter.phase(now));
        runTo(130);
        CombatEvent.ActionStarted strike = starts().get(0);
        assertEquals(108, strike.tick());
        assertEquals(ActionKind.HEAVY, strike.action().kind());
        CombatEvent.Hit hit = hits().get(0);
        assertEquals(111, hit.tick());
        assertEquals(1.8, hit.damageScale(), EPS);
    }

    @Test
    public void chargeScalesDamageAndFullChargeReleasesAutomatically() {
        submit(CombatRequest.HEAVY_START);
        runTo(119);
        submit(CombatRequest.HEAVY_RELEASE);
        runTo(150);
        assertEquals(1.8 + 1.2 * 11.0 / 22.0, hits().get(0).damageScale(), EPS);

        events.clear();
        submit(CombatRequest.HEAVY_START);
        runTo(200);
        assertEquals("auto release at full charge", 180, starts().get(0).tick());
        assertEquals(3.0, hits().get(0).damageScale(), EPS);
    }

    @Test
    public void heavyStartedDuringALightComboIsDerived() {
        submit(CombatRequest.LIGHT);
        runTo(106);
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.HEAVY_START));
        runTo(114);
        submit(CombatRequest.HEAVY_RELEASE);
        runTo(140);
        boolean derived = events.stream().anyMatch(e -> e instanceof CombatEvent.ChargeStarted c && c.derived());
        assertTrue(derived);
        assertEquals(1.8 + 0.3, hits().get(1).damageScale(), EPS);
    }

    @Test
    public void heavyPressedAndReleasedDuringWindupIsBufferedWithItsRelease() {
        submit(CombatRequest.LIGHT);
        runTo(101);
        assertEquals(RequestOutcome.BUFFERED, submit(CombatRequest.HEAVY_START));
        runTo(102);
        assertEquals(RequestOutcome.BUFFERED, submit(CombatRequest.HEAVY_RELEASE));
        runTo(140);
        // light hit at 103, charge starts at the cancel point 106, strikes after the minimum charge (114).
        assertEquals(103, hits().get(0).tick());
        assertEquals(114, starts().get(1).tick());
        assertEquals(1.8 + 0.3, hits().get(1).damageScale(), EPS);
    }

    @Test
    public void heavyCostsStaminaAtTheStrikeAndNeedsEnoughToStart() {
        submit(CombatRequest.HEAVY_START);
        runTo(104);
        assertEquals(100.0, fighter.stamina(), EPS);
        submit(CombatRequest.HEAVY_RELEASE);
        runTo(108);
        assertEquals(85.0, fighter.stamina(), EPS);

        runTo(200);
        fighter.setStamina(10.0);
        assertEquals(RequestOutcome.REJECTED_STAMINA, submit(CombatRequest.HEAVY_START));
    }

    @Test
    public void releaseWithoutChargeIsIgnored() {
        assertEquals(RequestOutcome.IGNORED, submit(CombatRequest.HEAVY_RELEASE));
        submit(CombatRequest.HEAVY_START);
        assertEquals(RequestOutcome.REJECTED_BUSY, submit(CombatRequest.HEAVY_START));
        assertEquals(RequestOutcome.REJECTED_BUSY, submit(CombatRequest.LIGHT));
        assertEquals(RequestOutcome.REJECTED_BUSY, submit(CombatRequest.SKILL));
    }

    // ------------------------------------------------------------------ dodge

    @Test
    public void dodgeGivesInvulnerabilityAndCostsStamina() {
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.DODGE));
        assertEquals(75.0, fighter.stamina(), EPS);
        runTo(107);
        assertEquals(AvoidOutcome.DODGED, fighter.checkAvoid(now, true, false));
        assertEquals("non-evadable damage (fall, fire) is not dodged", AvoidOutcome.NONE,
                fighter.checkAvoid(now, false, false));
        runTo(108);
        assertEquals(AvoidOutcome.NONE, fighter.checkAvoid(now, true, false));
    }

    @Test
    public void dodgeIsRejectedWithoutStaminaOrWhenMobilityIsRestricted() {
        assertEquals(RequestOutcome.REJECTED_NOT_ALLOWED, fighter.submit(CombatRequest.DODGE, now, SWORD, true));
        fighter.setStamina(24.0);
        assertEquals(RequestOutcome.REJECTED_STAMINA, submit(CombatRequest.DODGE));
    }

    @Test
    public void dodgeCannotCutAWindupButCancelsTheRecovery() {
        submit(CombatRequest.LIGHT);
        runTo(101);
        assertEquals(RequestOutcome.BUFFERED, submit(CombatRequest.DODGE));
        runTo(120);
        assertEquals(106, starts().get(1).tick());

        events.clear();
        submit(CombatRequest.LIGHT); // hit at 123
        runTo(124);
        assertEquals("after the last hit the recovery can be dodge-cancelled", RequestOutcome.ACCEPTED,
                submit(CombatRequest.DODGE));
    }

    @Test
    public void dodgeChainsOnlyAfterItsCancelPoint() {
        submit(CombatRequest.DODGE);
        runTo(105);
        assertEquals(RequestOutcome.BUFFERED, submit(CombatRequest.DODGE));
        runTo(130);
        assertEquals(110, starts().get(1).tick());
        assertEquals(50.0 + 6.0, fighter.stamina(), EPS); // second dodge at 110, regenerating on ticks 125..130
    }

    @Test
    public void dodgeCancelsACharge() {
        submit(CombatRequest.HEAVY_START);
        runTo(105);
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.DODGE));
        assertEquals(RequestOutcome.IGNORED, submit(CombatRequest.HEAVY_RELEASE));
        runTo(150);
        assertTrue("the cancelled charge never strikes", hits().isEmpty());
    }

    // ------------------------------------------------------------------ skill

    @Test
    public void skillStartsItsCooldownAndIsRejectedUntilReady() {
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.SKILL));
        assertEquals(160, fighter.skillCooldownRemaining(now));
        assertEquals(160, fighter.skillCooldownTotal());
        runTo(125);
        assertEquals(RequestOutcome.REJECTED_COOLDOWN, submit(CombatRequest.SKILL));
        runTo(259);
        assertEquals(RequestOutcome.REJECTED_COOLDOWN, submit(CombatRequest.SKILL));
        runTo(260);
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.SKILL));
    }

    @Test
    public void skillNeedsStaminaAndCanCancelALightRecovery() {
        submit(CombatRequest.LIGHT);
        runTo(101);
        assertEquals(RequestOutcome.BUFFERED, submit(CombatRequest.SKILL));
        runTo(103);
        events.clear();
        fighter = new Combatant(BASE);
        fighter.tick(now);
        submit(CombatRequest.LIGHT); // hit at 106, cancel point 109
        runTo(107);
        assertEquals("after the last hit a skill cancels the recovery", RequestOutcome.ACCEPTED,
                submit(CombatRequest.SKILL));

        Combatant tired = new Combatant(BASE);
        tired.tick(now);
        tired.setStamina(24.0);
        assertEquals(RequestOutcome.REJECTED_STAMINA, tired.submit(CombatRequest.SKILL, now, SWORD, false));
    }

    // ------------------------------------------------------------------ guard, parry, stagger

    @Test
    public void guardBlocksAttacksSkillsAndChargesButNotDodges() {
        assertTrue(fighter.startGuard(now, SWORD));
        assertEquals(RequestOutcome.REJECTED_GUARDING, submit(CombatRequest.LIGHT));
        assertEquals(RequestOutcome.REJECTED_GUARDING, submit(CombatRequest.HEAVY_START));
        assertEquals(RequestOutcome.REJECTED_GUARDING, submit(CombatRequest.SKILL));
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.DODGE));
        assertFalse("dodging drops the guard", fighter.isGuarding());
    }

    @Test
    public void guardCannotInterruptAWindupButCancelsTheRecovery() {
        submit(CombatRequest.LIGHT);
        runTo(101);
        assertFalse(fighter.startGuard(now, SWORD));
        runTo(104); // hit at 103 fired
        assertTrue(fighter.startGuard(now, SWORD));
        assertTrue(fighter.currentAction().isEmpty());
        runTo(120);
        assertEquals(1, hits().size());
    }

    @Test
    public void attackInsideTheParryWindowIsParried() {
        fighter.setStamina(50.0);
        fighter.startGuard(now, SWORD);
        runTo(104);
        assertEquals(AvoidOutcome.NONE, fighter.checkAvoid(now, true, false)); // behind / not guardable
        assertEquals(AvoidOutcome.PARRIED, fighter.checkAvoid(now, true, true));
        assertEquals(65.0 + 4.0 * 0.5, fighter.stamina(), EPS); // refund 15 (+ guarded regen of 4 ticks)
        runTo(105);
        assertEquals("the window is 5 ticks", AvoidOutcome.NONE, fighter.checkAvoid(now, true, true));
    }

    @Test
    public void parryGrantsOneRiposte() {
        fighter.startGuard(now, SWORD);
        runTo(102);
        fighter.checkAvoid(now, true, true);
        fighter.stopGuard();
        runTo(110);
        submit(CombatRequest.LIGHT);
        runTo(116);
        submit(CombatRequest.LIGHT);
        runTo(130);
        List<CombatEvent.Hit> hits = hits();
        assertEquals(CombatRules.RIPOSTE_MULTIPLIER, hits.get(0).damageScale(), EPS);
        assertEquals("the riposte is used up by one action", 1.0, hits.get(1).damageScale(), EPS);
    }

    @Test
    public void riposteExpires() {
        fighter.startGuard(now, SWORD);
        runTo(102);
        fighter.checkAvoid(now, true, true);
        fighter.stopGuard();
        runTo(102 + CombatRules.RIPOSTE_WINDOW_TICKS);
        submit(CombatRequest.LIGHT);
        runTo(150);
        assertEquals(1.0, hits().get(0).damageScale(), EPS);
    }

    @Test
    public void guardReducesDamageForStamina() {
        fighter.startGuard(now, SWORD);
        runTo(110);
        GuardResult result = fighter.applyGuard(now, true, 10.0);
        assertEquals(GuardResult.Outcome.GUARDED, result.outcome());
        assertEquals(4.0, result.amount(), EPS); // 60% blocked
        assertEquals(100.0 - 6.0 * 3.0, fighter.stamina(), EPS);

        GuardResult behind = fighter.applyGuard(now, false, 10.0);
        assertEquals(GuardResult.Outcome.NONE, behind.outcome());
        assertEquals(10.0, behind.amount(), EPS);
    }

    @Test
    public void guardBreaksWhenStaminaRunsOut() {
        fighter.startGuard(now, SWORD);
        runTo(110);
        fighter.setStamina(9.0);
        GuardResult result = fighter.applyGuard(now, true, 10.0);
        assertEquals(GuardResult.Outcome.GUARD_BROKEN, result.outcome());
        assertEquals(10.0 - 3.0, result.amount(), EPS); // 9 stamina covered 3 damage
        assertEquals(0.0, fighter.stamina(), EPS);
        assertFalse(fighter.isGuarding());
        assertEquals(Combatant.Phase.STAGGERED, fighter.phase(now));
        assertEquals(RequestOutcome.REJECTED_STAGGERED, submit(CombatRequest.LIGHT));
        assertFalse(fighter.canStartGuard(now, SWORD));
        runTo(110 + CombatRules.GUARD_BREAK_STAGGER_TICKS);
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.LIGHT));
    }

    @Test
    public void staggerInterruptsTheCurrentAction() {
        submit(CombatRequest.LIGHT);
        runTo(101);
        fighter.stagger(now, 10);
        runTo(130);
        assertTrue(hits().isEmpty());
    }

    // ------------------------------------------------------------------ stamina, stats, reset

    @Test
    public void staminaRegeneratesAfterTheDelaySlowerWhileGuardingAndNeverAboveMax() {
        submit(CombatRequest.DODGE); // 75 at 100
        runTo(114);
        assertEquals(75.0, fighter.stamina(), EPS);
        runTo(115);
        assertEquals(76.0, fighter.stamina(), EPS);
        runTo(130); // dodge over; guard while regenerating
        fighter.startGuard(now, SWORD);
        double before = fighter.stamina();
        runTo(132);
        assertEquals(before + 1.0, fighter.stamina(), EPS);
        fighter.stopGuard();
        runTo(500);
        assertEquals(100.0, fighter.stamina(), EPS);
    }

    @Test
    public void lowerMaxStaminaClampsTheCurrentValue() {
        fighter.setStats(new CombatStats(1.0, 0.0, 0.0, 1.0, 0.0, 1.5, 40.0, 1.0));
        assertEquals(40.0, fighter.stamina(), EPS);
    }

    @Test
    public void resetRestoresAFreshStateButKeepsTheSkillCooldown() {
        submit(CombatRequest.SKILL);
        runTo(101);
        fighter.stagger(now, 50);
        fighter.reset();
        assertEquals(100.0, fighter.stamina(), EPS);
        assertEquals(Combatant.Phase.IDLE, fighter.phase(now));
        assertTrue(fighter.skillCooldownRemaining(now) > 0);
        assertEquals(RequestOutcome.ACCEPTED, submit(CombatRequest.LIGHT));
    }

    @Test
    public void startedActionsReportTheirWeapon() {
        submit(CombatRequest.LIGHT);
        runTo(101);
        assertSame(SWORD, starts().get(0).weapon());
        runTo(110);
        assertSame(SWORD, hits().get(0).weapon());
    }
}
