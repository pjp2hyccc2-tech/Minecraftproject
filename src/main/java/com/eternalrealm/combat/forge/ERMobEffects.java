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

    static final String BLEED_DPS = "eternalrealm_bleed_dps";
    static final String BLEED_SOURCE = "eternalrealm_bleed_source";
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
