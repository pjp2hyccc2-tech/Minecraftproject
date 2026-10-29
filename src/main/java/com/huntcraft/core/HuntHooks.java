package com.huntcraft.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Cross-module hooks with safe defaults. Modules install implementations during init.
 */
public final class HuntHooks {
    private HuntHooks() {
    }

    // ---- extra carves (meal skills etc.)
    private static final List<ToIntFunction<Player>> EXTRA_CARVES = new ArrayList<>();

    public static void addExtraCarveProvider(ToIntFunction<Player> f) {
        EXTRA_CARVES.add(f);
    }

    public static int extraCarves(Player p) {
        int n = 0;
        for (ToIntFunction<Player> f : EXTRA_CARVES) n += f.applyAsInt(p);
        return n;
    }

    // ---- attack multipliers (meal buffs, hunting horn songs, ...)
    private static final List<java.util.function.ToDoubleFunction<Player>> ATTACK_MULTS = new ArrayList<>();

    public static void addAttackMultiplier(java.util.function.ToDoubleFunction<Player> f) {
        ATTACK_MULTS.add(f);
    }

    public static float attackMultiplier(Player p) {
        double m = 1.0;
        for (java.util.function.ToDoubleFunction<Player> f : ATTACK_MULTS) m *= f.applyAsDouble(p);
        return (float) m;
    }

    // ---- starter kit (given once on first login; modules add weapons/armor)
    private static final List<Consumer<ServerPlayer>> STARTER_KIT = new ArrayList<>();

    public static void addStarterKit(Consumer<ServerPlayer> giver) {
        STARTER_KIT.add(giver);
    }

    public static void giveStarterKit(ServerPlayer p) {
        for (Consumer<ServerPlayer> c : STARTER_KIT) c.accept(p);
    }

    // ---- village (installed by the village module)
    /** Teleports the player to the hunter village (builds it if needed). */
    public static Consumer<ServerPlayer> toVillage = p -> {
    };
    /** Teleports the player back to the overworld (their spawn). */
    public static Consumer<ServerPlayer> toOverworld = p -> {
    };

    // ---- quest (installed by the quest module)
    /** Teleports the player to the base camp of their active quest (farcaster). */
    public static Consumer<ServerPlayer> toBaseCamp = p -> {
    };

    /** Rank of the player's active quest, or null when not on a quest. */
    public interface QuestRankProvider {
        @Nullable
        Rank rankOf(Player p);
    }

    public static QuestRankProvider questRank = p -> null;
}
