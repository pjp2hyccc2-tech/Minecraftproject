package jp.archiquest.analysis;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** 建物の材料の大まかな分類 */
public enum MaterialKind {
    WOOD("木材"),
    CONCRETE("コンクリート"),
    STEEL("鉄・金属"),
    MASONRY("石・レンガ"),
    GLASS("ガラス"),
    OTHER("その他");

    public final String label;

    MaterialKind(String label) {
        this.label = label;
    }

    private static final String[] WOOD_WORDS = {"oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry",
            "bamboo", "crimson", "warped", "planks", "_log", "_wood"};
    private static final String[] MASONRY_WORDS = {"brick", "stone", "cobble", "andesite", "diorite", "granite",
            "deepslate", "tuff", "basalt", "quartz", "terracotta", "prismarine", "purpur", "calcite", "mud"};

    public static String path(BlockState st) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(st.getBlock());
        return key == null ? "" : key.getPath();
    }

    public static MaterialKind classify(BlockState st) {
        if (Terrain.isGlass(st)) return GLASS;
        String path = path(st);
        if (path.endsWith("_concrete")) return CONCRETE;
        if (path.contains("iron") || path.contains("copper") || path.equals("chain") || path.contains("anvil")
                || path.contains("netherite") || path.equals("lightning_rod")) return STEEL;
        if (st.is(BlockTags.LOGS) || st.is(BlockTags.PLANKS)) return WOOD;
        for (String w : WOOD_WORDS) if (path.contains(w)) return WOOD;
        for (String w : MASONRY_WORDS) if (path.contains(w)) return MASONRY;
        return OTHER;
    }
}
