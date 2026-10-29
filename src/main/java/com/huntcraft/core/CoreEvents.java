package com.huntcraft.core;

import com.huntcraft.HuntCraft;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.registry.HCEffects;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Core gameplay events: dodge, i-frames, status locks, starter kit, admin commands, server actions. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CoreEvents {
    public static final float DODGE_COST = 22.0F;

    private CoreEvents() {
    }

    /** Called once from the mod constructor. */
    public static void registerActions() {
        HCNetwork.registerServerAction("core/dodge", CoreEvents::handleDodge);
        HCNetwork.registerServerAction("smith/forge", (p, t) -> SmithingRegistry.tryForge(p, t.getString("id")));
        HCNetwork.registerServerAction("smith/levelup", (p, t) -> SmithingRegistry.tryLevelUp(p, t.getInt("slot")));
        HCNetwork.registerServerAction("shop/buy", (p, t) -> ShopRegistry.buy(p, t.getString("id"), Math.max(1, Math.min(64, t.getInt("count")))));
        HCNetwork.registerServerAction("shop/sell", (p, t) -> ShopRegistry.sell(p, t.getInt("slot"), Math.max(1, t.getInt("count"))));
        HuntHooks.addStarterKit(p -> {
            give(p, new ItemStack(HCItems.GUILD_PASS.get()));
            give(p, new ItemStack(HCItems.HUNTER_NOTES.get()));
            give(p, new ItemStack(HCItems.POTION.get(), 10));
            give(p, new ItemStack(HCItems.WELL_DONE_STEAK.get(), 5));
            give(p, new ItemStack(HCItems.BBQ_SPIT.get()));
            give(p, new ItemStack(HCItems.RAW_MEAT.get(), 5));
            give(p, new ItemStack(HCItems.PAINTBALL.get(), 5));
            HunterData.get(p).addZenny(3000);
        });
    }

    public static void give(Player p, ItemStack st) {
        if (!p.getInventory().add(st)) p.drop(st, false);
    }

    // ------------------------------------------------------------------ dodge
    private static void handleDodge(ServerPlayer p, CompoundTag tag) {
        HunterData d = HunterData.get(p);
        if (d.dodgeCooldown > 0 || isLocked(p)) return;
        float cost = DODGE_COST * (1.0F - 0.1F * HunterSkills.level(p, SkillType.CONSTITUTION));
        if (d.stamina < cost * 0.5F && !p.isCreative()) return;
        if (!p.isCreative()) d.consumeStamina(cost);
        d.iframes = 6 + 2 * HunterSkills.level(p, SkillType.EVADE_WINDOW);
        d.dodgeTicks = 10;
        d.dodgeCooldown = 11;
        d.dodgeDir = tag.getInt("dir");
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HCSounds.DODGE.get(), SoundSource.PLAYERS, 0.7F, 0.9F + p.getRandom().nextFloat() * 0.2F);
        // rolling puts out fireblight
        if (p.hasEffect(HCEffects.FIREBLIGHT.get())) {
            if (++d.fireblightRolls >= 2) {
                p.removeEffect(HCEffects.FIREBLIGHT.get());
                d.fireblightRolls = 0;
            }
        }
        p.clearFire();
        CompoundTag anim = new CompoundTag();
        anim.putInt("id", p.getId());
        anim.putInt("dir", d.dodgeDir);
        HCNetwork.sendToTracking(p, "core/dodge_anim", anim);
    }

    public static boolean isLocked(Player p) {
        for (MobEffect e : new MobEffect[]{HCEffects.PARALYSIS.get(), HCEffects.SLEEP.get(), HCEffects.STUN.get(), HCEffects.ROAR.get()}) {
            if (p.hasEffect(e)) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player p && !p.level().isClientSide) {
            HunterData d = HunterData.get(p);
            // i-frames: dodge through monster attacks (not void / starvation)
            if (d.isInvulnerable() && (event.getSource().getEntity() instanceof HuntMonster || event.getSource().getDirectEntity() != null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isLocked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (isLocked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player p = event.player;
        // no hunger in hunt dimensions (stamina replaces it)
        if (HCDimensions.isHuntDimension(p.level()) && p.tickCount % 40 == 0) {
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(Math.max(p.getFoodData().getSaturationLevel(), 5.0F));
        }
        if (isLocked(p)) {
            p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
            p.hurtMarked = true;
        }
        if (!p.hasEffect(HCEffects.FIREBLIGHT.get())) HunterData.get(p).fireblightRolls = 0;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        HunterData d = HunterData.get(sp);
        if (!d.starterKitReceived) {
            d.starterKitReceived = true;
            HuntHooks.giveStarterKit(sp);
            sp.sendSystemMessage(Component.translatable("message.huntcraft.welcome"));
            d.markDirty();
        }
    }

    // ------------------------------------------------------------------ admin commands
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("huntcraft").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").then(Commands.argument("monster", StringArgumentType.word())
                        .executes(ctx -> spawnCmd(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "monster"), Rank.LOW))
                        .then(Commands.literal("high")
                                .executes(ctx -> spawnCmd(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "monster"), Rank.HIGH)))))
                .then(Commands.literal("zenny").then(Commands.argument("amount", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            HunterData.get(p).addZenny(IntegerArgumentType.getInteger(ctx, "amount"));
                            return 1;
                        })))
                .then(Commands.literal("hr").then(Commands.argument("rank", IntegerArgumentType.integer(1, 999))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            HunterData d = HunterData.get(p);
                            d.hunterRank = IntegerArgumentType.getInteger(ctx, "rank");
                            d.markDirty();
                            return 1;
                        })))
                .then(Commands.literal("village").executes(ctx -> {
                    HuntHooks.toVillage.accept(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("monsters").executes(ctx -> {
                    StringBuilder sb = new StringBuilder();
                    MonsterRegistry.all().forEach(e -> sb.append(e.id()).append(' '));
                    ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                    return 1;
                })));
    }

    private static int spawnCmd(ServerPlayer p, String id, Rank rank) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(p.getX() - Mth.sin(yaw) * 10, p.getY(), p.getZ() + Mth.cos(yaw) * 10);
        HuntMonster m = MonsterRegistry.spawn(p.serverLevel(), id, pos, rank, null);
        if (m == null) {
            p.sendSystemMessage(Component.literal("Unknown monster: " + id));
            return 0;
        }
        return 1;
    }
}
