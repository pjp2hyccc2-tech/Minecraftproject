package com.hitori.registry;

import com.mojang.serialization.Codec;
import com.hitori.Hitori;
import com.hitori.world.Layout;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;

/** The apartment's dimension (owner: world). The dimension key is {@link Layout#DIMENSION}. */
public final class ModDimensions {
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Hitori.MODID);

    private ModDimensions() {}

    public static boolean isApartment(Level level) {
        return level != null && level.dimension() == Layout.DIMENSION;
    }
}
