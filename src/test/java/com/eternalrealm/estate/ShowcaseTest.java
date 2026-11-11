package com.eternalrealm.estate;

import com.eternalrealm.guild.house.GuildHouseCatalog;
import com.eternalrealm.guild.house.GuildHouseDesign;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ShowcaseTest {
    private static List<String> roomDesigns() {
        List<String> ids = new ArrayList<>();
        for (RoomDesign design : RoomDesign.values()) {
            ids.add(design.id());
        }
        return ids;
    }

    /** The shipped guild house designs (read from the source tree: the offline runner has no resources). */
    private static List<String> guildDesigns() {
        try (Reader reader = Files.newBufferedReader(Paths.get("src", "main", "resources", "data", "eternalrealm",
                "guildhouse", "designs.json"), StandardCharsets.UTF_8)) {
            return GuildHouseCatalog.parse(reader).designs().stream().map(GuildHouseDesign::id).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void everyRoomAndGuildHouseHasItsOwnFixedShowcase() {
        Set<UUID> owners = new HashSet<>();
        int count = 0;
        for (String kind : List.of(EstateKey.ROOM, EstateKey.GUILD)) {
            for (String design : kind.equals(EstateKey.ROOM) ? roomDesigns() : guildDesigns()) {
                UUID owner = Showcase.owner(kind, design);
                assertEquals("the same after a restart", owner, Showcase.owner(kind, design));
                assertEquals("a name-based id", 3, owner.version());
                assertTrue(kind + "/" + design + " shares its owner", owners.add(owner));
                EstateKey key = Showcase.key(kind, design);
                assertEquals(new EstateKey(kind, owner, design), key);
                assertTrue(Showcase.is(key));
                count++;
            }
        }
        assertEquals("5 rooms and 20 guild houses", 25, count);
    }

    @Test
    public void playersAndGuildsNeverOwnAShowcase() {
        UUID random = UUID.randomUUID();
        UUID offlinePlayer = UUID.nameUUIDFromBytes("OfflinePlayer:garret".getBytes(StandardCharsets.UTF_8));
        for (UUID owner : List.of(random, offlinePlayer)) {
            assertFalse(Showcase.is(new EstateKey(EstateKey.ROOM, owner, "garret")));
            assertFalse(Showcase.is(new EstateKey(EstateKey.GUILD, owner, "dragon_lair")));
        }
        // the owner names one design of one kind only
        UUID garret = Showcase.owner(EstateKey.ROOM, "garret");
        assertFalse(Showcase.is(new EstateKey(EstateKey.ROOM, garret, "canal")));
        assertFalse(Showcase.is(new EstateKey(EstateKey.GUILD, garret, "garret")));
        assertFalse(Showcase.is(null));
    }

    @Test
    public void theDesignOfAShowcaseOwnerIsFoundAmongTheDesigns() {
        List<String> guild = guildDesigns();
        for (String design : guild) {
            assertEquals(Optional.of(design), Showcase.designOf(EstateKey.GUILD, Showcase.owner(EstateKey.GUILD, design),
                    guild));
        }
        assertEquals(Optional.empty(), Showcase.designOf(EstateKey.GUILD, UUID.randomUUID(), guild));
        assertEquals("a room's showcase is no guild house", Optional.empty(),
                Showcase.designOf(EstateKey.GUILD, Showcase.owner(EstateKey.ROOM, "garret"), guild));
        assertEquals(Optional.empty(), Showcase.designOf(EstateKey.GUILD, null, guild));
        assertEquals(Optional.empty(), Showcase.designOf("Bad Kind", UUID.randomUUID(), guild));
    }

    @Test
    public void badNamesAreRefused() {
        for (String[] bad : new String[][]{{"room", "Grand Suite"}, {"", "garret"}, {"room", null}}) {
            try {
                Showcase.owner(bad[0], bad[1]);
                fail("accepted " + bad[0] + "/" + bad[1]);
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
    }
}
