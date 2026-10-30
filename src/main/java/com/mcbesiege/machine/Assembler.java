package com.mcbesiege.machine;

import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.block.MachinePartBlockEntity;
import com.mcbesiege.part.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/** Captures the machine connected to a starting block (Besiege attachment rules: parts only join through their attach faces). */
public final class Assembler {
    private Assembler() {}

    public static final class Result {
        public Blueprint blueprint;
        public String error;
        public int startingBlocks;
    }

    /** True if a part of the given type/facing can connect through the given face. */
    public static boolean connects(PartType t, Direction facing, Direction face) {
        return switch (t.connect) {
            case ALL -> true;
            case BACK -> face == facing.getOpposite();
            case BACK_FRONT -> face == facing.getOpposite() || face == facing;
        };
    }

    public static Result capture(Level level, BlockPos start, int maxParts) {
        Result r = new Result();
        BlockState s0 = level.getBlockState(start);
        if (!(s0.getBlock() instanceof MachinePartBlock b0) || b0.type != PartType.STARTING_BLOCK) {
            r.error = "no_start";
            return r;
        }
        Blueprint bp = new Blueprint();
        bp.origin = start.immutable();
        bp.forward = s0.getValue(MachinePartBlock.FACING).getAxis().isHorizontal() ? s0.getValue(MachinePartBlock.FACING) : Direction.NORTH;
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof MachinePartBlock mb)) continue;
            if (mb.type == PartType.STARTING_BLOCK) r.startingBlocks++;
            if (bp.entries.size() >= maxParts) {
                r.error = "too_big";
                return r;
            }
            CompoundTag data = new CompoundTag();
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof MachinePartBlockEntity pbe) data = pbe.saveData();
            bp.entries.add(new Blueprint.Entry(p.subtract(start), st.setValue(MachinePartBlock.SIM_PART, MachinePartBlock.SimPart.FULL), data));
            Direction facing = st.getValue(MachinePartBlock.FACING);
            for (Direction d : Direction.values()) {
                if (!connects(mb.type, facing, d)) continue;
                BlockPos q = p.relative(d);
                if (seen.contains(q)) continue;
                BlockState qs = level.getBlockState(q);
                if (!(qs.getBlock() instanceof MachinePartBlock qb)) continue;
                if (!connects(qb.type, qs.getValue(MachinePartBlock.FACING), d.getOpposite())) continue;
                seen.add(q.immutable());
                queue.add(q.immutable());
            }
            // two-point connectors
            ListTag cons = data.getList("con", 10);
            for (int i = 0; i < cons.size(); i++) {
                CompoundTag c = cons.getCompound(i);
                BlockPos q = p.offset(c.getInt("dx"), c.getInt("dy"), c.getInt("dz"));
                if (seen.contains(q)) continue;
                if (!(level.getBlockState(q).getBlock() instanceof MachinePartBlock)) continue;
                seen.add(q.immutable());
                queue.add(q.immutable());
            }
        }
        if (r.startingBlocks > 1) {
            r.error = "two_starts";
            return r;
        }
        r.blueprint = bp;
        return r;
    }
}
