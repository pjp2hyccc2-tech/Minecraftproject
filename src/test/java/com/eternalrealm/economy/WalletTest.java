package com.eternalrealm.economy;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WalletTest {
    @Test
    public void creditAndDebitReturnNewWallets() {
        Wallet wallet = Wallet.of(100);
        WalletResult credited = wallet.credit(50);
        assertTrue(credited.isSuccess());
        assertEquals(150, credited.wallet().balance());
        assertEquals("the original is unchanged", 100, wallet.balance());
        WalletResult debited = credited.wallet().debit(150);
        assertEquals(Wallet.EMPTY, debited.wallet());
        assertSame(Wallet.EMPTY, Wallet.of(0));
        assertEquals(wallet, wallet.credit(0).wallet());
        assertEquals(wallet, wallet.debit(0).wallet());
    }

    @Test
    public void debitNeverGoesNegative() {
        Wallet wallet = Wallet.of(10);
        WalletResult result = wallet.debit(11);
        assertEquals(WalletResult.Status.INSUFFICIENT_FUNDS, result.status());
        assertSame(wallet, result.wallet());
        assertEquals(WalletResult.Status.INSUFFICIENT_FUNDS, Wallet.EMPTY.debit(Long.MAX_VALUE).status());
        assertTrue(wallet.canAfford(10));
        assertFalse(wallet.canAfford(11));
        assertFalse(wallet.canAfford(-1));
    }

    @Test
    public void creditNeverExceedsTheMaximumOrWraps() {
        Wallet nearlyFull = Wallet.of(Wallet.MAX_BALANCE - 5);
        assertEquals(5, nearlyFull.capacity());
        assertEquals(Wallet.MAX_BALANCE, nearlyFull.credit(5).wallet().balance());
        WalletResult over = nearlyFull.credit(6);
        assertEquals(WalletResult.Status.OVERFLOW, over.status());
        assertSame(nearlyFull, over.wallet());
        assertEquals("no long overflow", WalletResult.Status.OVERFLOW, nearlyFull.credit(Long.MAX_VALUE).status());
        assertEquals(0, Wallet.of(Wallet.MAX_BALANCE).capacity());
    }

    @Test
    public void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Wallet(-1));
        assertThrows(IllegalArgumentException.class, () -> new Wallet(Wallet.MAX_BALANCE + 1));
        assertThrows(IllegalArgumentException.class, () -> Wallet.of(5).credit(-1));
        assertThrows(IllegalArgumentException.class, () -> Wallet.of(5).debit(-1));
        assertThrows(NullPointerException.class, () -> new WalletResult(null, Wallet.EMPTY));
    }

    @Test
    public void failuresHaveMessages() {
        assertEquals(Optional.empty(), WalletResult.Status.OK.messageKey());
        assertEquals(Optional.of("message.eternalrealm.wallet.overflow"), WalletResult.Status.OVERFLOW.messageKey());
    }
}
