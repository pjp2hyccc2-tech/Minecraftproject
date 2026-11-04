package jp.satsujinki.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: death game round announcements and status. */
public final class RoundPacket {
    public static final int INTRO = 0, COUNT = 1, GO = 2, UPDATE = 3, END = 4;

    public final int type;
    public final int a, b, c, d, e;
    public final boolean flag;

    public RoundPacket(int type, int a, int b, int c, int d, int e, boolean flag) {
        this.type = type;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.flag = flag;
    }

    public static RoundPacket intro(int count) {
        return new RoundPacket(INTRO, count, 0, 0, 0, 0, false);
    }

    public static RoundPacket count(int n) {
        return new RoundPacket(COUNT, n, 0, 0, 0, 0, false);
    }

    public static RoundPacket go(int alive, int total, int ticksLeft) {
        return new RoundPacket(GO, alive, total, ticksLeft, 0, 0, false);
    }

    public static RoundPacket update(int alive, int total, int ticksLeft) {
        return new RoundPacket(UPDATE, alive, total, ticksLeft, 0, 0, false);
    }

    /** kills, total, points earned this round, perfect bonus, duration ticks, perfect? */
    public static RoundPacket end(int kills, int total, int earned, int bonus, int ticks, boolean perfect) {
        return new RoundPacket(END, kills, total, earned, bonus, ticks, perfect);
    }

    public static void encode(RoundPacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.type);
        buf.writeVarInt(p.a);
        buf.writeVarInt(p.b);
        buf.writeVarInt(p.c);
        buf.writeVarInt(p.d);
        buf.writeVarInt(p.e);
        buf.writeBoolean(p.flag);
    }

    public static RoundPacket decode(FriendlyByteBuf buf) {
        return new RoundPacket(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(RoundPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> jp.satsujinki.client.ClientState.onRound(p));
        ctx.get().setPacketHandled(true);
    }
}
