package com.birdiecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Big on-screen golf announcements ("BIRDIE!", "OB", "NICE ON!") with an optional sound. */
public final class S2CMessagePacket {
    public static final int KIND_TITLE = 0;
    public static final int KIND_SUB = 1;
    public static final int KIND_TOAST = 2;

    public static final int SOUND_NONE = 0;
    public static final int SOUND_FANFARE_SMALL = 1;
    public static final int SOUND_FANFARE_BIG = 2;
    public static final int SOUND_APPLAUSE = 3;
    public static final int SOUND_GROAN = 4;
    public static final int SOUND_PERFECT = 5;
    public static final int SOUND_TURN = 6;

    public final int kind;
    public final Component text;
    public final int color;
    public final int sound;
    public final int duration;

    public S2CMessagePacket(int kind, Component text, int color, int sound, int duration) {
        this.kind = kind;
        this.text = text;
        this.color = color;
        this.sound = sound;
        this.duration = duration;
    }

    public S2CMessagePacket(FriendlyByteBuf buf) {
        this.kind = buf.readByte();
        this.text = buf.readComponent();
        this.color = buf.readInt();
        this.sound = buf.readByte();
        this.duration = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeComponent(text);
        buf.writeInt(color);
        buf.writeByte(sound);
        buf.writeVarInt(duration);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.birdiecraft.client.ClientPacketHandler.onMessage(this));
    }
}
