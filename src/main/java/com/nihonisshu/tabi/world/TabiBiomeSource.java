package com.nihonisshu.tabi.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nihonisshu.tabi.TabiMod;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

/** Picks satoyama (countryside), kobe (city) or seto_sea depending on position. */
public class TabiBiomeSource extends BiomeSource {
    public static final ResourceKey<Biome> SATOYAMA = key("satoyama");
    public static final ResourceKey<Biome> SEA = key("seto_sea");
    public static final ResourceKey<Biome> KOBE = key("kobe");

    public static final Codec<TabiBiomeSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryOps.retrieveElement(SATOYAMA),
            RegistryOps.retrieveElement(SEA),
            RegistryOps.retrieveElement(KOBE)
    ).apply(i, i.stable(TabiBiomeSource::new)));

    private final Holder<Biome> satoyama, sea, kobe;

    public TabiBiomeSource(Holder<Biome> satoyama, Holder<Biome> sea, Holder<Biome> kobe) {
        this.satoyama = satoyama;
        this.sea = sea;
        this.kobe = kobe;
    }

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, new ResourceLocation(TabiMod.MODID, name));
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(satoyama, sea, kobe);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        int x = QuartPos.toBlock(qx), z = QuartPos.toBlock(qz);
        if (Terrain.inland(x, z) < -10) return sea;
        if (x > 3150) return kobe;
        return satoyama;
    }
}
