package com.sounan.registry;

import com.sounan.Sounan;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ModDimensions {
    /** 隠り世: the eternal-night copy of Sayo's mansion. */
    public static final ResourceKey<Level> KAKURIYO = ResourceKey.create(Registries.DIMENSION, Sounan.id("kakuriyo"));

    private ModDimensions() {}

    public static boolean isKakuriyo(Level level) {
        return level != null && level.dimension() == KAKURIYO;
    }
}
