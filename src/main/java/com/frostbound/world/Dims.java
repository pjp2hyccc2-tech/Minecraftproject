package com.frostbound.world;

import com.frostbound.FrostboundMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/** Keys of the frozen dimension, its biomes and the FROSTBOUND world type. */
public final class Dims {
    public static final ResourceKey<Level> FROZEN_WASTES = ResourceKey.create(Registries.DIMENSION, FrostboundMod.id("frozen_wastes"));
    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, FrostboundMod.id("frostbound"));
    public static final ResourceLocation EFFECTS = FrostboundMod.id("frozen_wastes");

    public static final ResourceKey<Biome> FROZEN_VOID = biome("frozen_void");
    public static final ResourceKey<Biome> FROZEN_BASIN = biome("frozen_basin");
    public static final ResourceKey<Biome> ICE_FIELD = biome("ice_field");
    public static final ResourceKey<Biome> FROST_PEAKS = biome("frost_peaks");
    public static final ResourceKey<Biome> GLACIER = biome("glacier");
    public static final ResourceKey<Biome> FROZEN_LAKE = biome("frozen_lake");
    public static final ResourceKey<Biome> BURIED_RUINS = biome("buried_ruins");
    public static final ResourceKey<Biome> INDUSTRIAL_WASTES = biome("industrial_wastes");

    private Dims() {
    }

    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME, FrostboundMod.id(name));
    }
}
