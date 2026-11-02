package com.birdiecraft.match;

import com.birdiecraft.golf.engine.Difficulty;
import com.birdiecraft.golf.engine.Lie;
import com.birdiecraft.golf.engine.PlayStyle;

import java.util.UUID;

/** A golfer in a match: a player or a CPU. */
public final class Participant {
    public static final int[] COLORS = {0xFFFFFF, 0xFF5A5A, 0x5AB4FF, 0xFFD24A, 0x7CFF6B, 0xFF8AE2, 0xFF9F40, 0xB48CFF};

    public final int id;
    public final String name;
    public final UUID uuid;
    public final boolean cpu;
    public PlayStyle style;
    public Difficulty difficulty = Difficulty.AMATEUR;
    public int skin;
    public int color;
    public final int[] strokes;
    public int cur;
    public double bx, by, bz;
    public double px, py, pz;
    public Lie lie = Lie.TEE;
    public Lie prevLie = Lie.TEE;
    public boolean holed;
    public boolean finished;
    public int powerShots;
    public int lastTerm = -1;
    public UUID entityId;
    public int perfects;
    public int shots;

    public Participant(int id, String name, UUID uuid, boolean cpu, PlayStyle style, int holeCount) {
        this.id = id;
        this.name = name;
        this.uuid = uuid;
        this.cpu = cpu;
        this.style = style;
        this.strokes = new int[holeCount];
        this.color = COLORS[id % COLORS.length];
    }

    public int total() {
        int t = 0;
        for (int s : strokes) t += s;
        return t;
    }

    public int holesPlayed() {
        int n = 0;
        for (int s : strokes) if (s > 0) n++;
        return n;
    }

    public void placeBall(double x, double y, double z, Lie lie) {
        this.bx = x;
        this.by = y;
        this.bz = z;
        this.lie = lie;
    }

    public void rememberPrevious() {
        this.px = bx;
        this.py = by;
        this.pz = bz;
        this.prevLie = lie;
    }
}
