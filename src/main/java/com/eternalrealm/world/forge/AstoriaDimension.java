package com.eternalrealm.world.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.gen.AstoriaBiome;
import com.eternalrealm.world.gen.WorldgenIds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Registry keys of the アストリア dimension and its biomes. */
public final class AstoriaDimension {
    /** The dimension {@code eternalrealm:astoria} (data/eternalrealm/dimension/astoria.json). */
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, id(WorldgenIds.DIMENSION));

    private static final Map<AstoriaBiome, ResourceKey<Biome>> KEYS = new EnumMap<>(AstoriaBiome.class);
    private static final Map<ResourceKey<Biome>, AstoriaBiome> BY_KEY = new HashMap<>();

    static {
        for (AstoriaBiome biome : AstoriaBiome.values()) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id(biome.id()));
            KEYS.put(biome, key);
            BY_KEY.put(key, biome);
        }
    }

    private AstoriaDimension() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(ModInfo.MOD_ID, path);
    }

    public static ResourceKey<Biome> biomeKey(AstoriaBiome biome) {
        return KEYS.get(biome);
    }

    /** @return the アストリア biome of a holder, or null for any other biome */
    @Nullable
    public static AstoriaBiome biome(Holder<Biome> holder) {
        return holder.unwrapKey().map(BY_KEY::get).orElse(null);
    }
}
