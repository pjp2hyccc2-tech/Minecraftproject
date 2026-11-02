package com.clockworkdiver.network;

import com.clockworkdiver.ClockworkDiver;
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
    private static final String PROTOCOL = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ClockworkDiver.id("main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int nextId;

    private ModNetwork() {}

    public static void register() {
        toServer(C2S.AcceptCommission.class, C2S.AcceptCommission::encode, C2S.AcceptCommission::decode, C2S.AcceptCommission::handle);
        toServer(C2S.GoToBench.class, C2S.GoToBench::encode, C2S.GoToBench::decode, C2S.GoToBench::handle);
        toServer(C2S.DiscoverDefect.class, C2S.DiscoverDefect::encode, C2S.DiscoverDefect::decode, C2S.DiscoverDefect::handle);
        toServer(C2S.StartDive.class, C2S.StartDive::encode, C2S.StartDive::decode, C2S.StartDive::handle);
        toServer(C2S.DeliverClock.class, C2S.DeliverClock::encode, C2S.DeliverClock::decode, C2S.DeliverClock::handle);
        toServer(C2S.BeatInput.class, C2S.BeatInput::encode, C2S.BeatInput::decode, C2S.BeatInput::handle);
        toServer(C2S.AbortDive.class, C2S.AbortDive::encode, C2S.AbortDive::decode, C2S.AbortDive::handle);
        toServer(C2S.Upgrade.class, C2S.Upgrade::encode, C2S.Upgrade::decode, C2S.Upgrade::handle);

        toClient(S2C.OpenDialogue.class, S2C.OpenDialogue::encode, S2C.OpenDialogue::decode, S2C.OpenDialogue::handle);
        toClient(S2C.OpenBench.class, S2C.OpenBench::encode, S2C.OpenBench::decode, S2C.OpenBench::handle);
        toClient(S2C.Objective.class, S2C.Objective::encode, S2C.Objective::decode, S2C.Objective::handle);
        toClient(S2C.Hint.class, S2C.Hint::encode, S2C.Hint::decode, S2C.Hint::handle);
        toClient(S2C.DiveState.class, S2C.DiveState::encode, S2C.DiveState::decode, S2C.DiveState::handle);
        toClient(S2C.Combat.class, S2C.Combat::encode, S2C.Combat::decode, S2C.Combat::handle);
        toClient(S2C.Transition.class, S2C.Transition::encode, S2C.Transition::decode, S2C.Transition::handle);
        toClient(S2C.TempoSync.class, S2C.TempoSync::encode, S2C.TempoSync::decode, S2C.TempoSync::handle);
        toClient(S2C.RepairPrompt.class, S2C.RepairPrompt::encode, S2C.RepairPrompt::decode, S2C.RepairPrompt::handle);
        toClient(S2C.Memory.class, S2C.Memory::encode, S2C.Memory::decode, S2C.Memory::handle);
        toClient(S2C.StorySync.class, S2C.StorySync::encode, S2C.StorySync::decode, S2C.StorySync::handle);
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
