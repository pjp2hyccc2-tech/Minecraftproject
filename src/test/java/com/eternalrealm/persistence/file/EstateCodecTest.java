package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.estate.EstateData;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstatePlot;
import com.eternalrealm.estate.EstateRegistry;
import com.eternalrealm.testutil.MutableClock;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EstateCodecTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static EstateData sample() {
        EstateRegistry registry = new EstateRegistry();
        UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        String id = registry.claim(new EstateKey(EstateKey.ROOM, alice, "attic"), 1_762_851_200_000L)
                .record().orElseThrow().id();
        registry.startMove(id, "guest");
        registry.claim(new EstateKey(EstateKey.GUILD, UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                "hall"), 1_762_851_300_000L);
        registry.markOrphan(new EstatePlot(0, 17));
        return registry.snapshot();
    }

    @Test
    public void theRegistryRoundTrips() throws Exception {
        EstateData data = sample();
        String json = EstateCodec.encode(data);
        assertTrue(json.contains("\"previous\""));
        assertEquals(data, EstateCodec.decode(json));
        assertEquals(EstateData.empty(), EstateCodec.decode(EstateCodec.encode(EstateData.empty())));
    }

    @Test
    public void brokenOrNewerDataIsRefused() {
        assertThrows(CorruptDataException.class, () -> EstateCodec.decode("{\"schema\":1,\"estates\":["));
        assertThrows(CorruptDataException.class, () -> EstateCodec.decode("{\"schema\":1,\"estates\":[{\"kind\":"
                + "\"room\",\"owner\":\"00000000-0000-0000-0000-00000000000a\",\"design\":\"attic\",\"plot\":[0],"
                + "\"createdAt\":1}],\"wiping\":[]}"));
        assertThrows("a plot used twice", CorruptDataException.class, () -> EstateCodec.decode("{\"schema\":1,"
                + "\"estates\":[{\"kind\":\"room\",\"owner\":\"00000000-0000-0000-0000-00000000000a\",\"design\":"
                + "\"attic\",\"plot\":[0,3],\"createdAt\":1}],\"wiping\":[[0,3]]}"));
        assertThrows("a bad design name", CorruptDataException.class, () -> EstateCodec.decode("{\"schema\":1,"
                + "\"estates\":[{\"kind\":\"room\",\"owner\":\"00000000-0000-0000-0000-00000000000a\",\"design\":"
                + "\"At tic\",\"plot\":[0,3],\"createdAt\":1}],\"wiping\":[]}"));
        assertThrows(IncompatibleDataException.class, () -> EstateCodec.decode("{\"schema\":2,\"estates\":[],"
                + "\"wiping\":[]}"));
    }

    @Test
    public void theRepositoryKeepsOneDocumentWithAPreviousGeneration() throws Exception {
        Path root = tmp.newFolder("eternalrealm").toPath();
        FileEstateRepository repository = new FileEstateRepository(root,
                new AtomicFileStore(root.resolve("quarantine"), MutableClock.startingAt("2026-11-11T00:00:00Z")));
        assertEquals(Optional.empty(), repository.load());
        repository.save(EstateData.empty());
        EstateData data = sample();
        repository.save(data);
        assertTrue(Files.exists(root.resolve(FileEstateRepository.FILE_NAME)));
        assertEquals(Optional.of(data), repository.load());
        Files.writeString(root.resolve(FileEstateRepository.FILE_NAME), "{\"schema\":1,\"est", StandardCharsets.UTF_8);
        assertEquals("a corrupt file falls back to the previous generation", Optional.of(EstateData.empty()),
                repository.load());
    }
}
