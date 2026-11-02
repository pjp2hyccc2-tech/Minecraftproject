package com.birdiecraft.network;

import com.birdiecraft.course.HoleMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class S2CHoleMapPacket {
    public final int matchId;
    public final int holeIdx;
    public final HoleMap map;

    public S2CHoleMapPacket(int matchId, int holeIdx, HoleMap map) {
        this.matchId = matchId;
        this.holeIdx = holeIdx;
        this.map = map;
    }

    public S2CHoleMapPacket(FriendlyByteBuf buf) {
        this.matchId = buf.readVarInt();
        this.holeIdx = buf.readVarInt();
        this.map = HoleMap.read(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(matchId);
        buf.writeVarInt(holeIdx);
        map.write(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.birdiecraft.client.ClientPacketHandler.onHoleMap(this));
    }
}
