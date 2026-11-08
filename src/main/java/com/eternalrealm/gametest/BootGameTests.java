package com.eternalrealm.gametest;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.ExperienceResult;
import com.eternalrealm.character.ExperienceSource;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-game integration tests, run headless by {@code ./gradlew runGameTestServer} (and in CI).
 *
 * <p>They verify, inside a real Minecraft + Forge server, that the mod boots, the server services are wired, and that a
 * character survives join → progress → leave → re-join through the real server thread, I/O executor and disk.
 * GameTests are only enabled in development environments; they are inert in production.</p>
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BootGameTests {
    /** {@code data/eternalrealm/structures/empty.nbt}: an empty 3x3x3 structure. */
    private static final String EMPTY = "empty";

    private BootGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void serverServicesAreRunning(GameTestHelper helper) {
        ServerContext context = requireContext();
        if (context.server() != helper.getLevel().getServer()) {
            throw new GameTestAssertException("ServerContext belongs to another server");
        }
        if (!Files.isDirectory(context.dataDirectory())) {
            throw new GameTestAssertException("Data directory missing: " + context.dataDirectory());
        }
        helper.succeed();
    }

    /**
     * Inside {@code succeedWhen} every thrown {@link GameTestAssertException} means "not yet, retry next tick".
     * A definitive failure is therefore remembered and re-thrown on every tick, so the test fails at its timeout
     * with that message instead of being retried into a false success.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void characterSurvivesLeaveAndRejoin(GameTestHelper helper) {
        ServerContext context = requireContext();
        PlayerDataService data = context.playerData();
        UUID playerId = UUID.randomUUID();
        AtomicInteger stage = new AtomicInteger();
        AtomicInteger expectedLevel = new AtomicInteger();
        AtomicReference<String> failure = new AtomicReference<>();
        data.onPlayerJoin(playerId, "gametest");

        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw new GameTestAssertException(failure.get());
            }
            switch (stage.get()) {
                case 0 -> {
                    if (!data.isActive(playerId)) {
                        throw new GameTestAssertException("waiting for the character to load");
                    }
                    ExperienceResult result = data.grantExperience(playerId, 1_000, ExperienceSource.SYSTEM).orElse(null);
                    if (result == null || !result.leveledUp()) {
                        failure.set("1000 EXP should level up a new character, got " + result);
                        throw new GameTestAssertException(failure.get());
                    }
                    expectedLevel.set(result.newLevel());
                    data.onPlayerLeave(playerId);
                    // Per-player I/O ordering guarantees this load runs after the logout save.
                    data.onPlayerJoin(playerId, "gametest");
                    stage.set(1);
                    throw new GameTestAssertException("waiting for save and reload");
                }
                case 1 -> {
                    if (!data.isActive(playerId)) {
                        throw new GameTestAssertException("waiting for the character to reload");
                    }
                    int reloadedLevel = data.snapshot(playerId).map(CharacterSnapshot::level).orElse(-1);
                    data.onPlayerLeave(playerId);
                    stage.set(2);
                    if (reloadedLevel != expectedLevel.get()) {
                        failure.set("Reloaded level " + reloadedLevel + " != saved level " + expectedLevel.get());
                        throw new GameTestAssertException(failure.get());
                    }
                    // Returning normally lets the test succeed.
                }
                default -> {
                    // Verified in stage 1.
                }
            }
        });
    }

    private static ServerContext requireContext() {
        return ServerContext.current().orElseThrow(() -> new GameTestAssertException("ServerContext is not installed"));
    }
}
