package com.eternalrealm.dungeon.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockFill;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.InstanceSlot;
import com.eternalrealm.dungeon.forge.decor.DecorBuilder;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The blocks of one instance slot (the owner, 2026-11-13: dungeons are built and cleared without making the game
 * heavy): the layout's build plan, then the fitting of bars and fences to their neighbours, then the decoration, a
 * slice per tick until a time limit; later the clearing of the same boxes. Blocks are set without the neighbour shape
 * updates (shapes are fitted once, after the plan, like the capital's blueprint) and only where they differ from what
 * is there. While it works the site keeps the slot's chunks loaded with tickets, asked for in advance, so no tick
 * waits for a chunk to load.
 *
 * <p>A site can be built before anyone starts its dungeon ({@link DungeonService} prepares one when a player comes near
 * the gate) and is handed to the run that starts it. Server thread only.</p>
 */
public final class DungeonSite {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Keeps the slot's chunks loaded while blocks are set; refreshed while needed, expires on its own. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create(ModInfo.MOD_ID + "_dungeon_site",
            Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
    private static final int TICKET_REFRESH_TICKS = 20 * 10;
    /** Blocks between two looks at the clock. */
    private static final int CHECK_EVERY = 256;

    public enum Phase { PLAN, SHAPES, DECOR, BUILT, CLEARING, CLEARED }

    public final String contentId;
    public final DungeonLayout layout;
    public final InstanceSlot slot;
    public final ServerLevel level;
    public final BlockPos origin;
    public final DecorBuilder decor;
    /** Prepared sites: the last tick someone was near the gate (or asked the gatekeeper). */
    long lastWanted;
    /** Told once, when the first block is set (the service writes the slot down as holding blocks). */
    java.util.function.Consumer<DungeonSite> onTouched;
    private final List<BlockFill> plan;
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final LongArrayList shaped = new LongArrayList();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private Phase phase = Phase.PLAN;
    private int fillIndex;
    private long fillOffset;
    private int shapeIndex;
    private boolean touched;
    private List<BlockBox> clearBoxes = List.of();
    private int clearIndex;
    private long clearOffset;
    private boolean drained;
    private boolean ticketed;
    private long ticketedAt;

    public DungeonSite(String contentId, DungeonLayout layout, InstanceSlot slot, ServerLevel level) {
        this.contentId = contentId;
        this.layout = layout;
        this.slot = slot;
        this.level = level;
        this.origin = DungeonLevels.origin(level, slot);
        this.plan = layout.buildPlan();
        this.decor = new DecorBuilder(level, origin, layout);
        int minX = origin.getX() >> 4;
        int minZ = origin.getZ() >> 4;
        int maxX = (origin.getX() + DungeonLayout.FOOTPRINT - 1) >> 4;
        int maxZ = (origin.getZ() + DungeonLayout.FOOTPRINT - 1) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
    }

    public Phase phase() {
        return phase;
    }

    public boolean isBuilt() {
        return phase == Phase.BUILT;
    }

    /** Whether any block was set yet (a site that never started needs no clearing). */
    boolean touched() {
        return touched;
    }

    private void touch() {
        if (!touched) {
            touched = true;
            if (onTouched != null) {
                onTouched.accept(this);
            }
        }
    }

    /** A site found holding blocks after a restart: it is cleared like any other. */
    void markTouched() {
        touched = true;
    }

    // ------------------------------------------------------------------ building

    /**
     * Works on the build until {@code deadline} ({@link System#nanoTime()}). @return whether the plan, the fitted
     * shapes and the decoration are all in place
     */
    public boolean build(long deadline, long now) {
        if (phase == Phase.BUILT) {
            return true;
        }
        if (phase.ordinal() > Phase.BUILT.ordinal()) {
            return false;
        }
        holdChunks(now);
        int count = 0;
        if (phase == Phase.PLAN) {
            while (fillIndex < plan.size()) {
                BlockFill fill = plan.get(fillIndex);
                BlockBox box = fill.box();
                if (fillOffset == 0 && !loaded(box)) {
                    return false; // its chunks are still loading
                }
                long volume = box.volume();
                while (fillOffset < volume) {
                    if ((++count & (CHECK_EVERY - 1)) == 0 && System.nanoTime() >= deadline) {
                        return false;
                    }
                    at(box, fillOffset++);
                    BlockState state = DungeonPalette.block(layout.theme(), fill.role(), cursor.getX(), cursor.getY(),
                            cursor.getZ());
                    if (level.getBlockState(cursor) != state) {
                        level.setBlock(cursor, state, FLAGS);
                        touch();
                    }
                    if (needsShape(state)) {
                        shaped.add(cursor.asLong());
                    }
                }
                fillIndex++;
                fillOffset = 0;
            }
            touch(); // the slot holds the layout now, set by this site or not
            phase = Phase.SHAPES;
        }
        if (phase == Phase.SHAPES) {
            while (shapeIndex < shaped.size()) {
                if ((++count & (CHECK_EVERY - 1)) == 0 && System.nanoTime() >= deadline) {
                    return false;
                }
                cursor.set(shaped.getLong(shapeIndex++));
                BlockState state = level.getBlockState(cursor);
                if (!needsShape(state)) {
                    continue; // carved away by a later fill
                }
                BlockState fitted = Block.updateFromNeighbourShapes(state, level, cursor);
                if (fitted != state && !fitted.isAir()) {
                    level.setBlock(cursor, fitted, FLAGS);
                }
            }
            shaped.clear();
            shaped.trim();
            phase = Phase.DECOR;
        }
        if (phase == Phase.DECOR) {
            if (!decor.buildSlice(deadline)) {
                return false;
            }
            phase = Phase.BUILT;
            releaseChunks();
        }
        return true;
    }

    /** Bars, fences, walls, stairs and gates join their neighbours. */
    private static boolean needsShape(BlockState state) {
        Block block = state.getBlock();
        return block instanceof IronBarsBlock || block instanceof FenceBlock || block instanceof WallBlock
                || block instanceof StairBlock || block instanceof FenceGateBlock;
    }

    // ------------------------------------------------------------------ clearing

    /**
     * From now on the site is cleared: the layout's boxes and the boxes the decoration built into (known once its
     * planning is over).
     */
    public void startClearing() {
        if (phase == Phase.CLEARING || phase == Phase.CLEARED) {
            return;
        }
        clearBoxes = List.of();
        clearIndex = 0;
        clearOffset = 0;
        drained = false;
        phase = Phase.CLEARING;
    }

    /** Works on the clearing until {@code deadline}. @return whether the slot is empty again */
    public boolean clear(long deadline, long now) {
        if (phase == Phase.CLEARED) {
            return true;
        }
        if (phase != Phase.CLEARING) {
            return false;
        }
        holdChunks(now);
        if (!drained) {
            if (!decor.settled()) {
                return false; // a site restored after a restart: its decoration is still being planned
            }
            for (ChunkPos pos : chunks) {
                if (!level.getChunkSource().hasChunk(pos.x, pos.z)) {
                    return false;
                }
            }
            decor.drainFluids(); // nothing may flow while the walls around it go
            List<BlockBox> boxes = new ArrayList<>(layout.occupiedBoxes());
            boxes.addAll(decor.extraBoxes());
            clearBoxes = boxes;
            drained = true;
        }
        int count = 0;
        while (clearIndex < clearBoxes.size()) {
            BlockBox box = clearBoxes.get(clearIndex);
            long volume = box.volume();
            while (clearOffset < volume) {
                if ((++count & (CHECK_EVERY - 1)) == 0 && System.nanoTime() >= deadline) {
                    return false;
                }
                at(box, clearOffset++);
                if (!level.getBlockState(cursor).isAir()) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            clearIndex++;
            clearOffset = 0;
        }
        phase = Phase.CLEARED;
        releaseChunks();
        return true;
    }

    // ------------------------------------------------------------------ chunks

    private void at(BlockBox box, long offset) {
        int dx = (int) (offset % box.sizeX());
        int dz = (int) ((offset / box.sizeX()) % box.sizeZ());
        int dy = (int) (offset / ((long) box.sizeX() * box.sizeZ()));
        cursor.set(origin.getX() + box.minX() + dx, origin.getY() + box.minY() + dy, origin.getZ() + box.minZ() + dz);
    }

    /** Whether every chunk under the box is loaded. */
    private boolean loaded(BlockBox box) {
        int minX = (origin.getX() + box.minX()) >> 4;
        int maxX = (origin.getX() + box.maxX()) >> 4;
        int minZ = (origin.getZ() + box.minZ()) >> 4;
        int maxZ = (origin.getZ() + box.maxZ()) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void holdChunks(long now) {
        if (ticketed && now - ticketedAt < TICKET_REFRESH_TICKS) {
            return;
        }
        for (ChunkPos pos : chunks) {
            level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
        }
        ticketed = true;
        ticketedAt = now;
    }

    /** Lets the slot's chunks unload (a built site waits on disk; players load what they need). */
    public void releaseChunks() {
        if (!ticketed) {
            return;
        }
        for (ChunkPos pos : chunks) {
            level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
        }
        ticketed = false;
    }
}
