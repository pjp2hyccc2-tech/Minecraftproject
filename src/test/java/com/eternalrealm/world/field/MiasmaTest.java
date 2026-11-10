package com.eternalrealm.world.field;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MiasmaTest {
    @Test
    public void killsFillTheGaugeAndAnOmenFollows() {
        Miasma miasma = new Miasma();
        long now = 100;
        for (int i = 0; i < 124; i++) {
            assertTrue(miasma.addKill(1, Miasma.NORMAL_KILL, now).isEmpty());
        }
        assertEquals(992, miasma.gauge(1));
        List<Miasma.Event> events = miasma.addKill(1, Miasma.NORMAL_KILL, now);
        assertEquals(List.of(new Miasma.Event(Miasma.Event.Kind.OMEN, 1)), events);
        assertEquals(Miasma.Phase.OMEN, miasma.phase(1));
        assertEquals(Miasma.MAX, miasma.gauge(1));
        // kills during the omen change nothing
        assertTrue(miasma.addKill(1, Miasma.LORD_KILL, now).isEmpty());
        assertEquals(Miasma.OMEN_TICKS, miasma.remaining(1, now));
        assertTrue(miasma.tick(now + Miasma.OMEN_TICKS - 1).isEmpty());
        assertEquals(List.of(new Miasma.Event(Miasma.Event.Kind.MANIFEST, 1)), miasma.tick(now + Miasma.OMEN_TICKS));
        assertEquals(Miasma.Phase.MANIFEST, miasma.phase(1));
        // other fields are untouched
        assertEquals(Miasma.Phase.CALM, miasma.phase(2));
    }

    @Test
    public void subduingPurifiesTheField() {
        Miasma miasma = manifested(3, 0);
        long now = Miasma.OMEN_TICKS;
        assertEquals(List.of(new Miasma.Event(Miasma.Event.Kind.PURIFIED, 3)), miasma.subdued(3, now + 50));
        assertEquals(Miasma.Phase.PURIFIED, miasma.phase(3));
        assertEquals(0, miasma.gauge(3));
        assertEquals(Miasma.PURIFIED_EXP_MULTIPLIER, miasma.expMultiplier(3), 1e-9);
        assertEquals(1.0, miasma.expMultiplier(4), 1e-9);
        assertTrue(miasma.addKill(3, Miasma.ELITE_KILL, now + 60).isEmpty());
        assertEquals(0, miasma.gauge(3));
        assertEquals(List.of(new Miasma.Event(Miasma.Event.Kind.CALM, 3)),
                miasma.tick(now + 50 + Miasma.PURIFIED_TICKS));
        assertEquals(Miasma.Phase.CALM, miasma.phase(3));
    }

    @Test
    public void anUnfoughtCalamityDeparts() {
        Miasma miasma = manifested(5, 0);
        long manifestedAt = Miasma.OMEN_TICKS;
        miasma.engaged(5, manifestedAt + 100);
        assertTrue(miasma.tick(manifestedAt + 100 + Miasma.IDLE_DEPART_TICKS - 1).isEmpty());
        assertEquals(List.of(new Miasma.Event(Miasma.Event.Kind.DEPART, 5)),
                miasma.tick(manifestedAt + 100 + Miasma.IDLE_DEPART_TICKS));
        assertEquals(Miasma.Phase.CALM, miasma.phase(5));
        assertEquals(Miasma.AFTER_DEPARTURE, miasma.gauge(5));
    }

    @Test
    public void aCalamityDepartsAfterItsTimeEvenWhenFought() {
        Miasma miasma = manifested(6, 0);
        long manifestedAt = Miasma.OMEN_TICKS;
        for (long t = manifestedAt; t < manifestedAt + Miasma.MANIFEST_LIMIT_TICKS; t += 20 * 60) {
            miasma.engaged(6, t);
            assertTrue(miasma.tick(t).isEmpty());
        }
        assertEquals(List.of(new Miasma.Event(Miasma.Event.Kind.DEPART, 6)),
                miasma.tick(manifestedAt + Miasma.MANIFEST_LIMIT_TICKS));
    }

    @Test
    public void miasmaSeepsInSlowly() {
        Miasma miasma = new Miasma();
        miasma.tick(1);
        miasma.tick(1 + Miasma.SEEP_TICKS * 10L);
        assertEquals(10, miasma.gauge(7));
    }

    @Test
    public void gaugesSurviveARestartButPhasesDoNot() {
        Miasma miasma = manifested(2, 0);
        miasma.addKill(4, 333, 10);
        Miasma restored = Miasma.fromJson(miasma.toJson());
        assertEquals(800, restored.gauge(2));
        assertEquals(333, restored.gauge(4));
        assertEquals(Miasma.Phase.CALM, restored.phase(2));
        assertEquals(0, Miasma.fromJson("nonsense").gauge(1));
    }

    @Test
    public void everyFieldHasTwoLordsAndOneCalamity() {
        Set<String> ids = new HashSet<>();
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            assertEquals(2, FieldThreats.lordsOf(field).size());
            assertTrue(FieldThreats.calamityOf(field).isPresent());
        }
        for (FieldThreats.Lord lord : FieldThreats.lords()) {
            assertTrue(lord.id(), ids.add(lord.id()));
            assertEquals(lord.id() + " stands in its field", lord.field(), FieldLayout.areaAt(lord.x(), lord.z()));
            assertFalse(FieldLayout.inCapital(lord.x(), lord.z()));
            int ticks = FieldThreats.respawnTicks(lord, new SplittableRandom(1));
            assertTrue(ticks >= lord.respawnMinTicks() && ticks <= lord.respawnMaxTicks());
        }
        for (FieldThreats.Calamity calamity : FieldThreats.calamities()) {
            assertEquals(calamity.id() + " lair in its field", calamity.field(),
                    FieldLayout.areaAt(calamity.x(), calamity.z()));
        }
    }

    private static Miasma manifested(int field, long start) {
        Miasma miasma = new Miasma();
        miasma.addKill(field, Miasma.MAX, start);
        miasma.tick(start + Miasma.OMEN_TICKS);
        assertEquals(Miasma.Phase.MANIFEST, miasma.phase(field));
        return miasma;
    }
}
