package com.sounan.network;

import com.sounan.Sounan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Sounan.id("main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int nextId;

    private ModNetwork() {}

    public static void register() {
        toClient(S2C.State.class, S2C.State::encode, S2C.State::decode, S2C.State::handle);
        toClient(S2C.Event.class, S2C.Event::encode, S2C.Event::decode, S2C.Event::handle);
        toClient(S2C.Sound.class, S2C.Sound::encode, S2C.Sound::decode, S2C.Sound::handle);
        toClient(S2C.Note.class, S2C.Note::encode, S2C.Note::decode, S2C.Note::handle);
        toServer(C2S.Action.class, C2S.Action::encode, C2S.Action::decode, C2S.Action::handle);
    }

    private static <M> void toServer(Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, M> decoder,
                                     BiConsumer<M, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(encoder).decoder(decoder).consumerMainThread(handler).add();
    }

    private static <M> void toClient(Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, M> decoder,
                                     BiConsumer<M, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encoder).decoder(decoder).consumerMainThread(handler).add();
    }

    public static void send(ServerPlayer player, Object message) {
        if (player.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
