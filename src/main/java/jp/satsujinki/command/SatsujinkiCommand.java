package jp.satsujinki.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import jp.satsujinki.game.DeathGameManager;
import jp.satsujinki.game.HostData;
import jp.satsujinki.game.KillStyle;
import jp.satsujinki.game.ScoreManager;
import jp.satsujinki.item.MopItem;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /satsujinki start [count] | stop | points &lt;n&gt; | clean | kit | stats */
public final class SatsujinkiCommand {
    private SatsujinkiCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("satsujinki")
                .then(Commands.literal("start").requires(s -> s.hasPermission(2))
                        .executes(ctx -> start(ctx.getSource(), 8))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 40))
                                .executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("stop").executes(ctx -> {
                    DeathGameManager.stop(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("points").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(-100000000, 100000000))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    int n = IntegerArgumentType.getInteger(ctx, "amount");
                                    ScoreManager.addPoints(p, n);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("satsujinki.cmd.points", n, ScoreManager.points(p)), false);
                                    return 1;
                                })))
                .then(Commands.literal("clean").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    int n = MopItem.clean(p.serverLevel(), p.blockPosition(), 24);
                    ctx.getSource().sendSuccess(() -> Component.translatable("satsujinki.mop.cleaned", n), false);
                    return n;
                }))
                .then(Commands.literal("kit").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    ScoreManager.giveStarterKit(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("stats").executes(ctx -> stats(ctx.getSource()))));
    }

    private static int start(CommandSourceStack src, int count) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        return DeathGameManager.start(p, p.blockPosition(), count) ? 1 : 0;
    }

    private static int stats(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        HostData.Stats s = HostData.get(p.server).of(p.getUUID());
        src.sendSuccess(() -> Component.translatable("satsujinki.cmd.stats", s.points, s.totalKills, s.maxCombo, s.roundsPlayed, s.roundsPerfect)
                .withStyle(ChatFormatting.DARK_RED), false);
        for (KillStyle k : KillStyle.values()) {
            int n = s.styleKills[k.ordinal()];
            src.sendSuccess(() -> Component.literal(" - ").append(Component.translatable(k.langKey())).append(": " + n), false);
        }
        return 1;
    }
}
