package com.huntcraft.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client generic action. */
public record ClientActionPacket(String id, CompoundTag data) {

    public static void encode(ClientActionPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.id, 128);
        buf.writeNbt(p.data);
    }

    public static ClientActionPacket decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(128);
        CompoundTag tag = buf.readNbt();
        return new ClientActionPacket(id, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(ClientActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> HCNetwork.dispatchClient(p.id, p.data));
        c.setPacketHandled(true);
    }
}
