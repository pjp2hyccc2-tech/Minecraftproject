package com.birdiecraft.course;

import com.birdiecraft.block.FlagColor;
import com.birdiecraft.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Supplier;

/**
 * Visual and layout style of a generated course. Every preset course uses one theme; random
 * generated courses pick one.
 */
public enum CourseTheme {
    MEADOW(Elevation.FLAT, FlagColor.RED, 0.8, 1.20, 1.15, 0.6, 0.30, 1, 2, 0, 1, 0.50, Fluid.WATER,
            () -> ModBlocks.ROUGH.get(), () -> ModBlocks.DEEP_ROUGH.get(), () -> ModBlocks.BUNKER_SAND.get(),
            new Block[]{Blocks.GRASS_BLOCK}, () -> Blocks.DIRT,
            new TreeStyle[]{TreeStyle.OAK, TreeStyle.OAK, TreeStyle.BIRCH},
            new Decor[]{Decor.FLOWERS, Decor.FLOWERS, Decor.TALL_GRASS}),
    COAST(Elevation.FLAT, FlagColor.BLUE, 1.4, 1.0, 1.0, 0.7, 0.70, 2, 4, 1, 3, 0.22, Fluid.WATER,
            () -> ModBlocks.ROUGH.get(), () -> ModBlocks.DEEP_ROUGH.get(), () -> ModBlocks.BUNKER_WHITE.get(),
            new Block[]{Blocks.SAND, Blocks.SAND, Blocks.SAND, Blocks.GRASS_BLOCK}, () -> Blocks.SANDSTONE,
            new TreeStyle[]{TreeStyle.PALM},
            new Decor[]{Decor.DUNES, Decor.DEAD_BUSH, Decor.TALL_GRASS}),
    AUTUMN(Elevation.ROLLING, FlagColor.ORANGE, 0.9, 1.0, 1.0, 1.0, 0.40, 1, 3, 0, 1, 0.85, Fluid.WATER,
            () -> ModBlocks.ROUGH.get(), () -> ModBlocks.DEEP_ROUGH.get(), () -> ModBlocks.BUNKER_SAND.get(),
            new Block[]{Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.COARSE_DIRT}, () -> Blocks.DIRT,
            new TreeStyle[]{TreeStyle.MAPLE, TreeStyle.MAPLE, TreeStyle.GINKGO, TreeStyle.OAK},
            new Decor[]{Decor.FERNS, Decor.BOULDERS, Decor.MUSHROOMS}),
    SAKURA(Elevation.FLAT, FlagColor.PINK, 0.8, 1.0, 1.05, 0.8, 0.60, 1, 3, 0, 1, 0.60, Fluid.WATER,
            () -> ModBlocks.ROUGH.get(), () -> ModBlocks.DEEP_ROUGH.get(), () -> ModBlocks.BUNKER_WHITE.get(),
            new Block[]{Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK}, () -> Blocks.DIRT,
            new TreeStyle[]{TreeStyle.CHERRY, TreeStyle.CHERRY, TreeStyle.CHERRY, TreeStyle.OAK},
            new Decor[]{Decor.PETALS, Decor.LANTERNS, Decor.PETALS}),
    DESERT(Elevation.FLAT, FlagColor.YELLOW, 1.1, 1.0, 1.0, 0.8, 0.35, 2, 3, 1, 2, 0.35, Fluid.WATER,
            () -> ModBlocks.ROUGH_DRY.get(), () -> ModBlocks.ROUGH_DRY.get(), () -> ModBlocks.BUNKER_RED.get(),
            new Block[]{Blocks.SAND, Blocks.RED_SAND, Blocks.RED_SAND, Blocks.COARSE_DIRT}, () -> Blocks.SANDSTONE,
            new TreeStyle[]{TreeStyle.CACTUS, TreeStyle.CACTUS, TreeStyle.PALM},
            new Decor[]{Decor.MESAS, Decor.DEAD_BUSH, Decor.DEAD_BUSH}),
    SNOW(Elevation.ROLLING, FlagColor.RED, 1.2, 1.0, 1.0, 0.9, 0.55, 1, 2, 0, 1, 0.60, Fluid.ICE,
            () -> ModBlocks.ROUGH_SNOW.get(), () -> ModBlocks.DEEP_ROUGH.get(), () -> ModBlocks.BUNKER_WHITE.get(),
            new Block[]{Blocks.SNOW_BLOCK, Blocks.SNOW_BLOCK, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE}, () -> Blocks.DIRT,
            new TreeStyle[]{TreeStyle.SNOWY_SPRUCE},
            new Decor[]{Decor.ICE_SPIKES, Decor.BOULDERS}),
    VOLCANIC(Elevation.FLAT, FlagColor.ORANGE, 1.0, 1.0, 0.95, 0.9, 0.55, 2, 3, 0, 2, 0.30, Fluid.LAVA,
            () -> ModBlocks.ROUGH_ASH.get(), () -> ModBlocks.DEEP_ROUGH.get(), () -> ModBlocks.BUNKER_ASH.get(),
            new Block[]{Blocks.BLACKSTONE, Blocks.BASALT, Blocks.TUFF, Blocks.BLACKSTONE}, () -> Blocks.STONE,
            new TreeStyle[]{TreeStyle.BASALT_SPIRE, TreeStyle.DEAD},
            new Decor[]{Decor.BOULDERS, Decor.MAGMA}),
    MYSTIC(Elevation.FLAT, FlagColor.WHITE, 0.6, 0.82, 0.95, 1.0, 0.50, 1, 2, 0, 1, 0.95, Fluid.WATER,
            () -> ModBlocks.ROUGH.get(), () -> ModBlocks.DEEP_ROUGH.get(), () -> ModBlocks.BUNKER_SAND.get(),
            new Block[]{Blocks.PODZOL, Blocks.MYCELIUM, Blocks.GRASS_BLOCK}, () -> Blocks.DIRT,
            new TreeStyle[]{TreeStyle.DARK_OAK, TreeStyle.GIANT_MUSHROOM, TreeStyle.DARK_OAK},
            new Decor[]{Decor.MUSHROOMS, Decor.FERNS}),
    CANYON(Elevation.CANYON, FlagColor.YELLOW, 1.3, 1.0, 1.0, 0.7, 0.0, 1, 3, 0, 1, 0.30, Fluid.WATER,
            () -> ModBlocks.ROUGH_DRY.get(), () -> ModBlocks.DEEP_ROUGH.get(), () -> ModBlocks.BUNKER_RED.get(),
            new Block[]{Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA}, () -> Blocks.TERRACOTTA,
            new TreeStyle[]{TreeStyle.ACACIA},
            new Decor[]{Decor.MESAS, Decor.DEAD_BUSH}),
    ROYAL(Elevation.FLAT, FlagColor.YELLOW, 1.1, 0.85, 0.85, 0.8, 0.60, 3, 4, 1, 2, 0.60, Fluid.WATER,
            () -> ModBlocks.ROUGH.get(), () -> ModBlocks.DEEP_ROUGH.get(), () -> ModBlocks.BUNKER_WHITE.get(),
            new Block[]{Blocks.GRASS_BLOCK}, () -> Blocks.DIRT,
            new TreeStyle[]{TreeStyle.OAK, TreeStyle.BIRCH, TreeStyle.SPRUCE},
            new Decor[]{Decor.HEDGES, Decor.FLOWERS, Decor.HEDGES});

