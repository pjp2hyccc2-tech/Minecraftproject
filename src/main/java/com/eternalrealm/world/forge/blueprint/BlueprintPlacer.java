package com.eternalrealm.world.forge.blueprint;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Places a {@link Blueprint} at an origin a slice at a time, so the server keeps running: tiles nearest a chosen point
 * first (every column of a tile is written top to bottom; void leaves the world as it is), then the shapes of
 * stairs/fences/panes/walls are fitted to their neighbours, the block entity data (banners, signs, sunbeams...) and
 * the entities are applied (entities of an earlier placement with the same tag inside the build's box are replaced)
 * and fluids that should start flowing get a tick. The capital ({@code CapitalBuilder}) and the estates use it.
 * Server thread only.
 */
public final class BlueprintPlacer {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Optional callbacks of the owner of a placement. */
    public interface Hooks {
        /** A tile's columns were written (world x/z of its corner and its size). */
        default void afterTile(int x0, int z0, int size) {
        }

        /** Placing stopped for this tick in the middle of the tiles; {@code percent} of them are done. */
        default void progress(int percent) {
        }

        /** Called for every entity before it is added (the estates mark what they own). */
        default void entityCreated(Entity entity) {
        }
    }

    private enum Phase { TILES, SHAPES, BLOCK_ENTITIES, ENTITIES, FLUIDS, DONE }

    private final ServerLevel level;
    private final Blueprint blueprint;
    private final BlockPos origin;
    private final String entityTag;
    private final Hooks hooks;
    private final BlockState[] palette;
    private final List<Blueprint.Tile> order;
    private final LongArrayList shaped = new LongArrayList();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private Phase phase = Phase.TILES;
    private int tileIndex;
    private short[] voxels;
    private int voxel;
    private int index;
    private long placed;

    /**
     * @param origin     world position of the blueprint's local (0, 0, 0)
     * @param nearX      tiles nearest this world x/z are placed first
     * @param entityTag  persistent-data flag set on the placed entities (and used to replace an earlier placement's)
     * @param problems   receives the palette entries that do not parse on this game version
     */
    public BlueprintPlacer(ServerLevel level, Blueprint blueprint, BlockPos origin, int nearX, int nearZ,
                           String entityTag, Hooks hooks, Consumer<List<String>> problems) {
        this.level = level;
        this.blueprint = blueprint;
        this.origin = origin.immutable();
        this.entityTag = entityTag;
        this.hooks = hooks == null ? new Hooks() {
        } : hooks;
        List<String> invalid = new ArrayList<>();
        this.palette = blueprint.resolvePalette(level.holderLookup(Registries.BLOCK), invalid);
        if (!invalid.isEmpty()) {
            problems.accept(invalid);
        }
        int size = blueprint.tileSize();
        this.order = new ArrayList<>(blueprint.tiles());
        this.order.sort(Comparator.comparingDouble(tile -> {
            double cx = origin.getX() + tile.x() * size + size / 2.0 - nearX;
            double cz = origin.getZ() + tile.z() * size + size / 2.0 - nearZ;
            return cx * cx + cz * cz;
        }));
        int[] bounds = blueprint.tileBounds();
        this.minX = origin.getX() + bounds[0];
        this.minZ = origin.getZ() + bounds[1];
        this.maxX = origin.getX() + bounds[2];
        this.maxZ = origin.getZ() + bounds[3];
    }

    /** @return true when everything is placed */
    public boolean step(long deadline) {
        while (System.nanoTime() < deadline) {
            switch (phase) {
                case TILES -> {
                    if (!tiles(deadline)) {
                        return false;
                    }
                    phase = Phase.SHAPES;
                    index = 0;
                }
                case SHAPES -> {
                    if (!shapes(deadline)) {
                        return false;
                    }
                    phase = Phase.BLOCK_ENTITIES;
                    index = 0;
                }
                case BLOCK_ENTITIES -> {
                    blockEntities();
                    phase = Phase.ENTITIES;
                }
                case ENTITIES -> {
                    entities();
                    phase = Phase.FLUIDS;
                }
                case FLUIDS -> {
                    fluids();
                    phase = Phase.DONE;
                }
                case DONE -> {
                    return true;
                }
            }
        }
        return phase == Phase.DONE;
    }

    public boolean done() {
        return phase == Phase.DONE;
    }

    /** Tiles placed in percent (100 once the tiles are done). */
    public int percent() {
        return (int) (100L * tileIndex / Math.max(1, order.size()));
    }

    /** Blocks that changed. */
    public long placed() {
        return placed;
    }

    /** Stairs, fences, panes and walls whose shape was fitted. */
    public int shapedCount() {
        return shaped.size();
    }

    /** World bounds of the tiles: min/max x and z (inclusive). */
    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    public Blueprint blueprint() {
        return blueprint;
    }

    public BlockPos origin() {
        return origin;
    }

    // ---- tiles

    private boolean tiles(long deadline) {
        int size = blueprint.tileSize();
        int height = blueprint.height();
        int minBuild = level.getMinBuildHeight();
        int maxBuild = level.getMaxBuildHeight() - 1;
        while (tileIndex < order.size()) {
            Blueprint.Tile tile = order.get(tileIndex);
            int tileX = origin.getX() + tile.x() * size;
            int tileZ = origin.getZ() + tile.z() * size;
            if (voxels == null) {
                try {
                    voxels = blueprint.decode(level.getServer().getResourceManager(), tile);
                } catch (IOException e) {
                    LOG.error("Blueprint tile {} skipped", tile.file(), e);
                    tileIndex++;
                    continue;
                }
                voxel = 0;
                for (int cx = 0; cx < size; cx += 16) {
                    for (int cz = 0; cz < size; cz += 16) {
                        level.getChunk((tileX + cx) >> 4, (tileZ + cz) >> 4);
                    }
                }
            }
            while (voxel < voxels.length) {
                if ((voxel & 1023) == 0 && System.nanoTime() >= deadline) {
                    hooks.progress(percent());
                    return false;
                }
                int paletteIndex = voxels[voxel];
                int at = voxel++;
                if (paletteIndex == 0) {
                    continue;
                }
                BlockState state = palette[paletteIndex];
                if (state == null) {
                    continue;
                }
                int column = at / height;
                int y = origin.getY() + blueprint.minY() + at % height;
                if (y < minBuild || y > maxBuild) {
                    continue;
                }
                cursor.set(tileX + column / size, y, tileZ + column % size);
                if (level.getBlockState(cursor) != state) {
                    level.setBlock(cursor, state, FLAGS);
                    placed++;
                }
                Block block = state.getBlock();
                if (block instanceof StairBlock || block instanceof FenceBlock || block instanceof IronBarsBlock
                        || block instanceof WallBlock || block instanceof FenceGateBlock) {
                    shaped.add(cursor.asLong());
                }
            }
            hooks.afterTile(tileX, tileZ, size);
            voxels = null;
            tileIndex++;
        }
        return true;
    }

    // ---- shapes, data, entities, fluids

    private boolean shapes(long deadline) {
        while (index < shaped.size()) {
            if ((index & 255) == 0 && System.nanoTime() >= deadline) {
                return false;
            }
            cursor.set(shaped.getLong(index++));
            BlockState state = level.getBlockState(cursor);
            BlockState fitted = Block.updateFromNeighbourShapes(state, level, cursor);
            if (fitted != state && !fitted.isAir()) {
                level.setBlock(cursor, fitted, FLAGS);
            }
        }
        return true;
    }

    private void blockEntities() {
        for (Blueprint.BlockEntityData data : blueprint.blockEntities()) {
            BlockPos pos = world(data.pos());
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                LOG.warn("Blueprint {}: block entity data at {} has no block entity ({})", blueprint.folder(), pos,
                        level.getBlockState(pos));
                continue;
            }
            try {
                CompoundTag tag = blockEntity.saveWithoutMetadata();
                tag.merge(TagParser.parseTag(data.snbt()));
                blockEntity.load(tag);
                blockEntity.setChanged();
                BlockState state = level.getBlockState(pos);
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            } catch (Exception e) {
                LOG.warn("Blueprint {}: block entity data at {} is invalid: {}", blueprint.folder(), pos, e.getMessage());
            }
        }
    }

    /** The box the placed entities may be in (a little larger than the tiles). */
    public AABB entityBox() {
        return new AABB(minX - 2, origin.getY() + blueprint.minY(), minZ - 2, maxX + 3,
                origin.getY() + blueprint.maxY() + 1, maxZ + 3);
    }

    private void entities() {
        for (Entity old : level.getEntities((Entity) null, entityBox(), e -> e.getPersistentData().getBoolean(entityTag))) {
            old.discard();
        }
        for (Blueprint.EntityData data : blueprint.entities()) {
            try {
                CompoundTag tag = TagParser.parseTag(data.snbt());
                tag.putString("id", data.type());
                double x = origin.getX() + data.x();
                double y = origin.getY() + data.y();
                double z = origin.getZ() + data.z();
                Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
                    e.moveTo(x, y, z, data.yaw(), e.getXRot());
                    return e;
                });
                if (entity == null) {
                    LOG.warn("Blueprint {}: entity {} could not be created", blueprint.folder(), data.type());
                    continue;
                }
                entity.getPersistentData().putBoolean(entityTag, true);
                hooks.entityCreated(entity);
                level.addFreshEntity(entity);
            } catch (Exception e) {
                LOG.warn("Blueprint {}: entity {} is invalid: {}", blueprint.folder(), data.type(), e.getMessage());
            }
        }
    }

    private void fluids() {
        for (BlockPos local : blueprint.fluidTicks()) {
            BlockPos pos = world(local);
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.isEmpty()) {
                level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
            }
        }
    }

    /** World position of a local blueprint position. */
    public BlockPos world(BlockPos local) {
        return origin.offset(local.getX(), local.getY(), local.getZ());
    }
}
