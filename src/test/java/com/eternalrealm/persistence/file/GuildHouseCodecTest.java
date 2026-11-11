package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.guild.house.BoardNote;
import com.eternalrealm.guild.house.GuildHouse;
import com.eternalrealm.guild.house.StorageLogEntry;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GuildHouseCodecTest {
    private static final Instant AT = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    public void roundTrip() throws Exception {
        UUID a = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID b = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID p = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
        GuildHouse owned = new GuildHouse(a, "great_tree", AT, 1200, "Raid tonight",
                List.of(new BoardNote(3, p, "Alice", "selling iron \"cheap\"", AT)),
                List.of(new StorageLogEntry(AT, p, "Alice", "minecraft:iron_ingot", 12, true, "chest.2"),
                        new StorageLogEntry(AT.plusSeconds(5), p, "Alice", "minecraft:bread", 3, false, "vault.1")), 4);
        GuildHouse fundOnly = new GuildHouse(b, null, null, 50, "", List.of(), List.of(), 1);
        List<GuildHouse> houses = List.of(owned, fundOnly);
        assertEquals(houses, GuildHouseCodec.decode(GuildHouseCodec.encode(houses)));
        assertEquals(List.of(), GuildHouseCodec.decode(GuildHouseCodec.encode(List.of())));
    }

    @Test
    public void corruptDataIsReported() throws IncompatibleDataException {
        for (String bad : List.of("{\"schema\":1}", "{\"schema\":1,\"houses\":[{}]}",
                "{\"schema\":1,\"houses\":[{\"guild\":\"x\",\"fund\":0,\"motd\":\"\",\"nextId\":1,\"notes\":[],\"log\":[]}]}",
                "{\"schema\":1,\"houses\":[{\"guild\":\"00000000-0000-4000-8000-000000000001\",\"fund\":-5,"
                        + "\"motd\":\"\",\"nextId\":1,\"notes\":[],\"log\":[]}]}")) {
            try {
                GuildHouseCodec.decode(bad);
                fail("accepted " + bad);
            } catch (CorruptDataException expected) {
                // reported as corruption, never as an empty directory
            }
        }
    }
}
