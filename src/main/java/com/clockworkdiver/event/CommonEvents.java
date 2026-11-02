package com.clockworkdiver.event;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.combat.Quality;
import com.clockworkdiver.combat.TickStrike;
import com.clockworkdiver.command.ClockworkCommand;
import com.clockworkdiver.data.PlayerProgress;
import com.clockworkdiver.data.WorldProgress;
import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.interior.DiveSession;
import com.clockworkdiver.registry.ModBlocks;
import com.clockworkdiver.registry.ModDimensions;
import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.registry.ModSounds;
import com.clockworkdiver.story.StoryManager;
import com.clockworkdiver.util.DelayedTasks;
import com.clockworkdiver.workshop.Objectives;
import com.clockworkdiver.workshop.WorkshopManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ClockworkDiver.MODID)
public final class CommonEvents {
    private CommonEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        DelayedTasks.tick(server);
        DiveManager.tick(server);
        TickStrike.tick(server);
        if (server.getTickCount() % 20 == 0) WorkshopManager.tick(server);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DelayedTasks.clear();
        DiveManager.clearAll();
        TickStrike.clearAll();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ClockworkCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerProgress progress = WorldProgress.of(player);
        if (!progress.gotKey) {
            progress.gotKey = true;
            ItemStack key = new ItemStack(ModItems.WORKSHOP_KEY.get());
            if (!player.getInventory().add(key)) player.drop(key, false);
        }
        if (!progress.tutorialDone) {
            Component link = Component.translatable("message.clockworkdiver.welcome.link").withStyle(Style.EMPTY
                    .withColor(ChatFormatting.GOLD).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clockwork workshop"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.clockworkdiver.welcome.hover"))));
            player.sendSystemMessage(Component.translatable("message.clockworkdiver.welcome").withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.translatable("message.clockworkdiver.welcome.key").append(" ").append(link));
        }
        // Logged out while shrunk inside a clock: wake up at the bench.
        if (ModDimensions.isInterior(player.level()) && DiveManager.session(player) == null) {
            DelayedTasks.schedule(player.server, 20, () -> WorkshopManager.returnToBench(player, false));
        }
        DelayedTasks.schedule(player.server, 40, () -> {
            Objectives.sync(player);
            StoryManager.sync(player);
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DiveManager.onLogout(player);
            TickStrike.clear(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DelayedTasks.schedule(player.server, 15, () -> Objectives.sync(player));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DiveManager.onPlayerDeath(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        TickStrike.onLivingHurt(event);
    }

    /** A Just Tick blow shatters a rust cluster at once. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        BlockPos pos = event.getPos();
        if (level.getBlockState(pos).is(ModBlocks.MEMORY_COG.get())) {
            DiveManager.onFragment(player, pos);
            event.setCanceled(true);
            return;
        }
        if (level.getBlockState(pos).is(ModBlocks.DUST_PILE.get())) {
            DiveSession ds = DiveManager.session(player);
            if (ds != null && player.getMainHandItem().is(ModItems.TINKER_WRENCH.get())
                    && DiveManager.onDustPoked(player, pos, TickStrike.consumeQuality(player, ds))) {
                event.setCanceled(true);
            }
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.RUST_CLUSTER.get())) return;
        DiveSession s = DiveManager.session(player);
        if (s == null || !player.getMainHandItem().is(ModItems.TINKER_WRENCH.get())) return;
        if (TickStrike.consumeQuality(player, s) == Quality.PERFECT) {
            DiveManager.onRustBroken(player, pos);
            level.destroyBlock(pos, false, player);
            ((ServerLevel) level).playSound(null, pos, ModSounds.PERFECT.get(), SoundSource.PLAYERS, 1.0f, 1.2f);
            TickStrike.onPerfectAction(player);
            event.setCanceled(true);
        }
    }

    /** The workshop and clock interiors are not for building in (except in creative mode). */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isCreative()) return;
        if (!(event.getLevel() instanceof Level level) || !ModDimensions.isModDimension(level)) return;
        if (ModDimensions.isInterior(level) && event.getState().is(ModBlocks.RUST_CLUSTER.get())) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;
        if (event.getLevel() instanceof Level level && ModDimensions.isModDimension(level)) event.setCanceled(true);
    }
}
