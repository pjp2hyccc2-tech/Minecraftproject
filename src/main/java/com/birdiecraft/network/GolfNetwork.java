package com.birdiecraft.network;

import com.birdiecraft.BirdieCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class GolfNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            BirdieCraft.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    private GolfNetwork() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        // client -> server
        CHANNEL.messageBuilder(C2SShotPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SShotPacket::encode).decoder(C2SShotPacket::new).consumerMainThread(C2SShotPacket::handle).add();
        CHANNEL.messageBuilder(C2SClubPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SClubPacket::encode).decoder(C2SClubPacket::new).consumerMainThread(C2SClubPacket::handle).add();
        CHANNEL.messageBuilder(C2SMenuActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SMenuActionPacket::encode).decoder(C2SMenuActionPacket::new).consumerMainThread(C2SMenuActionPacket::handle).add();
        // server -> client
        CHANNEL.messageBuilder(S2CMatchStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CMatchStatePacket::encode).decoder(S2CMatchStatePacket::new).consumerMainThread(S2CMatchStatePacket::handle).add();
        CHANNEL.messageBuilder(S2CBallFlightPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBallFlightPacket::encode).decoder(S2CBallFlightPacket::new).consumerMainThread(S2CBallFlightPacket::handle).add();
        CHANNEL.messageBuilder(S2CHoleMapPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CHoleMapPacket::encode).decoder(S2CHoleMapPacket::new).consumerMainThread(S2CHoleMapPacket::handle).add();
        CHANNEL.messageBuilder(S2CMessagePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CMessagePacket::encode).decoder(S2CMessagePacket::new).consumerMainThread(S2CMessagePacket::handle).add();
        CHANNEL.messageBuilder(S2CMenuDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CMenuDataPacket::encode).decoder(S2CMenuDataPacket::new).consumerMainThread(S2CMenuDataPacket::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
