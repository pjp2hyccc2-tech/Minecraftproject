package com.eternalrealm.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Remembers which area each online player is in and reports changes. Server thread only.
 *
 * <p>The Forge layer calls {@link #update} every few ticks with the player's offset to the spawn and reacts to a
 * transition (area title, {@code AreaEntered} quest event, {@link Discovery}). A player only leaves an area once they are
 * {@code hysteresisBlocks} beyond its border, so walking along a boundary does not flood them with titles.</p>
 */
public final class AreaTracker {
    public static final double DEFAULT_HYSTERESIS_BLOCKS = 8.0;

    private final AreaMap map;
    private final double hysteresis;
    private final Map<UUID, AreaDefinition> current = new HashMap<>();

    public AreaTracker(AreaMap map, double hysteresisBlocks) {
        this.map = Objects.requireNonNull(map, "map");
        if (!(hysteresisBlocks >= 0.0) || hysteresisBlocks > 1000.0) {
            throw new IllegalArgumentException("hysteresisBlocks out of range [0, 1000]: " + hysteresisBlocks);
        }
        this.hysteresis = hysteresisBlocks;
    }

    /**
     * @param dx block offset {@code x - spawnX}
     * @param dz block offset {@code z - spawnZ}
     * @return the transition if the player is now in another area (always present for the first update of a player)
     */
    public Optional<AreaTransition> update(UUID playerId, double dx, double dz) {
        Objects.requireNonNull(playerId, "playerId");
        double distance = AreaMap.horizontalDistance(dx, dz);
        AreaDefinition previous = current.get(playerId);
        if (previous != null && insideWithMargin(previous, distance)) {
            return Optional.empty();
        }
        AreaDefinition now = map.areaAtDistance(distance);
        if (previous != null && previous.id() == now.id()) {
            return Optional.empty();
        }
        current.put(playerId, now);
        return Optional.of(new AreaTransition(previous == null ? 0 : previous.id(), now));
    }

    public Optional<AreaDefinition> currentArea(UUID playerId) {
        return Optional.ofNullable(current.get(playerId));
    }

    /** Call on logout (and on dimension changes) so the next update reports the area again. */
    public void forget(UUID playerId) {
        current.remove(playerId);
    }

    public int size() {
        return current.size();
    }

    private boolean insideWithMargin(AreaDefinition area, double distance) {
        return distance >= area.innerRadius() - hysteresis
                && (area.isUnbounded() || distance < area.outerRadius() + hysteresis);
    }
}
