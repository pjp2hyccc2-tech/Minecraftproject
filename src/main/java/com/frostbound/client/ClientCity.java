package com.frostbound.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** What the client knows about the city: the last snapshot from the server plus transient UI state. */
public final class ClientCity {
    public record Toast(Component text, int color, long until) {
    }

    private static CompoundTag snap = new CompoundTag();
    private static long syncedAt;
    private static final Deque<Toast> toasts = new ArrayDeque<>();
    private static byte[] heatGrid;
    private static int heatCx;
    private static int heatCz;
    private static int heatHalf;
    private static int heatCell;
    private static long heatAt;
    private static CompoundTag citizens;

    private ClientCity() {
    }

    public static void reset() {
        snap = new CompoundTag();
        syncedAt = 0;
        toasts.clear();
        heatGrid = null;
        citizens = null;
    }

    public static void applySync(CompoundTag tag) {
        snap = tag;
        syncedAt = Util.getMillis();
    }

    public static CompoundTag snap() {
        return snap;
    }

    /** In a FROSTBOUND world (founded or not). */
    public static boolean frostbound() {
        return snap.getBoolean("frostbound");
    }

    /** A founded city exists. */
    public static boolean hasCity() {
        return snap.contains("day");
    }

    public static boolean stale() {
        return Util.getMillis() - syncedAt > 5000;
    }

    public static CompoundTag generator() {
        return snap.getCompound("generator");
    }

    public static CompoundTag pop() {
        return snap.getCompound("pop");
    }

    public static ListTag buildings() {
        return snap.getList("buildings", Tag.TAG_COMPOUND);
    }

    public static CompoundTag building(int id) {
        ListTag list = buildings();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            if (b.getInt("id") == id) {
                return b;
            }
        }
        return null;
    }

    public static CompoundTag resource(String id) {
        ListTag list = snap.getList("resources", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag r = list.getCompound(i);
            if (r.getString("id").equals(id)) {
                return r;
            }
        }
        return new CompoundTag();
    }

    public static double amount(String id) {
        return resource(id).getDouble("amount");
    }

    public static List<String> strings(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        List<String> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    public static boolean unlocked(String typeId) {
        return strings(snap, "unlocked").contains(typeId);
    }

    public static CompoundTag event() {
        return snap.contains("event") ? snap.getCompound("event") : null;
    }

    public static boolean ended() {
        String p = snap.getString("phase");
        return p.equals("ENDED") || p.equals("FAILED");
    }

    // ------------------------------------------------------------------ messages

    /** A message tag {key, args[]} as text; args starting with '#' are language keys themselves. */
    public static MutableComponent msg(CompoundTag tag) {
        return tr(tag.getString("key"), strings(tag, "args"));
    }

    public static MutableComponent tr(String key, List<String> args) {
        Object[] a = new Object[args.size()];
        for (int i = 0; i < a.length; i++) {
            String s = args.get(i);
            a[i] = s.startsWith("#") ? Component.translatable(s.substring(1)) : s;
        }
        return Component.translatable(key, a);
    }

    // ------------------------------------------------------------------ toasts

    public static void toast(Component text, int color) {
        toasts.addLast(new Toast(text, color, Util.getMillis() + 6000));
        while (toasts.size() > 6) {
            toasts.removeFirst();
        }
    }

    public static List<Toast> toasts() {
        long now = Util.getMillis();
        for (Iterator<Toast> it = toasts.iterator(); it.hasNext();) {
            if (it.next().until() < now) {
                it.remove();
            }
        }
        return new ArrayList<>(toasts);
    }

    // ------------------------------------------------------------------ heat grid and citizens

    public static void applyHeatGrid(CompoundTag tag) {
        heatGrid = tag.getByteArray("grid");
        heatCx = tag.getInt("cx");
        heatCz = tag.getInt("cz");
        heatHalf = tag.getInt("half");
        heatCell = Math.max(1, tag.getInt("cell"));
        heatAt = Util.getMillis();
    }

    public static byte[] heatGrid() {
        return heatGrid;
    }

    public static int heatCx() {
        return heatCx;
    }

    public static int heatCz() {
        return heatCz;
    }

    public static int heatHalf() {
        return heatHalf;
    }

    public static int heatCell() {
        return heatCell;
    }

    public static long heatAge() {
        return heatGrid == null ? Long.MAX_VALUE : Util.getMillis() - heatAt;
    }

    public static void applyCitizens(CompoundTag tag) {
        citizens = tag;
    }

    public static CompoundTag citizens() {
        return citizens;
    }
}
