package com.sounan.registry;

import com.mojang.serialization.Codec;
import com.sounan.Sounan;
import com.sounan.world.MountainGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModDimensions {
    /** 帰らず山: the mountain at night. */
    public static final ResourceKey<Level> KAERAZU = ResourceKey.create(Registries.DIMENSION, Sounan.id("kaerazu"));

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Sounan.MODID);
    public static final RegistryObject<Codec<? extends ChunkGenerator>> MOUNTAIN = CHUNK_GENERATORS.register("mountain",
            () -> MountainGenerator.CODEC);

    private ModDimensions() {}

    public static boolean isMountain(Level level) {
        return level != null && level.dimension() == KAERAZU;
    }
}
