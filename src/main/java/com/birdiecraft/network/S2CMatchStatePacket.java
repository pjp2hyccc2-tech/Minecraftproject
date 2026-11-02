package com.birdiecraft.network;

import com.birdiecraft.match.MatchView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Full match snapshot (or "not in a match" when view is null). */
public final class S2CMatchStatePacket {
    public final MatchView view;

    public S2CMatchStatePacket(MatchView view) {
        this.view = view;
    }

    /** "You are not in a match any more." */
    public static S2CMatchStatePacket none() {
        return new S2CMatchStatePacket((MatchView) null);
    }

    public S2CMatchStatePacket(FriendlyByteBuf buf) {
        this.view = buf.readBoolean() ? MatchView.read(buf) : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(view != null);
        if (view != null) view.write(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.birdiecraft.client.ClientPacketHandler.onMatchState(this));
    }
}
