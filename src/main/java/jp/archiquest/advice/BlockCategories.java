package jp.archiquest.advice;

import jp.archiquest.analysis.MaterialKind;
import jp.archiquest.analysis.Terrain;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** 置いたブロック → アドバイスのカテゴリID（tips.json の categories のキー） */
public final class BlockCategories {
    private BlockCategories() {}

    @Nullable
    public static String categorize(BlockState st) {
        Block b = st.getBlock();
        String path = MaterialKind.path(st);

        if (b == Blocks.LIGHTNING_ROD) return "lightning_rod";
        if (b == Blocks.SCAFFOLDING) return "scaffolding";
        if (b == Blocks.TNT) return "tnt";
        if (b == Blocks.LADDER) return "ladder";
        if (b == Blocks.HAY_BLOCK) return "hay";
        if (b == Blocks.BOOKSHELF || b == Blocks.LECTERN || b == Blocks.CHISELED_BOOKSHELF) return "library";
        if (b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER
                || b == Blocks.CAMPFIRE || b == Blocks.SOUL_CAMPFIRE) return "fire";
        if (b == Blocks.CAULDRON || b == Blocks.WATER_CAULDRON) return "water";
        if (b == Blocks.SNOW_BLOCK || st.is(BlockTags.ICE)) return "snow";
        if (st.is(BlockTags.BEDS)) return "bed";
        if (Terrain.isGlass(st) || path.contains("glass")) return "glass";
        if (st.is(BlockTags.DOORS)) return "door";
        if (st.is(BlockTags.TRAPDOORS)) return "trapdoor";
        if (st.is(BlockTags.STAIRS)) return "stairs";
        if (st.is(BlockTags.FENCES) || st.is(BlockTags.WALLS) || b == Blocks.IRON_BARS) return "fence";
        if (st.is(BlockTags.WOOL_CARPETS)) return "carpet";
        if (st.is(BlockTags.WOOL)) return "wool";
        if (st.is(BlockTags.SLABS)) return "slab";
        if (st.getLightEmission() > 0) return "light";
        if (path.endsWith("_concrete")) return "concrete";
        if (path.contains("copper")) return "copper";
        if (path.contains("iron") || b == Blocks.CHAIN) return "steel";
        if (st.is(BlockTags.LOGS) || st.is(BlockTags.PLANKS)) return "wood";
        if (st.is(BlockTags.TERRACOTTA) || path.contains("terracotta")) return "tile";
        if (path.contains("brick")) return "masonry";
        if (path.contains("stone") || path.contains("cobble") || path.contains("andesite") || path.contains("diorite")
                || path.contains("granite") || path.contains("deepslate") || path.contains("tuff")
                || path.contains("basalt") || path.contains("quartz")) return "stone";
        if (st.is(BlockTags.LEAVES) || st.is(BlockTags.FLOWERS) || st.is(BlockTags.SAPLINGS)) return "green";
        if (st.is(BlockTags.SAND) || b == Blocks.GRAVEL || st.is(BlockTags.DIRT)) return "ground";
        return null;
    }
}
