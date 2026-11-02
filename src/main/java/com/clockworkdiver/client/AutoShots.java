package com.clockworkdiver.client;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.client.gui.BenchScreen;
import com.clockworkdiver.client.gui.DialogueScreen;
import com.clockworkdiver.client.gui.EndingScreen;
import com.clockworkdiver.client.gui.JournalScreen;
import com.clockworkdiver.story.StoryQuests;
import com.clockworkdiver.client.gui.RepairPromptScreen;
import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.ClockType;
import com.clockworkdiver.clock.CommissionGenerator;
import com.clockworkdiver.network.S2C;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * CI only (enabled with {@code -Dclockworkdiver.clienttest=true}, run under Xvfb): plays through the
 * tutorial and the other repair chambers and takes screenshots into {@code run/screenshots/}.
 * The log goes to {@code run/clockworkdiver_autoshots.txt}.
 */
public final class AutoShots {
    private static final boolean ENABLED = Boolean.getBoolean("clockworkdiver.clienttest");
    private static int stage;
    private static int wait;
    private static int ticks;
    private static int index;
    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> LOG = new ArrayList<>();

    private record Step(String name, Consumer<Minecraft> action, int waitAfter, boolean shot) {}

    private AutoShots() {}

    static void onTitle(TitleScreen title) {
        if (!ENABLED || stage != 0) return;
        stage = 1;
        wait = 40;
        Minecraft.getInstance().options.pauseOnLostFocus = false;
        log("title screen");
    }

    static void tick(Minecraft mc) {
        if (!ENABLED || stage == 0 || stage == 9) return;
        if (++ticks > 20 * 60 * 26) {
            log("global timeout");
            finish(mc);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (stage) {
            case 1 -> {
                if (mc.getOverlay() != null) return;
                shot(mc, "00_title");
                createWorld(mc);
                stage = 2;
            }
            case 2 -> {
                if (mc.level == null || mc.player == null || mc.screen != null) return;
                stage = 3;
                wait = 100;
                build();
                log("in world, " + STEPS.size() + " steps");
            }
            case 3 -> {
                if (index >= STEPS.size()) {
                    finish(mc);
                    return;
                }
                Step s = STEPS.get(index++);
                if (s.shot()) {
                    shot(mc, s.name());
                    wait = 3;
                    return;
                }
                try {
                    s.action().accept(mc);
                    log("step " + s.name() + " screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())
                            + " dim=" + (mc.level == null ? "?" : mc.level.dimension().location()));
                } catch (Exception e) {
                    log("step failed " + s.name() + ": " + e);
                    ClockworkDiver.LOGGER.error("[clockworkdiver] autoshot step {} failed", s.name(), e);
                }
                wait = s.waitAfter();
            }
            default -> {
            }
        }
    }

    private static void createWorld(Minecraft mc) {
        try {
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "ClockworkCI", "");
            LevelSettings settings = new LevelSettings("ClockworkCI", GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(),
                    WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(20261102L, false, false);
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, options,
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            log("world creation requested");
        } catch (Exception e) {
            log("could not create world: " + e);
            ClockworkDiver.LOGGER.error("[clockworkdiver] could not create world", e);
        }
    }

    private static void add(String name, Consumer<Minecraft> action, int waitAfter) {
        STEPS.add(new Step(name, action, waitAfter, false));
    }

    private static void snap(String name) {
        STEPS.add(new Step(name, null, 0, true));
    }

    private static void cmd(Minecraft mc, String command) {
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        if (mc.player != null) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
        }
    }

    private static BenchScreen bench(Minecraft mc) {
        return mc.screen instanceof BenchScreen b ? b : null;
    }

    private static void lensOn(Minecraft mc, int defect) {
        BenchScreen b = bench(mc);
        if (b == null) return;
        b.forcedLensX = b.defectScreenX(defect);
        b.forcedLensY = b.defectScreenY(defect);
    }

    private static void zone(String type, String shot, float yaw, float pitch, int extraWait) {
        String id = type.replace(' ', '_');
        add("dive_" + id, mc -> {
            mc.setScreen(null);
            cmd(mc, "clockwork dive " + type);
        }, 90 + extraWait);
        add("look_" + id, mc -> look(mc, yaw, pitch), 10);
        snap(shot);
        add("abort_" + id, mc -> cmd(mc, "clockwork abort"), 70);
        add("close_" + id, mc -> mc.setScreen(null), 10);
    }

