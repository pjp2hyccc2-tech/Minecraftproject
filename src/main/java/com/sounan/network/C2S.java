package com.sounan.network;

import com.sounan.game.GameManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server messages: phone apps, shader state, ending / intro done. */
public final class C2S {
    public static final int PHONE_OPEN = 0, PHONE_CAMERA = 1, PHONE_TORCH = 2, PHONE_CALL = 3, SHADERS = 4, ENDING_DONE = 5,
            INTRO_DONE = 6;

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
            if (p == null) return;
            GameManager.phoneAction(p, type, arg);
        }
    }
}
