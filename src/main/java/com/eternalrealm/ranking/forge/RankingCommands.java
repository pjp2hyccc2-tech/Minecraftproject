package com.eternalrealm.ranking.forge;

import com.eternalrealm.ranking.RankingCategory;
import com.eternalrealm.ranking.RankingEntry;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.server.UiData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** {@code /ranking [category]}, {@code /season} and the journal's {@code ranking.view} request. */
public final class RankingCommands {
    private RankingCommands() {
    }

    public static void register() {
        UiActions.register("ranking.view", (player, context, args) -> {
            String id = UiArgs.id(args, "category");
            RankingCategory category = byId(id)
                    .orElseThrow(() -> new UiArgs.InvalidArgumentException("unknown category " + id));
            UiData.send(player, "ranking", context.game().rankings().view(player.getUUID(), category, 20));
        });
    }

    private static Optional<RankingCategory> byId(String id) {
        return Arrays.stream(RankingCategory.values()).filter(c -> c.id().equals(id)).findFirst();
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        String[] ids = Arrays.stream(RankingCategory.values()).map(RankingCategory::id).toArray(String[]::new);
        dispatcher.register(Commands.literal("ranking")
                .executes(ctx -> show(ctx.getSource().getPlayerOrException(), RankingCategory.OVERALL))
                .then(Commands.argument("category", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ids, builder))
                        .executes(ctx -> byId(StringArgumentType.getString(ctx, "category"))
                                .map(category -> {
                                    try {
                                        return show(ctx.getSource().getPlayerOrException(), category);
                                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                                        return 0;
                                    }
                                }).orElse(0))));
        dispatcher.register(Commands.literal("season").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerContext.current().ifPresent(context -> {
                var season = context.game().rankings().season();
                player.sendSystemMessage(Component.translatable("message.eternalrealm.season.info",
                        season.seasonId().value(), Component.translatable("season_status.eternalrealm." + season.status().id()),
                        season.displayDay(Instant.now()), season.schedule().lengthDays()).withStyle(ChatFormatting.GOLD));
            });
            return 1;
        }));
    }

    private static int show(ServerPlayer player, RankingCategory category) {
        Optional<ServerContext> context = ServerContext.current();
        if (context.isEmpty()) {
            return 0;
        }
        RankingService rankings = context.get().game().rankings();
        player.sendSystemMessage(Component.translatable("message.eternalrealm.ranking.header",
                Component.translatable(category.translationKey())).withStyle(ChatFormatting.GOLD));
        if (category == RankingCategory.GUILD) {
            for (RankingEntry<String> entry : rankings.board().topGuilds(10)) {
                player.sendSystemMessage(line(entry.rank(), entry.name(), entry.score(), false));
            }
            return 1;
        }
        if (category == RankingCategory.BOSS_TIME_ATTACK) {
            player.sendSystemMessage(Component.translatable("message.eternalrealm.ranking.time_attack_hint"));
            return 1;
        }
        List<RankingEntry<java.util.UUID>> top = rankings.board().top(category, 10);
        for (RankingEntry<java.util.UUID> entry : top) {
            player.sendSystemMessage(line(entry.rank(), entry.name(), entry.score(), entry.id().equals(player.getUUID())));
        }
        rankings.board().entryOf(category, player.getUUID())
                .filter(own -> own.rank() > 10)
                .ifPresent(own -> player.sendSystemMessage(line(own.rank(), own.name(), own.score(), true)));
        if (top.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.eternalrealm.ranking.empty"));
        }
        return 1;
    }

    private static Component line(int rank, String name, long score, boolean self) {
        ChatFormatting color = rank == 1 ? ChatFormatting.GOLD : rank == 2 ? ChatFormatting.WHITE
                : rank == 3 ? ChatFormatting.RED : ChatFormatting.GRAY;
        return Component.literal(String.format(java.util.Locale.ROOT, "%3d. ", rank)).withStyle(color)
                .append(Component.literal(name).withStyle(self ? ChatFormatting.AQUA : ChatFormatting.WHITE))
                .append(Component.literal("  " + String.format(java.util.Locale.ROOT, "%,d", score))
                        .withStyle(ChatFormatting.YELLOW));
    }
}
