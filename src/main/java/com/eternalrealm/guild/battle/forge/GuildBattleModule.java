package com.eternalrealm.guild.battle.forge;

import com.eternalrealm.combat.forge.PvpRules;
import com.eternalrealm.estate.forge.EstateService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.BiConsumer;

/**
 * Wiring of the guild battles (docs/modules/guild_battles.md), called once from the mod's constructor: the arenas'
 * access rule, the PvP rule of the arenas, the page's actions, {@code /guildbattle} and the server events.
 */
public final class GuildBattleModule {
    private GuildBattleModule() {
    }

    public static void register() {
        EstateService.registerAccess(GuildBattleService.KIND, GuildBattleService::mayBeIn);
        PvpRules.setArenaRule(GuildBattleService::mayHurt);
        GuildBattleUi.register();
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((ServerStartedEvent event) -> GuildBattleService.start(event.getServer()));
        bus.addListener((ServerStoppedEvent event) -> GuildBattleService.stop());
        bus.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                GuildBattleService.get().filter(s -> s.server == event.getServer()).ifPresent(GuildBattleService::tick);
            }
        });
        bus.addListener(EventPriority.HIGHEST, (LivingDeathEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
                GuildBattleService.get().ifPresent(s -> {
                    if (s.onDeath(player, event.getSource())) {
                        event.setCanceled(true);
                    }
                });
            }
        });
        // before everything else: a fighter who must not be hurt is not hurt by anything
        bus.addListener(EventPriority.HIGHEST, (LivingAttackEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                GuildBattleService.get().ifPresent(s -> {
                    if (s.sheltered(player)) {
                        event.setCanceled(true);
                    }
                });
            }
        });
        bus.addListener(EventPriority.LOWEST, (LivingHurtEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
                GuildBattleService.get().ifPresent(s -> s.onHurt(player, event.getSource()));
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                GuildBattleService.get().ifPresent(s -> s.loggedOut(player));
            }
        });
        bus.addListener(GuildBattleModule::onRegisterCommands);
    }

    /**
     * {@code /guildbattle challenge <tag or name>}, {@code accept [tag]}, {@code decline [tag]}, {@code withdraw},
     * {@code join}, {@code leave} (also {@code /gb}); the chat buttons run these.
     */
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        for (String root : new String[]{"guildbattle", "gb"}) {
            dispatcher.register(Commands.literal(root)
                    .then(Commands.literal("challenge").then(Commands.argument("guild", StringArgumentType.greedyString())
                            .executes(ctx -> run(ctx, (s, p) -> s.challenge(p,
                                    StringArgumentType.getString(ctx, "guild"))))))
                    .then(Commands.literal("accept")
                            .executes(ctx -> run(ctx, (s, p) -> s.accept(p, null)))
                            .then(Commands.argument("guild", StringArgumentType.greedyString())
                                    .executes(ctx -> run(ctx, (s, p) -> s.accept(p,
                                            StringArgumentType.getString(ctx, "guild"))))))
                    .then(Commands.literal("decline")
                            .executes(ctx -> run(ctx, (s, p) -> s.decline(p, null)))
                            .then(Commands.argument("guild", StringArgumentType.greedyString())
                                    .executes(ctx -> run(ctx, (s, p) -> s.decline(p,
                                            StringArgumentType.getString(ctx, "guild"))))))
                    .then(Commands.literal("withdraw").executes(ctx -> run(ctx, GuildBattleService::withdrawChallenge)))
                    .then(Commands.literal("join").executes(ctx -> run(ctx, GuildBattleService::join)))
                    .then(Commands.literal("leave").executes(ctx -> run(ctx, GuildBattleService::leave))));
        }
    }

    private static int run(CommandContext<CommandSourceStack> ctx, BiConsumer<GuildBattleService, ServerPlayer> action)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GuildBattleService.get().ifPresent(service -> {
            action.accept(service, player);
            GuildBattleUi.send(player, service);
        });
        return 1;
    }
}
