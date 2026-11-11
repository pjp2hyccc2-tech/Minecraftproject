package com.eternalrealm.guild.house;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The shipped catalogue: twenty designs, every theme different, four per tier, prices rising with the tier, a name and a
 * description in both languages, a blueprint with the spots the game needs, and the same ids as the capital's
 * portal blocks ({@code tools/capital/blocks.py}).
 */
public class GuildHouseCatalogTest {
    private static final Path RESOURCES = Paths.get("src", "main", "resources");
    static final List<String> THEMES = List.of("northern_longhouse", "mushroom_grove", "ancient_ruins", "pirate_ship",
            "flower_glasshouse", "dwarven_forge", "desert_palace", "observatory", "japanese_mansion", "great_tree",
            "sage_library", "jungle_temple", "gothic_cathedral", "coral_hall", "volcanic_fortress", "ice_castle",
            "dragon_lair", "arcane_tower", "sky_temple", "marble_palace");

    static GuildHouseCatalog shipped() throws IOException {
        try (Reader reader = Files.newBufferedReader(RESOURCES.resolve("data/eternalrealm/guildhouse/designs.json"),
                StandardCharsets.UTF_8)) {
            return GuildHouseCatalog.parse(reader);
        }
    }

    @Test
    public void twentyDifferentDesignsFourPerTier() throws IOException {
        GuildHouseCatalog catalog = shipped();
        assertEquals(20, catalog.size());
        assertEquals(THEMES, catalog.designs().stream().map(GuildHouseDesign::id).toList());
        long previousMax = 0;
        Set<Integer> colours = new HashSet<>();
        for (int tier = 1; tier <= 5; tier++) {
            List<GuildHouseDesign> designs = catalog.tier(tier);
            assertEquals("tier " + tier, 4, designs.size());
            long min = designs.stream().mapToLong(GuildHouseDesign::price).min().orElseThrow();
            long max = designs.stream().mapToLong(GuildHouseDesign::price).max().orElseThrow();
            assertTrue("tier " + tier + " must cost more than tier " + (tier - 1), min > previousMax);
            previousMax = max;
            for (GuildHouseDesign design : designs) {
                assertTrue(design.id() + " portal colour used twice", colours.add(design.baseColor()));
                if (tier > 1) {
                    int below = catalog.tier(tier - 1).stream().mapToInt(GuildHouseDesign::width).max().orElseThrow();
                    assertTrue(design.id() + " is smaller than the tier below", design.width() >= below);
                }
            }
        }
    }

    @Test
    public void everyDesignHasNamesDescriptionsAndABlueprint() throws IOException {
        JsonObject ja = lang("ja_jp");
        JsonObject en = lang("en_us");
        for (GuildHouseDesign design : shipped().designs()) {
            for (String key : List.of(design.nameKey(), design.descriptionKey())) {
                assertTrue("missing ja " + key, ja.has(key));
                assertTrue("missing en " + key, en.has(key));
            }
            Path manifest = RESOURCES.resolve("data/eternalrealm/estates/guild/" + design.id() + "/manifest.json");
            assertTrue("missing blueprint " + manifest, Files.isRegularFile(manifest));
            JsonObject spots = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("spots");
            for (String spot : List.of("entry", "exit", "board")) {
                assertTrue(design.id() + " has no spot " + spot, spots.has(spot));
            }
        }
    }

    @Test
    public void theCapitalBuilderKnowsTheSameThemes() throws IOException {
        String blocks = Files.readString(Paths.get("tools", "capital", "blocks.py"), StandardCharsets.UTF_8);
        Matcher tuple = Pattern.compile("GUILD_THEMES = \\(([^)]*)\\)", Pattern.DOTALL).matcher(blocks);
        assertTrue(tuple.find());
        Matcher id = Pattern.compile("\"([a-z_]+)\"").matcher(tuple.group(1));
        List<String> ids = new java.util.ArrayList<>();
        while (id.find()) {
            ids.add(id.group(1));
        }
        assertEquals(THEMES, ids);
    }

    @Test
    public void malformedCataloguesAreRejected() {
        for (String bad : List.of("{}", "{\"schema\":2,\"designs\":[]}", "{\"schema\":1,\"designs\":[]}",
                "{\"schema\":1,\"designs\":[{\"id\":\"a\",\"tier\":6,\"price\":1,\"base\":\"#000000\","
                        + "\"accent\":\"#000000\",\"size\":[1,1,1]}]}",
                "{\"schema\":1,\"designs\":[{\"id\":\"a\",\"tier\":1,\"price\":1,\"base\":\"red\","
                        + "\"accent\":\"#000000\",\"size\":[1,1,1]}]}")) {
            try {
                GuildHouseCatalog.parse(new StringReader(bad));
                throw new AssertionError("accepted " + bad);
            } catch (IllegalArgumentException expected) {
                assertFalse(expected.getMessage().isEmpty());
            }
        }
    }

    private static JsonObject lang(String name) throws IOException {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve("assets/eternalrealm/lang/" + name + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
