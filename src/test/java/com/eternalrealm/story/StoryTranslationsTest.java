package com.eternalrealm.story;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.BeforeClass;
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
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * docs/i18n/story.json holds the Japanese and English text of every key the story uses; the integrator merges it into
 * the lang files. The keys must match the catalog exactly (nothing missing, nothing stale) and every text must fit
 * where it is shown: chat lines at most 60 Japanese characters, titles shorter.
 */
public class StoryTranslationsTest {
    private static final Path FILE = Paths.get("docs", "i18n", "story.json");
    private static final Pattern JAPANESE = Pattern.compile("[\\u3040-\\u30ff\\u3400-\\u9fff\\uff01-\\uff60]");
    private static final StoryCatalog CATALOG = StoryCatalog.standard();
    private static JsonObject root;

    @BeforeClass
    public static void load() throws IOException {
        root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String ja(String key) {
        JsonObject texts = root.getAsJsonObject(key);
        assertTrue("missing key " + key, texts != null);
        return texts.get("ja").getAsString();
    }

    private static String en(String key) {
        return root.getAsJsonObject(key).get("en").getAsString();
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    @Test
    public void everyKeyHasJapaneseAndEnglishAndNothingIsStale() {
        assertEquals(new TreeSet<>(CATALOG.translationKeys()), new TreeSet<>(root.keySet()));
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            assertTrue(key, key.startsWith(StoryCatalog.KEY_PREFIX) || key.startsWith(StoryCatalog.QUEST_KEY_PREFIX));
            JsonObject texts = entry.getValue().getAsJsonObject();
            assertEquals(key, Set.of("ja", "en"), texts.keySet());
            for (String language : Set.of("ja", "en")) {
                String text = texts.get(language).getAsString();
                assertFalse(key + " " + language + " is blank", text.isBlank());
                assertEquals(key + " " + language + " has padding", text.strip(), text);
                assertFalse(key + " " + language + " is a placeholder", text.contains("TODO"));
                // Minecraft's translation formatter would treat % as a format argument; the story passes none.
                assertFalse(key + " " + language + " contains %", text.contains("%"));
            }
        }
    }

    @Test
    public void everyDialogueLineFitsAChatLine() {
        List<String> tooLong = new ArrayList<>();
        for (Dialogue dialogue : CATALOG.dialogues()) {
            for (DialogueLine line : dialogue.lines()) {
                String text = ja(line.textKey());
                if (length(text) > 60) {
                    tooLong.add(line.textKey() + " (" + length(text) + "): " + text);
                }
            }
        }
        if (!tooLong.isEmpty()) {
            fail("lines longer than 60 characters:\n  " + String.join("\n  ", tooLong));
        }
    }

    @Test
    public void titlesNamesAndBarksAreShort() {
        for (Speaker speaker : Speaker.values()) {
            assertTrue(speaker.key(), length(ja(speaker.key())) <= 16);
            assertTrue(speaker.key(), en(speaker.key()).length() <= 40);
        }
        for (Chapter chapter : Chapter.values()) {
            assertTrue(chapter.titleKey(), length(ja(chapter.titleKey())) <= 20);
            assertTrue(chapter.synopsisKey(), length(ja(chapter.synopsisKey())) <= 60);
        }
        for (StoryArea area : StoryArea.values()) {
            AreaCard card = CATALOG.areaCard(area.id()).orElseThrow();
            assertTrue(card.titleKey(), length(ja(card.titleKey())) <= 12);
            assertTrue(card.subtitleKey(), length(ja(card.subtitleKey())) <= 24);
            assertTrue(card.subtitleKey(), en(card.subtitleKey()).length() <= 60);
        }
        for (String boss : StoryProgress.MAIN_BOSSES) {
            for (int phase = 2; phase <= 4; phase++) {
                CATALOG.bossPhaseBark(boss, phase).ifPresent(bark -> assertTrue(bark.textKey(),
                        length(ja(bark.textKey())) <= 30));
            }
        }
        for (EndingSequence.Credit credit : CATALOG.ending().staffRoll()) {
            assertTrue(credit.textKey(), length(ja(credit.textKey())) <= 40);
        }
    }

    @Test
    public void textsAreTranslatedAndStoredWithoutQuotes() {
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            String japanese = ja(key);
            String english = en(key);
            assertFalse(key + ": English text contains Japanese: " + english, JAPANESE.matcher(english).find());
            assertFalse(key + ": stored with quotes, the platform adds them", japanese.startsWith("「")
                    || japanese.endsWith("」") || english.startsWith("\"") && english.endsWith("\""));
            boolean credit = key.startsWith(StoryCatalog.KEY_PREFIX + "staff.");
            if (!credit) {
                assertTrue(key + ": Japanese text expected: " + japanese, JAPANESE.matcher(japanese).find());
            }
        }
    }

    @Test
    public void officialNamesAreUsed() {
        Map<StoryArea, String> areas = Map.of(StoryArea.ARCELIA, "はじまりの草原", StoryArea.ELDER_FOREST, "月影の森",
                StoryArea.SANDRIA_DESERT, "鉄鉱の鉱山", StoryArea.NORDIA_SNOWFIELD, "聖なる滝",
                StoryArea.DRAGONIA_VOLCANO, "古代遺跡", StoryArea.CELESTIA, "天翔の峰",
                StoryArea.TOWER_OF_THE_END, "終焉の森");
        for (Map.Entry<StoryArea, String> area : areas.entrySet()) {
            assertEquals(area.getValue(), ja(CATALOG.areaCard(area.getKey().id()).orElseThrow().titleKey()));
        }
        Map<Speaker, String> bosses = Map.of(Speaker.GUARDIAN, "古代樹の守護者", Speaker.EXECUTIONER, "凍獄の処刑人",
                Speaker.END_GATEKEEPER, "終焉の門番", Speaker.END_KING, "終焉の王");
        for (Map.Entry<Speaker, String> boss : bosses.entrySet()) {
            assertEquals(boss.getValue(), ja(boss.getKey().key()));
        }
        assertTrue(ja(Speaker.LASZLO.key()).contains("盗賊王"));
        assertTrue(ja(Speaker.INGRID.key()).contains("女王"));
        assertTrue(ja(Speaker.AZRAG.key()).contains("竜王"));
        assertTrue(ja(Speaker.ORDO.key()).contains("機械神"));
        // the catchphrase closes the prologue
        Dialogue prologue = CATALOG.dialogue(StoryTrigger.FIRST_LOGIN).orElseThrow();
        assertEquals("十四日間で、世界の運命を変えろ。", ja(prologue.lines().get(prologue.size() - 1).textKey()));
    }

    @Test
    public void jobNamesInTheJobMastersLinesAreTheOfficialOnes() {
        String text = ja(CATALOG.dialogue(new StoryTrigger.NpcTalk("job_master", 0)).orElseThrow().first().textKey());
        for (String job : List.of("ナイト", "アサシン", "メイジ", "レンジャー", "プリースト", "魔剣士")) {
            assertTrue(job, text.contains(job));
        }
        String english = en(CATALOG.dialogue(new StoryTrigger.NpcTalk("job_master", 0)).orElseThrow().first().textKey());
        for (String job : List.of("Knight", "Assassin", "Mage", "Ranger", "Priest", "Spellblade")) {
            assertTrue(job, english.contains(job));
        }
    }
}
