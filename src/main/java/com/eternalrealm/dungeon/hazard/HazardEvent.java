package com.eternalrealm.dungeon.hazard;

import com.eternalrealm.combat.Telegraph;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** What a {@link HazardField} tick asks the platform to do, in order. */
public sealed interface HazardEvent permits HazardEvent.Warn, HazardEvent.Strike {
    HazardSite site();

    /** Show {@code telegraph} now; it hits at {@code fireTick}. */
    record Warn(HazardSite site, Telegraph telegraph, long fireTick) implements HazardEvent {
        public Warn {
            Objects.requireNonNull(site, "site");
            Objects.requireNonNull(telegraph, "telegraph");
        }
    }

    /** The trap hits now: fire the marker; {@code hit} are the victims inside it (empty if nobody stood there). */
    record Strike(HazardSite site, Telegraph telegraph, List<UUID> hit) implements HazardEvent {
        public Strike {
            Objects.requireNonNull(site, "site");
            Objects.requireNonNull(telegraph, "telegraph");
            hit = List.copyOf(hit);
        }
    }
}
