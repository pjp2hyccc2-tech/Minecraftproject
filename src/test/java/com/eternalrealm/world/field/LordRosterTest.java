package com.eternalrealm.world.field;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Two lords of a field out at a time; every 20 minutes the line-up changes and brings one that was resting. */
public class LordRosterTest {
    @Test
    public void everyFieldHasThreeLordsOfWhichTwoAreOut() {
        assertEquals(2, LordRoster.PER_FIELD);
        assertEquals(20L * 60L * 20L, LordRoster.ROTATION_TICKS);
        SplittableRandom random = new SplittableRandom(1);
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            List<String> lords = ids(field);
            assertEquals("field " + field, 3, lords.size());
            List<String> out = LordRoster.first(lords, random);
            assertEquals(LordRoster.PER_FIELD, out.size());
            assertEquals(LordRoster.PER_FIELD, new HashSet<>(out).size());
            assertTrue(lords.containsAll(out));
        }
    }

    @Test
    public void everyChangeBringsTheRestingLordAndKeepsTwoOut() {
        List<String> lords = List.of("a", "b", "c");
        SplittableRandom random = new SplittableRandom(7);
        List<String> out = LordRoster.first(lords, random);
        Map<String, Integer> turnsOut = new HashMap<>();
        for (int change = 0; change < 3000; change++) {
            List<String> next = LordRoster.next(lords, out, random);
            assertEquals(LordRoster.PER_FIELD, next.size());
            assertEquals(LordRoster.PER_FIELD, new HashSet<>(next).size());
            for (String id : lords) {
                if (!out.contains(id)) {
                    assertTrue("the resting lord " + id + " comes out", next.contains(id));
                }
            }
            assertFalse("the line-up changes", new HashSet<>(next).equals(new HashSet<>(out)));
            next.forEach(id -> turnsOut.merge(id, 1, Integer::sum));
            out = next;
        }
        // every lord gets its turns: about two changes in three each
        for (String id : lords) {
            assertEquals(id, 2000.0, turnsOut.getOrDefault(id, 0), 120.0);
        }
    }

    @Test
    public void withMoreLordsTheWholeLineUpIsNew() {
        List<String> lords = List.of("a", "b", "c", "d", "e");
        SplittableRandom random = new SplittableRandom(3);
        List<String> out = List.of("a", "b");
        for (int change = 0; change < 200; change++) {
            List<String> next = LordRoster.next(lords, out, random);
            assertEquals(2, next.size());
            for (String id : next) {
                assertFalse("only lords that were resting: " + next + " after " + out, out.contains(id));
            }
            out = next;
        }
        // a field with fewer lords than places keeps them all out; lords not of the field are dropped
        assertEquals(Set.of("x"), new HashSet<>(LordRoster.next(List.of("x"), List.of("x", "elsewhere"), random)));
        assertEquals(List.of(), LordRoster.first(List.of(), random));
    }

    @Test
    public void aLordInAFightStaysUntilTheFightIsOver() {
        long hit = 1000L;
        assertTrue(LordRoster.engaged(hit, hit));
        assertTrue(LordRoster.engaged(hit, hit + LordRoster.ENGAGED_TICKS - 1));
        assertFalse(LordRoster.engaged(hit, hit + LordRoster.ENGAGED_TICKS));
        assertFalse("never hit", LordRoster.engaged(-1L, 5L));
    }

    private static List<String> ids(int field) {
        List<String> ids = new ArrayList<>();
        for (FieldThreats.Lord lord : FieldThreats.lordsOf(field)) {
            ids.add(lord.id());
        }
        return ids;
    }
}
