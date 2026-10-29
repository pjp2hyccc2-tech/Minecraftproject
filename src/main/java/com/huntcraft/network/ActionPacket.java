package com.huntcraft.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server generic action. */
public record ActionPacket(String id, CompoundTag data) {

    public static void encode(ActionPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.id, 128);
        buf.writeNbt(p.data);
    }

    public static ActionPacket decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(128);
        CompoundTag tag = buf.readNbt();
        return new ActionPacket(id, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(ActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer sender = c.getSender();
        c.enqueueWork(() -> {
            if (sender != null) HCNetwork.dispatchServer(p.id, sender, p.data);
        });
        c.setPacketHandled(true);
    }
}
