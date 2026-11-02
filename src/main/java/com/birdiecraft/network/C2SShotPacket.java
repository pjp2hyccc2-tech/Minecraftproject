package com.birdiecraft.network;

import com.birdiecraft.golf.engine.Club;
import com.birdiecraft.golf.engine.ShotInput;
import com.birdiecraft.golf.engine.ShotMode;
import com.birdiecraft.match.MatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The player finished the swing gauge. */
public final class C2SShotPacket {
    private final int club;
    private final int mode;
    private final float yaw;
    private final float power;
    private final float deviation;
    private final int spinX;
    private final int spinY;
    private final int puttRange;

    public C2SShotPacket(ShotInput in) {
        this.club = in.club().ordinal();
        this.mode = in.mode().ordinal();
        this.yaw = (float) in.yawDeg();
        this.power = (float) in.power();
        this.deviation = (float) in.deviation();
        this.spinX = in.spinX();
        this.spinY = in.spinY();
        this.puttRange = in.puttRange();
    }

    public C2SShotPacket(FriendlyByteBuf buf) {
        this.club = buf.readByte();
        this.mode = buf.readByte();
        this.yaw = buf.readFloat();
        this.power = buf.readFloat();
        this.deviation = buf.readFloat();
        this.spinX = buf.readByte();
        this.spinY = buf.readByte();
        this.puttRange = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(club);
        buf.writeByte(mode);
        buf.writeFloat(yaw);
        buf.writeFloat(power);
        buf.writeFloat(deviation);
        buf.writeByte(spinX);
        buf.writeByte(spinY);
        buf.writeByte(puttRange);
    }

    public ShotInput toInput() {
        float safePower = Float.isFinite(power) ? power : 0f;
        float safeDev = Float.isFinite(deviation) ? deviation : 3f;
        float safeYaw = Float.isFinite(yaw) ? yaw : 0f;
        return new ShotInput(Club.byId(club), ShotMode.byId(mode), safeYaw, safePower, safeDev, spinX, spinY, puttRange);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) MatchManager.handleShot(player, toInput());
    }
}
