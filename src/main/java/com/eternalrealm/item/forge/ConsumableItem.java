package com.eternalrealm.item.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.ConsumableEffect;
import com.eternalrealm.server.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * A consumable of the item module. Potions and tonics are drunk (short use), the return scroll is channelled
 * (moving or taking damage cancels it), the oblivion elixir works instantly. The server checks the level requirement
 * and the shared cooldown group; the item is consumed only when the effect happened.
 */
public final class ConsumableItem extends Item {
    private static final int DRINK_TICKS = 16;
    /** Squared horizontal speed above which a channel counts as moving. */
    private static final double MOVING_SPEED_SQR = 0.0016;

    /** Where the return scroll sends a player; the world layer installs the hub of アストリア. */
    private static volatile Function<ServerPlayer, Boolean> homeTeleport = ConsumableItem::teleportToRespawn;
    /**
     * Revives a fallen player with a 永久花の花びら: {@code (helper, target)} → whether it happened. The revival system
     * installs it; until then nobody is ever fallen.
     */
    private static volatile java.util.function.BiPredicate<ServerPlayer, ServerPlayer> reviveHook = (helper, target) -> false;

    private final Consumable consumable;

    public ConsumableItem(Consumable consumable, Properties properties) {
        super(properties);
        this.consumable = consumable;
    }

    public Consumable consumable() {
        return consumable;
    }

    /** @param teleport moves the player home and returns whether it worked */
    public static void setHomeTeleport(Function<ServerPlayer, Boolean> teleport) {
        homeTeleport = teleport;
    }

    /** @param hook revives {@code target} (fallen) for {@code helper} and returns whether it happened */
    public static void setReviveHook(java.util.function.BiPredicate<ServerPlayer, ServerPlayer> hook) {
        reviveHook = hook;
    }

