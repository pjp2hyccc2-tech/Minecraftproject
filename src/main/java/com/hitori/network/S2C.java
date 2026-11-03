package com.hitori.network;

import com.hitori.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server to client messages. Handlers hop to {@link ClientPacketHandler}, which only loads on the client. */
public final class S2C {
    private S2C() {}

    /**
     * The player's nerves and body and the night's progress, five times a second: fear, danger, the salt water in the
     * mouth, the held breath, flags (GameManager.F_*), the phase, the steps done, the HUD line, the clock, the rules
     * broken, the timed action in progress (key and 0..1), the doll's name.
     */
    public record State(float fear, float danger, float mouth, float breath, int flags, int phase, long steps, int hud, float clock,
                        int broken, String action, float progress, String dollName, int cameras) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(fear);
            buf.writeFloat(danger);
            buf.writeFloat(mouth);
            buf.writeFloat(breath);
            buf.writeVarInt(flags);
            buf.writeVarInt(phase);
            buf.writeLong(steps);
            buf.writeVarInt(hud + 1);
            buf.writeFloat(clock);
            buf.writeVarInt(broken);
            buf.writeUtf(action, 32);
            buf.writeFloat(progress);
            buf.writeUtf(dollName, 64);
            buf.writeVarInt(cameras);
        }

        public static State decode(FriendlyByteBuf buf) {
            return new State(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                    buf.readLong(), buf.readVarInt() - 1, buf.readFloat(), buf.readVarInt(), buf.readUtf(32), buf.readFloat(), buf.readUtf(64),
                    buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.state(this));
        }
    }

    /** A one-off event (GameManager.EV_*: a step done, three o'clock, eyes closed, a jump scare, an ending...). */
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

    /** A spoken line (subtitle): speaker ("me", "doll", "woman", "phone", "tv", ""), a lang key with arguments, how long. */
    public record Say(String speaker, String key, List<String> args, int ticks) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(speaker);
            buf.writeUtf(key);
            buf.writeVarInt(args.size());
            for (String a : args) buf.writeUtf(a);
            buf.writeVarInt(ticks);
        }

        public static Say decode(FriendlyByteBuf buf) {
            String sp = buf.readUtf();
            String key = buf.readUtf();
            int n = buf.readVarInt();
            List<String> args = new ArrayList<>(n);
            for (int i = 0; i < n; i++) args.add(buf.readUtf());
            return new Say(sp, key, args, buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.say(this));
        }
    }

    /** A positional sound only this player hears (director tricks). */
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

    /**
     * The endings' last feeds: the client views the apartment from this eye (a camera's, or a virtual one), takes
     * {@code frames} frames {@code interval} ticks apart under its black / white screen and keeps them for the ending
     * screen ({@code label}: "fusuma", "bathtub", "balcony"...). number = the camera's number (0: virtual).
     */
    public record Capture(int number, double x, double y, double z, float yaw, float pitch, String label, int frames, int interval) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(number);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(yaw);
            buf.writeFloat(pitch);
            buf.writeUtf(label);
            buf.writeVarInt(frames);
            buf.writeVarInt(interval);
        }

        public static Capture decode(FriendlyByteBuf buf) {
            return new Capture(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                    buf.readUtf(), buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.capture(this));
        }
    }
}
