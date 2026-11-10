package com.eternalrealm.world.field;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.FieldBosses;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.gen.LandmarkSite;
import com.eternalrealm.world.gen.LandmarkSites;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The wandering bosses of the fields: who wanders where, the places they appear at and their schedule. */
public class WanderersTest {
    private static final Path PIECES = Paths.get("src", "main", "resources", "data", "eternalrealm", "fieldpieces",
            "manifest.json");
    private static final long MINUTE = 20L * 60L;
    private static final long STEP = 20L;
    /** Fixed places for the schedule tests (the real ones come from the terrain). */
    private static final IntFunction<List<Wanderers.Spot>> FIXED = field -> List.of(new Wanderers.Spot(field * 100, 0),
            new Wanderers.Spot(field * 100, 300));
    private static Map<Integer, List<Wanderers.Spot>> spots;

    private static List<Wanderers.Box> pieceBoxes() throws IOException {
        JsonObject manifest = JsonParser.parseString(Files.readString(PIECES, StandardCharsets.UTF_8)).getAsJsonObject();
        List<Wanderers.Box> boxes = new ArrayList<>();
        for (JsonElement element : manifest.getAsJsonArray("pieces")) {
            JsonArray box = element.getAsJsonObject().getAsJsonArray("box");
            boxes.add(new Wanderers.Box(box.get(0).getAsInt(), box.get(2).getAsInt(), box.get(3).getAsInt(),
                    box.get(5).getAsInt()));
        }
        assertFalse("no set pieces in " + PIECES, boxes.isEmpty());
        return boxes;
    }

