package com.birdiecraft.client.selftest;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.client.ClientGolfState;
import com.birdiecraft.client.ClientHooks;
import com.birdiecraft.client.SwingController;
import com.birdiecraft.client.screen.GolfMenuScreen;
import com.birdiecraft.course.Course;
import com.birdiecraft.course.GolfCourseData;
import com.birdiecraft.golf.engine.Difficulty;
import com.birdiecraft.golf.engine.GaugeMath;
import com.birdiecraft.match.MatchManager;
import com.birdiecraft.match.MatchMode;
import com.birdiecraft.match.MatchState;
import com.birdiecraft.match.MatchView;
import com.birdiecraft.registry.ModItems;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Automated client walkthrough, only active with -Dbirdiecraft.selftest=client (used by CI):
 * starts a VS CPU match in the loaded world, swings automatically and saves screenshots.
 */
@Mod.EventBusSubscriber(modid = BirdieCraft.MODID, value = Dist.CLIENT)
public final class ClientSelfTest {
    public static final boolean ENABLED = "client".equals(System.getProperty("birdiecraft.selftest"));

    private static int ticks;
    private static boolean setupDone;
    private static final Set<String> TAKEN = new HashSet<>();
    private static MatchState lastState;
    private static int stateTicks;
    private static String turnKey = "";
    private static int turnTicks;
    private static int flights;
    private static double targetPower;
    private static int finishTicks = -1;

    private ClientSelfTest() {
    }

    private static void log(String msg) {
        BirdieCraft.LOGGER.info("[ClientSelfTest] {}", msg);
    }

    private static void shot(String name) {
        if (!TAKEN.add(name)) return;
        Minecraft mc = Minecraft.getInstance();
        File dir = new File(mc.gameDirectory, "selftest-screens");
        dir.mkdirs();
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            File f = new File(dir, name + ".png");
            img.writeToFile(f);
            log("screenshot " + f.getName());
        } catch (Exception e) {
            BirdieCraft.LOGGER.error("[ClientSelfTest] screenshot failed", e);
        }
    }

    private static void setup(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.player == null) return;
        setupDone = true;
        UUID id = mc.player.getUUID();
        mc.player.getInventory().selected = 0;
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp == null) return;
            ServerLevel level = srv.overworld();
            level.setDayTime(5000);
            level.setWeatherParameters(20000, 0, false, false);
            sp.setGameMode(GameType.CREATIVE);
            sp.getInventory().setItem(0, new ItemStack(ModItems.GOLF_CLUB.get()));
            sp.getInventory().selected = 0;
            GolfCourseData data = GolfCourseData.get(srv);
            Course c = data.get("coral_bay");
            if (c == null && !data.all().isEmpty()) c = data.all().iterator().next();
            if (c == null) {
                BirdieCraft.LOGGER.error("[ClientSelfTest] no course in this world");
                return;
            }
            MatchManager.create(sp, MatchMode.VS_CPU, c, 0, 2, 1, Difficulty.PRO);
            BirdieCraft.LOGGER.info("[ClientSelfTest] match created on {}", c.id);
        });
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        if (ticks == 100) shot("00_spawn_view");
        if (ticks == 120 && !setupDone) setup(mc);
        if (ticks > 20 * 60 * 14) {
            log("time limit reached");
            shot("99_timeout");
            mc.stop();
            return;
        }
        if (finishTicks >= 0) {
            finishTicks++;
            runEnding(mc);
            return;
        }
        MatchView m = ClientGolfState.match;
        if (m == null) return;
        if (m.state != lastState) {
            lastState = m.state;
            stateTicks = 0;
        } else {
            stateTicks++;
        }
        int hole = m.currentHoleNumber();
        switch (m.state) {
            case INTRO -> {
                if (stateTicks == 45) shot("01_intro_hole" + hole);
            }
            case PLAYING -> playTurn(m, hole);
            case BALL_MOVING -> {
                if (stateTicks == 35) shot("03_flight_" + (flights++));
                if (stateTicks == 70 && m.turnId == m.myId) shot("03_flight_late_" + flights);
            }
            case HOLE_END -> {
                if (stateTicks == 40) shot("06_scorecard_hole" + hole);
            }
            case FINISHED -> {
                if (stateTicks == 50) {
                    shot("08_final_results");
                    finishTicks = 0;
                }
            }
            default -> {
            }
        }
    }

    private static void playTurn(MatchView m, int hole) {
        MatchView.PView me = m.me();
        MatchView.PView turn = m.player(m.turnId);
        if (me == null || turn == null) return;
        String key = m.turnId + ":" + turn.cur + ":" + hole;
        if (!key.equals(turnKey)) {
            turnKey = key;
            turnTicks = 0;
        } else {
            turnTicks++;
        }
        if (turn.cpu) {
            if (turnTicks == 32) shot("04_cpu_turn_h" + hole + "_s" + turn.cur);
            return;
        }
        if (!m.isMyTurn() || ClientGolfState.isFlying(m.myId)) return;
        String tag = "h" + hole + "_s" + me.cur;
        if (turnTicks == 40) {
            shot((SwingController.club.isPutter() ? "05_putt_" : "02_aim_") + tag);
        }
        if (turnTicks < 50) return;
        switch (SwingController.phase) {
            case IDLE -> {
                double pin = SwingController.pinDistance();
                double need = GaugeMath.powerForDistance(SwingController.club, SwingController.mode, SwingController.lie,
                        SwingController.style(), SwingController.puttRange, pin);
                targetPower = Math.max(0.08, Math.min(1.0, SwingController.club.isPutter() ? need * 1.05 : need));
                log("swing " + tag + " club " + SwingController.club.label + " pin " + Math.round(pin * 2) + "Y power " + Math.round(targetPower * 100) + "%");
                SwingController.click();
            }
            case RISING -> {
                double pos = SwingController.gaugePosition();
                if (pos > 0.35) shot("02b_gauge_" + tag);
                if (pos >= targetPower - 0.02) SwingController.click();
            }
            case RETURNING -> {
                if (SwingController.gaugePosition() <= 0.012) SwingController.click();
            }
            default -> {
            }
        }
    }

    private static void runEnding(Minecraft mc) {
        switch (finishTicks) {
            case 20 -> ClientHooks.openGolfMenu();
            case 60 -> shot("09_menu_play");
            case 70 -> GolfMenuScreen.selectTab(1);
            case 100 -> shot("10_menu_courses");
            case 110 -> GolfMenuScreen.selectTab(3);
            case 140 -> shot("11_menu_style");
            case 150 -> {
                mc.setScreen(null);
                log("CLIENT SELFTEST DONE");
            }
            case 170 -> mc.stop();
            default -> {
            }
        }
    }
}
