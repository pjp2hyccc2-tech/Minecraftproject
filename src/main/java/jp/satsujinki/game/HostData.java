package jp.satsujinki.game;

import jp.satsujinki.SatsujinkiConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent per-host statistics (points, kills, records), stored with the overworld. */
public class HostData extends SavedData {
    public static final String NAME = "satsujinki_hosts";

    public static final class Stats {
        public int points;
        public int totalKills;
        public int[] styleKills = new int[KillStyle.values().length];
        public int maxCombo;
        public int bestRank;
        public int roundsPlayed;
        public int roundsPerfect;
        public boolean starterGiven;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Points", points);
            t.putInt("Kills", totalKills);
            t.putIntArray("StyleKills", styleKills);
            t.putInt("MaxCombo", maxCombo);
            t.putInt("BestRank", bestRank);
            t.putInt("Rounds", roundsPlayed);
            t.putInt("Perfect", roundsPerfect);
            t.putBoolean("Starter", starterGiven);
            return t;
        }

        static Stats load(CompoundTag t) {
            Stats s = new Stats();
            s.points = t.getInt("Points");
            s.totalKills = t.getInt("Kills");
            int[] arr = t.getIntArray("StyleKills");
            System.arraycopy(arr, 0, s.styleKills, 0, Math.min(arr.length, s.styleKills.length));
            s.maxCombo = t.getInt("MaxCombo");
            s.bestRank = t.getInt("BestRank");
            s.roundsPlayed = t.getInt("Rounds");
            s.roundsPerfect = t.getInt("Perfect");
            s.starterGiven = t.getBoolean("Starter");
            return s;
        }
    }

    private final Map<UUID, Stats> stats = new HashMap<>();

    public HostData() {
    }

    public static HostData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HostData::load, HostData::new, NAME);
    }

    public Stats of(UUID id) {
        return stats.computeIfAbsent(id, k -> {
            Stats s = new Stats();
            s.points = SatsujinkiConfig.startingPoints();
            setDirty();
            return s;
        });
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Stats> e : stats.entrySet()) {
            CompoundTag t = e.getValue().save();
            t.putUUID("Id", e.getKey());
            list.add(t);
        }
        tag.put("Hosts", list);
        return tag;
    }

    public static HostData load(CompoundTag tag) {
        HostData d = new HostData();
        ListTag list = tag.getList("Hosts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.hasUUID("Id")) d.stats.put(t.getUUID("Id"), Stats.load(t));
        }
        return d;
    }
}
