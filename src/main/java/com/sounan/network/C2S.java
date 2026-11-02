package com.sounan.network;

import com.sounan.game.GameManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server messages. */
public final class C2S {
    public static final int START_GAME = 0, SAKAZUKI = 1, ENDING_DONE = 2;

    private C2S() {}

    public record Action(int type) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(type);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            switch (type) {
                case START_GAME -> {
                    if (!GameManager.inGame(p)) GameManager.startGame(p);
                }
                case SAKAZUKI -> GameManager.sakazukiAccepted(p);
                case ENDING_DONE -> GameManager.endingDone(p);
                default -> {
                }
            }
        }
    }
}
