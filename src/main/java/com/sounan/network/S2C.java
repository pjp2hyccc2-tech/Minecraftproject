package com.sounan.network;

import com.sounan.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client messages. Handlers hop to {@link ClientPacketHandler}, which is only loaded on the client. */
public final class S2C {
    private S2C() {}

    /** The player's body and the night's progress, sent 5 times a second. */
    public record State(float fear, float danger, float stamina, float flash, float phone, int flags, int captures, int area,
                        int minutes, int days, int notes) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(fear);
            buf.writeFloat(danger);
            buf.writeFloat(stamina);
            buf.writeFloat(flash);
            buf.writeFloat(phone);
            buf.writeVarInt(flags);
            buf.writeVarInt(captures);
            buf.writeVarInt(area);
            buf.writeVarInt(minutes);
            buf.writeVarInt(days);
            buf.writeVarInt(notes);
        }

        public static State decode(FriendlyByteBuf buf) {
            return new State(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.state(this));
        }
    }

    /** A one-off event (jump scare, area title, thought, ending...). */
    public record Event(int type, int arg, String text) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(type);
            buf.writeVarInt(arg);
            buf.writeUtf(text);
        }

        public static Event decode(FriendlyByteBuf buf) {
            return new Event(buf.readVarInt(), buf.readVarInt(), buf.readUtf());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.event(this));
        }
    }

    /** A positional sound only this player hears (director tricks, hallucinations). */
    public record Sound(String id, double x, double y, double z, float volume, float pitch) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(volume);
            buf.writeFloat(pitch);
        }

        public static Sound decode(FriendlyByteBuf buf) {
            return new Sound(buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.sound(this));
        }
    }

    /** Opens a note. {@code extra} = "playerName|captures|dayOffset". */
    public record Note(int id, String extra) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUtf(extra);
        }

        public static Note decode(FriendlyByteBuf buf) {
            return new Note(buf.readVarInt(), buf.readUtf());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.note(this));
        }
    }
}
