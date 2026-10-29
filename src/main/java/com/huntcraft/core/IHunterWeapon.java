package com.huntcraft.core;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Implemented by every HuntCraft weapon item (gear.weapon module).
 * The core damage pipeline ({@link HunterDamage}) reads these values to apply the
 * MH damage formula: raw * motion value * sharpness * crit * hitzone + element.
 */
public interface IHunterWeapon {

    WeaponClass weaponClass();

    /** "True raw" attack in Minecraft damage units (already including upgrade level). */
    float getAttack(ItemStack stack);

    /** Element or status carried by the weapon (NONE if raw). */
    Element getElement(ItemStack stack);

    /** Displayed element/status value, e.g. 240 (MH scale). Element damage = value/100 * hitzone * sharpness. */
    float getElementValue(ItemStack stack);

    /** Affinity in percent (-100..100). */
    int getAffinity(ItemStack stack);

    default DamageKind getDamageKind(ItemStack stack) {
        return weaponClass().defaultKind;
    }

    default boolean usesSharpness() {
        return weaponClass() != WeaponClass.BOW;
    }

    /** Current sharpness colour for this stack (ignored when !usesSharpness()). */
    Sharpness getSharpness(ItemStack stack, Player player);

    /** Consume sharpness points after a hit. */
    void consumeSharpness(ItemStack stack, Player player, int amount);

    /**
     * Motion value for a normal left-click hit. Called once per vanilla melee attack.
     * Implementations can advance combo state here.
     */
    float getLeftClickMotionValue(Player player, ItemStack stack, LivingEntity target);

    /** Extra damage multiplier from weapon-internal states (spirit level, demon mode...). Default 1. */
    default float getStateMultiplier(Player player, ItemStack stack) {
        return 1.0F;
    }

    /** Called after a successful hunter hit (left click or special) so the weapon can fill gauges etc. */
    default void onHunterHit(Player player, ItemStack stack, LivingEntity target, float finalDamage, boolean weakSpot) {
    }
}
