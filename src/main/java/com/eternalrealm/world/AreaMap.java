package com.eternalrealm.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Finds the area at a position from its horizontal offset to the world spawn.
 *
 * <p>The rings must tile the plane: sorted by inner radius they start at 0, each ring starts where the previous one ends
 * and only the last one is unbounded. The distance is Euclidean ({@code sqrt(dx² + dz²)}), so areas are circles, not
 * squares; for integer block offsets inside the world border the computation is exact, so a ring boundary is exactly
 * where the numbers say. Immutable.</p>
 */
public final class AreaMap {
    private static final AreaMap STANDARD = new AreaMap(Areas.all());

    private final List<AreaDefinition> rings;

    public AreaMap(List<AreaDefinition> areas) {
        Objects.requireNonNull(areas, "areas");
        List<AreaDefinition> sorted = new ArrayList<>(areas);
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("an area map needs at least one area");
        }
        sorted.sort(Comparator.comparingInt(AreaDefinition::innerRadius));
        Set<Integer> ids = new HashSet<>();
        long expectedInner = 0;
        for (int i = 0; i < sorted.size(); i++) {
            AreaDefinition area = Objects.requireNonNull(sorted.get(i), "area");
            if (!ids.add(area.id())) {
                throw new IllegalArgumentException("duplicate area id " + area.id());
            }
            if (area.innerRadius() != expectedInner) {
                throw new IllegalArgumentException("area " + area.id() + " starts at " + area.innerRadius()
                        + " but the previous ring ends at " + expectedInner + " (rings must not overlap or leave gaps)");
            }
            boolean outermost = i == sorted.size() - 1;
            if (outermost != area.isUnbounded()) {
                throw new IllegalArgumentException("only the outermost area may be (and must be) unbounded: area "
                        + area.id());
            }
            expectedInner = area.outerRadius();
        }
        this.rings = List.copyOf(sorted);
    }

    /** The map of {@link Areas#all()}. */
    public static AreaMap standard() {
        return STANDARD;
    }

    /** The areas from the spawn outwards. */
    public List<AreaDefinition> areas() {
        return rings;
    }

    /**
     * @param dx block offset {@code x - spawnX}
     * @param dz block offset {@code z - spawnZ}
     */
    public AreaDefinition areaAt(double dx, double dz) {
        return areaAtDistance(horizontalDistance(dx, dz));
    }

    /** @param distance horizontal distance from the spawn; infinity is the outermost area */
    public AreaDefinition areaAtDistance(double distance) {
        if (!(distance >= 0.0)) {
            throw new IllegalArgumentException("distance must be >= 0: " + distance);
        }
        for (AreaDefinition ring : rings) {
            if (ring.isUnbounded() || distance < ring.outerRadius()) {
                return ring;
            }
        }
        throw new IllegalStateException("the outermost ring is unbounded");
    }

    /** Euclidean horizontal distance; may be infinite for absurd (but finite) offsets. */
    public static double horizontalDistance(double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz)) {
            throw new IllegalArgumentException("non-finite offset: " + dx + ", " + dz);
        }
        return Math.sqrt(dx * dx + dz * dz);
    }
}
