package com.clockworkdiver.data;

import com.clockworkdiver.clock.ClockData;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

/** Everything the mod remembers about one player: workshop slot, tutorial, commission, upgrades. */
public final class PlayerProgress {
    public static final int HINT_WORKSHOP = 1;
    public static final int HINT_BENCH = 1 << 1;
    public static final int HINT_DIVE = 1 << 2;
    public static final int HINT_RUST = 1 << 3;
    public static final int HINT_TEETH = 1 << 4;
    public static final int HINT_SCREW = 1 << 5;
    public static final int HINT_SPRING = 1 << 6;
    public static final int HINT_NEST = 1 << 7;
    public static final int HINT_STEAM = 1 << 8;
    public static final int HINT_DELIVER = 1 << 9;

    public final UUID id;
    public int slot = -1;
    public boolean started;
    public boolean workshopBuilt;
    public int workshopVersion;
    public boolean tutorialDone;
    public boolean gotKey;
    public int reputation;
    public int repairedClocks;
    public int coinsEarned;
    public int wrenchLevel;
    public int steamLevel;
    public int lensLevel;
    @Nullable
    public ClockData activeClock;
    /** True once the customer handed the clock over (the player accepted the job). */
    public boolean accepted;
    @Nullable
    public UUID customerId;
    public long nextCustomerTime;
    public long commissionCounter;
    public int hintsSeen;
    @Nullable
    public String returnDim;
    public double returnX;
    public double returnY;
    public double returnZ;
    public float returnYaw;
    /** Not saved: how many workshop checks in a row found the customer missing. */
    public int missingCustomerChecks;

    public PlayerProgress(UUID id) {
        this.id = id;
    }

    public boolean seen(int hint) {
        return (hintsSeen & hint) != 0;
    }

    /** Marks a hint as seen and returns true if it was new. */
    public boolean markSeen(int hint) {
        if (seen(hint)) return false;
        hintsSeen |= hint;
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putInt("Slot", slot);
        tag.putBoolean("Started", started);
        tag.putBoolean("WorkshopBuilt", workshopBuilt);
        tag.putInt("WorkshopVersion", workshopVersion);
        tag.putBoolean("TutorialDone", tutorialDone);
        tag.putBoolean("GotKey", gotKey);
        tag.putInt("Reputation", reputation);
        tag.putInt("Repaired", repairedClocks);
        tag.putInt("Coins", coinsEarned);
        tag.putInt("WrenchLevel", wrenchLevel);
        tag.putInt("SteamLevel", steamLevel);
        tag.putInt("LensLevel", lensLevel);
        if (activeClock != null) tag.put("ActiveClock", activeClock.save());
        tag.putBoolean("Accepted", accepted);
        if (customerId != null) tag.putUUID("Customer", customerId);
        tag.putLong("NextCustomer", nextCustomerTime);
        tag.putLong("CommissionCounter", commissionCounter);
        tag.putInt("Hints", hintsSeen);
        if (returnDim != null) {
            tag.putString("ReturnDim", returnDim);
            tag.putDouble("ReturnX", returnX);
            tag.putDouble("ReturnY", returnY);
            tag.putDouble("ReturnZ", returnZ);
            tag.putFloat("ReturnYaw", returnYaw);
        }
        return tag;
    }

    public static PlayerProgress load(CompoundTag tag) {
        PlayerProgress p = new PlayerProgress(tag.getUUID("Id"));
        p.slot = tag.getInt("Slot");
        p.started = tag.getBoolean("Started");
        p.workshopBuilt = tag.getBoolean("WorkshopBuilt");
        p.workshopVersion = tag.getInt("WorkshopVersion");
        p.tutorialDone = tag.getBoolean("TutorialDone");
        p.gotKey = tag.getBoolean("GotKey");
        p.reputation = tag.getInt("Reputation");
        p.repairedClocks = tag.getInt("Repaired");
        p.coinsEarned = tag.getInt("Coins");
        p.wrenchLevel = tag.getInt("WrenchLevel");
        p.steamLevel = tag.getInt("SteamLevel");
        p.lensLevel = tag.getInt("LensLevel");
        p.activeClock = tag.contains("ActiveClock") ? ClockData.load(tag.getCompound("ActiveClock")) : null;
        p.accepted = tag.getBoolean("Accepted");
        p.customerId = tag.hasUUID("Customer") ? tag.getUUID("Customer") : null;
        p.nextCustomerTime = tag.getLong("NextCustomer");
        p.commissionCounter = tag.getLong("CommissionCounter");
        p.hintsSeen = tag.getInt("Hints");
        if (tag.contains("ReturnDim")) {
            p.returnDim = tag.getString("ReturnDim");
            p.returnX = tag.getDouble("ReturnX");
            p.returnY = tag.getDouble("ReturnY");
            p.returnZ = tag.getDouble("ReturnZ");
            p.returnYaw = tag.getFloat("ReturnYaw");
        }
        return p;
    }
}
