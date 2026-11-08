package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.economy.MarketData;
import com.eternalrealm.guild.Guild;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.testutil.MutableClock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SocialRepositoriesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path root;
    private FileGuildRepository guilds;
    private FileMarketRepository markets;

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("eternalrealm").toPath();
        AtomicFileStore store = new AtomicFileStore(root.resolve("quarantine"),
                MutableClock.startingAt("2026-11-08T00:00:00Z"));
        guilds = new FileGuildRepository(root, store);
        markets = new FileMarketRepository(root, store);
    }

    @Test
    public void guildsAreOnePermanentDocumentAtTheDataRoot() throws Exception {
        assertEquals("nothing saved yet", List.of(), guilds.loadAll());
        List<Guild> directory = List.of(GuildCodecTest.alpha(), GuildCodecTest.beta());
        guilds.saveAll(directory);
        assertTrue(Files.exists(root.resolve("guilds.json")));
        assertEquals(directory, guilds.loadAll());
        guilds.saveAll(List.of(GuildCodecTest.beta()));
        assertTrue(Files.exists(root.resolve("guilds.json.prev")));
        assertEquals(List.of(GuildCodecTest.beta()), guilds.loadAll());
    }

    @Test
    public void aCorruptGuildFileFallsBackToThePreviousGeneration() throws Exception {
        List<Guild> older = List.of(GuildCodecTest.alpha());
        guilds.saveAll(older);
        guilds.saveAll(List.of(GuildCodecTest.alpha(), GuildCodecTest.beta()));
        Files.writeString(root.resolve("guilds.json"), "{\"schema\": 1, \"guil", StandardCharsets.UTF_8);
        assertEquals(older, guilds.loadAll());
        try (Stream<Path> quarantined = Files.list(root.resolve("quarantine"))) {
            assertEquals(1, quarantined.count());
        }
    }

    @Test
    public void aCorruptGuildFileIsNeverReadAsNoGuilds() throws Exception {
        Files.writeString(root.resolve("guilds.json"), "garbage", StandardCharsets.UTF_8);
        assertThrows(CorruptDataException.class, () -> guilds.loadAll());
    }

    @Test
    public void newerGuildDataIsRefusedAndLeftInPlace() throws Exception {
        String newer = GuildCodec.encode(List.of(GuildCodecTest.beta())).replace("\"schema\": 1", "\"schema\": 9");
        Files.writeString(root.resolve("guilds.json"), newer, StandardCharsets.UTF_8);
        assertThrows(IncompatibleDataException.class, () -> guilds.loadAll());
        assertEquals(newer, Files.readString(root.resolve("guilds.json"), StandardCharsets.UTF_8));
    }

    @Test
    public void theMarketIsStoredPerSeason() throws Exception {
        MarketData data = MarketCodecTest.sample();
        assertEquals(Optional.empty(), markets.load(MarketCodecTest.S1));
        markets.save(data);
        Path file = root.resolve("seasons/s1/market.json");
        assertTrue(Files.exists(file));
        assertEquals(Optional.of(data), markets.load(MarketCodecTest.S1));
        assertEquals("seasons never share a market", Optional.empty(), markets.load(SeasonId.of("s2")));

        Path otherSeason = root.resolve("seasons/s2/market.json");
        Files.createDirectories(otherSeason.getParent());
        Files.copy(file, otherSeason);
        assertThrows("a market file in the wrong season directory is corrupt", CorruptDataException.class,
                () -> markets.load(SeasonId.of("s2")));
    }
}
