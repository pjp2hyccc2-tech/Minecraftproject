package jp.satsujinki.game;

import jp.satsujinki.block.entity.TrapBlockEntity;
import jp.satsujinki.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/** One-click builders for the trap kits and the execution arena. */
public final class Structures {
    private Structures() {
    }

    /** 5x4x5 giant mixer. origin = core block (bottom center). Interior is 3x3x3 above the core. */
    public static Map<BlockPos, BlockState> mixer(BlockPos core) {
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                m.put(core.offset(dx, 0, dz), (dx == 0 && dz == 0 ? ModBlocks.MIXER_CORE.get() : ModBlocks.MIXER_CASING.get()).defaultBlockState());
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                for (int y = 1; y <= 3; y++) {
                    m.put(core.offset(dx, y, dz), ring ? ModBlocks.MIXER_GLASS.get().defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        return m;
    }

    /** 5x7x5 spike press. origin = center of the floor (spike beds). Press head hangs at y+5 under a roof at y+6. */
    public static Map<BlockPos, BlockState> press(BlockPos floor) {
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        BlockState plate = ModBlocks.RUSTED_PLATE.get().defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                m.put(floor.offset(dx, -1, dz), plate);
                m.put(floor.offset(dx, 0, dz), inner ? ModBlocks.SPIKE_BED.get().defaultBlockState() : ModBlocks.BLOODY_TILES.get().defaultBlockState());
                for (int y = 1; y <= 5; y++) {
                    if (corner) m.put(floor.offset(dx, y, dz), plate);
                    else if (!(dx == 0 && dz == 0 && y == 5)) m.put(floor.offset(dx, y, dz), Blocks.AIR.defaultBlockState());
                }
                m.put(floor.offset(dx, 6, dz), plate);
            }
        }
        m.put(floor.offset(0, 5, 0), ModBlocks.PRESS_HEAD.get().defaultBlockState());
        return m;
    }

    /** 17x5x17 execution arena: tiled floor, rusted walls with bars, host terminal in the middle. */
    public static Map<BlockPos, BlockState> arena(BlockPos center, Direction facing) {
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        int r = 8;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean wall = Math.abs(dx) == r || Math.abs(dz) == r;
                boolean checker = ((dx + dz) & 1) == 0;
                m.put(center.offset(dx, -1, dz), wall ? ModBlocks.RUSTED_PLATE.get().defaultBlockState()
                        : (checker || ((dx * 7 + dz * 13) % 11 == 0) ? ModBlocks.EXECUTION_TILES.get() : ModBlocks.BLOODY_TILES.get()).defaultBlockState());
                for (int y = 0; y <= 3; y++) {
                    BlockState st;
                    if (wall) {
                        boolean window = y == 2 && Math.abs(dx + dz) % 3 == 1;
                        st = window ? Blocks.IRON_BARS.defaultBlockState() : ModBlocks.RUSTED_PLATE.get().defaultBlockState();
                    } else {
                        st = Blocks.AIR.defaultBlockState();
                    }
                    m.put(center.offset(dx, y, dz), st);
                }
            }
        }
        // lights on the wall tops
        for (int i = -r; i <= r; i += 4) {
            m.put(center.offset(i, 4, -r), Blocks.REDSTONE_LAMP.defaultBlockState());
            m.put(center.offset(i, 4, r), Blocks.REDSTONE_LAMP.defaultBlockState());
            m.put(center.offset(-r, 4, i), Blocks.REDSTONE_LAMP.defaultBlockState());
            m.put(center.offset(r, 4, i), Blocks.REDSTONE_LAMP.defaultBlockState());
        }
        m.put(center, ModBlocks.HOST_TERMINAL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
        return m;
    }

    /** Kits terraform freely, but never break unbreakable blocks or containers / other block entities. */
    public static boolean canPlace(ServerLevel level, Map<BlockPos, BlockState> plan) {
        for (BlockPos p : plan.keySet()) {
            if (level.isOutsideBuildHeight(p)) return false;
            BlockState cur = level.getBlockState(p);
            if (cur.getDestroySpeed(level, p) < 0) return false;
            if (level.getBlockEntity(p) != null) return false;
        }
        return true;
    }

    public static void build(ServerLevel level, Map<BlockPos, BlockState> plan, @Nullable Player owner) {
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            if (level.getBlockState(e.getKey()).getDestroySpeed(level, e.getKey()) < 0) continue;
            level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
        }
        if (owner != null) {
            for (BlockPos p : plan.keySet()) {
                if (level.getBlockEntity(p) instanceof TrapBlockEntity t) t.setOwner(owner.getUUID());
            }
        }
    }
}
