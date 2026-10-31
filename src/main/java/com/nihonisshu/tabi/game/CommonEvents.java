package com.nihonisshu.tabi.game;

import com.google.gson.JsonObject;
import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.block.RicePlantBlock;
import com.nihonisshu.tabi.block.TabiCropBlock;
import com.nihonisshu.tabi.data.TabiData;
import com.nihonisshu.tabi.entity.VehicleEntity;
import com.nihonisshu.tabi.registry.TabiItems;
import com.nihonisshu.tabi.registry.TabiSounds;
import com.nihonisshu.tabi.registry.TabiWorldgen;
import com.nihonisshu.tabi.test.SelfTest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TabiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    private CommonEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!PlayerData.flag(sp, "started")) {
            startJourney(sp);
        }
        PlayerData.sync(sp);
    }

    public static void startJourney(ServerPlayer sp) {
        ServerLevel japan = sp.server.getLevel(TabiWorldgen.JAPAN);
        if (japan == null) {
            TabiMod.LOGGER.error("[tabi] japan dimension missing");
            return;
        }
        JsonObject spawn = TabiData.meta().getAsJsonObject("spawn");
        double x = TabiData.dbl(spawn, "x", 0.5), y = TabiData.dbl(spawn, "y", 80), z = TabiData.dbl(spawn, "z", 0.5);
        float yaw = (float) TabiData.dbl(spawn, "yaw", 0);
        japan.getChunk(BlockPos.containing(x, y, z));
        sp.teleportTo(japan, x, y, z, yaw, 10f);
        sp.setRespawnPosition(TabiWorldgen.JAPAN, BlockPos.containing(x, y, z), yaw, true, false);
        PlayerData.setYen(sp, 500);
        PlayerData.setFlag(sp, "started", true);
        ItemStack journal = new ItemStack(TabiItems.JOURNAL.get());
        if (!sp.getInventory().add(journal)) sp.drop(journal, false);
        if (sp.server.getPlayerCount() <= 1) {
            sp.server.overworld().setDayTime(500);
        }
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(20, 80, 30));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("日本一周旅")));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("夏休み、はじまりの朝。まずは家族に話しかけてみよう")));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (PlayerData.flag(sp, "started") && sp.level().dimension() != TabiWorldgen.JAPAN && !event.isEndConquered()) {
            ServerLevel japan = sp.server.getLevel(TabiWorldgen.JAPAN);
            JsonObject spawn = TabiData.meta().getAsJsonObject("spawn");
            if (japan != null) {
                sp.teleportTo(japan, TabiData.dbl(spawn, "x", 0.5), TabiData.dbl(spawn, "y", 80), TabiData.dbl(spawn, "z", 0.5),
                        (float) TabiData.dbl(spawn, "yaw", 0), 0f);
            }
        }
        PlayerData.sync(sp);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // persisted data is copied by Forge; nothing else to do
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 10 == 0) Areas.tick(sp);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        TransitManager.tick(event.getServer());
        ServerLevel japan = event.getServer().getLevel(TabiWorldgen.JAPAN);
        if (japan != null && japan.getDayTime() % 24000L == 11000L) {
            for (ServerPlayer p : japan.players()) {
                if (p.getX() < 1500) p.playNotifySound(TabiSounds.EVENING_CHIME.get(), SoundSource.AMBIENT, 0.7f, 1f);
            }
        }
        SelfTest.tick(event.getServer());
        CrossingManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        SelfTest.start(event.getServer());
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof VehicleEntity v
                && event.getEntityMounting() instanceof ServerPlayer sp
                && sp.isAlive() && !sp.hasDisconnected() && !sp.isRemoved()
                && v.getState() != VehicleEntity.STOPPED) {
            event.setCanceled(true);
            sp.displayClientMessage(Component.literal("走行中は降りられません。次の駅で降りよう"), true);
        }
    }

    private static boolean farmBlock(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.getBlock() instanceof TabiCropBlock
                || state.getBlock() instanceof RicePlantBlock || state.getBlock() instanceof FarmBlock;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player p = event.getPlayer();
        if (p == null || p.isCreative() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != TabiWorldgen.JAPAN) return;
        if (farmBlock(event.getState()) || Areas.buildable(level, event.getPos())) return;
        event.setCanceled(true);
        p.displayClientMessage(Component.literal("ここのものは壊せません（畑と自分の部屋ならOK）"), true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player p) || p.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != TabiWorldgen.JAPAN) return;
        if (farmBlock(event.getPlacedBlock()) || Areas.buildable(level, event.getPos())) return;
        event.setCanceled(true);
        p.displayClientMessage(Component.literal("ここには置けません（畑と自分の部屋ならOK）"), true);
    }
}
