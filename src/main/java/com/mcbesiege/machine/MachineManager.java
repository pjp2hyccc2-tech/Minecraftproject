package com.mcbesiege.machine;

import com.mcbesiege.Config;
import com.mcbesiege.MCBesiege;
import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.block.MachinePartBlockEntity;
import com.mcbesiege.entity.MachineEntity;
import com.mcbesiege.mission.MissionManager;
import com.mcbesiege.net.Net;
import com.mcbesiege.part.PartType;
import com.mcbesiege.physics.V3;
import com.mcbesiege.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Build → simulate → reset cycle (Besiege's play button), plus ticking of the per-level physics. */
@Mod.EventBusSubscriber(modid = MCBesiege.MODID)
public final class MachineManager {
    private MachineManager() {}

    @SubscribeEvent
    public static void levelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.level.isClientSide || !(e.level instanceof ServerLevel sl)) return;
        LevelPhysics lp = LevelPhysics.peek(sl);
        if (lp != null) {
            try {
                lp.tick();
            } catch (Exception ex) {
                MCBesiege.LOGGER.error("[mcbesiege] physics tick failed", ex);
            }
        }
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent e) {
        LevelPhysics.clearAll();
    }

    // ---------------------------------------------------------------------------------------------------- start / stop

    public static MachineEntity startSimulation(ServerLevel level, BlockPos start, ServerPlayer player) {
        Assembler.Result r = Assembler.capture(level, start, Config.maxParts());
        if (r.error != null) {
            if (player != null) player.displayClientMessage(Component.translatable("msg.mcbesiege.err." + r.error), true);
            return null;
        }
        Blueprint bp = r.blueprint;
        if (player != null) {
            String err = MissionManager.checkStart(player, bp);
            if (err != null) {
                player.displayClientMessage(Component.translatable(err), true);
                return null;
            }
        }
        // seat to board (player standing next to a seat)
        BlockPos seatPos = null;
        if (player != null) {
            double best = 2.6 * 2.6;
            for (Blueprint.Entry e : bp.entries) {
                if (e.type() != PartType.SEAT) continue;
                BlockPos p = bp.origin.offset(e.rel);
                double d = player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                if (d < best) {
                    best = d;
                    seatPos = p;
                }
            }
        }
        for (Blueprint.Entry e : bp.entries) {
            BlockPos p = bp.origin.offset(e.rel);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        }
        MachineEntity ent = new MachineEntity(ModEntities.MACHINE.get(), level);
        ent.setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        UUID owner = player != null ? player.getUUID() : null;
        Machine m = Machine.create(ent, level, bp, owner);
        m.groundLift();
        ent.setMachine(m);
        V3 c = m.corePos();
        ent.setPos(c.x, c.y - 0.5, c.z);
        level.addFreshEntity(ent);
        m.addToWorld();
        level.playSound(null, start, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        if (player != null) {
            if (seatPos != null) player.startRiding(ent, true);
            CompoundTag t = new CompoundTag();
            t.putInt("id", ent.getId());
            Net.send(player, "control", t);
            player.displayClientMessage(Component.translatable("msg.mcbesiege.started", bp.entries.size()), true);
            MissionManager.onSimulationStarted(player, ent);
        }
        return ent;
    }

    public static void reset(MachineEntity e, boolean restore) {
        Machine m = e.machine;
        e.ejectPassengers();
        if (m != null) {
            m.removeFromWorld();
            if (restore) restoreBlueprint(m.level, m.blueprint);
            m.level.playSound(null, m.blueprint.origin, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.4f);
        }
        e.discard();
    }

    public static void restoreBlueprint(ServerLevel level, Blueprint bp) {
        for (Blueprint.Entry e : bp.entries) place(level, bp.origin.offset(e.rel), e.state, e.data);
    }

    private static void place(ServerLevel level, BlockPos p, BlockState state, CompoundTag data) {
        BlockState cur = level.getBlockState(p);
        if (cur.isAir() || cur.canBeReplaced() || cur.getBlock() instanceof LiquidBlock) {
            level.setBlock(p, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof MachinePartBlockEntity pbe) pbe.loadData(data);
        } else if (!(cur.getBlock() instanceof MachinePartBlock)) {
            Block.popResource(level, p, new ItemStack(state.getBlock()));
        }
    }

    /** Settles the machine where it stands: blocks are placed at the current (grid-snapped) positions. */
    public static boolean placeHere(MachineEntity e, ServerPlayer player) {
        Machine m = e.machine;
        if (m == null || m.core == null || m.core.base.body == null) return false;
        V3 up = m.core.base.body.rb.rot.rotate(V3.Y);
        if (up.y < 0.8) {
            if (player != null) player.displayClientMessage(Component.translatable("msg.mcbesiege.not_upright"), true);
            return false;
        }
        V3 fwd = m.core.base.body.rb.rot.rotate(m.forward);
        double yaw = Math.atan2(fwd.x, -fwd.z) - Math.atan2(m.forward.x, -m.forward.z);
        int quarter = Math.floorMod((int) Math.round(yaw / (Math.PI / 2)), 4);
        Rotation rot = switch (quarter) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        ServerLevel level = m.level;
        m.removeFromWorld();
        e.ejectPassengers();
        for (MPart p : m.parts) {
            if (!p.alive) continue;
            V3 c = p.base.worldCellCenter();
            BlockPos bp = BlockPos.containing(c.x, c.y, c.z);
            BlockState st = p.state.rotate(rot);
            if (p.debris) {
                Block.popResource(level, bp, new ItemStack(p.state.getBlock()));
                continue;
            }
            CompoundTag data = p.data.copy();
            if (rot != Rotation.NONE) data.remove("con");
            place(level, bp, st, data);
        }
        e.discard();
        return true;
    }

    // ---------------------------------------------------------------------------------------------------- lookups

    public static MachineEntity ownedMachine(ServerPlayer p) {
        LevelPhysics lp = LevelPhysics.peek(p.serverLevel());
        if (lp == null) return null;
        MachineEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Machine m : lp.machines) {
            if (m.owner == null || !m.owner.equals(p.getUUID()) || m.entity.isRemoved()) continue;
            double d = m.entity.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = m.entity;
            }
        }
        return best;
    }

    /** The R key: reset the player's running machine, or start the machine at / near the given starting block. */
    public static void toggle(ServerPlayer p, BlockPos looked) {
        MachineEntity own = ownedMachine(p);
        if (own != null) {
            reset(own, true);
            p.displayClientMessage(Component.translatable("msg.mcbesiege.reset"), true);
            return;
        }
        ServerLevel level = p.serverLevel();
        BlockPos start = null;
        if (looked != null && isStart(level, looked)) start = looked;
        if (start == null) {
            BlockPos base = p.blockPosition();
            double bd = Double.MAX_VALUE;
            for (BlockPos q : BlockPos.betweenClosed(base.offset(-8, -4, -8), base.offset(8, 6, 8))) {
                if (!isStart(level, q)) continue;
                double d = q.distSqr(base);
                if (d < bd) {
                    bd = d;
                    start = q.immutable();
                }
            }
        }
        if (start == null) {
            p.displayClientMessage(Component.translatable("msg.mcbesiege.no_start"), true);
            return;
        }
        startSimulation(level, start, p);
    }

    private static boolean isStart(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getBlock() instanceof MachinePartBlock b && b.type == PartType.STARTING_BLOCK;
    }

    public static Direction horizontal(V3 v) {
        return Direction.getNearest(v.x, 0, v.z);
    }
}
