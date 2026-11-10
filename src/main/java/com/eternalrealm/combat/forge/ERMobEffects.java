package com.eternalrealm.combat.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Status effects of the combat system. Buff strengths are stored in the amplifier in 5 % steps (amplifier
 * {@code a} = {@code (a + 1) × 5 %}), so vanilla handles duration, syncing, icons and removal (milk, death).
 */
public final class ERMobEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ModInfo.MOD_ID);

    /** Damage dealt × (1 + magnitude). */
    public static final RegistryObject<MobEffect> ATTACK_UP = EFFECTS.register("attack_up",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0xE0563A));
    /** Damage taken × (1 − magnitude). */
    public static final RegistryObject<MobEffect> DEFENSE_UP = EFFECTS.register("defense_up",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0x4C7FE0));
    /** Melee hits set the target on fire. */
    public static final RegistryObject<MobEffect> FLAME_WEAPON = EFFECTS.register("flame_weapon",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0xFF8A1E));
    /** Damage every second (strength stored on the victim, see {@link #applyBleed}). */
    public static final RegistryObject<MobEffect> BLEED = EFFECTS.register("bleed", BleedEffect::new);
    /** Movement speed +15 % per level (ranger/assassin buffs). */
    public static final RegistryObject<MobEffect> HASTE = EFFECTS.register("haste",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0x7CE0FF).addAttributeModifier(
                    net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                    "6d4b5e8a-91c3-4f0e-9a51-2f7c8e1d4b01", 0.15,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** Frozen: movement speed −30 % per level, shown with the powder snow frost overlay. */
    public static final RegistryObject<MobEffect> FROZEN = EFFECTS.register("frozen",
            () -> new MarkerEffect(MobEffectCategory.HARMFUL, 0x9AD8FF).addAttributeModifier(
                    net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                    "a83f0c55-2d1e-4b7a-8c36-5e0f9b2d7c12", -0.30,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** Shown while a guard shield (absorption from a skill) holds. */
    public static final RegistryObject<MobEffect> GUARD_UP = EFFECTS.register("guard_up",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0xC8D2E0));
    /** Cursed: healing received is halved (boss mechanics). */
    public static final RegistryObject<MobEffect> CURSED = EFFECTS.register("cursed",
            () -> new MarkerEffect(MobEffectCategory.HARMFUL, 0x6A2C8C));

    // ---------------------------------------------------------------- skill tree effects
    /** Stunned: cannot move (navigation stops every tick) and its attacks are cancelled. */
    public static final RegistryObject<MobEffect> STUNNED = EFFECTS.register("stunned",
            () -> new StunEffect().addAttributeModifier(
                    net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                    "3c8e5f12-6a4b-4d0e-b8f1-9e2d7c4a1b33", -1.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** Damage every second (strength stored on the victim like bleeding; both can run at once). */
    public static final RegistryObject<MobEffect> POISONED = EFFECTS.register("poisoned", PoisonEffect::new);
    /** Damage dealt × (1 − magnitude). */
    public static final RegistryObject<MobEffect> WEAKENED = EFFECTS.register("weakened",
            () -> new MarkerEffect(MobEffectCategory.HARMFUL, 0x8A8A6A));
    /** Damage taken × (1 + magnitude). */
    public static final RegistryObject<MobEffect> VULNERABLE = EFFECTS.register("vulnerable",
            () -> new MarkerEffect(MobEffectCategory.HARMFUL, 0xD04A2A));
    /** Critical chance + magnitude. */
    public static final RegistryObject<MobEffect> CRIT_UP = EFFECTS.register("crit_up",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0xFFD24A));
    /** Heals magnitude of the damage dealt. */
    public static final RegistryObject<MobEffect> LIFESTEAL = EFFECTS.register("lifesteal",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0xB0102A));
    /** Melee hits chill the target. */
    public static final RegistryObject<MobEffect> FROST_WEAPON = EFFECTS.register("frost_weapon",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0x7ADAFF));
    /** Melee hits poison the target. */
    public static final RegistryObject<MobEffect> VENOM_WEAPON = EFFECTS.register("venom_weapon",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0x6ACD3A));
    /** Melee hits arc to a second enemy nearby. */
    public static final RegistryObject<MobEffect> THUNDER_WEAPON = EFFECTS.register("thunder_weapon",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0xF0F080));
    /** Chance (magnitude) to evade an attack completely. */
    public static final RegistryObject<MobEffect> EVASION = EFFECTS.register("evasion",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0xB8C8E8));
    /** Reflects magnitude of the damage taken from attackers. */
    public static final RegistryObject<MobEffect> THORNS = EFFECTS.register("thorns",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0x7A9A3A));

    static final String BLEED_DPS = "eternalrealm_bleed_dps";
    static final String BLEED_SOURCE = "eternalrealm_bleed_source";
    static final String POISON_DPS = "eternalrealm_poison_dps";
    static final String POISON_SOURCE = "eternalrealm_poison_source";
    private static final double STEP = 0.05;

    private ERMobEffects() {
    }

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    /** Amplifier encoding {@code magnitude} (rounded to 5 % steps, 5 %..100 %). */
    public static int amplifierFor(double magnitude) {
        return Math.max(0, Math.min(19, (int) Math.round(magnitude / STEP) - 1));
    }

    public static double magnitude(LivingEntity entity, MobEffect effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? 0.0 : (instance.getAmplifier() + 1) * STEP;
    }

    /** Multiplier of damage dealt by {@code entity}. */
    public static double attackMultiplier(LivingEntity entity) {
        return 1.0 + magnitude(entity, ATTACK_UP.get());
    }

    /** Multiplier of damage taken by {@code entity}. */
    public static double defenseMultiplier(LivingEntity entity) {
        return 1.0 - Math.min(0.9, magnitude(entity, DEFENSE_UP.get()));
    }

    /** Multiplier of the damage {@code entity} deals while weakened. */
    public static double weakenMultiplier(LivingEntity entity) {
        return 1.0 - Math.min(0.6, magnitude(entity, WEAKENED.get()));
    }

    /** Multiplier of the damage {@code entity} takes while vulnerable. */
    public static double vulnerabilityMultiplier(LivingEntity entity) {
        return 1.0 + Math.min(0.6, magnitude(entity, VULNERABLE.get()));
    }

    public static boolean isStunned(LivingEntity entity) {
        return entity.hasEffect(STUNNED.get());
    }

    /** Keeps the strongest of the running and the new poison. */
    public static void applyPoison(LivingEntity target, ServerPlayer source, float damagePerSecond, int ticks) {
        if (!(damagePerSecond > 0.0F) || ticks <= 0) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        boolean poisoned = target.hasEffect(POISONED.get());
        if (!poisoned || data.getFloat(POISON_DPS) <= damagePerSecond) {
            data.putFloat(POISON_DPS, damagePerSecond);
            data.putUUID(POISON_SOURCE, source.getUUID());
        }
        target.addEffect(new MobEffectInstance(POISONED.get(), ticks, 0, false, true, true), source);
    }

    /** Keeps the strongest of the running and the new bleed. */
    public static void applyBleed(LivingEntity target, ServerPlayer source, float damagePerSecond, int ticks) {
        if (!(damagePerSecond > 0.0F) || ticks <= 0) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        boolean bleeding = target.hasEffect(BLEED.get());
        if (!bleeding || data.getFloat(BLEED_DPS) <= damagePerSecond) {
            data.putFloat(BLEED_DPS, damagePerSecond);
            data.putUUID(BLEED_SOURCE, source.getUUID());
        }
        target.addEffect(new MobEffectInstance(BLEED.get(), ticks, 0, false, true, true), source);
    }

    /** An effect without vanilla behaviour; the combat code reads it. */
    static final class MarkerEffect extends MobEffect {
        MarkerEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    /** Stops a stunned mob's pathing every tick (the movement modifier holds it in place). */
    static final class StunEffect extends MobEffect {
        StunEffect() {
            super(MobEffectCategory.HARMFUL, 0xF4E36A);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return true;
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity instanceof net.minecraft.world.entity.Mob mob && !entity.level().isClientSide()) {
                mob.getNavigation().stop();
            }
        }
    }

    /** Poison damage once per second, from the strength stored on the victim (like {@link BleedEffect}). */
    static final class PoisonEffect extends MobEffect {
        PoisonEffect() {
            super(MobEffectCategory.HARMFUL, 0x4FA82A);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return duration % 20 == 10;
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            if (!(entity.level() instanceof ServerLevel level)) {
                return;
            }
            CompoundTag data = entity.getPersistentData();
            float damage = data.getFloat(POISON_DPS);
            if (!(damage > 0.0F)) {
                return;
            }
            Entity source = data.hasUUID(POISON_SOURCE) ? level.getEntity(data.getUUID(POISON_SOURCE)) : null;
            DamageSource damageSource = source != null
                    ? entity.damageSources().indirectMagic(source, source)
                    : entity.damageSources().magic();
            entity.invulnerableTime = 0;
            entity.hurt(damageSource, damage);
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(0.42F, 0.85F, 0.25F), 1.0F),
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
        }
    }

    static final class BleedEffect extends MobEffect {
        BleedEffect() {
            super(MobEffectCategory.HARMFUL, 0x8A0A0A);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return duration % 20 == 0;
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            if (!(entity.level() instanceof ServerLevel level)) {
                return;
            }
            CompoundTag data = entity.getPersistentData();
            float damage = data.getFloat(BLEED_DPS);
            if (!(damage > 0.0F)) {
                return;
            }
            Entity source = data.hasUUID(BLEED_SOURCE) ? level.getEntity(data.getUUID(BLEED_SOURCE)) : null;
            DamageSource damageSource = source != null
                    ? entity.damageSources().indirectMagic(source, source)
                    : entity.damageSources().magic();
            entity.invulnerableTime = 0;
            entity.hurt(damageSource, damage);
        }
    }
}
