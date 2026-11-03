package com.reisatsu.debug;

import com.reisatsu.Reisatsu;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.FileUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Named camera positions that show the restaurant and its surroundings (owner: world). The game's screenshot tour
 * ({@code client/AutoShots}) starts with {@link #shots()}. Each shot is a feet position with a view direction; a
 * shot may ask for a lighting setup first ({@link Lights}) and for night vision (to judge geometry in the dark).
 * <p>
 * Until the game's tour exists, {@link Runner} plays these shots by itself when {@code -Dreisatsu.shots=true}:
 * it creates a creative test world, takes {@code world_<name>.png} into {@code run/screenshots}, logs to
 * {@code run/reisatsu_autoshots.txt} (ending with "finished") and quits.
 */
public final class WorldTour {
    private WorldTour() {}

    /** Lighting setup applied (on the server) before a shot. */
    public enum Lights {
        /** Leave everything as it is. */
        KEEP,
        /** The start of the night: {@link Fixtures#resetAll}. */
        START,
        /** Closing time: {@link Fixtures#allLightsOn}. */
        ALL_ON
    }

    public record Shot(String name, double x, double y, double z, float yaw, float pitch, Lights lights, boolean nightVision) {
        /** Applies the shot's lighting setup on the server thread (call from the server thread). */
        public void apply(ServerLevel level) {
            switch (lights) {
                case START -> Fixtures.resetAll(level);
                case ALL_ON -> Fixtures.allLightsOn(level);
                default -> {
                }
            }
        }
    }

    private static Shot s(String name, double x, double z, float yaw, float pitch) {
        return new Shot(name, x, Layout.FEET_Y, z, yaw, pitch, Lights.KEEP, false);
    }

    private static Shot s(String name, double x, double y, double z, float yaw, float pitch) {
        return new Shot(name, x, y, z, yaw, pitch, Lights.KEEP, false);
    }

    private static Shot with(Shot s, Lights l, boolean nv, String suffix) {
        return new Shot(s.name() + suffix, s.x(), s.y(), s.z(), s.yaw(), s.pitch(), l, nv);
    }

    /** Outside: the approach on the road, the façade and entrance, the sign, the lot, the curve, the yards. */
    public static List<Shot> exterior() {
        List<Shot> l = new ArrayList<>();
        l.add(s("ext_road_approach", -26.5, 53.5, -72, 2));
        l.add(s("ext_front", 20.5, 46.5, 180, -6));
        l.add(s("ext_entrance", 4.5, 37.5, 172, -8));
        l.add(s("ext_vending", 10.5, 34.5, 125, 0));
        l.add(s("ext_sign", 37.5, 40.5, -45, -34));
        l.add(s("ext_lot", -6.5, 47.5, -140, 4));
        l.add(s("ext_window_table7", 34.5, 36.5, 180, 2));
        l.add(s("ext_curve", 52.5, 54.5, -95, 2));
        l.add(s("ext_flowers", 72.5, 48.5, -45, 22));
        l.add(s("ext_curve_mirror", 61.5, 53.5, -40, -8));
        l.add(s("ext_traffic_light", -18.5, 54.5, 95, 0));
        l.add(s("ext_back_yard", 38.5, -8.5, 62, 4));
        l.add(s("ext_staff_path", -2.5, 28.5, 180, 4));
        l.add(s("ext_east_side", 44.5, 31.5, 165, 4));
        l.add(s("ext_overview", 20.5, 88, 72.5, 180, 32));
        l.add(s("ext_overview_curve", 30.5, 92, 92.5, -150, 34));
        return l;
    }

    /** Inside: every room. */
    public static List<Shot> interior() {
        List<Shot> l = new ArrayList<>();
        l.add(s("vestibule", 7.5, 27.5, 110, 8));
        l.add(s("waiting", 1.5, 24.5, -90, 6));
        l.add(s("register", 8.5, 25.5, -125, 12));
        l.add(s("kids_corner", 11.5, 25.5, 10, 16));
        l.add(s("dining_west", 15.5, 12.5, -40, 10));
        l.add(s("dining_east", 37.5, 26.5, 130, 8));
        l.add(s("dining_booths", 22.5, 26.5, -88, 8));
        l.add(s("table7", 31.5, 25.5, -40, 18));
        l.add(s("drink_bar", 19.5, 15.5, 155, 6));
        l.add(s("pass_window", 24.5, 14.5, 180, 4));
        l.add(s("kitchen_line", 25.5, 8.5, 180, 8));
        l.add(s("kitchen_dish_pit", 31.5, 6.5, -120, 10));
        l.add(s("fridge", 17.5, 3.5, 160, 10));
        l.add(s("corridor", 13.5, 8.5, 90, 4));
        l.add(s("staff_room", 5.5, 5.5, 135, 10));
        l.add(s("office", 12.5, 5.5, 75, 8));
        l.add(s("office_window", 9.5, 9.5, 180, 4));
        l.add(s("storage", 3.5, 11.5, 15, 10));
        l.add(s("mens", 3.5, 21.5, 175, 8));
        l.add(s("womens", 11.5, 21.5, 160, 6));
        l.add(s("mirror", 10.5, 19.5, 180, 0));
        l.add(s("cavity", 11.5, 11.5, 30, 6));
        return l;
    }

    /** The full tour: outside at night, outside with night vision, inside at the start of the night, inside with every light on. */
    public static List<Shot> shots() {
        List<Shot> out = new ArrayList<>();
        List<Shot> ext = exterior();
        for (int i = 0; i < ext.size(); i++) out.add(i == 0 ? with(ext.get(i), Lights.START, false, "") : ext.get(i));
        for (Shot s : ext) if (!s.name().startsWith("ext_overview")) out.add(with(s, Lights.KEEP, true, "_nv"));
        List<Shot> in = interior();
        for (int i = 0; i < in.size(); i++) out.add(with(in.get(i), i == 0 ? Lights.START : Lights.KEEP, false, "_off"));
        for (int i = 0; i < in.size(); i++) out.add(with(in.get(i), i == 0 ? Lights.ALL_ON : Lights.KEEP, false, "_on"));
        return out;
    }

    // ================================================================================================ the standalone runner
    /** True when this standalone tour should run (CI flag set and the game's own tour is not there). */
    public static boolean standalone() {
        if (!Boolean.getBoolean("reisatsu.shots")) return false;
        try {
            Class.forName("com.reisatsu.client.AutoShots", false, WorldTour.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }

    @Mod.EventBusSubscriber(modid = Reisatsu.MODID, value = Dist.CLIENT)
    public static final class Runner {
        private static final boolean SHADERS = Boolean.getBoolean("reisatsu.shots.shaders");
        private static Boolean active;
        private static int stage;
        private static int wait;
        private static int ticks;
        private static int index;
        private static final List<Step> STEPS = new ArrayList<>();
        private static final List<String> LOG = new ArrayList<>();

        private record Step(String name, Consumer<Minecraft> action, int waitAfter, boolean shot) {}

        private Runner() {}

        private static boolean active() {
            if (active == null) active = standalone();
            return active;
        }

        @SubscribeEvent
        public static void screen(ScreenEvent.Init.Post event) {
            if (!(event.getScreen() instanceof TitleScreen) || !active() || stage != 0) return;
            stage = 1;
            wait = 60;
            Minecraft.getInstance().options.pauseOnLostFocus = false;
            log("title screen (world tour, shaders=" + SHADERS + ")");
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || stage == 0 || stage == 9) return;
            Minecraft mc = Minecraft.getInstance();
            if (++ticks > 20 * 60 * (SHADERS ? 50 : 36)) {
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
                    createWorld(mc);
                    stage = 2;
                }
                case 2 -> {
                    if (mc.level == null || mc.player == null || mc.screen != null) return;
                    stage = 3;
                    wait = 40;
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
                                + " pos=" + (mc.player == null ? "?" : mc.player.blockPosition().toShortString()));
                    } catch (Exception e) {
                        log("step failed " + s.name() + ": " + e);
                        Reisatsu.LOGGER.error("[reisatsu] world tour step {} failed", s.name(), e);
                    }
                    wait = s.waitAfter() * (SHADERS && s.waitAfter() > 10 ? 3 : 1);
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
                LevelSettings settings = new LevelSettings("ReisatsuCI", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
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

        private static void cmd(Minecraft mc, String command) {
            if (mc.player != null) mc.player.connection.sendCommand(command);
        }

        private static void onServer(Minecraft mc, Consumer<ServerLevel> action) {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                ServerLevel level = server.getLevel(Layout.DIMENSION);
                if (level != null) action.accept(level);
            });
        }

        private static void build() {
            STEPS.clear();
            STEPS.add(new Step("rules", mc -> {
                cmd(mc, "gamerule sendCommandFeedback false");
                cmd(mc, "gamerule doMobSpawning false");
                cmd(mc, "gamemode spectator");
                mc.options.hideGui = true;
            }, 10, false));
            STEPS.add(new Step("enter", mc -> {
                Layout.Marker sp = Layout.marker("spawn");
                cmd(mc, String.format(Locale.ROOT, "execute in %s run tp @s %.1f %d %.1f 180 0", Layout.DIMENSION.location(),
                        sp.pos().getX() + 0.5, sp.pos().getY(), sp.pos().getZ() + 0.5));
            }, 100, false));
            STEPS.add(new Step("ensure_built", mc -> onServer(mc, level -> {
                Fixtures.ensureBuilt(level);
                Fixtures.resetAll(level);
            }), 60, false));
            boolean nv = false;
            Shot last = null;
            for (Shot s : shots()) {
                final boolean wantNv = s.nightVision();
                if (wantNv != nv) {
                    nv = wantNv;
                    STEPS.add(new Step("nv_" + nv, mc -> cmd(mc, wantNv ? "effect give @s night_vision 99999 0 true" : "effect clear @s night_vision"),
                            5, false));
                }
                double dist = last == null ? 99 : Math.sqrt(Math.pow(s.x() - last.x(), 2) + Math.pow(s.y() - last.y(), 2) + Math.pow(s.z() - last.z(), 2));
                int settle = (int) Math.min(110, 30 + dist * 1.5);
                STEPS.add(new Step("go_" + s.name(), mc -> {
                    if (s.lights() != Lights.KEEP) onServer(mc, s::apply);
                    cmd(mc, String.format(Locale.ROOT, "execute in %s run tp @s %.2f %.2f %.2f %.1f %.1f", Layout.DIMENSION.location(),
                            s.x(), s.y(), s.z(), s.yaw(), s.pitch()));
                }, Math.max(settle, s.lights() != Lights.KEEP ? 70 : 0), false));
                STEPS.add(new Step(s.name(), null, 0, true));
                last = s;
            }
        }

        private static void shot(Minecraft mc, String name) {
            try {
                Screenshot.grab(mc.gameDirectory, "world_" + name + ".png", mc.getMainRenderTarget(), c -> {
                });
                log("shot world_" + name);
            } catch (Exception e) {
                log("screenshot failed " + name + ": " + e);
            }
        }

        private static void log(String s) {
            LOG.add(s);
            Reisatsu.LOGGER.info("[reisatsu] world tour: {}", s);
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
}
