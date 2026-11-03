package com.reisatsu.network;

import com.reisatsu.client.ClientPacketHandler;
import com.reisatsu.game.GameData;
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

    /** The player's nerves and the week's progress (the night, its stage, what was witnessed tonight), five times a second. */
    public record State(float fear, float danger, float aware, int flags, int film, int ofuda, int captured, int submitted,
                        int phase, int reishou, float breath, int closingMask, int night, int nightStage, int signs) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(fear);
            buf.writeFloat(danger);
            buf.writeFloat(aware);
            buf.writeVarInt(flags);
            buf.writeVarInt(film);
            buf.writeVarInt(ofuda);
            buf.writeVarInt(captured);
            buf.writeVarInt(submitted);
            buf.writeVarInt(phase);
            buf.writeVarInt(reishou);
            buf.writeFloat(breath);
            buf.writeVarInt(closingMask);
            buf.writeVarInt(night);
            buf.writeVarInt(nightStage);
            buf.writeVarInt(signs);
        }

        public static State decode(FriendlyByteBuf buf) {
            return new State(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.state(this));
        }
    }

    /** A one-off event (jump scare, area title, subtitle, closing time, ending...). */
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

    /** The server's verdict on a shot: result (GameData.RESULT_*), the spirit (-1 none), ★, a ghost overlay (0 none). */
    public record PhotoResult(String id, int result, int spirit, int stars, int ghost) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeVarInt(result);
            buf.writeVarInt(spirit + 1);
            buf.writeVarInt(stars);
            buf.writeVarInt(ghost);
        }

        public static PhotoResult decode(FriendlyByteBuf buf) {
            return new PhotoResult(buf.readUtf(), buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.photoResult(this));
        }
    }

    /** Every shot of the night (the album), and the notes found. */
    public record Album(List<GameData.Photo> photos, List<String> notes) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(photos.size());
            for (GameData.Photo p : photos) {
                buf.writeUtf(p.id());
                buf.writeVarInt(p.spirit() + 1);
                buf.writeVarInt(p.stars());
                buf.writeVarLong(p.time());
                buf.writeVarInt(p.result());
                buf.writeVarInt(p.ghost());
            }
            buf.writeVarInt(notes.size());
            for (String n : notes) buf.writeUtf(n);
        }

        public static Album decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<GameData.Photo> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(new GameData.Photo(buf.readUtf(), buf.readVarInt() - 1, buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt()));
            }
            int m = buf.readVarInt();
            List<String> notes = new ArrayList<>(m);
            for (int i = 0; i < m; i++) notes.add(buf.readUtf());
            return new Album(list, notes);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.album(this));
        }
    }

    /** Opens a note; {@code extra} carries the witness list for the report. */
    public record Note(String id, String extra) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(extra);
        }

        public static Note decode(FriendlyByteBuf buf) {
            return new Note(buf.readUtf(), buf.readUtf());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.note(this));
        }
    }

    /** A spoken script: speaker ("medium", "waitress", "manager", "pa"...), and the lines with their durations (ticks). */
    public record Dialogue(String speaker, List<String> lines, List<Integer> ticks) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(speaker);
            buf.writeVarInt(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                buf.writeUtf(lines.get(i));
                buf.writeVarInt(ticks.get(i));
            }
        }

        public static Dialogue decode(FriendlyByteBuf buf) {
            String sp = buf.readUtf();
            int n = buf.readVarInt();
            List<String> l = new ArrayList<>(n);
            List<Integer> t = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                l.add(buf.readUtf());
                t.add(buf.readVarInt());
            }
            return new Dialogue(sp, l, t);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.dialogue(this));
        }
    }

    /** Opens the breaker panel GUI with the circuits' states (bit per Circuit ordinal). */
    public record Breaker(int on) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(on);
        }

        public static Breaker decode(FriendlyByteBuf buf) {
            return new Breaker(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.breaker(this));
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
}
