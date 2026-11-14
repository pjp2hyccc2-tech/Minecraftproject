package com.eternalrealm.world.field;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Banks one or two blocks above the water go down one; walls behind them follow; built and protected ground stays. */
public class ShoresTest {
    @Test
    public void banksAboveTheWaterGoDownOne() {
        assertFalse("already level", Shores.lowers(63, 63));
        assertTrue(Shores.lowers(64, 63));
        assertTrue(Shores.lowers(65, 63));
        assertFalse("a rock face is left as it is", Shores.lowers(66, 63));
        assertFalse(Shores.lowers(62, 63));
    }

    @Test
    public void aWallOfTwoBehindALoweredBankFollows() {
        assertTrue(Shores.ripples(66, 64, 63));
        assertFalse("a step of one is a walk", Shores.ripples(65, 64, 63));
        assertFalse("high ground beyond the shore stays", Shores.ripples(70, 64, 63));
    }

    @Test
    public void onlyNaturalGroundMoves() {
        assertTrue(Shores.natural("minecraft:grass_block"));
        assertTrue(Shores.natural("minecraft:sand"));
        assertTrue(Shores.natural("minecraft:stone"));
        assertFalse(Shores.natural("minecraft:oak_planks"));
        assertFalse(Shores.natural("minecraft:stone_bricks"));
        assertFalse(Shores.natural("eternalrealm:plaza_flagstones"));
    }

    @Test
    public void theCapitalWaystonesAndEntrancesKeepTheirGround() {
        FieldLayout.Waystone stone = FieldLayout.waystones().get(0);
        assertFalse(Shores.mayChange(stone.x(), stone.z()));
        assertFalse("the capital", Shores.mayChange(0, 0) && FieldLayout.inCapital(0, 0));
    }

    @Test
    public void onlyChunksNearWaterAreLookedAt() {
        FieldLayout.River river = FieldLayout.rivers().get(0);
        double[] point = river.points()[river.points().length / 2];
        assertTrue(Shores.nearWater(point[0], point[1], 0));
        assertTrue(Shores.chunkNearWater((int) Math.floor(point[0] / 16), (int) Math.floor(point[1] / 16)));
        assertTrue("the lake's shore", Shores.nearWater(FieldLayout.LAKE_X + FieldLayout.LAKE_RX, FieldLayout.LAKE_Z, 8));
        assertTrue("the pond", Shores.nearWater(FieldLayout.POND_X + FieldLayout.POND_R, FieldLayout.POND_Z, 4));
        int dry = 0;
        int all = 0;
        for (int cx = -80; cx < 80; cx += 3) {
            for (int cz = -80; cz < 80; cz += 3) {
                all++;
                if (!Shores.chunkNearWater(cx, cz)) {
                    dry++;
                }
            }
        }
        assertTrue("most of the land has no shore: " + dry + " of " + all, dry > all * 0.7);
    }
}
