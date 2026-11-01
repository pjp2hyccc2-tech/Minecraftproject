package com.frostbound.selftest;

import com.frostbound.FrostboundMod;
import com.frostbound.block.GeneratorCoreBlockEntity;
import com.frostbound.core.CoreInfo;
import com.frostbound.core.city.ActionResult;
import com.frostbound.core.city.Building;
import com.frostbound.core.city.BuildingTypes;
import com.frostbound.core.city.City;
import com.frostbound.core.resources.ResourceLedger;
import com.frostbound.core.resources.ResourceType;
import com.frostbound.core.sim.SimClock;
import com.frostbound.core.util.Msg;
import com.frostbound.registry.ModRegistries;
import com.frostbound.save.FrostboundSavedData;
import com.frostbound.server.CitizenTracker;
import com.frostbound.server.CityService;
import com.frostbound.server.ForgeWorldAccess;
import com.frostbound.server.StructureBuilder;
import com.frostbound.world.Dims;
import com.frostbound.world.Terrain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.versions.forge.ForgeVersion;

/**
 * CI self-test on a dedicated server ({@code -Dfrostbound.selftest=true}), run twice on the same world.
 *
 * <p>Phase 1 (fresh FROSTBOUND world): the city is founded automatically; the test checks the dimension,
 * terrain, generator and building structures, placement rules, construction, heat, laws, research,
 * commands, simulates three days, saves, records a hash of the saved city and stops the server.
 * Phase 2 (restart): reloads the world and checks that the city is identical to the one saved, that the
 * structures and the backup file are there and that the simulation continues. Results go to
 * {@code frostbound-selftest-phase1.json} and {@code frostbound-selftest.json} in the server directory.
 */
public final class BootSelfTest {
    public static final String FILE_NAME = "frostbound-selftest.json";
    public static final String PHASE1_FILE = "frostbound-selftest-phase1.json";

    private record Check(String name, boolean pass, String detail) {
    }

    private static final List<Check> checks = new ArrayList<>();

