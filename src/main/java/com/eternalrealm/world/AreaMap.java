package com.eternalrealm.world;

import com.eternalrealm.world.field.FieldLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Finds the field at a position from its horizontal offset to the world spawn (the centre of the capital).
 *
 * <p>The standard map is the field map of {@link FieldLayout}: a region per field with natural, wandering borders.
 * Other maps (tests, a server whose realm has another shape) plug in their own {@link Locator}. Immutable.</p>
 */
public final class AreaMap {
    /** Where each field lies. */
    public interface Locator {
        /** The area id at a block offset from the spawn. */
        int areaAt(double dx, double dz);

        /** Roughly how many blocks the offset lies inside its field (distance to the nearest border). */
        double borderDistance(double dx, double dz);
    }

    private static final Locator FIELDS = new Locator() {
        @Override
        public int areaAt(double dx, double dz) {
            return FieldLayout.areaAt(dx, dz);
        }

        @Override
        public double borderDistance(double dx, double dz) {
            return FieldLayout.borderDistance(dx, dz);
        }
    };
    private static final AreaMap STANDARD = new AreaMap(Areas.all(), FIELDS);

    private final List<AreaDefinition> areas;
    private final Map<Integer, AreaDefinition> byId = new HashMap<>();
    private final Locator locator;

    public AreaMap(List<AreaDefinition> areas, Locator locator) {
        Objects.requireNonNull(areas, "areas");
        this.locator = Objects.requireNonNull(locator, "locator");
        if (areas.isEmpty()) {
            throw new IllegalArgumentException("an area map needs at least one area");
        }
        for (AreaDefinition area : areas) {
            if (byId.put(Objects.requireNonNull(area, "area").id(), area) != null) {
                throw new IllegalArgumentException("duplicate area id " + area.id());
            }
        }
        this.areas = List.copyOf(areas);
    }

    /** The field map of {@link Areas#all()}. */
    public static AreaMap standard() {
        return STANDARD;
    }

    /** The fields, ordered by id. */
    public List<AreaDefinition> areas() {
        return areas;
    }

    /**
     * @param dx block offset {@code x - spawnX}
     * @param dz block offset {@code z - spawnZ}
     */
    public AreaDefinition areaAt(double dx, double dz) {
        checkFinite(dx, dz);
        AreaDefinition area = byId.get(locator.areaAt(dx, dz));
        if (area == null) {
            throw new IllegalStateException("the locator returned an unknown area at " + dx + ", " + dz);
        }
        return area;
    }

    /** How far the offset lies inside its field (distance to the nearest border, about blocks). */
    public double borderDistance(double dx, double dz) {
        checkFinite(dx, dz);
        return locator.borderDistance(dx, dz);
    }

    /** Euclidean horizontal distance. */
    public static double horizontalDistance(double dx, double dz) {
        checkFinite(dx, dz);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void checkFinite(double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz)) {
            throw new IllegalArgumentException("non-finite offset: " + dx + ", " + dz);
        }
    }
}
