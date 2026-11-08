package com.eternalrealm.character.forge;

import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.ExperienceSource;
import com.eternalrealm.character.SetLevelResult;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /eternalrealm} (alias {@code /er}).
 * <ul>
 *     <li>{@code character} – own character (everyone); {@code character <player>} – permission level 2</li>
 *     <li>{@code admin exp add|level set|stats reset|status} – permission level 2 (testing / support)</li>
 *     <li>{@code admin save|backup} – permission level 3</li>
 * </ul>
 * All mutations go through {@link PlayerDataService}, exactly like gameplay, so they are validated and persisted.
 */
public final class CharacterCommands {
    private static final int GAMEMASTER = 2;
    private static final int ADMIN = 3;
    private static final long MAX_ADMIN_EXP = 1_000_000_000L;

    private CharacterCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("eternalrealm")
                .then(Commands.literal("character")
                        .executes(ctx -> showCharacter(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(GAMEMASTER))
                                .executes(ctx -> showCharacter(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("exp")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .then(Commands.argument("amount", LongArgumentType.longArg(1, MAX_ADMIN_EXP))
                                                        .executes(ctx -> addExperience(ctx.getSource(),
                                                                EntityArgument.getPlayers(ctx, "players"),
                                                                LongArgumentType.getLong(ctx, "amount")))))))
                        .then(Commands.literal("level")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, CharacterRules.MAX_LEVEL))
                                                        .executes(ctx -> setLevel(ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                IntegerArgumentType.getInteger(ctx, "level")))))))
                        .then(Commands.literal("stats")
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> resetStats(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("save")
                                .requires(source -> source.hasPermission(ADMIN))
                                .executes(ctx -> saveAll(ctx.getSource())))
                        .then(Commands.literal("backup")
                                .requires(source -> source.hasPermission(ADMIN))
                                .executes(ctx -> backup(ctx.getSource())))));
        dispatcher.register(Commands.literal("er").redirect(root));
    }

    private static Optional<ServerContext> context(CommandSourceStack source) {
        Optional<ServerContext> context = ServerContext.current();
        if (context.isEmpty()) {
            source.sendFailure(Component.translatable("commands.eternalrealm.unavailable"));
        }
        return context;
    }

    private static int showCharacter(CommandSourceStack source, ServerPlayer target) {
        Optional<ServerContext> context = context(source);
        if (context.isEmpty()) {
            return 0;
        }
        Optional<CharacterSnapshot> snapshot = context.get().playerData().snapshot(target.getUUID());
        if (snapshot.isEmpty()) {
            source.sendFailure(Component.translatable("commands.eternalrealm.not_loaded", target.getDisplayName()));
            return 0;
        }
        CharacterSnapshot s = snapshot.get();
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.character.header",
                target.getDisplayName(), s.seasonId().value()), false);
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.character.level",
                s.level(), s.levelCap(), s.experience(), s.experienceToNext()), false);
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.character.stats", statsLine(s)), false);
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.character.points",
                s.unspentStatPoints()), false);
        context.get().combat().service().get(target.getUUID()).ifPresent(combatant -> {
            CombatStats c = combatant.stats();
            source.sendSuccess(() -> Component.translatable("commands.eternalrealm.character.combat",
                    String.format(Locale.ROOT, "%.2f", c.attackMultiplier()),
                    String.format(Locale.ROOT, "%.1f", c.maxHealthBonus()),
                    String.format(Locale.ROOT, "%.1f", c.damageReduction() * 100.0),
                    String.format(Locale.ROOT, "%.1f", (1.0 / c.actionSpeedFactor() - 1.0) * 100.0),
                    String.format(Locale.ROOT, "%.1f", c.critChance() * 100.0),
                    String.format(Locale.ROOT, "%.0f", c.maxStamina())), false);
        });
        return 1;
    }

    private static MutableComponent statsLine(CharacterSnapshot snapshot) {
        MutableComponent line = Component.empty();
        StatType[] types = StatType.values();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                line.append(" / ");
            }
            line.append(Component.translatable(types[i].translationKey()))
                    .append(" " + snapshot.totalStats().get(types[i]));
        }
        return line;
    }

    private static int addExperience(CommandSourceStack source, Collection<ServerPlayer> targets, long amount) {
        Optional<ServerContext> context = context(source);
        if (context.isEmpty()) {
            return 0;
        }
        int granted = 0;
        for (ServerPlayer target : targets) {
            if (context.get().playerData().grantExperience(target.getUUID(), amount, ExperienceSource.ADMIN).isPresent()) {
                granted++;
            }
        }
        int done = granted;
        int skipped = targets.size() - granted;
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.exp.added", amount, done, skipped), true);
        return done;
    }

    private static int setLevel(CommandSourceStack source, ServerPlayer target, int level) {
        Optional<ServerContext> context = context(source);
        if (context.isEmpty()) {
            return 0;
        }
        PlayerDataService data = context.get().playerData();
        SetLevelResult result = data.setLevel(target.getUUID(), level);
        switch (result) {
            case SUCCESS -> {
                source.sendSuccess(() -> Component.translatable("commands.eternalrealm.level.set",
                        target.getDisplayName(), level), true);
                return 1;
            }
            case OUT_OF_RANGE -> source.sendFailure(Component.translatable("commands.eternalrealm.level.out_of_range",
                    data.rules().levelCap()));
            case NOT_READY -> source.sendFailure(Component.translatable("commands.eternalrealm.not_loaded",
                    target.getDisplayName()));
        }
        return 0;
    }

    private static int resetStats(CommandSourceStack source, ServerPlayer target) {
        Optional<ServerContext> context = context(source);
        if (context.isEmpty()) {
            return 0;
        }
        PlayerDataService data = context.get().playerData();
        if (!data.isActive(target.getUUID())) {
            source.sendFailure(Component.translatable("commands.eternalrealm.not_loaded", target.getDisplayName()));
            return 0;
        }
        if (!data.resetStatAllocation(target.getUUID())) {
            source.sendFailure(Component.translatable("commands.eternalrealm.stats.nothing_to_reset", target.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.stats.reset", target.getDisplayName()), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        Optional<ServerContext> context = context(source);
        if (context.isEmpty()) {
            return 0;
        }
        PlayerDataService.Status status = context.get().playerData().status();
        int pendingIo = context.get().ioExecutor().pendingKeys();
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.status",
                status.sessions(), status.loading(), status.active(), status.unsavedInMemory(),
                status.savesInFlight(), pendingIo), false);
        return 1;
    }

    private static int saveAll(CommandSourceStack source) {
        Optional<ServerContext> context = context(source);
        if (context.isEmpty()) {
            return 0;
        }
        int queued = context.get().playerData().saveAllDirty();
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.save.queued", queued), true);
        return queued;
    }

    private static int backup(CommandSourceStack source) {
        Optional<ServerContext> context = context(source);
        if (context.isEmpty()) {
            return 0;
        }
        ServerContext ctx = context.get();
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.backup.started"), true);
        ctx.backups().createSnapshotAsync().whenComplete((path, error) -> ctx.mainThread().execute(() -> {
            if (error != null) {
                source.sendFailure(Component.translatable("commands.eternalrealm.backup.failed", String.valueOf(error.getMessage())));
            } else {
                source.sendSuccess(() -> Component.translatable("commands.eternalrealm.backup.done",
                        path.getFileName().toString()), true);
            }
        }));
        return 1;
    }
}
