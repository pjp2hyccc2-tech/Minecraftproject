package com.eternalrealm.entity;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Consistency of the generated entity art (tools/art_entities/generate.py) without Minecraft: every texture has its
 * names in docs/i18n/entity.json, every boss of the boss module has an entity name and a texture, glow textures
 * pair with body textures, and texture sizes match the UV map the models were generated with.
 */
public class EntityAssetsTest {
    private static final Path TEXTURES = Paths.get("src", "main", "resources", "assets", "eternalrealm", "textures", "entity");
    private static final Path I18N = Paths.get("docs", "i18n", "entity.json");
    private static final Path UV_MAP = Paths.get("tools", "art_entities", "uv_map.txt");
    private static final List<String> ENEMY_RIGS = List.of("humanoid", "beast", "golem", "flyer");
    private static final List<String> BOSS_RIGS = List.of("king", "treant", "colossus", "queen", "dragon");

    private static JsonObject i18n() throws IOException {
        return JsonParser.parseString(Files.readString(I18N, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<String> bodyTextures(String rig) throws IOException {
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(TEXTURES.resolve(rig))) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".png") && !name.endsWith("_glow.png"))
                    .forEach(name -> ids.add(name.substring(0, name.length() - 4)));
        }
        return ids;
    }

    @Test
    public void everyKeyHasJapaneseAndEnglish() throws IOException {
        for (Map.Entry<String, JsonElement> entry : i18n().entrySet()) {
            JsonObject texts = entry.getValue().getAsJsonObject();
            assertEquals(entry.getKey(), Set.of("ja", "en"), texts.keySet());
            assertFalse(entry.getKey(), texts.get("ja").getAsString().isBlank());
            assertFalse(entry.getKey(), texts.get("en").getAsString().isBlank());
        }
    }

    @Test
    public void everyEnemyVariantTextureIsNamed() throws IOException {
        JsonObject names = i18n();
        Set<String> variants = new TreeSet<>();
        for (String rig : ENEMY_RIGS) {
            assertTrue("entity name of " + rig, names.has("entity.eternalrealm." + rig + "_enemy"));
            List<String> ids = bodyTextures(rig);
            assertFalse("no textures for " + rig, ids.isEmpty());
            for (String id : ids) {
                assertTrue("duplicate variant id " + id, variants.add(id));
                assertTrue("missing name of variant " + id, names.has("entity.eternalrealm.enemy." + id));
            }
        }
        for (String key : names.keySet()) {
            if (key.startsWith("entity.eternalrealm.enemy.")) {
                assertTrue("stale variant name " + key, variants.contains(key.substring("entity.eternalrealm.enemy.".length())));
            }
        }
        assertTrue(variants.containsAll(List.of("bandit", "ghoul", "mummy", "cultist", "prisoner", "jailer", "ice_knight",
                "frost_mage", "fire_imp", "dark_knight", "void_mage", "shade", "wolf", "cave_rat", "frost_hound", "salamander",
                "scorpion", "abyss_hound", "treant", "sand_golem", "ice_golem", "lava_golem", "ruin_construct", "harpy",
                "ice_wraith", "storm_spirit", "sky_sentinel", "fire_spirit")));
    }

    @Test
    public void everyBossHasANameAndATexture() throws IOException {
        JsonObject names = i18n();
        JsonObject bossNames = JsonParser.parseString(Files.readString(Paths.get("docs", "i18n", "boss.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> bossTextures = new TreeSet<>();
        for (String rig : BOSS_RIGS) {
            bossTextures.addAll(bodyTextures(rig));
        }
        for (BossDefinition boss : Bosses.all()) {
            String key = "entity.eternalrealm.boss_" + boss.id();
            assertTrue("missing " + key, names.has(key));
            assertEquals("entity name = boss name", bossNames.getAsJsonObject(boss.translationKey()).get("ja").getAsString(),
                    names.getAsJsonObject(key).get("ja").getAsString());
            assertTrue("missing texture of boss " + boss.id(), bossTextures.contains(boss.id()));
        }
        assertTrue("final boss phase looks", bossTextures.containsAll(List.of("final_p1", "final_p2", "final_p3")));
        assertTrue("shield texture", Files.isRegularFile(TEXTURES.resolve("fx").resolve("boss_shield.png")));
        assertTrue("aura texture", Files.isRegularFile(TEXTURES.resolve("fx").resolve("boss_aura.png")));
    }

    /** Every later form of every boss has its own look ({@code <id>_p1} .. {@code <id>_p3}, see ERBoss.lookFor). */
    @Test
    public void bossesChangeTheirLookInLaterForms() throws IOException {
        Set<String> bossTextures = new TreeSet<>();
        for (String rig : BOSS_RIGS) {
            bossTextures.addAll(bodyTextures(rig));
        }
        for (BossDefinition boss : Bosses.all()) {
            for (int form = 1; form < Math.min(4, boss.phases().size()); form++) {
                assertTrue(boss.id() + ": form " + (form + 1) + " has no look of its own",
                        bossTextures.contains(boss.id() + "_p" + form));
            }
            assertFalse(boss.id() + ": no look for a form that does not exist",
                    bossTextures.contains(boss.id() + "_p" + boss.phases().size()));
        }
    }

    @Test
    public void texturesMatchTheGeneratedUvLayout() throws IOException {
        Map<String, int[]> sizes = new HashMap<>();
        for (String line : Files.readAllLines(UV_MAP, StandardCharsets.UTF_8)) {
            if (line.startsWith("== ")) {
                String[] parts = line.substring(3).trim().split(" ");
                String[] wh = parts[1].split("x");
                sizes.put(parts[0], new int[]{Integer.parseInt(wh[0]), Integer.parseInt(wh[1])});
            }
        }
        List<String> rigs = new ArrayList<>(ENEMY_RIGS);
        rigs.addAll(BOSS_RIGS);
        for (String rig : rigs) {
            int[] expected = sizes.get(rig);
            assertTrue("rig missing from uv_map.txt: " + rig, expected != null);
            for (String id : bodyTextures(rig)) {
                assertSize(TEXTURES.resolve(rig).resolve(id + ".png"), expected);
                Path glow = TEXTURES.resolve(rig).resolve(id + "_glow.png");
                if (Files.exists(glow)) {
                    assertSize(glow, expected);
                }
            }
        }
    }

    private static void assertSize(Path png, int[] expected) throws IOException {
        try (InputStream in = Files.newInputStream(png); DataInputStream data = new DataInputStream(in)) {
            byte[] signature = new byte[8];
            data.readFully(signature);
            assertEquals("not a PNG: " + png, 'P', signature[1]);
            data.readInt();
            byte[] type = new byte[4];
            data.readFully(type);
            assertEquals("IHDR", new String(type, StandardCharsets.US_ASCII));
            int width = data.readInt();
            int height = data.readInt();
            assertEquals(png + " width", expected[0], width);
            assertEquals(png + " height", expected[1], height);
        }
    }
}
