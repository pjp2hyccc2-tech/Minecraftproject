package com.eternalrealm.network.forge;

import com.eternalrealm.item.forge.QuickUse;
import com.eternalrealm.server.ServerContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: "use the item in my inventory slot {@code slot}" (quick-use keys, the bag's use button). Only an intent: the
 * server checks the slot, the item, the level and the cooldown ({@link QuickUse#useSlot}).
 */
public record UseItemPacket(int slot) {
    /** One VarInt. */
    static final int MAX_BYTES = 5;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(slot);
    }

    public static UseItemPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "UseItemPacket");
        return new UseItemPacket(buffer.readVarInt());
    }

    /** Server thread (via consumerMainThread). */
    public static void handle(UseItemPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPacketGuard.accept(contextSupplier, "use_item", ServerContext::uiLimiter).ifPresent(accepted -> {
            if (packet.slot() < 0 || packet.slot() >= accepted.player().getInventory().items.size()) {
                ServerPacketGuard.violation(accepted.context(), accepted.player(), "use_item: slot " + packet.slot());
                return;
            }
            QuickUse.useSlot(accepted.player(), packet.slot());
        });
    }
}
