package com.eternalrealm.network.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.season.SeasonId;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * S2C: the authoritative state of the receiving player's own character (HUD, journal), plus the stats the job and the
 * equipment add on top of it.
 */
public record CharacterSnapshotPacket(CharacterSnapshot snapshot, StatBlock bonusStats) {
    static final int MAX_BYTES = 12288;
    static final int MAX_SKILLS = 128;

    public CharacterSnapshotPacket {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(bonusStats, "bonusStats");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(snapshot.seasonId().value(), SeasonId.MAX_LENGTH);
        buffer.writeVarInt(snapshot.level());
        buffer.writeVarInt(snapshot.levelCap());
        buffer.writeVarLong(snapshot.experience());
        buffer.writeVarLong(snapshot.experienceToNext());
        buffer.writeVarInt(snapshot.unspentStatPoints());
        writeStats(buffer, snapshot.allocatedStats());
        writeStats(buffer, snapshot.totalStats());
        buffer.writeUtf(snapshot.jobId(), 32);
        buffer.writeVarLong(snapshot.gold());
        buffer.writeVarInt(snapshot.unspentSkillPoints());
        buffer.writeVarInt(snapshot.skillInvestments().size());
        snapshot.skillInvestments().forEach((id, points) -> {
            buffer.writeUtf(id, 64);
            buffer.writeVarInt(points);
        });
        writeStats(buffer, bonusStats);
        buffer.writeVarInt(snapshot.skillLoadout().size());
        for (String id : snapshot.skillLoadout()) {
            buffer.writeUtf(id, 64);
        }
    }

    public static CharacterSnapshotPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "CharacterSnapshotPacket");
        try {
            SeasonId season = new SeasonId(buffer.readUtf(SeasonId.MAX_LENGTH));
            int level = buffer.readVarInt();
            int levelCap = buffer.readVarInt();
            long experience = buffer.readVarLong();
            long experienceToNext = buffer.readVarLong();
            int unspent = buffer.readVarInt();
            StatBlock allocated = readStats(buffer);
            StatBlock total = readStats(buffer);
            String jobId = buffer.readUtf(32);
            long gold = buffer.readVarLong();
            int skillPoints = buffer.readVarInt();
            int skillCount = buffer.readVarInt();
            if (skillCount < 0 || skillCount > MAX_SKILLS) {
                throw new DecoderException("Too many skills: " + skillCount);
            }
            Map<String, Integer> skills = new java.util.LinkedHashMap<>();
            for (int i = 0; i < skillCount; i++) {
                skills.put(buffer.readUtf(64), buffer.readVarInt());
            }
            StatBlock bonus = readStats(buffer);
            int loadoutSize = buffer.readVarInt();
            if (loadoutSize < 0 || loadoutSize > com.eternalrealm.character.CharacterRecord.MAX_LOADOUT_SLOTS) {
                throw new DecoderException("Too many loadout slots: " + loadoutSize);
            }
            java.util.List<String> loadout = new java.util.ArrayList<>(loadoutSize);
            for (int i = 0; i < loadoutSize; i++) {
                loadout.add(buffer.readUtf(64));
            }
            return new CharacterSnapshotPacket(new CharacterSnapshot(season, level, levelCap, experience,
                    experienceToNext, unspent, allocated, total, jobId, gold, skillPoints, skills, loadout), bonus);
        } catch (IllegalArgumentException e) {
            throw new DecoderException("Invalid CharacterSnapshotPacket: " + e.getMessage(), e);
        }
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(CharacterSnapshotPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onCharacterSnapshot(packet);
    }

    private static void writeStats(FriendlyByteBuf buffer, StatBlock stats) {
        for (StatType type : StatType.values()) {
            buffer.writeVarInt(stats.get(type));
        }
    }

    private static StatBlock readStats(FriendlyByteBuf buffer) {
        Map<StatType, Integer> values = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            values.put(type, buffer.readVarInt());
        }
        return StatBlock.of(values);
    }
}
