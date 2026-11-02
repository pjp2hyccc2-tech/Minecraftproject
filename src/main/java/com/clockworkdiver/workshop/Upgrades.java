package com.clockworkdiver.workshop;

import com.clockworkdiver.data.PlayerProgress;
import com.clockworkdiver.data.WorldProgress;
import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Tool upgrades bought with brass coins at the repair bench. */
public final class Upgrades {
    public static final int WRENCH = 0;
    public static final int STEAM = 1;
    public static final int LENS = 2;
    public static final int MAX_LEVEL = 3;

    private Upgrades() {}

    public static int cost(int kind, int level) {
        return 8 + level * 10 + kind * 2;
    }

    public static int level(PlayerProgress progress, int kind) {
        return switch (kind) {
            case WRENCH -> progress.wrenchLevel;
            case STEAM -> progress.steamLevel;
            default -> progress.lensLevel;
        };
    }

    public static void buy(ServerPlayer player, int kind) {
        if (kind < 0 || kind > 2 || DiveManager.session(player) != null) return;
        PlayerProgress progress = WorldProgress.of(player);
        int level = level(progress, kind);
        if (level >= MAX_LEVEL) return;
        int cost = cost(kind, level);
        if (coins(player) < cost) {
            player.displayClientMessage(Component.translatable("message.clockworkdiver.not_enough_coins", cost), true);
            return;
        }
        takeCoins(player, cost);
        switch (kind) {
            case WRENCH -> progress.wrenchLevel++;
            case STEAM -> progress.steamLevel++;
            default -> progress.lensLevel++;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        player.displayClientMessage(Component.translatable("message.clockworkdiver.upgraded",
                Component.translatable("upgrade.clockworkdiver." + kind), level + 1), true);
        WorkshopManager.openBench(player, false);
    }

    public static int coins(ServerPlayer player) {
        return DiveManager.countItem(player, ModItems.BRASS_COIN.get());
    }

    private static void takeCoins(ServerPlayer player, int amount) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(ModItems.BRASS_COIN.get())) continue;
            int take = Math.min(amount, st.getCount());
            st.shrink(take);
            amount -= take;
        }
        inv.setChanged();
    }
}
