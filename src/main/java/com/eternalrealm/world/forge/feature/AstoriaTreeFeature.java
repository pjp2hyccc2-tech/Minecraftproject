package com.eternalrealm.world.forge.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/** Places one of the {@link TreeShapes} by variant, on suitable dry ground. */
public final class AstoriaTreeFeature extends Feature<VariantConfig> {
    public AstoriaTreeFeature(Codec<VariantConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<VariantConfig> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        String variant = context.config().variant();
        FeatureKit.Bounds bounds = FeatureKit.Bounds.of(level, origin);
        int x = origin.getX();
        int z = origin.getZ();
        if (FeatureKit.keepOut(x, z)) {
            return false;
        }
        if (!FeatureKit.dry(level, x, z) || !bounds.fits(x, z, variant.equals("giant") ? 12 : 6)) {
            return false;
        }
        int ground = FeatureKit.ground(level, x, z);
        BlockState soil = FeatureKit.get(level, x, ground, z);
        boolean living = !variant.equals("charred") && !variant.equals("ashen") && !variant.equals("bleached");
        if (living && !soil.is(BlockTags.DIRT) && !(variant.equals("palm") && soil.is(BlockTags.SAND))) {
            return false;
        }
        if (!living && !FeatureKit.isTerrain(soil)) {
            return false;
        }
        int y = ground + 1;
        switch (variant) {
            case "giant" -> TreeShapes.giant(level, bounds, random, x, y, z);
            case "palm" -> TreeShapes.palm(level, bounds, random, x, y, z);
            case "white" -> TreeShapes.white(level, bounds, random, x, y, z);
            case "cherry" -> TreeShapes.cherry(level, bounds, random, x, y, z);
            default -> TreeShapes.dead(level, bounds, random, x, y, z, variant);
        }
        return true;
    }
}
