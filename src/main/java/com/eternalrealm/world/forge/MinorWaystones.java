package com.eternalrealm.world.forge;

import com.eternalrealm.block.forge.ERBlocks;
import com.eternalrealm.block.forge.WaystoneBlock;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.field.FieldLayout;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the minor waystones ({@link FieldLayout#minorWaystones()}) where they stand: a stone on a small platform in
 * the field's materials, with a lantern at each corner. Built once per world, when a player first comes near (or
 * travels there), so worlds made before they existed get them too; the floor height is kept in
 * {@link AstoriaWorldState}.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MinorWaystones {
    private static final Logger LOG = LogUtils.getLogger();
    /** A player this close makes the stone appear. */
    public static final int TRIGGER_RADIUS = 96;
    /** Half the platform (5x5). */
    private static final int HALF = 2;

    private record Materials(Block floor, Block post, Block lantern, Block base) {
    }

    /** waystone id -> server tick before which a failed build is not tried again. */
    private static final java.util.Map<String, Integer> RETRY_AFTER = new java.util.HashMap<>();

    private MinorWaystones() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server == null || server.getTickCount() % 20 != 11) {
            return;
        }
        ServerLevel astoria = server.getLevel(Realm.ASTORIA);
        if (astoria == null || astoria.players().isEmpty()) {
            return;
        }
        AstoriaWorldState state = AstoriaWorldState.get(astoria);
        for (FieldLayout.Waystone stone : FieldLayout.minorWaystones()) {
            if (state.minorWaystoneBuilt(stone.id()) || server.getTickCount() < RETRY_AFTER.getOrDefault(stone.id(), 0)
                    || !playerNear(astoria, stone) || !loaded(astoria, stone)) {
                continue;
            }
            try {
                build(astoria, stone, state);
            } catch (RuntimeException e) {
                RETRY_AFTER.put(stone.id(), server.getTickCount() + 20 * 60 * 5);
                LOG.error("Building minor waystone {} failed; retrying in 5 minutes", stone.id(), e);
            }
        }
    }

    /** Builds the stone now if it is a minor waystone that is not built yet (travel destination). */
    public static void ensureBuilt(ServerLevel astoria, FieldLayout.Waystone stone) {
        if (!FieldLayout.isMinor(stone.id()) || astoria.dimension() != Realm.ASTORIA) {
            return;
        }
        AstoriaWorldState state = AstoriaWorldState.get(astoria);
        if (!state.minorWaystoneBuilt(stone.id())) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    astoria.getChunk((stone.x() >> 4) + dx, (stone.z() >> 4) + dz);
                }
            }
            build(astoria, stone, state);
        }
    }

    private static boolean playerNear(ServerLevel level, FieldLayout.Waystone stone) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - stone.x();
            double dz = player.getZ() - stone.z();
            if (dx * dx + dz * dz <= (double) TRIGGER_RADIUS * TRIGGER_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static boolean loaded(ServerLevel level, FieldLayout.Waystone stone) {
        int r = HALF + 2;
        return level.hasChunkAt(new BlockPos(stone.x() - r, 64, stone.z() - r))
                && level.hasChunkAt(new BlockPos(stone.x() + r, 64, stone.z() + r))
                && level.hasChunkAt(new BlockPos(stone.x() - r, 64, stone.z() + r))
                && level.hasChunkAt(new BlockPos(stone.x() + r, 64, stone.z() - r));
    }

    private static Materials materials(int field) {
        return switch (field) {
            case FieldLayout.MOON_FOREST -> new Materials(Blocks.MOSSY_STONE_BRICKS, Blocks.DARK_OAK_FENCE,
                    Blocks.LANTERN, Blocks.COBBLESTONE);
            case FieldLayout.IRON_MINE -> new Materials(Blocks.POLISHED_ANDESITE, Blocks.COBBLESTONE_WALL, Blocks.LANTERN,
                    Blocks.ANDESITE);
            case FieldLayout.SACRED_FALLS -> new Materials(Blocks.POLISHED_DIORITE, Blocks.SPRUCE_FENCE, Blocks.LANTERN,
                    Blocks.STONE);
            case FieldLayout.ANCIENT_RUINS -> new Materials(Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_WALL, Blocks.LANTERN,
                    Blocks.SANDSTONE);
            case FieldLayout.SOARING_PEAKS -> new Materials(Blocks.CALCITE, Blocks.BIRCH_FENCE, Blocks.LANTERN,
                    Blocks.STONE);
            case FieldLayout.END_FOREST -> new Materials(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE_WALL,
                    Blocks.SOUL_LANTERN, Blocks.BLACKSTONE);
            case FieldLayout.FARM -> new Materials(Blocks.COBBLESTONE, Blocks.OAK_FENCE, Blocks.LANTERN, Blocks.DIRT);
            case FieldLayout.LAKESIDE -> new Materials(Blocks.SMOOTH_STONE, Blocks.BIRCH_FENCE, Blocks.LANTERN,
                    Blocks.STONE);
            case FieldLayout.HARBOUR -> new Materials(Blocks.STONE_BRICKS, Blocks.SPRUCE_FENCE, Blocks.LANTERN,
                    Blocks.STONE);
            default -> new Materials(Blocks.STONE_BRICKS, Blocks.OAK_FENCE, Blocks.LANTERN, Blocks.STONE);
        };
    }

    /** The platform at the median ground height of its 5x5, fill below it, room above it, the stone in the middle. */
    static void build(ServerLevel level, FieldLayout.Waystone stone, AstoriaWorldState state) {
        List<Integer> heights = new ArrayList<>();
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                heights.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, stone.x() + dx, stone.z() + dz));
            }
        }
        heights.sort(null);
        int floorY = heights.get(heights.size() / 2) - 1; // the floor replaces the top ground block
        Materials m = materials(stone.field());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
            for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
                boolean platform = Math.abs(dx) <= HALF && Math.abs(dz) <= HALF;
                int x = stone.x() + dx;
                int z = stone.z() + dz;
                if (platform) {
                    // supports down to the ground (at most six blocks), the floor, then room to stand
                    for (int y = floorY - 1; y >= floorY - 6; y--) {
                        pos.set(x, y, z);
                        BlockState here = level.getBlockState(pos);
                        if (!here.isAir() && here.getFluidState().isEmpty() && !here.canBeReplaced()) {
                            break;
                        }
                        level.setBlock(pos, m.base().defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    level.setBlock(pos.set(x, floorY, z), m.floor().defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                // clear plants, snow and leaves above the platform and its rim
                for (int y = floorY + 1; y <= floorY + 4; y++) {
                    pos.set(x, y, z);
                    BlockState here = level.getBlockState(pos);
                    if (!here.isAir() && (platform || here.canBeReplaced())) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        // lanterns on posts at the four corners
        for (int cx : new int[]{-HALF, HALF}) {
            for (int cz : new int[]{-HALF, HALF}) {
                level.setBlock(pos.set(stone.x() + cx, floorY + 1, stone.z() + cz), m.post().defaultBlockState(),
                        Block.UPDATE_CLIENTS);
                level.setBlock(pos.set(stone.x() + cx, floorY + 2, stone.z() + cz),
                        m.lantern().defaultBlockState().setValue(LanternBlock.HANGING, false), Block.UPDATE_CLIENTS);
            }
        }
        BlockState lower = ERBlocks.WAYSTONE.get().defaultBlockState().setValue(WaystoneBlock.FACING, Direction.SOUTH)
                .setValue(WaystoneBlock.HALF, DoubleBlockHalf.LOWER);
        // upper half first and without shape updates, so neither half sees the other missing
        level.setBlock(pos.set(stone.x(), floorY + 2, stone.z()), lower.setValue(WaystoneBlock.HALF, DoubleBlockHalf.UPPER),
                Block.UPDATE_CLIENTS);
        level.setBlock(pos.set(stone.x(), floorY + 1, stone.z()), lower, Block.UPDATE_CLIENTS);
        state.markMinorWaystoneBuilt(stone.id(), floorY);
        LOG.info("Built minor waystone {} at {} {} {}", stone.id(), stone.x(), floorY + 1, stone.z());
    }
}
