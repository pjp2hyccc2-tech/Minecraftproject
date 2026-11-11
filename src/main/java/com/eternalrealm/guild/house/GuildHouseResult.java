package com.eternalrealm.guild.house;

import java.util.Objects;

/**
 * Result of a {@link GuildHouseService} operation.
 *
 * @param house      the guild's data after the operation (unchanged on failure)
 * @param cost       the full price of the change (0 if it pays out)
 * @param fromFund   taken out of the house fund
 * @param fromWallet to be taken from the acting master's wallet (the caller charges it)
 * @param toFund     paid into the fund (refunds, donations)
 */
public record GuildHouseResult(GuildHouseOutcome outcome, GuildHouse house, long cost, long fromFund, long fromWallet,
                               long toFund) {
    public GuildHouseResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(house, "house");
        if (cost < 0 || fromFund < 0 || fromWallet < 0 || toFund < 0) {
            throw new IllegalArgumentException("negative amount");
        }
    }

    static GuildHouseResult failure(GuildHouseOutcome outcome, GuildHouse house) {
        return new GuildHouseResult(outcome, house, 0, 0, 0, 0);
    }

    static GuildHouseResult ok(GuildHouse house) {
        return new GuildHouseResult(GuildHouseOutcome.OK, house, 0, 0, 0, 0);
    }

    public boolean isSuccess() {
        return outcome.isSuccess();
    }
}
