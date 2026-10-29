package com.huntcraft.data;

import com.huntcraft.network.HCNetwork;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-player hunter progress (capability). Server is authoritative; a copy is synced to the owning
 * client (see {@link #markDirty()} / {@link HunterDataEvents}).
 */
public class HunterData {
    public static final int ITEM_BOX_SIZE = 108;
    public static final float BASE_MAX_STAMINA = 100.0F;
    public static final float STAMINA_CAP = 150.0F;
    public static final float STAMINA_FLOOR = 50.0F;

    // ---- persistent progress
    public int zenny = 0;
    public int hunterRank = 1;
    public int hrPoints = 0;
    public final Set<String> completedQuests = new HashSet<>();
    public final Map<String, Integer> questClears = new HashMap<>();
    public final Map<String, Integer> huntCounts = new HashMap<>();
    public final Map<String, Integer> captureCounts = new HashMap<>();
    /** monster id -> {smallest scale, largest scale} */
    public final Map<String, float[]> sizeRecords = new HashMap<>();
    public boolean starterKitReceived = false;
    public final NonNullList<ItemStack> itemBox = NonNullList.withSize(ITEM_BOX_SIZE, ItemStack.EMPTY);
    /** Free-form module data: each module stores under its own key (e.g. "companions", "meal", "quest"). */
    public CompoundTag moduleData = new CompoundTag();

    // ---- stamina (persisted so relogging does not refill)
    public float stamina = BASE_MAX_STAMINA;
    public float maxStamina = BASE_MAX_STAMINA;

    // ---- transient combat state (not saved)
    public int iframes;
    public int dodgeTicks;
    public int dodgeCooldown;
    public int dodgeDir;
    public int staminaDelay;
    public boolean exhausted;
    public int exhaustTicks;
    /** Set every tick by weapons that are guarding (0 = not guarding). Fraction of damage blocked. */
    public float guardPower;
    public int guardTicksLeft;
    public int sharpenTicks;
    public float lastStrengthScale = 1.0F;
    public int fireblightRolls;

    private boolean dirty = true;

    // ------------------------------------------------------------------ helpers
    public static HunterData get(Player player) {
        return player.getCapability(HCCapabilities.HUNTER).orElseGet(HunterData::new);
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public CompoundTag getModuleTag(String key) {
        if (!moduleData.contains(key, Tag.TAG_COMPOUND)) moduleData.put(key, new CompoundTag());
        return moduleData.getCompound(key);
    }

    public void addZenny(int amount) {
        zenny = Math.max(0, Math.min(99_999_999, zenny + amount));
        markDirty();
    }

    public boolean spendZenny(int amount) {
        if (zenny < amount) return false;
        zenny -= amount;
        markDirty();
        return true;
    }

    /** HR points needed to go from rank r to r+1. */
    public static int hrpForNext(int rank) {
        return 100 + rank * 60;
    }

    /** Adds HR points, levelling up. Returns number of ranks gained. */
    public int addHrPoints(int amount, int rankCap) {
        hrPoints += amount;
        int gained = 0;
        while (hunterRank < rankCap && hrPoints >= hrpForNext(hunterRank)) {
            hrPoints -= hrpForNext(hunterRank);
            hunterRank++;
            gained++;
        }
        if (hunterRank >= rankCap) hrPoints = Math.min(hrPoints, hrpForNext(hunterRank));
        markDirty();
        return gained;
    }

    public void recordHunt(String monsterId, float scale, boolean captured) {
        if (captured) captureCounts.merge(monsterId, 1, Integer::sum);
        else huntCounts.merge(monsterId, 1, Integer::sum);
        float[] rec = sizeRecords.get(monsterId);
        if (rec == null) sizeRecords.put(monsterId, new float[]{scale, scale});
        else {
            rec[0] = Math.min(rec[0], scale);
            rec[1] = Math.max(rec[1], scale);
        }
        markDirty();
    }

    public int totalHunted(String monsterId) {
        return huntCounts.getOrDefault(monsterId, 0) + captureCounts.getOrDefault(monsterId, 0);
    }

    public boolean consumeStamina(float amount) {
        if (stamina <= 0) return false;
        stamina = Math.max(0, stamina - amount);
        staminaDelay = 20;
        if (stamina <= 0) {
            exhausted = true;
            exhaustTicks = 50;
        }
        return true;
    }

    public void restoreMaxStamina(float amount) {
        maxStamina = Math.min(STAMINA_CAP, maxStamina + amount);
        stamina = Math.min(maxStamina, stamina + amount);
        markDirty();
    }

    public boolean isDodging() {
        return dodgeTicks > 0;
    }

    public boolean isInvulnerable() {
        return iframes > 0;
    }

    // ------------------------------------------------------------------ serialization
    public CompoundTag save(boolean includeItemBox) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("zenny", zenny);
        tag.putInt("hr", hunterRank);
        tag.putInt("hrp", hrPoints);
        ListTag done = new ListTag();
        for (String s : completedQuests) done.add(StringTag.valueOf(s));
        tag.put("completed", done);
        tag.put("clears", mapToTag(questClears));
        tag.put("hunts", mapToTag(huntCounts));
        tag.put("captures", mapToTag(captureCounts));
        CompoundTag sizes = new CompoundTag();
        sizeRecords.forEach((k, v) -> {
            CompoundTag t = new CompoundTag();
            t.putFloat("min", v[0]);
            t.putFloat("max", v[1]);
            sizes.put(k, t);
        });
        tag.put("sizes", sizes);
        tag.putBoolean("starter", starterKitReceived);
        tag.put("modules", moduleData.copy());
        tag.putFloat("stamina", stamina);
        tag.putFloat("maxStamina", maxStamina);
        if (includeItemBox) {
            CompoundTag box = new CompoundTag();
            ContainerHelper.saveAllItems(box, itemBox);
            tag.put("itemBox", box);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        zenny = tag.getInt("zenny");
        hunterRank = Math.max(1, tag.getInt("hr"));
        hrPoints = tag.getInt("hrp");
        completedQuests.clear();
        ListTag done = tag.getList("completed", Tag.TAG_STRING);
        for (int i = 0; i < done.size(); i++) completedQuests.add(done.getString(i));
        tagToMap(tag.getCompound("clears"), questClears);
        tagToMap(tag.getCompound("hunts"), huntCounts);
        tagToMap(tag.getCompound("captures"), captureCounts);
        sizeRecords.clear();
        CompoundTag sizes = tag.getCompound("sizes");
        for (String k : sizes.getAllKeys()) {
            CompoundTag t = sizes.getCompound(k);
            sizeRecords.put(k, new float[]{t.getFloat("min"), t.getFloat("max")});
        }
        starterKitReceived = tag.getBoolean("starter");
        moduleData = tag.getCompound("modules").copy();
        if (tag.contains("maxStamina")) {
            maxStamina = Math.max(STAMINA_FLOOR, tag.getFloat("maxStamina"));
            stamina = Math.min(maxStamina, tag.getFloat("stamina"));
        }
        if (tag.contains("itemBox")) {
            for (int i = 0; i < itemBox.size(); i++) itemBox.set(i, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound("itemBox"), itemBox);
        }
    }

    public void copyFrom(HunterData other) {
        load(other.save(true));
    }

    private static CompoundTag mapToTag(Map<String, Integer> map) {
        CompoundTag t = new CompoundTag();
        map.forEach(t::putInt);
        return t;
    }

    private static void tagToMap(CompoundTag t, Map<String, Integer> map) {
        map.clear();
        for (String k : t.getAllKeys()) map.put(k, t.getInt(k));
    }

    /** Sends the client copy now. */
    public void syncTo(ServerPlayer player) {
        HCNetwork.sendToPlayer(player, "core/hunter_data", save(false));
        dirty = false;
        lastSentStamina = stamina;
        lastSentExhausted = exhausted;
    }

    public float lastSentStamina = -1;
    public boolean lastSentExhausted = false;

    /** Lightweight stamina-only sync (sent frequently). */
    public void syncStamina(ServerPlayer player) {
        CompoundTag t = new CompoundTag();
        t.putFloat("s", stamina);
        t.putFloat("m", maxStamina);
        t.putBoolean("e", exhausted);
        HCNetwork.sendToPlayer(player, "core/stamina", t);
        lastSentStamina = stamina;
        lastSentExhausted = exhausted;
    }
}
