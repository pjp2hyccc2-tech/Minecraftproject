package com.eternalrealm.estate.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

/**
 * Keeps the chunks of an estate loaded while the service works on it. Blocks load at once, but entities (armour
 * stands and item frames that hold things) load a little later: nothing is moved or wiped before
 * {@link #hold} reports them loaded, so nothing is ever missed.
 */
final class EstateChunks {
    /** Expires on its own 10 s after the last {@link #hold}. */
    static final TicketType<ChunkPos> TICKET = TicketType.create("eternalrealm_estate",
            Comparator.comparingLong(ChunkPos::toLong), 200);

    private EstateChunks() {
    }

    /** Keeps every chunk of {@code box} loaded a little longer. @return whether all their entities are loaded */
    static boolean hold(ServerLevel level, AABB box) {
        boolean loaded = true;
        int cx0 = (int) Math.floor(box.minX) >> 4;
        int cx1 = (int) Math.floor(box.maxX - 1.0E-4) >> 4;
        int cz0 = (int) Math.floor(box.minZ) >> 4;
        int cz1 = (int) Math.floor(box.maxZ - 1.0E-4) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                level.getChunkSource().addRegionTicket(TICKET, pos, 1, pos);
                if (!level.areEntitiesLoaded(pos.toLong())) {
                    loaded = false;
                }
            }
        }
        return loaded;
    }
}
