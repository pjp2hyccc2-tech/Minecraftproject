package com.birdiecraft.network;

import com.birdiecraft.golf.engine.Trajectory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A ball flight to animate on the client. */
public final class S2CBallFlightPacket {
    public final int participantId;
    public final boolean practice;
    public final int color;
    public final int club;
    public final boolean perfect;
    public final float[] xs, ys, zs;
    public final byte[] events;
    public final int result;
    public final int restLie;
    public final float carry;
    public final float total;

    public S2CBallFlightPacket(int participantId, boolean practice, int color, int club, boolean perfect, Trajectory t) {
        this.participantId = participantId;
        this.practice = practice;
        this.color = color;
        this.club = club;
        this.perfect = perfect;
        this.xs = t.xs;
        this.ys = t.ys;
        this.zs = t.zs;
        this.events = t.events;
        this.result = t.result.ordinal();
        this.restLie = t.restLie.ordinal();
        this.carry = (float) Math.max(0, t.carry);
        this.total = (float) t.totalDistance();
    }

    public S2CBallFlightPacket(FriendlyByteBuf buf) {
        this.participantId = buf.readVarInt() - 1;
        this.practice = buf.readBoolean();
        this.color = buf.readInt();
        this.club = buf.readByte();
        this.perfect = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), 2000);
        float ox = buf.readFloat();
        float oy = buf.readFloat();
        float oz = buf.readFloat();
        this.xs = new float[n];
        this.ys = new float[n];
        this.zs = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = ox + buf.readFloat();
            ys[i] = oy + buf.readFloat();
            zs[i] = oz + buf.readFloat();
        }
        this.events = buf.readByteArray(4096);
        this.result = buf.readByte();
        this.restLie = buf.readByte();
        this.carry = buf.readFloat();
        this.total = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(participantId + 1);
        buf.writeBoolean(practice);
        buf.writeInt(color);
        buf.writeByte(club);
        buf.writeBoolean(perfect);
        int n = xs.length;
        buf.writeVarInt(n);
        float ox = n > 0 ? xs[0] : 0;
        float oy = n > 0 ? ys[0] : 0;
        float oz = n > 0 ? zs[0] : 0;
        buf.writeFloat(ox);
        buf.writeFloat(oy);
        buf.writeFloat(oz);
        for (int i = 0; i < n; i++) {
            buf.writeFloat(xs[i] - ox);
            buf.writeFloat(ys[i] - oy);
            buf.writeFloat(zs[i] - oz);
        }
        buf.writeByteArray(events);
        buf.writeByte(result);
        buf.writeByte(restLie);
        buf.writeFloat(carry);
        buf.writeFloat(total);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.birdiecraft.client.ClientPacketHandler.onBallFlight(this));
    }
}
