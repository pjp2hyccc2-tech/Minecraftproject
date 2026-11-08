package com.eternalrealm.ranking;

import com.eternalrealm.season.SeasonEvent;
import com.eternalrealm.season.SeasonStatus;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Every player-visible key of the ranking and season modules has a Japanese and an English text in
 * docs/i18n/ranking.json (merged into the lang files by the integration), and the file has no stale keys.
 */
public class RankingTranslationsTest {
    private static final Path FILE = Paths.get("docs", "i18n", "ranking.json");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?s");

    static Set<String> keysUsedInCode() {
        Set<String> keys = new TreeSet<>();
        RankingCategory.all().forEach(category -> keys.add(category.translationKey()));
        SeasonCounter.all().forEach(counter -> keys.add(counter.translationKey()));
        BossDifficulty.all().forEach(difficulty -> keys.add(difficulty.translationKey()));
        PartyBracket.all().forEach(bracket -> keys.add(bracket.translationKey()));
        keys.addAll(TimeAttackValidator.ALL_REASONS);
        keys.add(TimeAttackSubmission.FIRST_CLEAR_MESSAGE);
        keys.add(TimeAttackSubmission.RECORD_MESSAGE);
        keys.add(TimeAttackSubmission.PERSONAL_BEST_MESSAGE);
        SeasonStatus.all().forEach(status -> keys.add(status.translationKey()));
        for (SeasonEvent.Type type : SeasonEvent.Type.values()) {
            type.announcementKey().ifPresent(keys::add);
        }
        return keys;
    }

    @Test
    public void everyKeyHasJapaneseAndEnglishWithMatchingPlaceholders() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("keys used in code vs. keys in " + FILE, keysUsedInCode(), new TreeSet<>(root.keySet()));
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            JsonObject texts = entry.getValue().getAsJsonObject();
            String japanese = texts.get("ja").getAsString();
            String english = texts.get("en").getAsString();
            assertFalse(entry.getKey(), japanese.isBlank());
            assertFalse(entry.getKey(), english.isBlank());
            assertEquals(entry.getKey(), placeholders(english), placeholders(japanese));
            assertEquals(entry.getKey(), Set.of("ja", "en"), texts.keySet());
        }
    }

    @Test
    public void keysFollowTheModConvention() {
        for (String key : keysUsedInCode()) {
            assertTrue(key, key.matches("[a-z_]+\\.eternalrealm\\.[a-z_.]+"));
        }
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
