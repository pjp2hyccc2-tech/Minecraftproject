package com.eternalrealm.world.gen;

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
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Checks the アストリア data pack without Minecraft: everything here would otherwise only fail when the server loads
 * the world (unknown references, feature order cycles, vanilla hostile spawns).
 */
public class WorldgenDataTest {
    private static final Path DATA = Paths.get("src", "main", "resources", "data", "eternalrealm");
    private static final Path I18N = Paths.get("docs", "i18n", "worldgen.json");
    private static final Path WORLD_I18N = Paths.get("docs", "i18n", "world.json");
    private static final int STEPS = 11;
    /** Passive animals allowed in the biomes (the mod spawns its own monsters). */
    private static final Set<String> PASSIVE = Set.of("minecraft:sheep", "minecraft:pig", "minecraft:chicken",
            "minecraft:cow", "minecraft:horse", "minecraft:donkey", "minecraft:rabbit", "minecraft:wolf",
            "minecraft:fox", "minecraft:mooshroom", "minecraft:camel", "minecraft:goat", "minecraft:bat",
            "minecraft:glow_squid", "minecraft:squid", "minecraft:salmon");

    @Test
    public void everyBiomeHasExactlyOneDefinition() throws IOException {
        Set<String> files = names(DATA.resolve("worldgen/biome"));
        Set<String> expected = new TreeSet<>();
        for (AstoriaBiome biome : AstoriaBiome.values()) {
            expected.add(biome.id());
        }
        assertEquals(expected, files);
    }

    @Test
    public void biomesSpawnNoVanillaMonsters() throws IOException {
        for (AstoriaBiome biome : AstoriaBiome.values()) {
            JsonObject spawners = biome(biome).getAsJsonObject("spawners");
            assertEquals(biome + " must not spawn vanilla monsters", 0, spawners.getAsJsonArray("monster").size());
            for (Map.Entry<String, JsonElement> category : spawners.entrySet()) {
                for (JsonElement entry : category.getValue().getAsJsonArray()) {
                    String type = entry.getAsJsonObject().get("type").getAsString();
                    assertTrue(biome + " spawns " + type, PASSIVE.contains(type));
                }
            }
            JsonObject effects = biome(biome).getAsJsonObject("effects");
            for (String color : List.of("sky_color", "fog_color", "water_color", "water_fog_color", "grass_color",
                    "foliage_color")) {
                assertTrue(biome + " needs " + color, effects.has(color));
            }
        }
    }

    /** Minecraft builds one feature order per step from all biomes of the biome source and crashes on a cycle. */
    @Test
    public void featureOrderIsConsistentAcrossAllBiomes() throws IOException {
        Map<String, Integer> stepOf = new HashMap<>();
        List<Map<String, Set<String>>> after = new ArrayList<>();
        for (int step = 0; step < STEPS; step++) {
            after.add(new HashMap<>());
        }
        for (AstoriaBiome biome : AstoriaBiome.values()) {
            JsonArray steps = biome(biome).getAsJsonArray("features");
            assertEquals(biome + " feature steps", STEPS, steps.size());
            for (int step = 0; step < STEPS; step++) {
                JsonArray features = steps.get(step).getAsJsonArray();
                Set<String> seen = new HashSet<>();
                for (int i = 0; i < features.size(); i++) {
                    String feature = features.get(i).getAsString();
                    assertTrue(biome + " lists " + feature + " twice", seen.add(feature));
                    Integer previous = stepOf.putIfAbsent(feature, step);
                    assertTrue(feature + " is used in two steps", previous == null || previous == step);
                    for (int j = i + 1; j < features.size(); j++) {
                        after.get(step).computeIfAbsent(feature, key -> new HashSet<>()).add(features.get(j).getAsString());
                    }
                }
            }
        }
        for (int step = 0; step < STEPS; step++) {
            Map<String, Set<String>> graph = after.get(step);
            for (String feature : graph.keySet()) {
                assertFalse("feature order cycle through " + feature + " in step " + step,
                        reaches(graph, feature, feature, new HashSet<>()));
            }
        }
    }

