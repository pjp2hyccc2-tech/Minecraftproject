package jp.satsujinki.network;

import jp.satsujinki.game.DeathGameManager;
import jp.satsujinki.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: start / stop a death game from the host terminal. */
public final class TerminalActionPacket {
    public static final int START = 0, STOP = 1;
    public final int action;
    public final int count;
    public final BlockPos pos;

    public TerminalActionPacket(int action, int count, BlockPos pos) {
        this.action = action;
        this.count = count;
        this.pos = pos;
    }

    public static void encode(TerminalActionPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.count);
        buf.writeBlockPos(p.pos);
    }

    public static TerminalActionPacket decode(FriendlyByteBuf buf) {
        return new TerminalActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readBlockPos());
    }

    public static void handle(TerminalActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (sp == null) return;
        if (sp.blockPosition().distSqr(p.pos) > 16 * 16) return;
        if (!sp.level().getBlockState(p.pos).is(ModBlocks.HOST_TERMINAL.get())) return;
        if (p.action == START) DeathGameManager.start(sp, p.pos, Math.max(1, Math.min(40, p.count)));
        else if (p.action == STOP) DeathGameManager.stop(sp);
    }
}
