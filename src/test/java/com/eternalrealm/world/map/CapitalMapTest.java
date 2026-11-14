package com.eternalrealm.world.map;

import com.eternalrealm.world.field.FieldLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The capital's map (2026-11-14: 「王国に入るとまた王国専用のマップを開けるようにして、どこに何があるかみやすくして」):
 * what tools/capital/map_texture.py wrote from the blueprint is read whole, every place is where the blueprint puts it,
 * has its icon, its name and what one does there in both languages, and the services one looks for are all on it.
 */
public class CapitalMapTest {
    private static final Path ASSETS = Paths.get("src", "main", "resources", "assets", "eternalrealm");
    private static final Path MANIFEST = Paths.get("src", "main", "resources", "data", "eternalrealm", "capital",
            "manifest.json");

    private static JsonObject json(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static CapitalMap.Data shipped() throws IOException {
        return CapitalMap.parse(json(ASSETS.resolve(CapitalMap.RESOURCE)));
    }

    @Test
    public void theShippedMapHasEveryServiceAndItsPicture() throws IOException {
        CapitalMap.Data map = shipped();
        Set<String> ids = new HashSet<>();
        for (CapitalMap.Place place : map.places()) {
            assertTrue("one place per id: " + place.id(), ids.add(place.id()));
        }
        for (String service : List.of("arrival", "goddess", "quest_board", "item_shop", "blacksmith", "job_master",
                "auction", "mailbox", "guild_registrar", "guild_estates", "gatekeeper", "arena_master", "hall_of_fame",
                "inn", "stable", "castle_gate", "throne")) {
            assertTrue("the map shows " + service, ids.contains(service));
        }
        assertTrue(map.districts().size() >= 10);
        // the picture: two pixels a block over the bounds
        BufferedImage picture = ImageIO.read(ASSETS.resolve(CapitalMap.TEXTURE).toFile());
        assertEquals(map.width() * 2, picture.getWidth());
        assertEquals(map.height() * 2, picture.getHeight());
        // the whole capital is on it
        assertTrue(map.contains(FieldLayout.CAPITAL_MIN_X, FieldLayout.CAPITAL_MIN_Z));
        assertTrue(map.contains(FieldLayout.CAPITAL_MAX_X, FieldLayout.CAPITAL_MAX_Z));
    }

    @Test
    public void everyPlaceStandsWhereTheBlueprintPutsIt() throws IOException {
        CapitalMap.Data map = shipped();
        JsonObject spots = json(MANIFEST).getAsJsonObject("spots");
        for (CapitalMap.Place place : map.places()) {
            assertTrue(place.id() + " lies in the capital", FieldLayout.inCapital(place.x(), place.z()));
            assertTrue(place.id() + " lies on the picture", map.contains(place.x(), place.z()));
            if (!place.hasNpc() || place.id().equals("stable")) {
                continue; // the stablemaster has no spot in the blueprint (RealmSetup puts him by the south gate)
            }
            String spot = "npc." + (place.id().equals("inn") ? "innkeeper" : place.npc());
            assertTrue("the blueprint has " + spot, spots.has(spot));
            var pos = spots.getAsJsonObject(spot).getAsJsonArray("pos");
            assertEquals(place.id() + " x", pos.get(0).getAsDouble(), place.x(), 1.0E-9);
            assertEquals(place.id() + " z", pos.get(2).getAsDouble(), place.z(), 1.0E-9);
        }
    }

    @Test
    public void everyPlaceHasItsIconNameAndUse() throws IOException {
        CapitalMap.Data map = shipped();
        JsonObject ja = json(ASSETS.resolve("lang/ja_jp.json"));
        JsonObject en = json(ASSETS.resolve("lang/en_us.json"));
        int story = CapitalMap.icon(CapitalMap.STORY);
        Set<Integer> icons = new HashSet<>();
        for (CapitalMap.Place place : map.places()) {
            assertTrue(place.id() + " has its own icon", place.icon() != story && icons.add(place.icon()));
            for (String key : List.of(place.nameKey(), place.descKey())) {
                assertTrue(key + " (ja)", ja.has(key));
                assertTrue(key + " (en)", en.has(key));
            }
            if (place.hasNpc()) {
                assertTrue(place.npc() + " is a town NPC of the minimap", NpcMarkers.roles().contains(place.npc()));
                assertTrue("npc.eternalrealm." + place.npc(), ja.has("npc.eternalrealm." + place.npc()));
            }
        }
        for (CapitalMap.District district : map.districts()) {
            assertTrue(district.nameKey(), ja.has(district.nameKey()) && en.has(district.nameKey()));
        }
        for (CapitalMap.Group group : CapitalMap.Group.values()) {
            assertTrue(group.translationKey(), ja.has(group.translationKey()) && en.has(group.translationKey()));
        }
        for (String direction : List.of("n", "ne", "e", "se", "s", "sw", "w", "nw", "here")) {
            assertTrue(direction, ja.has("map.eternalrealm.capital.dir." + direction));
        }
        // the atlas drawn by tools/art/capital_map_icons.py: one icon per entry of icons(), in that order
        BufferedImage atlas = ImageIO.read(ASSETS.resolve(CapitalMap.ICON_ATLAS).toFile());
        assertEquals(CapitalMap.ICON_SIZE * CapitalMap.icons().size(), atlas.getWidth());
        assertEquals(CapitalMap.ICON_SIZE, atlas.getHeight());
        for (int i = 0; i < CapitalMap.icons().size(); i++) {
            int opaque = 0;
            for (int x = 0; x < CapitalMap.ICON_SIZE; x++) {
                for (int y = 0; y < CapitalMap.ICON_SIZE; y++) {
                    if ((atlas.getRGB(i * CapitalMap.ICON_SIZE + x, y) >>> 24) > 0) {
                        opaque++;
                    }
                }
            }
            assertTrue("icon " + CapitalMap.icons().get(i) + " is drawn", opaque >= 30);
        }
    }

    @Test
    public void theListGoesByGroup() throws IOException {
        List<CapitalMap.Place> legend = CapitalMap.legend(shipped());
        assertEquals(shipped().places().size(), legend.size());
        for (int i = 1; i < legend.size(); i++) {
            assertTrue(legend.get(i - 1).kind().group().ordinal() <= legend.get(i).kind().group().ordinal());
        }
        assertEquals("the quests come first", "quest_board", legend.get(0).id());
        assertEquals(CapitalMap.Group.CASTLE, legend.get(legend.size() - 1).kind().group());
    }

    @Test
    public void directionsGoRoundTheCompass() {
        String p = "map.eternalrealm.capital.dir.";
        assertEquals(p + "n", CapitalMap.direction(0, 0, 0, -50, 5));
        assertEquals(p + "ne", CapitalMap.direction(0, 0, 40, -40, 5));
        assertEquals(p + "e", CapitalMap.direction(0, 0, 50, 3, 5));
        assertEquals(p + "se", CapitalMap.direction(0, 0, 30, 30, 5));
        assertEquals(p + "s", CapitalMap.direction(10, 10, 10, 90, 5));
        assertEquals(p + "sw", CapitalMap.direction(0, 0, -30, 30, 5));
        assertEquals(p + "w", CapitalMap.direction(0, 0, -50, 0, 5));
        assertEquals(p + "nw", CapitalMap.direction(0, 0, -30, -30, 5));
        assertEquals(p + "here", CapitalMap.direction(0, 0, 3, 3, 5));
    }

    @Test
    public void brokenEntriesAreLeftOutAndAMapWithoutBoundsIsRefused() {
        JsonObject json = JsonParser.parseString("{\"x0\": -10, \"z0\": -10, \"width\": 20, \"height\": 20,"
                + " \"places\": [{\"id\": \"inn\", \"kind\": \"inn\", \"npc\": \"innkeeper\", \"x\": 1, \"z\": 2},"
                + " {\"id\": \"odd\", \"kind\": \"spaceport\", \"x\": 0, \"z\": 0}, {\"kind\": \"shop\", \"x\": 0,"
                + " \"z\": 0}, {\"id\": \"far\", \"kind\": \"shop\"}, 7, {\"id\": \"bad\", \"kind\": \"shop\","
                + " \"x\": \"east\", \"z\": 0}],"
                + " \"districts\": [{\"id\": \"plaza\", \"x\": 0, \"z\": 0}, {\"id\": \"\", \"x\": 0, \"z\": 0}]}")
                .getAsJsonObject();
        CapitalMap.Data map = CapitalMap.parse(json);
        assertEquals(1, map.places().size());
        CapitalMap.Place inn = map.places().get(0);
        assertEquals(CapitalMap.Kind.INN, inn.kind());
        assertEquals("innkeeper", inn.npc());
        assertEquals("map.eternalrealm.capital.place.inn.desc", inn.descKey());
        assertEquals(1, map.districts().size());
        assertTrue(map.place("inn").isPresent());
        assertFalse(map.place("odd").isPresent());
        assertNotNull(map.toString());
        for (String broken : List.of("{}", "{\"x0\": 0, \"z0\": 0, \"width\": 0, \"height\": 5}",
                "{\"x0\": 0, \"z0\": 0, \"width\": 5}")) {
            try {
                CapitalMap.parse(JsonParser.parseString(broken).getAsJsonObject());
                fail("refused: " + broken);
            } catch (IllegalArgumentException expected) {
                // fine
            }
        }
    }
}
