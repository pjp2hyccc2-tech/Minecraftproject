package com.eternalrealm.mount;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Buying, calling, the flight ceiling, ownership and the texts of the mounts. */
public class MountRulesTest {
    private static final MountRules.Situation FINE = new MountRules.Situation(true, false, false, false, false, true);

    @Test
    public void theMountsComeAtLevelsTenThirtyAndFifty() {
        assertEquals(10, MountKind.HORSE.requiredLevel());
        assertEquals(30, MountKind.GRIFFIN.requiredLevel());
        assertEquals(50, MountKind.BIKE.requiredLevel());
        assertTrue(MountKind.HORSE.price() < MountKind.GRIFFIN.price());
        assertTrue(MountKind.GRIFFIN.price() < MountKind.BIKE.price());
        assertTrue(MountKind.GRIFFIN.flies());
        assertFalse(MountKind.HORSE.flies() || MountKind.BIKE.flies());
        Set<String> ids = new HashSet<>();
        for (MountKind kind : MountKind.values()) {
            assertTrue(ids.add(kind.id()));
            assertEquals(Optional.of(kind), MountKind.byId(kind.id()));
            assertEquals("mount_" + kind.id(), kind.entityPath());
        }
        assertEquals(Optional.empty(), MountKind.byId("dragon"));
        assertEquals(Optional.empty(), MountKind.byId(null));
    }

    @Test
    public void buyingNeedsTheLevelAndTheGoldAndHappensOnce() {
        MountOwnership none = MountOwnership.NONE;
        assertEquals(MountRules.Purchase.LEVEL_TOO_LOW, MountRules.purchase(MountKind.HORSE, 9, 1_000_000L, none));
        assertEquals(MountRules.Purchase.OK, MountRules.purchase(MountKind.HORSE, 10, MountKind.HORSE.price(), none));
        assertEquals(MountRules.Purchase.NOT_ENOUGH_GOLD,
                MountRules.purchase(MountKind.HORSE, 10, MountKind.HORSE.price() - 1, none));
        assertEquals(MountRules.Purchase.LEVEL_TOO_LOW, MountRules.purchase(MountKind.GRIFFIN, 29, 1_000_000L, none));
        assertEquals(MountRules.Purchase.OK, MountRules.purchase(MountKind.GRIFFIN, 30, 1_000_000L, none));
        assertEquals(MountRules.Purchase.LEVEL_TOO_LOW, MountRules.purchase(MountKind.BIKE, 49, 1_000_000L, none));
        assertEquals(MountRules.Purchase.OK, MountRules.purchase(MountKind.BIKE, 50, 1_000_000L, none));
        MountOwnership horse = none.withBought(MountKind.HORSE);
        assertEquals(MountRules.Purchase.ALREADY_OWNED, MountRules.purchase(MountKind.HORSE, 60, 1_000_000L, horse));
    }

    @Test
    public void aCallNeedsAMountAndASafePlace() {
        MountOwnership horse = MountOwnership.NONE.withBought(MountKind.HORSE);
        assertEquals(MountRules.Call.OK, MountRules.call(horse, FINE));
        assertEquals(MountRules.Call.NONE_OWNED, MountRules.call(MountOwnership.NONE, FINE));
        assertEquals(MountRules.Call.NOT_HERE, MountRules.call(horse,
                new MountRules.Situation(false, true, true, true, true, false)));
        assertEquals(MountRules.Call.DOWN, MountRules.call(horse,
                new MountRules.Situation(true, true, true, true, true, false)));
        assertEquals(MountRules.Call.IN_BOSS_FIGHT, MountRules.call(horse,
                new MountRules.Situation(true, true, true, false, true, false)));
        assertEquals(MountRules.Call.IN_COMBAT, MountRules.call(horse,
                new MountRules.Situation(true, false, true, false, true, false)));
        assertEquals(MountRules.Call.IN_WATER, MountRules.call(horse,
                new MountRules.Situation(true, false, false, false, true, false)));
        assertEquals(MountRules.Call.NO_ROOM, MountRules.call(horse,
                new MountRules.Situation(true, false, false, false, false, false)));
        for (MountRules.Call call : MountRules.Call.values()) {
            assertEquals(call == MountRules.Call.OK, call.messageKey().isEmpty());
        }
    }

