package com.hitori.client;

import com.hitori.Hitori;
import com.hitori.client.gui.EndingScreen;
import com.hitori.client.gui.NamingScreen;
import com.hitori.client.gui.PhoneScreen;
import com.hitori.client.gui.PrintoutScreen;
import com.hitori.entity.OniDoll;
import com.hitori.game.Apt;
import com.hitori.game.Doll;
import com.hitori.game.GameData;
import com.hitori.game.GameManager;
import com.hitori.game.Ritual;
import com.hitori.world.FixtureEvents;
import com.hitori.world.Layout;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * CI only ({@code -Dhitori.shots=true}, Xvfb + software GL): a scripted tour saved to {@code run/screenshots/}. The
 * intro; the world's own camera positions ({@code com.hitori.debug.WorldTour.shots()} by reflection, when it exists);
 * then the game: the HUD at the desk, the printout's pages, each preparation step mid-action, three o'clock, the naming
 * field, the first line, the doll in the tub, the lights off, the eyes-closed count, the stab; the closet, the peek,
 * the laptop in the closet; every camera feed with the doll in its poses, the woman coming closer, the glitches, the
 * phone; fear, danger, the jump scare; each ending and the playback. Log: {@code run/hitori_autoshots.txt}.
 */
public final class AutoShots {
    private static final boolean ENABLED = Boolean.getBoolean("hitori.shots");
    private static final boolean SHADERS = Boolean.getBoolean("hitori.shots.shaders");
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

