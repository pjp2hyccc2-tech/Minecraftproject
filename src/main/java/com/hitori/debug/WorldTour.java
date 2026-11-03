package com.hitori.debug;

import com.hitori.Hitori;
import com.hitori.world.Fixtures;
import com.hitori.world.Layout;
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
 * Named camera positions that show コーポ白樺 and room 203 (owner: world). The game's screenshot tour
 * ({@code client/AutoShots}) can start with {@link #shots()}. Each shot is a feet position with a view direction; a
 * shot may ask for a lighting setup first ({@link Lights}) and for night vision (to judge geometry in the dark).
 * <p>
 * Until the game's tour exists, {@link Runner} plays these shots by itself when {@code -Dhitori.shots=true}: it
 * creates a creative test world, takes {@code world_<name>.png} into {@code run/screenshots}, logs to
 * {@code run/hitori_autoshots.txt} (ending with "finished") and quits.
 */
public final class WorldTour {
    private WorldTour() {}

    /** Lighting setup applied (on the server) before a shot. */
    public enum Lights {
        /** Leave everything as it is. */
        KEEP,
        /** The start of the evening: {@link Fixtures#resetAll} (only the western room lit). */
        START,
        /** Every light on, the TV and the desk lamp on, the curtains drawn. */
        ALL_ON,
        /** The ritual's darkness: every light off, only the TV (static) on. */
        DARK,
        /** Hiding: dark, the TV on, the closet's fusuma cracked open. */
        PEEK
    }

    public record Shot(String name, double x, double y, double z, float yaw, float pitch, Lights lights, boolean nightVision) {
        /** Applies the shot's lighting setup on the server thread (call from the server thread). */
        public void apply(ServerLevel level) {
            switch (lights) {
                case START -> Fixtures.resetAll(level);
                case ALL_ON -> {
                    Fixtures.setBreaker(level, true);
                    for (String r : Layout.LIGHT_ROOMS) Fixtures.setLights(level, r, true);
                    Fixtures.setTv(level, true);
                    Fixtures.setDevice(level, "desk_lamp", true);
                    Fixtures.setDevice(level, "laptop", true);
                    Fixtures.setClosetFusuma(level, 0);
                }
                case DARK -> {
                    for (String r : Layout.LIGHT_ROOMS) Fixtures.setLights(level, r, false);
                    Fixtures.setDevice(level, "desk_lamp", false);
                    Fixtures.setTv(level, true);
                    Fixtures.setTvChannel(level, 0);
                    Fixtures.setClosetFusuma(level, 0);
                }
                case PEEK -> {
                    for (String r : Layout.LIGHT_ROOMS) Fixtures.setLights(level, r, false);
                    Fixtures.setTv(level, true);
                    Fixtures.setClosetFusuma(level, 1);
                }
                default -> {
                }
            }
        }
    }

    private static Shot s(String name, double x, double y, double z, float yaw, float pitch) {
        return new Shot(name, x, y, z, yaw, pitch, Lights.KEEP, false);
    }

    private static Shot with(Shot s, Lights l, boolean nv, String suffix) {
        return new Shot(s.name() + suffix, s.x(), s.y(), s.z(), s.yaw(), s.pitch(), l, nv);
    }

    /** Outside: the street, the building, the corridor, the stairs, the yard, the park, the view from the peephole. */
    public static List<Shot> exterior() {
        List<Shot> l = new ArrayList<>();
        l.add(s("ext_street_west", -18.5, 65, 21.5, -78, -4));
        l.add(s("ext_front", 21.5, 65.5, 25.8, 172, -14));
        l.add(s("ext_lamp", 28.5, 65, 22.5, 160, -24));
        l.add(s("ext_vending", 4.5, 65, 22.5, 130, -6));
        l.add(s("ext_park", 47.5, 65, 21.5, -100, -2));
        l.add(s("ext_side_path", -2.5, 65, 15.5, 175, -6));
        l.add(s("ext_stairs", -9.5, 65, 0.5, -60, -16));
        l.add(s("ext_corridor", -0.5, 69, -1.5, -88, 2));
        l.add(s("ext_corridor_door", 22.5, 69, -2.2, 35, 6));
        l.add(s("ext_balcony", 26.5, 70, 15.2, 165, 4));
        l.add(s("ext_balcony_view", 25.5, 70, 15.2, 10, 6));
        l.add(s("ext_overview", 22.5, 90, 46.5, 180, 32));
        l.add(s("ext_overview_ne", 58.5, 88, -14.5, 130, 30));
        return l;
    }

    /** The peephole: from the front door's face out onto the corridor. */
    public static Shot peephole() {
        return s("peephole", 24.5, Layout.GENKAN_Y - 0.05, -0.15, 180, 4);
    }

    /** Inside 203: every room. */
    public static List<Shot> interior() {
        List<Shot> l = new ArrayList<>();
        l.add(s("genkan", 26.7, 70, 2.4, 118, 16));
        l.add(s("hall", 26.5, 70, 1.3, 2, 10));
        l.add(s("dk", 27.5, 70, 6.2, 108, 12));
        l.add(s("dk_table", 26.6, 70, 7.5, 145, 18));
        l.add(s("toilet", 29.5, 70, 2.7, 180, 28));
        l.add(s("bath", 31.3, 70, 2.7, -145, 22));
        l.add(s("washroom", 30.3, 70, 5.6, -112, 14));
        l.add(s("tatami", 27.5, 70, 12.5, 128, 10));
        l.add(s("tatami_tv", 24.6, 70, 10.4, -118, 6));
        l.add(s("closet_front", 26.4, 70, 11.0, 90, 4));
        l.add(s("western", 29.4, 70, 7.4, -48, 12));
        l.add(s("western_desk", 30.0, 70, 8.6, -108, 18));
        l.add(s("western_closet", 30.4, 70, 11.6, -90, 6));
        l.add(s("curtains", 25.5, 70, 10.2, 0, 4));
        return l;
    }

    /** The full tour: outside at night (and with night vision), the rooms at the start of the evening, with every light
     *  on, in the ritual's darkness, then the closet from inside and the peek through the crack. */
    public static List<Shot> shots() {
        List<Shot> out = new ArrayList<>();
        List<Shot> ext = exterior();
        for (int i = 0; i < ext.size(); i++) out.add(i == 0 ? with(ext.get(i), Lights.START, false, "") : ext.get(i));
        out.add(peephole());
        for (Shot s : ext) if (!s.name().startsWith("ext_overview")) out.add(with(s, Lights.KEEP, true, "_nv"));
        List<Shot> in = interior();
        for (int i = 0; i < in.size(); i++) out.add(with(in.get(i), i == 0 ? Lights.START : Lights.KEEP, false, "_start"));
        for (int i = 0; i < in.size(); i++) out.add(with(in.get(i), i == 0 ? Lights.ALL_ON : Lights.KEEP, false, "_on"));
        for (int i = 0; i < in.size(); i++) out.add(with(in.get(i), i == 0 ? Lights.DARK : Lights.KEEP, false, "_off"));
        out.add(new Shot("closet_inside", 23.5, 70, 11.5, -90, 6, Lights.DARK, false));
        out.add(new Shot("closet_inside_up", 23.5, 70, 11.5, -90, -55, Lights.KEEP, false));
        out.add(new Shot("closet_peek", 23.62, 69.7, 10.1, -95, 4, Lights.PEEK, false));
        out.add(new Shot("closet_open", 26.4, 70, 11.0, 90, 4, Lights.KEEP, false));
        return out;
    }

    // ================================================================================================ the standalone runner
    /** True when this standalone tour should run (CI flag set and the game's own tour is not there). */
    public static boolean standalone() {
        if (!Boolean.getBoolean("hitori.shots")) return false;
        try {
            Class.forName("com.hitori.client.AutoShots", false, WorldTour.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }

    @Mod.EventBusSubscriber(modid = Hitori.MODID, value = Dist.CLIENT)
    public static final class Runner {
        private static final boolean SHADERS = Boolean.getBoolean("hitori.shots.shaders");
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
                        Hitori.LOGGER.error("[hitori] preview pack", e);
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
                        Hitori.LOGGER.error("[hitori] world tour step {} failed", s.name(), e);
                    }
                    wait = s.waitAfter() * (SHADERS && s.waitAfter() > 10 ? 3 : 1);
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
                LevelSettings settings = new LevelSettings("HitoriCI", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                        WorldDataConfiguration.DEFAULT);
                WorldOptions options = new WorldOptions(20261102L, false, false);
                mc.createWorldOpenFlows().createFreshLevel(folder, settings, options,
                        ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                log("world creation requested");
            } catch (Exception e) {
                log("could not create world: " + e);
                Hitori.LOGGER.error("[hitori] could not create world", e);
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
                cmd(mc, String.format(Locale.ROOT, "execute in %s run tp @s %.1f %d %.1f -90 0", Layout.DIMENSION.location(),
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
            Hitori.LOGGER.info("[hitori] world tour: {}", s);
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
    // ================================================================================================ CI preview pack
    /**
     * CI only (standalone tour): a resource pack generated at runtime into {@code run/resourcepacks} with stand-in
     * vanilla textures for every HITORI texture that does not exist yet and plain box models (the blocks' own shapes)
     * for every prop model the art pass has not delivered, so layout screenshots are readable before the art is
     * merged. Assets that exist are never shadowed. Nothing of this is in the repository.
     */
    static final class Preview {
        static final String ID = "hitori_ci_preview";

        private Preview() {}

        /** Stand-in vanilla texture for a HITORI texture or model name (longest matching prefix). */
        static String vanilla(String n) {
            String[][] exact = {
                    {"tatami", "hay_block_top"}, {"tatami_worn", "hay_block_side"}, {"old_flooring", "dark_oak_planks"},
                    {"vinyl_floor", "sandstone_top"}, {"genkan_tile", "deepslate_tiles"}, {"bath_tile_floor", "light_blue_concrete"},
                    {"bath_tile_wall", "light_blue_concrete_powder"}, {"plaster_wall", "smooth_sandstone"}, {"plaster_wall_stained", "sandstone_bottom"},
                    {"wallpaper_old", "white_terracotta"}, {"wood_wainscot", "spruce_planks"}, {"ceiling_board", "oak_planks"},
                    {"ceiling_board_stained", "stripped_oak_log"}, {"oshiire_floor", "birch_planks"}, {"apartment_siding", "light_gray_concrete"},
                    {"apartment_door_metal", "iron_block"}, {"corridor_concrete", "smooth_stone"}, {"concrete", "polished_andesite"},
                    {"asphalt_wet", "black_concrete_powder"}, {"block_wall", "stone_bricks"}, {"roof_slate", "deepslate_tiles"},
                    {"puddle", "light_blue_stained_glass"}, {"rain_window", "glass"}, {"balcony_door", "glass"}, {"bath_door", "glass"},
                    {"fusuma", "white_wool"}, {"oshiire_fusuma", "white_wool"}, {"chabudai", "spruce_planks"}, {"zabuton", "red_wool"},
                    {"crt_tv", "black_concrete"}, {"tv_stand", "dark_oak_planks"}, {"hanging_lamp", "white_stained_glass"},
                    {"fluorescent_ring", "white_concrete"}, {"pull_switch", "white_wool"}, {"light_switch", "white_concrete"},
                    {"curtain", "gray_wool"}, {"futon_folded", "white_wool"}, {"cardboard_box", "stripped_oak_log"}, {"altar_shelf", "dark_oak_planks"},
                    {"bathtub", "light_blue_terracotta"}, {"bath_tap", "iron_block"}, {"shower", "iron_block"}, {"bath_stool", "light_blue_concrete"},
                    {"bucket", "yellow_concrete"}, {"washing_machine", "white_concrete"}, {"washbasin", "white_concrete"},
                    {"mirror_small", "light_blue_stained_glass"}, {"toilet_seat", "white_concrete"}, {"kitchen_sink", "iron_block"},
                    {"gas_stove", "gray_concrete"}, {"fridge_old", "white_concrete"}, {"rice_bag", "white_wool"}, {"rice_cooker", "white_concrete"},
                    {"salt_shaker", "white_concrete"}, {"cup_glass", "glass"}, {"knife_rack", "iron_block"}, {"microwave", "white_concrete"},
                    {"dining_table", "oak_planks"}, {"dining_chair", "oak_planks"}, {"drawer", "spruce_planks"}, {"desk", "oak_planks"},
                    {"desk_chair", "gray_wool"}, {"laptop_block", "gray_concrete"}, {"sewing_box", "red_concrete"}, {"printout", "white_concrete"},
                    {"bed", "light_blue_wool"}, {"shelf", "spruce_planks"}, {"plush_doll", "white_wool"}, {"camera_bag", "black_wool"},
                    {"poster", "pink_wool"}, {"desk_lamp", "gray_concrete"}, {"shoe_cabinet", "dark_oak_planks"}, {"shoes", "black_wool"},
                    {"umbrella", "blue_wool"}, {"breaker_panel", "white_concrete"}, {"intercom", "white_concrete"}, {"calendar", "white_wool"},
                    {"wall_clock", "white_concrete"}, {"laundry_pole", "iron_block"}, {"ac_outdoor_unit", "white_concrete"},
                    {"vending_machine_small", "white_concrete"}, {"street_lamp_old", "gray_concrete"}, {"utility_pole", "smooth_stone"},
                    {"mailboxes", "light_gray_concrete"}, {"bicycle", "iron_block"}, {"stairs_rail", "iron_block"}, {"nameplate", "white_concrete"},
                    {"vacancy_sign", "white_concrete"}, {"swing_set", "red_concrete"}};
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
                String door = n.startsWith("front_door") ? "iron_door" : n.startsWith("toilet_door") ? "birch_door" : n.startsWith("interior_door")
                        ? "spruce_door" : n.startsWith("bath_door") ? "acacia_door" : n.startsWith("balcony_door") ? "glass" : "oak_door";
                if (door.equals("glass")) return "glass";
                return door + (bottom ? "_bottom" : "_top");
            }
            return best != null ? best : "white_concrete";
        }

        /** The glowing stand-in of a lit / switched-on model. */
        static String litVanilla(String n, String base) {
            if (n.contains("street_lamp") || n.contains("hanging_lamp")) return "ochre_froglight_top";
            if (n.contains("fluorescent_ring") || n.contains("desk_lamp")) return "pearlescent_froglight_top";
            if (n.contains("crt_tv") || n.contains("laptop")) return "light_blue_concrete";
            if (n.contains("vending")) return "sea_lantern";
            if (n.contains("gas_stove")) return "blue_concrete";
            return base;
        }

        static boolean exists(net.minecraft.server.packs.resources.ResourceManager rm, String path) {
            return rm.getResource(new net.minecraft.resources.ResourceLocation(Hitori.MODID, path)).isPresent();
        }

        static void collect(com.google.gson.JsonElement el, java.util.Set<String> out) {
            if (el == null) return;
            if (el.isJsonArray()) {
                for (var e : el.getAsJsonArray()) collect(e, out);
                return;
            }
            if (!el.isJsonObject() || !el.getAsJsonObject().has("model")) return;
            String model = el.getAsJsonObject().get("model").getAsString();
            if (model.startsWith(Hitori.MODID + ":block/")) out.add(model.substring((Hitori.MODID + ":block/").length()));
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
            Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"HITORI CI preview (stand-in art)\"}}");
            java.util.Set<String> textures = new java.util.TreeSet<>();
            java.util.Map<String, net.minecraft.world.level.block.Block> models = new java.util.TreeMap<>();
            for (var e : net.minecraftforge.registries.ForgeRegistries.BLOCKS.getEntries()) {
                if (!e.getKey().location().getNamespace().equals(Hitori.MODID)) continue;
                String id = e.getKey().location().getPath();
                var bs = rm.getResource(new net.minecraft.resources.ResourceLocation(Hitori.MODID, "blockstates/" + id + ".json"));
                if (bs.isEmpty()) continue;
                com.google.gson.JsonObject json;
                try (var r = bs.get().openAsReader()) {
                    json = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
                }
                java.util.Set<String> names = new java.util.TreeSet<>();
                if (json.has("variants")) for (var v : json.getAsJsonObject("variants").entrySet()) collect(v.getValue(), names);
                if (json.has("multipart")) for (var part : json.getAsJsonArray("multipart")) collect(part.getAsJsonObject().get("apply"), names);
                for (String n : names) models.putIfAbsent(n, e.getValue());
            }
            int written = 0;
            for (var e : models.entrySet()) {
                String name = e.getKey();
                var res = rm.getResource(new net.minecraft.resources.ResourceLocation(Hitori.MODID, "models/block/" + name + ".json"));
                if (res.isPresent()) {
                    try (var r = res.get().openAsReader()) {
                        var json = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
                        if (json.has("textures")) {
                            for (var t : json.getAsJsonObject("textures").entrySet()) {
                                String v = t.getValue().getAsString();
                                if (v.startsWith(Hitori.MODID + ":block/")) textures.add(v.substring((Hitori.MODID + ":block/").length()));
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
                Path out = pack.resolve("assets/" + Hitori.MODID + "/textures/block/" + t + ".png");
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
            var shape = outline.isEmpty() ? coll : outline;
            List<net.minecraft.world.phys.AABB> boxes = shape.isEmpty() ? List.of(new net.minecraft.world.phys.AABB(0.25, 0, 0.25, 0.75, 0.5, 0.75)) : shape.toAabbs();
            String base = vanilla(name);
            boolean lit = name.endsWith("_lit") || name.contains("_on") || name.contains("_lit_") || name.endsWith("_running");
            String tex = lit ? litVanilla(name, base) : base;
            if (Minecraft.getInstance().getResourceManager().getResource(
                    new net.minecraft.resources.ResourceLocation("minecraft", "textures/block/" + tex + ".png")).isEmpty()) tex = "white_concrete";
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
                        b.minX * 16, b.minY * 16, b.minZ * 16, b.maxX * 16, Math.min(32, b.maxY * 16), b.maxZ * 16, lit ? "false" : "true"));
                String[] faces = {"north", "south", "east", "west", "up", "down"};
                for (int i = 0; i < faces.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(faces[i]).append("\":{\"texture\":\"#t\"}");
                }
                sb.append("}}");
            }
            sb.append("]}");
            Path out = pack.resolve("assets/" + Hitori.MODID + "/models/block/" + name + ".json");
            Files.createDirectories(out.getParent());
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        }
    }
}
