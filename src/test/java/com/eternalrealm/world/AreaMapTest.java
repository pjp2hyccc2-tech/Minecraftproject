package com.eternalrealm.world;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class AreaMapTest {
    private final AreaMap map = AreaMap.standard();

    private int areaAt(double dx, double dz) {
        return map.areaAt(dx, dz).id();
    }

    @Test
    public void ringBoundariesAreInnerInclusiveAndOuterExclusive() {
        assertEquals(1, areaAt(0, 0));
        assertEquals(1, areaAt(299.999, 0));
        assertEquals(2, areaAt(300, 0));
        assertEquals(2, areaAt(0, -599));
        assertEquals(3, areaAt(-600, 0));
        assertEquals(4, areaAt(0, 900));
        assertEquals(4, areaAt(1199, 0));
        assertEquals(5, areaAt(1200, 0));
        assertEquals(6, areaAt(0, 1500));
        assertEquals(6, areaAt(-1799.5, 0));
        assertEquals(7, areaAt(1800, 0));
        assertEquals(7, areaAt(29_999_999, 29_999_999));
    }

    @Test
    public void distanceIsEuclideanSoAreasAreCirclesNotSquares() {
        assertEquals(300.0, AreaMap.horizontalDistance(180, 240), 0.0);
        assertEquals(2, areaAt(180, 240));
        assertEquals(1, areaAt(180, 239));
        // A square ring model would put (290, 290) into area 1; the circle puts it into area 2 (distance ~410).
        assertEquals(2, areaAt(290, 290));
        assertEquals(2, areaAt(-290, -290));
    }

    @Test
    public void absurdOffsetsLandInTheOutermostAreaAndNonFiniteOnesAreRejected() {
        assertEquals(7, areaAt(1e200, 1e200));
        assertEquals(7, map.areaAtDistance(Double.POSITIVE_INFINITY).id());
        assertThrows(IllegalArgumentException.class, () -> map.areaAt(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> map.areaAt(0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> map.areaAtDistance(-1));
        assertThrows(IllegalArgumentException.class, () -> map.areaAtDistance(Double.NaN));
    }

    @Test
    public void customMapsMustTileThePlane() {
        AreaDefinition inner = new AreaDefinition(1, "inner", 0, 100, 1, 5, null, "t", 0);
        AreaDefinition outer = new AreaDefinition(2, "outer", 100, AreaDefinition.UNBOUNDED, 5, 9, "b1", "t", 0);
        AreaMap custom = new AreaMap(List.of(outer, inner));
        assertEquals(List.of(inner, outer), custom.areas());
        assertEquals(2, custom.areaAt(100, 0).id());

        AreaDefinition gap = new AreaDefinition(2, "gap", 120, AreaDefinition.UNBOUNDED, 5, 9, null, "t", 0);
        AreaDefinition overlap = new AreaDefinition(2, "overlap", 50, AreaDefinition.UNBOUNDED, 5, 9, null, "t", 0);
        AreaDefinition bounded = new AreaDefinition(2, "bounded", 100, 200, 5, 9, null, "t", 0);
        AreaDefinition notAtSpawn = new AreaDefinition(1, "late", 10, 100, 1, 5, null, "t", 0);
        AreaDefinition duplicateId = new AreaDefinition(1, "dup", 100, AreaDefinition.UNBOUNDED, 5, 9, null, "t", 0);
        AreaDefinition unboundedInner = new AreaDefinition(1, "everything", 0, AreaDefinition.UNBOUNDED, 1, 5, null, "t", 0);
        assertThrows(IllegalArgumentException.class, () -> new AreaMap(List.of(inner, gap)));
        assertThrows(IllegalArgumentException.class, () -> new AreaMap(List.of(inner, overlap)));
        assertThrows(IllegalArgumentException.class, () -> new AreaMap(List.of(inner, bounded)));
        assertThrows(IllegalArgumentException.class, () -> new AreaMap(List.of(notAtSpawn, outer)));
        assertThrows(IllegalArgumentException.class, () -> new AreaMap(List.of(inner, duplicateId)));
        assertThrows(IllegalArgumentException.class, () -> new AreaMap(List.of(unboundedInner, outer)));
        assertThrows(IllegalArgumentException.class, () -> new AreaMap(List.of()));
        assertEquals(1, new AreaMap(List.of(unboundedInner)).areaAt(5e6, 0).id());
    }
}
