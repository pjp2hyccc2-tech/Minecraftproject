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
    public void buyingNeedsTheLevelTheGoldAndRoomAndOneArcbikeAtMost() {
        MountOwnership none = MountOwnership.NONE;
        assertEquals(MountRules.Purchase.LEVEL_TOO_LOW, MountRules.purchase(MountKind.HORSE, 9, 1_000_000L, none));
        assertEquals(MountRules.Purchase.OK, MountRules.purchase(MountKind.HORSE, 10, MountKind.HORSE.price(), none));
        assertEquals(MountRules.Purchase.NOT_ENOUGH_GOLD,
                MountRules.purchase(MountKind.HORSE, 10, MountKind.HORSE.price() - 1, none));
        assertEquals(MountRules.Purchase.LEVEL_TOO_LOW, MountRules.purchase(MountKind.GRIFFIN, 29, 1_000_000L, none));
        assertEquals(MountRules.Purchase.OK, MountRules.purchase(MountKind.GRIFFIN, 30, 1_000_000L, none));
        assertEquals(MountRules.Purchase.LEVEL_TOO_LOW, MountRules.purchase(MountKind.BIKE, 49, 1_000_000L, none));
        assertEquals(MountRules.Purchase.OK, MountRules.purchase(MountKind.BIKE, 50, 1_000_000L, none));
        // horses and griffins differ one from another: keep several; the arcbike is one machine
        MountOwnership horse = none.withAdded(standard(MountKind.HORSE));
        assertEquals(MountRules.Purchase.OK, MountRules.purchase(MountKind.HORSE, 60, 1_000_000L, horse));
        MountOwnership bike = none.withAdded(standard(MountKind.BIKE));
        assertEquals(MountRules.Purchase.ALREADY_OWNED, MountRules.purchase(MountKind.BIKE, 60, 1_000_000L, bike));
        MountOwnership full = none;
        for (int i = 0; i < MountRules.STABLE_CAPACITY; i++) {
            full = full.withAdded(standard(MountKind.HORSE));
        }
        assertEquals(MountRules.STABLE_CAPACITY, full.size());
        assertEquals(MountRules.Purchase.STABLE_FULL, MountRules.purchase(MountKind.GRIFFIN, 60, 1_000_000L, full));
        assertEquals(MountKind.HORSE.price() * MountRules.REROLL_PERCENT / 100, MountRules.rerollCost(MountKind.HORSE));
    }

    @Test
    public void deedsAreTakenInByPlayersOfTheLevelWithRoom() {
        MountIndividual griffin = standard(MountKind.GRIFFIN);
        assertEquals(MountRules.Redeem.OK, MountRules.redeem(griffin, 30, MountOwnership.NONE));
        assertEquals(MountRules.Redeem.LEVEL_TOO_LOW, MountRules.redeem(griffin, 29, MountOwnership.NONE));
        assertEquals(MountRules.Redeem.ALREADY_KEPT, MountRules.redeem(griffin, 60,
                MountOwnership.NONE.withAdded(griffin)));
        assertEquals(MountRules.Redeem.NOT_TRADEABLE, MountRules.redeem(standard(MountKind.BIKE), 60,
                MountOwnership.NONE));
        MountOwnership full = MountOwnership.NONE;
        for (int i = 0; i < MountRules.STABLE_CAPACITY; i++) {
            full = full.withAdded(standard(MountKind.HORSE));
        }
        assertEquals(MountRules.Redeem.STABLE_FULL, MountRules.redeem(griffin, 60, full));
        assertTrue(MountRules.tradeable(MountKind.HORSE) && MountRules.tradeable(MountKind.GRIFFIN));
        assertFalse(MountRules.tradeable(MountKind.BIKE));
    }

    private static MountIndividual standard(MountKind kind) {
        return MountIndividual.standard(java.util.UUID.randomUUID(), kind);
    }

    @Test
    public void aCallNeedsAMountAndASafePlace() {
        MountOwnership horse = MountOwnership.NONE.withAdded(standard(MountKind.HORSE));
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
        MountIndividual horse = standard(MountKind.HORSE);
        MountIndividual bike = standard(MountKind.BIKE);
        MountOwnership owned = MountOwnership.NONE.withAdded(horse).withAdded(bike);
        assertEquals(Set.of(MountKind.HORSE, MountKind.BIKE), owned.kinds());
        assertEquals(Optional.of(bike), owned.selected());
        MountOwnership picked = owned.withSelected(horse.id());
        assertEquals(Optional.of(horse), picked.selectedOrFirst());
        assertEquals(picked, picked.withSelected(java.util.UUID.randomUUID())); // not kept: ignored
        assertEquals(picked, MountOwnership.fromJson(picked.toJson()));
        assertEquals(owned, MountOwnership.fromJson(owned.toJson()));
        // made into a deed: gone, the pick falls back to the newest kept
        MountOwnership without = picked.without(horse.id());
        assertEquals(List.of(bike), without.mounts());
        assertEquals(Optional.empty(), without.selected());
        assertEquals(Optional.of(bike), without.selectedOrFirst());
        // several of one kind
        MountOwnership two = owned.withAdded(standard(MountKind.HORSE));
        assertEquals(2, two.count(MountKind.HORSE));
        assertEquals(Optional.of(horse), two.first(MountKind.HORSE));
        // lenient
        assertEquals(MountOwnership.NONE, MountOwnership.fromJson(""));
        assertEquals(MountOwnership.NONE, MountOwnership.fromJson("not json"));
        assertEquals(MountOwnership.NONE, MountOwnership.fromJson("[1,2]"));
        assertEquals(1, MountOwnership.fromJson("{\"v\":2,\"mounts\":[" + horse.toJson()
                + ",{\"kind\":\"wyvern\",\"id\":\"" + java.util.UUID.randomUUID() + "\"},{\"kind\":\"horse\"},3]}")
                .size());
        assertTrue(MountOwnership.NONE.selectedOrFirst().isEmpty());
    }

    @Test
    public void mountsFromBeforeIndividualityBecomeStandardOnesOfTheClassicLook() {
        java.util.UUID owner = java.util.UUID.randomUUID();
        MountOwnership old = MountOwnership.fromJson("{\"owned\":[\"horse\",\"griffin\",\"wyvern\",3],"
                + "\"selected\":\"griffin\"}", owner);
        assertEquals(Set.of(MountKind.HORSE, MountKind.GRIFFIN), old.kinds());
        MountIndividual griffin = old.selected().orElseThrow();
        assertEquals(MountKind.GRIFFIN, griffin.kind());
        assertEquals(MountLooks.classic(MountKind.GRIFFIN), griffin.look());
        assertEquals(MountTraits.STANDARD, griffin.traits());
        assertEquals(MountOwnership.legacyId(owner, MountKind.GRIFFIN), griffin.id());
        // the same ids on every load, different ones for another player
        assertEquals(old, MountOwnership.fromJson("{\"owned\":[\"horse\",\"griffin\"],\"selected\":\"griffin\"}",
                owner));
        assertFalse(MountOwnership.legacyId(java.util.UUID.randomUUID(), MountKind.GRIFFIN).equals(griffin.id()));
        // no pick: the newest kept comes; a pick of a kind not owned is dropped
        MountOwnership unpicked = MountOwnership.fromJson("{\"owned\":[\"horse\",\"griffin\"]}", owner);
        assertEquals(MountKind.GRIFFIN, unpicked.selectedOrFirst().orElseThrow().kind());
        assertEquals(Optional.empty(), MountOwnership.fromJson("{\"owned\":[\"horse\"],\"selected\":\"bike\"}",
                owner).selected());
        // saved again in the new form, it stays the same
        assertEquals(old, MountOwnership.fromJson(old.toJson(), java.util.UUID.randomUUID()));
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