    public enum Elevation { FLAT, ROLLING, CANYON }

    public enum Fluid { WATER, LAVA, ICE }

    public enum TreeStyle { OAK, BIRCH, SPRUCE, SNOWY_SPRUCE, CHERRY, MAPLE, GINKGO, PALM, ACACIA, DARK_OAK, GIANT_MUSHROOM, CACTUS, BASALT_SPIRE, DEAD }

    public enum Decor { FLOWERS, TALL_GRASS, DUNES, DEAD_BUSH, FERNS, BOULDERS, MUSHROOMS, PETALS, LANTERNS, MESAS, ICE_SPIKES, MAGMA, HEDGES }

    public final Elevation elevation;
    public final FlagColor flag;
    public final double windFactor;
    public final double fairwayScale;
    public final double greenScale;
    public final double doglegScale;
    public final double waterChance;
    public final int greenBunkersMin;
    public final int greenBunkersMax;
    public final int fairwayBunkersMin;
    public final int fairwayBunkersMax;
    public final double treeDensity;
    public final Fluid fluid;
    private final Supplier<Block> rough;
    private final Supplier<Block> deepRough;
    private final Supplier<Block> bunker;
    public final Block[] obGround;
    private final Supplier<Block> subsurface;
    public final TreeStyle[] trees;
    public final Decor[] decor;

    CourseTheme(Elevation elevation, FlagColor flag, double windFactor, double fairwayScale, double greenScale,
                double doglegScale, double waterChance, int greenBunkersMin, int greenBunkersMax,
                int fairwayBunkersMin, int fairwayBunkersMax, double treeDensity, Fluid fluid,
                Supplier<Block> rough, Supplier<Block> deepRough, Supplier<Block> bunker,
                Block[] obGround, Supplier<Block> subsurface, TreeStyle[] trees, Decor[] decor) {
        this.elevation = elevation;
        this.flag = flag;
        this.windFactor = windFactor;
        this.fairwayScale = fairwayScale;
        this.greenScale = greenScale;
        this.doglegScale = doglegScale;
        this.waterChance = waterChance;
        this.greenBunkersMin = greenBunkersMin;
        this.greenBunkersMax = greenBunkersMax;
        this.fairwayBunkersMin = fairwayBunkersMin;
        this.fairwayBunkersMax = fairwayBunkersMax;
        this.treeDensity = treeDensity;
        this.fluid = fluid;
        this.rough = rough;
        this.deepRough = deepRough;
        this.bunker = bunker;
        this.obGround = obGround;
        this.subsurface = subsurface;
        this.trees = trees;
        this.decor = decor;
    }

    public Block rough() {
        return rough.get();
    }

    public Block deepRough() {
        return deepRough.get();
    }

    public Block bunker() {
        return bunker.get();
    }

    public Block subsurface() {
        return subsurface.get();
    }

    public static CourseTheme byId(int id) {
        if (id < 0 || id >= values().length) return MEADOW;
        return values()[id];
    }

    public String translationKey() {
        return "theme.birdiecraft." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