    @Test
    public void everyReferencedFeatureExists() throws IOException {
        Set<String> placed = names(DATA.resolve("worldgen/placed_feature"));
        Set<String> configured = names(DATA.resolve("worldgen/configured_feature"));
        Set<String> used = new HashSet<>();
        for (AstoriaBiome biome : AstoriaBiome.values()) {
            for (JsonElement step : biome(biome).getAsJsonArray("features")) {
                for (JsonElement feature : step.getAsJsonArray()) {
                    String id = feature.getAsString();
                    if (id.startsWith("eternalrealm:")) {
                        String name = id.substring("eternalrealm:".length());
                        assertTrue(biome + " uses missing placed feature " + id, placed.contains(name));
                        used.add(name);
                    } else {
                        assertTrue(id, id.startsWith("minecraft:"));
                    }
                }
            }
        }
        assertEquals("unused placed features", placed, used);
        for (String name : placed) {
            JsonObject json = read(DATA.resolve("worldgen/placed_feature/" + name + ".json"));
            String feature = json.get("feature").getAsString();
            assertTrue(name + " -> " + feature, feature.startsWith("eternalrealm:")
                    && configured.contains(feature.substring("eternalrealm:".length())));
            for (JsonElement modifier : json.getAsJsonArray("placement")) {
                String type = modifier.getAsJsonObject().get("type").getAsString();
                if (type.equals("minecraft:heightmap")) {
                    String heightmap = modifier.getAsJsonObject().get("heightmap").getAsString();
                    assertTrue(name + " must use a world generation heightmap",
                            heightmap.equals("WORLD_SURFACE_WG") || heightmap.equals("OCEAN_FLOOR_WG"));
                }
            }
        }
        for (String name : configured) {
            JsonObject json = read(DATA.resolve("worldgen/configured_feature/" + name + ".json"));
            String type = json.get("type").getAsString();
            assertTrue(type, type.startsWith("eternalrealm:"));
            List<String> variants = WorldgenIds.FEATURE_VARIANTS.get(type.substring("eternalrealm:".length()));
            assertTrue(name + " has unknown feature type " + type, variants != null);
            JsonObject config = json.getAsJsonObject("config");
            if (variants.isEmpty()) {
                assertEquals(name + " takes no config", 0, config.size());
            } else {
                assertTrue(name + " variant " + config, variants.contains(config.get("variant").getAsString()));
            }
        }
    }

    @Test
    public void dimensionUsesTheOverworldNoiseAndTheAstoriaBiomeSource() throws IOException {
        JsonObject dimension = read(DATA.resolve("dimension/" + WorldgenIds.DIMENSION + ".json"));
        assertEquals("eternalrealm:" + WorldgenIds.DIMENSION, dimension.get("type").getAsString());
        JsonObject generator = dimension.getAsJsonObject("generator");
        assertEquals("minecraft:noise", generator.get("type").getAsString());
        assertEquals("minecraft:overworld", generator.get("settings").getAsString());
        assertEquals("eternalrealm:" + WorldgenIds.BIOME_SOURCE,
                generator.getAsJsonObject("biome_source").get("type").getAsString());
        JsonObject type = read(DATA.resolve("dimension_type/" + WorldgenIds.DIMENSION + ".json"));
        assertEquals(384, type.get("height").getAsInt());
        assertEquals(-64, type.get("min_y").getAsInt());
    }

    @Test
    public void everyWorldgenTextIsTranslated() throws IOException {
        JsonObject texts = JsonParser.parseString(Files.readString(I18N, StandardCharsets.UTF_8)).getAsJsonObject();
        List<String> required = new ArrayList<>();
        for (AstoriaBiome biome : AstoriaBiome.values()) {
            required.add(biome.translationKey());
        }
        for (HubSpot spot : HubSpot.values()) {
            required.add(spot.translationKey());
        }
        for (LandmarkSite site : LandmarkSites.all()) {
            required.add("landmark.eternalrealm." + site.dungeonId());
        }
        required.add("landmark.eternalrealm.capital");
        required.add("dimension.eternalrealm.astoria");
        JsonObject worldTexts = JsonParser.parseString(Files.readString(WORLD_I18N, StandardCharsets.UTF_8))
                .getAsJsonObject();
        for (int area = 1; area <= AstoriaAreas.COUNT; area++) {
            assertTrue("area title owned by the world module", worldTexts.has(AstoriaAreas.titleKey(area)));
            assertTrue("area description owned by the world module", worldTexts.has(AstoriaAreas.subtitleKey(area)));
            assertFalse("area texts belong to docs/i18n/world.json", texts.has(AstoriaAreas.titleKey(area)));
        }
        for (String key : required) {
            JsonObject entry = texts.getAsJsonObject(key);
            if (entry == null) {
                fail("docs/i18n/worldgen.json is missing " + key);
            }
            assertFalse(key + " ja", entry.get("ja").getAsString().isBlank());
            assertFalse(key + " en", entry.get("en").getAsString().isBlank());
        }
    }

    private static boolean reaches(Map<String, Set<String>> graph, String from, String target, Set<String> visited) {
        for (String next : graph.getOrDefault(from, Set.of())) {
            if (next.equals(target)) {
                return true;
            }
            if (visited.add(next) && reaches(graph, next, target, visited)) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject biome(AstoriaBiome biome) throws IOException {
        return read(DATA.resolve("worldgen/biome/" + biome.id() + ".json"));
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> names(Path directory) throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".json"))
                    .forEach(name -> names.add(name.substring(0, name.length() - ".json".length())));
        }
        return names;
    }
}
