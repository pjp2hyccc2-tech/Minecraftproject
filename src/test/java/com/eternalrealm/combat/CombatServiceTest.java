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
        Combatant first = service.register(one, A);
        first.submit(CombatRequest.DODGE, 0, WeaponProfiles.SPEAR, false);
        Combatant again = service.register(one, B);
        assertSame(first, again);
        assertEquals(B, again.stats());
        assertEquals("stamina clamped to the new maximum", 50.0, again.stamina(), 0.0);
        assertNotSame(first, service.register(two, A));
        assertEquals(2, service.size());
        assertTrue(service.get(two).isPresent());

        service.remove(one);
        assertFalse(service.get(one).isPresent());
        assertEquals(1, service.all().size());
        assertThrows(UnsupportedOperationException.class, () -> service.all().clear());
    }
}