    private static synchronized Map<Integer, List<Wanderers.Spot>> spots() throws IOException {
        if (spots == null) {
            List<Wanderers.Box> boxes = pieceBoxes();
            Map<Integer, List<Wanderers.Spot>> found = new HashMap<>();
            for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
                found.put(field, Wanderers.findSpots(field, boxes));
            }
            spots = found;
        }
        return spots;
    }

    // ------------------------------------------------------------------ who wanders where

    @Test
    public void everyFieldHasAWandererAndTheFiveHighestHaveTwo() {
        assertEquals(15, Wanderers.all().size());
        Set<String> ids = new HashSet<>();
        Set<String> bosses = new HashSet<>();
        for (Wanderers.Wanderer wanderer : Wanderers.all()) {
            assertTrue("duplicate wanderer " + wanderer.id(), ids.add(wanderer.id()));
            assertTrue("two wanderers share " + wanderer.boss(), bosses.add(wanderer.boss()));
        }
        List<Integer> two = new ArrayList<>();
        int lowestTopOfTwo = Integer.MAX_VALUE;
        int highestTopOfOne = Integer.MIN_VALUE;
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            int count = Wanderers.of(field).size();
            assertTrue("field " + field + " has " + count + " wanderers", count == 1 || count == 2);
            int top = Areas.byId(field).map(AreaDefinition::maxLevel).orElseThrow();
            if (count == 2) {
                two.add(field);
                lowestTopOfTwo = Math.min(lowestTopOfTwo, top);
            } else {
                highestTopOfOne = Math.max(highestTopOfOne, top);
            }
        }
        assertEquals("fields with two wanderers: " + two, 5, two.size());
        assertTrue("the fields with two wanderers are the higher-level ones", lowestTopOfTwo >= highestTopOfOne);
    }

    @Test
    public void eachWandererIsItsOwnFieldBossAndTheCalamityOutranksIt() {
        Set<String> fieldWanderers = new TreeSet<>();
        FieldBosses.WANDERERS.forEach(boss -> fieldWanderers.add(boss.id()));
        Set<String> used = new TreeSet<>();
        Wanderers.all().forEach(wanderer -> used.add(wanderer.boss()));
        assertEquals(fieldWanderers, used);
        for (Wanderers.Wanderer wanderer : Wanderers.all()) {
            BossDefinition boss = Bosses.byId(wanderer.boss()).orElseThrow();
            AreaDefinition area = Areas.byId(wanderer.field()).orElseThrow();
            assertEquals(wanderer.id() + " has two forms", 2, boss.phases().size());
            assertTrue(wanderer.id() + " Lv" + boss.level() + " fits " + area.slug(),
                    boss.level() >= area.minLevel() && boss.level() <= area.maxLevel() + 5);
            assertEquals(Optional.of(wanderer), Wanderers.byBoss(wanderer.boss()));
            assertEquals(Optional.of(wanderer), Wanderers.byId(wanderer.id()));
            assertEquals("boss.eternalrealm." + wanderer.boss(), wanderer.translationKey());
            FieldThreats.Calamity calamity = FieldThreats.calamityOf(wanderer.field()).orElseThrow();
            BossDefinition calamityBoss = Bosses.byId(calamity.boss()).orElseThrow();
            assertTrue(calamity.id() + " is at least as strong as " + wanderer.id(),
                    calamityBoss.level() >= boss.level());
        }
        Set<String> calamityBosses = new TreeSet<>();
        FieldThreats.calamities().forEach(calamity -> calamityBosses.add(calamity.boss()));
        Set<String> fieldCalamities = new TreeSet<>();
        FieldBosses.CALAMITIES.forEach(boss -> fieldCalamities.add(boss.id()));
        assertEquals("every calamity is its own field boss", fieldCalamities, calamityBosses);
    }

    // ------------------------------------------------------------------ where they appear

    @Test
    public void spotsLieInTheHeartOfTheirFieldOnFlatDryGround() throws IOException {
        int arena = FieldThreats.LAIR_RADIUS;
        for (Map.Entry<Integer, List<Wanderers.Spot>> entry : spots().entrySet()) {
            int field = entry.getKey();
            List<Wanderers.Spot> found = entry.getValue();
            assertTrue("field " + field + " has only " + found.size() + " spots", found.size() >= 3);
            double[] heart = FieldLayout.labelPoint(field);
            for (Wanderers.Spot spot : found) {
                String where = "field " + field + " spot " + spot;
                assertEquals(where, field, FieldLayout.areaAt(spot.x(), spot.z()));
                assertTrue(where + " far from the heart",
                        Math.hypot(spot.x() - heart[0], spot.z() - heart[1]) <= Wanderers.HEART_RADIUS);
                assertFalse(where + " in the capital", FieldLayout.inCapital(spot.x(), spot.z()));
                int top = (int) Math.floor(FieldTerrain.heightAt(spot.x(), spot.z()));
                for (int dx = -arena; dx <= arena; dx += 5) {
                    for (int dz = -arena; dz <= arena; dz += 5) {
                        if (dx * dx + dz * dz > (arena - 4) * (arena - 4)) {
                            continue;
                        }
                        int h = (int) Math.floor(FieldTerrain.heightAt(spot.x() + dx, spot.z() + dz));
                        assertTrue(where + " uneven at " + dx + "," + dz, Math.abs(h - top) <= Wanderers.MAX_STEP + 1);
                        assertEquals(where + " wet at " + dx + "," + dz, FieldTerrain.NO_WATER,
                                FieldTerrain.waterTop(spot.x() + dx, spot.z() + dz, h));
                    }
                }
                for (Wanderers.Spot other : found) {
                    if (other != spot) {
                        assertTrue(where + " too close to " + other, Math.hypot(other.x() - spot.x(),
                                other.z() - spot.z()) >= Wanderers.SPOT_SPACING);
                    }
                }
            }
        }
    }

    @Test
    public void spotsKeepClearOfRoadsWaystonesPiecesLordsAndLairs() throws IOException {
        int arena = FieldThreats.LAIR_RADIUS;
        List<Wanderers.Box> boxes = pieceBoxes();
        for (List<Wanderers.Spot> found : spots().values()) {
            for (Wanderers.Spot spot : found) {
                String where = "spot " + spot;
                double[] road = FieldLayout.nearestRoad(spot.x(), spot.z());
                assertTrue(where + " on a road", road[0] - road[1] >= arena);
                for (FieldLayout.Waystone waystone : FieldLayout.waystones()) {
                    assertTrue(where + " at waystone " + waystone.id(),
                            Math.hypot(waystone.x() - spot.x(), waystone.z() - spot.z()) >= arena + 30);
                }
                for (LandmarkSite site : LandmarkSites.all()) {
                    assertTrue(where + " at the entrance of " + site.dungeonId(),
                            Math.hypot(site.x() - spot.x(), site.z() - spot.z()) >= site.padRadius() + arena + 20);
                }
                for (Wanderers.Box box : boxes) {
                    assertTrue(where + " on set piece " + box, box.distance(spot.x(), spot.z()) >= arena + 8);
                }
                for (FieldThreats.Lord lord : FieldThreats.lords()) {
                    assertTrue(where + " in the territory of " + lord.id(),
                            Math.hypot(lord.x() - spot.x(), lord.z() - spot.z()) >= lord.territory() + arena + 10);
                }
                for (FieldThreats.Calamity calamity : FieldThreats.calamities()) {
                    assertTrue(where + " at the lair of " + calamity.id(),
                            Math.hypot(calamity.x() - spot.x(), calamity.z() - spot.z()) >= 2 * arena + 40);
                }
            }
        }
    }

    @Test
    public void spotsAreFoundTheSameWayEveryTime() throws IOException {
        assertEquals(spots().get(FieldLayout.FARM), Wanderers.findSpots(FieldLayout.FARM, pieceBoxes()));
    }

    @Test
    public void boxDistanceIsZeroInsideAndEuclideanOutside() {
        Wanderers.Box box = new Wanderers.Box(0, 0, 10, 20);
        assertEquals(0.0, box.distance(5, 5), 1e-9);
        assertEquals(5.0, box.distance(15, 10), 1e-9);
        assertEquals(5.0, box.distance(13, 24), 1e-9);
    }

    @Test
    public void directionsFollowTheCompassWithNorthAtMinusZ() {
        assertEquals("n", Wanderers.direction(0, 0, 0, -100));
        assertEquals("ne", Wanderers.direction(0, 0, 70, -70));
        assertEquals("e", Wanderers.direction(0, 0, 100, 5));
        assertEquals("se", Wanderers.direction(0, 0, 60, 60));
        assertEquals("s", Wanderers.direction(0, 0, 0, 100));
        assertEquals("sw", Wanderers.direction(0, 0, -60, 60));
        assertEquals("w", Wanderers.direction(0, 0, -100, 0));
        assertEquals("nw", Wanderers.direction(0, 0, -60, -60));
        for (Wanderers.Wanderer wanderer : Wanderers.all()) {
            FieldLayout.Waystone near = Wanderers.landmark(wanderer.field(), 0, 0).orElseThrow();
            assertEquals(wanderer.field(), near.field());
        }
    }

    // ------------------------------------------------------------------ the schedule

    private record Seen(long tick, Wanderers.Event event) {
    }

    /** Ticks {@code field} every second from {@code from} (inclusive) to {@code to} (exclusive). */
    private static List<Seen> run(Wanderers.Schedule schedule, int field, long from, long to, boolean players,
                                  boolean calamity) {
        List<Seen> seen = new ArrayList<>();
        for (long tick = from; tick < to; tick += STEP) {
            for (Wanderers.Event event : schedule.tick(field, tick, players, calamity)) {
                seen.add(new Seen(tick, event));
            }
        }
        return seen;
    }

    private static Wanderers.Schedule schedule(long seed) {
        return new Wanderers.Schedule(new SplittableRandom(seed), FIXED);
    }

    @Test
    public void nothingAppearsWhileNobodyIsInTheField() {
        for (long seed = 0; seed < 8; seed++) {
            Wanderers.Schedule schedule = schedule(seed);
            assertTrue(run(schedule, FieldLayout.MEADOW, 0, 6 * 60 * MINUTE, false, false).isEmpty());
            assertTrue(schedule.current(FieldLayout.MEADOW).isEmpty());
        }
    }

    @Test
    public void theFirstWandererComesAfterHalfAGapOfPlayerTime() {
        for (long seed = 0; seed < 20; seed++) {
            Wanderers.Schedule schedule = schedule(seed);
            List<Seen> seen = run(schedule, FieldLayout.SACRED_FALLS, 0, Wanderers.MAX_GAP_MINUTES * MINUTE / 2 + STEP,
                    true, false);
            assertEquals("seed " + seed, 1, seen.size());
            Seen first = seen.get(0);
            assertTrue(first.event() instanceof Wanderers.Event.Appeared);
            assertTrue("seed " + seed + ": at " + first.tick(), first.tick() >= Wanderers.MIN_GAP_MINUTES * MINUTE / 2);
            assertTrue("seed " + seed + ": at " + first.tick(),
                    first.tick() <= Wanderers.MAX_GAP_MINUTES * MINUTE / 2 + STEP);
            Wanderers.Event.Appeared appeared = (Wanderers.Event.Appeared) first.event();
            assertEquals(FieldLayout.SACRED_FALLS, appeared.wanderer().field());
            assertTrue(FIXED.apply(FieldLayout.SACRED_FALLS).contains(appeared.spot()));
            assertEquals(Optional.of(appeared.wanderer()), schedule.current(FieldLayout.SACRED_FALLS));
            assertEquals(Optional.of(appeared.spot()), schedule.spot(FieldLayout.SACRED_FALLS));
        }
    }

    @Test
    public void theClockOnlyRunsWhilePlayersAreInTheField() {
        Wanderers.Schedule schedule = schedule(3);
        long half = Wanderers.MIN_GAP_MINUTES * MINUTE / 2;
        // just short of the earliest appearance with players, then hours without them
        assertTrue(run(schedule, FieldLayout.FARM, 0, half - MINUTE, true, false).isEmpty());
        assertTrue(run(schedule, FieldLayout.FARM, half - MINUTE, half + 5 * 60 * MINUTE, false, false).isEmpty());
        // back in the field: it comes within the rest of the wait, counted in player time only
        long back = half + 5 * 60 * MINUTE;
        List<Seen> seen = run(schedule, FieldLayout.FARM, back, back + Wanderers.MAX_GAP_MINUTES * MINUTE, true, false);
        assertFalse(seen.isEmpty());
        long playerTime = half - MINUTE + (seen.get(0).tick() - back);
        assertTrue("came after " + playerTime + " ticks of player time",
                playerTime >= half - STEP && playerTime <= Wanderers.MAX_GAP_MINUTES * MINUTE / 2 + 2 * STEP);
    }

    @Test
    public void atMostOneAtATimeAndAnUnfoughtOneLeavesAfterItsStay() {
        for (long seed = 0; seed < 10; seed++) {
            Wanderers.Schedule schedule = schedule(seed);
            List<Seen> seen = run(schedule, FieldLayout.ANCIENT_RUINS, 0, 10 * 60 * MINUTE, true, false);
            assertTrue(seen.size() >= 4);
            for (int i = 0; i < seen.size(); i++) {
                Seen event = seen.get(i);
                // appearances and departures alternate: never two out in one field
                assertEquals("seed " + seed + " event " + i, i % 2 == 0,
                        event.event() instanceof Wanderers.Event.Appeared);
                if (event.event() instanceof Wanderers.Event.Left left) {
                    long out = event.tick() - seen.get(i - 1).tick();
                    assertFalse(left.calamity());
                    assertTrue("stayed " + out, out >= Wanderers.STAY_MINUTES * MINUTE
                            && out < Wanderers.STAY_MINUTES * MINUTE + STEP);
                    assertEquals(((Wanderers.Event.Appeared) seen.get(i - 1).event()).wanderer(), left.wanderer());
                } else if (i > 0) {
                    long gap = event.tick() - seen.get(i - 1).tick();
                    assertTrue("gap " + gap, gap >= Wanderers.MIN_GAP_MINUTES * MINUTE);
                }
            }
        }
    }

    @Test
    public void theSameWandererDoesNotComeBackBeforeItsCooldown() {
        for (int field : new int[]{FieldLayout.MEADOW, FieldLayout.SOARING_PEAKS, FieldLayout.HARBOUR}) {
            Wanderers.Schedule schedule = schedule(field * 31L);
            List<Seen> seen = run(schedule, field, 0, 24 * 60 * MINUTE, true, false);
            Map<String, Long> leftAt = new HashMap<>();
            Set<String> appeared = new HashSet<>();
            for (Seen event : seen) {
                if (event.event() instanceof Wanderers.Event.Appeared up) {
                    Long last = leftAt.get(up.wanderer().id());
                    assertTrue(up.wanderer().id() + " back after " + (last == null ? 0 : event.tick() - last),
                            last == null || event.tick() - last >= Wanderers.COOLDOWN_MINUTES * MINUTE);
                    appeared.add(up.wanderer().id());
                } else if (event.event() instanceof Wanderers.Event.Left left) {
                    leftAt.put(left.wanderer().id(), event.tick());
                }
            }
            Set<String> expected = new HashSet<>();
            Wanderers.of(field).forEach(wanderer -> expected.add(wanderer.id()));
            assertEquals("field " + field + ": all its wanderers come in a day", expected, appeared);
        }
    }

    @Test
    public void aFoughtWandererStaysUntilTheFightEnds() {
        Wanderers.Schedule schedule = schedule(5);
        Wanderers.Wanderer wanderer = Wanderers.of(FieldLayout.LAKESIDE).get(0);
        assertTrue(schedule.force(wanderer, new Wanderers.Spot(1, 2), 0L).isPresent());
        assertEquals(Wanderers.STAY_MINUTES * MINUTE, schedule.leavesIn(FieldLayout.LAKESIDE, 0L));
        schedule.engaged(FieldLayout.LAKESIDE);
        assertTrue(schedule.fighting(FieldLayout.LAKESIDE));
        assertEquals(0L, schedule.leavesIn(FieldLayout.LAKESIDE, 0L));
        assertTrue(run(schedule, FieldLayout.LAKESIDE, 0, 3 * Wanderers.STAY_MINUTES * MINUTE, true, true).isEmpty());
        long end = 3 * Wanderers.STAY_MINUTES * MINUTE;
        schedule.ended(FieldLayout.LAKESIDE, end);
        assertTrue(schedule.current(FieldLayout.LAKESIDE).isEmpty());
        assertFalse(schedule.fighting(FieldLayout.LAKESIDE));
        assertTrue(schedule.cooldown(wanderer, end) > 0L);
        List<Seen> next = run(schedule, FieldLayout.LAKESIDE, end, end + 2 * 60 * MINUTE, true, false);
        assertFalse(next.isEmpty());
        assertTrue(next.get(0).tick() - end >= Math.max(Wanderers.MIN_GAP_MINUTES, Wanderers.COOLDOWN_MINUTES) * MINUTE);
    }

    @Test
    public void noWandererWhileTheCalamityIsUpAndAnUnfoughtOneFlees() {
        Wanderers.Schedule schedule = schedule(9);
        assertTrue(run(schedule, FieldLayout.END_FOREST, 0, 3 * 60 * MINUTE, true, true).isEmpty());
        // the calamity is gone: the overdue wanderer comes at once
        List<Seen> seen = run(schedule, FieldLayout.END_FOREST, 3 * 60 * MINUTE, 3 * 60 * MINUTE + 2 * STEP, true,
                false);
        assertEquals(1, seen.size());
        // an omen: the wanderer nobody fights flees
        List<Seen> fled = run(schedule, FieldLayout.END_FOREST, 3 * 60 * MINUTE + 2 * STEP, 3 * 60 * MINUTE + 4 * STEP,
                true, true);
        assertEquals(1, fled.size());
        assertTrue(((Wanderers.Event.Left) fled.get(0).event()).calamity());
        assertTrue(schedule.current(FieldLayout.END_FOREST).isEmpty());
    }

    @Test
    public void noPlacesNoWanderer() {
        Wanderers.Schedule schedule = new Wanderers.Schedule(new SplittableRandom(1), field -> List.of());
        assertTrue(run(schedule, FieldLayout.MOON_FOREST, 0, 3 * 60 * MINUTE, true, false).isEmpty());
        Wanderers.Schedule unknown = new Wanderers.Schedule(new SplittableRandom(1), field -> null);
        assertTrue(run(unknown, FieldLayout.MOON_FOREST, 0, 3 * 60 * MINUTE, true, false).isEmpty());
    }

    @Test
    public void forceRefusesASecondWandererInAField() {
        Wanderers.Schedule schedule = schedule(2);
        List<Wanderers.Wanderer> pair = Wanderers.of(FieldLayout.HARBOUR);
        assertEquals(2, pair.size());
        assertTrue(schedule.force(pair.get(0), new Wanderers.Spot(0, 0), 0L).isPresent());
        assertTrue(schedule.force(pair.get(1), new Wanderers.Spot(0, 0), 0L).isEmpty());
        assertTrue(schedule.force(Wanderers.of(FieldLayout.MEADOW).get(0), new Wanderers.Spot(0, 0), 0L).isPresent());
        assertEquals(Optional.of(pair.get(0)), schedule.current(FieldLayout.HARBOUR));
    }

    // ------------------------------------------------------------------ texts

    @Test
    public void textsAreInJapaneseAndEnglishAndInTheLangFiles() throws IOException {
        JsonObject docs = read(Paths.get("docs", "i18n", "field_wanderers.json"));
        assertEquals(new TreeSet<>(Wanderers.textKeys()), new TreeSet<>(docs.keySet()));
        Path lang = Paths.get("src", "main", "resources", "assets", "eternalrealm", "lang");
        JsonObject ja = read(lang.resolve("ja_jp.json"));
        JsonObject en = read(lang.resolve("en_us.json"));
        for (String key : docs.keySet()) {
            JsonObject texts = docs.getAsJsonObject(key);
            String japanese = texts.get("ja").getAsString();
            String english = texts.get("en").getAsString();
            assertFalse(key, japanese.isBlank() || english.isBlank());
            assertEquals(key + ": same arguments", arguments(japanese), arguments(english));
            assertEquals(key + " in ja_jp.json", japanese, ja.has(key) ? ja.get(key).getAsString() : null);
            assertEquals(key + " in en_us.json", english, en.has(key) ? en.get(key).getAsString() : null);
        }
        assertEquals(Set.of("1$", "2$", "3$", "4$", "5$", "6$", "7$", "8$"),
                arguments(docs.getAsJsonObject(Wanderers.APPEARS).get("ja").getAsString()));
        for (Wanderers.Wanderer wanderer : Wanderers.all()) {
            assertNotNull(wanderer.translationKey(), ja.get(wanderer.translationKey()));
            assertNotNull(wanderer.translationKey(), en.get(wanderer.translationKey()));
        }
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> arguments(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = Pattern.compile("%(\\d+\\$)?s").matcher(text);
        int plain = 0;
        while (matcher.find()) {
            found.add(matcher.group(1) == null ? "#" + (++plain) : matcher.group(1));
        }
        return found;
    }
}
