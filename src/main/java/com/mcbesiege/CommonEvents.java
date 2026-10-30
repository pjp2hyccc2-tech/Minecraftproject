package com.mcbesiege;

import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.block.MachinePartBlockEntity;
import com.mcbesiege.entity.MachineEntity;
import com.mcbesiege.machine.LevelPhysics;
import com.mcbesiege.machine.Machine;
import com.mcbesiege.machine.MachineManager;
import com.mcbesiege.mission.MissionManager;
import com.mcbesiege.mission.Missions;
import com.mcbesiege.part.PartType;
import com.mcbesiege.registry.ModBlocks;
import com.mcbesiege.test.SelfTest;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = MCBesiege.MODID)
public final class CommonEvents {
    private CommonEvents() {}

    /** Besiege-style mirror building: places the mirrored copy across the starting block's center plane. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockState st = e.getPlacedBlock();
        if (!(st.getBlock() instanceof MachinePartBlock mb) || mb.type == PartType.STARTING_BLOCK) return;
        if (!p.getPersistentData().getBoolean("mcbesiege_mirror")) return;
        if (!p.isCreative() && !MissionManager.inMission(p)) return;
        BlockPos pos = e.getPos();
        BlockPos start = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-16, -8, -16), pos.offset(16, 8, 16))) {
            BlockState s = level.getBlockState(q);
            if (s.getBlock() instanceof MachinePartBlock b && b.type == PartType.STARTING_BLOCK) {
                double d = q.distSqr(pos);
                if (d < bd) {
                    bd = d;
                    start = q.immutable();
                }
            }
        }
        if (start == null) return;
        Direction fwd = level.getBlockState(start).getValue(MachinePartBlock.FACING);
        BlockPos mirrored;
        Mirror mirror;
        if (fwd.getAxis() == Direction.Axis.Z) {
            mirrored = new BlockPos(2 * start.getX() - pos.getX(), pos.getY(), pos.getZ());
            mirror = Mirror.FRONT_BACK;
        } else {
            mirrored = new BlockPos(pos.getX(), pos.getY(), 2 * start.getZ() - pos.getZ());
            mirror = Mirror.LEFT_RIGHT;
        }
        if (mirrored.equals(pos) || !level.getBlockState(mirrored).canBeReplaced()) return;
        level.setBlock(mirrored, st.mirror(mirror), 3);
        BlockEntity src = level.getBlockEntity(pos);
        BlockEntity dst = level.getBlockEntity(mirrored);
        if (src instanceof MachinePartBlockEntity a && dst instanceof MachinePartBlockEntity b) b.setConfig(a.getConfig().copy());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getState().is(ModBlocks.SIEGE_TARGET.get()) && e.getPlayer() instanceof ServerPlayer p && MissionManager.inMission(p) && !p.isCreative()) {
            e.setCanceled(true);
            p.displayClientMessage(Component.translatable("msg.mcbesiege.target_hand"), true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        MissionManager.onDeath(e.getEntity());
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        MissionManager.tick(server);
        SelfTest.tick(server);
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent e) {
        SelfTest.start(e.getServer());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            MachineEntity own;
            int guard = 0;
            while ((own = MachineManager.ownedMachine(p)) != null && guard++ < 16) MachineManager.reset(own, true);
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) MissionManager.onDimensionChange(p);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("mcbesiege")
                .then(Commands.literal("reset").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    MachineEntity own = MachineManager.ownedMachine(p);
                    if (own != null) MachineManager.reset(own, true);
                    return 1;
                }))
                .then(Commands.literal("resetall").requires(s -> s.hasPermission(2)).executes(c -> {
                    LevelPhysics lp = LevelPhysics.peek(c.getSource().getLevel());
                    int n = 0;
                    if (lp != null) {
                        for (Machine m : new ArrayList<>(lp.machines)) {
                            MachineManager.reset(m.entity, m.owner != null);
                            n++;
                        }
                    }
                    final int count = n;
                    c.getSource().sendSuccess(() -> Component.literal("reset " + count + " machines"), true);
                    return n;
                }))
                .then(Commands.literal("leave").executes(c -> {
                    MissionManager.leave(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("mission").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (Missions.Def d : Missions.ALL) b.suggest(d.id());
                            return b.buildFuture();
                        })
                        .executes(c -> {
                            MissionManager.start(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id"));
                            return 1;
                        }))));
    }
}
