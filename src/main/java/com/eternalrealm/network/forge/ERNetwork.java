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
    public static final String PROTOCOL_VERSION = "8";

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
        CHANNEL.messageBuilder(CombatActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CombatActionPacket::encode)
                .decoder(CombatActionPacket::decode)
                .consumerMainThread(CombatActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(CombatStatusPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CombatStatusPacket::encode)
                .decoder(CombatStatusPacket::decode)
                .consumerMainThread(CombatStatusPacket::handle)
                .add();
        CHANNEL.messageBuilder(CombatStatsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CombatStatsPacket::encode)
                .decoder(CombatStatsPacket::decode)
                .consumerMainThread(CombatStatsPacket::handle)
                .add();
        CHANNEL.messageBuilder(DamageNumberPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DamageNumberPacket::encode)
                .decoder(DamageNumberPacket::decode)
                .consumerMainThread(DamageNumberPacket::handle)
                .add();
        CHANNEL.messageBuilder(UiActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UiActionPacket::encode)
                .decoder(UiActionPacket::decode)
                .consumerMainThread(UiActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(UiDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UiDataPacket::encode)
                .decoder(UiDataPacket::decode)
                .consumerMainThread(UiDataPacket::handle)
                .add();
        CHANNEL.messageBuilder(PlayerActionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerActionPacket::encode)
                .decoder(PlayerActionPacket::decode)
                .consumerMainThread(PlayerActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(TelegraphPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TelegraphPacket::encode)
                .decoder(TelegraphPacket::decode)
                .consumerMainThread(TelegraphPacket::handle)
                .add();
        CHANNEL.messageBuilder(UseItemPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UseItemPacket::encode)
                .decoder(UseItemPacket::decode)
                .consumerMainThread(UseItemPacket::handle)
                .add();
        CHANNEL.messageBuilder(PickupFeedPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PickupFeedPacket::encode)
                .decoder(PickupFeedPacket::decode)
                .consumerMainThread(PickupFeedPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** To every player tracking {@code entity} (not the entity itself). */
    public static void sendToTracking(net.minecraft.world.entity.Entity entity, Object message) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
    }

    /** To every player tracking {@code entity} and the entity itself if it is a player. */
    public static void sendToTrackingAndSelf(net.minecraft.world.entity.Entity entity, Object message) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), message);
    }

    /** Client side only (called from client/ui code). */
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
