package com.eternalrealm.dungeon.hazard;

import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.dungeon.BlockBox;

import java.util.Objects;

/**
 * One trap of an instance, in layout coordinates (relative to the instance origin, floors at {@code y = 0}, feet at
 * {@code y = 1}).
 *
 * @param index   position in the instance's list (stable for the run)
 * @param zone    where someone must stand for the trap to work (the corridor passage or the room interior)
 * @param x       centre of the marker (block centres end in .5); for targeted kinds the centre of the zone
 * @param z       centre of the marker
 * @param yaw     facing of RECT/CROSS/SQUARE markers (Minecraft degrees: 0 = +z, 90 = -x)
 * @param a       first size of the marker, fitted to the spot (see {@link HazardKind})
 * @param b       second size of the marker
 * @param delay   ticks from the zone becoming occupied to the first warning
 */
public record HazardSite(int index, HazardKind kind, BlockBox zone, double x, double z, double yaw, double a, double b,
                         int delay) {
    public HazardSite {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(zone, "zone");
        if (index < 0 || delay < 0 || !Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(yaw)) {
            throw new IllegalArgumentException("bad hazard site " + index);
        }
        // validates the sizes the same way the marker will
        new Telegraph(kind.shape(), kind.style(), x, 1.0, z, yaw, a, b, kind.warnTicks());
    }

    /** The marker of a fixed-spot trap, in world coordinates ({@code origin*} = the instance origin). */
    public Telegraph telegraph(double originX, double originY, double originZ) {
        return markerAt(originX + x, originY + 1.0, originZ + z);
    }

    /** The marker centred on a world position (targeted kinds: where the victim stood). */
    public Telegraph markerAt(double worldX, double feetY, double worldZ) {
        int ticks = kind.warnTicks();
        return switch (kind.shape()) {
            case RECT -> {
                // the strip is centred on the spot: its origin lies half a length behind
                double radians = Math.toRadians(yaw);
                double fx = -Math.sin(radians);
                double fz = Math.cos(radians);
                yield Telegraph.rect(kind.style(), worldX - fx * a / 2.0, feetY, worldZ - fz * a / 2.0, yaw, a, b, ticks);
            }
            default -> new Telegraph(kind.shape(), kind.style(), worldX, feetY, worldZ, yaw, a, b, ticks);
        };
    }

    /** Whether a position (layout coordinates) lies in the zone. Feet may be up to one block off the floor level. */
    public boolean inZone(double localX, double localY, double localZ) {
        return localX >= zone.minX() && localX < zone.maxX() + 1.0 && localZ >= zone.minZ() && localZ < zone.maxZ() + 1.0
                && localY >= zone.minY() - 1.0 && localY < zone.maxY() + 1.0;
    }
}
