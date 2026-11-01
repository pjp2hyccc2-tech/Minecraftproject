package com.frostbound.network;

import com.frostbound.FrostboundMod;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Two generic packets: client-to-server actions ({@code op} + NBT arguments) and server-to-client data
 * ({@code kind} + NBT). Handlers always run on the main thread of the receiving side.
 */
public final class Net {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(FrostboundMod.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    private Net() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(0, Action.class, Action::encode, Action::decode, Action::handle);
        CHANNEL.registerMessage(1, Data.class, Data::encode, Data::decode, Data::handle);
    }

    public static void sendToServer(String op, CompoundTag args) {
        CHANNEL.sendToServer(new Action(op, args));
    }

    public static void send(ServerPlayer player, String kind, CompoundTag data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Data(kind, data));
    }

    /** Client to server. */
    public record Action(String op, CompoundTag args) {
        static void encode(Action a, FriendlyByteBuf buf) {
            buf.writeUtf(a.op, 64);
            buf.writeNbt(a.args);
        }

        static Action decode(FriendlyByteBuf buf) {
            String op = buf.readUtf(64);
            CompoundTag tag = buf.readNbt();
            return new Action(op, tag == null ? new CompoundTag() : tag);
        }

        static void handle(Action a, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player != null) {
                    ServerActions.handle(player, a.op, a.args);
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Server to client. */
    public record Data(String kind, CompoundTag data) {
        static void encode(Data d, FriendlyByteBuf buf) {
            buf.writeUtf(d.kind, 64);
            buf.writeNbt(d.data);
        }

        static Data decode(FriendlyByteBuf buf) {
            String kind = buf.readUtf(64);
            CompoundTag tag = buf.readNbt();
            return new Data(kind, tag == null ? new CompoundTag() : tag);
        }

        static void handle(Data d, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.frostbound.client.ClientPackets.handle(d.kind, d.data)));
            c.setPacketHandled(true);
        }
    }
}
