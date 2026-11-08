package com.eternalrealm.combat.forge;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiPredicate;

/**
 * Whether one player may hurt another with the mod's attacks. ETERNAL REALM is cooperative: outside PvP areas
 * (arena matches, installed later by the PvP module) players never hurt each other, whatever the vanilla PvP setting.
 */
public final class PvpRules {
    private static volatile BiPredicate<ServerPlayer, ServerPlayer> arena = (attacker, victim) -> false;

    private PvpRules() {
    }

    public static boolean canHurt(ServerPlayer attacker, ServerPlayer victim) {
        return arena.test(attacker, victim);
    }

    /** Installed by the PvP arena: returns true for two opponents in the same running match. */
    public static void setArenaRule(BiPredicate<ServerPlayer, ServerPlayer> rule) {
        arena = rule;
    }
}