    @Test
    public void theFlightCeilingFollowsTheHighestGroundAndStaysBelowTheWorldTop() {
        assertEquals(102.0, MountRules.flightCeiling(List.of(64, 70, 66), 320), 0.0);
        assertEquals(312.0, MountRules.flightCeiling(List.of(300), 320), 0.0);
        assertEquals(312.0, MountRules.flightCeiling(List.of(), 320), 0.0);
    }

    @Test
    public void ownershipKeepsWhatWasBoughtAndWhatIsPicked() {
        MountOwnership owned = MountOwnership.NONE.withBought(MountKind.HORSE).withBought(MountKind.BIKE);
        assertEquals(Set.of(MountKind.HORSE, MountKind.BIKE), owned.owned());
        assertEquals(Optional.of(MountKind.BIKE), owned.selected());
        MountOwnership horse = owned.withSelected(MountKind.HORSE);
        assertEquals(Optional.of(MountKind.HORSE), horse.selectedOrFirst());
        assertEquals(horse, horse.withSelected(MountKind.GRIFFIN)); // not owned: ignored
        assertEquals(horse, MountOwnership.fromJson(horse.toJson()));
        assertEquals(owned, MountOwnership.fromJson(owned.toJson()));
        // no pick: the best owned one comes
        MountOwnership unpicked = MountOwnership.fromJson("{\"owned\":[\"horse\",\"griffin\"]}");
        assertEquals(Optional.empty(), unpicked.selected());
        assertEquals(Optional.of(MountKind.GRIFFIN), unpicked.selectedOrFirst());
        // lenient
        assertEquals(MountOwnership.NONE, MountOwnership.fromJson(""));
        assertEquals(MountOwnership.NONE, MountOwnership.fromJson("not json"));
        assertEquals(MountOwnership.NONE, MountOwnership.fromJson("[1,2]"));
        assertEquals(Set.of(MountKind.HORSE), MountOwnership.fromJson("{\"owned\":[\"horse\",\"wyvern\",3],"
                + "\"selected\":\"bike\"}").owned());
        assertEquals(Optional.empty(), MountOwnership.fromJson("{\"owned\":[\"horse\"],\"selected\":\"bike\"}")
                .selected());
        assertTrue(MountOwnership.NONE.selectedOrFirst().isEmpty());
    }

    @Test
    public void textsAreInJapaneseAndEnglishAndInTheLangFiles() throws IOException {
        JsonObject docs = read(Paths.get("docs", "i18n", "mounts.json"));
        Set<String> expected = new TreeSet<>(MountRules.textKeys());
        expected.add("key.eternalrealm.mount");
        expected.add("key.eternalrealm.mount_choose");
        for (MountKind kind : MountKind.values()) {
            expected.add("entity.eternalrealm." + kind.entityPath());
        }
        assertEquals(expected, new TreeSet<>(docs.keySet()));
        Path lang = Paths.get("src", "main", "resources", "assets", "eternalrealm", "lang");
        JsonObject ja = read(lang.resolve("ja_jp.json"));
        JsonObject en = read(lang.resolve("en_us.json"));
        for (String key : docs.keySet()) {
            JsonObject texts = docs.getAsJsonObject(key);
            String japanese = texts.get("ja").getAsString();
            String english = texts.get("en").getAsString();
            assertFalse(key, japanese.isBlank() || english.isBlank());
            assertEquals(key + ": same arguments", arguments(japanese), arguments(english));
            assertEquals(key + " in ja_jp.json", japanese, ja.has(key) ? ja.get(key).getAsString() : null);
            assertEquals(key + " in en_us.json", english, en.has(key) ? en.get(key).getAsString() : null);
        }
        for (MountKind kind : MountKind.values()) {
            assertNotNull(docs.get(kind.translationKey()));
        }
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> arguments(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = Pattern.compile("%(\\d+\\$)?s").matcher(text);
        int plain = 0;
        while (matcher.find()) {
            found.add(matcher.group(1) == null ? "#" + (++plain) : matcher.group(1));
        }
        return found;
    }
}
