package com.eternalrealm.world;

import java.util.Objects;

/**
 * A player moved into another area (or was placed in one for the first time).
 *
 * @param previousAreaId the area the player was in, or 0 when this is the first position known for the player
 *                       (login, respawn after {@link AreaTracker#forget})
 */
public record AreaTransition(int previousAreaId, AreaDefinition area) {
    public AreaTransition {
        Objects.requireNonNull(area, "area");
        if (previousAreaId < 0 || previousAreaId == area.id()) {
            throw new IllegalArgumentException("invalid previous area " + previousAreaId + " for area " + area.id());
        }
    }

    public boolean isFirstPosition() {
        return previousAreaId == 0;
    }
}
