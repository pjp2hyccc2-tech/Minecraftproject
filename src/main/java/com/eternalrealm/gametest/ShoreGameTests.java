package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.forge.ShorePatch;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Shores one can climb out onto (2026-11-14: 「川に落ちたら登れなくなってるから川沿いのブロックを1段ずつ下げて」): a pool
 * with banks one and two blocks above the water, a wall of two behind a bank, a bank someone built and a flower on a
 * bank; the patch lowers the natural banks by one, the wall behind with them, and leaves what was built.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShoreGameTests {
    private static final String ARENA = "arena";

    private ShoreGameTests() {
    }

    @GameTest(template = ARENA)
    public static void banksGoDownOneSoSwimmersCanClimbOut(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.GRASS_BLOCK);
            }
        }
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.WATER); // the water's top block: y 2
            }
        }
        // west: one above the water; east: two above, and a wall of two behind it; north: built; south: a flower
        raise(helper, 2, 4, 3);
        raise(helper, 6, 4, 4);
        raise(helper, 7, 4, 5);
        helper.setBlock(new BlockPos(4, 3, 2), Blocks.OAK_PLANKS);
        raise(helper, 4, 6, 3);
        helper.setBlock(new BlockPos(4, 4, 6), Blocks.POPPY);

        BlockPos from = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockPos to = helper.absolutePos(new BlockPos(8, 0, 8));
        BlockPos bank = helper.absolutePos(new BlockPos(2, 0, 4));
        BlockPos pool = helper.absolutePos(new BlockPos(3, 0, 4));
        String seen = "bank " + ShorePatch.describeForTest(helper.getLevel(), bank.getX(), bank.getZ()) + ", pool "
                + ShorePatch.describeForTest(helper.getLevel(), pool.getX(), pool.getZ()) + ", floor at "
                + helper.absolutePos(new BlockPos(0, 1, 0)).getY();
        int lowered = ShorePatch.mend(helper.getLevel(), Math.min(from.getX(), to.getX()),
                Math.min(from.getZ(), to.getZ()), Math.max(from.getX(), to.getX()), Math.max(from.getZ(), to.getZ()),
                (x, z) -> true);

        top(helper, 2, 4, 2, "one above the water: now level with it [" + seen + ", lowered " + lowered + "]");
        top(helper, 6, 4, 3, "two above: now one above");
        top(helper, 7, 4, 4, "the wall behind it went down with it");
        check(helper.getBlockState(new BlockPos(4, 3, 2)).is(Blocks.OAK_PLANKS), "what someone built stays");
        top(helper, 4, 6, 2, "the flowered bank: level with the water");
        check(helper.getBlockState(new BlockPos(4, 4, 6)).isAir(), "its flower is gone");
        top(helper, 1, 4, 2, "ground already level with the water is left alone");
        check(lowered == 4, "four columns lowered: " + lowered);
        helper.succeed();
    }

    /** A grass-topped column up to {@code top}. */
    private static void raise(GameTestHelper helper, int x, int z, int top) {
        for (int y = 2; y < top; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
        }
        helper.setBlock(new BlockPos(x, top, z), Blocks.GRASS_BLOCK);
    }

    /** The column's top block is grass at {@code y} with air above. */
    private static void top(GameTestHelper helper, int x, int z, int y, String what) {
        Block at = helper.getBlockState(new BlockPos(x, y, z)).getBlock();
        boolean clear = helper.getBlockState(new BlockPos(x, y + 1, z)).isAir();
        check(at == Blocks.GRASS_BLOCK && clear, what + ": " + at + " at " + y + (clear ? "" : ", not clear above"));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
