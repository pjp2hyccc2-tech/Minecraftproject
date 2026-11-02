package com.birdiecraft.world;

import com.birdiecraft.config.GolfConfig;
import com.birdiecraft.golf.engine.Lie;
import com.birdiecraft.registry.ModBlocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Maps blocks to golf lies. */
public final class SurfaceResolver {
    private static Map<Block, Lie> golfBlocks;

    private SurfaceResolver() {
    }

    private static Map<Block, Lie> golf() {
        if (golfBlocks == null) {
            Map<Block, Lie> m = new IdentityHashMap<>();
            m.put(ModBlocks.TEE_BOX.get(), Lie.TEE);
            m.put(ModBlocks.TEE_MARKER.get(), Lie.TEE);
            m.put(ModBlocks.FAIRWAY.get(), Lie.FAIRWAY);
            m.put(ModBlocks.FAIRWAY_DARK.get(), Lie.FAIRWAY);
            m.put(ModBlocks.FRINGE.get(), Lie.FRINGE);
            m.put(ModBlocks.GREEN.get(), Lie.GREEN);
            m.put(ModBlocks.GOLF_CUP.get(), Lie.GREEN);
            m.put(ModBlocks.ROUGH.get(), Lie.ROUGH);
            m.put(ModBlocks.ROUGH_DRY.get(), Lie.ROUGH);
            m.put(ModBlocks.ROUGH_SNOW.get(), Lie.ROUGH);
            m.put(ModBlocks.ROUGH_ASH.get(), Lie.ROUGH);
            m.put(ModBlocks.DEEP_ROUGH.get(), Lie.DEEP_ROUGH);
            m.put(ModBlocks.BUNKER_SAND.get(), Lie.BUNKER);
            m.put(ModBlocks.BUNKER_WHITE.get(), Lie.BUNKER);
            m.put(ModBlocks.BUNKER_RED.get(), Lie.BUNKER);
            m.put(ModBlocks.BUNKER_ASH.get(), Lie.BUNKER);
            m.put(ModBlocks.CART_PATH.get(), Lie.CART_PATH);
            golfBlocks = m;
        }
        return golfBlocks;
    }

    public static Lie lieOf(BlockState state) {
        Lie golf = golf().get(state.getBlock());
        if (golf != null) return golf;
        Block b = state.getBlock();
        if (state.is(BlockTags.ICE)) return Lie.ICE;
        if (state.is(BlockTags.SAND) || b == Blocks.GRAVEL || b == Blocks.SUSPICIOUS_GRAVEL) return Lie.NATURAL_SAND;
        if (b == Blocks.SNOW_BLOCK || b == Blocks.SNOW || b == Blocks.POWDER_SNOW) return Lie.SOFT;
        if (state.is(BlockTags.DIRT)) return Lie.NATURAL;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) return Lie.WOOD;
        return Lie.HARD;
    }

    public static boolean isGolfBlock(BlockState state) {
        return golf().containsKey(state.getBlock());
    }

    /** In-bounds rule used by matches (server config dependent). */
    public static Predicate<Lie> inBoundsRule() {
        boolean naturalOb = GolfConfig.NATURAL_GROUND_IS_OB.get();
        return lie -> lie.courseSurface || (!naturalOb && !lie.isHazard());
    }
}
