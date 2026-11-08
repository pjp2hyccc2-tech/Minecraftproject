package com.eternalrealm.world.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.level.BlockEvent;

/**
 * The royal capital is shared by everyone: players cannot break or place blocks or use buckets inside it. Operators in
 * creative mode still can (to repair or decorate).
 */
public final class CapitalProtection {
    private CapitalProtection() {
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (isProtected(event.getLevel(), event.getPos()) && !mayBuild(event.getPlayer())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("message.eternalrealm.capital.protected"), true);
        }
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && isProtected(event.getLevel(), event.getPos())
                && !mayBuild(player)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.eternalrealm.capital.protected"), true);
        }
    }

    public static void onBucket(FillBucketEvent event) {
        if (event.getTarget() != null && isProtected(event.getLevel(), BlockPos.containing(event.getTarget().getLocation()))
                && !mayBuild(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    static boolean isProtected(LevelAccessor level, BlockPos pos) {
        return level instanceof ServerLevel server && server.dimension() == Realm.ASTORIA
                && Realm.inCapital(server, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    private static boolean mayBuild(Player player) {
        return player != null && player.isCreative() && player.hasPermissions(2);
    }
}
