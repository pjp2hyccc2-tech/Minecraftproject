package com.eternalrealm.persistence.file;

import com.eternalrealm.character.CharacterRecord;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.player.AccountRecord;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.testutil.MutableClock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileRepositoriesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");

    private Path root;
    private FileCharacterRepository characters;
    private FileAccountRepository accounts;

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("eternalrealm").toPath();
        AtomicFileStore store = new AtomicFileStore(root.resolve("quarantine"), MutableClock.startingAt("2026-11-08T00:00:00Z"));
        characters = new FileCharacterRepository(root, store);
        accounts = new FileAccountRepository(root, store);
    }

    @Test
    public void characterIsStoredPerSeason() throws Exception {
        SeasonId s1 = SeasonId.of("s1");
        CharacterRecord record = new CharacterRecord(PLAYER, s1, 12, 34, 5678, StatBlock.ZERO.with(StatType.VIT, 9), NOW, NOW);
        characters.save(record);

        Path expected = root.resolve("seasons/s1/characters/" + PLAYER + ".json");
        assertTrue(Files.exists(expected));
        assertEquals(Optional.of(record), characters.load(PLAYER, s1));
        assertEquals("season data must never leak into another season", Optional.empty(), characters.load(PLAYER, SeasonId.of("s2")));
        assertEquals(Optional.empty(), characters.load(UUID.randomUUID(), s1));
    }

    @Test
    public void accountIsStoredOutsideSeasons() throws Exception {
        AccountRecord account = AccountRecord.firstLogin(PLAYER, "Alex", NOW).withLogin("Alex2", NOW.plusSeconds(60));
        accounts.save(account);
        assertTrue(Files.exists(root.resolve("accounts/" + PLAYER + ".json")));
        AccountRecord loaded = accounts.load(PLAYER).orElseThrow();
        assertEquals(account, loaded);
        assertEquals(2, loaded.loginCount());
        assertEquals("Alex2", loaded.lastKnownName());
    }
}
