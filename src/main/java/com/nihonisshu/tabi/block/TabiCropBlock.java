package com.nihonisshu.tabi.block;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

import java.util.function.Supplier;

/** Tomato / cucumber / eggplant crops grown on farmland. */
public class TabiCropBlock extends CropBlock {
    private final Supplier<? extends ItemLike> seed;

    public TabiCropBlock(Properties props, Supplier<? extends ItemLike> seed) {
        super(props);
        this.seed = seed;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return seed.get();
    }
}
