package com.eternalrealm.world.forge.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Config of the multi-variant features: {@code "config": {"variant": "basalt"}}. The allowed values per feature type
 * are listed in {@code world.gen.WorldgenIds#FEATURE_VARIANTS} (checked by the data tests).
 */
public record VariantConfig(String variant) implements FeatureConfiguration {
    public static final Codec<VariantConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("variant").forGetter(VariantConfig::variant)
    ).apply(instance, VariantConfig::new));
}
