package com.reisatsu.client;

import com.reisatsu.Reisatsu;
import com.reisatsu.client.gui.AlbumScreen;
import com.reisatsu.client.gui.BreakerScreen;
import com.reisatsu.client.gui.EndingScreen;
import com.reisatsu.client.gui.NoteScreen;
import com.reisatsu.entity.Spirit;
import com.reisatsu.game.GameData;
import com.reisatsu.world.Layout;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * CI only ({@code -Dreisatsu.shots=true}, Xvfb + software GL): a scripted tour saved to {@code run/screenshots/}.
 * First the world's own camera positions ({@code com.reisatsu.debug.WorldTour.shots()}, by reflection), then the
 * game: the intro and the medium, the HUD, the finder, the spirits seen with the eye and through the finder, a photo
 * ejecting and developed with its name, the album, a note, the breaker, the locker, a jump scare, closing time, the
 * exorcism, both endings. Log: {@code run/reisatsu_autoshots.txt}.
 */
public final class AutoShots {
    private static final boolean ENABLED = Boolean.getBoolean("reisatsu.shots");
    private static final boolean SHADERS = Boolean.getBoolean("reisatsu.shots.shaders");
    private static final int MUL = SHADERS ? 3 : 1;
    private static int stage;
    private static int wait;
    private static int ticks;
    private static int index;
    private static int dynamicWait = -1;
    private static float fovOverride = -1;
    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> LOG = new ArrayList<>();

    private record Step(String name, Consumer<Minecraft> action, int waitAfter, boolean shot) {}

    private AutoShots() {}

    public static boolean active() {
        return ENABLED && stage == 3;
    }

    public static boolean overridesFov() {
        return ENABLED && fovOverride > 0;
    }

    static void onTitle(TitleScreen title) {
        if (!ENABLED || stage != 0) return;
        stage = 1;
        wait = 60;
        Minecraft.getInstance().options.pauseOnLostFocus = false;
        log("title screen (shaders=" + SHADERS + ")");
    }

