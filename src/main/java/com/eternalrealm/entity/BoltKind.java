package com.eternalrealm.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Element of an {@link EnemyBolt}: its look (item sprite and particle trail), sound and on-hit effect. */
public enum BoltKind {
    FIRE, FROST, VOID, CURSE, STORM, NATURE, ARROW;

    public Item item() {
        return switch (this) {
            case FIRE -> Items.FIRE_CHARGE;
            case FROST -> Items.SNOWBALL;
            case VOID -> Items.ENDER_PEARL;
            case CURSE -> Items.NETHER_WART;
            case STORM -> Items.GLOWSTONE_DUST;
            case NATURE -> Items.SLIME_BALL;
            case ARROW -> Items.ARROW;
        };
    }

    public ParticleOptions particle() {
        return switch (this) {
            case FIRE -> ParticleTypes.FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case VOID -> ParticleTypes.PORTAL;
            case CURSE -> ParticleTypes.CRIMSON_SPORE;
            case STORM -> ParticleTypes.ELECTRIC_SPARK;
            case NATURE -> ParticleTypes.HAPPY_VILLAGER;
            case ARROW -> ParticleTypes.CRIT;
        };
    }

    public SoundEvent shootSound() {
        return switch (this) {
            case FIRE -> SoundEvents.BLAZE_SHOOT;
            case FROST -> SoundEvents.SNOW_GOLEM_SHOOT;
            case ARROW -> SoundEvents.CROSSBOW_SHOOT;
            default -> SoundEvents.EVOKER_CAST_SPELL;
        };
    }

    /** Extra effect on a living target that took the bolt's damage. */
    public void onHit(LivingEntity target, Entity owner) {
        switch (this) {
            case FIRE -> target.setSecondsOnFire(3);
            case FROST -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), owner);
            case VOID -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), owner);
            case CURSE -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), owner);
            case NATURE -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0), owner);
            default -> {
            }
        }
    }

    /** The kind whose sprite is {@code item} (clients only know the synced item), or {@code null}. */
    public static BoltKind byItem(Item item) {
        for (BoltKind kind : values()) {
            if (kind.item() == item) {
                return kind;
            }
        }
        return null;
    }
}