    public static float fovOverride() {
        return fovOverride;
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
        if (++ticks > 20 * 60 * (SHADERS ? 60 : 40)) {
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
                wait = 5;
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
                    Hitori.LOGGER.error("[hitori] autoshot step {} failed", s.name(), e);
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
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "HitoriCI", "");
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            LevelSettings settings = new LevelSettings("HitoriCI", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules,
                    WorldDataConfiguration.DEFAULT);
            WorldOptions options = new WorldOptions(20261103L, false, false);
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, options,
                    ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            log("world creation requested");
        } catch (Exception e) {
            log("could not create world: " + e);
            Hitori.LOGGER.error("[hitori] could not create world", e);
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

    private static void server(Minecraft mc, BiConsumer<ServerLevel, ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerLevel level = GameManager.level(server);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (level != null && p != null) {
                try {
                    action.accept(level, p);
                } catch (Exception e) {
                    log("  server action failed: " + e);
                }
            }
        });
    }

    private static void at(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        server(mc, (level, p) -> GameManager.teleport(p, level, new Vec3(x, y, z), yaw, pitch));
        if (mc.player != null) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }
    }

    /** Stands in front of a marker (one block out along its facing), looking back at it. */
    private static void before(Minecraft mc, String marker, double dist, float pitch) {
        if (!Layout.has(marker)) return;
        Layout.Marker m = Layout.marker(marker);
        Vec3 c = Vec3.atBottomCenterOf(m.pos());
        Direction f = m.facing();
        double x = c.x + f.getStepX() * dist, z = c.z + f.getStepZ() * dist;
        at(mc, x, Layout.FEET_Y, z, f.getOpposite().toYRot(), pitch);
    }

    private static void hold(Minecraft mc, String item) {
        cmd(mc, "item replace entity @s weapon.mainhand with " + item);
    }

    private static void fear(Minecraft mc, float fear, float danger) {
        cmd(mc, String.format(Locale.ROOT, "hitori debug fear %.2f %.2f 4000", fear, danger));
    }

    private static void ev(Minecraft mc, int type, int arg, String text) {
        server(mc, (level, p) -> GameManager.event(p, type, arg, text));
    }

    /** Turns the view onto the doll (heightFrac of its height). */
    private static void faceDoll(Minecraft mc, float heightFrac) {
        if (mc.player == null || mc.level == null) return;
        Entity best = null;
        for (Entity e : mc.level.entitiesForRendering()) if (e instanceof OniDoll) best = e;
        if (best == null) {
            log("  no doll in view range");
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
        log(String.format(Locale.ROOT, "  doll %.1f blocks away", best.distanceTo(mc.player)));
    }

    private static void logState(Minecraft mc) {
        server(mc, (level, p) -> {
            OniDoll d = Doll.get(level);
            GameData data = GameData.get(level.getServer());
            log("  phase=" + data.phase + " steps=" + Long.toBinaryString(data.steps) + " doll=" + (d == null ? "-" : d.describe())
                    + " cams=" + data.cameras.size());
        });
    }

    // ================================================================================================ the tour
    private static void build() {
        STEPS.clear();
        add("rules", mc -> {
            cmd(mc, "gamerule sendCommandFeedback false");
            cmd(mc, "gamerule doMobSpawning false");
            cmd(mc, "time set 18000");
        }, 5);
        // the intro plays by itself on a new world
        if (!SHADERS) {
            add("intro_wait", mc -> dynamicWait = Math.max(0, 40 - ClientState.introTicks), 0);
            snap("01_intro_text");
            add("intro_title_wait", mc -> dynamicWait = Math.max(0, 200 - ClientState.introTicks), 0);
            snap("02_intro_title");
        }
        add("intro_end", mc -> {
            ClientState.introTicks = -1;
            cmd(mc, "hitori debug peaceful true");
            cmd(mc, "hitori debug director true");
        }, 40);
        snap("03_spawn_hud");
        // like a player getting ready: the lights on where the work is
        add("prep_lights", mc -> cmd(mc, "hitori debug fixture lights true"), 10);
        worldTour();
        // ---- the evening: the printout
        if (!SHADERS) {
            scene("04_printout_1", mc -> mc.setScreen(new PrintoutScreen()), 10);
            scene("04_printout_2", mc -> mc.setScreen(new PrintoutScreen().page(1)), 6);
            scene("04_printout_3", mc -> mc.setScreen(new PrintoutScreen().page(2)), 6);
            add("printout_close", mc -> mc.setScreen(null), 4);
        }
        // ---- phase 0: each step
        scene("10_plush", mc -> {
            cmd(mc, "hitori debug step plush");
            before(mc, "plush_shelf", 1.6, 25.0f);
        }, 40);
        add("plush_take", mc -> server(mc, (level, p) -> FixtureEvents.LISTENER.interact(p, "plush_shelf", Layout.marker("plush_shelf").pos())), 10);
        snap("11_plush_taken");
        add("cut_setup", mc -> {
            cmd(mc, "hitori debug step cut");
            before(mc, "table", 1.4, 42.0f);
        }, 30);
        add("cut", mc -> {
            hold(mc, "hitori:scissors");
            server(mc, (level, p) -> FixtureEvents.LISTENER.interact(p, "table", Layout.marker("table").pos()));
        }, 34);
        snap("12_cut");
        add("rice", mc -> {
            cmd(mc, "hitori debug step rice");
            before(mc, "rice", 1.5, 35.0f);
        }, 30);
        add("rice_go", mc -> server(mc, (level, p) -> FixtureEvents.LISTENER.interact(p, "rice", Layout.marker("rice").pos())), 22);
        snap("13_rice");
        add("nails", mc -> cmd(mc, "hitori debug step nails"), 20);
        add("nails_go", mc -> server(mc, (level, p) -> Ritual.useItem(p, InteractionHand.MAIN_HAND, com.hitori.item.GameItem.Kind.NAIL_CLIPPER)), 16);
        snap("14_nails");
        add("sew", mc -> {
            cmd(mc, "hitori debug step sew");
            before(mc, "table", 1.4, 45.0f);
        }, 20);
        add("sew_go", mc -> server(mc, (level, p) -> Ritual.useItem(p, InteractionHand.MAIN_HAND, com.hitori.item.GameItem.Kind.RED_THREAD)), 60);
        snap("15_sew");
        scene("16_bath", mc -> {
            cmd(mc, "hitori debug step bath");
            before(mc, "bathtub", 1.4, 40.0f);
            server(mc, (level, p) -> Apt.setBathTap(level, true));
        }, 40);
        add("salt", mc -> {
            cmd(mc, "hitori debug step salt");
            before(mc, "kitchen_sink", 1.4, 30.0f);
            hold(mc, "hitori:cup_water");
            cmd(mc, "give @s hitori:salt");
        }, 30);
        add("salt_go", mc -> server(mc, (level, p) -> FixtureEvents.LISTENER.interact(p, "salt", Layout.marker("salt").pos())), 15);
        snap("17_salt");
        add("cameras", mc -> {
            cmd(mc, "hitori debug step cameras");
            cmd(mc, "hitori debug cameras");
        }, 30);
        scene("18_camera_placed", mc -> {
            if (Layout.has("cam_suggest")) {
                // in front of the tatami room's camera, looking up at it
                List<Layout.Marker> l = Layout.markers("cam_suggest");
                Layout.Marker m = l.get(Math.min(2, l.size() - 1));
                Vec3 c = m.center();
                at(mc, c.x + m.facing().getStepX() * 2.2, Layout.FEET_Y, c.z + m.facing().getStepZ() * 2.2, m.facing().getOpposite().toYRot(), -28.0f);
            }
        }, 30);
        scene("19_tv", mc -> {
            cmd(mc, "hitori debug step tv");
            cmd(mc, "hitori debug fixture tv true");
            cmd(mc, "hitori debug fixture lights false");
            before(mc, "tv", 2.6, 8.0f);
        }, 40);
        // ---- three o'clock and phase 1
        add("ritual", mc -> {
            cmd(mc, "hitori debug step name");
            hold(mc, "hitori:ritual_doll");
        }, 30);
        add("three_card", mc -> ev(mc, GameManager.EV_THREE, 0, ""), 40);
        snap("20_three_am");
        add("three_done", mc -> ClientState.threeTicks = -1, 4);
        if (!SHADERS) {
            scene("21_naming", mc -> mc.setScreen(new NamingScreen("みーちゃん")), 12);
            add("naming_ok", mc -> {
                if (mc.screen instanceof NamingScreen n) n.confirmForTest();
            }, 20);
        }
        add("first_oni", mc -> {
            cmd(mc, "hitori debug step first_oni");
            hold(mc, "hitori:ritual_doll");
            before(mc, "table", 1.6, 20.0f);
        }, 20);
        add("first_oni_say", mc -> server(mc, (level, p) -> Ritual.actionKey(p)), 20);
        snap("22_first_oni");
        add("tub_setup", mc -> {
            cmd(mc, "hitori debug step lights");
            cmd(mc, "hitori debug fixture lights false");
            before(mc, "bathtub", 1.5, 50.0f);
        }, 40);
        add("tub_face", mc -> faceDoll(mc, 0.3f), 4);
        snap("23_doll_in_tub");
        scene("24_lights_off", mc -> before(mc, "table", 3.0, 10.0f), 30);
        add("count_setup", mc -> {
            cmd(mc, "hitori debug step count");
            before(mc, "tv", 2.0, 5.0f);
        }, 30);
        add("count_go", mc -> server(mc, (level, p) -> Ritual.actionKey(p)), 50);
        snap("25_eyes_closed");
        add("count_wait", mc -> {
        }, 180);
        add("stab_setup", mc -> {
            cmd(mc, "hitori debug step stab");
            before(mc, "bathtub", 1.3, 55.0f);
        }, 30);
        add("stab_face", mc -> faceDoll(mc, 0.3f), 3);
        add("stab_go", mc -> server(mc, (level, p) -> {
            OniDoll d = Doll.get(level);
            if (d != null) Ritual.interactDoll(p, InteractionHand.MAIN_HAND, d);
        }), 8);
        snap("26_stab");
        // ---- phase 2: the closet
        add("hide_setup", mc -> {
            cmd(mc, "hitori debug step hide");
            cmd(mc, "hitori debug fixture tv true");
            cmd(mc, "hitori debug mouth 1");
        }, 30);
        add("hide_go", mc -> cmd(mc, "hitori debug hide"), 30);
        add("hide_look", mc -> {
            if (mc.player != null && Layout.has("closet_peek")) {
                mc.player.setYRot(Layout.marker("closet_peek").yaw() + 25.0f);
                mc.player.setXRot(5.0f);
            }
        }, 10);
        snap("30_closet");
        add("peek_go", mc -> {
            if (mc.player != null && Layout.has("closet_peek")) mc.player.setYRot(Layout.marker("closet_peek").yaw());
            Peek.startForTest(mc);
        }, 20);
        snap("31_peek");
        add("peek_stop", mc -> Peek.stop(mc), 10);
        add("laptop", mc -> hold(mc, "hitori:laptop"), 6);
        add("laptop_app", mc -> CameraApp.open(0), 24);
        snap("32_closet_laptop");
        // ---- every camera feed, the doll in its poses
        String[][] poses = {{"cam1", "1"}, {"cam2", "2"}, {"cam3", "4"}, {"cam4", "6"}};
        for (int c = 1; c <= 4; c++) {
            int cam = c;
            String[] pz = poses[c - 1];
            add("feed_" + c, mc -> {
                cmd(mc, "hitori debug doll spawn " + pz[0]);
                cmd(mc, "hitori debug doll pose " + pz[1]);
                CameraApp.select(cam);
            }, 30);
            add("feed_log_" + c, AutoShots::logState, 2);
            snap("33_cam" + c + "_pose" + pz[1]);
        }
        add("crawl", mc -> {
            cmd(mc, "hitori debug doll spawn cam2");
            cmd(mc, "hitori debug doll pose 2");
            CameraApp.select(2);
        }, 30);
        snap("34_cam_crawl");
        add("lunge_pose", mc -> cmd(mc, "hitori debug doll pose 7"), 14);
        snap("35_cam_lunge");
        add("float_tub", mc -> {
            cmd(mc, "hitori debug doll spawn tub");
            CameraApp.select(1);
        }, 30);
        snap("36_cam_tub_float");
        // ---- the woman, closer each time
        add("woman", mc -> {
            cmd(mc, "hitori debug doll remove");
            cmd(mc, "hitori debug woman 3");
            CameraApp.select(3);
        }, 30);
        snap("37_woman_0");
        for (int i = 1; i <= 3; i++) {
            int k = i;
            add("woman_away_" + i, mc -> CameraApp.select(2), 10);
            add("woman_back_" + i, mc -> CameraApp.select(3), 24);
            snap("37_woman_" + k);
        }
        add("woman_away_4", mc -> CameraApp.select(2), 10);
        add("woman_back_4", mc -> CameraApp.select(3), 3);
        snap("38_woman_scare");
        add("woman_clear", mc -> cmd(mc, "hitori debug woman remove"), 30);
        // ---- feed glitches
        scene("39_glitch_noise", mc -> CameraApp.glitch(0), 2);
        scene("40_glitch_blackout", mc -> CameraApp.glitch(2), 6);
        add("blackout_end", mc -> {
        }, 34);
        add("freeze", mc -> CameraApp.glitch(1), 5);
        snap("41_glitch_freeze");
        scene("42_glitch_stamp", mc -> CameraApp.glitch(3), 4);
        add("app_close", mc -> CameraApp.close(), 10);
        // ---- the phone
        add("phone", mc -> {
            cmd(mc, "hitori debug unhide");
            hold(mc, "hitori:phone");
            before(mc, "table", 2.5, 10.0f);
        }, 30);
        add("phone_app", mc -> CameraApp.open(1), 24);
        snap("43_phone_app");
        add("phone_app_close", mc -> CameraApp.close(), 10);
        add("phone_msg", mc -> cmd(mc, "hitori debug message 2"), 20);
        snap("44_phone_message");
        add("phone_call", mc -> cmd(mc, "hitori debug call"), 20);
        snap("45_phone_call");
        if (!SHADERS) {
            scene("46_phone_screen", mc -> mc.setScreen(new PhoneScreen()), 10);
            add("phone_screen_close", mc -> mc.setScreen(null), 4);
        }
        add("phone_light", mc -> cmd(mc, "hitori debug light"), 10);
        snap("47_phone_light");
        // ---- fear, danger
        scene("48_fear_high", mc -> {
            cmd(mc, "hitori debug light");
            fear(mc, 0.95f, 0.2f);
        }, 40);
        scene("49_danger_high", mc -> fear(mc, 0.95f, 0.95f), 30);
        add("calm", mc -> fear(mc, 0.3f, 0.0f), 20);
        // ---- phase 3: finding it, spitting
        add("finish_setup", mc -> {
            cmd(mc, "hitori debug step find");
            cmd(mc, "hitori debug doll spawn hide0");
            cmd(mc, "hitori debug doll pose 1");
        }, 30);
        add("finish_go", mc -> server(mc, (level, p) -> {
            OniDoll d = Doll.get(level);
            if (d == null) return;
            Vec3 at = d.position().add(1.8, 0, 0);
            GameManager.teleport(p, level, new Vec3(at.x, d.getY(), at.z), 90.0f, 25.0f);
        }), 20);
        add("finish_face", mc -> faceDoll(mc, 0.4f), 6);
        snap("50_found");
        // ---- the endings
        add("oni", mc -> {
            cmd(mc, "hitori debug peaceful false");
            cmd(mc, "gamemode adventure");
            cmd(mc, "hitori debug ending oni");
        }, GameManager.LUNGE_CUT + 5);
        snap("60_jumpscare");
        add("oni_wait", mc -> {
        }, 180);
        add("oni_screen", mc -> {
            if (mc.screen instanceof EndingScreen e) {
                e.localOnly = true;
                e.setTicks(60);
            }
        }, 4);
        snap("61_ending_oni");
        add("oni_feed", mc -> {
            if (mc.screen instanceof EndingScreen e) e.setTicks(200);
        }, 4);
        snap("62_ending_oni_feed");
        add("good_setup", mc -> {
            if (mc.screen != null) mc.setScreen(null);
            cmd(mc, "gamemode creative");
            cmd(mc, "hitori reset");
        }, 20);
        add("good_start", mc -> {
            cmd(mc, "hitori debug step burn");
            cmd(mc, "hitori debug cameras");
        }, 30);
        // a night's worth of recordings for the playback: each feed for a while
        add("record_app", mc -> {
            ClientState.introTicks = -1;
            hold(mc, "hitori:laptop");
        }, 6);
        add("record_open", mc -> CameraApp.open(0), 70);
        for (int c = 2; c <= 4; c++) {
            int cam = c;
            add("record_cam" + c, mc -> CameraApp.select(cam), 70);
        }
        add("record_close", mc -> CameraApp.close(), 10);
        add("good", mc -> {
            ClientState.introTicks = -1;
            cmd(mc, "hitori debug ending good");
        }, 30);
        snap("63_morning");
        add("good_wait", mc -> {
        }, 120);
        add("good_text", mc -> {
            if (mc.screen instanceof EndingScreen e) {
                e.localOnly = true;
                e.setTicks(320);
            }
        }, 4);
        snap("64_ending_good");
        add("good_play", mc -> {
            if (mc.screen instanceof EndingScreen e) e.setTicks(e.playStart() + 60);
        }, 4);
        snap("65_playback");
        add("good_final", mc -> {
            if (mc.screen instanceof EndingScreen e) e.setTicks(e.finalStart() + 85);
        }, 4);
        snap("66_playback_fusuma");
        add("good_card", mc -> {
            if (mc.screen instanceof EndingScreen e) e.setTicks(e.cardAt() + 40);
        }, 4);
        snap("67_ending_card");
        add("mikan_setup", mc -> {
            if (mc.screen != null) mc.setScreen(null);
            cmd(mc, "hitori reset");
        }, 20);
        add("mikan_start", mc -> {
            cmd(mc, "hitori debug step hide");
            cmd(mc, "hitori debug cameras");
        }, 30);
        add("mikan", mc -> {
            ClientState.introTicks = -1;
            cmd(mc, "hitori debug ending mikan");
        }, 160);
        add("mikan_screen", mc -> {
            if (mc.screen instanceof EndingScreen e) {
                e.localOnly = true;
                e.setTicks(260);
            }
        }, 4);
        snap("68_ending_mikan");
        add("soto_setup", mc -> {
            if (mc.screen != null) mc.setScreen(null);
            cmd(mc, "hitori reset");
        }, 20);
        add("soto_start", mc -> cmd(mc, "hitori debug step knife"), 30);
        add("soto", mc -> {
            ClientState.introTicks = -1;
            cmd(mc, "hitori debug ending soto");
        }, 110);
        snap("69_soto_corridor");
        add("soto_map", mc -> ClientState.mapTicks = 30, 6);
        snap("70_soto_map");
        add("soto_wait", mc -> {
        }, 20 * 30 + 20);
        add("soto_screen", mc -> {
            if (mc.screen instanceof EndingScreen e) {
                e.localOnly = true;
                e.setTicks(150);
            }
        }, 4);
        snap("71_ending_soto");
        add("end", mc -> {
            if (mc.screen != null) mc.setScreen(null);
        }, 5);
    }

    /** The world's camera positions ({@code com.hitori.debug.WorldTour.shots()}, by reflection), HUD hidden. */
    private static void worldTour() {
        List<Object> shots;
        try {
            Class<?> c = Class.forName("com.hitori.debug.WorldTour");
            Method m = c.getMethod("shots");
            shots = new ArrayList<>((List<?>) m.invoke(null));
        } catch (ClassNotFoundException e) {
            log("no WorldTour yet");
            return;
        } catch (Throwable e) {
            log("WorldTour.shots() failed: " + e);
            return;
        }
        add("tour_begin", mc -> {
            cmd(mc, "gamemode spectator");
            mc.options.hideGui = true;
        }, 10);
        for (Object s : shots) {
            String name = String.valueOf(get(s, "name"));
            Object x = get(s, "x"), y = get(s, "y"), z = get(s, "z"), yaw = get(s, "yaw"), pitch = get(s, "pitch");
            Object nv = get(s, "nightVision");
            if (x == null || y == null || z == null) continue;
            if (SHADERS && Boolean.TRUE.equals(nv)) continue;
            add("go_world_" + name, mc -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    ServerLevel level = GameManager.level(server);
                    if (level == null) return;
                    try {
                        Method apply = s.getClass().getMethod("apply", ServerLevel.class);
                        apply.invoke(s, level);
                    } catch (Throwable ignored) {
                    }
                });
                boolean night = Boolean.TRUE.equals(nv);
                server(mc, (level, p) -> {
                    if (night) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 99999, 0, false, false));
                    else p.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                });
                cmd(mc, String.format(Locale.ROOT, "execute in %s run tp @s %.2f %.2f %.2f %.1f %.1f", Layout.DIMENSION.location(),
                        ((Number) x).doubleValue(), ((Number) y).doubleValue(), ((Number) z).doubleValue(),
                        yaw == null ? 0.0f : ((Number) yaw).floatValue(), pitch == null ? 0.0f : ((Number) pitch).floatValue()));
            }, 60);
            snap("world_" + name);
        }
        add("tour_end", mc -> {
            server(mc, (level, p) -> p.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION));
            cmd(mc, "gamemode creative");
            mc.options.hideGui = false;
            mc.gui.getChat().clearMessages(false);
        }, 10);
        log("world tour: " + shots.size() + " shots");
    }

    private static Object get(Object rec, String name) {
        try {
            return rec.getClass().getMethod(name).invoke(rec);
        } catch (Throwable t) {
            return null;
        }
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

    private static synchronized void log(String s) {
        LOG.add(s);
        Hitori.LOGGER.info("[hitori] autoshots: {}", s);
        try {
            Files.writeString(Path.of("hitori_autoshots.txt"), String.join("\n", LOG) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void finish(Minecraft mc) {
        stage = 9;
        log("finished");
        mc.stop();
    }
}
