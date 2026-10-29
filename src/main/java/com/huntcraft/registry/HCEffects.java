package com.huntcraft.registry;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCMobEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Blights, status ailments and consumable buffs. */
public final class HCEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, HuntCraft.MODID);

    private HCEffects() {
    }

    private static final String LOCK_UUID = "6c0a1b3e-2f8d-4b52-9d6e-1a2b3c4d5e01";

    // elemental blights
    public static final RegistryObject<MobEffect> FIREBLIGHT = EFFECTS.register("fireblight",
            () -> new HCMobEffect(MobEffectCategory.HARMFUL, 0xFF5A1F, HCMobEffect.Behavior.FIREBLIGHT));
    public static final RegistryObject<MobEffect> ICEBLIGHT = EFFECTS.register("iceblight",
            () -> new HCMobEffect(MobEffectCategory.HARMFUL, 0x9BE7FF, HCMobEffect.Behavior.NONE)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "6c0a1b3e-2f8d-4b52-9d6e-1a2b3c4d5e02", -0.15D, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final RegistryObject<MobEffect> THUNDERBLIGHT = EFFECTS.register("thunderblight",
            () -> new HCMobEffect(MobEffectCategory.HARMFUL, 0xFFE23D, HCMobEffect.Behavior.NONE));
    public static final RegistryObject<MobEffect> WATERBLIGHT = EFFECTS.register("waterblight",
            () -> new HCMobEffect(MobEffectCategory.HARMFUL, 0x3D8BFF, HCMobEffect.Behavior.NONE));
    public static final RegistryObject<MobEffect> DRAGONBLIGHT = EFFECTS.register("dragonblight",
            () -> new HCMobEffect(MobEffectCategory.HARMFUL, 0xB04CFF, HCMobEffect.Behavior.NONE));
    // status
    public static final RegistryObject<MobEffect> POISON = EFFECTS.register("deadly_poison",
            () -> new HCMobEffect(MobEffectCategory.HARMFUL, 0x8E3FC9, HCMobEffect.Behavior.POISON));
    public static final RegistryObject<MobEffect> PARALYSIS = EFFECTS.register("paralysis",
            () -> new HCMobEffect(MobEffectCategory.HARMFUL, 0xF2D21B, HCMobEffect.Behavior.LOCK)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, LOCK_UUID, -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final RegistryObject<MobEffect> SLEEP = EFFECTS.register("sleep",
            () -> new HCMobEffect(MobEffectCategory.HARMFUL, 0x7FA8FF, HCMobEffect.Behavior.LOCK)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, LOCK_UUID, -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final RegistryObject<MobEffect> STUN = EFFECTS.register("stun",
            () -> new HCMobEffect(MobEffectCategory.HARMFUL, 0xFFFF88, HCMobEffect.Behavior.LOCK)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, LOCK_UUID, -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final RegistryObject<MobEffect> ROAR = EFFECTS.register("roar",
            () -> new HCMobEffect(MobEffectCategory.HARMFUL, 0xDDDDDD, HCMobEffect.Behavior.LOCK)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, LOCK_UUID, -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
    // buffs
    public static final RegistryObject<MobEffect> COOL_DRINK = EFFECTS.register("cool_drink",
            () -> new HCMobEffect(MobEffectCategory.BENEFICIAL, 0x66CCFF, HCMobEffect.Behavior.NONE));
    public static final RegistryObject<MobEffect> HOT_DRINK = EFFECTS.register("hot_drink",
            () -> new HCMobEffect(MobEffectCategory.BENEFICIAL, 0xFF7744, HCMobEffect.Behavior.NONE));
    public static final RegistryObject<MobEffect> DEMONDRUG = EFFECTS.register("demondrug",
            () -> new HCMobEffect(MobEffectCategory.BENEFICIAL, 0xE0302F, HCMobEffect.Behavior.NONE));
    public static final RegistryObject<MobEffect> ARMORSKIN = EFFECTS.register("armorskin",
            () -> new HCMobEffect(MobEffectCategory.BENEFICIAL, 0xE0A030, HCMobEffect.Behavior.NONE)
                    .addAttributeModifier(Attributes.ARMOR, "6c0a1b3e-2f8d-4b52-9d6e-1a2b3c4d5e03", 4.0D, AttributeModifier.Operation.ADDITION));
}
