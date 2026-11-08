package com.eternalrealm.economy;

import com.eternalrealm.core.ModInfo;

import java.util.Optional;

/**
 * Why an item was put into a {@link Mailbox}. {@link #id()} is the stable persistence key: never rename or reuse it.
 */
public enum MailReason {
    /** The seller cancelled the listing. */
    AUCTION_CANCELLED("auction_cancelled"),
    /** The listing expired unsold. */
    AUCTION_EXPIRED("auction_expired"),
    /** A purchase the buyer had no inventory room for. */
    AUCTION_PURCHASE("auction_purchase"),
    /** Rewards and deliveries from other systems or operators. */
    SYSTEM("system");

    private final String id;

    MailReason(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "mail_reason." + ModInfo.MOD_ID + "." + id;
    }

    public static Optional<MailReason> byId(String id) {
        for (MailReason reason : values()) {
            if (reason.id.equals(id)) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }
}
