package com.eternalrealm.world;

import com.eternalrealm.monster.MonsterRole;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.Rarity;
import com.eternalrealm.quest.AcceptResult;
import com.eternalrealm.quest.NpcDefinition;
import com.eternalrealm.quest.Npcs;
import com.eternalrealm.quest.Objective;
import com.eternalrealm.quest.QuestDefinition;
import com.eternalrealm.quest.QuestKind;
import com.eternalrealm.quest.QuestMessages;
import com.eternalrealm.quest.Quests;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * docs/i18n/world.json must translate exactly the keys the world, monster and quest modules produce, in Japanese (the
 * main language) and English, with the same number of format arguments. Item names of the crafting materials belong to
 * the crafting module and are not part of this file.
 */
public class WorldTranslationsTest {
    private static final Path FILE = Paths.get("docs", "i18n", "world.json");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?s");

    static Set<String> expectedKeys() {
        Set<String> keys = new TreeSet<>();
        for (AreaDefinition area : Areas.all()) {
            keys.add(area.translationKey());
            keys.add(area.descriptionKey());
        }
        keys.addAll(WorldMessages.ALL);
        for (MonsterTemplate template : Monsters.all()) {
            keys.add(template.translationKey());
        }
        for (MonsterRole role : MonsterRole.values()) {
            keys.add(role.translationKey());
            keys.add(role.nameplateKey());
        }
        for (Rarity rarity : Rarity.values()) {
            keys.add(rarity.translationKey());
        }
        for (QuestDefinition quest : Quests.catalog().all()) {
            keys.add(quest.translationKey());
            keys.add(quest.descriptionKey());
        }
        for (Objective objective : List.<Objective>of(Objective.KillMonsters.of("x", 1),
                new Objective.KillMonsters("x", 1, 1), Objective.KillMonsters.anyIn(0, 1), Objective.KillMonsters.anyIn(1, 1),
                new Objective.CollectItems("a:b", 1), new Objective.ClearDungeon("d1", 1), new Objective.DefeatBoss("b1"),
                new Objective.ReachArea(1), new Objective.TalkTo("n"))) {
            keys.add(objective.translationKey());
        }
        for (QuestKind kind : QuestKind.values()) {
            keys.add(kind.translationKey());
        }
        for (AcceptResult result : AcceptResult.values()) {
            keys.add(result.messageKey());
        }
        keys.addAll(QuestMessages.ALL);
        for (NpcDefinition npc : Npcs.all()) {
            keys.add(npc.translationKey());
        }
        return keys;
    }

    private static JsonObject file() throws IOException {
        assertTrue("missing " + FILE.toAbsolutePath(), Files.isRegularFile(FILE));
        return JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    public void everyKeyTheModulesUseIsTranslatedAndNothingElse() throws IOException {
        Set<String> expected = expectedKeys();
        Set<String> actual = new TreeSet<>(file().keySet());
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> unused = new TreeSet<>(actual);
        unused.removeAll(expected);
        assertEquals("missing translations", Set.of(), missing);
        assertEquals("translations nobody uses", Set.of(), unused);
    }

    @Test
    public void everyEntryHasJapaneseAndEnglishWithTheSameArguments() throws IOException {
        for (Map.Entry<String, JsonElement> entry : file().entrySet()) {
            String key = entry.getKey();
            JsonObject value = entry.getValue().getAsJsonObject();
            assertEquals(key, Set.of("ja", "en"), value.keySet());
            String ja = value.get("ja").getAsString();
            String en = value.get("en").getAsString();
            assertFalse(key, ja.isBlank() || en.isBlank());
            assertEquals(key + " argument count", placeholders(en), placeholders(ja));
            assertFalse(key + " has a stray %", PLACEHOLDER.matcher(ja).replaceAll("").replace("%%", "").contains("%"));
            assertFalse(key + " has a stray %", PLACEHOLDER.matcher(en).replaceAll("").replace("%%", "").contains("%"));
            if (!key.startsWith("hud.") && !key.startsWith("quest_objective.") && !key.startsWith("message.")) {
                assertTrue(key + " should be written in Japanese", ja.codePoints().anyMatch(c -> c > 0x3000));
            }
        }
    }

    private static int placeholders(String text) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
