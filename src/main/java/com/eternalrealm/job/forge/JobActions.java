package com.eternalrealm.job.forge;

import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
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
 * Job selection, skill learning, the skill loadout and the paid skill reset, from the journal ({@code job.choose},
 * {@code skill.invest}, {@code skill.slot}, {@code skill.reset}) or by command ({@code /er job <job>},
 * {@code /er skill <id>}); plus {@code /er admin gold <player> <amount>}. Every request is validated by the job
 * module's rules on the server.
 */
public final class JobActions {
    private static final long MAX_ADMIN_GOLD = 1_000_000_000L;
    /** The skill tree can be reset within this distance of the job master. */
    private static final double JOB_MASTER_RANGE = 8.0;

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
        UiActions.register("skill.slot", (player, context, args) -> {
            int slot = (int) UiArgs.number(args, "slot", 1, SkillTree.LOADOUT_SLOTS);
            String skill = UiArgs.string(args, "skill", 64);
            if (!skill.isEmpty() && Jobs.skill(skill).isEmpty()) {
                throw new UiArgs.InvalidArgumentException("unknown skill " + skill);
            }
            setSlot(player, context, slot, skill);
        });
        UiActions.register("skill.reset", (player, context, args) -> resetSkills(player, context));
        UiActions.register("outfit.set", (player, context, args) -> outfit(player, UiArgs.flag(args, "tier")));
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
                com.eternalrealm.item.forge.OutfitStyles.refresh(player);
                warnAboutWeapons(player, job);
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
        java.util.List<String> before = context.playerData().snapshot(player.getUUID())
                .map(com.eternalrealm.character.CharacterSnapshot::skillLoadout).orElse(java.util.List.of());
        Optional<SkillTree.InvestResult> result = context.playerData().investSkill(player.getUUID(), skillId);
        if (result.isEmpty()) {
            return false;
        }
        if (result.get() == SkillTree.InvestResult.OK) {
            SkillDefinition skill = Jobs.skill(skillId).orElseThrow();
            com.eternalrealm.character.CharacterSnapshot snapshot = context.playerData().snapshot(player.getUUID())
                    .orElseThrow();
            int level = SkillTree.levelOf(skill, snapshot.level(), snapshot.skillInvestments());
            Component name = Component.translatable(skill.translationKey());
            player.displayClientMessage(level == 1
                    ? Component.translatable("message.eternalrealm.skill.acquired", name).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("message.eternalrealm.skill.learned", name, level), true);
            if (!before.equals(snapshot.skillLoadout())) {
                int slot = SkillTree.effectiveLoadout(JobType.fromStored(snapshot.jobId()), snapshot.level(),
                        snapshot.skillInvestments(), snapshot.skillLoadout()).indexOf(skillId) + 1;
                if (slot > 0) {
                    player.sendSystemMessage(Component.translatable("message.eternalrealm.skill.auto_slot", name, slot)
                            .withStyle(ChatFormatting.AQUA));
                }
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, level == 1 ? 1.0F : 1.2F);
            return true;
        }
        player.displayClientMessage(Component.translatable(result.get().translationKey()).withStyle(ChatFormatting.RED),
                true);
        return false;
    }

    /** Puts a learned active into a loadout slot ({@code ""} clears it). */
    public static boolean setSlot(ServerPlayer player, ServerContext context, int slot, String skillId) {
        Optional<SkillTree.LoadoutResult> result = context.playerData().setSkillSlot(player.getUUID(), slot, skillId);
        if (result.isEmpty()) {
            return false;
        }
        if (result.get() != SkillTree.LoadoutResult.OK) {
            player.displayClientMessage(Component.translatable(result.get().translationKey())
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.eternalrealm.sound.ERSounds.UI_CLICK.get(),
                SoundSource.PLAYERS, 0.4F, 1.4F);
        return true;
    }

    /** Refunds every skill point for gold, only next to the job master. */
    public static boolean resetSkills(ServerPlayer player, ServerContext context) {
        if (!nearJobMaster(player)) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.skill.reset_job_master")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        com.eternalrealm.player.PlayerDataService.SkillReset reset = context.playerData().resetSkills(player.getUUID(),
                context.game().jobRules());
        switch (reset.result()) {
            case OK -> {
                player.displayClientMessage(Component.translatable("message.eternalrealm.skill.reset_done",
                        reset.cost()).withStyle(ChatFormatting.GOLD), false);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 0.7F);
                return true;
            }
            case NOTHING_TO_RESET -> player.displayClientMessage(Component.translatable(
                    "message.eternalrealm.skill.reset_nothing"), true);
            case NOT_ENOUGH_GOLD -> player.displayClientMessage(Component.translatable(
                    "message.eternalrealm.skill.reset_gold", reset.cost()).withStyle(ChatFormatting.RED), true);
            case NOT_READY -> {
                // data still loading: nothing to say
            }
        }
        return false;
    }

    private static boolean nearJobMaster(ServerPlayer player) {
        return !player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(JOB_MASTER_RANGE),
                mob -> com.eternalrealm.npc.forge.Npcs.roleOf(mob)
                        .filter(role -> role == com.eternalrealm.npc.forge.NpcRole.JOB_MASTER).isPresent()).isEmpty();
    }

    /** After a job change: weapons the new job cannot use stay where they are, but the player is told. */
    static void warnAboutWeapons(ServerPlayer player, JobType job) {
        WeaponType held = heldWeapon(player);
        if (held != null && !Jobs.canUse(job, held)) {
            player.sendSystemMessage(Component.translatable("message.eternalrealm.job.weapon_unusable",
                    Component.translatable(held.translationKey()), Component.translatable(job.translationKey()))
                    .withStyle(ChatFormatting.YELLOW));
        }
        java.util.List<Component> names = new java.util.ArrayList<>();
        for (WeaponType type : Jobs.get(job).weapons()) {
            names.add(Component.translatable(type.translationKey()));
        }
        net.minecraft.network.chat.MutableComponent list = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                list.append(Component.translatable("tooltip.eternalrealm.equipment.job_separator"));
            }
            list.append(names.get(i));
        }
        player.sendSystemMessage(Component.translatable("message.eternalrealm.job.weapons", list)
                .withStyle(ChatFormatting.GRAY));
    }

    private static WeaponType heldWeapon(ServerPlayer player) {
        com.eternalrealm.combat.WeaponProfile profile =
                com.eternalrealm.combat.forge.WeaponItem.profileOf(player.getMainHandItem());
        return profile == null ? null : profile.type();
    }

    /** Small health potions in the starter kit: health does not come back by itself (player.forge.Vitals). */
    public static final int STARTER_POTIONS = 10;

    /** The first job choice hands out the job's weapon and a stack of small health potions. */
    static void giveStarterKit(ServerPlayer player, JobType job) {
        WeaponType type = Jobs.get(job).starterWeapon();
        ItemStack weapon = com.eternalrealm.item.forge.EquipmentStacks.create(com.eternalrealm.item.EquipmentData.of(
                com.eternalrealm.item.EquipmentBase.weapon(type, com.eternalrealm.item.ItemTier.IRON),
                com.eternalrealm.item.ItemRarity.COMMON, java.util.List.of()));
        if (!player.getInventory().add(weapon)) {
            player.drop(weapon, false);
        }
        com.eternalrealm.item.forge.MainHand.ensureWeapon(player); // into the main hand (slot 0) if it holds none
        player.displayClientMessage(Component.translatable("message.eternalrealm.job.starter_kit", weapon.getHoverName())
                .withStyle(ChatFormatting.AQUA), false);
        ItemStack potions = new ItemStack(com.eternalrealm.item.forge.ERGearItems.consumable(
                com.eternalrealm.item.Consumable.HEALTH_POTION_SMALL), STARTER_POTIONS);
        com.eternalrealm.item.forge.Bags.give(player, potions); // into the bag (never the weapon slot), in the feed
        player.displayClientMessage(Component.translatable("message.eternalrealm.job.starter_potions", STARTER_POTIONS)
                .withStyle(ChatFormatting.AQUA), false);
    }

    /** Job outfit or tier look for the worn armor (cosmetic only). */
    static int outfit(ServerPlayer player, boolean tierLook) {
        com.eternalrealm.item.forge.OutfitStyles.setPrefersTierLook(player, tierLook);
        player.displayClientMessage(Component.translatable(tierLook ? "message.eternalrealm.outfit.tier"
                : "message.eternalrealm.outfit.job"), true);
        return 1;
    }

    // ------------------------------------------------------------------ commands

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        String[] jobIds = Arrays.stream(JobType.values()).filter(JobType::isChosen).map(JobType::id).toArray(String[]::new);
        dispatcher.register(Commands.literal("eternalrealm")
                .then(Commands.literal("setup").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("town").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int placed = com.eternalrealm.npc.forge.TownSetup.placeAround(player.serverLevel(),
                                    player.blockPosition());
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "message.eternalrealm.setup.town", placed), true);
                            return placed;
                        })))
                .then(Commands.literal("outfit")
                        .then(Commands.literal("job").executes(ctx -> outfit(ctx.getSource().getPlayerOrException(), false)))
                        .then(Commands.literal("tier").executes(ctx -> outfit(ctx.getSource().getPlayerOrException(), true))))
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
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return ServerContext.current().map(context -> resetSkills(player, context) ? 1 : 0)
                                    .orElse(0);
                        }))
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
