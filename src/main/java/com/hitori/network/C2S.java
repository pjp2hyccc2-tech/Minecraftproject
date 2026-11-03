package com.hitori.network;

import com.hitori.game.GameManager;
import com.hitori.game.Ritual;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server messages. */
public final class C2S {
    /** BREATH (hold 1 / release 0), ACTION (the R key), APP (camera number viewed, 0 = closed; arg >> 8 = device), PEEK (1 / 0),
     *  UNHIDE, PHONE (0 silent, 1 light, 2 answer, 3 decline), ENDING_DONE, SHADERS, SKIP, EYES_DONE, PRINTOUT (read it). */
    public static final int BREATH = 0, ACTION = 1, APP = 2, PEEK = 3, UNHIDE = 4, PHONE = 5, ENDING_DONE = 6, SHADERS = 7, SKIP = 8,
            PRINTOUT = 9;

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

    /** The doll's name from the naming field (step 10). */
    public record Name(String name) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(name, 64);
        }

        public static Name decode(FriendlyByteBuf buf) {
            return new Name(buf.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) Ritual.named(p, name);
        }
    }
}
