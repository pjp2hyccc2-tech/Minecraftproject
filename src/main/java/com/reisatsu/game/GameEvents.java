package com.reisatsu.game;

import com.reisatsu.Reisatsu;
import com.reisatsu.command.ReisatsuCommand;
import com.reisatsu.debug.SelfTest;
import com.reisatsu.item.GameItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus events on the logical server. */
@Mod.EventBusSubscriber(modid = Reisatsu.MODID)
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
        if (data.started && p.getPersistentData().contains("reisatsu_return")) {
            var level = GameManager.level(p.server);
            if (level != null) {
                var w = com.reisatsu.world.Layout.marker("wake");
                GameManager.teleport(p, level, w.center(), w.yaw(), 0.0f);
            }
        }
    }

    /** Nothing in the restaurant hurts you the ordinary way: they take you instead. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!GameManager.inGame(p)) return;
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) || event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!GameManager.inGame(p) || event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        event.setCanceled(true);
        p.setHealth(p.getMaxHealth());
    }

    /** The camera, the photos and the お札 never leave your hands. */
    @SubscribeEvent
    public static void toss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (!(event.getPlayer() instanceof ServerPlayer p) || !GameManager.inGame(p)) return;
        if (GameItem.kindOf(stack) != null) {
            event.setCanceled(true);
            p.getInventory().add(stack.copy());
        }
    }

    /** Film packs and the office key lying in the restaurant. */
    @SubscribeEvent
    public static void pickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        ItemEntity ie = event.getItem();
        GameData data = GameData.get(p.server);
        for (int n = 1; n <= 3; n++) {
            if (ie.getTags().contains("reisatsu_film_" + n)) {
                data.filmTaken |= 1 << n;
                data.setDirty();
                GameManager.event(p, GameManager.EV_THOUGHT, 0, "thought.reisatsu.film_found");
            }
        }
        if (ie.getTags().contains("reisatsu_office_key")) {
            data.officeKeyTaken = true;
            data.setDirty();
            GameManager.event(p, GameManager.EV_THOUGHT, 0, "thought.reisatsu.office_key");
        }
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p && GameManager.inGame(p) && !p.isCreative()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        ReisatsuCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        SelfTest.onStarted(event.getServer());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        GameManager.onServerStopping(event.getServer());
    }
}