    private static void build() {
        STEPS.clear();
        add("gamerules", mc -> {
            cmd(mc, "gamerule doDaylightCycle false");
            cmd(mc, "gamerule doMobSpawning false");
        }, 10);
        add("workshop", mc -> cmd(mc, "clockwork workshop"), 140);
        snap("01_workshop");
        add("look_bench", mc -> look(mc, 180.0f, 12.0f), 10);
        snap("02_workshop_bench");
        add("customer_counter", mc -> cmd(mc, "clockwork debug counter"), 20);
        add("behind_counter", mc -> cmd(mc, "tp @s 0.5 64 1.2 0 8"), 30);
        snap("03_customer");
        add("talk", mc -> cmd(mc, "clockwork debug talk"), 25);
        snap("04_dialogue");
        add("skip", mc -> {
            if (mc.screen instanceof DialogueScreen d) d.skipAll();
        }, 6);
        snap("05_dialogue_full");
        add("accept", mc -> {
            if (mc.screen instanceof DialogueScreen d) d.primaryAction();
        }, 12);
        snap("06_repair_bench_button");
        add("go_bench", mc -> {
            if (mc.screen instanceof RepairPromptScreen r) r.goToBench();
            else cmd(mc, "clockwork bench");
        }, 16);
        snap("07_glasses_on");
        add("wait_glasses", mc -> {
        }, 40);
        snap("08_bench");
        add("lens_first", mc -> lensOn(mc, 0), 26);
        snap("09_lens_found");
        add("lens_second", mc -> lensOn(mc, 1), 26);
        snap("10_lens_second");
        add("select", mc -> {
            BenchScreen b = bench(mc);
            if (b != null) {
                b.forcedLensX = -1000;
                b.forcedLensY = -1000;
                b.select(0);
            }
        }, 8);
        snap("11_selected");
        add("dive", mc -> {
            BenchScreen b = bench(mc);
            if (b != null) b.startDive();
        }, 16);
        snap("12_shrinking");
        add("arrive", mc -> {
        }, 30);
        snap("13_arrival");
        add("wait_zone", mc -> {
        }, 50);
        add("look_rust", mc -> look(mc, 180.0f, 18.0f), 10);
        snap("14_rust_zone");
        add("wave", mc -> cmd(mc, "clockwork debug wave"), 40);
        snap("15_combat");
        add("look_up", mc -> look(mc, 180.0f, -40.0f), 8);
        snap("16_gears_above");
        add("complete", mc -> cmd(mc, "clockwork complete"), 25);
        snap("17_repaired");
        add("back", mc -> {
        }, 110);
        snap("18_back_at_bench");
        add("dive_teeth_real", mc -> {
            BenchScreen b = bench(mc);
            if (b != null) {
                b.select(1);
                b.startDive();
            }
        }, 140);
        add("look_teeth", mc -> look(mc, 180.0f, -8.0f), 10);
        snap("19_teeth_zone");
        add("complete_teeth", mc -> cmd(mc, "clockwork complete"), 130);
        snap("20_all_repaired");
        add("close_bench", mc -> mc.setScreen(null), 10);
        add("to_counter", mc -> cmd(mc, "tp @s 0.5 64 1.2 0 8"), 30);
        add("deliver_talk", mc -> cmd(mc, "clockwork debug talk"), 20);
        add("deliver_skip", mc -> {
            if (mc.screen instanceof DialogueScreen d) d.skipAll();
        }, 6);
        snap("21_deliver");
        add("deliver", mc -> {
            if (mc.screen instanceof DialogueScreen d) d.primaryAction();
        }, 30);
        add("thanks_skip", mc -> {
            if (mc.screen instanceof DialogueScreen d) d.skipAll();
        }, 6);
        snap("22_thanks");
        add("thanks_close", mc -> mc.setScreen(null), 40);
        snap("23_tutorial_done");
        add("hold", mc -> cmd(mc, "clockwork debug hold"), 10);
        zone("screw", "24_screw_zone", 180.0f, -12.0f, 0);
        zone("spring", "25_spring_zone", 180.0f, -18.0f, 0);
        zone("nest", "26_boss", 180.0f, 5.0f, 60);
        int n = 0;
        for (ClockType type : ClockType.values()) {
            final String shotName = String.format("%02d_bench_%s", 29 + n++, type.id);
            add("bench_" + type.id, mc -> {
                ClockData clock = null;
                if (type == ClockType.GREAT_CLOCK) {
                    clock = StoryQuests.get(17).createClock(7);
                } else {
                    RandomSource rand = RandomSource.create(type.ordinal() * 31L + 7L);
                    for (int i = 0; i < 400 && clock == null; i++) {
                        ClockData c = CommissionGenerator.random(rand, 99, i);
                        if (c.type == type && c.defects.size() >= 2) clock = c;
                    }
                }
                if (clock == null) return;
                clock.defects.get(0).found = true;
                clock.defects.get(1).repaired = true;
                clock.defects.get(1).found = true;
                BenchScreen b = new BenchScreen(new S2C.OpenBench(clock.save(), 30, 1, 1, 1, false, false, 12));
                mc.setScreen(b);
                int target = clock.defects.size() > 2 ? 2 : 0;
                b.forcedLensX = b.defectScreenX(clock.defects.get(target).id);
                b.forcedLensY = b.defectScreenY(clock.defects.get(target).id);
            }, 12);
            snap(shotName);
        }
        add("close_benches", mc -> mc.setScreen(null), 5);
        add("street", mc -> cmd(mc, "tp @s 0.5 64 20.5 180 -4"), 40);
        snap("38_shop_front");
        add("tower", mc -> look(mc, 0.0f, -55.0f), 10);
        snap("39_clock_tower");
        story();
    }

