package com.sounan.game;

import com.sounan.Sounan;
import com.sounan.command.SounanCommand;
import com.sounan.debug.SelfTest;
import com.sounan.item.GameItem;
import com.sounan.registry.ModDimensions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus events on the logical server. */
@Mod.EventBusSubscriber(modid = Sounan.MODID)
public final class GameEvents {
    private GameEvents() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        GameManager.serverTick(event.getServer());
        SelfTest.tick(event.getServer());
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) GameManager.onLogin(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) GameManager.onLogout(p);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        GameData data = GameData.get(p.server);
        if (data.started && data.ending == 0 && p.getPersistentData().contains("sounan_return")) GameManager.toCheckpoint(p, data);
    }

    /** Nothing on the mountain hurts you the ordinary way — they take you instead. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!ModDimensions.isMountain(p.level())) return;
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) || event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!ModDimensions.isMountain(p.level())) return;
        if (event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        event.setCanceled(true);
        p.setHealth(p.getMaxHealth());
        GameManager.loopBack(p, GameManager.state(p));
    }

    /** The flashlight and the phone never leave your hands. */
    @SubscribeEvent
    public static void toss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (!(event.getPlayer() instanceof ServerPlayer p) || !ModDimensions.isMountain(p.level())) return;
        GameItem.Kind k = GameItem.kindOf(stack);
        if (k != null && k != GameItem.Kind.MEMO) {
            event.setCanceled(true);
            p.getInventory().add(stack.copy());
        }
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && ModDimensions.isMountain(p.level()) && !p.isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        SounanCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        SelfTest.onStarted(event.getServer());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        Director.clearAll();
        GameManager.onServerStopping(event.getServer());
    }
}
