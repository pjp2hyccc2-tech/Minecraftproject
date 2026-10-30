package com.nihonisshu.tabi.registry;

import com.mojang.serialization.Codec;
import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.world.TabiBiomeSource;
import com.nihonisshu.tabi.world.TabiChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class TabiWorldgen {
    private TabiWorldgen() {}

    public static final ResourceKey<Level> JAPAN = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(TabiMod.MODID, "japan"));

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, TabiMod.MODID);
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, TabiMod.MODID);

    public static final RegistryObject<Codec<? extends ChunkGenerator>> JAPAN_GENERATOR = CHUNK_GENERATORS.register("japan", () -> TabiChunkGenerator.CODEC);
    public static final RegistryObject<Codec<? extends BiomeSource>> JAPAN_BIOMES = BIOME_SOURCES.register("japan", () -> TabiBiomeSource.CODEC);
}
