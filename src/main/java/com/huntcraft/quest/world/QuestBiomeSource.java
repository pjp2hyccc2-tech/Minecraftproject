package com.huntcraft.quest.world;

import com.huntcraft.HuntCraft;
import com.huntcraft.quest.QuestRegions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Biome source of the quest maps: the biome of each column comes from the designed {@link QuestMapData}
 * ("biomes" lists the biomes in the same order as the data's biome indices); caves get the map's cave biome.
 */
public class QuestBiomeSource extends BiomeSource {
    public static final Codec<QuestBiomeSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("map").forGetter(s -> s.mapId),
            Biome.CODEC.listOf().fieldOf("biomes").forGetter(s -> s.biomes)
    ).apply(i, QuestBiomeSource::new));

    private final String mapId;
    private final List<Holder<Biome>> biomes;
    private volatile QuestMapData data;
    private volatile boolean checked;

    public QuestBiomeSource(String mapId, List<Holder<Biome>> biomes) {
        this.mapId = mapId;
        this.biomes = List.copyOf(biomes);
    }

    private QuestMapData data() {
        QuestMapData d = data;
        if (d == null) {
            d = QuestMapData.get(mapId);
            data = d;
        }
        if (!checked) {
            checked = true;
            for (int k = 0; k < Math.min(biomes.size(), d.biomeIds.size()); k++) {
                Optional<ResourceKey<Biome>> key = biomes.get(k).unwrapKey();
                if (key.isPresent() && !key.get().location().toString().equals(d.biomeIds.get(k))) {
                    HuntCraft.LOGGER.warn("Quest map {}: biome #{} is {} in the dimension json but {} in the map data", mapId, k, key.get().location(), d.biomeIds.get(k));
                }
            }
        }
        return d;
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
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        QuestMapData d = data();
        int x = (qx << 2) + 2, y = (qy << 2) + 2, z = (qz << 2) + 2;
        int u = QuestRegions.tileU(x), v = QuestRegions.tileV(z);
        int b;
        if (u < 0 || v < 0) {
            b = d.outside.biome();
        } else {
            int i = QuestMapData.idx(u, v);
            b = d.biome[i] & 255;
            int cl = d.clo[i] & 255;
            if (cl > 0 && d.caveBiome >= 0 && y >= cl - 3 && y <= (d.chi[i] & 255) + 1) b = d.caveBiome;
        }
        if (b < 0 || b >= biomes.size()) b = 0;
        return biomes.get(b);
    }
}
