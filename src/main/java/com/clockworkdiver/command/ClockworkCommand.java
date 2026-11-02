package com.clockworkdiver.command;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.ClockType;
import com.clockworkdiver.clock.CommissionGenerator;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.clock.DefectType;
import com.clockworkdiver.customer.CustomerEntity;
import com.clockworkdiver.data.PlayerProgress;
import com.clockworkdiver.data.WorldProgress;
import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.registry.ModEntities;
import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.workshop.Objectives;
import com.clockworkdiver.workshop.WorkshopManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * /clockwork workshop | leave | bench | status — for everyone.
 * /clockwork tutorial | customer | dive | complete | coins | debug — for operators (testing).
 */
public final class ClockworkCommand {
    private ClockworkCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("clockwork")
                .then(Commands.literal("workshop").executes(ctx -> act(ctx, WorkshopManager::enterWorkshop)))
                .then(Commands.literal("leave").executes(ctx -> act(ctx, WorkshopManager::leaveWorkshop)))
                .then(Commands.literal("bench").executes(ctx -> act(ctx, WorkshopManager::goToBench)))
                .then(Commands.literal("abort").executes(ctx -> act(ctx, DiveManager::abort)))
                .then(Commands.literal("status").executes(ClockworkCommand::status))
                .then(Commands.literal("tutorial").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("reset").executes(ClockworkCommand::resetTutorial)))
                .then(Commands.literal("customer").requires(s -> s.hasPermission(2))
                        .executes(ctx -> customer(ctx, null))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(ClockType.values()).map(t -> t.id), b))
                                .executes(ctx -> customer(ctx, StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("dive").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("defect", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(DefectType.values()).map(t -> t.id), b))
                                .executes(ClockworkCommand::dive)))
                .then(Commands.literal("complete").requires(s -> s.hasPermission(2))
                        .executes(ctx -> check(ctx, DiveManager::forceComplete)))
                .then(Commands.literal("coins").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 999))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ItemStack coins = new ItemStack(ModItems.BRASS_COIN.get(), IntegerArgumentType.getInteger(ctx, "amount"));
                                    if (!p.getInventory().add(coins)) p.drop(coins, false);
                                    return 1;
                                })))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("counter").executes(ctx -> check(ctx, WorkshopManager::customerToCounter)))
                        .then(Commands.literal("talk").executes(ClockworkCommand::talk))
                        .then(Commands.literal("findall").executes(ClockworkCommand::findAll))
                        .then(Commands.literal("repairall").executes(ClockworkCommand::repairAll))
                        .then(Commands.literal("wave").executes(ClockworkCommand::wave))));
    }

    private interface PlayerAction {
        void run(ServerPlayer player);
    }

    private interface PlayerCheck {
        boolean run(ServerPlayer player);
    }

    private static int act(CommandContext<CommandSourceStack> ctx, PlayerAction action) throws CommandSyntaxException {
        action.run(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int check(CommandContext<CommandSourceStack> ctx, PlayerCheck action) throws CommandSyntaxException {
        return action.run(ctx.getSource().getPlayerOrException()) ? 1 : 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerProgress pr = WorldProgress.of(p);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.clockworkdiver.status", pr.repairedClocks, pr.reputation,
                pr.wrenchLevel, pr.steamLevel, pr.lensLevel), false);
        return 1;
    }

    private static int resetTutorial(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerProgress pr = WorldProgress.of(p);
        ServerLevel ws = WorkshopManager.workshopLevel(p.server);
        if (ws != null) {
            CustomerEntity c = WorkshopManager.findCustomer(ws, pr);
            if (c != null) c.discard();
        }
        pr.tutorialDone = false;
        pr.activeClock = null;
        pr.accepted = false;
        pr.customerId = null;
        pr.hintsSeen = 0;
        pr.nextCustomerTime = 0;
        Objectives.sync(p);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.clockworkdiver.tutorial_reset"), false);
        return 1;
    }

    private static int customer(CommandContext<CommandSourceStack> ctx, String type) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel ws = WorkshopManager.workshopLevel(p.server);
        if (ws == null) return 0;
        PlayerProgress pr = WorldProgress.of(p);
        ClockData forced = null;
        if (type != null) {
            ClockType wanted = ClockType.byId(type);
            for (int i = 0; i < 200; i++) {
                ClockData c = CommissionGenerator.random(ws.random, 99, 0);
                if (c.type == wanted) {
                    forced = c;
                    break;
                }
            }
        }
        if (!pr.tutorialDone && forced == null) forced = CommissionGenerator.tutorial(0);
        WorkshopManager.newCommission(p, pr, ws, forced);
        return 1;
    }

    private static int dive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        DefectType type = DefectType.byId(StringArgumentType.getString(ctx, "defect"));
        return DiveManager.startDebugDive(p, type, false) ? 1 : 0;
    }

    private static int talk(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel ws = WorkshopManager.workshopLevel(p.server);
        if (ws == null) return 0;
        CustomerEntity c = WorkshopManager.findCustomer(ws, WorldProgress.of(p));
        if (c == null) return 0;
        WorkshopManager.onCustomerInteract(p, c);
        return 1;
    }

    private static int findAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerProgress pr = WorldProgress.of(p);
        if (pr.activeClock == null) return 0;
        for (Defect d : pr.activeClock.defects) d.found = true;
        WorkshopManager.syncClockItem(p, pr.activeClock);
        Objectives.sync(p);
        return 1;
    }

    private static int repairAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerProgress pr = WorldProgress.of(p);
        if (pr.activeClock == null) return 0;
        for (Defect d : pr.activeClock.defects) {
            d.found = true;
            d.repaired = true;
        }
        WorkshopManager.syncClockItem(p, pr.activeClock);
        Objectives.sync(p);
        return 1;
    }

    private static int wave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        double baseYaw = Math.toRadians(p.getYRot());
        for (int i = 0; i < 4; i++) {
            EntityType<? extends Mob> type = i % 3 == 0 ? ModEntities.COG_GREMLIN.get()
                    : i % 3 == 1 ? ModEntities.RUST_MITE.get() : ModEntities.DUST_MOTH.get();
            Mob mob = type.create(level);
            if (mob == null) continue;
            double a = baseYaw + (i - 1.5) * 0.45;
            mob.moveTo(p.getX() - Math.sin(a) * 4.5, p.getY() + (i % 3 == 2 ? 2.5 : 0.1), p.getZ() + Math.cos(a) * 4.5,
                    p.getYRot() + 180.0f, 0.0f);
            mob.setTarget(p);
            level.addFreshEntity(mob);
        }
        return 1;
    }
}
