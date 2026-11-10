package com.eternalrealm.persistence.file;

import com.eternalrealm.character.CharacterRecord;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.player.AccountRecord;
import com.eternalrealm.season.SeasonId;
import org.junit.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JsonCodecsTest {
    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final SeasonId SEASON = SeasonId.of("s1");
    private static final Instant CREATED = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-11-08T12:34:56.789Z");

    private static CharacterRecord sample() {
        StatBlock stats = StatBlock.ZERO.with(StatType.STR, 4).with(StatType.LUK, 2);
        return new CharacterRecord(PLAYER, SEASON, 7, 123, 4567, stats, CREATED, UPDATED);
    }

    private static String validJson() {
        return JsonCodecs.encodeCharacter(sample());
    }

    @Test
    public void characterRoundTrip() throws Exception {
        String json = validJson();
        assertTrue(json.contains("\"schema\": 3"));
        assertEquals(sample(), JsonCodecs.decodeCharacter(json, PLAYER, SEASON));
    }

    @Test
    public void schemaTwoFieldsRoundTrip() throws Exception {
        CharacterRecord full = new CharacterRecord(PLAYER, SEASON, 12, 5, 999, StatBlock.ZERO, CREATED, UPDATED,
                "mage", java.util.Map.of("mage_fireball", 2, "mage_ice_lance", 1), 123_456L,
                java.util.Map.of("quests", "{\"active\":[{\"id\":\"q1\",\"progress\":[3]}]}",
                        "raw", "not json at all"));
        String json = JsonCodecs.encodeCharacter(full);
        assertTrue(json.contains("\"active\""));
        assertEquals(full, JsonCodecs.decodeCharacter(json, PLAYER, SEASON));
    }

    @Test
    public void theSkillLoadoutRoundTripsAndSchemaTwoFilesGetTheDefault() throws Exception {
        CharacterRecord full = new CharacterRecord(PLAYER, SEASON, 20, 5, 999, StatBlock.ZERO, CREATED, UPDATED,
                "mage", java.util.Map.of("mage_fireball", 4, "mage_explosion", 1), 10L, java.util.Map.of(),
                java.util.List.of("mage_explosion", "", "mage_fireball", "", "", ""));
        String json = JsonCodecs.encodeCharacter(full);
        assertTrue(json.contains("\"skillLoadout\""));
        assertEquals(full, JsonCodecs.decodeCharacter(json, PLAYER, SEASON));
        String schemaTwo = json.replace("\"schema\": 3", "\"schema\": 2");
        assertTrue(JsonCodecs.decodeCharacter(schemaTwo, PLAYER, SEASON).skillLoadout().isEmpty());
        assertCorrupt(json.replace("\"mage_explosion\",\n    \"\"", "\"Bad Id\",\n    \"\""));
        assertCorrupt(json.replaceFirst("\"skillLoadout\": \\[", "\"skillLoadout\": {\"x\": ["));
    }

    @Test
    public void schemaOneFilesAreMigratedWithDefaults() throws Exception {
        String v1 = "{\"schema\":1,\"playerId\":\"" + PLAYER + "\",\"seasonId\":\"s1\",\"level\":7,"
                + "\"experience\":123,\"totalExperience\":4567,\"allocatedStats\":{\"str\":4,\"vit\":0,\"agi\":0,"
                + "\"int\":0,\"dex\":0,\"luk\":2},\"createdAt\":\"2026-11-01T10:00:00Z\","
                + "\"updatedAt\":\"2026-11-08T12:34:56.789Z\"}";
        CharacterRecord migrated = JsonCodecs.decodeCharacter(v1, PLAYER, SEASON);
        assertEquals(sample(), migrated);
        assertEquals(CharacterRecord.NO_JOB, migrated.jobId());
        assertEquals(0L, migrated.gold());
    }

    @Test
    public void invalidSchemaTwoFieldsAreCorrupt() {
        assertCorrupt(validJson().replace("\"gold\": 0", "\"gold\": -5"));
        assertCorrupt(validJson().replace("\"jobId\": \"none\"", "\"jobId\": \"Not A Job\""));
        assertCorrupt(validJson().replace("\"skills\": {}", "\"skills\": {\"x\": \"high\"}"));
        assertCorrupt(validJson().replace("\"extensions\": {}", "\"extensions\": {\"q\": null}"));
        assertCorrupt(validJson().replace("\"skills\": {},", ""));
    }

    @Test
    public void accountRoundTrip() throws Exception {
        AccountRecord account = new AccountRecord(PLAYER, "Steve", CREATED, UPDATED, 12);
        assertEquals(account, JsonCodecs.decodeAccount(JsonCodecs.encodeAccount(account), PLAYER));
    }

    @Test
    public void malformedOrTruncatedJsonIsCorrupt() {
        String json = validJson();
        assertCorrupt(json.substring(0, json.length() / 2));
        assertCorrupt("");
        assertCorrupt("[]");
        assertCorrupt("\"text\"");
        assertCorrupt("{not json");
    }

    @Test
    public void missingOrMistypedFieldsAreCorrupt() {
        assertCorrupt(validJson().replace("\"level\": 7,", ""));
        assertCorrupt(validJson().replace("\"level\": 7", "\"level\": \"7\""));
        assertCorrupt(validJson().replace("\"level\": 7", "\"level\": 7.5"));
        assertCorrupt(validJson().replace("\"level\": 7", "\"level\": 0"));
        assertCorrupt(validJson().replace("\"experience\": 123", "\"experience\": -1"));
        assertCorrupt(validJson().replace("\"createdAt\": \"2026-11-01T10:00:00Z\"", "\"createdAt\": \"yesterday\""));
    }

    @Test
    public void statsMustBeCompleteAndKnown() {
        assertCorrupt(validJson().replace("\"luk\": 2", "\"luck\": 2"));
        assertCorrupt(validJson().replace(",\n    \"luk\": 2", ""));
        assertCorrupt(validJson().replace("\"str\": 4", "\"str\": 99999"));
    }

    @Test
    public void identityMustMatchTheRequestedFile() {
        UUID other = UUID.fromString("99999999-2222-3333-4444-555555555555");
        assertThrows(CorruptDataException.class, () -> JsonCodecs.decodeCharacter(validJson(), other, SEASON));
        assertThrows(CorruptDataException.class, () -> JsonCodecs.decodeCharacter(validJson(), PLAYER, SeasonId.of("s2")));
    }

    @Test
    public void newerSchemaIsIncompatibleNotCorrupt() {
        String newer = validJson().replace("\"schema\": 3", "\"schema\": 4");
        assertThrows(IncompatibleDataException.class, () -> JsonCodecs.decodeCharacter(newer, PLAYER, SEASON));
        assertCorrupt(validJson().replace("\"schema\": 3", "\"schema\": 0"));
    }

    private static void assertCorrupt(String json) {
        assertThrows(json, CorruptDataException.class, () -> JsonCodecs.decodeCharacter(json, PLAYER, SEASON));
    }
}