    private BootSelfTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean("frostbound.selftest");
    }

    public static int phase() {
        String p = System.getProperty("frostbound.selftest.phase");
        if (p == null || p.isBlank()) {
            p = System.getenv("FROSTBOUND_SELFTEST_PHASE");
        }
        try {
            return p == null ? 1 : Integer.parseInt(p.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static void check(String name, boolean pass, String detail) {
        checks.add(new Check(name, pass, detail));
        FrostboundMod.LOGGER.info("[FROSTBOUND] self-test {} {}: {}", pass ? "PASS" : "FAIL", name, detail);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        int phase = phase();
        String extra = "";
        try {
            if (phase == 1) {
                extra = phase1(server);
            } else {
                extra = phase2(server);
            }
        } catch (Throwable t) {
            FrostboundMod.LOGGER.error("[FROSTBOUND] self-test crashed", t);
            check("no_exception", false, t.toString());
        }
        boolean pass = !checks.isEmpty() && checks.stream().allMatch(Check::pass);
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"result\": \"").append(pass ? "pass" : "fail").append("\",\n");
        json.append("  \"phase\": ").append(phase).append(",\n");
        json.append("  \"minecraft\": \"").append(esc(SharedConstants.getCurrentVersion().getName())).append("\",\n");
        json.append("  \"forge\": \"").append(esc(ForgeVersion.getVersion())).append("\",\n");
        json.append("  \"mod\": \"").append(esc(CoreInfo.MOD_ID)).append("\",\n");
        json.append(extra);
        json.append("  \"checks\": [\n");
        for (int i = 0; i < checks.size(); i++) {
            Check c = checks.get(i);
            json.append("    {\"name\": \"").append(esc(c.name())).append("\", \"pass\": ").append(c.pass())
                    .append(", \"detail\": \"").append(esc(c.detail())).append("\"}").append(i + 1 < checks.size() ? ",\n" : "\n");
        }
        json.append("  ]\n}\n");
        Path out = server.getServerDirectory().toPath().resolve(phase == 1 ? PHASE1_FILE : FILE_NAME);
        try {
            Files.writeString(out, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FrostboundMod.LOGGER.error("[FROSTBOUND] could not write self-test result", e);
        }
        FrostboundMod.LOGGER.info("[FROSTBOUND] self-test phase {} {}", phase, pass ? "PASS" : "FAIL");
        server.halt(false);
    }

    // ------------------------------------------------------------------ phase 1

    private static String phase1(MinecraftServer server) throws IOException {
        ServerLevel fl = CityService.frozenLevel();
        check("frozen_dimension", fl != null, String.valueOf(Dims.FROZEN_WASTES.location()));
        check("frostbound_world", CityService.isFrostboundWorld(), "phase=" + CityService.phase());
        City city = CityService.city();
        check("city_founded", city != null && city.population().alive == 30,
                city == null ? "no city" : "alive=" + city.population().alive + " buildings=" + city.buildings().size());
        if (city == null || fl == null) {
            return "";
        }
        int gx = city.generatorX();
        int gy = city.generatorY();
        int gz = city.generatorZ();
        BlockPos core = StructureBuilder.corePos(gx, gy, gz);
        boolean genOk = fl.getBlockState(core).is(ModRegistries.GENERATOR_CORE.get())
                && fl.getBlockEntity(core) instanceof GeneratorCoreBlockEntity
                && fl.getBlockState(new BlockPos(gx + 1, gy + 30, gz)).is(ModRegistries.CHIMNEY_BRICK.get())
                && fl.getBlockState(new BlockPos(gx, gy, gz + 3)).is(ModRegistries.GENERATOR_GRATE.get());
        check("generator_structure", genOk, "core=" + core.toShortString() + " floor=" + gy);
        check("structures_match", structuresMatch(fl, city), structureDetail);
        boolean terrain = Terrain.height(0, 60) == Terrain.BASIN_Y && fl.getBlockState(new BlockPos(0, Terrain.BASIN_Y, 60)).is(Blocks.SNOW_BLOCK)
                && fl.getBiome(new BlockPos(0, 65, 60)).is(Dims.FROZEN_BASIN)
                && server.overworld().getBiome(BlockPos.ZERO).is(Dims.FROZEN_VOID);
        check("terrain_and_biomes", terrain, "basinY=" + Terrain.BASIN_Y + " ring(215,0)=" + Terrain.height(215, 0)
                + " zone(400,0)=" + Terrain.zone(400, 0));

        Msg overlap = city.checkPlacement("tent", gx - 1, gy, gz - 1, 0);
        Msg far = city.checkPlacement("tent", gx + 400, gy, gz, 0);
        Msg locked = city.checkPlacement("apartment", gx - 30, gy, gz + 30, 0);
        check("placement_rules", overlap != null && overlap.key().equals("frostbound.reason.overlap")
                && far != null && far.key().equals("frostbound.reason.too_far")
                && locked != null && locked.key().equals("frostbound.reason.locked"),
                key(overlap) + " / " + key(far) + " / " + key(locked));

        ActionResult gen = city.setGeneratorLevel(1);
        CityService.advance(6 * SimClock.STEPS_PER_HOUR);
        Building shelter = null;
        for (Building b : city.buildings()) {
            if (b.typeId.equals("shelter")) {
                shelter = b;
                break;
            }
        }
        check("heat_network", shelter != null && city.generator().running() && shelter.temperature > city.outsideTemperature() + 5,
                String.format(Locale.ROOT, "gen=%s level=%d outside=%.1f shelter=%.1f eta=%.2f supply=%.2f", gen.ok(),
                        city.generator().level(), city.outsideTemperature(), shelter == null ? Double.NaN : shelter.temperature,
                        shelter == null ? 0 : shelter.eta, city.heat().supplyRatio()));

        int tx = gx - 14;
        int tz = gz - 16;
        int ty = ForgeWorldAccess.floorHeight(tx + 1, tz + 1);
        ActionResult placed = city.placeBuilding("tent", tx, ty, tz, 0);
        Building tent = null;
        for (Building b : city.buildings()) {
            if (b.typeId.equals("tent")) {
                tent = b;
            }
        }
        boolean scaffold = tent != null && fl.getBlockState(new BlockPos(tx, ty, tz)).is(Blocks.SCAFFOLDING);
        int hours = 0;
        while (tent != null && tent.state == Building.State.CONSTRUCTION && hours < 96) {
            CityService.advance(SimClock.STEPS_PER_HOUR);
            StructureBuilder.tickConstruction(fl, city);
            hours++;
        }
        boolean built = tent != null && tent.active() && fl.getBlockState(StructureBuilder.corePos(tent)).is(ModRegistries.BUILDING_CORE.get())
                && matches(fl, tent) == StructureBuilder.blueprint(tent).size();
        check("construction", placed.ok() && scaffold && built, "result=" + placed.msg().key() + " scaffold=" + scaffold
                + " hours=" + hours + " state=" + (tent == null ? "-" : tent.state.name()));

        ActionResult law = city.enactLaw("medical_priority");
        check("law", law.ok() && city.society().isEnacted("medical_priority"), law.msg().key());
        ActionResult research = city.startResearch("gen_efficiency");
        check("research_start", research.ok() || research.msg().key().startsWith("frostbound.research.reason."),
                research.msg().key());

        long t0 = System.nanoTime();
        int steps = 3 * SimClock.STEPS_PER_DAY;
        CityService.advance(steps);
        double msPerStep = (System.nanoTime() - t0) / 1e6 / steps;
        boolean nonNegative = true;
        StringBuilder res = new StringBuilder();
        for (ResourceType t : ResourceType.values()) {
            long a = city.ledger().amountMilli(t);
            nonNegative &= a >= 0;
            res.append(t.id()).append('=').append(a / ResourceLedger.MILLI).append(' ');
        }
        check("simulate_3_days", city.clock().day() >= 4 && city.running() && nonNegative && city.population().alive > 0,
                String.format(Locale.ROOT, "day=%d alive=%d hope=%.1f disc=%.1f %s msPerStep=%.4f", city.clock().day(),
                        city.population().alive, city.population().avgHope, city.population().avgDiscontent, res, msPerStep));
        check("performance", msPerStep < 2.0, String.format(Locale.ROOT, "%.4f ms per simulation step", msPerStep));

        int rc = server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "frostbound status");
        check("command_status", rc == 1, "rc=" + rc);
        check("npc_tracker_idle", CitizenTracker.liveCount() == 0, "no players, live=" + CitizenTracker.liveCount());

        CityService.markDirty();
        server.saveEverything(true, true, true);
        Path file = cityFile(server);
        long size = Files.exists(file) ? Files.size(file) : -1;
        check("save_file", size > 0 && CityService.isReadableCityFile(file.toFile()), file.getFileName() + " size=" + size);
        CityService.freeze();
        String hash = hash(city);
        return "  \"hash\": \"" + hash + "\",\n  \"step\": " + city.clock().step() + ",\n";
    }

    // ------------------------------------------------------------------ phase 2

    private static String phase2(MinecraftServer server) throws IOException {
        Path p1 = server.getServerDirectory().toPath().resolve(PHASE1_FILE);
        String phase1 = Files.exists(p1) ? Files.readString(p1, StandardCharsets.UTF_8) : "";
        check("phase1_passed", phase1.contains("\"result\": \"pass\""), Files.exists(p1) ? "found" : "missing " + PHASE1_FILE);
        Matcher m = Pattern.compile("\"hash\": \"([0-9a-f]+)\"").matcher(phase1);
        String expected = m.find() ? m.group(1) : "";
        City city = CityService.city();
        check("reload_city", city != null, "phase=" + CityService.phase());
        if (city == null) {
            return "";
        }
        String hash = hash(city);
        check("reload_identical", hash.equals(expected), "saved=" + abbrev(expected) + " loaded=" + abbrev(hash)
                + " step=" + city.clock().step());
        ServerLevel fl = CityService.frozenLevel();
        check("reload_structures", fl != null && structuresMatch(fl, city), structureDetail);
        Path file = cityFile(server);
        Path bak = file.resolveSibling(file.getFileName() + ".bak");
        check("backup_file", Files.exists(bak) && CityService.isReadableCityFile(bak.toFile()), bak.getFileName().toString());
        Path junk = file.resolveSibling("frostbound_selftest_junk.dat");
        Files.write(junk, new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        boolean detects = !CityService.isReadableCityFile(junk.toFile());
        Files.deleteIfExists(junk);
        check("corrupt_file_detected", detects, "garbage file rejected=" + detects);
        long s0 = city.clock().step();
        CityService.advance(2 * SimClock.STEPS_PER_HOUR);
        check("continue_after_reload", city.clock().step() == s0 + 2L * SimClock.STEPS_PER_HOUR && city.running(),
                "step " + s0 + " -> " + city.clock().step());
        int idx = phase1.indexOf("\"checks\"");
        String p1checks = idx >= 0 ? phase1.substring(idx) : "";
        return "  \"hash\": \"" + hash + "\",\n  \"phase1\": {" + (p1checks.isEmpty() ? "" : p1checks.trim().replaceAll("\\}\\s*$", ""))
                + "},\n";
    }

    // ------------------------------------------------------------------ helpers

    private static String structureDetail = "";

    private static boolean structuresMatch(ServerLevel fl, City city) {
        StringBuilder sb = new StringBuilder();
        boolean ok = true;
        for (Building b : city.buildings()) {
            if (b.state == Building.State.CONSTRUCTION) {
                continue;
            }
            int total = StructureBuilder.blueprint(b).size();
            int hit = matches(fl, b);
            boolean coreOk = b.type() == BuildingTypes.GENERATOR
                    ? fl.getBlockState(StructureBuilder.corePos(b)).is(ModRegistries.GENERATOR_CORE.get())
                    : fl.getBlockState(StructureBuilder.corePos(b)).is(ModRegistries.BUILDING_CORE.get());
            boolean good = coreOk && hit >= total * 0.97;
            ok &= good;
            sb.append(b.typeId).append(':').append(hit).append('/').append(total).append(good ? "" : "!").append(' ');
        }
        structureDetail = sb.toString().trim();
        return ok;
    }

    /** Blueprint blocks that are in the world (lit/unlit grates and connected shapes count by block). */
    private static int matches(ServerLevel fl, Building b) {
        int hit = 0;
        for (StructureBuilder.Placement p : StructureBuilder.blueprint(b)) {
            if (fl.getBlockState(p.pos()).getBlock() == p.state().getBlock()) {
                hit++;
            }
        }
        return hit;
    }

    private static Path cityFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FrostboundSavedData.NAME + ".dat");
    }

    private static String key(Msg m) {
        return m == null ? "null" : m.key();
    }

    private static String abbrev(String h) {
        return h.length() > 12 ? h.substring(0, 12) : h;
    }

    /** SHA-256 of the saved city in a canonical form (sorted keys). */
    public static String hash(City city) {
        StringBuilder sb = new StringBuilder();
        canonical(city.save(), sb);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    @SuppressWarnings("unchecked")
    private static void canonical(Object o, StringBuilder sb) {
        if (o instanceof Map<?, ?> map) {
            sb.append('{');
            for (Map.Entry<String, Object> e : new TreeMap<>((Map<String, Object>) map).entrySet()) {
                sb.append(e.getKey()).append('=');
                canonical(e.getValue(), sb);
                sb.append(';');
            }
            sb.append('}');
        } else if (o instanceof List<?> list) {
            sb.append('[');
            for (Object x : list) {
                canonical(x, sb);
                sb.append(',');
            }
            sb.append(']');
        } else if (o instanceof Double || o instanceof Float) {
            sb.append(Double.toString(((Number) o).doubleValue()));
        } else if (o instanceof Number n) {
            sb.append(n.longValue());
        } else if (o instanceof Boolean b) {
            sb.append(b ? '1' : '0');
        } else {
            sb.append('"').append(o).append('"');
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
