package com.eternalrealm.combat;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CombatServiceTest {
    private static final CombatStats A = new CombatStats(1, 0, 0, 1, 0, 1.5, 100, 1);
    private static final CombatStats B = new CombatStats(2, 0, 0, 1, 0, 1.5, 50, 1);

    @Test
    public void registerKeepsStateAndUpdatesStats() {
        CombatService service = new CombatService();
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        Combatant first = service.register(one, A, 0);
        first.submit(CombatRequest.DODGE, 0, WeaponProfiles.SPEAR, false, false);
        Combatant again = service.register(one, B, 0);
        assertSame(first, again);
        assertEquals(B, again.stats());
        assertEquals("stamina clamped to the new maximum", 50.0, again.stamina(), 0.0);
        assertNotSame(first, service.register(two, A, 0));
        assertEquals(2, service.size());
        assertTrue(service.get(two).isPresent());

        service.remove(one, 0);
        assertFalse(service.get(one).isPresent());
        assertEquals(1, service.all().size());
        assertThrows(UnsupportedOperationException.class, () -> service.all().clear());
    }

    @Test
    public void skillCooldownSurvivesLeavingAndRejoining() {
        CombatService service = new CombatService();
        UUID id = UUID.randomUUID();
        Combatant fighter = service.register(id, A, 100);
        fighter.submit(CombatRequest.SKILL, 100, WeaponProfiles.ONE_HANDED_SWORD, false, false); // 160 ticks
        service.remove(id, 110);
        Combatant back = service.register(id, A, 150);
        assertNotSame(fighter, back);
        assertEquals(110, back.skillCooldownRemaining(150));
        assertEquals(160, back.skillCooldownTotal());

        service.remove(id, 300); // expired by then: nothing to remember
        assertEquals(0, service.register(id, A, 301).skillCooldownRemaining(301));
    }
}
