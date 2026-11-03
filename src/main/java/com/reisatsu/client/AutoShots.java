package com.reisatsu.client;

import com.reisatsu.Reisatsu;
import com.reisatsu.client.gui.AlbumScreen;
import com.reisatsu.client.gui.BreakerScreen;
import com.reisatsu.client.gui.EndingScreen;
import com.reisatsu.client.gui.NoteScreen;
import com.reisatsu.debug.WorldTour;
import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.entity.Visitor;
import com.reisatsu.game.GameData;
import com.reisatsu.game.GameManager;
import com.reisatsu.game.Spirits;
import com.reisatsu.world.Circuit;
import com.reisatsu.world.FixtureEvents;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * CI only ({@code -Dreisatsu.shots=true}, Xvfb + software GL): a scripted tour saved to {@code run/screenshots/}.
 * The intro, then the world's own camera positions ({@link WorldTour#shots()}, {@code world_<name>.png}, HUD hidden),
 * then the game in the real restaurant: the HUD and the finder, each spirit at work where it lives (with the eye and
 * through the finder), a pose sweep, a photo ejecting and developing, the album, notes, the breaker, a locker, fear,
 * a jump scare and waking, closing time, the exorcism, both endings. Log: {@code run/reisatsu_autoshots.txt}.
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
        if (++ticks > 20 * 60 * (SHADERS ? 60 : 42)) {
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

    /** Runs on the integrated server's thread, in the restaurant's level. */
    private static void server(Minecraft mc, Consumer<ServerLevel> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerLevel level = GameManager.level(server);
            if (level != null) action.accept(level);
        });
    }

    private static void server(Minecraft mc, BiConsumer<ServerLevel, ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerLevel level = GameManager.level(server);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (level != null && p != null) action.accept(level, p);
        });
    }

    private static void at(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        cmd(mc, String.format(Locale.ROOT, "execute in %s run tp @s %.2f %.2f %.2f %.1f %.1f", Layout.DIMENSION.location(), x, y, z, yaw, pitch));
    }

    private static void at(Minecraft mc, double x, double z, float yaw, float pitch) {
        at(mc, x, Layout.FEET_Y, z, yaw, pitch);
    }

    private static void hold(Minecraft mc, String item) {
        cmd(mc, "item replace entity @s weapon.mainhand with " + item);
    }

    private static void fear(Minecraft mc, float fear, float danger) {
        cmd(mc, String.format(Locale.ROOT, "reisatsu debug fear %.2f %.2f 4000", fear, danger));
    }

    /** Turns the camera onto the nearest spirit (heightFrac of its height). */
    private static void face(Minecraft mc, float heightFrac) {
        if (mc.player == null || mc.level == null) return;
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Spirit)) continue;
            double d = e.distanceTo(mc.player);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) {
            log("  no spirit in view range");
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
        log(String.format(Locale.ROOT, "  %s %.1f blocks away", best.getType().getDescriptionId(), bd));
    }

    private static void clearSpirits(Minecraft mc) {
        server(mc, (ServerLevel level) -> {
            Spirits.paused = true;
            Spirits.removeAll(level);
        });
    }

    // ================================================================================================ the tour
    private static void build() {
        STEPS.clear();
        add("rules", mc -> {
            cmd(mc, "gamerule sendCommandFeedback false");
            cmd(mc, "gamerule doMobSpawning false");
        }, 5);
        // the intro plays by itself on a new world, at the real spawn with the medium and her car
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
        snap("05_after_intro");
        worldTour();
        // ---- the HUD and the finder in the real rooms
        add("lights_start", mc -> server(mc, (ServerLevel level) -> Fixtures.resetAll(level)), 40);
        scene("10_hud_dining", mc -> {
            hold(mc, "reisatsu:reisha_camera{film:10}");
            at(mc, 16.5, 13.5, -100.0f, 6.0f);
        }, 70);
        scene("11_flashlight", mc -> hold(mc, "reisatsu:flashlight_on"), 20);
        scene("12_finder_dining", mc -> hold(mc, "reisatsu:reisha_camera_finder{film:10}"), 30);
        scene("13_finder_kitchen", mc -> at(mc, 25.5, 8.5, 180.0f, 8.0f), 50);
        scene("14_finder_restroom", mc -> at(mc, 10.5, 19.5, 180.0f, 0.0f), 50);
        scene("15_finder_outside", mc -> at(mc, 4.5, 41.5, 180.0f, 0.0f), 50);
        add("finder_down", mc -> hold(mc, "reisatsu:reisha_camera{film:10}"), 10);
        // ---- each spirit at work where it lives: with the eye, then through the finder
        inSitu("chef", SpiritKind.CHEF, 24.5, 13.5, 180.0f, 6.0f);
        inSitu("waitress", SpiritKind.WAITRESS, 32.5, 17.5, 180.0f, 4.0f);
        inSitu("child", SpiritKind.CHILD, 9.5, 27.5, -90.0f, 12.0f);
        inSitu("mirror_woman", SpiritKind.MIRROR_WOMAN, 10.5, 19.5, 180.0f, 2.0f);
        inSitu("manager", SpiritKind.MANAGER, 12.5, 5.5, 70.0f, 10.0f);
        inSitu("visitor", SpiritKind.VISITOR, 34.5, 24.5, 14.0f, 14.0f);
        // ---- a pose sweep in the dining room's north aisle, lights on
        add("sweep_lights", mc -> {
            clearSpirits(mc);
            server(mc, (ServerLevel level) -> Fixtures.setCircuit(level, Circuit.DINING, true));
        }, 20);
        String[][] poses = {
                {"visitor", "1", "4"}, {"waitress", "0", "2"}, {"chef", "0", "5"},
                {"child", "2", "6"}, {"mirror_woman", "1", "3"}, {"manager", "1", "5"}};
        int n = 40;
        for (String[] p : poses) {
            String id = p[0];
            add("sweep_" + id, mc -> {
                cmd(mc, "kill @e[type=reisatsu:" + id + "]");
                at(mc, 16.5, 13.5, -90.0f, 6.0f);
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
                }, 24);
                add("face_" + id + "_" + pose, mc -> face(mc, 0.6f), 4);
                snap(String.format(Locale.ROOT, "%02d_%s_eye_p%s", k, id, pose));
                add("finder_" + id + "_" + pose, mc -> hold(mc, "reisatsu:reisha_camera_finder{film:10}"), 16);
                snap(String.format(Locale.ROOT, "%02d_%s_finder_p%s", k, id, pose));
            }
            add("clear_" + id, mc -> cmd(mc, "kill @e[type=reisatsu:" + id + "]"), 5);
        }
        // ---- a photo: the waitress bowing, unaware, facing us
        add("photo_setup", mc -> {
            at(mc, 16.5, 13.5, -90.0f, 6.0f);
            hold(mc, "reisatsu:reisha_camera_finder{film:10}");
            cmd(mc, "reisatsu debug spawn waitress 5");
        }, 25);
        add("photo_pose", mc -> {
            cmd(mc, "reisatsu debug pose waitress 2");
            cmd(mc, "reisatsu debug face waitress");
        }, 10);
        add("photo_face", mc -> face(mc, 0.85f), 6);
        add("photo_shutter", mc -> Finder.shutter(mc), 14);
        snap("50_photo_eject");
        add("photo_develop", mc -> {
        }, 90);
        snap("51_photo_named");
        add("photo_lower", mc -> {
            hold(mc, "reisatsu:reisha_camera{film:9}");
            cmd(mc, "kill @e[type=reisatsu:waitress]");
        }, 20);
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
        // ---- a staff locker: in through the world's own block behaviour
        add("locker_go", mc -> {
            Layout.Marker l = Layout.marker("film_2");
            Vec3 front = Vec3.atBottomCenterOf(l.pos().relative(l.facing()));
            at(mc, front.x, front.y, front.z, l.facing().getOpposite().toYRot(), 0.0f);
        }, 40);
        scene("56_locker", mc -> server(mc, (ServerLevel level, ServerPlayer p) -> {
            if (FixtureEvents.LISTENER.hide(p, Layout.marker("film_2").pos())) Fixtures.flapLocker(level, Layout.marker("film_2").pos());
        }), 30);
        add("locker_out", mc -> server(mc, (ServerLevel level, ServerPlayer p) -> FixtureEvents.LISTENER.unhide(p, Layout.marker("film_2").pos())), 20);
        // ---- fear and danger on screen
        scene("57_fear_high", mc -> {
            at(mc, 16.5, 13.5, -60.0f, 2.0f);
            hold(mc, "reisatsu:flashlight_on");
            fear(mc, 0.92f, 0.2f);
        }, 50);
        scene("58_danger_high", mc -> fear(mc, 0.95f, 0.95f), 30);
        add("calm", mc -> fear(mc, 0.3f, 0.0f), 20);
        if (!SHADERS) {
            // ---- being taken: the real jump scare art, then waking by the car
            add("caught_prep", mc -> {
                cmd(mc, "gamemode adventure");
                cmd(mc, "reisatsu debug peaceful false");
            }, 10);
            add("caught", mc -> cmd(mc, "reisatsu debug caught chef"), 4);
            snap("60_jumpscare");
            add("caught_wait", mc -> {
            }, 76);
            snap("61_wake");
            add("caught_done", mc -> {
                cmd(mc, "gamemode creative");
                cmd(mc, "reisatsu debug peaceful true");
            }, 60);
            snap("61_wake_car");
        }
        // ---- closing time
        add("closing_spirits", mc -> server(mc, (ServerLevel level) -> Spirits.paused = false), 5);
        scene("62_closing", mc -> {
            at(mc, 16.5, 13.5, -100.0f, 4.0f);
            cmd(mc, "reisatsu debug closing");
        }, 60);
        add("closing_wait", mc -> {
        }, 160);
        snap("63_closing_manager");
        // ---- the exorcism at table 7
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

    /** One spirit alone, spawned at home and left to its routine; seen with the eye, then through the finder. */
    private static void inSitu(String id, SpiritKind kind, double x, double z, float yaw, float pitch) {
        add("situ_" + id, mc -> {
            clearSpirits(mc);
            hold(mc, "reisatsu:reisha_camera{film:10}");
            at(mc, x, z, yaw, pitch);
            server(mc, (ServerLevel level) -> {
                Spirit s = Spirits.spawnAtHome(level, kind);
                if (s instanceof Visitor v) v.seatNow();
                if (s == null) Reisatsu.LOGGER.warn("[reisatsu] autoshots: {} did not spawn at home", id);
            });
        }, 90);
        snap("30_situ_" + id + "_eye");
        add("situ_finder_" + id, mc -> hold(mc, "reisatsu:reisha_camera_finder{film:10}"), 20);
        snap("31_situ_" + id + "_finder");
        add("situ_down_" + id, mc -> hold(mc, "reisatsu:reisha_camera{film:10}"), 6);
    }

    /** The world's camera positions with its lighting setups, the HUD hidden, as a spectator (no collisions). */
    private static void worldTour() {
        List<WorldTour.Shot> shots = WorldTour.shots();
        add("tour_begin", mc -> {
            clearSpirits(mc);
            cmd(mc, "gamemode spectator");
            mc.options.hideGui = true;
        }, 10);
        boolean nv = false;
        WorldTour.Shot last = null;
        for (WorldTour.Shot s : shots) {
            final boolean wantNv = s.nightVision();
            if (wantNv != nv) {
                nv = wantNv;
                add("nv_" + nv, mc -> cmd(mc, wantNv ? "effect give @s night_vision 99999 0 true" : "effect clear @s night_vision"), 5);
            }
            double dist = last == null ? 99 : Math.sqrt(Math.pow(s.x() - last.x(), 2) + Math.pow(s.y() - last.y(), 2) + Math.pow(s.z() - last.z(), 2));
            int settle = (int) Math.min(110, 30 + dist * 1.5);
            if (s.lights() != WorldTour.Lights.KEEP) settle = Math.max(settle, 70);
            add("go_world_" + s.name(), mc -> {
                if (s.lights() != WorldTour.Lights.KEEP) server(mc, (ServerLevel level) -> s.apply(level));
                at(mc, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
            }, settle);
            snap("world_" + s.name());
            last = s;
        }
        add("tour_end", mc -> {
            cmd(mc, "effect clear @s night_vision");
            cmd(mc, "gamemode creative");
            mc.options.hideGui = false;
            server(mc, (ServerLevel level) -> Spirits.paused = false);
        }, 10);
        log("world tour: " + shots.size() + " shots");
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