    static void tick(Minecraft mc) {
        if (!ENABLED || stage == 0 || stage == 9) return;
        if (++ticks > 20 * 60 * (SHADERS ? 45 : 32)) {
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
                wait = 10;
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
                    log("step " + s.name() + " pos=" + (mc.player == null ? "?" : mc.player.blockPosition().toShortString())
                            + (SHADERS ? " shaders=" + ShaderCompat.active() : ""));
                } catch (Exception e) {
                    log("step failed " + s.name() + ": " + e);
                    Reisatsu.LOGGER.error("[reisatsu] autoshot step {} failed", s.name(), e);
                }
                wait = dynamicWait >= 0 ? dynamicWait : s.waitAfter() * (s.waitAfter() > 10 ? MUL : 1);
                dynamicWait = -1;
            }
            default -> {
            }
        }
    }

    private static void createWorld(Minecraft mc) {
        try {
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "ReisatsuCI", "");
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            LevelSettings settings = new LevelSettings("ReisatsuCI", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules,
                    WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(20261102L, false, false);
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, options,
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            log("world creation requested");
        } catch (Exception e) {
            log("could not create world: " + e);
            Reisatsu.LOGGER.error("[reisatsu] could not create world", e);
        }
    }

    // ================================================================================================ helpers
    private static void add(String name, Consumer<Minecraft> action, int waitAfter) {
        STEPS.add(new Step(name, action, waitAfter, false));
    }

    private static void snap(String name) {
        STEPS.add(new Step(name, null, 0, true));
    }

    private static void scene(String name, Consumer<Minecraft> setup, int settle) {
        add("go_" + name, setup, settle);
        snap(name);
    }

    private static void cmd(Minecraft mc, String command) {
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }

    private static String dim(Minecraft mc) {
        var sp = mc.getSingleplayerServer();
        return sp != null && sp.getLevel(Layout.DIMENSION) != null ? "reisatsu:hidamari" : "minecraft:overworld";
    }

    private static void at(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        cmd(mc, String.format(Locale.ROOT, "execute in %s run tp @s %.2f %.2f %.2f %.1f %.1f", dim(mc), x, y, z, yaw, pitch));
    }

    /** At a marker, looking along its facing (or a given yaw), with an offset. */
    private static void atMarker(Minecraft mc, String marker, double dx, double dz, float yaw, float pitch) {
        Layout.Marker m = Layout.marker(marker);
        Vec3 c = m.center();
        at(mc, c.x + dx, c.y, c.z + dz, yaw, pitch);
    }

    private static void hold(Minecraft mc, String item) {
        cmd(mc, "item replace entity @s weapon.mainhand with " + item);
    }

    private static void fear(Minecraft mc, float fear, float danger) {
        cmd(mc, String.format(Locale.ROOT, "reisatsu debug fear %.2f %.2f 4000", fear, danger));
    }

    /** Turns the camera onto the nearest entity of a kind. */
    private static void face(Minecraft mc, Class<? extends Entity> kind, float heightFrac) {
        if (mc.player == null || mc.level == null) return;
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!kind.isInstance(e)) continue;
            double d = e.distanceTo(mc.player);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) {
            log("  no " + kind.getSimpleName() + " in view range");
            return;
        }
        double dx = best.getX() - mc.player.getX(), dz = best.getZ() - mc.player.getZ();
        double dy = best.getY() + best.getBbHeight() * heightFrac - mc.player.getEyeY();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setXRot(pitch);
        mc.player.xRotO = pitch;
        log(String.format(Locale.ROOT, "  %s %.1f blocks away", kind.getSimpleName(), bd));
    }

    // ================================================================================================ the tour
    private static void build() {
        STEPS.clear();
        add("rules", mc -> {
            cmd(mc, "gamerule sendCommandFeedback false");
            cmd(mc, "gamerule doMobSpawning false");
        }, 5);
        // the intro plays by itself on a new world
        if (!SHADERS) {
            add("intro_text_wait", mc -> dynamicWait = Math.max(0, 70 - ClientState.introTicks), 0);
            snap("01_intro_text");
            add("intro_title_wait", mc -> dynamicWait = Math.max(0, com.reisatsu.game.Story.INTRO_TEXT + 30 - ClientState.introTicks), 0);
            snap("02_intro_title");
            add("intro_medium_wait", mc -> dynamicWait = Math.max(0, com.reisatsu.game.Story.INTRO_TEXT + 160 - ClientState.introTicks), 0);
            snap("03_intro_medium");
            add("intro_items_wait", mc -> dynamicWait = Math.max(0, com.reisatsu.game.Story.INTRO_TEXT + 560 - ClientState.introTicks), 0);
            snap("04_intro_items");
        }
        add("skip_intro", mc -> cmd(mc, "reisatsu debug skipintro"), 30);
        add("settle", mc -> {
            cmd(mc, "reisatsu debug peaceful true");
            fear(mc, 0.3f, 0.0f);
        }, 40);
        worldTour();
        // the HUD in the dining room
        scene("10_hud_dining", mc -> {
            hold(mc, "reisatsu:reisha_camera{film:10}");
            atMarker(mc, "table_12", 0.0, 3.0, 160.0f, 6.0f);
        }, 60);
        scene("11_flashlight", mc -> hold(mc, "reisatsu:flashlight_on"), 20);
        scene("12_finder_dining", mc -> hold(mc, "reisatsu:reisha_camera_finder{film:10}"), 30);
        scene("13_finder_kitchen", mc -> atMarker(mc, "chef_pass", 0.0, 3.0, 180.0f, 4.0f), 40);
        scene("14_finder_restroom", mc -> atMarker(mc, "sink_w", 0.5, 2.5, 180.0f, 4.0f), 40);
        scene("15_finder_outside", mc -> atMarker(mc, "spawn", 0.0, 0.0, 180.0f, 0.0f), 40);
        // the spirits: with the eye and through the finder
        String[][] poses = {
                {"visitor", "0", "1", "3", "4"}, {"waitress", "0", "1", "2", "4"}, {"chef", "0", "2", "3", "5"},
                {"child", "1", "2", "3", "4"}, {"mirror_woman", "0", "1", "2", "3"}, {"manager", "0", "2", "3", "5"}};
        int n = 20;
        for (String[] p : poses) {
            String id = p[0];
            add("spirit_" + id, mc -> {
                cmd(mc, "kill @e[type=reisatsu:" + id + "]");
                atMarker(mc, "table_12", 0.0, 3.0, 0.0f, 8.0f);
                hold(mc, "reisatsu:reisha_camera{film:10}");
            }, 30);
            add("spawn_" + id, mc -> cmd(mc, "reisatsu debug spawn " + id + " 4"), 20);
            for (int i = 1; i < p.length; i++) {
                String pose = p[i];
                int k = n++;
                add("pose_" + id + "_" + pose, mc -> {
                    cmd(mc, "reisatsu debug pose " + id + " " + pose);
                    cmd(mc, "reisatsu debug face " + id);
                    hold(mc, "reisatsu:reisha_camera{film:10}");
                }, 14);
                add("face_" + id + "_" + pose, mc -> face(mc, Spirit.class, 0.6f), 4);
                snap(String.format(Locale.ROOT, "%02d_%s_eye_p%s", k, id, pose));
                add("finder_" + id + "_" + pose, mc -> hold(mc, "reisatsu:reisha_camera_finder{film:10}"), 16);
                snap(String.format(Locale.ROOT, "%02d_%s_finder_p%s", k, id, pose));
            }
            add("clear_" + id, mc -> cmd(mc, "kill @e[type=reisatsu:" + id + "]"), 5);
        }
        // a photo: the waitress bowing, unaware, facing us
        add("photo_setup", mc -> {
            atMarker(mc, "table_12", 0.0, 3.0, 0.0f, 8.0f);
            hold(mc, "reisatsu:reisha_camera_finder{film:10}");
            cmd(mc, "reisatsu debug spawn waitress 5");
        }, 25);
        add("photo_pose", mc -> {
            cmd(mc, "reisatsu debug pose waitress 2");
            cmd(mc, "reisatsu debug face waitress");
        }, 10);
        add("photo_face", mc -> face(mc, Spirit.class, 0.85f), 6);
        add("photo_shutter", mc -> Finder.shutter(mc), 14);
        snap("50_photo_eject");
        add("photo_develop", mc -> {
        }, 90);
        snap("51_photo_named");
        add("photo_lower", mc -> hold(mc, "reisatsu:reisha_camera{film:9}"), 20);
        if (!SHADERS) {
            scene("52_album", mc -> mc.setScreen(new AlbumScreen()), 12);
            add("album_close", mc -> mc.setScreen(null), 4);
            scene("53_note", mc -> mc.setScreen(new NoteScreen("handover", "")), 12);
            add("note_close", mc -> mc.setScreen(null), 4);
            scene("54_note_report", mc -> mc.setScreen(new NoteScreen("report", "door,menus,call")), 12);
            add("report_close", mc -> mc.setScreen(null), 4);
            scene("55_breaker", mc -> mc.setScreen(new BreakerScreen(0b1101110)), 12);
            add("breaker_close", mc -> mc.setScreen(null), 4);
        }
        scene("56_locker", mc -> {
            atMarker(mc, "lockers", 0.0, 0.0, Layout.marker("lockers").yaw(), 0.0f);
            ClientState.lockerFacing = Layout.marker("lockers").facing().get2DDataValue();
        }, 30);
        add("locker_out", mc -> ClientState.lockerFacing = -1, 5);
        // fear and danger on screen
        scene("57_fear_high", mc -> {
            atMarker(mc, "table_12", 0.0, 3.0, 40.0f, 2.0f);
            hold(mc, "reisatsu:flashlight_on");
            fear(mc, 0.92f, 0.2f);
        }, 50);
        scene("58_danger_high", mc -> fear(mc, 0.95f, 0.95f), 30);
        add("calm", mc -> fear(mc, 0.3f, 0.0f), 20);
        if (!SHADERS) {
            // being taken
            add("caught_prep", mc -> {
                cmd(mc, "gamemode adventure");
                cmd(mc, "reisatsu debug peaceful false");
            }, 10);
            add("caught", mc -> cmd(mc, "reisatsu debug caught waitress"), 4);
            snap("60_jumpscare");
            add("caught_wait", mc -> {
            }, 76);
            snap("61_wake");
            add("caught_done", mc -> {
                cmd(mc, "gamemode creative");
                cmd(mc, "reisatsu debug peaceful true");
            }, 40);
        }
        // closing time
        scene("62_closing", mc -> {
            atMarker(mc, "table_12", 0.0, 3.0, 160.0f, 4.0f);
            cmd(mc, "reisatsu debug closing");
        }, 60);
        add("closing_wait", mc -> {
        }, 160);
        snap("63_closing_manager");
        // the exorcism
        add("exorcism", mc -> cmd(mc, "reisatsu debug exorcism"), 120);
        snap("64_exorcism_walk");
        add("exorcism_wait", mc -> dynamicWait = 20 * 34, 0);
        snap("65_exorcism_table");
        add("exorcism_wait2", mc -> dynamicWait = 20 * 3, 0);
        snap("66_exorcism_spirit");
        add("exorcism_wait3", mc -> dynamicWait = 20 * 6, 0);
        snap("67_exorcism_release");
        if (!SHADERS) {
            add("ending_bad", mc -> {
                EndingScreen e = new EndingScreen(GameData.ENDING_BAD, "3");
                e.localOnly = true;
                mc.setScreen(e);
                e.setTicks(380);
            }, 10);
            snap("70_ending_bad");
            add("ending_good", mc -> {
                EndingScreen e = new EndingScreen(GameData.ENDING_GOOD, "0");
                e.localOnly = true;
                mc.setScreen(e);
                e.setTicks(330);
            }, 10);
            snap("71_ending_good");
            add("ending_card", mc -> {
                if (mc.screen instanceof EndingScreen e) e.setTicks(560);
            }, 10);
            snap("72_ending_card");
            add("ending_close", mc -> mc.setScreen(null), 5);
        }
    }

    /** The world worker's camera positions, by reflection (any record/class with a name and a position). */
    private static void worldTour() {
        try {
            Class<?> c = Class.forName("com.reisatsu.debug.WorldTour");
            Method m = c.getMethod("shots");
            Object list = m.invoke(null);
            if (!(list instanceof List<?> l)) {
                log("WorldTour.shots() did not return a list");
                return;
            }
            int i = 0;
            for (Object o : l) {
                String name = String.valueOf(read(o, "name", "id", "label"));
                Object pos = read(o, "pos", "position", "at");
                double x, y, z;
                if (pos instanceof Vec3 v) {
                    x = v.x;
                    y = v.y;
                    z = v.z;
                } else if (pos instanceof BlockPos b) {
                    x = b.getX() + 0.5;
                    y = b.getY();
                    z = b.getZ() + 0.5;
                } else {
                    x = num(read(o, "x"));
                    y = num(read(o, "y"));
                    z = num(read(o, "z"));
                }
                float yaw = (float) num(read(o, "yaw"));
                float pitch = (float) num(read(o, "pitch"));
                final double fx = x, fy = y, fz = z;
                String shot = String.format(Locale.ROOT, "w%02d_%s", i++, name.replaceAll("[^a-zA-Z0-9_]", "_"));
                scene(shot, mc -> at(mc, fx, fy, fz, yaw, pitch), 60);
            }
            log("world tour: " + i + " shots");
        } catch (ClassNotFoundException e) {
            log("no WorldTour (the world is not merged)");
        } catch (Throwable t) {
            log("WorldTour failed: " + t);
        }
    }

    private static Object read(Object o, String... names) {
        for (String n : names) {
            try {
                Method m = o.getClass().getMethod(n);
                return m.invoke(o);
            } catch (Exception ignored) {
            }
            try {
                Field f = o.getClass().getField(n);
                return f.get(o);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
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
        Reisatsu.LOGGER.info("[reisatsu] autoshots: {}", s);
        try {
            Files.writeString(Path.of("reisatsu_autoshots.txt"), String.join("\n", LOG) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void finish(Minecraft mc) {
        stage = 9;
        log("finished");
        mc.stop();
    }
}
