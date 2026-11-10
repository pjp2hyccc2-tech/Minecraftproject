package com.eternalrealm.network.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: something went into the player's bag (or, with {@link #BAG_FULL}, did not fit and lies at their feet): one row
 * of the pickup feed. The stack is a single item (its name, icon and rarity); {@code count} is how many.
 */
public record PickupFeedPacket(ItemStack stack, int count, int flags) {
    /** The bag was full: the items were dropped at the player's feet (for them alone). */
    public static final int BAG_FULL = 1;
    public static final int MAX_COUNT = 1_000_000;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeItem(stack);
        buffer.writeVarInt(count);
        buffer.writeByte(flags);
    }

    public static PickupFeedPacket decode(FriendlyByteBuf buffer) {
        ItemStack stack = buffer.readItem();
        int count = Math.max(1, Math.min(MAX_COUNT, buffer.readVarInt()));
        int flags = buffer.readByte() & BAG_FULL;
        return new PickupFeedPacket(stack, count, flags);
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(PickupFeedPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onPickupFeed(packet);
    }
}
