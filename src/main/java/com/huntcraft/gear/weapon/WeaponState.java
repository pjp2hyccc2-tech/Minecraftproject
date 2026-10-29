package com.huntcraft.gear.weapon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Transient per-player combat state of the weapon system (combo, gauges, horn notes, song buffs, sharpening...).
 * The logical server owns the real state; the owning client keeps a mirror updated by "weapons/state" packets
 * (plus a few fields it sets itself, like the current use mode).
 */
public final class WeaponState {
    public static final int MODE_NONE = 0;
    public static final int MODE_CHARGE = 1;
    public static final int MODE_GUARD = 2;

    private static final Map<UUID, WeaponState> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, WeaponState> CLIENT = new ConcurrentHashMap<>();

    public static WeaponState get(Player p) {
        return (p.level().isClientSide ? CLIENT : SERVER).computeIfAbsent(p.getUUID(), k -> new WeaponState());
    }

    public static void removeServer(Player p) {
        SERVER.remove(p.getUUID());
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    // ---- left click combo
    public int combo;
    public long lastSwing = -1000;
    /** Set while a special attack runs (gauges are not filled by specials). */
    public boolean special;

    // ---- right click use
    public int useMode = MODE_NONE;
    public int chargeReached = -1;

    // ---- long sword
    public float spirit;
    public int spiritLevel;
    public int spiritLevelTicks;
    public int spiritStep;
    public long lastSpiritTime = -1000;
    public long lastHitTime = -1000;
    public int foresightTicks;
    public boolean foresightDodged;
    public final Set<Integer> foresightPre = new HashSet<>();

    // ---- dual blades
    public boolean demon;
    public boolean archdemon;
    public float demonGauge;

    // ---- lance
    public int dashTicks;
    public long lastGuardHit = -1000;
    public int counterCooldown;

    // ---- hunting horn
    public final int[] notes = new int[4];
    public int noteCount;

    // ---- sharpening (-99 = none, -1 = off hand, 0..35 inventory slot)
    public int sharpenSlot = -99;
    public int sharpenTotal;
    public int sharpenLeft;

    // ---- song buffs (remaining ticks / level per HornSongs effect)
    public final int[] buffTicks = new int[HornSongs.EFFECT_COUNT];
    public final int[] buffLevel = new int[HornSongs.EFFECT_COUNT];

    // ---- server bookkeeping
    public final List<Scheduled> scheduled = new ArrayList<>();
    @Nullable
    public Item lastHeld;
    public boolean dirty = true;
    public int syncCooldown;

    public record Scheduled(long due, Consumer<ServerPlayer> action) {
    }

    public void schedule(Player p, int delay, Consumer<ServerPlayer> action) {
        scheduled.add(new Scheduled(p.level().getGameTime() + Math.max(0, delay), action));
    }

    public void markDirty() {
        dirty = true;
    }

    // ------------------------------------------------------------------ horn notes
    public void addNote(int note) {
        if (noteCount < notes.length) {
            notes[noteCount++] = note;
        } else {
            System.arraycopy(notes, 1, notes, 0, notes.length - 1);
            notes[notes.length - 1] = note;
        }
        dirty = true;
    }

    public void clearNotes() {
        noteCount = 0;
        for (int i = 0; i < notes.length; i++) notes[i] = 0;
        dirty = true;
    }

    public boolean anyBuff() {
        for (int t : buffTicks) if (t > 0) return true;
        return false;
    }

    // ------------------------------------------------------------------ resets
    /** Clears weapon specific transient state when the held weapon changes. */
    public void resetWeaponState() {
        combo = 0;
        useMode = MODE_NONE;
        chargeReached = -1;
        spiritStep = 0;
        foresightTicks = 0;
        foresightDodged = false;
        foresightPre.clear();
        demon = false;
        dashTicks = 0;
        counterCooldown = 0;
        scheduled.clear();
        special = false;
        dirty = true;
    }

    // ------------------------------------------------------------------ sync (server -> owning client)
    public CompoundTag toSyncTag() {
        CompoundTag t = new CompoundTag();
        t.putFloat("sp", spirit);
        t.putInt("sl", spiritLevel);
        t.putInt("slt", spiritLevelTicks);
        t.putInt("ss", spiritStep);
        t.putInt("ft", foresightTicks);
        t.putBoolean("dm", demon);
        t.putBoolean("ad", archdemon);
        t.putFloat("dg", demonGauge);
        t.putIntArray("nt", notes.clone());
        t.putInt("nc", noteCount);
        t.putInt("sh", sharpenLeft);
        t.putInt("st", sharpenTotal);
        t.putIntArray("bt", buffTicks.clone());
        t.putIntArray("bl", buffLevel.clone());
        return t;
    }

    public void readSyncTag(CompoundTag t) {
        spirit = t.getFloat("sp");
        spiritLevel = t.getInt("sl");
        spiritLevelTicks = t.getInt("slt");
        spiritStep = t.getInt("ss");
        foresightTicks = t.getInt("ft");
        demon = t.getBoolean("dm");
        archdemon = t.getBoolean("ad");
        demonGauge = t.getFloat("dg");
        int[] nt = t.getIntArray("nt");
        for (int i = 0; i < notes.length; i++) notes[i] = i < nt.length ? nt[i] : 0;
        noteCount = Math.min(notes.length, t.getInt("nc"));
        sharpenLeft = t.getInt("sh");
        sharpenTotal = t.getInt("st");
        int[] bt = t.getIntArray("bt");
        int[] bl = t.getIntArray("bl");
        for (int i = 0; i < buffTicks.length; i++) {
            buffTicks[i] = i < bt.length ? bt[i] : 0;
            buffLevel[i] = i < bl.length ? bl[i] : 0;
        }
    }
}