    private static DialogueScreen dialogue(Minecraft mc) {
        return mc.screen instanceof DialogueScreen d ? d : null;
    }

    /** The story: chapter and quest cards, story dialogue, clues, twists, the tower plaza, the journal and the ending. */
    private static void story() {
        add("story_start", mc -> {
            mc.setScreen(null);
            cmd(mc, "tp @s 0.5 64 1.2 0 8");
            cmd(mc, "clockwork story set 1");
        }, 70);
        snap("40_chapter_card");
        add("wait_quest_card", mc -> {
        }, 110);
        snap("41_quest_card");
        add("q1_counter", mc -> cmd(mc, "clockwork debug counter"), 20);
        add("q1_talk", mc -> cmd(mc, "clockwork debug talk"), 20);
        add("q1_line_me", mc -> {
            DialogueScreen d = dialogue(mc);
            if (d != null) d.showLine(2);
        }, 6);
        snap("42_story_dialogue_me");
        add("q1_line_bolt", mc -> {
            DialogueScreen d = dialogue(mc);
            if (d != null) d.showLine(1);
        }, 6);
        snap("43_story_dialogue");
        add("q1_skip", mc -> {
            DialogueScreen d = dialogue(mc);
            if (d != null) d.skipAll();
        }, 6);
        snap("44_story_offer");
        add("q1_accept", mc -> {
            DialogueScreen d = dialogue(mc);
            if (d != null) d.primaryAction();
        }, 12);
        add("q1_bench", mc -> {
            if (mc.screen instanceof RepairPromptScreen r) r.goToBench();
            else cmd(mc, "clockwork bench");
        }, 60);
        add("q1_lens_screw", mc -> lensOn(mc, 1), 26);
        add("q1_lens_clue", mc -> lensOn(mc, 2), 26);
        snap("45_clue_found");
        add("q1_select_screw", mc -> {
            BenchScreen b = bench(mc);
            if (b != null) {
                b.forcedLensX = -1000;
                b.forcedLensY = -1000;
                b.select(1);
            }
        }, 8);
        snap("46_bench_twists");
        add("q1_dive", mc -> {
            BenchScreen b = bench(mc);
            if (b != null) b.startDive();
        }, 150);
        add("q1_look", mc -> look(mc, 180.0f, -10.0f), 10);
        snap("47_timed_screw");
        add("q1_complete", mc -> cmd(mc, "clockwork complete"), 130);
        add("q1_close", mc -> mc.setScreen(null), 10);

        add("q10_set", mc -> {
            cmd(mc, "tp @s 0.5 64 1.2 0 8");
            cmd(mc, "clockwork story set 10");
        }, 80);
        add("q10_counter", mc -> cmd(mc, "clockwork debug counter"), 20);
        add("q10_talk", mc -> cmd(mc, "clockwork debug talk"), 20);
        add("q10_line", mc -> {
            DialogueScreen d = dialogue(mc);
            if (d != null) d.showLine(3);
        }, 6);
        snap("48_stranger_dialogue");
        add("q10_accept", mc -> {
            DialogueScreen d = dialogue(mc);
            if (d != null) {
                d.skipAll();
                d.primaryAction();
            }
        }, 12);
        add("q10_bench", mc -> {
            if (mc.screen instanceof RepairPromptScreen r) r.goToBench();
            else cmd(mc, "clockwork bench");
        }, 60);
        add("q10_lens", mc -> lensOn(mc, 0), 26);
        snap("49_bench_music_box");
        add("q10_dive", mc -> {
            BenchScreen b = bench(mc);
            if (b != null) {
                b.select(0);
                b.startDive();
            }
        }, 150);
        add("q10_look", mc -> look(mc, 180.0f, 10.0f), 10);
        snap("50_spring_fragments");
        add("q10_fragment", mc -> cmd(mc, "clockwork debug fragment"), 24);
        snap("51_memory_card");
        add("q10_complete", mc -> cmd(mc, "clockwork complete"), 130);
        add("q10_close", mc -> mc.setScreen(null), 10);
        add("hold2", mc -> cmd(mc, "clockwork debug hold"), 5);

        zone("dust swarm", "52_dust_zone", 180.0f, 14.0f, 0);
        zone("tuning", "53_tuning_bells", 180.0f, 16.0f, 90);
        zone("teeth offbeat dark elite", "54_dark_offbeat", 180.0f, 2.0f, 30);
        zone("nest shadow", "55_shadow_boss", 180.0f, 6.0f, 70);
        zone("nest queen", "56_queen", 180.0f, -6.0f, 70);

        add("q17_set", mc -> {
            mc.setScreen(null);
            cmd(mc, "tp @s 0.5 64 26.5 0 0");
            cmd(mc, "clockwork story set 17");
        }, 100);
        add("q17_view", mc -> cmd(mc, "tp @s 0.5 64 29.2 0 -12"), 40);
        snap("57_plaza");
        add("q17_wide", mc -> cmd(mc, "tp @s 0.5 64 20.5 0 -28"), 30);
        snap("58_tower_arch");
        add("q17_back", mc -> cmd(mc, "tp @s 0.5 64 30.5 0 0"), 20);
        add("q17_talk", mc -> cmd(mc, "clockwork debug talk"), 20);
        add("q17_line", mc -> {
            DialogueScreen d = dialogue(mc);
            if (d != null) d.showLine(1);
        }, 6);
        snap("59_tower_dialogue");
        add("q17_accept", mc -> {
            DialogueScreen d = dialogue(mc);
            if (d != null) {
                d.skipAll();
                d.primaryAction();
            }
        }, 12);
        add("q17_bench", mc -> {
            if (mc.screen instanceof RepairPromptScreen r) r.goToBench();
            else cmd(mc, "clockwork bench");
        }, 70);
        add("q17_lens", mc -> lensOn(mc, 0), 26);
        snap("60_great_clock_bench");
        add("q17_close", mc -> mc.setScreen(null), 10);

        add("journal", mc -> mc.setScreen(new JournalScreen()), 15);
        snap("61_journal");
        add("journal_done", mc -> {
            if (mc.screen instanceof JournalScreen j) j.select(9);
        }, 8);
        snap("62_journal_done");
        add("ending", mc -> {
            EndingScreen e = new EndingScreen(false);
            mc.setScreen(e);
            e.setScroll(260);
        }, 20);
        snap("63_ending");
        add("ending_cast", mc -> {
            if (mc.screen instanceof EndingScreen e) e.setScroll(820);
        }, 10);
        snap("64_ending_cast");
        add("ending_close", mc -> mc.setScreen(null), 10);

        add("q20_set", mc -> {
            cmd(mc, "tp @s 0.5 64 1.2 0 8");
            cmd(mc, "clockwork story set 20");
        }, 100);
        add("q20_counter", mc -> cmd(mc, "clockwork debug counter"), 40);
        add("q20_view", mc -> cmd(mc, "tp @s 0.5 64 -2.5 0 14"), 30);
        snap("65_finale_shop");
        add("q20_talk", mc -> cmd(mc, "clockwork debug talk"), 20);
        add("q20_line", mc -> {
            DialogueScreen d = dialogue(mc);
            if (d != null) d.showLine(3);
        }, 6);
        snap("66_finale_dialogue");
        add("q20_close", mc -> mc.setScreen(null), 10);
    }

    private static void shot(Minecraft mc, String name) {
        try {
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {
            });
            log("shot " + name);
        } catch (Exception e) {
            log("screenshot failed " + name + ": " + e);
        }
    }

    private static void log(String s) {
        LOG.add(s);
        ClockworkDiver.LOGGER.info("[clockworkdiver] autoshots: {}", s);
        try {
            Files.writeString(Path.of("clockworkdiver_autoshots.txt"), String.join("\n", LOG) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void finish(Minecraft mc) {
        stage = 9;
        log("finished");
        mc.stop();
    }
}
