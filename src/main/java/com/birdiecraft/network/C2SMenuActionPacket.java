package com.birdiecraft.network;

import com.birdiecraft.match.MenuActions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Golf menu buttons. */
public final class C2SMenuActionPacket {
    public static final int REQUEST_MENU = 0;
    public static final int START_SINGLE = 1;
    public static final int START_VS = 2;
    public static final int CREATE_LOBBY = 3;
    public static final int JOIN = 4;
    public static final int LEAVE = 5;
    public static final int BEGIN = 6;
    public static final int SET_STYLE = 7;
    public static final int BUILD_PRESET = 8;
    public static final int ADD_CPU = 9;

    public final int action;
    public final String arg;
    public final int[] ints;

    public C2SMenuActionPacket(int action, String arg, int... ints) {
        this.action = action;
        this.arg = arg == null ? "" : arg;
        this.ints = ints == null ? new int[0] : ints;
    }

    public C2SMenuActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readByte();
        this.arg = buf.readUtf(64);
        this.ints = buf.readVarIntArray(16);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeUtf(arg, 64);
        buf.writeVarIntArray(ints);
    }

    public int intArg(int i, int def) {
        return i < ints.length ? ints[i] : def;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) MenuActions.handle(player, this);
    }
}
