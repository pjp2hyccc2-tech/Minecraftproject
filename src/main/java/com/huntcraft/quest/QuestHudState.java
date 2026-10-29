package com.huntcraft.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Client copy of the quest HUD state (written by the client network handlers, read by the HUD / screens and by
 * {@link com.huntcraft.core.HuntHooks#questRank} on the client side). Plain data, safe to load on both sides.
 */
public final class QuestHudState {
    public record Marker(double x, double z, String id, boolean enraged, boolean dead, boolean capturable) {
    }

    public record Mate(double x, double z, String name) {
    }

    /** Area label for the map screen (world coordinates). */
    public record AreaInfo(int n, String key, int x, int z, int climate) {
    }

    public static boolean active;
    public static String questId = "";
    public static String mapId = "";
    public static int state;
    public static int remainingTicks;
    public static long syncedAt;
    public static int faints;
    public static int maxFaints = 3;
    public static int centerX, centerZ, half = QuestRegions.HALF;
    public static int campX, campY, campZ;
    public static int[] progress = new int[0];
    public static int[] targetAreas = new int[0];
    public static int rank;
    public static boolean leader;
    public static String reason = "";
    public static int area;
    public static int climate;
    public static final List<Marker> MONSTERS = new ArrayList<>();
    public static final List<Mate> PARTY = new ArrayList<>();
    public static final List<AreaInfo> AREAS = new ArrayList<>();

    private QuestHudState() {
    }

    public static void reset() {
        active = false;
        questId = "";
        progress = new int[0];
        targetAreas = new int[0];
        area = 0;
        climate = 0;
        MONSTERS.clear();
        PARTY.clear();
    }

    /** Applies a "quest/info" packet (static per quest). */
    public static void readInfo(CompoundTag t) {
        questId = t.getString("q");
        mapId = t.getString("map");
        centerX = t.getInt("cx");
        centerZ = t.getInt("cz");
        half = t.contains("half") ? t.getInt("half") : QuestRegions.HALF;
        campX = t.getInt("kx");
        campY = t.getInt("ky");
        campZ = t.getInt("kz");
        AREAS.clear();
        ListTag l = t.getList("areas", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            AREAS.add(new AreaInfo(c.getInt("n"), c.getString("k"), c.getInt("x"), c.getInt("z"), c.getInt("c")));
        }
    }

    /** Applies a "quest/hud" packet (every second). */
    public static void readHud(CompoundTag t, long clientTime) {
        if (!t.getBoolean("active")) {
            reset();
            return;
        }
        active = true;
        questId = t.getString("q");
        if (t.contains("map")) mapId = t.getString("map");
        state = t.getInt("st");
        remainingTicks = t.getInt("rem");
        syncedAt = clientTime;
        faints = t.getInt("f");
        maxFaints = Math.max(1, t.getInt("mf"));
        progress = t.getIntArray("p");
        targetAreas = t.getIntArray("ta");
        rank = t.getInt("rank");
        reason = t.getString("reason");
        leader = t.getBoolean("leader");
        area = t.getInt("ar");
        climate = t.getInt("cl");
        MONSTERS.clear();
        ListTag m = t.getList("m", Tag.TAG_COMPOUND);
        for (int i = 0; i < m.size(); i++) {
            CompoundTag c = m.getCompound(i);
            MONSTERS.add(new Marker(c.getDouble("x"), c.getDouble("z"), c.getString("id"), c.getBoolean("e"), c.getBoolean("d"), c.getBoolean("c")));
        }
        PARTY.clear();
        ListTag p = t.getList("party", Tag.TAG_COMPOUND);
        for (int i = 0; i < p.size(); i++) {
            CompoundTag c = p.getCompound(i);
            PARTY.add(new Mate(c.getDouble("x"), c.getDouble("z"), c.getString("n")));
        }
    }

    /** Remaining ticks now (interpolated between syncs). */
    public static int remainingNow(long clientTime) {
        return (int) Math.max(0, remainingTicks - Math.max(0, clientTime - syncedAt));
    }

    public static String areaKey(int n) {
        for (AreaInfo a : AREAS) if (a.n() == n) return a.key();
        return "";
    }
}
