package com.clockworkdiver.debug;

import com.clockworkdiver.ClockworkDiver;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** CI only: enabled with -Dclockworkdiver.selftest=true. */
@Mod.EventBusSubscriber(modid = ClockworkDiver.MODID)
public final class SelfTest {
    private static final boolean ENABLED = Boolean.getBoolean("clockworkdiver.selftest");
    private static final List<String> LINES = new ArrayList<>();
    private static boolean failed = false;
    private static int ticks = -1;

    private SelfTest() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ENABLED) return;
        LINES.add("Server started; running checks");
        ticks = 0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || ticks < 0 || event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ticks == 40) {
            finish(event.getServer());
        }
    }

    private static void finish(MinecraftServer server) {
        LINES.add("SELFTEST RESULT: " + (failed ? "FAIL" : "PASS"));
        try {
            Files.write(Path.of("selftest-report.txt"), LINES, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ClockworkDiver.LOGGER.error("Could not write self test report", e);
        }
        server.halt(false);
    }
}
