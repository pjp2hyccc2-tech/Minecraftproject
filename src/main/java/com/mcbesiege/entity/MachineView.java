package com.mcbesiege.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Client copy of a machine: render pieces, connectors and interpolated body poses (plain data, safe on both sides). */
public final class MachineView {
    public static final class SubView {
        public BlockState state;
        public int body;
        public float ox, oy, oz;
        public int part;
        public boolean charred;
    }

    public static final class ConView {
        public String kind;
        public int ba, bb;
        public float ax, ay, az, bx, by, bz;
    }

    public static final class HelpEntry {
        public String action;
        public int[] keys;
        public int count;
    }

    public int version = -1;
    public int bodyCount;
    public final List<HelpEntry> help = new ArrayList<>();
    public int totalParts, aliveParts;
    public final List<SubView> subs = new ArrayList<>();
    public final List<ConView> cons = new ArrayList<>();
    public double[] prev = new double[0];
    public double[] cur = new double[0];
    public byte[] flags = new byte[0];
    public boolean hasState;
    public int stateAge;

    public void applyStructure(CompoundTag t) {
        if (t == null || !t.contains("v")) return;
        version = t.getInt("v");
        bodyCount = t.getInt("n");
        subs.clear();
        ListTag l = t.getList("subs", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            SubView s = new SubView();
            s.state = Block.stateById(c.getInt("s"));
            s.body = c.getInt("b");
            s.ox = c.getFloat("x");
            s.oy = c.getFloat("y");
            s.oz = c.getFloat("z");
            s.part = c.getInt("p");
            s.charred = c.getBoolean("c");
            subs.add(s);
        }
        cons.clear();
        ListTag cl = t.getList("cons", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) {
            CompoundTag c = cl.getCompound(i);
            ConView v = new ConView();
            v.kind = c.getString("k");
            v.ba = c.getInt("ba");
            v.bb = c.getInt("bb");
            v.ax = c.getFloat("ax");
            v.ay = c.getFloat("ay");
            v.az = c.getFloat("az");
            v.bx = c.getFloat("bx");
            v.by = c.getFloat("by");
            v.bz = c.getFloat("bz");
            cons.add(v);
        }
        help.clear();
        ListTag hl = t.getList("help", Tag.TAG_COMPOUND);
        for (int i = 0; i < hl.size(); i++) {
            CompoundTag c = hl.getCompound(i);
            HelpEntry h = new HelpEntry();
            h.action = c.getString("a");
            h.keys = c.getIntArray("k");
            h.count = c.getInt("n");
            help.add(h);
        }
        totalParts = t.getInt("total");
        aliveParts = t.getInt("alive");
        if (cur.length != bodyCount * 7) {
            hasState = false;
        }
    }

    public void applyState(int v, double[] poses, byte[] fl) {
        if (v != version) return;
        if (!hasState || cur.length != poses.length) {
            prev = poses.clone();
        } else {
            prev = cur;
        }
        cur = poses;
        flags = fl;
        hasState = true;
        stateAge = 0;
    }

    public void clientTick() {
        stateAge++;
    }

    /** Interpolated pose of body i: {x, y, z, qw, qx, qy, qz}. */
    public boolean pose(int i, float pt, double[] out) {
        if (!hasState || i < 0 || (i + 1) * 7 > cur.length) return false;
        int o = i * 7;
        if (prev.length != cur.length) {
            System.arraycopy(cur, o, out, 0, 7);
            return true;
        }
        float t = Math.min(1f, pt);
        for (int k = 0; k < 3; k++) out[k] = prev[o + k] + (cur[o + k] - prev[o + k]) * t;
        double aw = prev[o + 3], ax = prev[o + 4], ay = prev[o + 5], az = prev[o + 6];
        double bw = cur[o + 3], bx = cur[o + 4], by = cur[o + 5], bz = cur[o + 6];
        double d = aw * bw + ax * bx + ay * by + az * bz;
        if (d < 0) {
            bw = -bw;
            bx = -bx;
            by = -by;
            bz = -bz;
        }
        double w = aw + (bw - aw) * t, x = ax + (bx - ax) * t, y = ay + (by - ay) * t, z = az + (bz - az) * t;
        double l = Math.sqrt(w * w + x * x + y * y + z * z);
        if (l < 1e-9) l = 1;
        out[3] = w / l;
        out[4] = x / l;
        out[5] = y / l;
        out[6] = z / l;
        return true;
    }

    /** World position of a body-frame offset, using a pose from {@link #pose}. */
    public static double[] transform(double[] pose, double lx, double ly, double lz) {
        double w = pose[3], x = pose[4], y = pose[5], z = pose[6];
        double tx = 2 * (y * lz - z * ly), ty = 2 * (z * lx - x * lz), tz = 2 * (x * ly - y * lx);
        double rx = lx + w * tx + (y * tz - z * ty);
        double ry = ly + w * ty + (z * tx - x * tz);
        double rz = lz + w * tz + (x * ty - y * tx);
        return new double[]{pose[0] + rx, pose[1] + ry, pose[2] + rz};
    }

    public int partFlags(int part) {
        return part >= 0 && part < flags.length ? flags[part] : 0;
    }
}
