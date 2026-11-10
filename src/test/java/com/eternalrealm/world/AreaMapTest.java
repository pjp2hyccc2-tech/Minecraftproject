package com.eternalrealm.world;

import com.eternalrealm.world.field.FieldLayout;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AreaMapTest {
    private final AreaMap map = AreaMap.standard();

    private int areaAt(double dx, double dz) {
        return map.areaAt(dx, dz).id();
    }

    @Test
    public void theFieldMapPutsEachFieldWhereThePaintingShowsIt() {
        assertEquals("the capital counts as the meadow", 1, areaAt(0, 0));
        assertEquals("meadow south of the capital", 1, areaAt(-300, 600));
        assertEquals("moonshadow forest to the west", 2, areaAt(-800, 0));
        assertEquals("iron mine in the far west", 3, areaAt(-1350, 650));
        assertEquals("sacred falls to the north-east", 4, areaAt(600, -400));
        assertEquals("ancient ruins to the east", 5, areaAt(1300, -150));
        assertEquals("soaring peaks behind the capital", 6, areaAt(100, -1000));
        assertEquals("end forest to the north-west", 7, areaAt(-1100, -800));
        assertEquals("farms to the east", 8, areaAt(800, 300));
        assertEquals("lake to the south-east", 9, areaAt(360, 880));
        assertEquals("harbour to the south-east", 10, areaAt(1250, 850));
    }

    @Test
    public void everyFieldIsReachableAndBordersAreFarFromItsLabel() {
        for (AreaDefinition area : map.areas()) {
            double[] label = FieldLayout.labelPoint(area.id());
            assertEquals(area.slug(), area.id(), areaAt(label[0], label[1]));
            assertTrue(area.slug() + " label near a border", map.borderDistance(label[0], label[1]) > 40.0);
        }
    }

    @Test
    public void nonFiniteOffsetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> map.areaAt(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> map.areaAt(0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> map.borderDistance(Double.NaN, 0));
        assertEquals(5.0, AreaMap.horizontalDistance(3, 4), 0.0);
    }

    @Test
    public void customMapsUseTheirOwnLocator() {
        AreaDefinition west = new AreaDefinition(1, "west", 1, 5, false, null, "t", 0, AreaDefinition.Kind.MAIN, 1);
        AreaDefinition east = new AreaDefinition(2, "east", 5, 9, false, "b1", "t", 1, AreaDefinition.Kind.MAIN, 2);
        AreaMap.Locator halves = new AreaMap.Locator() {
            @Override
            public int areaAt(double dx, double dz) {
                return dx < 0 ? 1 : 2;
            }

            @Override
            public double borderDistance(double dx, double dz) {
                return Math.abs(dx);
            }
        };
        AreaMap custom = new AreaMap(List.of(west, east), halves);
        assertEquals(List.of(west, east), custom.areas());
        assertEquals(1, custom.areaAt(-1, 0).id());
        assertEquals(2, custom.areaAt(0, 0).id());
        assertEquals(7.0, custom.borderDistance(-7, 3), 0.0);
        assertThrows(IllegalArgumentException.class, () -> new AreaMap(List.of(), halves));
        assertThrows(IllegalArgumentException.class, () -> new AreaMap(List.of(west, west), halves));
        AreaMap broken = new AreaMap(List.of(west), halves);
        assertThrows(IllegalStateException.class, () -> broken.areaAt(5, 0));
    }
}
