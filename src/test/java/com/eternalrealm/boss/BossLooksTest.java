package com.eternalrealm.boss;

import com.eternalrealm.combat.Telegraph;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Looks of the shipped abilities, their ground markers and the defeat sequence. */
public class BossLooksTest {
    @Test
    public void everyShippedAbilityHasItsOwnLook() {
        for (BossDefinition boss : Bosses.everyBoss()) {
            for (BossAbility ability : boss.abilities()) {
                AbilityLook look = AbilityLook.defined(ability.id()).orElse(null);
                assertTrue(ability.id() + " has no look", look != null);
                if (ability.kind() instanceof AbilityKind.Soak) {
                    assertEquals(ability.id() + ": stack circles are gold", Telegraph.Style.HOLY,
                            look.styleFor(ability.kind()));
                }
                assertTrue(ability.id() + ": green only marks safety", look.style() != Telegraph.Style.SAFE);
            }
        }
        assertTrue(AbilityLook.defined("nope").isEmpty());
        AbilityLook pull = AbilityLook.of(Bosses.B1.ability("b1_greedy_grab").orElseThrow());
        assertEquals("pulls are drawn violet", Telegraph.Style.MAGIC, pull.styleFor(new AbilityKind.Knockback(5, -1)));
        assertEquals(Telegraph.Style.DANGER, pull.styleFor(new AbilityKind.CircleAroundBoss(4)));
        assertThrows(IllegalArgumentException.class, () -> new AbilityLook(Telegraph.Style.FIRE,
                AbilityLook.Effect.METEOR, null));
        assertThrows(IllegalArgumentException.class, () -> new AbilityLook(Telegraph.Style.FIRE,
                AbilityLook.Effect.FIRE, "minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> new AbilityLook(Telegraph.Style.FIRE,
                AbilityLook.Effect.SPIKES, "Stone Block"));
    }

    @Test
    public void everyShapeOfEveryBossBecomesAMarkerThatMatchesIt() {
        for (BossDefinition boss : Bosses.everyBoss()) {
            BossBrain brain = new BossBrain(boss, Math.max(2, boss.minPlayers()), Difficulty.EX, new Random(9));
            brain.setArena(new Point(0, 64, 0), boss == Bosses.FINAL ? 25.5 : 14.5);
            double hp = 1.0;
            for (long t = 0; t < 12_000 && !brain.isDead(); t++) {
                hp = Math.max(0.0, hp - 1.0 / 10_000.0);
                List<TargetSnapshot> targets = List.of(BossFixtures.target(1, 5, 3), BossFixtures.target(2, -4, 8),
                        BossFixtures.target(3, 9, -6));
                for (BossCommand command : brain.tick(t, hp, BossFixtures.AT_ORIGIN, targets)) {
                    if (command instanceof BossCommand.Telegraph telegraph) {
                        AbilityLook look = AbilityLook.of(telegraph.ability());
                        Telegraph marker = BossMarkers.marker(look.styleFor(telegraph.hitKind()), telegraph.shape(),
                                telegraph.durationTicks(), 22);
                        assertEquals(telegraph.durationTicks(), marker.durationTicks());
                        assertSame(telegraph.abilityId(), telegraph.shape(), marker);
                    } else if (command instanceof BossCommand.SafeSpot spot) {
                        Telegraph marker = BossMarkers.safe(spot.shape(), spot.durationTicks());
                        assertEquals(Telegraph.Style.SAFE, marker.style());
                        assertSame("safe", spot.shape(), marker);
                    }
                }
            }
        }
    }

    /** Points inside the shape are inside the marker and the other way round (sampled on a grid). */
    private static void assertSame(String label, BossShape shape, Telegraph marker) {
        if (shape instanceof BossShape.Arena || shape instanceof BossShape.Outside) {
            assertEquals(label, Telegraph.Kind.CIRCLE, marker.kind());
            return;
        }
        Point anchor = shape.anchor();
        int agree = 0;
        int total = 0;
        for (double dx = -30; dx <= 30; dx += 0.75) {
            for (double dz = -30; dz <= 30; dz += 0.75) {
                double x = anchor.x() + dx + 0.13;
                double z = anchor.z() + dz + 0.07;
                total++;
                if (shape.contains(x, anchor.y(), z) == marker.contains(x, anchor.y(), z)) {
                    agree++;
                }
            }
        }
        // strips have rounded caps in the shape and square ends in the marker: allow a sliver of difference
        assertTrue(label + ": marker differs from the area " + shape + " (" + agree + "/" + total + ")",
                agree >= total * 0.995);
    }

    @Test
    public void defeatSequencesCollapseThenDissolveThenVanish() {
        for (DefeatSequence sequence : List.of(DefeatSequence.REGULAR, DefeatSequence.FINAL)) {
            List<DefeatSequence.Kind> kinds = sequence.beats().stream().map(DefeatSequence.Beat::kind).toList();
            assertEquals(DefeatSequence.Kind.COLLAPSE, kinds.get(0));
            assertEquals(DefeatSequence.Kind.VANISH, kinds.get(kinds.size() - 1));
            assertTrue(kinds.contains(DefeatSequence.Kind.PILLAR) && kinds.contains(DefeatSequence.Kind.SHOCKWAVE)
                    && kinds.contains(DefeatSequence.Kind.BURST));
            assertTrue("several flashes", kinds.stream().filter(k -> k == DefeatSequence.Kind.FLASH).count() >= 4);
            assertTrue(sequence.dissolveTick() < sequence.lengthTicks());
            assertEquals(List.of(DefeatSequence.Kind.VANISH),
                    sequence.at(sequence.lengthTicks()).stream().map(DefeatSequence.Beat::kind).toList());
        }
        assertTrue("dungeon bosses collapse for 3 to 5 seconds", DefeatSequence.REGULAR.lengthTicks() >= 60
                && DefeatSequence.REGULAR.lengthTicks() <= 100);
        assertTrue("the King of the End takes longer", DefeatSequence.FINAL.lengthTicks()
                > DefeatSequence.REGULAR.lengthTicks());
        assertEquals(DefeatSequence.FINAL, DefeatSequence.of("final"));
        assertEquals(DefeatSequence.REGULAR, DefeatSequence.of("b3"));
        assertFalse(DefeatSequence.REGULAR.at(1).stream().anyMatch(b -> b.kind() == DefeatSequence.Kind.VANISH));
        assertThrows(IllegalArgumentException.class, () -> new DefeatSequence(10, List.of(
                new DefeatSequence.Beat(5, DefeatSequence.Kind.VANISH, 1))));
    }
}
