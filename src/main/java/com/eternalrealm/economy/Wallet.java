package com.eternalrealm.economy;

/**
 * A player's gold: an immutable balance within {@code [0, MAX_BALANCE]}.
 *
 * <p>Operations return a {@link WalletResult} with a new wallet instead of changing this one, so a failed step of a
 * multi-step transaction never leaves a half-changed balance behind: the caller stores the new wallet only after
 * every step succeeded. The balance never becomes negative and never wraps around.</p>
 *
 * <p>Amounts passed in are computed by the server (prices, fees, validated player input); a negative amount is a
 * programming error and throws.</p>
 */
public record Wallet(long balance) {
    /** 9,999,999,999 G: fits the HUD and keeps every price × quantity product far from {@code long} overflow. */
    public static final long MAX_BALANCE = 9_999_999_999L;
    public static final Wallet EMPTY = new Wallet(0L);

    public Wallet {
        if (balance < 0 || balance > MAX_BALANCE) {
            throw new IllegalArgumentException("balance out of range [0, " + MAX_BALANCE + "]: " + balance);
        }
    }

    public static Wallet of(long balance) {
        return balance == 0 ? EMPTY : new Wallet(balance);
    }

    /** Adds gold; fails with {@link WalletResult.Status#OVERFLOW} if the balance would exceed {@link #MAX_BALANCE}. */
    public WalletResult credit(long amount) {
        checkAmount(amount);
        if (amount > capacity()) {
            return new WalletResult(WalletResult.Status.OVERFLOW, this);
        }
        return new WalletResult(WalletResult.Status.OK, of(balance + amount));
    }

    /** Removes gold; fails with {@link WalletResult.Status#INSUFFICIENT_FUNDS} if the balance is too low. */
    public WalletResult debit(long amount) {
        checkAmount(amount);
        if (amount > balance) {
            return new WalletResult(WalletResult.Status.INSUFFICIENT_FUNDS, this);
        }
        return new WalletResult(WalletResult.Status.OK, of(balance - amount));
    }

    public boolean canAfford(long amount) {
        return amount >= 0 && amount <= balance;
    }

    /** Gold that can still be credited before the balance reaches {@link #MAX_BALANCE}. */
    public long capacity() {
        return MAX_BALANCE - balance;
    }

    private static void checkAmount(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }
    }
}
