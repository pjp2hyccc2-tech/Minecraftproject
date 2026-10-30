package com.mcbesiege.net;

import com.mcbesiege.MCBesiege;
import com.mcbesiege.entity.MachineEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Networking: generic NBT messages both ways, a compact per-tick machine pose packet and the control key state. */
public final class Net {
    private Net() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MCBesiege.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void init() {
        CHANNEL.messageBuilder(S2C.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2C::encode).decoder(S2C::decode).consumerMainThread(S2C::handle).add();
        CHANNEL.messageBuilder(C2S.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2S::encode).decoder(C2S::decode).consumerMainThread(C2S::handle).add();
        CHANNEL.messageBuilder(State.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(State::encode).decoder(State::decode).consumerMainThread(State::handle).add();
        CHANNEL.messageBuilder(KeyState.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KeyState::encode).decoder(KeyState::decode).consumerMainThread(KeyState::handle).add();
    }

    public static void send(ServerPlayer player, String kind, CompoundTag data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2C(kind, data));
    }

    public static void toTracking(MachineEntity e, String kind, CompoundTag data) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> e), new S2C(kind, data));
    }

    public static void sendState(MachineEntity e, int version, double[] poses, byte[] flags) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> e), new State(e.getId(), version, poses, flags));
    }

    public static void toServer(String kind, CompoundTag data) {
        CHANNEL.sendToServer(new C2S(kind, data));
    }

    public static void sendKeys(int entityId, int[] keys) {
        CHANNEL.sendToServer(new KeyState(entityId, keys));
    }

    // ------------------------------------------------------------------------------------------------------------

    public record S2C(String kind, CompoundTag data) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(kind);
            buf.writeNbt(data);
        }

        public static S2C decode(FriendlyByteBuf buf) {
            String k = buf.readUtf();
            CompoundTag t = buf.readNbt();
            return new S2C(k, t == null ? new CompoundTag() : t);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mcbesiege.client.ClientNet.handle(kind, data));
            ctx.get().setPacketHandled(true);
        }
    }

    public record C2S(String kind, CompoundTag data) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(kind);
            buf.writeNbt(data);
        }

        public static C2S decode(FriendlyByteBuf buf) {
            String k = buf.readUtf();
            CompoundTag t = buf.readNbt();
            return new C2S(k, t == null ? new CompoundTag() : t);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().setPacketHandled(true);
            if (p != null) ServerNet.handle(p, kind, data);
        }
    }

    public record State(int id, int version, double[] poses, byte[] flags) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeVarInt(version);
            buf.writeVarInt(poses.length / 7);
            for (int i = 0; i < poses.length; i += 7) {
                buf.writeDouble(poses[i]);
                buf.writeDouble(poses[i + 1]);
                buf.writeDouble(poses[i + 2]);
                for (int k = 3; k < 7; k++) buf.writeFloat((float) poses[i + k]);
            }
            buf.writeByteArray(flags);
        }

        public static State decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            int v = buf.readVarInt();
            int n = buf.readVarInt();
            if (n < 0 || n > 4096) n = 0;
            double[] poses = new double[n * 7];
            for (int i = 0; i < n * 7; i += 7) {
                poses[i] = buf.readDouble();
                poses[i + 1] = buf.readDouble();
                poses[i + 2] = buf.readDouble();
                for (int k = 3; k < 7; k++) poses[i + k] = buf.readFloat();
            }
            byte[] flags = buf.readByteArray(65536);
            return new State(id, v, poses, flags);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mcbesiege.client.ClientNet.state(id, version, poses, flags));
            ctx.get().setPacketHandled(true);
        }
    }

    public record KeyState(int id, int[] keys) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeVarIntArray(keys);
        }

        public static KeyState decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            int[] k = buf.readVarIntArray(64);
            return new KeyState(id, k);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().setPacketHandled(true);
            if (p != null) ServerNet.keys(p, id, keys);
        }
    }
}
