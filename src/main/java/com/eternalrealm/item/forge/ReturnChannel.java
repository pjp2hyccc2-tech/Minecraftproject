package com.eternalrealm.item.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The return scroll read from the inventory (quick-use key or bag): the player stands still for the scroll's channel
 * time, then one scroll is used up and they are sent home. Moving more than {@value #MOVE_LIMIT} blocks or taking
 * damage cancels it, as for a scroll held in the hand.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ReturnChannel {
    /** How far the player may drift while reading (blocks). */
    public static final double MOVE_LIMIT = 0.6;

    private record Channel(ServerPlayer player, ConsumableItem item, Vec3 start, int startTick, int endTick,
                           float health) {
    }

    private static final Map<UUID, Channel> ACTIVE = new HashMap<>();

    private ReturnChannel() {
    }

    /** @return false when a channel is already running */
    static boolean start(ServerPlayer player, ConsumableItem item, int ticks) {
        if (ACTIVE.containsKey(player.getUUID())) {
            return false;
        }
        int now = player.server.getTickCount();
        ACTIVE.put(player.getUUID(), new Channel(player, item, player.position(), now, now + Math.max(1, ticks),
                player.getHealth()));
        player.displayClientMessage(Component.translatable("message.eternalrealm.consumable.return_channel",
                String.format(java.util.Locale.ROOT, "%.0f", ticks / 20.0)).withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    public static boolean isChanneling(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Channel>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Channel channel = it.next().getValue();
            ServerPlayer player = channel.player();
            if (player.isRemoved() || !player.isAlive() || player.hasDisconnected()) {
                it.remove();
                continue;
            }
            boolean moved = player.position().distanceToSqr(channel.start()) > MOVE_LIMIT * MOVE_LIMIT;
            boolean hurt = player.getHealth() < channel.health() || (player.hurtTime > 0 && now > channel.startTick());
            if (moved || hurt) {
                it.remove();
                player.displayClientMessage(Component.translatable("message.eternalrealm.consumable.channel_cancelled")
                        .withStyle(ChatFormatting.RED), true);
                continue;
            }
            if ((now - channel.startTick()) % 5 == 0) {
                player.serverLevel().sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0,
                        player.getZ(), 10, 0.4, 0.6, 0.4, 0.25);
            }
            int left = channel.endTick() - now;
            if (left > 0 && left % 20 == 0) {
                player.displayClientMessage(Component.translatable("message.eternalrealm.consumable.return_channel",
                        Integer.toString(left / 20)).withStyle(ChatFormatting.AQUA), true);
            }
            if (now >= channel.endTick()) {
                it.remove();
                finish(player, channel.item());
            }
        }
    }

    /** One scroll from the inventory: used up only if the player was really sent home. */
    private static void finish(ServerPlayer player, ConsumableItem item) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == item) {
                item.applyAndConsume(player, stack);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }
}
