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
                    try {
                        int n = Preview.prepare(mc);
                        log("preview pack: " + n + " stand-in assets for missing art");
                    } catch (Exception e) {
                        log("preview pack failed: " + e);
                        Reisatsu.LOGGER.error("[reisatsu] preview pack", e);
                    }
                    stage = 4;
                    wait = 40;
                }
                case 4 -> {
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

    // ================================================================================================ CI preview pack
    /**
     * CI only (standalone tour): a resource pack generated at runtime into {@code run/resourcepacks} with stand-in
     * vanilla textures for every REISATSU texture that does not exist yet and plain box models (the blocks' own
     * shapes) for every prop model the art pass has not delivered, so layout screenshots are readable before the art
     * is merged. Assets that exist are never shadowed. Nothing of this is in the repository.
     */
    static final class Preview {
        static final String ID = "reisatsu_ci_preview";

        private Preview() {}

        /** Stand-in vanilla texture for a REISATSU texture or model name. */
        static String vanilla(String n) {
            String[][] exact = {
                    {"dining_floor", "spruce_planks"}, {"kitchen_floor", "red_terracotta"}, {"restroom_floor", "calcite"},
                    {"restroom_floor_dark", "polished_deepslate"}, {"vestibule_floor", "polished_andesite"}, {"entrance_mat", "black_wool"},
                    {"wallpaper", "white_terracotta"}, {"wallpaper_stained", "light_gray_terracotta"}, {"wainscot", "dark_oak_planks"},
                    {"white_tile", "quartz_bricks"}, {"white_tile_dirty", "polished_diorite"}, {"ceiling_tile", "white_wool"},
                    {"ceiling_tile_stained", "smooth_sandstone"}, {"ceiling_light", "light_gray_concrete"}, {"ceiling_light_lit", "glowstone"},
                    {"downlight", "light_gray_concrete"}, {"downlight_lit", "glowstone"}, {"exterior_wall", "smooth_sandstone"},
                    {"facade_brick", "bricks"}, {"brick_sill", "bricks"}, {"roof_tile", "orange_terracotta"}, {"fascia", "orange_concrete"},
                    {"shop_window", "glass"}, {"asphalt", "black_concrete_powder"}, {"parking_line", "white_concrete"},
                    {"road_line", "yellow_concrete"}, {"curb", "smooth_stone"}, {"sidewalk", "stone_bricks"}, {"puddle", "light_blue_stained_glass"},
                    {"rice_paddy", "mud"}, {"hedge", "azalea_leaves"}, {"concrete_wall", "light_gray_concrete"}, {"granite_counter", "polished_blackstone"},
                    {"stainless", "iron_block"}, {"pillar", "stripped_dark_oak_log"}, {"auto_door_panel", "glass"}, {"swing_door", "iron_trapdoor"},
                    {"booth_seat", "red_wool"}, {"booth_partition", "white_stained_glass"}, {"restaurant_table", "dark_oak_planks"},
                    {"restaurant_chair", "spruce_planks"}, {"call_button", "white_concrete"}, {"menu_stand", "orange_wool"},
                    {"menu_scattered", "orange_wool"}, {"water_glass", "glass"}, {"broken_plate", "white_concrete"}, {"register", "gray_concrete"},
                    {"service_bell", "gold_block"}, {"counter", "stripped_oak_log"}, {"cake_case", "white_stained_glass"},
                    {"business_phone", "smooth_sandstone"}, {"reservation_book", "red_wool"}, {"capsule_machine", "red_concrete"},
                    {"waiting_bench", "red_wool"}, {"magazine_rack", "oak_planks"}, {"umbrella_stand", "iron_block"}, {"notice_board", "brown_wool"},
                    {"drink_bar", "light_gray_concrete"}, {"coffee_machine", "black_concrete"}, {"ice_machine", "iron_block"},
                    {"cup_shelf", "iron_block"}, {"soup_warmer", "iron_block"}, {"server_station", "dark_oak_planks"}, {"call_display", "black_concrete"},
                    {"planter", "azalea_leaves"}, {"kids_mat", "light_blue_wool"}, {"toy_shelf", "yellow_concrete"}, {"stuffed_bear", "brown_wool"},
                    {"kids_fence", "yellow_concrete"}, {"high_chair", "oak_planks"}, {"gas_range", "iron_block"}, {"fryer", "iron_block"},
                    {"griddle", "iron_block"}, {"kitchen_counter", "iron_block"}, {"exhaust_hood", "iron_block"}, {"pot_shelf", "iron_block"},
                    {"dish_rack", "iron_block"}, {"kitchen_trash", "gray_concrete"}, {"prep_table", "iron_block"}, {"cutting_board", "oak_planks"},
                    {"kitchen_sink", "iron_block"}, {"dishwasher", "iron_block"}, {"pass_shelf", "iron_block"}, {"pass_bell", "gold_block"},
                    {"walkin_shelf", "iron_block"}, {"frost", "packed_ice"}, {"locker", "light_gray_concrete"}, {"time_recorder", "smooth_sandstone"},
                    {"staff_table", "birch_planks"}, {"folding_chair", "gray_concrete"}, {"bulletin_board", "oak_planks"},
                    {"cardboard_boxes", "stripped_oak_wood"}, {"cleaning_cart", "yellow_concrete"}, {"filing_cabinet", "gray_concrete"},
                    {"safe", "black_concrete"}, {"crt_tv", "black_concrete"}, {"office_desk", "gray_concrete"}, {"office_chair", "black_wool"},
                    {"pc", "black_concrete"}, {"security_monitors", "black_concrete"}, {"security_camera", "black_concrete"},
                    {"breaker_panel", "gray_concrete"}, {"restroom_sink", "white_concrete"}, {"mirror", "black_stained_glass"},
                    {"restroom_mirror", "light_blue_stained_glass"}, {"toilet", "white_concrete"}, {"urinal", "white_concrete"},
                    {"stall_wall", "birch_planks"}, {"hand_dryer", "white_concrete"}, {"paper_towel", "white_concrete"},
                    {"vending_machine", "white_concrete"}, {"pole_sign", "orange_concrete"}, {"roof_sign", "orange_concrete"},
                    {"menu_board", "black_concrete"}, {"bike_rack", "iron_block"}, {"trash_station", "gray_concrete"}, {"ac_unit", "white_concrete"},
                    {"gas_cylinder", "gray_concrete"}, {"dumpster", "green_concrete"}, {"wheel_stop", "smooth_stone"},
                    {"outdoor_planter", "azalea_leaves"}, {"street_lamp", "gray_concrete"}, {"utility_pole", "smooth_stone"},
                    {"guardrail", "iron_block"}, {"chevron_sign", "yellow_concrete"}, {"curve_mirror", "orange_concrete"},
                    {"traffic_light", "black_concrete"}, {"flower_offering", "pink_wool"}, {"note_paper", "white_wool"},
                    {"auto_door_rail", "iron_block"}, {"swing_door_frame", "iron_block"}, {"exit_sign", "lime_concrete"},
                    {"pendant_lamp", "orange_stained_glass"}, {"window_blind", "white_wool"}, {"door_switch", "gray_concrete"}};
            String best = null;
            int len = -1;
            for (String[] e : exact) {
                if (n.equals(e[0])) return e[1];
                if (n.startsWith(e[0] + "_") && e[0].length() > len) {
                    best = e[1];
                    len = e[0].length();
                }
            }
            if (n.endsWith("_bottom") || n.endsWith("_top")) {
                boolean bottom = n.endsWith("_bottom");
                String door = n.contains("occupied") ? "crimson_door" : n.startsWith("office") ? "spruce_door" : n.startsWith("restroom")
                        ? "birch_door" : n.startsWith("stall") ? "jungle_door" : "iron_door";
                return door + (bottom ? "_bottom" : "_top");
            }
            return best != null ? best : "white_concrete";
        }

        /** The glowing stand-in of a lit / switched-on model. */
        static String litVanilla(String n, String base) {
            if (n.contains("pole_sign_tile") || n.contains("roof_sign")) return "ochre_froglight_side";
            if (n.contains("street_lamp_head")) return "ochre_froglight_top";
            if (n.contains("traffic_light")) return "yellow_concrete";
            if (n.contains("call_button")) return "red_concrete";
            if (n.contains("pendant_lamp")) return "shroomlight";
            if (n.contains("security_monitors") || n.contains("crt_tv")) return "light_blue_concrete";
            if (n.contains("gas_range")) return "blue_concrete";
            if (n.contains("pass_shelf")) return "orange_concrete";
            return base;
        }

        static boolean exists(net.minecraft.server.packs.resources.ResourceManager rm, String path) {
            return rm.getResource(new net.minecraft.resources.ResourceLocation(Reisatsu.MODID, path)).isPresent();
        }

        static int prepare(Minecraft mc) throws IOException {
            var rm = mc.getResourceManager();
            Path pack = mc.getResourcePackDirectory().resolve(ID);
            if (Files.exists(pack)) {
                try (var walk = Files.walk(pack)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
            Files.createDirectories(pack);
            Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"REISATSU CI preview (stand-in art)\"}}");
            java.util.Set<String> textures = new java.util.TreeSet<>();
            java.util.Map<String, net.minecraft.world.level.block.Block> models = new java.util.TreeMap<>();
            textures.add("auto_door_panel");
            textures.add("swing_door");
            for (var e : net.minecraftforge.registries.ForgeRegistries.BLOCKS.getEntries()) {
                if (!e.getKey().location().getNamespace().equals(Reisatsu.MODID)) continue;
                String id = e.getKey().location().getPath();
                var bs = rm.getResource(new net.minecraft.resources.ResourceLocation(Reisatsu.MODID, "blockstates/" + id + ".json"));
                if (bs.isEmpty()) continue;
                com.google.gson.JsonObject json;
                try (var r = bs.get().openAsReader()) {
                    json = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
                }
                for (var v : json.getAsJsonObject("variants").entrySet()) {
                    com.google.gson.JsonElement el = v.getValue();
                    String model = (el.isJsonArray() ? el.getAsJsonArray().get(0) : el).getAsJsonObject().get("model").getAsString();
                    if (!model.startsWith(Reisatsu.MODID + ":block/")) continue;
                    models.putIfAbsent(model.substring((Reisatsu.MODID + ":block/").length()), e.getValue());
                }
            }
            int written = 0;
            for (var e : models.entrySet()) {
                String name = e.getKey();
                String path = "models/block/" + name + ".json";
                var res = rm.getResource(new net.minecraft.resources.ResourceLocation(Reisatsu.MODID, path));
                if (res.isPresent()) {
                    try (var r = res.get().openAsReader()) {
                        var json = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
                        if (json.has("textures")) {
                            for (var t : json.getAsJsonObject("textures").entrySet()) {
                                String v = t.getValue().getAsString();
                                if (v.startsWith(Reisatsu.MODID + ":block/")) textures.add(v.substring((Reisatsu.MODID + ":block/").length()));
                            }
                        }
                    }
                    continue;
                }
                writeModel(pack, name, e.getValue());
                written++;
            }
            for (String t : textures) {
                if (exists(rm, "textures/block/" + t + ".png")) continue;
                String v = vanilla(t);
                if (t.endsWith("_lit")) v = litVanilla(t, v);
                var src = rm.getResource(new net.minecraft.resources.ResourceLocation("minecraft", "textures/block/" + v + ".png"));
                if (src.isEmpty()) src = rm.getResource(new net.minecraft.resources.ResourceLocation("minecraft", "textures/block/white_concrete.png"));
                Path out = pack.resolve("assets/" + Reisatsu.MODID + "/textures/block/" + t + ".png");
                Files.createDirectories(out.getParent());
                try (var in = src.get().open()) {
                    Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                written++;
            }
            var repo = mc.getResourcePackRepository();
            repo.reload();
            String packId = "file/" + ID;
            if (repo.getPack(packId) != null) {
                List<String> ids = new ArrayList<>(repo.getSelectedIds());
                if (!ids.contains(packId)) ids.add(packId);
                repo.setSelected(ids);
                mc.options.updateResourcePacks(repo);
            }
            return written;
        }

        /** A box model of the block's own shape (facing north), textured with a vanilla stand-in. */
        static void writeModel(Path pack, String name, net.minecraft.world.level.block.Block block) throws IOException {
            var state = block.defaultBlockState();
            if (state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
                state = state.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH);
            }
            var outline = state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO);
            var coll = state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO);
            var shape = coll.isEmpty() ? outline : coll;
            List<net.minecraft.world.phys.AABB> boxes = shape.isEmpty() ? List.of(new net.minecraft.world.phys.AABB(0.25, 0, 0.25, 0.75, 0.5, 0.75)) : shape.toAabbs();
            String base = vanilla(name);
            boolean lit = name.endsWith("_lit") || name.endsWith("_on") || name.contains("_on_") || name.contains("_lit_");
            String tex = lit ? litVanilla(name, base) : base;
            if (name.startsWith("pole_sign_pole") || name.startsWith("street_lamp_pole")) tex = "gray_concrete";
            StringBuilder sb = new StringBuilder();
            sb.append("{\"parent\":\"minecraft:block/block\",");
            if (tex.contains("glass") || tex.contains("leaves") || tex.contains("ice")) sb.append("\"render_type\":\"minecraft:translucent\",");
            sb.append("\"textures\":{\"particle\":\"minecraft:block/").append(tex).append("\",\"t\":\"minecraft:block/").append(tex).append("\"},");
            sb.append("\"elements\":[");
            boolean first = true;
            for (var b : boxes) {
                if (!first) sb.append(",");
                first = false;
                sb.append(String.format(Locale.ROOT, "{\"from\":[%.3f,%.3f,%.3f],\"to\":[%.3f,%.3f,%.3f],\"shade\":%s,\"faces\":{",
                        b.minX * 16, b.minY * 16, b.minZ * 16, b.maxX * 16, b.maxY * 16, b.maxZ * 16, lit ? "false" : "true"));
                String[] faces = {"north", "south", "east", "west", "up", "down"};
                for (int i = 0; i < faces.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(faces[i]).append("\":{\"texture\":\"#t\"}");
                }
                sb.append("}}");
            }
            sb.append("]}");
            Path out = pack.resolve("assets/" + Reisatsu.MODID + "/models/block/" + name + ".json");
            Files.createDirectories(out.getParent());
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        }
    }
}
