package jp.satsujinki.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: spawn a blood effect (particles, screen blood, camera shake) at a position. */
public final class GoreFxPacket {
    public static final int BLEED = 0, DEATH_GENERIC = 1, DEATH_MIXER = 2, DEATH_PRESS = 3, DEATH_SAW = 4, DEATH_GUILLOTINE = 5,
            DEATH_SPIKES = 6, MIXER_GRIND = 7, SAW_CUT = 8, PRESS_IMPACT = 9, SPAWN_POOF = 10;

    public final double x, y, z;
    public final int kind;
    public final float intensity, dirX, dirZ;

    public GoreFxPacket(double x, double y, double z, int kind, float intensity, float dirX, float dirZ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.kind = kind;
        this.intensity = intensity;
        this.dirX = dirX;
        this.dirZ = dirZ;
    }

    public static void encode(GoreFxPacket p, FriendlyByteBuf buf) {
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeByte(p.kind);
        buf.writeFloat(p.intensity);
        buf.writeFloat(p.dirX);
        buf.writeFloat(p.dirZ);
    }

    public static GoreFxPacket decode(FriendlyByteBuf buf) {
        return new GoreFxPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(GoreFxPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> jp.satsujinki.client.ClientFx.handle(p));
        ctx.get().setPacketHandled(true);
    }
}
