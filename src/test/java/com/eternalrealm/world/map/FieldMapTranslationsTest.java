package com.eternalrealm.world.map;

import com.eternalrealm.dungeon.hazard.HazardKind;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * docs/i18n/fieldmap.json holds the Japanese and English text of every key of the map, the traps and the trap damage;
 * the lang files must contain them (merged with scripts/add_lang.py).
 */
public class FieldMapTranslationsTest {
    private static final Path FILE = Paths.get("docs", "i18n", "fieldmap.json");
    private static final Path LANG = Paths.get("src", "main", "resources", "assets", "eternalrealm", "lang");
    /** Keys used by the client map code (which this test cannot load). */
    private static final List<String> CLIENT_KEYS = List.of("key.eternalrealm.world_map",
            "key.eternalrealm.minimap_toggle", "key.eternalrealm.minimap_zoom_in", "key.eternalrealm.minimap_zoom_out",
            "screen.eternalrealm.tab.map", "screen.eternalrealm.map.title", "screen.eternalrealm.map.center",
            "screen.eternalrealm.map.journal", "screen.eternalrealm.map.hint", "screen.eternalrealm.map.painting",
            "screen.eternalrealm.map.elsewhere", "map.eternalrealm.capital.desc", "map.eternalrealm.tower.desc",
            "map.eternalrealm.tower.final_open", "map.eternalrealm.level_range", "map.eternalrealm.dungeon_level",
            "map.eternalrealm.dungeon_label", "map.eternalrealm.sealed", "map.eternalrealm.sealed.desc",
            "map.eternalrealm.undiscovered", "map.eternalrealm.you", "map.eternalrealm.member.health",
            "map.eternalrealm.npc.offer", "map.eternalrealm.npc.ready", "map.eternalrealm.entrance_hall",
            "map.eternalrealm.room", "map.eternalrealm.room_short", "map.eternalrealm.boss_room",
            "map.eternalrealm.trap", "death.attack.eternalrealm.trap", "death.attack.eternalrealm.trap.player");

    private static Set<String> codeKeys() {
        Set<String> keys = new TreeSet<>(CLIENT_KEYS);
        for (HazardKind kind : HazardKind.values()) {
            keys.add(kind.translationKey());
        }
        keys.add(MapMarkers.CAPITAL_KEY);
        keys.add(MapMarkers.TOWER_KEY);
        return keys;
    }

    @Test
    public void everyKeyHasJapaneseAndEnglishAndNothingIsStale() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(codeKeys(), new TreeSet<>(root.keySet()));
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            JsonObject texts = entry.getValue().getAsJsonObject();
            assertEquals(entry.getKey(), Set.of("ja", "en"), texts.keySet());
            String japanese = texts.get("ja").getAsString();
            String english = texts.get("en").getAsString();
            assertFalse(entry.getKey(), japanese.isBlank() || english.isBlank());
            assertEquals(entry.getKey() + ": same format arguments", arguments(japanese), arguments(english));
            assertFalse(entry.getKey() + ": Minecraft only formats %s", japanese.contains("%d") || english.contains("%d"));
        }
    }

    @Test
    public void theLangFilesContainEveryKey() throws IOException {
        for (String file : List.of("ja_jp.json", "en_us.json")) {
            JsonObject lang = JsonParser.parseString(Files.readString(LANG.resolve(file), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (String key : codeKeys()) {
                assertTrue(file + " lacks " + key, lang.has(key));
            }
        }
    }

    private static List<String> arguments(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = Pattern.compile("%(\\d+\\$)?s").matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        found.sort(String::compareTo);
        return found;
    }
}
