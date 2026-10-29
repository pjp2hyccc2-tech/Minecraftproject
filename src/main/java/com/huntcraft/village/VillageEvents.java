package com.huntcraft.village;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCDimensions;
import com.huntcraft.core.HCEvents;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.village.companion.CompanionManager;
import com.huntcraft.village.meal.MealSystem;
import com.huntcraft.village.world.VillageBuilder;
import com.huntcraft.village.world.VillageTravel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Forge-bus events of the village module: companions, meals, hunt records, village protection, upkeep. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillageEvents {
    private VillageEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        MealSystem.tick(p);
        if (p.tickCount % 10 == 3) CompanionManager.tick(p);
        if (p.tickCount % 100 == 51) CompanionManager.tickExpeditions(p);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 100 != 17) return;
        ServerLevel village = server.getLevel(HCDimensions.HUNTER_VILLAGE);
        if (village != null && !village.players().isEmpty()) VillageBuilder.maintain(village);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CompanionManager.clear();
    }

    /** Hunter notes records (hunt / capture counts, size records) + companion XP. */
    @SubscribeEvent
    public static void onHunted(HCEvents.MonsterHuntedEvent event) {
        HuntMonster m = event.getMonster();
        if (m.level().isClientSide) return;
        for (ServerPlayer p : CompanionManager.participants(event)) {
            HunterData.get(p).recordHunt(m.profile().id, m.getMonsterScale(), event.isCaptured());
        }
        CompanionManager.onHunted(event);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        // meals last for one quest: returning from a quest map to the village ends the meal
        if (HCDimensions.QUEST_MAPS.contains(event.getFrom()) && event.getTo() == HCDimensions.HUNTER_VILLAGE) MealSystem.clear(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CompanionManager.despawn(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !event.isCanceled()) {
            MealSystem.clear(p);
            CompanionManager.despawn(p.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent event) {
        MealSystem.onLivingDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && event.getEntity() != p && event.getAmount() > 0) {
            CompanionManager.noteOwnerHit(p, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        VillageCommands.register(event.getDispatcher());
    }

    // ------------------------------------------------------------------ village protection (survival players)
    private static boolean protectedAt(Level level, net.minecraft.core.BlockPos pos, Player player) {
        return HCDimensions.isVillage(level) && VillageTravel.inVillageBounds(pos) && (player == null || !player.isCreative());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && protectedAt(level, event.getPos(), event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && event.getEntity() instanceof Player p && protectedAt(level, event.getPos(), p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof Level level && HCDimensions.isVillage(level)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplode(ExplosionEvent.Detonate event) {
        if (HCDimensions.isVillage(event.getLevel())) event.getAffectedBlocks().clear();
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player p = event.getEntity();
        if (!protectedAt(event.getLevel(), event.getPos(), p)) return;
        ItemStack st = event.getItemStack();
        if (st.getItem() instanceof BucketItem || st.is(Items.FLINT_AND_STEEL) || st.is(Items.FIRE_CHARGE) || st.is(Items.BONE_MEAL)) {
            event.setCanceled(true);
        }
    }
}
