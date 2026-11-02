package com.clockworkdiver.command;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.ClockType;
import com.clockworkdiver.clock.CommissionGenerator;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.clock.DefectType;
import com.clockworkdiver.clock.DiveMods;
import com.clockworkdiver.item.KeepsakeItem;
import com.clockworkdiver.story.StoryManager;
import com.clockworkdiver.story.StoryQuest;
import com.clockworkdiver.story.StoryQuests;
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
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(DefectType.values())
                                        .filter(DefectType::divable).map(t -> t.id), b))
                                .executes(ctx -> dive(ctx, ""))
                                .then(Commands.argument("options", StringArgumentType.greedyString())
                                        .executes(ctx -> dive(ctx, StringArgumentType.getString(ctx, "options"))))))
                .then(Commands.literal("story")
                        .executes(ClockworkCommand::storyStatus)
                        .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("quest", IntegerArgumentType.integer(1, StoryQuests.count() + 1))
                                        .executes(ctx -> storySet(ctx, IntegerArgumentType.getInteger(ctx, "quest")))))
                        .then(Commands.literal("skip").requires(s -> s.hasPermission(2)).executes(ClockworkCommand::storySkip))
                        .then(Commands.literal("keepsakes").requires(s -> s.hasPermission(2)).executes(ClockworkCommand::keepsakes)))
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
                        .then(Commands.literal("wave").executes(ClockworkCommand::wave))
                        .then(Commands.literal("fragment").executes(ctx -> check(ctx, DiveManager::debugFragment)))
                        .then(Commands.literal("hold").executes(ctx -> {
                            // Keeps the next customer away for half an hour (CI tour).
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            WorldProgress.of(p).nextCustomerTime = p.level().getGameTime() + 20L * 60 * 30;
                            return 1;
                        }))));
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

    /** {@code /clockwork dive <defect> [timed fast offbeat dark swarm elite novents precision frag3 shadow shade queen]} */
    private static int dive(CommandContext<CommandSourceStack> ctx, String options) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        DefectType type = DefectType.byId(StringArgumentType.getString(ctx, "defect"));
        int mods = 0, frags = 0, boss = Defect.BOSS_NORMAL;
        for (String opt : options.trim().split("\\s+")) {
            if (opt.isEmpty()) continue;
            if (opt.startsWith("frag")) {
                try {
                    frags = Math.max(1, Math.min(6, Integer.parseInt(opt.substring(4))));
                } catch (NumberFormatException e) {
                    frags = 2;
                }
            } else if (opt.equals("shadow")) {
                boss = Defect.BOSS_SHADOW;
            } else if (opt.equals("shade")) {
                boss = Defect.BOSS_SHADE;
            } else if (opt.equals("queen")) {
                boss = Defect.BOSS_QUEEN;
            } else {
                mods |= DiveMods.byName(opt);
            }
        }
        return DiveManager.startDebugDive(p, type, false, mods, frags, boss) ? 1 : 0;
    }

    private static int storyStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerProgress pr = WorldProgress.of(p);
        if (StoryManager.finished(pr)) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.clockworkdiver.story.done"), false);
        } else {
            StoryQuest q = StoryQuests.get(pr.storyIndex);
            ctx.getSource().sendSuccess(() -> Component.translatable("command.clockworkdiver.story.status", q.number,
                    Component.translatable(q.titleKey()), pr.storyIndex, StoryQuests.count()), false);
        }
        return 1;
    }

    /** Jumps the story to the given quest (1-based; count + 1 = finished) and sends its client right away. */
    private static int storySet(CommandContext<CommandSourceStack> ctx, int quest) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerProgress pr = WorldProgress.of(p);
        ServerLevel ws = WorkshopManager.workshopLevel(p.server);
        if (ws != null) {
            CustomerEntity c = WorkshopManager.findCustomer(ws, pr);
            if (c != null) c.discard();
        }
        pr.tutorialDone = true;
        pr.storyIndex = Math.max(0, Math.min(StoryQuests.count(), quest - 1));
        pr.keepsakes = pr.storyIndex >= 31 ? -1 : (1 << pr.storyIndex) - 1;
        pr.activeClock = null;
        pr.accepted = false;
        pr.customerId = null;
        pr.nextCustomerTime = ws != null ? ws.getGameTime() + 20 : 0;
        StoryManager.sync(p);
        Objectives.sync(p);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.clockworkdiver.story.set", quest), false);
        return 1;
    }

    /** Finishes the current story quest as if it had been repaired and delivered. */
    private static int storySkip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerProgress pr = WorldProgress.of(p);
        if (!StoryManager.active(pr)) return 0;
        return storySet(ctx, pr.storyIndex + 2);
    }

    private static int keepsakes(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        for (StoryQuest q : StoryQuests.all()) {
            ItemStack stack = KeepsakeItem.create(q);
            if (!p.getInventory().add(stack)) p.drop(stack, false);
        }
        return 1;
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
