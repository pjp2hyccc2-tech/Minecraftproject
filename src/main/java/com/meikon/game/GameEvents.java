package com.meikon.game;

import com.meikon.Meikon;
import com.meikon.command.MeikonCommand;
import com.meikon.debug.SelfTest;
import com.meikon.registry.ModDimensions;
import com.meikon.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
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
@Mod.EventBusSubscriber(modid = Meikon.MODID)
public final class GameEvents {
    private GameEvents() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        GameManager.serverTick(event.getServer());
        ServerLevel level = GameManager.kakuriyo(event.getServer());
        if (level != null) Director.runTasks(level);
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
        if (p.getPersistentData().contains("meikon_return") && GameData.get(p.server).active) {
            GameManager.stateOrCreate(p).everLeftSafeRoom = true;
            GameManager.toSpawn(p);
        }
    }

    /** Nothing hurts you in the mansion — except her. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!ModDimensions.isKakuriyo(p.level()) || !GameData.get(p.server).active) return;
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) || event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!ModDimensions.isKakuriyo(p.level()) || !GameData.get(p.server).active) return;
        if (event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        event.setCanceled(true);
        p.setHealth(p.getMaxHealth());
        GameManager.fakeDeath(p);
    }

    @SubscribeEvent
    public static void toss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (!(event.getPlayer() instanceof ServerPlayer p) || !ModDimensions.isKakuriyo(p.level())) return;
        if (stack.is(ModItems.SUZU.get()) || stack.is(ModItems.CHOCHIN.get()) || stack.is(ModItems.CHOCHIN_LIT.get())) {
            event.setCanceled(true);
            p.getInventory().add(stack.copy());
        }
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && ModDimensions.isKakuriyo(p.level()) && !p.isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        MeikonCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        SelfTest.onStarted(event.getServer());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        ServerLevel level = GameManager.kakuriyo(event.getServer());
        if (level != null) LanternLight.removeAll(level);
        Director.clearTasks();
        GameManager.onServerStopping();
    }
}
