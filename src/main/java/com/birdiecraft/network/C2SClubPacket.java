package com.birdiecraft.network;

import com.birdiecraft.golf.engine.Club;
import com.birdiecraft.item.GolfClubItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Club switched on the client: update the held club item so everyone sees the right model. */
public final class C2SClubPacket {
    private final int club;

    public C2SClubPacket(Club club) {
        this.club = club.ordinal();
    }

    public C2SClubPacket(FriendlyByteBuf buf) {
        this.club = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(club);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof GolfClubItem) {
            GolfClubItem.setClub(stack, Club.byId(club));
        }
    }
}
