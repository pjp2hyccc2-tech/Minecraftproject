package com.birdiecraft.match;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Network snapshot of a match, as seen by one player. */
public final class MatchView {
    public int matchId;
    public MatchMode mode = MatchMode.SINGLE;
    public MatchState state = MatchState.LOBBY;
    public int stateTicks;
    public String courseId = "";
    public Component courseTitle = Component.empty();
    public int flagColor;
    public int holeIdx;
    public int holeCount;
    public int[] holeNumbers = new int[0];
    public int[] pars = new int[0];
    public int[] yards = new int[0];
    public BlockPos tee = BlockPos.ZERO;
    public BlockPos cup = BlockPos.ZERO;
    public float windSpeed;
    public float windYaw;
    public int turnId = -1;
    public int turnTicksLeft;
    public int myId = -1;
    public boolean isHost;
    public String hostName = "";
    public final List<PView> players = new ArrayList<>();

    public static final class PView {
        public int id;
        public String name = "";
        public boolean cpu;
        public int style;
        public int difficulty;
        public int skin;
        public int color;
        public int[] strokes = new int[0];
        public int cur;
        public double bx, by, bz;
        public int lie;
        public boolean holed;
        public boolean finished;
        public int powerShots;
        public int lastTerm = -1;

        public int total() {
            int t = 0;
            for (int s : strokes) t += s;
            return t;
        }

        /** Strokes relative to par over the holes finished so far. */
        public int toPar(int[] pars) {
            int d = 0;
            for (int i = 0; i < strokes.length && i < pars.length; i++) {
                if (strokes[i] > 0) d += strokes[i] - pars[i];
            }
            return d;
        }
    }

    public int currentPar() {
        return holeIdx >= 0 && holeIdx < pars.length ? pars[holeIdx] : 4;
    }

    public int currentYards() {
        return holeIdx >= 0 && holeIdx < yards.length ? yards[holeIdx] : 0;
    }

    public int currentHoleNumber() {
        return holeIdx >= 0 && holeIdx < holeNumbers.length ? holeNumbers[holeIdx] : holeIdx + 1;
    }

    public PView player(int id) {
        for (PView p : players) if (p.id == id) return p;
        return null;
    }

    public PView me() {
        return player(myId);
    }

    public boolean isMyTurn() {
        return (state == MatchState.PLAYING) && turnId == myId && myId >= 0;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(matchId);
        buf.writeByte(mode.ordinal());
        buf.writeByte(state.ordinal());
        buf.writeVarInt(stateTicks);
        buf.writeUtf(courseId);
        buf.writeComponent(courseTitle);
        buf.writeByte(flagColor);
        buf.writeVarInt(holeIdx);
        buf.writeVarInt(holeCount);
        buf.writeVarIntArray(holeNumbers);
        buf.writeVarIntArray(pars);
        buf.writeVarIntArray(yards);
        buf.writeBlockPos(tee);
        buf.writeBlockPos(cup);
        buf.writeFloat(windSpeed);
        buf.writeFloat(windYaw);
        buf.writeVarInt(turnId + 1);
        buf.writeVarInt(turnTicksLeft);
        buf.writeVarInt(myId + 1);
        buf.writeBoolean(isHost);
        buf.writeUtf(hostName);
        buf.writeVarInt(players.size());
        for (PView p : players) {
            buf.writeVarInt(p.id);
            buf.writeUtf(p.name);
            buf.writeBoolean(p.cpu);
            buf.writeByte(p.style);
            buf.writeByte(p.difficulty);
            buf.writeByte(p.skin);
            buf.writeInt(p.color);
            buf.writeVarIntArray(p.strokes);
            buf.writeVarInt(p.cur);
            buf.writeDouble(p.bx);
            buf.writeDouble(p.by);
            buf.writeDouble(p.bz);
            buf.writeByte(p.lie);
            buf.writeBoolean(p.holed);
            buf.writeBoolean(p.finished);
            buf.writeVarInt(p.powerShots);
            buf.writeByte(p.lastTerm + 1);
        }
    }

    public static MatchView read(FriendlyByteBuf buf) {
        MatchView v = new MatchView();
        v.matchId = buf.readVarInt();
        v.mode = MatchMode.byId(buf.readByte());
        v.state = MatchState.byId(buf.readByte());
        v.stateTicks = buf.readVarInt();
        v.courseId = buf.readUtf();
        v.courseTitle = buf.readComponent();
        v.flagColor = buf.readByte();
        v.holeIdx = buf.readVarInt();
        v.holeCount = buf.readVarInt();
        v.holeNumbers = buf.readVarIntArray();
        v.pars = buf.readVarIntArray();
        v.yards = buf.readVarIntArray();
        v.tee = buf.readBlockPos();
        v.cup = buf.readBlockPos();
        v.windSpeed = buf.readFloat();
        v.windYaw = buf.readFloat();
        v.turnId = buf.readVarInt() - 1;
        v.turnTicksLeft = buf.readVarInt();
        v.myId = buf.readVarInt() - 1;
        v.isHost = buf.readBoolean();
        v.hostName = buf.readUtf();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            PView p = new PView();
            p.id = buf.readVarInt();
            p.name = buf.readUtf();
            p.cpu = buf.readBoolean();
            p.style = buf.readByte();
            p.difficulty = buf.readByte();
            p.skin = buf.readByte();
            p.color = buf.readInt();
            p.strokes = buf.readVarIntArray();
            p.cur = buf.readVarInt();
            p.bx = buf.readDouble();
            p.by = buf.readDouble();
            p.bz = buf.readDouble();
            p.lie = buf.readByte();
            p.holed = buf.readBoolean();
            p.finished = buf.readBoolean();
            p.powerShots = buf.readVarInt();
            p.lastTerm = buf.readByte() - 1;
            v.players.add(p);
        }
        return v;
    }
}
