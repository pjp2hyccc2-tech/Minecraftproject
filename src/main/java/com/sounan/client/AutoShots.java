package com.sounan.client;

import com.sounan.Sounan;
import com.sounan.client.gui.EndingScreen;
import com.sounan.client.gui.NoteScreen;
import com.sounan.client.gui.PhoneScreen;
import com.sounan.client.gui.TrailMapScreen;
import com.sounan.world.Pois;
import com.sounan.world.PoiLayout;
import com.sounan.world.TerrainData;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
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
import java.util.Locale;
import java.util.function.Consumer;

/**
 * CI only ({@code -Dsounan.shots=true}, Xvfb + software GL): plays a scripted tour of the mountain and saves
 * screenshots into {@code run/screenshots/}. With {@code -Dsounan.shots.shaders=true} (Oculus run) it takes a shorter
 * tour with longer waits. Log: {@code run/sounan_autoshots.txt}.
 */
public final class AutoShots {
    private static final boolean ENABLED = Boolean.getBoolean("sounan.shots");
    private static final boolean SHADERS = Boolean.getBoolean("sounan.shots.shaders");
    private static final int MUL = SHADERS ? 3 : 1;
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
        wait = 60;
        Minecraft.getInstance().options.pauseOnLostFocus = false;
        log("title screen (shaders=" + SHADERS + ")");
    }

    static void tick(Minecraft mc) {
        if (!ENABLED || stage == 0 || stage == 9) return;
        if (++ticks > 20 * 60 * (SHADERS ? 50 : 34)) {
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
                wait = 20;
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
                    log("step " + s.name() + " dim=" + (mc.level == null ? "?" : mc.level.dimension().location())
                            + " pos=" + (mc.player == null ? "?" : mc.player.blockPosition().toShortString())
                            + (SHADERS ? " shaders=" + ShaderCompat.active() : ""));
                } catch (Exception e) {
                    log("step failed " + s.name() + ": " + e);
                    Sounan.LOGGER.error("[sounan] autoshot step {} failed", s.name(), e);
                }
                wait = s.waitAfter() * (s.waitAfter() > 10 ? MUL : 1);
            }
            default -> {
            }
        }
    }

    private static void createWorld(Minecraft mc) {
        try {
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "SounanCI", "");
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            LevelSettings settings = new LevelSettings("SounanCI", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules,
                    WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(20261102L, false, false);
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, options,
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            log("world creation requested");
        } catch (Exception e) {
            log("could not create world: " + e);
            Sounan.LOGGER.error("[sounan] could not create world", e);
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

    /** Teleport to a column of the mountain (feet on the surface) looking along yaw / pitch. */
    private static void at(Minecraft mc, double x, double z, float yaw, float pitch) {
        int y = TerrainData.get().height((int) Math.floor(x), (int) Math.floor(z)) + 1;
        cmd(mc, String.format(Locale.ROOT, "execute in sounan:kaerazu run tp @s %.2f %d %.2f %.1f %.1f", x, y, z, yaw, pitch));
    }

    private static void atY(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        cmd(mc, String.format(Locale.ROOT, "execute in sounan:kaerazu run tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
    }

    private static void hold(Minecraft mc, String item) {
        cmd(mc, "item replace entity @s weapon.mainhand with " + item);
    }

    private static void fear(Minecraft mc, int fear, float danger) {
        cmd(mc, String.format(Locale.ROOT, "sounan debug fear %d %.2f 2000", fear, danger));
    }

    private static void scene(String name, Consumer<Minecraft> setup, int settle) {
        add("go_" + name, setup, settle);
        snap(name);
    }

    private static void build() {
        STEPS.clear();
        PoiLayout L = PoiLayout.get();
        Pois P = Pois.get();
        add("rules", mc -> {
            cmd(mc, "gamerule sendCommandFeedback false");
            cmd(mc, "gamerule doMobSpawning false");
        }, 10);
        if (!SHADERS) {
            snap("01_intro_text");
            add("intro_wait", mc -> {
            }, 120);
            snap("02_intro_wake");
        } else {
            add("intro_wait", mc -> {
            }, 110);
        }
        add("settle", mc -> {
            cmd(mc, "sounan debug peaceful true");
            fear(mc, 30, 0.0f);
        }, 120);
        scene("03_start_dark", mc -> {
            hold(mc, "sounan:flashlight");
            at(mc, L.start.getX() + 0.5, L.start.getZ() + 0.5, 155.0f, 4.0f);
        }, 80);
        scene("04_start_flashlight", mc -> hold(mc, "sounan:flashlight_on"), 20);
        scene("05_bag", mc -> at(mc, L.bag.getX() - 3.5, L.bag.getZ() + 3.5, -135.0f, 25.0f), 80);
        scene("06_cliff_edge", mc -> at(mc, L.ropeAnchor.getX() - 1.5, L.ropeAnchor.getZ() - 2.5, -20.0f, 40.0f), 80);
        scene("07_forest_ribbons", mc -> at(mc, -95.5, -88.5, -45.0f, 2.0f), 90);
        scene("08_forest_deep", mc -> at(mc, -42.5, -44.5, -40.0f, 0.0f), 90);
        scene("09_dead_end", mc -> {
            Pois.Point d = P.point("dead_end");
            at(mc, d.x() + 0.5, d.z() - 5.5, 0.0f, 12.0f);
        }, 90);
        scene("10_village", mc -> at(mc, 2.5, 6.5, -90.0f, 4.0f), 90);
        scene("11_village_houses", mc -> at(mc, 30.5, 6.5, 160.0f, 2.0f), 40);
        scene("12_phone_booth", mc -> at(mc, L.publicPhone.getX() + 0.5, L.publicPhone.getZ() - 3.5, 0.0f, 10.0f), 40);
        scene("13_omiki_house", mc -> atY(mc, L.villageSleeper.getX() - 3.5, L.villageSleeper.getY(), L.villageSleeper.getZ() - 1.5, 120.0f, 25.0f), 40);
        scene("14_shrine_steps", mc -> at(mc, L.hokora.getX() - 18.5, L.hokora.getZ() + 0.5, -90.0f, -8.0f), 90);
        scene("15_shrine", mc -> at(mc, L.hokora.getX() - 3.5, L.hokora.getZ() + 0.5, -90.0f, 12.0f), 40);
        scene("16_fog_rope", mc -> at(mc, P.fogCx - P.fogNx * 6, P.fogCz - P.fogNz * 6, yawToward(-P.fogNx, -P.fogNz) + 180.0f, 0.0f), 80);
        scene("17_bridge", mc -> atY(mc, L.bridgeX + 0.5, L.bridgeY + 1, L.bridgeZ0 - 2.5, 0.0f, 8.0f), 90);
        scene("18_hut", mc -> at(mc, L.hut.x2() + 8.5, L.hut.cz() + 2.5, 105.0f, 4.0f), 90);
        scene("19_hut_inside", mc -> atY(mc, L.hut.x2() - 1.5, L.hut.floorY() + 1, L.hut.cz() + 0.5, 90.0f, 15.0f), 40);
        scene("20_pylon", mc -> at(mc, L.pylon.getX() + 12.5, L.pylon.getZ() + 6.5, 120.0f, -25.0f), 90);
        scene("21_tunnel_gate", mc -> at(mc, L.tunnelX + 0.5, L.tunnelZ0 - 7.5, 0.0f, 2.0f), 90);
        scene("22_tunnel_inside", mc -> atY(mc, L.tunnelX + 0.5, L.roadY(L.tunnelZ0 + 30) + 1, L.tunnelZ0 + 30.5, 0.0f, 0.0f), 90);
        scene("23_trailhead", mc -> at(mc, L.carDoor.getX() - 9.5, L.carDoor.getZ() + 4.5, -110.0f, 6.0f), 90);
        // the monsters, lined up in the village (camera on so the camera-only one shows)
        add("lineup_spawn", mc -> {
            at(mc, 20.5, 6.5, -90.0f, 6.0f);
            cmd(mc, "kill @e[type=!player]");
        }, 60);
        add("lineup_mobs", mc -> {
            int y = TerrainData.get().height(26, 6) + 1;
            String[] ids = {"nozoki", "yobigoe", "ushiro", "koyanushi", "oyamasama", "utsurumono"};
            for (int i = 0; i < ids.length; i++) {
                cmd(mc, String.format(Locale.ROOT, "summon sounan:%s %d %d %d {NoAI:1b,Rotation:[90f,0f]}", ids[i], 27, y, 1 + i * 2));
            }
            hold(mc, "sounan:flashlight_on");
        }, 40);
        snap("24_monsters");
        add("lineup_camera", mc -> hold(mc, "sounan:phone_camera"), 25);
        snap("25_monsters_camera");
        scene("26_fear_high", mc -> {
            hold(mc, "sounan:flashlight_on");
            cmd(mc, "kill @e[type=!player]");
            at(mc, -60.5, -60.5, 45.0f, 0.0f);
            fear(mc, 92, 0.15f);
        }, 80);
        scene("27_danger_high", mc -> fear(mc, 95, 0.9f), 30);
        scene("28_phone_light", mc -> {
            fear(mc, 30, 0.0f);
            hold(mc, "sounan:phone_light");
        }, 30);
        scene("29_flare", mc -> hold(mc, "sounan:flare_lit"), 30);
        scene("30_flashlight_low", mc -> hold(mc, "sounan:flashlight_low"), 20);
        if (!SHADERS) {
            add("phone_hold", mc -> hold(mc, "sounan:phone"), 10);
            scene("31_phone_home", mc -> mc.setScreen(new PhoneScreen()), 15);
            add("phone_close", mc -> mc.setScreen(null), 5);
            scene("32_trail_map", mc -> mc.setScreen(new TrailMapScreen()), 15);
            add("map_close", mc -> mc.setScreen(null), 5);
            scene("33_note", mc -> mc.setScreen(new NoteScreen(13, mc.getUser().getName() + "|0|0")), 15);
            add("note_close", mc -> mc.setScreen(null), 5);
            add("capture_mode", mc -> {
                cmd(mc, "gamemode adventure");
                cmd(mc, "sounan debug peaceful false");
            }, 10);
            scene("34_capture", mc -> cmd(mc, "sounan debug capture 0"), 8);
            add("capture_wait", mc -> {
            }, 90);
            snap("35_wake");
            add("wake_wait", mc -> {
                cmd(mc, "gamemode creative");
                cmd(mc, "sounan debug peaceful true");
            }, 60);
            add("ending_bad", mc -> {
                EndingScreen e = new EndingScreen(2, mc.getUser().getName() + "|5|0");
                e.localOnly = true;
                mc.setScreen(e);
                e.setTicks(520);
            }, 10);
            snap("36_ending_bad");
            add("ending_good", mc -> {
                EndingScreen e = new EndingScreen(1, mc.getUser().getName() + "|0|0");
                e.localOnly = true;
                mc.setScreen(e);
                e.setTicks(9999);
            }, 10);
            snap("37_ending_good");
            add("ending_close", mc -> mc.setScreen(null), 5);
        }
    }

    private static float yawToward(double dx, double dz) {
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
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
        Sounan.LOGGER.info("[sounan] autoshots: {}", s);
        try {
            Files.writeString(Path.of("sounan_autoshots.txt"), String.join("\n", LOG) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void finish(Minecraft mc) {
        stage = 9;
        log("finished");
        mc.stop();
    }
}
