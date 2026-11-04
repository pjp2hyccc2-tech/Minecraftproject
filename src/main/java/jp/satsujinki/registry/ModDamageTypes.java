package jp.satsujinki.registry;

import jp.satsujinki.Satsujinki;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** Data-driven damage types (data/satsujinki/damage_type/*.json). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> MIXER = key("mixer");
    public static final ResourceKey<DamageType> PRESS = key("press");
    public static final ResourceKey<DamageType> SAW = key("saw");
    public static final ResourceKey<DamageType> GUILLOTINE = key("guillotine");
    public static final ResourceKey<DamageType> SPIKES = key("spikes");

    private ModDamageTypes() {
    }

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Satsujinki.id(name));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> key, @Nullable Entity attacker) {
        Holder<DamageType> holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return attacker == null ? new DamageSource(holder) : new DamageSource(holder, attacker);
    }
}
