package com.nihonisshu.tabi.network;

import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.dialogue.DialogueManager;
import com.nihonisshu.tabi.game.ShopManager;
import com.nihonisshu.tabi.game.TicketOffice;
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

/** One generic message type per direction: a kind string plus an NBT payload. */
public final class TabiNet {
    private TabiNet() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TabiMod.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void init() {
        CHANNEL.messageBuilder(S2C.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2C::encode).decoder(S2C::decode).consumerMainThread(S2C::handle).add();
        CHANNEL.messageBuilder(C2S.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2S::encode).decoder(C2S::decode).consumerMainThread(C2S::handle).add();
    }

    public static void send(ServerPlayer player, String kind, CompoundTag data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2C(kind, data));
    }

    public static void toServer(String kind, CompoundTag data) {
        CHANNEL.sendToServer(new C2S(kind, data));
    }

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
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.nihonisshu.tabi.client.ClientHooks.handle(kind, data));
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
            if (p == null) return;
            switch (kind) {
                case "choice" -> DialogueManager.choose(p, data);
                case "buy" -> ShopManager.buy(p, data.getString("shop"), data.getInt("index"), Math.max(1, data.getInt("count")));
                case "sell" -> ShopManager.sell(p, data.getString("shop"), data.getInt("index"));
                case "ticket" -> TicketOffice.buy(p, data.getString("route"), data.getInt("from"), data.getInt("to"));
                default -> TabiMod.LOGGER.debug("[tabi] unknown c2s {}", kind);
            }
        }
    }
}
