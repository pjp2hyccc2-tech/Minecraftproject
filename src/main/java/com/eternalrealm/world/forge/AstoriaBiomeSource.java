package com.eternalrealm.world.forge;

import com.eternalrealm.world.gen.AstoriaBiome;
import com.eternalrealm.world.gen.AstoriaLayout;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Biome source of {@code eternalrealm:astoria} ({@code "biome_source": {"type": "eternalrealm:astoria"}}): the seven
 * area rings around (0, 0) with noisy borders and their sub-biomes, as computed by the pure {@link AstoriaLayout}.
 * The biome does not depend on height or on the climate sampler, so caves below an area share its biome.
 */
public final class AstoriaBiomeSource extends BiomeSource {
    public static final Codec<AstoriaBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(instance, AstoriaBiomeSource::new));

    /** Holders indexed by {@link AstoriaBiome#ordinal()}. */
    private final List<Holder<Biome>> biomes;

    public AstoriaBiomeSource(HolderGetter<Biome> lookup) {
        List<Holder<Biome>> holders = new ArrayList<>();
        for (AstoriaBiome biome : AstoriaBiome.values()) {
            holders.add(lookup.getOrThrow(AstoriaDimension.biomeKey(biome)));
        }
        this.biomes = List.copyOf(holders);
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return biomes.get(AstoriaLayout.biomeAt(QuartPos.toBlock(quartX), QuartPos.toBlock(quartZ)).ordinal());
    }
}
