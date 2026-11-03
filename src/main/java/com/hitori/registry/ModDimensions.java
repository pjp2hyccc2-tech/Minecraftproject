package com.hitori.registry;

import com.mojang.serialization.Codec;
import com.hitori.Hitori;
import com.hitori.world.Layout;
import com.hitori.world.ShirakabaGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The apartment's dimension (owner: world). The dimension key is {@link Layout#DIMENSION}. */
public final class ModDimensions {
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Hitori.MODID);

    /** 白樺: コーポ白樺, its street and the houses around, all built by code. */
    public static final RegistryObject<Codec<? extends ChunkGenerator>> SHIRAKABA = CHUNK_GENERATORS.register("shirakaba",
            () -> ShirakabaGenerator.CODEC);

    private ModDimensions() {}

    public static boolean isApartment(Level level) {
        return level != null && level.dimension() == Layout.DIMENSION;
    }
}
