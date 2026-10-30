package com.mcbesiege.net;

import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.block.MachinePartBlockEntity;
import com.mcbesiege.entity.MachineEntity;
import com.mcbesiege.machine.Assembler;
import com.mcbesiege.machine.Blueprint;
import com.mcbesiege.machine.MachineManager;
import com.mcbesiege.mission.MissionManager;
import com.mcbesiege.part.PartConfig;
import com.mcbesiege.part.PartType;
import com.mcbesiege.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/** Server-side handling of client requests. */
public final class ServerNet {
    private ServerNet() {}

    public static void handle(ServerPlayer p, String kind, CompoundTag d) {
        switch (kind) {
            case "toggle" -> MachineManager.toggle(p, d.contains("pos") ? BlockPos.of(d.getLong("pos")) : null);
            case "reset" -> {
                MachineEntity e = MachineManager.ownedMachine(p);
                if (e != null) MachineManager.reset(e, true);
            }
            case "place_here" -> {
                MachineEntity e = MachineManager.ownedMachine(p);
                if (e != null && MachineManager.placeHere(e, p)) p.displayClientMessage(Component.translatable("msg.mcbesiege.placed"), true);
            }
            case "board" -> {
                MachineEntity e = MachineManager.ownedMachine(p);
                if (e != null && p.getVehicle() == null && e.distanceToSqr(p) < 24 * 24) p.startRiding(e, true);
            }
            case "cfg" -> config(p, d);
            case "con_cfg" -> connectorConfig(p, d);
            case "palette" -> palette(p, d.getString("item"));
            case "mirror" -> {
                boolean on = !p.getPersistentData().getBoolean("mcbesiege_mirror");
                p.getPersistentData().putBoolean("mcbesiege_mirror", on);
                p.displayClientMessage(Component.translatable(on ? "msg.mcbesiege.mirror_on" : "msg.mcbesiege.mirror_off"), true);
            }
            case "clear" -> clearMachine(p, BlockPos.of(d.getLong("pos")));
            case "mission_start" -> MissionManager.start(p, d.getString("id"));
            case "mission_leave" -> MissionManager.leave(p);
            default -> {}
        }
    }

    public static void keys(ServerPlayer p, int id, int[] keys) {
        Entity e = p.level().getEntity(id);
        if (!(e instanceof MachineEntity me) || me.machine == null) return;
        boolean allowed = (me.machine.owner != null && me.machine.owner.equals(p.getUUID())) || p.getVehicle() == me;
        if (!allowed || me.distanceToSqr(p) > 256 * 256) return;
        Set<Integer> s = new HashSet<>();
        for (int k : keys) s.add(k);
        me.machine.keys.clear();
        me.machine.keys.addAll(s);
    }

    private static boolean near(ServerPlayer p, BlockPos pos) {
        return p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 16 * 16;
    }

    private static void config(ServerPlayer p, CompoundTag d) {
        BlockPos pos = BlockPos.of(d.getLong("pos"));
        if (!near(p, pos)) return;
        BlockState st = p.level().getBlockState(pos);
        if (!(st.getBlock() instanceof MachinePartBlock mb)) return;
        BlockEntity be = p.level().getBlockEntity(pos);
        if (!(be instanceof MachinePartBlockEntity pbe)) return;
        PartConfig c = PartConfig.of(mb.type, d.getCompound("cfg"));
        pbe.setConfig(c.save());
        if (d.getBoolean("all")) {
            // copy to every part of the same type in the same machine
            BlockPos start = findStart(p.serverLevel(), pos);
            if (start != null) {
                Assembler.Result r = Assembler.capture(p.level(), start, 2000);
                if (r.blueprint != null) {
                    for (Blueprint.Entry e : r.blueprint.entries) {
                        if (e.type() != mb.type) continue;
                        BlockEntity o = p.level().getBlockEntity(start.offset(e.rel));
                        if (o instanceof MachinePartBlockEntity ope) ope.setConfig(c.save());
                    }
                }
            }
        }
    }

    private static void connectorConfig(ServerPlayer p, CompoundTag d) {
        BlockPos pos = BlockPos.of(d.getLong("pos"));
        if (!near(p, pos)) return;
        BlockEntity be = p.level().getBlockEntity(pos);
        if (!(be instanceof MachinePartBlockEntity pbe)) return;
        ListTag list = pbe.getConnectors().copy();
        int idx = d.getInt("index");
        if (idx < 0 || idx >= list.size()) return;
        if (d.getBoolean("remove")) list.remove(idx);
        else list.getCompound(idx).put("cfg", d.getCompound("cfg"));
        pbe.setConnectors(list);
    }

    private static void palette(ServerPlayer p, String id) {
        PartType t = PartType.byId(id);
        Item item = t != null ? ModItems.part(t) : null;
        if (item == null) {
            for (var ro : ModItems.ITEMS.getEntries()) {
                if (ro.getId().getPath().equals(id)) item = ro.get();
            }
        }
        if (item == null) return;
        boolean free = p.isCreative() || MissionManager.inMission(p);
        if (!free) {
            p.displayClientMessage(Component.translatable("msg.mcbesiege.need_creative"), true);
            return;
        }
        ItemStack stack = new ItemStack(item, item.getMaxStackSize() > 1 ? 64 : 1);
        if (MissionManager.inMission(p)) stack.getOrCreateTag().putBoolean("mcbesiege_mission", true);
        int slot = p.getInventory().selected;
        ItemStack cur = p.getInventory().getItem(slot);
        if (cur.isEmpty()) p.getInventory().setItem(slot, stack);
        else if (!p.getInventory().add(stack)) p.drop(stack, false);
        p.inventoryMenu.broadcastChanges();
    }

    private static BlockPos findStart(ServerLevel level, BlockPos from) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(from.offset(-24, -16, -24), from.offset(24, 16, 24))) {
            BlockState s = level.getBlockState(q);
            if (s.getBlock() instanceof MachinePartBlock b && b.type == PartType.STARTING_BLOCK) {
                double dd = q.distSqr(from);
                if (dd < bd) {
                    bd = dd;
                    best = q.immutable();
                }
            }
        }
        return best;
    }

    private static void clearMachine(ServerPlayer p, BlockPos pos) {
        if (!p.isCreative() && !MissionManager.inMission(p)) return;
        ServerLevel level = p.serverLevel();
        BlockPos start = level.getBlockState(pos).getBlock() instanceof MachinePartBlock mb && mb.type == PartType.STARTING_BLOCK ? pos : findStart(level, pos);
        if (start == null) return;
        Assembler.Result r = Assembler.capture(level, start, 2000);
        if (r.blueprint == null) return;
        for (Blueprint.Entry e : r.blueprint.entries) {
            if (e.type() == PartType.STARTING_BLOCK) continue;
            level.setBlock(start.offset(e.rel), Blocks.AIR.defaultBlockState(), 3);
        }
        p.displayClientMessage(Component.translatable("msg.mcbesiege.cleared"), true);
    }
}
