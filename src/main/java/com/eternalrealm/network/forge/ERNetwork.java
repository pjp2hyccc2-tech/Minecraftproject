package com.eternalrealm.network.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's single network channel. See docs/NETWORK_PROTOCOL.md for the packet table and validation rules.
 *
 * <p>Clients without the mod, or with a different {@link #PROTOCOL_VERSION}, are refused at login: the server never
 * has to deal with clients that cannot speak the protocol.</p>
 */
public final class ERNetwork {
    /** Bump whenever a packet is added, removed, reordered or its layout changes. */
    public static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static boolean registered;

    private ERNetwork() {
    }

    /** Registers every packet. Must run during mod construction, identically on both sides. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(CharacterSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CharacterSnapshotPacket::encode)
                .decoder(CharacterSnapshotPacket::decode)
                .consumerMainThread(CharacterSnapshotPacket::handle)
                .add();
        CHANNEL.messageBuilder(AllocateStatPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AllocateStatPacket::encode)
                .decoder(AllocateStatPacket::decode)
                .consumerMainThread(AllocateStatPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** Client side only (called from client/ui code). */
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
