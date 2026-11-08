package com.eternalrealm.dungeon;

import com.eternalrealm.boss.BossAbility;
import com.eternalrealm.boss.BossCommand;
import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.Difficulty;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * docs/i18n/boss.json holds the Japanese and English text of every key the boss and dungeon modules define; the
 * integrator merges it into the lang files. Keys must match exactly, so nothing is missing and nothing is stale.
 */
public class TranslationKeysTest {
    private static final Path FILE = Paths.get("docs", "i18n", "boss.json");

    private static Set<String> codeKeys() {
        Set<String> keys = new TreeSet<>();
        for (BossDefinition boss : Bosses.all()) {
            keys.add(boss.translationKey());
            for (BossAbility ability : boss.abilities()) {
                keys.add(ability.translationKey());
            }
        }
        for (Difficulty difficulty : Difficulty.values()) {
            keys.add(difficulty.translationKey());
        }
        for (DungeonDefinition dungeon : Dungeons.all()) {
            keys.add(dungeon.translationKey());
        }
        keys.add(Dungeons.FINAL_RAID.translationKey());
        for (FailureReason reason : FailureReason.values()) {
            keys.add(reason.messageKey());
        }
        keys.addAll(Set.of(BossCommand.PhaseChanged.MESSAGE_KEY, BossCommand.Enraged.MESSAGE_KEY,
                BossCommand.ShieldChanged.SHIELD_UP_KEY, BossCommand.ShieldChanged.SHIELD_DOWN_KEY,
                DungeonRunEvent.Started.MESSAGE_KEY, DungeonRunEvent.SpawnWave.MESSAGE_KEY,
                DungeonRunEvent.OpenDoor.MESSAGE_KEY, DungeonRunEvent.StartBoss.MESSAGE_KEY,
                DungeonRunEvent.Cleared.MESSAGE_KEY, InstanceAllocator.FULL_MESSAGE_KEY));
        return keys;
    }

    @Test
    public void everyKeyHasJapaneseAndEnglishAndNothingIsStale() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(codeKeys(), new TreeSet<>(root.keySet()));
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            JsonObject texts = entry.getValue().getAsJsonObject();
            assertEquals(entry.getKey(), Set.of("ja", "en"), texts.keySet());
            for (String language : Set.of("ja", "en")) {
                String text = texts.get(language).getAsString();
                assertFalse(entry.getKey() + " " + language, text.isBlank());
                assertFalse(entry.getKey() + " " + language + " is a placeholder", text.contains("TODO"));
            }
            String japanese = texts.get("ja").getAsString();
            String english = texts.get("en").getAsString();
            assertEquals(entry.getKey() + ": same format arguments", arguments(japanese), arguments(english));
        }
    }

    @Test
    public void officialNamesAreUsed() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> official = Map.of("boss.eternalrealm.b2", "古代樹の守護者", "boss.eternalrealm.b3", "砂塵の巨神",
                "boss.eternalrealm.b5", "氷獄の女王", "boss.eternalrealm.b6", "灼炎の竜王", "boss.eternalrealm.b7", "天空の機械神",
                "boss.eternalrealm.final", "終焉の王", "dungeon.eternalrealm.d1", "失われた地下都市",
                "dungeon.eternalrealm.d4", "凍てつく地下牢", "dungeon.eternalrealm.d5", "氷結の城",
                "dungeon.eternalrealm.d8", "終焉の塔");
        for (Map.Entry<String, String> entry : official.entrySet()) {
            assertEquals(entry.getValue(), root.getAsJsonObject(entry.getKey()).get("ja").getAsString());
        }
        assertTrue(root.has("dungeon.eternalrealm.d7"));
    }

    private static Set<String> arguments(String text) {
        Set<String> found = new TreeSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("%(\\d+\\$)?s").matcher(text);
        int plain = 0;
        while (matcher.find()) {
            found.add(matcher.group(1) == null ? "#" + (++plain) : matcher.group(1));
        }
        return found;
    }
}
