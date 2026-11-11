package com.eternalrealm.economy;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The texts of the auction hall and the trading board (docs/i18n/market.json): every hall refusal and every key the
 * market screens and service use is translated in both languages, with the same arguments, and merged into the lang
 * files, so no screen ever shows a raw key.
 */
public class MarketTranslationsTest {
    private static final Path FILE = Paths.get("docs", "i18n", "market.json");
    private static final Path LANG = Paths.get("src", "main", "resources", "assets", "eternalrealm", "lang");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s");
    private static final Pattern CODE_KEY = Pattern.compile(
            "\"((?:message|screen|key|mail_reason)\\.eternalrealm\\.[a-z0-9_.]*[a-z0-9_])\"");
    private static final List<Path> CODE = List.of(
            Paths.get("src", "main", "java", "com", "eternalrealm", "ui", "market"),
            Paths.get("src", "main", "java", "com", "eternalrealm", "client", "market"),
            Paths.get("src", "main", "java", "com", "eternalrealm", "economy", "forge"));

    @Test
    public void everyHallRefusalHasAText() throws IOException {
        JsonObject texts = load();
        for (HallOutcome outcome : HallOutcome.values()) {
            outcome.messageKey().ifPresent(key -> assertTrue("missing " + key, texts.has(key)));
        }
    }

    @Test
    public void everyEntryHasJapaneseAndEnglishWithTheSameArguments() throws IOException {
        for (Map.Entry<String, JsonElement> entry : load().entrySet()) {
            JsonObject texts = entry.getValue().getAsJsonObject();
            assertEquals(entry.getKey(), Set.of("ja", "en"), texts.keySet());
            String japanese = texts.get("ja").getAsString();
            String english = texts.get("en").getAsString();
            assertFalse(entry.getKey(), japanese.isBlank() || english.isBlank());
            assertEquals("placeholders of " + entry.getKey(), placeholders(english), placeholders(japanese));
        }
    }

    @Test
    public void entriesAreMergedIntoTheLangFiles() throws IOException {
        JsonObject english = lang("en_us.json");
        JsonObject japanese = lang("ja_jp.json");
        for (Map.Entry<String, JsonElement> entry : load().entrySet()) {
            JsonObject texts = entry.getValue().getAsJsonObject();
            assertTrue("not merged: " + entry.getKey(), english.has(entry.getKey()) && japanese.has(entry.getKey()));
            assertEquals(entry.getKey(), texts.get("en").getAsString(), english.get(entry.getKey()).getAsString());
            assertEquals(entry.getKey(), texts.get("ja").getAsString(), japanese.get(entry.getKey()).getAsString());
        }
    }

    /** Keys written out in the market's code (screens, service, commands) exist in both lang files. */
    @Test
    public void everyKeyInTheMarketCodeIsTranslated() throws IOException {
        JsonObject english = lang("en_us.json");
        JsonObject japanese = lang("ja_jp.json");
        Set<String> missing = new TreeSet<>();
        int found = 0;
        for (Path directory : CODE) {
            List<Path> files;
            try (Stream<Path> list = Files.list(directory)) {
                files = list.filter(path -> path.toString().endsWith(".java")).toList();
            }
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (directory.endsWith("forge") && !name.startsWith("Market")) {
                    continue;
                }
                Matcher matcher = CODE_KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    found++;
                    if (!english.has(matcher.group(1)) || !japanese.has(matcher.group(1))) {
                        missing.add(name + ": " + matcher.group(1));
                    }
                }
            }
        }
        assertTrue("the scan found the market's keys", found > 100);
        assertEquals(Set.of(), missing);
    }

    private static JsonObject load() throws IOException {
        return JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject lang(String file) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<Integer> placeholders(String text) {
        Matcher matcher = PLACEHOLDER.matcher(text.replace("%%", ""));
        List<Integer> arguments = new ArrayList<>();
        int next = 1;
        while (matcher.find()) {
            arguments.add(matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : next++);
        }
        Collections.sort(arguments);
        return arguments;
    }
}
