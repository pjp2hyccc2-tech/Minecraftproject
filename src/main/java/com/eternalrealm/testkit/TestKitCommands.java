package com.eternalrealm.testkit;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateStatus;
import com.eternalrealm.estate.forge.EstateService;
import com.eternalrealm.estate.forge.Showcases;
import com.eternalrealm.server.ServerContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The test kit (docs/modules/testkit.md), operators only (permission level 2): {@code /eternalrealm admin test ...}
 * (alias {@code /er}).
 * <ul>
 *     <li>{@code all [<player>]}: both of the following at once.</li>
 *     <li>{@code max [<player>]}: the level to the cap, gold to the limit, every stat and skill point unspent
 *     ({@link com.eternalrealm.player.PlayerDataService#maxOut}).</li>
 *     <li>{@code unlock [<player>]}: every inn room and every guild house opened for touring: the test pass, and the
 *     showcases are built ({@link Showcases}). {@code lock [<player>]} takes the pass back.</li>
 *     <li>{@code tour <kind> <design>}: into a showcase from anywhere ({@code kind} = {@code room} or
 *     {@code guild}).</li>
 *     <li>{@code status}: how far the showcases are built, who holds a pass.</li>
 * </ul>
 * Without a player the command acts on whoever runs it. Everything goes through the services the game itself uses, so
 * it is validated, saved and synced to the client like play.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TestKitCommands {
    private static final int GAMEMASTER = 2;

    private TestKitCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("eternalrealm")
                .then(Commands.literal("admin").requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("test").requires(source -> source.hasPermission(GAMEMASTER))
                                .then(Commands.literal("all")
                                        .executes(ctx -> all(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> all(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("max")
                                        .executes(ctx -> max(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> max(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("unlock")
                                        .executes(ctx -> unlock(ctx.getSource(),
                                                ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> unlock(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("lock")
                                        .executes(ctx -> lock(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> lock(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("tour")
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        Showcases.kinds(), builder))
                                                .then(Commands.argument("design", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                                designsOf(ctx), builder))
                                                        .executes(ctx -> tour(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "kind"),
                                                                StringArgumentType.getString(ctx, "design"))))))
                                .then(Commands.literal("status")
                                        .executes(ctx -> status(ctx.getSource()))))));
    }

    /** The designs of the kind typed before (none while it cannot be read). */
    private static List<String> designsOf(CommandContext<CommandSourceStack> ctx) {
        try {
            return Showcases.designs(StringArgumentType.getString(ctx, "kind"));
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    /** Everything: the character maxed out, every room and guild house opened. */
    static int all(CommandSourceStack source, ServerPlayer target) {
        int maxed = max(source, target);
        return maxed + unlock(source, target);
    }

    /** Level cap, gold limit, every point unspent. */
    static int max(CommandSourceStack source, ServerPlayer target) {
        Optional<ServerContext> context = ServerContext.current();
        if (context.isEmpty()) {
            source.sendFailure(Component.translatable("commands.eternalrealm.unavailable"));
            return 0;
        }
        Optional<CharacterSnapshot> maxed = context.get().playerData().maxOut(target.getUUID());
        if (maxed.isEmpty()) {
            source.sendFailure(Component.translatable("commands.eternalrealm.not_loaded", target.getDisplayName()));
            return 0;
        }
        CharacterSnapshot s = maxed.get();
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.test.maxed", target.getDisplayName(),
                s.level(), String.format("%,d", s.gold()), s.unspentStatPoints(), s.unspentSkillPoints()), true);
        return 1;
    }

    /** The test pass, and every showcase built (or being built). */
    static int unlock(CommandSourceStack source, ServerPlayer target) {
        Showcases.grant(target);
        int started = Showcases.prepareAll();
        int rooms = Showcases.designs(EstateKey.ROOM).size();
        int houses = Showcases.designs(EstateKey.GUILD).size();
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.test.unlocked", target.getDisplayName(),
                rooms, houses, started), true);
        if (!EstateService.available()) {
            source.sendFailure(Component.translatable("commands.eternalrealm.test.estates_unavailable"));
        }
        target.sendSystemMessage(Component.translatable("message.eternalrealm.showcase.unlocked")
                .withStyle(ChatFormatting.GOLD));
        return 1;
    }

    static int lock(CommandSourceStack source, ServerPlayer target) {
        if (!Showcases.revoke(target)) {
            source.sendFailure(Component.translatable("commands.eternalrealm.test.not_unlocked",
                    target.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.test.locked", target.getDisplayName()),
                true);
        return 1;
    }

    static int tour(CommandSourceStack source, String kind, String design) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!Showcases.known(kind, design)) {
            source.sendFailure(Component.translatable("commands.eternalrealm.test.unknown", kind + " " + design));
            return 0;
        }
        if (!Showcases.mayTour(player)) {
            source.sendFailure(Component.translatable("commands.eternalrealm.test.no_pass"));
            return 0;
        }
        return Showcases.tour(player, kind, design).ok() ? 1 : 0;
    }

    /** One line per kind (built / all, what is being built, what is not started), then who holds a pass. */
    static int status(CommandSourceStack source) {
        for (String kind : Showcases.kinds()) {
            List<String> designs = Showcases.designs(kind);
            int ready = 0;
            int missing = 0;
            MutableComponent building = Component.empty();
            for (String design : designs) {
                EstateStatus status = Showcases.status(kind, design);
                switch (status.phase()) {
                    case READY, REBUILDING -> ready++;
                    case NONE -> missing++;
                    case BUILDING -> {
                        if (!building.getSiblings().isEmpty()) {
                            building.append(", ");
                        }
                        building.append(Showcases.name(kind, design)).append(" " + status.percent() + "%");
                    }
                }
            }
            int built = ready;
            int notStarted = missing;
            Component kindName = Component.translatable("commands.eternalrealm.test.kind." + kind);
            source.sendSuccess(() -> Component.translatable("commands.eternalrealm.test.status", kindName, built,
                    designs.size(), notStarted), false);
            if (!building.getSiblings().isEmpty()) {
                source.sendSuccess(() -> Component.translatable("commands.eternalrealm.test.status.building", building),
                        false);
            }
        }
        List<String> holders = new ArrayList<>();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (Showcases.hasPass(player)) {
                holders.add(player.getGameProfile().getName());
            }
        }
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.test.status.passes",
                holders.isEmpty() ? "-" : String.join(", ", holders)), false);
        return 1;
    }
}
