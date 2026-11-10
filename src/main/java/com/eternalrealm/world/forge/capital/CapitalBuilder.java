package com.eternalrealm.world.forge.capital;

import com.eternalrealm.world.forge.AstoriaWorldState;
import com.eternalrealm.world.forge.FieldPieces;
import com.eternalrealm.world.gen.HubSpot;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds the blueprint capital ({@link CapitalBlueprint}) in アストリア, a few milliseconds per server tick so the game
 * keeps running: tiles nearest the spawn first (every column of a tile is written top to bottom, which also clears
 * the previous town), then the shapes of stairs/fences/panes/walls are fitted to their neighbours, block entity data
 * (banners, signs, sunbeams...) and the decoration entities are applied, waterfalls start flowing and the natural
 * terrain is eased into the city edge. Players see the progress in the action bar; anyone a new tile buries is lifted
 * out. When done the world remembers the blueprint's hash, so this runs once per blueprint version.
 */
public final class CapitalBuilder {
    private static final Logger LOG = LogUtils.getLogger();
    /** Work per server tick. */
    private static final long BUDGET_NANOS = 14_000_000L;
    /** Natural terrain is eased into the city over this many blocks outside the footprint. */
    private static final int BLEND = 24;
    private static final String ENTITY_TAG = "eternalrealm_capital";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static Job job;
    private static Consumer<ServerLevel> onCompleted = level -> {
    };

    private CapitalBuilder() {
    }

    /** What to do once a build finished (place the NPCs at the new spots...). */
    public static void onCompleted(Consumer<ServerLevel> callback) {
        onCompleted = callback;
    }

    /** Whether a build is running. */
    public static boolean building() {
        return job != null;
    }

    /** Progress of the running build in percent (tiles placed), or -1 without a build. */
    public static int progressPercent() {
        Job current = job;
        return current == null ? -1 : (int) (100L * current.tileIndex / Math.max(1, current.order.size()));
    }

    /** Starts building {@code blueprint} unless this world already has it (or a build is running). */
    public static void ensureStarted(ServerLevel astoria, CapitalBlueprint blueprint, int groundY, BlockPos spawn) {
        if (job != null || blueprint.build().equals(stateBuild(astoria))) {
            return;
        }
        LOG.info("Building the capital blueprint {} at y={} ({} tiles)", blueprint.build(), groundY, blueprint.tiles().size());
        job = new Job(astoria, blueprint, groundY, spawn);
    }

