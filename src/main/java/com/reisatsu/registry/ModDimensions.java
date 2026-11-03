package com.reisatsu.registry;

import com.mojang.serialization.Codec;
import com.reisatsu.Reisatsu;
import com.reisatsu.world.HidamariGenerator;
import com.reisatsu.world.Layout;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The restaurant's dimension (owner: world). The dimension key is {@link Layout#DIMENSION}. */
public final class ModDimensions {
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Reisatsu.MODID);

    /** ひだまり: the restaurant, its lot, the road and the paddies, all built by code. */
    public static final RegistryObject<Codec<? extends ChunkGenerator>> HIDAMARI = CHUNK_GENERATORS.register("hidamari",
            () -> HidamariGenerator.CODEC);

    private ModDimensions() {}

    public static boolean isRestaurant(Level level) {
        return level != null && level.dimension() == Layout.DIMENSION;
    }
}
