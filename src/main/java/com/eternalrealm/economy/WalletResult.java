package com.eternalrealm.economy;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a {@link Wallet} operation.
 *
 * @param wallet the new wallet on success; the unchanged wallet on failure
 */
public record WalletResult(Status status, Wallet wallet) {
    public enum Status {
        OK,
        /** Not enough gold. */
        INSUFFICIENT_FUNDS,
        /** The balance would exceed {@link Wallet#MAX_BALANCE}. */
        OVERFLOW;

        /** Translation key of the failure message (no arguments); empty for {@link #OK}. */
        public Optional<String> messageKey() {
            if (this == OK) {
                return Optional.empty();
            }
            return Optional.of("message." + ModInfo.MOD_ID + ".wallet." + name().toLowerCase(Locale.ROOT));
        }
    }

    public WalletResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(wallet, "wallet");
    }

    public boolean isSuccess() {
        return status == Status.OK;
    }
}
