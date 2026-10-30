package com.mcbesiege.machine;

import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.part.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** A captured machine design: every part with its position relative to the starting block. */
public final class Blueprint {
    public static final class Entry {
        public final BlockPos rel;
        public final BlockState state;
        public final CompoundTag data;

        public Entry(BlockPos rel, BlockState state, CompoundTag data) {
            this.rel = rel;
            this.state = state;
            this.data = data;
        }

        public PartType type() {
            return state.getBlock() instanceof MachinePartBlock b ? b.type : null;
        }
    }

    public BlockPos origin = BlockPos.ZERO;
    public Direction forward = Direction.NORTH;
    public String name = "";
    public final List<Entry> entries = new ArrayList<>();

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("origin", origin.asLong());
        t.putInt("fwd", forward.get3DDataValue());
        t.putString("name", name);
        ListTag l = new ListTag();
        for (Entry e : entries) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", e.rel.asLong());
            c.put("s", NbtUtils.writeBlockState(e.state));
            c.put("d", e.data.copy());
            l.add(c);
        }
        t.put("parts", l);
        return t;
    }

    public static Blueprint load(CompoundTag t) {
        Blueprint b = new Blueprint();
        b.origin = BlockPos.of(t.getLong("origin"));
        b.forward = Direction.from3DDataValue(t.getInt("fwd"));
        b.name = t.getString("name");
        ListTag l = t.getList("parts", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            BlockState s = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), c.getCompound("s"));
            if (!(s.getBlock() instanceof MachinePartBlock)) continue;
            b.entries.add(new Entry(BlockPos.of(c.getLong("p")), s, c.getCompound("d")));
        }
        return b;
    }

    public int count(PartType t) {
        int n = 0;
        for (Entry e : entries) if (e.type() == t) n++;
        return n;
    }
}
