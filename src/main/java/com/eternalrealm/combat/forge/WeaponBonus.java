package com.eternalrealm.combat.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.ToDoubleBiFunction;

/**
 * Bonus attack of a held weapon (tier, enhancement). The equipment module installs the real reader; until then every
 * weapon attacks with its profile's base value.
 */
public final class WeaponBonus {
    private static volatile ToDoubleBiFunction<ServerPlayer, ItemStack> reader = (player, stack) -> 0.0;

    private WeaponBonus() {
    }

    public static double bonusAttack(ServerPlayer attacker, ItemStack stack) {
        double value = reader.applyAsDouble(attacker, stack);
        return Double.isFinite(value) ? Math.max(0.0, Math.min(100_000.0, value)) : 0.0;
    }

    public static void setReader(ToDoubleBiFunction<ServerPlayer, ItemStack> newReader) {
        reader = newReader;
    }
}
