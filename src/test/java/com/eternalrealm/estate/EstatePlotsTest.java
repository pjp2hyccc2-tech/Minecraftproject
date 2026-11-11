package com.eternalrealm.estate;

import org.junit.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EstatePlotsTest {
    @Test
    public void roomsAndGuildHousesBuildInSeparateRegions() {
        assertEquals(EstatePlots.ROOM_REGION, EstatePlots.regionOf(EstateKey.ROOM));
        assertEquals(EstatePlots.GUILD_REGION, EstatePlots.regionOf(EstateKey.GUILD));
        int other = EstatePlots.regionOf("workshop");
        assertTrue(other >= 2 && other < EstatePlot.REGIONS);
        assertEquals("stable for a name", other, EstatePlots.regionOf("workshop"));

        EstatePlot room = new EstatePlot(EstatePlots.ROOM_REGION, 0);
        EstatePlot guild = new EstatePlot(EstatePlots.GUILD_REGION, 0);
        assertTrue(Math.abs(guild.originZ() - room.originZ()) >= EstatePlot.REGION_DEPTH - EstatePlot.SPACING);
    }

    @Test
    public void plotsAreFarApartAndFoundByPosition() {
        Set<Long> origins = new HashSet<>();
        for (int region = 0; region < EstatePlot.REGIONS; region++) {
            for (int index : new int[]{0, 1, 63, 64, 65, 4095, EstatePlot.CAPACITY - 1}) {
                EstatePlot plot = new EstatePlot(region, index);
                assertTrue(origins.add(((long) plot.originX() << 32) ^ plot.originZ()));
                assertEquals(Optional.of(plot), EstatePlot.at(plot.originX(), plot.originZ()));
                assertEquals(Optional.of(plot), EstatePlot.at(plot.originX() + EstatePlot.HALF_EXTENT,
                        plot.originZ() - EstatePlot.HALF_EXTENT));
                assertEquals(Optional.empty(), EstatePlot.at(plot.originX() + EstatePlot.HALF_EXTENT + 1,
                        plot.originZ()));
                assertEquals(plot, EstatePlot.ofKey(plot.key()));
                assertTrue("inside the world border", plot.originZ() + EstatePlot.HALF_EXTENT < 29_999_984
                        && plot.originX() + EstatePlot.HALF_EXTENT < 29_999_984);
            }
        }
        EstatePlot a = new EstatePlot(0, 0);
        EstatePlot b = new EstatePlot(0, 1);
        EstatePlot below = new EstatePlot(0, EstatePlot.COLUMNS);
        assertEquals(EstatePlot.SPACING, b.originX() - a.originX());
        assertEquals(EstatePlot.SPACING, below.originZ() - a.originZ());
        assertTrue("neighbours never see each other",
                EstatePlot.SPACING - 2 * EstatePlot.HALF_EXTENT >= 512);
        assertEquals(Optional.empty(), EstatePlot.at(-5, 10));
        assertThrows(IllegalArgumentException.class, () -> new EstatePlot(EstatePlot.REGIONS, 0));
        assertThrows(IllegalArgumentException.class, () -> new EstatePlot(0, EstatePlot.CAPACITY));
    }

    @Test
    public void theLowestFreePlotIsHandedOutAndReused() {
        EstatePlots plots = new EstatePlots();
        EstatePlot first = plots.allocate(EstateKey.ROOM).orElseThrow();
        EstatePlot second = plots.allocate(EstateKey.ROOM).orElseThrow();
        EstatePlot guild = plots.allocate(EstateKey.GUILD).orElseThrow();
        assertEquals(new EstatePlot(0, 0), first);
        assertEquals(new EstatePlot(0, 1), second);
        assertEquals(new EstatePlot(1, 0), guild);
        assertEquals(3, plots.taken());
        assertTrue(plots.free(first));
        assertFalse("freeing twice", plots.free(first));
        assertEquals(first, plots.allocate(EstateKey.ROOM).orElseThrow());
        assertFalse("taking a taken plot", plots.take(second));
        assertTrue(plots.isTaken(guild));
        assertNotEquals(first, second);
    }
}