    /** A 永久花の花びら used on a fallen ally revives them where they lie. */
    @Override
    public net.minecraft.world.InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                                      InteractionHand hand) {
        if (!(consumable.effect() instanceof ConsumableEffect.Revive) || !(target instanceof Player)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer helper && target instanceof ServerPlayer fallen) {
            if (reviveHook.test(helper, fallen)) {
                if (!helper.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            return net.minecraft.world.InteractionResult.FAIL;
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(player.level().isClientSide());
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        ConsumableEffect effect = consumable.effect();
        if (effect instanceof ConsumableEffect.Heal || effect instanceof ConsumableEffect.RestoreStamina) {
            return UseAnim.DRINK;
        }
        return effect instanceof ConsumableEffect.ReturnHome ? UseAnim.TOOT_HORN : UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        ConsumableEffect effect = consumable.effect();
        if (effect instanceof ConsumableEffect.Heal || effect instanceof ConsumableEffect.RestoreStamina) {
            return DRINK_TICKS;
        }
        return effect instanceof ConsumableEffect.ReturnHome home ? home.channelTicks() : 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Consumable.UseCheck check = consumable.check(levelOf(serverPlayer), 0);
            if (check != Consumable.UseCheck.OK) {
                serverPlayer.displayClientMessage(Component.translatable(check.translationKey(),
                        consumable.requiredLevel()).withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        if (getUseDuration(stack) <= 0) {
            if (player instanceof ServerPlayer serverPlayer && apply(serverPlayer)) {
                consume(serverPlayer, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (consumable.effect() instanceof ConsumableEffect.ReturnHome && !level.isClientSide()) {
            Vec3 motion = entity.getDeltaMovement();
            if (motion.x * motion.x + motion.z * motion.z > MOVING_SPEED_SQR) {
                cancelChannel(entity);
            } else if (entity.tickCount % 5 == 0 && level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                        8, 0.4, 0.6, 0.4, 0.2);
            }
        }
    }

    /** The return channel ends without effect (moving or damage). */
    public static void cancelChannel(LivingEntity entity) {
        entity.stopUsingItem();
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.consumable.channel_cancelled")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    /**
     * Quick use from the inventory (number keys, the bag's use button): no hand and no drinking animation. Level and
     * cooldown are checked as for a use from the hand; the return scroll channels for its usual time (moving or taking
     * damage cancels it, see {@link ReturnChannel}). @return whether something happened or started
     */
    public boolean useFromInventory(ServerPlayer player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(this)) {
            player.displayClientMessage(Component.translatable(Consumable.UseCheck.ON_COOLDOWN.translationKey(),
                    consumable.requiredLevel()).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Consumable.UseCheck check = consumable.check(levelOf(player), 0);
        if (check != Consumable.UseCheck.OK) {
            player.displayClientMessage(Component.translatable(check.translationKey(), consumable.requiredLevel())
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (consumable.effect() instanceof ConsumableEffect.ReturnHome home) {
            return ReturnChannel.start(player, this, home.channelTicks());
        }
        boolean done = applyAndConsume(player, stack);
        if (done && (consumable.effect() instanceof ConsumableEffect.Heal
                || consumable.effect() instanceof ConsumableEffect.RestoreStamina)) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK,
                    SoundSource.PLAYERS, 0.6F, 1.1F);
        }
        return done;
    }

    /** Runs the effect and, if it happened, uses up one item of {@code stack} and starts the cooldown. */
    boolean applyAndConsume(ServerPlayer player, ItemStack stack) {
        if (apply(player)) {
            consume(player, stack);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && apply(player)) {
            consume(player, stack);
        }
        return stack;
    }

    private void consume(ServerPlayer player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        boolean fighting = ServerContext.current().map(context -> context.combat().inCombat(player)).orElse(true);
        int cooldown = consumable.cooldownTicks(fighting);
        for (Consumable member : consumable.cooldownGroupMembers()) {
            player.getCooldowns().addCooldown(ERGearItems.consumable(member), cooldown);
        }
    }

    /** Runs the effect on the server. @return whether it happened (only then the item is used up) */
    private boolean apply(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ConsumableEffect effect = consumable.effect();
        if (effect instanceof ConsumableEffect.Heal heal) {
            // VIT makes potions stronger (the only health that comes back by itself is gone: see player.forge.Vitals)
            double bonus = ServerContext.current().flatMap(context -> context.combat().service().get(player.getUUID()))
                    .map(combatant -> combatant.stats().potionBonus()).orElse(0.0);
            player.heal((float) (heal.amount(player.getMaxHealth()) * (1.0 + bonus)));
            level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.6, player.getZ(),
                    4, 0.4, 0.3, 0.4, 0.0);
            return true;
        }
        if (effect instanceof ConsumableEffect.RestoreStamina stamina) {
            ServerContext.current().flatMap(context -> context.combat().service().get(player.getUUID()))
                    .ifPresent(combatant -> combatant.addStamina(stamina.amount(combatant.stats().maxStamina())));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(),
                    6, 0.4, 0.5, 0.4, 0.0);
            return true;
        }
        if (effect instanceof ConsumableEffect.ReturnHome) {
            boolean moved = homeTeleport.apply(player);
            if (moved) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
                player.displayClientMessage(Component.translatable("message.eternalrealm.consumable.returned")
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return moved;
        }
        if (effect instanceof ConsumableEffect.Revive) {
            // used in the air: the petal is for a fallen ally (or for oneself from the revival screen)
            player.displayClientMessage(Component.translatable("message.eternalrealm.revival.petal_hint")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (effect instanceof ConsumableEffect.ResetPoints) {
            boolean done = ServerContext.current().map(context -> context.playerData().resetAllPoints(player.getUUID()))
                    .orElse(false);
            player.displayClientMessage(Component.translatable(done ? "message.eternalrealm.consumable.points_reset"
                    : "message.eternalrealm.consumable.nothing_to_reset").withStyle(done ? ChatFormatting.LIGHT_PURPLE
                    : ChatFormatting.GRAY), true);
            if (done) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
            }
            return done;
        }
        return false;
    }

    private static boolean teleportToRespawn(ServerPlayer player) {
        ServerLevel level = player.server.getLevel(player.getRespawnDimension());
        BlockPos pos = player.getRespawnPosition();
        if (level == null || pos == null) {
            level = player.server.overworld();
            pos = level.getSharedSpawnPos();
        }
        player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
        return true;
    }

    private static int levelOf(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::level).orElse(1);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(consumable.descriptionKey(), consumable.descriptionArgs().toArray())
                .withStyle(ChatFormatting.GRAY));
        if (consumable.requiredLevel() > 1) {
            int viewer = EquipmentStacks.viewerLevel();
            tooltip.add(Component.translatable("tooltip.eternalrealm.equipment.required_level", consumable.requiredLevel())
                    .withStyle(viewer < consumable.requiredLevel() ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        // potions and tonics state their cooldown in the description; items without a cooldown show none
        ConsumableEffect effect = consumable.effect();
        boolean described = effect instanceof ConsumableEffect.Heal || effect instanceof ConsumableEffect.RestoreStamina;
        if (consumable.cooldownTicks() > 0 && !described) {
            tooltip.add(Component.translatable("tooltip.eternalrealm.consumable.cooldown",
                    String.format(Locale.ROOT, "%.0f", consumable.cooldownTicks() / 20.0)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
