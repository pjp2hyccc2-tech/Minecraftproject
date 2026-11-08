package com.eternalrealm.economy;

import java.util.Objects;
import java.util.UUID;

/**
 * What a confirmed trade moves: each side gives its own offer and receives the partner's. The integration performs
 * the transfer (inventories and wallets) and then calls {@link TradeSession#complete()} or
 * {@link TradeSession#fail(TradeEndReason)}.
 */
public record TradeSettlement<T>(UUID first, UUID second, TradeOffer<T> fromFirst, TradeOffer<T> fromSecond) {
    public TradeSettlement {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(fromFirst, "fromFirst");
        Objects.requireNonNull(fromSecond, "fromSecond");
        if (first.equals(second)) {
            throw new IllegalArgumentException("a player cannot trade with themselves");
        }
    }

    public TradeOffer<T> givenBy(UUID player) {
        return isFirst(player) ? fromFirst : fromSecond;
    }

    public TradeOffer<T> receivedBy(UUID player) {
        return isFirst(player) ? fromSecond : fromFirst;
    }

    /**
     * The player's wallet after the trade: first debits the gold they give, then credits the gold they receive (in
     * that order, so a full wallet that also pays gold does not fail needlessly). On failure the result carries the
     * original wallet with {@code INSUFFICIENT_FUNDS} or {@code OVERFLOW}; then fail the trade.
     */
    public WalletResult applyGold(UUID player, Wallet wallet) {
        Objects.requireNonNull(wallet, "wallet");
        WalletResult paid = wallet.debit(givenBy(player).gold());
        if (!paid.isSuccess()) {
            return paid;
        }
        WalletResult received = paid.wallet().credit(receivedBy(player).gold());
        return received.isSuccess() ? received : new WalletResult(received.status(), wallet);
    }

    private boolean isFirst(UUID player) {
        if (player.equals(first)) {
            return true;
        }
        if (player.equals(second)) {
            return false;
        }
        throw new IllegalArgumentException(player + " is not part of this trade");
    }
}
