package com.eternalrealm.combat.forge;

import net.minecraft.world.item.ItemStack;

import java.util.function.ToDoubleFunction;

/**
 * Bonus attack of a held weapon (tier, enhancement). The equipment module installs the real reader; until then every
 * weapon attacks with its profile's base value.
 */
public final class WeaponBonus {
    private static volatile ToDoubleFunction<ItemStack> reader = stack -> 0.0;

    private WeaponBonus() {
    }

    public static double bonusAttack(ItemStack stack) {
        double value = reader.applyAsDouble(stack);
        return Double.isFinite(value) ? Math.max(0.0, Math.min(100_000.0, value)) : 0.0;
    }

    public static void setReader(ToDoubleFunction<ItemStack> newReader) {
        reader = newReader;
    }
}
