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
 * S2C: the authoritative state of the receiving player's own character (HUD, character screen).
 */
public record CharacterSnapshotPacket(CharacterSnapshot snapshot) {
    static final int MAX_BYTES = 512;

    public CharacterSnapshotPacket {
        Objects.requireNonNull(snapshot, "snapshot");
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
            return new CharacterSnapshotPacket(new CharacterSnapshot(season, level, levelCap, experience,
                    experienceToNext, unspent, allocated, total));
        } catch (IllegalArgumentException e) {
            throw new DecoderException("Invalid CharacterSnapshotPacket: " + e.getMessage(), e);
        }
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(CharacterSnapshotPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onCharacterSnapshot(packet.snapshot());
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
