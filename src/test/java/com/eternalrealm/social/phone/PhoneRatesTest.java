package com.eternalrealm.social.phone;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneRatesTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    public void aBurstThenItRefills() {
        PhoneRates rates = new PhoneRates();
        long now = 1_000_000L;
        for (int i = 0; i < PhoneRates.Action.POST.burst(); i++) {
            assertTrue(rates.tryUse(A, PhoneRates.Action.POST, now));
        }
        assertFalse(rates.tryUse(A, PhoneRates.Action.POST, now));
        assertTrue("someone else is not held back", rates.tryUse(B, PhoneRates.Action.POST, now));
        assertTrue(rates.tryUse(A, PhoneRates.Action.POST, now + 30_000L));
    }

    @Test
    public void theDailyCapHoldsUntilTomorrow() {
        PhoneRates rates = new PhoneRates();
        long day = 86_400_000L;
        long now = 10 * day;
        int used = 0;
        for (int i = 0; i < 1000 && rates.tryUse(A, PhoneRates.Action.PHOTO, now + i * 20_000L); i++) {
            used++;
        }
        assertTrue(used == PhoneRates.Action.PHOTO.perDay());
        assertTrue(rates.dailyCapReached(A, PhoneRates.Action.PHOTO, now + used * 20_000L));
        assertTrue(rates.tryUse(A, PhoneRates.Action.PHOTO, 11 * day + 1));
    }
}
