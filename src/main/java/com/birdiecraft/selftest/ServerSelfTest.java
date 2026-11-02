package com.birdiecraft.selftest;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.course.Course;
import com.birdiecraft.course.CourseBuilder;
import com.birdiecraft.course.CoursePreset;
import com.birdiecraft.course.GolfCourseData;
import com.birdiecraft.course.Hole;
import com.birdiecraft.golf.engine.Difficulty;
import com.birdiecraft.match.GolfMatch;
import com.birdiecraft.match.MatchManager;
import com.birdiecraft.match.MatchState;
import com.birdiecraft.match.Participant;
import com.birdiecraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Automated end-to-end test, only active with -Dbirdiecraft.selftest=server (used by CI):
 * builds a preset course next to spawn, plays a CPU-only match on it and stops the server.
 */
@Mod.EventBusSubscriber(modid = BirdieCraft.MODID)
public final class ServerSelfTest {
    public static final boolean ENABLED = "server".equals(System.getProperty("birdiecraft.selftest"));
    private static final CoursePreset PRESET = CoursePreset.CORAL_BAY;
    private static int phase;
    private static int ticks;
    private static GolfMatch match;

    private ServerSelfTest() {
    }

    private static void log(String msg) {
        BirdieCraft.LOGGER.info("[SelfTest] {}", msg);
    }

    private static void fail(MinecraftServer server, String msg) {
        BirdieCraft.LOGGER.error("[SelfTest] SELFTEST FAIL: {}", msg);
        phase = 99;
        server.halt(false);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || phase == 99) return;
        MinecraftServer server = event.getServer();
        ticks++;
        try {
            switch (phase) {
                case 0 -> {
                    if (ticks < 40) return;
                    ServerLevel level = server.overworld();
                    BlockPos spawn = level.getSharedSpawnPos();
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
                    log("building " + PRESET.id + " at " + top.below());
                    CourseBuilder.buildPresetAt(level, top.below(), Direction.SOUTH, PRESET, UUID.randomUUID(),
                            net.minecraft.network.chat.Component.translatable(PRESET.translationKey()).getString());
                    phase = 1;
                }
                case 1 -> {
                    if (CourseBuilder.isBusy()) {
                        if (ticks % 100 == 0) log("still building...");
                        return;
                    }
                    Course c = GolfCourseData.get(server).get(PRESET.id);
                    if (c == null || c.holes.size() != PRESET.pars.length) {
                        fail(server, "course not registered");
                        return;
                    }
                    ServerLevel level = server.overworld();
                    for (int i = 0; i < c.holes.size(); i++) {
                        Hole h = c.holes.get(i);
                        boolean tee = level.getBlockState(h.tee).is(ModBlocks.TEE_MARKER.get());
                        boolean cup = level.getBlockState(h.cup).is(ModBlocks.GOLF_CUP.get());
                        log("hole " + (i + 1) + " par " + h.par + " " + h.yards() + "Y tee=" + tee + " cup=" + cup);
                        if (!tee || !cup) {
                            fail(server, "hole " + (i + 1) + " is missing its tee or cup");
                            return;
                        }
                    }
                    GolfMatch.verbose = true;
                    match = MatchManager.createCpuOnly(server, c, 0, 3, 3, Difficulty.PRO);
                    log("match started with 3 CPUs over 3 holes");
                    phase = 2;
                }
                case 2 -> {
                    for (int i = 0; i < 25 && !match.isDisposed() && match.state != MatchState.FINISHED; i++) match.tick();
                    if (match.state == MatchState.FINISHED) {
                        boolean ok = true;
                        for (Participant p : match.participants) {
                            StringBuilder sb = new StringBuilder(p.name + ":");
                            for (int i = 0; i < match.holeCount; i++) {
                                int s = p.strokes[i];
                                int par = match.course.holes.get(i).par;
                                sb.append(' ').append(s);
                                if (s < 1 || s > par + 5) ok = false;
                            }
                            sb.append("  total ").append(p.total()).append("  shots ").append(p.shots).append(" perfect ").append(p.perfects);
                            log(sb.toString());
                        }
                        if (!ok) {
                            fail(server, "implausible scores");
                            return;
                        }
                        log("SELFTEST PASS");
                        phase = 3;
                        server.halt(false);
                        return;
                    }
                    if (match.isDisposed()) fail(server, "match closed early (see errors above)");
                    else if (ticks > 20 * 60 * 15) fail(server, "timeout");
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            BirdieCraft.LOGGER.error("[SelfTest] SELFTEST FAIL with exception", t);
            phase = 99;
            server.halt(false);
        }
    }
}
