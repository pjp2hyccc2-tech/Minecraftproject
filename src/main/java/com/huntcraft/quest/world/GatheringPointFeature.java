package com.huntcraft.quest.world;

import com.huntcraft.HuntCraft;
import com.huntcraft.quest.block.GatherKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Places one gathering point on natural ground (never in fluids, on leaves or trunks). */
public class GatheringPointFeature extends Feature<NoneFeatureConfiguration> {
    private final GatherKind kind;

    public GatheringPointFeature(GatherKind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        try {
            WorldGenLevel level = ctx.level();
            BlockPos.MutableBlockPos p = ctx.origin().mutable();
            int minY = level.getMinBuildHeight() + 4;
            for (int i = 0; i < 12 && p.getY() > minY; i++) {
                BlockState here = level.getBlockState(p);
                BlockPos belowPos = p.below();
                BlockState below = level.getBlockState(belowPos);
                if (!here.getFluidState().isEmpty() || !below.getFluidState().isEmpty()) return false;
                boolean free = here.isAir() || (here.canBeReplaced() && !here.is(BlockTags.LEAVES));
                if (free && isGround(below) && below.isFaceSturdy(level, belowPos, Direction.UP)) {
                    level.setBlock(p, kind.block().defaultBlockState(), 2);
                    return true;
                }
                if (!free && !here.is(BlockTags.LEAVES)) return false;
                p.move(Direction.DOWN);
            }
        } catch (RuntimeException e) {
            HuntCraft.LOGGER.debug("gathering point placement failed", e);
        }
        return false;
    }

    private static boolean isGround(BlockState s) {
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) return false;
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.SAND) || s.is(Blocks.BASALT) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.BLACKSTONE)
                || s.is(Blocks.PACKED_ICE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.CLAY) || s.is(Blocks.ANDESITE);
    }
}
