package jp.archiquest.analysis;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

/** 地面・建物ブロックの判定 */
public final class Terrain {
    private Terrain() {}

    /** 自然の地面（地盤・水面）か */
    public static boolean isGround(BlockState st) {
        return st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(BlockTags.BASE_STONE_OVERWORLD)
                || st.is(BlockTags.BASE_STONE_NETHER) || st.is(Blocks.GRAVEL) || st.is(Blocks.CLAY)
                || st.is(Blocks.END_STONE) || st.is(Blocks.BEDROCK) || st.is(Tags.Blocks.ORES)
                || st.getBlock() instanceof LiquidBlock;
    }

    /** 建物を構成するブロックか（空気・液体・草花・地面・葉っぱ以外） */
    public static boolean isStructural(BlockState st) {
        if (st.isAir()) return false;
        if (st.getBlock() instanceof LiquidBlock) return false;
        if (st.canBeReplaced()) return false;
        if (isGround(st)) return false;
        return !st.is(BlockTags.LEAVES);
    }

    /** 人が通り抜けられる（当たり判定がない）ブロックか。部屋の空間判定に使う */
    public static boolean passable(BlockGetter level, BlockPos pos, BlockState st) {
        return st.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean isGlass(BlockState st) {
        return st.is(Tags.Blocks.GLASS) || st.is(Tags.Blocks.GLASS_PANES);
    }

    /** 開閉できる開口部（ドア・トラップドア・フェンスゲート） */
    public static boolean isOpening(BlockState st) {
        return st.is(BlockTags.DOORS) || st.is(BlockTags.TRAPDOORS) || st.is(BlockTags.FENCE_GATES);
    }
}
