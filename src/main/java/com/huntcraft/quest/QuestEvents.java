package com.huntcraft.quest;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCDimensions;
import com.huntcraft.core.HCEvents;
import com.huntcraft.entity.monster.HuntMonster;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Forge-bus events of the quest module. Every handler is defensive: core GameTests run through here too. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestEvents {
    private QuestEvents() {
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        try {
            QuestManager.tick(server);
        } catch (RuntimeException e) {
            HuntCraft.LOGGER.error("Quest tick failed", e);
        }
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp) || sp.tickCount % 10 != 0) return;
        try {
            QuestManager.playerTick(sp);
        } catch (RuntimeException e) {
            HuntCraft.LOGGER.error("Quest player tick failed", e);
        }
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        try {
            QuestManager.onServerStarted(event.getServer());
        } catch (RuntimeException e) {
            HuntCraft.LOGGER.error("Quest restore failed", e);
        }
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        QuestManager.onServerStopping();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        try {
            if (QuestManager.onPlayerDeath(sp)) event.setCanceled(true);
        } catch (RuntimeException e) {
            HuntCraft.LOGGER.error("Quest faint handling failed", e);
        }
    }

    @SubscribeEvent
    public static void onHunted(HCEvents.MonsterHuntedEvent event) {
        try {
            QuestManager.onMonsterHunted(event.getMonster(), event.getPlayer(), event.isCaptured());
        } catch (RuntimeException e) {
            HuntCraft.LOGGER.error("Quest hunt tracking failed", e);
        }
    }

    @SubscribeEvent
    public static void onPartBroken(HCEvents.MonsterPartBrokenEvent event) {
        try {
            QuestManager.onPartBroken(event.getMonster(), event.getPart());
        } catch (RuntimeException e) {
            HuntCraft.LOGGER.error("Quest part tracking failed", e);
        }
    }

    @SubscribeEvent
    public static void onGather(HCEvents.GatherEvent event) {
        if (event.getPlayer() instanceof ServerPlayer sp) {
            try {
                QuestManager.onGather(sp, event.getStack());
            } catch (RuntimeException e) {
                HuntCraft.LOGGER.error("Quest gather tracking failed", e);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            try {
                QuestManager.onLogin(sp);
            } catch (RuntimeException e) {
                HuntCraft.LOGGER.error("Quest login handling failed", e);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) QuestManager.onLogout(sp);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            try {
                QuestManager.onChangedDimension(sp, event.getFrom());
            } catch (RuntimeException e) {
                HuntCraft.LOGGER.error("Quest dimension handling failed", e);
            }
        }
    }

    /** Monsters of finished quests that load from disk in a quest map are removed. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.loadedFromDisk()) return;
        Entity e = event.getEntity();
        if (!(e instanceof HuntMonster hm) || hm.getQuestId() == null || !HCDimensions.isQuestMap(event.getLevel())) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!QuestManager.data(sl.getServer()).instances.containsKey(hm.getQuestId())) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ quest maps are protected terrain
    private static boolean protectedFor(Player p, Level level) {
        return p != null && !p.isCreative() && HCDimensions.isQuestMap(level);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && protectedFor(event.getPlayer(), level)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && event.getEntity() instanceof Player p && protectedFor(p, level)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (HCDimensions.isQuestMap(event.getLevel())) event.getAffectedBlocks().clear();
    }

    /** Tent beds heal instead of setting the spawn point. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!HCDimensions.isQuestMap(level) || !(level.getBlockState(event.getPos()).getBlock() instanceof BedBlock)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!level.isClientSide && event.getEntity() instanceof ServerPlayer sp) {
            sp.setHealth(sp.getMaxHealth());
            sp.displayClientMessage(Component.translatable("message.huntcraft.quest.rested"), true);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        QuestCommands.register(event.getDispatcher());
    }
}
