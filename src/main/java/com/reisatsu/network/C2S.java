package com.reisatsu.network;

import com.reisatsu.game.Camera;
import com.reisatsu.game.GameManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server messages. */
public final class C2S {
    public static final int INTRO_SKIP = 0, ENDING_DONE = 1, SHADERS = 2, BREATH = 3, UNHIDE = 4, BREAKER = 5, DIALOGUE_SKIP = 6,
            FINDER_ZOOM = 7, CREDITS_DONE = 8;

    private C2S() {}

    public record Action(int type, int arg) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(type);
            buf.writeVarInt(arg);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) GameManager.action(p, type, arg);
        }
    }

    /** The shutter: the photo's id (the client saves the PNG under it), the vertical FOV and zoom of the finder, the view. */
    public record Shutter(String id, float fov, float zoom, float yaw, float pitch) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            buf.writeFloat(fov);
            buf.writeFloat(zoom);
            buf.writeFloat(yaw);
            buf.writeFloat(pitch);
        }

        public static Shutter decode(FriendlyByteBuf buf) {
            return new Shutter(buf.readUtf(64), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) Camera.shutter(p, this);
        }
    }
}
