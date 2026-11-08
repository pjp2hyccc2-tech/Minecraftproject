package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EquipmentCodecTest {
    private static EquipmentData sample() {
        return new EquipmentData(EquipmentSlot.WEAPON, WeaponType.SPEAR, ItemTier.FROST, ItemRarity.RARE, 3,
                List.of(new Affix(AffixKind.STR, 7), new Affix(AffixKind.CRIT_CHANCE, 0.031)),
                UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
                UUID.fromString("00000000-0000-4000-8000-000000000001"));
    }

    @Test
    public void writesTheDocumentedFormat() {
        JsonObject json = EquipmentCodec.toJson(sample());
        assertEquals(1, json.get("v").getAsInt());
        assertEquals("weapon", json.get("slot").getAsString());
        assertEquals("spear", json.get("weaponType").getAsString());
        assertEquals("frost", json.get("tier").getAsString());
        assertEquals("rare", json.get("rarity").getAsString());
        assertEquals(3, json.get("enhancement").getAsInt());
        JsonArray affixes = json.getAsJsonArray("affixes");
        assertEquals("str", affixes.get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals("7", affixes.get(0).getAsJsonObject().get("value").toString());
        assertEquals("0.031", affixes.get(1).getAsJsonObject().get("value").toString());
        assertEquals("123e4567-e89b-42d3-a456-426614174000", json.get("uniqueId").getAsString());
        assertTrue(json.has("boundTo"));

        JsonObject armor = EquipmentCodec.toJson(EquipmentDataTest.armor(EquipmentSlot.HEAD, ItemTier.IRON,
                ItemRarity.COMMON));
        assertFalse(armor.has("weaponType"));
        assertFalse(armor.has("uniqueId"));
        assertFalse(armor.has("boundTo"));
        assertEquals(0, armor.getAsJsonArray("affixes").size());
    }

    @Test
    public void roundTripsEveryGeneratedPieceExactly() throws DataAccessException {
        SplittableRandom random = new SplittableRandom(2024);
        for (EquipmentBase base : EquipmentBase.all()) {
            for (ItemRarity rarity : ItemRarity.values()) {
                EquipmentData data = EquipmentGenerator.roll(base, rarity, random)
                        .withEnhancement(random.nextInt(11))
                        .ensureUniqueId(() -> new UUID(random.nextLong(), random.nextLong()));
                String encoded = EquipmentCodec.encode(data);
                assertTrue(encoded.length() < EquipmentCodec.MAX_ENCODED_LENGTH);
                assertEquals(data, EquipmentCodec.decode(encoded));
                assertEquals(data, EquipmentCodec.fromJson(EquipmentCodec.toJson(data)));
            }
        }
        EquipmentData sample = sample();
        assertEquals(sample, EquipmentCodec.decode(EquipmentCodec.encode(sample)));
    }

    @Test
    public void toleratesUnknownFieldsAndExplicitNulls() throws DataAccessException {
        JsonObject json = EquipmentCodec.toJson(sample());
        json.addProperty("futureField", "whatever");
        json.getAsJsonArray("affixes").get(0).getAsJsonObject().addProperty("locked", true);
        assertEquals(sample(), EquipmentCodec.fromJson(json));

        JsonObject armor = EquipmentCodec.toJson(EquipmentDataTest.armor(EquipmentSlot.FEET, ItemTier.IRON,
                ItemRarity.COMMON));
        armor.add("weaponType", JsonNull.INSTANCE);
        armor.add("uniqueId", JsonNull.INSTANCE);
        armor.add("boundTo", JsonNull.INSTANCE);
        EquipmentData decoded = EquipmentCodec.fromJson(armor);
        assertEquals(EquipmentSlot.FEET, decoded.slot());
        assertEquals(null, decoded.uniqueId());
        // a flat value written as 7.0 is still on the grid
        JsonObject withDouble = EquipmentCodec.toJson(sample());
        withDouble.getAsJsonArray("affixes").get(0).getAsJsonObject().addProperty("value", 7.0);
        assertEquals(sample(), EquipmentCodec.fromJson(withDouble));
    }

    @Test
    public void newerVersionsAreIncompatibleNotCorrupt() {
        JsonObject json = EquipmentCodec.toJson(sample());
        json.addProperty("v", EquipmentCodec.VERSION + 1);
        assertThrows(IncompatibleDataException.class, () -> EquipmentCodec.fromJson(json));
        corrupt(j -> j.addProperty("v", 0));
        corrupt(j -> j.addProperty("v", 1.5));
        corrupt(j -> j.addProperty("v", "1"));
        corrupt(j -> j.remove("v"));
    }

    @Test
    public void rejectsMissingOrMistypedFields() {
        for (String field : List.of("slot", "weaponType", "tier", "rarity", "enhancement", "affixes")) {
            corrupt(j -> j.remove(field));
            corrupt(j -> j.add(field, JsonNull.INSTANCE));
        }
        corrupt(j -> j.addProperty("slot", 1));
        corrupt(j -> j.addProperty("tier", 4));
        corrupt(j -> j.addProperty("enhancement", "3"));
        corrupt(j -> j.addProperty("enhancement", 3.5));
        corrupt(j -> j.addProperty("enhancement", 1.0e20));
        corrupt(j -> j.add("affixes", new JsonObject()));
        corrupt(j -> j.addProperty("uniqueId", 42));
    }

    @Test
    public void rejectsUnknownIds() {
        corrupt(j -> j.addProperty("slot", "hand"));
        corrupt(j -> j.addProperty("weaponType", "axe"));
        corrupt(j -> j.addProperty("tier", "gold"));
        corrupt(j -> j.addProperty("rarity", "mythic"));
        corrupt(j -> affix(j, 0).addProperty("kind", "attack_speed"));
    }

    @Test
    public void rejectsInvalidAffixes() {
        corrupt(j -> affix(j, 0).addProperty("value", 7.5));
        corrupt(j -> affix(j, 0).addProperty("value", -7));
        corrupt(j -> affix(j, 0).addProperty("value", "7"));
        corrupt(j -> affix(j, 0).remove("value"));
        corrupt(j -> affix(j, 1).addProperty("value", 0.0315));
        corrupt(j -> affix(j, 1).addProperty("kind", "str"));
        corrupt(j -> affix(j, 1).addProperty("kind", "defense"));
        corrupt(j -> affix(j, 0).addProperty("value", 999));
        corrupt(j -> j.getAsJsonArray("affixes").remove(1));
        corrupt(j -> j.getAsJsonArray("affixes").add("str"));
        corrupt(j -> {
            JsonArray many = new JsonArray();
            for (int i = 0; i < 5; i++) {
                many.add(affix(j, 0));
            }
            j.add("affixes", many);
        });
    }

    @Test
    public void rejectsInconsistentPieces() {
        corrupt(j -> j.addProperty("enhancement", 11));
        corrupt(j -> j.addProperty("enhancement", -1));
        corrupt(j -> j.addProperty("rarity", "epic"));
        corrupt(j -> j.addProperty("slot", "chest"));
    }

    @Test
    public void rejectsMalformedUuids() {
        corrupt(j -> j.addProperty("uniqueId", "not-a-uuid"));
        corrupt(j -> j.addProperty("uniqueId", "1-2-3-4-5"));
        corrupt(j -> j.addProperty("boundTo", "123e4567-e89b-42d3-a456-4266141740000"));
    }

    @Test
    public void armorMustNotCarryAWeaponType() {
        JsonObject armor = EquipmentCodec.toJson(EquipmentDataTest.armor(EquipmentSlot.HEAD, ItemTier.IRON,
                ItemRarity.COMMON));
        armor.addProperty("weaponType", "bow");
        assertThrows(CorruptDataException.class, () -> EquipmentCodec.fromJson(armor));
    }

    @Test
    public void rejectsBrokenStrings() {
        for (String text : new String[]{null, "", "{", "[]", "\"text\"", "42", "null", "{\"v\":1}"}) {
            assertThrows(String.valueOf(text), CorruptDataException.class, () -> EquipmentCodec.decode(text));
        }
        assertThrows(CorruptDataException.class, () -> EquipmentCodec.fromJson(null));
        String huge = "{\"pad\":\"" + "x".repeat(EquipmentCodec.MAX_ENCODED_LENGTH) + "\"}";
        assertThrows(CorruptDataException.class, () -> EquipmentCodec.decode(huge));
    }

    private static JsonObject affix(JsonObject json, int index) {
        return json.getAsJsonArray("affixes").get(index).getAsJsonObject();
    }

    private static void corrupt(Consumer<JsonObject> change) {
        JsonObject json = JsonParser.parseString(EquipmentCodec.encode(sample())).getAsJsonObject();
        change.accept(json);
        assertThrows(json.toString(), CorruptDataException.class, () -> EquipmentCodec.fromJson(json));
    }
}
