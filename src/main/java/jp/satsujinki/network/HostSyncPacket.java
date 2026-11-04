package jp.satsujinki.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the host's points, combo, style rank and records (+ an optional kill popup). */
public final class HostSyncPacket {
    public final int points, totalKills, combo, rank;
    public final float gauge;
    public final int popupStyle, popupPoints;
    public final boolean rankUp;
    public final int maxCombo, bestRank, rounds, perfect;
    public final int[] styleKills;

    public HostSyncPacket(int points, int totalKills, int combo, int rank, float gauge, int popupStyle, int popupPoints, boolean rankUp,
                          int maxCombo, int bestRank, int rounds, int perfect, int[] styleKills) {
        this.points = points;
        this.totalKills = totalKills;
        this.combo = combo;
        this.rank = rank;
        this.gauge = gauge;
        this.popupStyle = popupStyle;
        this.popupPoints = popupPoints;
        this.rankUp = rankUp;
        this.maxCombo = maxCombo;
        this.bestRank = bestRank;
        this.rounds = rounds;
        this.perfect = perfect;
        this.styleKills = styleKills;
    }

    public static void encode(HostSyncPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.points);
        buf.writeVarInt(p.totalKills);
        buf.writeVarInt(p.combo);
        buf.writeVarInt(p.rank);
        buf.writeFloat(p.gauge);
        buf.writeVarInt(p.popupStyle + 1);
        buf.writeVarInt(p.popupPoints);
        buf.writeBoolean(p.rankUp);
        buf.writeVarInt(p.maxCombo);
        buf.writeVarInt(p.bestRank);
        buf.writeVarInt(p.rounds);
        buf.writeVarInt(p.perfect);
        buf.writeVarIntArray(p.styleKills);
    }

    public static HostSyncPacket decode(FriendlyByteBuf buf) {
        return new HostSyncPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                buf.readVarInt() - 1, buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarIntArray());
    }

    public static void handle(HostSyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> jp.satsujinki.client.ClientState.onSync(p));
        ctx.get().setPacketHandled(true);
    }
}
