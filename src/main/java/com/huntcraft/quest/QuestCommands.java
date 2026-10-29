package com.huntcraft.quest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** /huntcraft quest list | start &lt;id&gt; | complete | fail | abandon | board | info (op level 2). */
public final class QuestCommands {
    private QuestCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("huntcraft").requires(s -> s.hasPermission(2))
                .then(Commands.literal("quest")
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("start").then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Quests.ids(), b))
                                .executes(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("complete").executes(ctx -> {
                            boolean ok = QuestManager.forceComplete(ctx.getSource().getPlayerOrException());
                            return feedback(ctx.getSource(), ok, "complete");
                        }))
                        .then(Commands.literal("fail").executes(ctx -> {
                            boolean ok = QuestManager.forceFail(ctx.getSource().getPlayerOrException());
                            return feedback(ctx.getSource(), ok, "fail");
                        }))
                        .then(Commands.literal("abandon").executes(ctx -> {
                            QuestManager.abandon(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("board").executes(ctx -> {
                            QuestBoardService.open(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))));
    }

    private static int feedback(CommandSourceStack src, boolean ok, String what) {
        if (ok) src.sendSuccess(() -> Component.translatable("command.huntcraft.quest." + what), true);
        else src.sendFailure(Component.translatable("command.huntcraft.quest.none"));
        return ok ? 1 : 0;
    }

    private static int list(CommandSourceStack src) {
        for (QuestDef q : Quests.all()) {
            MutableComponent line = Component.literal((q.isHighRank() ? "[HR] " : "[LR] ") + "★" + q.stars + " ")
                    .withStyle(q.urgent ? ChatFormatting.RED : q.isHighRank() ? ChatFormatting.GOLD : ChatFormatting.GREEN)
                    .append(Component.literal(q.id + " ").withStyle(ChatFormatting.GRAY))
                    .append(q.title().copy().withStyle(ChatFormatting.WHITE));
            src.sendSuccess(() -> line, false);
        }
        return Quests.all().size();
    }

    private static int start(CommandSourceStack src, String id) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        QuestDef q = Quests.get(id);
        if (q == null) {
            src.sendFailure(Component.translatable("command.huntcraft.quest.unknown", id));
            return 0;
        }
        QuestInstance cur = QuestManager.instanceOf(p);
        if (cur != null) {
            src.sendFailure(Component.translatable("message.huntcraft.quest.already_on_quest"));
            return 0;
        }
        QuestInstance inst = QuestManager.start(p.server, q, List.of(p));
        return inst != null ? 1 : 0;
    }

    private static int info(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        QuestInstance inst = QuestManager.instanceOf(p);
        if (inst == null) {
            src.sendFailure(Component.translatable("command.huntcraft.quest.none"));
            return 0;
        }
        String s = inst.id + " " + inst.questId + " state=" + inst.state + " slot=" + inst.slot + " camp=" + inst.camp.toShortString()
                + " faints=" + inst.faints + " progress=" + java.util.Arrays.toString(inst.progress) + " targets=" + inst.targets.size()
                + " smalls=" + inst.smalls.size();
        src.sendSuccess(() -> Component.literal(s), false);
        return 1;
    }
}
