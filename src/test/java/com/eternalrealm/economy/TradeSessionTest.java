package com.eternalrealm.economy;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TradeSessionTest {
    private static final UUID ALICE = new UUID(3L, 1L);
    private static final UUID BOB = new UUID(3L, 2L);
    private static final UUID EVE = new UUID(3L, 3L);

    /** Item handles: inventory slot numbers. */
    private final TradeSession<Integer> trade = new TradeSession<>(ALICE, BOB);

    private void lockAndConfirmBoth() {
        assertEquals(TradeOutcome.OK, trade.lock(ALICE, trade.revision()));
        assertEquals(TradeOutcome.OK, trade.lock(BOB, trade.revision()));
        assertEquals(TradeOutcome.OK, trade.confirm(ALICE, trade.revision()));
        assertEquals(TradeOutcome.OK, trade.confirm(BOB, trade.revision()));
    }

    @Test
    public void bothSidesLockThenConfirmThenTheIntegrationSettles() {
        assertEquals(TradeOutcome.OK, trade.addItem(ALICE, 3));
        assertEquals(TradeOutcome.OK, trade.addItem(ALICE, 7));
        assertEquals(TradeOutcome.OK, trade.setGold(BOB, 500));
        assertEquals(TradeSession.State.OPEN, trade.state());

        assertEquals(TradeOutcome.OK, trade.lock(ALICE, trade.revision()));
        assertEquals(TradeSession.Stage.LOCKED, trade.stage(ALICE));
        assertEquals(TradeOutcome.NOT_ALL_LOCKED, trade.confirm(ALICE, trade.revision()));
        assertEquals(TradeOutcome.OK, trade.lock(BOB, trade.revision()));
        assertEquals(TradeOutcome.OK, trade.confirm(ALICE, trade.revision()));
        assertEquals(TradeSession.Stage.CONFIRMED, trade.stage(ALICE));
        assertEquals(TradeSession.State.OPEN, trade.state());
        assertFalse(trade.settlement().isPresent());
        assertEquals(TradeOutcome.OK, trade.confirm(BOB, trade.revision()));
        assertEquals(TradeSession.State.SETTLING, trade.state());

        TradeSettlement<Integer> settlement = trade.settlement().orElseThrow();
        assertEquals(new TradeOffer<>(List.of(3, 7), 0), settlement.givenBy(ALICE));
        assertEquals(new TradeOffer<>(List.of(), 500), settlement.givenBy(BOB));
        assertEquals(settlement.givenBy(BOB), settlement.receivedBy(ALICE));
        assertEquals(settlement.givenBy(ALICE), settlement.receivedBy(BOB));
        assertEquals(TradeOutcome.NOT_OPEN, trade.addItem(BOB, 1));
        assertEquals(TradeOutcome.NOT_OPEN, trade.unlock(ALICE));

        assertEquals(TradeOutcome.OK, trade.complete());
        assertEquals(TradeSession.State.COMPLETED, trade.state());
        assertTrue(trade.state().isEnded());
        assertEquals(Optional.empty(), trade.endReason());
        assertFalse(trade.settlement().isPresent());
        assertEquals(TradeOutcome.NOT_SETTLING, trade.complete());
        assertEquals(TradeOutcome.NOT_OPEN, trade.cancel(TradeEndReason.TIMEOUT));
    }

    @Test
    public void anyOfferChangeClearsBothLocksAndConfirmations() {
        trade.addItem(ALICE, 1);
        trade.lock(ALICE, trade.revision());
        trade.lock(BOB, trade.revision());
        trade.confirm(ALICE, trade.revision());
        long before = trade.revision();

        assertEquals(TradeOutcome.OK, trade.setGold(BOB, 10));
        assertEquals(before + 1, trade.revision());
        assertEquals(TradeSession.Stage.NEGOTIATING, trade.stage(ALICE));
        assertEquals(TradeSession.Stage.NEGOTIATING, trade.stage(BOB));

        trade.lock(ALICE, trade.revision());
        trade.lock(BOB, trade.revision());
        assertEquals(TradeOutcome.OK, trade.removeItem(ALICE, 1));
        assertEquals(TradeSession.Stage.NEGOTIATING, trade.stage(BOB));
        trade.lock(BOB, trade.revision());
        assertEquals(TradeOutcome.OK, trade.addItem(BOB, 4));
        assertEquals(TradeSession.Stage.NEGOTIATING, trade.stage(BOB));
    }

    @Test
    public void settingTheSameGoldAgainChangesNothing() {
        trade.setGold(ALICE, 10);
        trade.lock(BOB, trade.revision());
        long revision = trade.revision();
        assertEquals(TradeOutcome.OK, trade.setGold(ALICE, 10));
        assertEquals(revision, trade.revision());
        assertEquals(TradeSession.Stage.LOCKED, trade.stage(BOB));
    }

    @Test
    public void lockAndConfirmMustReferToTheCurrentOffers() {
        trade.addItem(ALICE, 1);
        long seenByBob = trade.revision();
        trade.addItem(ALICE, 2); // swapped right before Bob's click
        assertEquals(TradeOutcome.OFFER_CHANGED, trade.lock(BOB, seenByBob));
        assertEquals(TradeSession.Stage.NEGOTIATING, trade.stage(BOB));
        trade.lock(ALICE, trade.revision());
        trade.lock(BOB, trade.revision());
        assertEquals(TradeOutcome.OFFER_CHANGED, trade.confirm(BOB, seenByBob));
        assertEquals(TradeSession.Stage.LOCKED, trade.stage(BOB));
    }

    @Test
    public void unlockingWithdrawsThePartnersConfirmation() {
        trade.lock(ALICE, 0);
        trade.lock(BOB, 0);
        trade.confirm(BOB, 0);
        assertEquals(TradeOutcome.OK, trade.unlock(ALICE));
        assertEquals(TradeSession.Stage.NEGOTIATING, trade.stage(ALICE));
        assertEquals(TradeSession.Stage.LOCKED, trade.stage(BOB));
        assertEquals(TradeOutcome.OK, trade.lock(BOB, 0));
        assertEquals("locking again keeps the lock", TradeSession.Stage.LOCKED, trade.stage(BOB));
    }

    @Test
    public void offersAreLimitedAndValidated() {
        for (int slot = 0; slot < TradeSession.MAX_ITEMS_PER_SIDE; slot++) {
            assertEquals(TradeOutcome.OK, trade.addItem(ALICE, slot));
        }
        assertEquals(TradeOutcome.TOO_MANY_ITEMS, trade.addItem(ALICE, 99));
        assertEquals(TradeOutcome.DUPLICATE_ITEM, trade.addItem(ALICE, 0));
        assertEquals(TradeOutcome.NO_SUCH_ITEM, trade.removeItem(BOB, 0));
        assertEquals(TradeOutcome.INVALID_GOLD, trade.setGold(BOB, -1));
        assertEquals(TradeOutcome.INVALID_GOLD, trade.setGold(BOB, Wallet.MAX_BALANCE + 1));
        assertEquals(TradeOutcome.OK, trade.setGold(BOB, Wallet.MAX_BALANCE));
        assertEquals(TradeOutcome.NOT_PARTICIPANT, trade.addItem(EVE, 1));
        assertEquals(TradeOutcome.NOT_PARTICIPANT, trade.lock(EVE, 0));
        assertEquals(TradeOutcome.NOT_PARTICIPANT, trade.confirm(EVE, 0));
        assertEquals(9, trade.offerOf(ALICE).items().size());
        assertThrows(NullPointerException.class, () -> trade.addItem(ALICE, null));
        assertThrows(IllegalArgumentException.class, () -> trade.offerOf(EVE));
        assertThrows(IllegalArgumentException.class, () -> new TradeSession<Integer>(ALICE, ALICE));
    }

    @Test
    public void cancelAndDisconnectEndTheTrade() {
        trade.addItem(ALICE, 1);
        assertFalse("strangers do not affect the trade", trade.playerDisconnected(EVE));
        assertEquals(TradeSession.State.OPEN, trade.state());
        assertTrue(trade.playerDisconnected(BOB));
        assertEquals(TradeSession.State.CANCELLED, trade.state());
        assertEquals(Optional.of(TradeEndReason.DISCONNECTED), trade.endReason());
        assertFalse(trade.playerDisconnected(ALICE));
        assertEquals(TradeOutcome.NOT_OPEN, trade.addItem(ALICE, 2));
        assertEquals(TradeOutcome.NOT_OPEN, trade.cancel(TradeEndReason.CANCELLED_BY_PLAYER));
        assertEquals(Optional.of(TradeEndReason.DISCONNECTED), trade.endReason());
    }

    @Test
    public void aSettlingTradeCanStillBeCancelledOrFailed() {
        lockAndConfirmBoth();
        assertEquals(TradeOutcome.OK, trade.cancel(TradeEndReason.TOO_FAR));
        assertFalse("nothing left to settle", trade.settlement().isPresent());
        assertEquals(TradeOutcome.NOT_SETTLING, trade.complete());

        TradeSession<Integer> failing = new TradeSession<>(ALICE, BOB);
        assertEquals(TradeOutcome.NOT_SETTLING, failing.fail(TradeEndReason.INVENTORY_FULL));
        failing.lock(ALICE, 0);
        failing.lock(BOB, 0);
        failing.confirm(ALICE, 0);
        failing.confirm(BOB, 0);
        assertEquals(TradeOutcome.OK, failing.fail(TradeEndReason.INVENTORY_FULL));
        assertEquals(TradeSession.State.FAILED, failing.state());
        assertEquals(Optional.of(TradeEndReason.INVENTORY_FULL), failing.endReason());
        assertEquals(TradeOutcome.NOT_SETTLING, failing.complete());
    }

    @Test
    public void settlementAppliesGoldDebitFirst() {
        TradeSettlement<Integer> settlement = new TradeSettlement<>(ALICE, BOB,
                new TradeOffer<>(List.of(), 100), new TradeOffer<>(List.of(1), 100));
        Wallet full = Wallet.of(Wallet.MAX_BALANCE);
        assertEquals("pays first, so a full wallet can swap gold", full,
                settlement.applyGold(ALICE, full).wallet());
        Wallet poor = Wallet.of(99);
        WalletResult broke = settlement.applyGold(ALICE, poor);
        assertEquals(WalletResult.Status.INSUFFICIENT_FUNDS, broke.status());
        assertSame(poor, broke.wallet());

        TradeSettlement<Integer> generous = new TradeSettlement<>(ALICE, BOB,
                new TradeOffer<>(List.of(), 0), new TradeOffer<>(List.of(), 100));
        WalletResult overflow = generous.applyGold(ALICE, full);
        assertEquals(WalletResult.Status.OVERFLOW, overflow.status());
        assertSame(full, overflow.wallet());
        assertEquals(Wallet.of(400), generous.applyGold(BOB, Wallet.of(500)).wallet());
        assertThrows(IllegalArgumentException.class, () -> generous.givenBy(EVE));
    }

    @Test
    public void offerValidation() {
        assertTrue(TradeOffer.<Integer>empty().isEmpty());
        assertFalse(new TradeOffer<>(List.of(), 1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new TradeOffer<>(List.of(1, 1), 0));
        assertThrows(IllegalArgumentException.class, () -> new TradeOffer<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 0));
        assertThrows(IllegalArgumentException.class, () -> new TradeOffer<>(List.of(), -1));
        assertThrows(IllegalArgumentException.class, () -> new TradeSettlement<>(ALICE, ALICE,
                TradeOffer.<Integer>empty(), TradeOffer.<Integer>empty()));
        assertEquals("message.eternalrealm.trade.end.wallet_full", TradeEndReason.WALLET_FULL.messageKey());
        assertFalse(TradeOutcome.NOT_PARTICIPANT.messageKey().isPresent());
        assertTrue(TradeOutcome.OFFER_CHANGED.messageKey().isPresent());
        assertEquals(BOB, trade.partnerOf(ALICE));
        assertEquals(ALICE, trade.partnerOf(BOB));
        assertTrue(trade.isParticipant(BOB));
        assertFalse(trade.isParticipant(EVE));
        assertEquals(ALICE, trade.first());
        assertEquals(BOB, trade.second());
    }
}
