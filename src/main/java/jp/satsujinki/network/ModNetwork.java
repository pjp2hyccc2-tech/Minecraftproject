package jp.satsujinki.network;

import jp.satsujinki.Satsujinki;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Satsujinki.id("main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(GoreFxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GoreFxPacket::encode).decoder(GoreFxPacket::decode).consumerMainThread(GoreFxPacket::handle).add();
        CHANNEL.messageBuilder(HostSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HostSyncPacket::encode).decoder(HostSyncPacket::decode).consumerMainThread(HostSyncPacket::handle).add();
        CHANNEL.messageBuilder(RoundPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RoundPacket::encode).decoder(RoundPacket::decode).consumerMainThread(RoundPacket::handle).add();
        CHANNEL.messageBuilder(BuyPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BuyPacket::encode).decoder(BuyPacket::decode).consumerMainThread(BuyPacket::handle).add();
        CHANNEL.messageBuilder(TerminalActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TerminalActionPacket::encode).decoder(TerminalActionPacket::decode).consumerMainThread(TerminalActionPacket::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
