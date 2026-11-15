package com.eternalrealm.guild.battle.forge;

import com.eternalrealm.guild.battle.BattleRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The guilds' battle records (wins, draws, losses, knock-outs), saved with the overworld as
 * data/eternalrealm_guild_battles.dat. Server thread only.
 */
public final class GuildBattleData extends SavedData {
    private static final String NAME = "eternalrealm_guild_battles";

    private final Map<UUID, BattleRecord> records = new LinkedHashMap<>();

    public GuildBattleData() {
    }

    public static GuildBattleData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(GuildBattleData::load, GuildBattleData::new, NAME);
    }

    static GuildBattleData load(CompoundTag tag) {
        GuildBattleData data = new GuildBattleData();
        CompoundTag all = tag.getCompound("records");
        for (String key : all.getAllKeys()) {
            try {
                CompoundTag r = all.getCompound(key);
                data.records.put(UUID.fromString(key), new BattleRecord(Math.max(0, r.getInt("w")),
                        Math.max(0, r.getInt("d")), Math.max(0, r.getInt("l")), Math.max(0, r.getInt("k"))));
            } catch (IllegalArgumentException e) {
                // a broken entry is dropped, the rest stays
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        records.forEach((guild, record) -> {
            CompoundTag r = new CompoundTag();
            r.putInt("w", record.wins());
            r.putInt("d", record.draws());
            r.putInt("l", record.losses());
            r.putInt("k", record.knockOuts());
            all.put(guild.toString(), r);
        });
        tag.put("records", all);
        return tag;
    }

    public BattleRecord record(UUID guild) {
        return records.getOrDefault(guild, BattleRecord.NONE);
    }

    public void put(UUID guild, BattleRecord record) {
        records.put(guild, record);
        setDirty();
    }

    /** A disbanded guild's record goes with it. */
    public void forget(UUID guild) {
        if (records.remove(guild) != null) {
            setDirty();
        }
    }
}