    private static String stateBuild(ServerLevel astoria) {
        return AstoriaWorldState.get(astoria).capitalBuild();
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || job == null) {
            return;
        }
        Job current = job;
        try {
            if (current.step(System.nanoTime() + BUDGET_NANOS)) {
                job = null;
                current.finish();
                onCompleted.accept(current.level);
            }
        } catch (RuntimeException e) {
            job = null;
            LOG.error("Building the capital failed; it will be retried on the next start", e);
        }
    }

    public static void onServerStopped() {
        job = null;
    }

    // ------------------------------------------------------------------------------------------------ the job

    private static final class Job {
        private enum Phase { TILES, SHAPES, BLOCK_ENTITIES, ENTITIES, FLUIDS, BLEND, DONE }

        final ServerLevel level;
        final CapitalBlueprint blueprint;
        final int groundY;
        final BlockState[] palette;
        final List<CapitalBlueprint.Tile> order;
        final LongArrayList shaped = new LongArrayList();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;
        Phase phase = Phase.TILES;
        int tileIndex;
        short[] voxels;
        int voxel;
        int index;
        int blendColumn;
        long placed;
        long started = System.nanoTime();
        int lastPercent = -1;

        Job(ServerLevel level, CapitalBlueprint blueprint, int groundY, BlockPos spawn) {
            this.level = level;
            this.blueprint = blueprint;
            this.groundY = groundY;
            List<String> problems = new ArrayList<>();
            this.palette = blueprint.resolvePalette(level.holderLookup(Registries.BLOCK), problems);
            if (!problems.isEmpty()) {
                LOG.error("{} capital palette entries are invalid and left out: {}", problems.size(), problems);
            }
            int size = blueprint.tileSize();
            this.order = new ArrayList<>(blueprint.tiles());
            this.order.sort(Comparator.comparingDouble(tile -> {
                double cx = tile.x() * size + size / 2.0 - spawn.getX();
                double cz = tile.z() * size + size / 2.0 - spawn.getZ();
                return cx * cx + cz * cz;
            }));
            int x0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE;
            int z0 = Integer.MAX_VALUE;
            int z1 = Integer.MIN_VALUE;
            for (CapitalBlueprint.Tile tile : blueprint.tiles()) {
                x0 = Math.min(x0, tile.x() * size);
                x1 = Math.max(x1, tile.x() * size + size - 1);
                z0 = Math.min(z0, tile.z() * size);
                z1 = Math.max(z1, tile.z() * size + size - 1);
            }
            this.minX = x0;
            this.maxX = x1;
            this.minZ = z0;
            this.maxZ = z1;
        }

        /** @return true when everything is done */
        boolean step(long deadline) {
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
                        phase = Phase.BLEND;
                        blendColumn = 0;
                    }
                    case BLEND -> {
                        if (!blend(deadline)) {
                            return false;
                        }
                        phase = Phase.DONE;
                    }
                    case DONE -> {
                        return true;
                    }
                }
            }
            return phase == Phase.DONE;
        }

        // ---- tiles

        private boolean tiles(long deadline) {
            int size = blueprint.tileSize();
            int height = blueprint.height();
            int minBuild = level.getMinBuildHeight();
            int maxBuild = level.getMaxBuildHeight() - 1;
            while (tileIndex < order.size()) {
                CapitalBlueprint.Tile tile = order.get(tileIndex);
                if (voxels == null) {
                    try {
                        voxels = blueprint.decode(level.getServer().getResourceManager(), tile);
                    } catch (IOException e) {
                        LOG.error("Capital tile {} skipped", tile.file(), e);
                        tileIndex++;
                        continue;
                    }
                    voxel = 0;
                    for (int cx = 0; cx < size; cx += 16) {
                        for (int cz = 0; cz < size; cz += 16) {
                            level.getChunk((tile.x() * size + cx) >> 4, (tile.z() * size + cz) >> 4);
                        }
                    }
                }
                while (voxel < voxels.length) {
                    if ((voxel & 1023) == 0 && System.nanoTime() >= deadline) {
                        progress();
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
                    int y = groundY + blueprint.minY() + at % height;
                    if (y < minBuild || y > maxBuild) {
                        continue;
                    }
                    cursor.set(tile.x() * size + column / size, y, tile.z() * size + column % size);
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
                liftBuriedPlayers(tile.x() * size, tile.z() * size, size);
                voxels = null;
                tileIndex++;
            }
            return true;
        }

        private void liftBuriedPlayers(int x0, int z0, int size) {
            for (ServerPlayer player : level.players()) {
                BlockPos feet = player.blockPosition();
                if (feet.getX() < x0 || feet.getX() >= x0 + size || feet.getZ() < z0 || feet.getZ() >= z0 + size) {
                    continue;
                }
                if (!level.getBlockState(feet).isSuffocating(level, feet)
                        && !level.getBlockState(feet.above()).isSuffocating(level, feet.above())) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ());
                player.teleportTo(level, player.getX(), top, player.getZ(), player.getYRot(), player.getXRot());
            }
        }

        private void progress() {
            int percent = (int) (100L * tileIndex / Math.max(1, order.size()));
            if (percent == lastPercent) {
                return;
            }
            lastPercent = percent;
            Component message = Component.translatable("message.eternalrealm.capital.building", percent)
                    .withStyle(ChatFormatting.GOLD);
            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(message, true);
            }
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
            for (CapitalBlueprint.BlockEntityData data : blueprint.blockEntities()) {
                BlockPos pos = world(data.pos());
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity == null) {
                    LOG.warn("Capital block entity data at {} has no block entity ({})", pos, level.getBlockState(pos));
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
                    LOG.warn("Capital block entity data at {} is invalid: {}", pos, e.getMessage());
                }
            }
        }

        private void entities() {
            AABB area = new AABB(minX - 2, groundY + blueprint.minY(), minZ - 2, maxX + 3, groundY + blueprint.maxY() + 1,
                    maxZ + 3);
            for (Entity old : level.getEntities((Entity) null, area, e -> e.getPersistentData().getBoolean(ENTITY_TAG))) {
                old.discard();
            }
            for (CapitalBlueprint.EntityData data : blueprint.entities()) {
                try {
                    CompoundTag tag = TagParser.parseTag(data.snbt());
                    tag.putString("id", data.type());
                    double x = data.x();
                    double y = groundY + data.y();
                    double z = data.z();
                    Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
                        e.moveTo(x, y, z, data.yaw(), e.getXRot());
                        return e;
                    });
                    if (entity == null) {
                        LOG.warn("Capital entity {} could not be created", data.type());
                        continue;
                    }
                    entity.getPersistentData().putBoolean(ENTITY_TAG, true);
                    level.addFreshEntity(entity);
                } catch (Exception e) {
                    LOG.warn("Capital entity {} is invalid: {}", data.type(), e.getMessage());
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

        // ---- easing the natural terrain into the city edge

        private boolean blend(long deadline) {
            int width = maxX - minX + 1 + 2 * BLEND;
            int depth = maxZ - minZ + 1 + 2 * BLEND;
            int total = width * depth;
            BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
            BlockState dirt = Blocks.DIRT.defaultBlockState();
            while (blendColumn < total) {
                if ((blendColumn & 63) == 0 && System.nanoTime() >= deadline) {
                    return false;
                }
                int i = blendColumn++;
                int x = minX - BLEND + i / depth;
                int z = minZ - BLEND + i % depth;
                int dx = Math.max(minX - x, x - maxX);
                int dz = Math.max(minZ - z, z - maxZ);
                int distance = Math.max(dx, dz);
                if (distance <= 0 || distance > BLEND || FieldPieces.covers(x, z)) {
                    continue;   // the field set pieces outside the gates (waystone shrine, road lanterns) stay
                }
                level.getChunk(x >> 4, z >> 4);
                int natural = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                cursor.set(x, natural, z);
                if (!level.getFluidState(cursor).isEmpty()) {
                    continue; // leave lakes and rivers alone
                }
                float t = distance / (float) BLEND;
                t = t * t * (3.0F - 2.0F * t);
                int target = Math.round(Mth.lerp(t, groundY, natural));
                if (target == natural) {
                    continue;
                }
                if (target < natural) {
                    for (int y = natural; y > target; y--) {
                        cursor.set(x, y, z);
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                } else {
                    for (int y = natural + 1; y < target; y++) {
                        cursor.set(x, y, z);
                        level.setBlock(cursor, dirt, FLAGS);
                    }
                }
                cursor.set(x, target, z);
                level.setBlock(cursor, grass, FLAGS);
            }
            return true;
        }

        private BlockPos world(BlockPos local) {
            return new BlockPos(local.getX(), groundY + local.getY(), local.getZ());
        }

        void finish() {
            AstoriaWorldState.get(level).markCapitalBuilt(blueprint.build(), groundY);
            long ms = (System.nanoTime() - started) / 1_000_000L;
            LOG.info("Built the capital blueprint {}: {} blocks changed, {} shaped, {} block entities, {} entities in {} s",
                    blueprint.build(), placed, shaped.size(), blueprint.blockEntities().size(),
                    blueprint.entities().size(), ms / 1000);
            Component message = Component.translatable("message.eternalrealm.capital.built").withStyle(ChatFormatting.GOLD);
            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(message, true);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ layout

    /** Spot names of the blueprint for the classic hub spots. */
    public static HubSpot hubSpot(String name) {
        return switch (name) {
            case "spawn" -> HubSpot.SPAWN;
            case "respawn" -> HubSpot.RESPAWN;
            case "npc.quest_board" -> HubSpot.QUEST_BOARD;
            case "npc.item_shop" -> HubSpot.ITEM_SHOP;
            case "npc.blacksmith" -> HubSpot.BLACKSMITH;
            case "npc.job_master" -> HubSpot.JOB_MASTER;
            case "npc.auction" -> HubSpot.AUCTION_HOUSE;
            case "npc.mailbox" -> HubSpot.MAILBOX;
            case "npc.guild_registrar" -> HubSpot.GUILD_REGISTRAR;
            case "npc.gatekeeper" -> HubSpot.GATEKEEPER;
            case "npc.arena_master" -> HubSpot.ARENA_MASTER;
            case "npc.hall_of_fame" -> HubSpot.HALL_OF_FAME;
            case "board.ranking" -> HubSpot.RANKING_BOARD;
            default -> null;
        };
    }
}
