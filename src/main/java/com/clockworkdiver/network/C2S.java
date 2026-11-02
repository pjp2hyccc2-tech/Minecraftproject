package com.clockworkdiver.network;

import com.clockworkdiver.combat.Quality;
import com.clockworkdiver.combat.TickStrike;
import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.workshop.Upgrades;
import com.clockworkdiver.workshop.WorkshopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server messages. Every handler runs on the server thread. */
public final class C2S {
    private C2S() {}

    private static ServerPlayer sender(Supplier<NetworkEvent.Context> ctx) {
        return ctx.get().getSender();
    }

    public record AcceptCommission(int entityId) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
        }

        public static AcceptCommission decode(FriendlyByteBuf buf) {
            return new AcceptCommission(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) WorkshopManager.acceptCommission(p, entityId);
        }
    }

    public record GoToBench() {
        public void encode(FriendlyByteBuf buf) {
        }

        public static GoToBench decode(FriendlyByteBuf buf) {
            return new GoToBench();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) WorkshopManager.goToBench(p);
        }
    }

    public record DiscoverDefect(int defectId) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(defectId);
        }

        public static DiscoverDefect decode(FriendlyByteBuf buf) {
            return new DiscoverDefect(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) WorkshopManager.discoverDefect(p, defectId);
        }
    }

    public record StartDive(int defectId) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(defectId);
        }

        public static StartDive decode(FriendlyByteBuf buf) {
            return new StartDive(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) DiveManager.startDive(p, defectId);
        }
    }

    public record DeliverClock(int entityId) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
        }

        public static DeliverClock decode(FriendlyByteBuf buf) {
            return new DeliverClock(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) WorkshopManager.deliverClock(p, entityId);
        }
    }

    /** Sent the moment the attack or use key is pressed inside a clock, judged against the client's view of the beat. */
    public record BeatInput(Quality quality, boolean attack) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(quality.ordinal());
            buf.writeBoolean(attack);
        }

        public static BeatInput decode(FriendlyByteBuf buf) {
            return new BeatInput(Quality.byOrdinal(buf.readByte()), buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) TickStrike.onBeatInput(p, quality, attack);
        }
    }

    public record AbortDive() {
        public void encode(FriendlyByteBuf buf) {
        }

        public static AbortDive decode(FriendlyByteBuf buf) {
            return new AbortDive();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) DiveManager.abort(p);
        }
    }

    public record Upgrade(int kind) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
        }

        public static Upgrade decode(FriendlyByteBuf buf) {
            return new Upgrade(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = sender(ctx);
            if (p != null) Upgrades.buy(p, kind);
        }
    }
}
