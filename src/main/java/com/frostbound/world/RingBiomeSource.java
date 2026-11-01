package com.frostbound.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Biomes of the frozen wastes, following {@link Terrain#zone}. */
public class RingBiomeSource extends BiomeSource {
    public static final Codec<RingBiomeSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryOps.retrieveElement(Dims.FROZEN_BASIN),
            RegistryOps.retrieveElement(Dims.ICE_FIELD),
            RegistryOps.retrieveElement(Dims.FROST_PEAKS),
            RegistryOps.retrieveElement(Dims.GLACIER),
            RegistryOps.retrieveElement(Dims.FROZEN_LAKE),
            RegistryOps.retrieveElement(Dims.BURIED_RUINS),
            RegistryOps.retrieveElement(Dims.INDUSTRIAL_WASTES)
    ).apply(i, i.stable(RingBiomeSource::new)));

    private final Holder<Biome> basin;
    private final Holder<Biome> iceField;
    private final Holder<Biome> peaks;
    private final Holder<Biome> glacier;
    private final Holder<Biome> lake;
    private final Holder<Biome> ruins;
    private final Holder<Biome> industrial;

    public RingBiomeSource(Holder<Biome> basin, Holder<Biome> iceField, Holder<Biome> peaks, Holder<Biome> glacier,
            Holder<Biome> lake, Holder<Biome> ruins, Holder<Biome> industrial) {
        this.basin = basin;
        this.iceField = iceField;
        this.peaks = peaks;
        this.glacier = glacier;
        this.lake = lake;
        this.ruins = ruins;
        this.industrial = industrial;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(basin, iceField, peaks, glacier, lake, ruins, industrial);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        return switch (Terrain.zone(QuartPos.toBlock(qx), QuartPos.toBlock(qz))) {
            case BASIN -> basin;
            case ICE_FIELD -> iceField;
            case FROST_PEAKS -> peaks;
            case GLACIER -> glacier;
            case FROZEN_LAKE -> lake;
            case BURIED_RUINS -> ruins;
            case INDUSTRIAL -> industrial;
        };
    }
}
