package com.eternalrealm.estate;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * Hands out {@link EstatePlot}s: each kind of estate builds in its own region (inn rooms in region 0, guild houses in
 * region 1, any other kind in one of regions 2..7 chosen from its name), the lowest free plot first so the dimension
 * stays compact and plots are reused. A plot stays taken until it is {@link #free freed}, which the service only does
 * once its blocks are wiped. Regions are shared per region, not per kind, so two kinds that land in the same region
 * still never get the same plot.
 */
public final class EstatePlots {
    public static final int ROOM_REGION = 0;
    public static final int GUILD_REGION = 1;
    private static final int FIRST_SHARED_REGION = 2;

    private final Map<Integer, BitSet> used = new HashMap<>();

    /** The region where estates of {@code kind} are built. */
    public static int regionOf(String kind) {
        Objects.requireNonNull(kind, "kind");
        if (EstateKey.ROOM.equals(kind)) {
            return ROOM_REGION;
        }
        if (EstateKey.GUILD.equals(kind)) {
            return GUILD_REGION;
        }
        CRC32 crc = new CRC32();
        crc.update(kind.getBytes(StandardCharsets.UTF_8));
        return FIRST_SHARED_REGION + (int) (crc.getValue() % (EstatePlot.REGIONS - FIRST_SHARED_REGION));
    }

    /** The lowest free plot of {@code kind}'s region, now taken; empty when the region is full. */
    public Optional<EstatePlot> allocate(String kind) {
        int region = regionOf(kind);
        BitSet bits = used.computeIfAbsent(region, r -> new BitSet());
        int index = bits.nextClearBit(0);
        if (index >= EstatePlot.CAPACITY) {
            return Optional.empty();
        }
        bits.set(index);
        return Optional.of(new EstatePlot(region, index));
    }

    /** Marks {@code plot} as taken (loading). @return false if it already was */
    public boolean take(EstatePlot plot) {
        BitSet bits = used.computeIfAbsent(plot.region(), r -> new BitSet());
        boolean was = bits.get(plot.index());
        bits.set(plot.index());
        return !was;
    }

    /** Frees {@code plot} for reuse. @return false if it was not taken */
    public boolean free(EstatePlot plot) {
        BitSet bits = used.get(plot.region());
        if (bits == null || !bits.get(plot.index())) {
            return false;
        }
        bits.clear(plot.index());
        return true;
    }

    public boolean isTaken(EstatePlot plot) {
        BitSet bits = used.get(plot.region());
        return bits != null && bits.get(plot.index());
    }

    /** Plots taken in all regions. */
    public int taken() {
        int count = 0;
        for (BitSet bits : used.values()) {
            count += bits.cardinality();
        }
        return count;
    }
}
