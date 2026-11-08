package com.eternalrealm.job.forge;

import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillTree;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Optional;

/**
 * Job selection and skill learning, from the journal ({@code job.choose}, {@code skill.invest}) or by command
 * ({@code /er job <job>}, {@code /er skill <id>}); plus {@code /er admin gold <player> <amount>}.
 */
public final class JobActions {
    private static final long MAX_ADMIN_GOLD = 1_000_000_000L;

    private JobActions() {
    }

    public static void register() {
        UiActions.register("job.choose", (player, context, args) -> {
            String id = UiArgs.id(args, "job");
            JobType job = JobType.byId(id).filter(JobType::isChosen)
                    .orElseThrow(() -> new UiArgs.InvalidArgumentException("unknown job " + id));
            chooseJob(player, context, job);
        });
        UiActions.register("skill.invest", JobActions::investFromUi);
    }

    private static void investFromUi(ServerPlayer player, ServerContext context, JsonObject args) {
        String id = UiArgs.id(args, "skill");
        if (Jobs.skill(id).isEmpty()) {
            throw new UiArgs.InvalidArgumentException("unknown skill " + id);
        }
        invest(player, context, id);
    }

    public static boolean chooseJob(ServerPlayer player, ServerContext context, JobType job) {
        PlayerDataService.JobChange change = context.playerData().chooseJob(player.getUUID(), job,
                context.game().jobRules());
        Component name = Component.translatable(job.translationKey());
        switch (change.result()) {
            case OK -> {
                player.displayClientMessage(Component.translatable("message.eternalrealm.job.changed", name)
                        .withStyle(ChatFormatting.GOLD), false);
                if (change.cost() > 0) {
                    player.displayClientMessage(Component.translatable("message.eternalrealm.job.paid", change.cost())
                            .withStyle(ChatFormatting.YELLOW), false);
                }
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
                if (change.firstChoice()) {
                    giveStarterKit(player, job);
                }
                player.refreshDisplayName();
                player.refreshTabListName();
                return true;
            }
            case SAME_JOB -> player.displayClientMessage(Component.translatable("message.eternalrealm.job.same", name), true);
            case NOT_ENOUGH_GOLD -> player.displayClientMessage(Component.translatable(
                    "message.eternalrealm.job.not_enough_gold", change.cost()).withStyle(ChatFormatting.RED), true);
            case NOT_READY -> {
                // data still loading: nothing to say
            }
        }
        return false;
    }

    public static boolean invest(ServerPlayer player, ServerContext context, String skillId) {
        Optional<SkillTree.InvestResult> result = context.playerData().investSkill(player.getUUID(), skillId);
        if (result.isEmpty()) {
            return false;
        }
        if (result.get() == SkillTree.InvestResult.OK) {
            Jobs.skill(skillId).ifPresent(skill -> player.displayClientMessage(Component.translatable(
                    "message.eternalrealm.skill.learned", Component.translatable(skill.translationKey())), true));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.2F);
            return true;
        }
        player.displayClientMessage(Component.translatable(result.get().translationKey()).withStyle(ChatFormatting.RED),
                true);
        return false;
    }

    /** The first job choice hands out the job's weapon. */
    static void giveStarterKit(ServerPlayer player, JobType job) {
        WeaponType type = Jobs.get(job).starterWeapon();
        ItemStack weapon = com.eternalrealm.item.forge.EquipmentStacks.create(com.eternalrealm.item.EquipmentData.of(
                com.eternalrealm.item.EquipmentBase.weapon(type, com.eternalrealm.item.ItemTier.IRON),
                com.eternalrealm.item.ItemRarity.COMMON, java.util.List.of()));
        if (!player.getInventory().add(weapon)) {
            player.drop(weapon, false);
        }
        player.displayClientMessage(Component.translatable("message.eternalrealm.job.starter_kit", weapon.getHoverName())
                .withStyle(ChatFormatting.AQUA), false);
    }

    // ------------------------------------------------------------------ commands

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        String[] jobIds = Arrays.stream(JobType.values()).filter(JobType::isChosen).map(JobType::id).toArray(String[]::new);
        dispatcher.register(Commands.literal("eternalrealm")
                .then(Commands.literal("job")
                        .then(Commands.argument("job", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(jobIds, builder))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "job");
                                    Optional<JobType> job = JobType.byId(id).filter(JobType::isChosen);
                                    Optional<ServerContext> context = ServerContext.current();
                                    if (job.isEmpty() || context.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.translatable("commands.eternalrealm.job.unknown", id));
                                        return 0;
                                    }
                                    return chooseJob(player, context.get(), job.get()) ? 1 : 0;
                                })))
                .then(Commands.literal("skill")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Jobs.allSkills().keySet(), builder))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    Optional<ServerContext> context = ServerContext.current();
                                    if (context.isEmpty()) {
                                        return 0;
                                    }
                                    return invest(player, context.get(), StringArgumentType.getString(ctx, "skill")) ? 1 : 0;
                                })))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("gold")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1, MAX_ADMIN_GOLD))
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                                    long added = ServerContext.current()
                                                            .flatMap(c -> c.playerData().grantGold(target.getUUID(), amount))
                                                            .orElse(0L);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable(
                                                            "commands.eternalrealm.gold.granted", added,
                                                            target.getDisplayName()), true);
                                                    return added > 0 ? 1 : 0;
                                                }))))));
    }
}
