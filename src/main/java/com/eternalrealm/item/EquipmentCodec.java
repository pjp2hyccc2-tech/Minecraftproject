package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * JSON form of {@link EquipmentData}, stored by the platform layer as a string in the item stack's NBT.
 *
 * <pre>
 * {"v":1, "slot":"weapon", "weaponType":"spear", "tier":"frost", "rarity":"rare", "enhancement":3,
 *  "affixes":[{"kind":"str","value":7}, {"kind":"crit_chance","value":0.031}],
 *  "uniqueId":"…", "boundTo":"…"}                       (weaponType only for weapons; the ids are optional)
 * </pre>
 *
 * <p>Decoding is strict, like the character files: missing fields, wrong types, unknown ids, non-integral numbers,
 * malformed UUIDs and anything {@link EquipmentData} rejects are {@link CorruptDataException}; a version newer than
 * {@link #VERSION} is {@link IncompatibleDataException} (valid data from a newer build). Unknown fields are ignored so
 * a newer build can add optional fields without breaking older readers of the same version. The platform layer must
 * never overwrite a stack whose data it cannot decode.</p>
 */
public final class EquipmentCodec {
    /** Version written by this build; bump it (and migrate in {@link #fromJson}) when the meaning of a field changes. */
    public static final int VERSION = 1;
    /** Upper bound of an encoded piece; longer strings are rejected before parsing. */
    public static final int MAX_ENCODED_LENGTH = 4096;

    private EquipmentCodec() {
    }

    public static JsonObject toJson(EquipmentData data) {
        JsonObject json = new JsonObject();
        json.addProperty("v", VERSION);
        json.addProperty("slot", data.slot().id());
        if (data.weaponType() != null) {
            json.addProperty("weaponType", data.weaponType().id());
        }
        json.addProperty("tier", data.tier().id());
        json.addProperty("rarity", data.rarity().id());
        json.addProperty("enhancement", data.enhancement());
        JsonArray affixes = new JsonArray();
        for (Affix affix : data.affixes()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("kind", affix.kind().id());
            if (affix.kind().isPercent()) {
                entry.addProperty("value", affix.value());
            } else {
                entry.addProperty("value", Math.round(affix.value()));
            }
            affixes.add(entry);
        }
        json.add("affixes", affixes);
        if (data.uniqueId() != null) {
            json.addProperty("uniqueId", data.uniqueId().toString());
        }
        if (data.boundTo() != null) {
            json.addProperty("boundTo", data.boundTo().toString());
        }
        return json;
    }

    /** Compact JSON for the NBT string. */
    public static String encode(EquipmentData data) {
        return toJson(data).toString();
    }

    public static EquipmentData decode(String json) throws CorruptDataException, IncompatibleDataException {
        if (json == null || json.isEmpty()) {
            throw new CorruptDataException("No equipment data");
        }
        if (json.length() > MAX_ENCODED_LENGTH) {
            throw new CorruptDataException("Equipment data too long: " + json.length() + " characters");
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new CorruptDataException("Malformed equipment JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isJsonObject()) {
            throw new CorruptDataException("Equipment data is not a JSON object");
        }
        return fromJson(root.getAsJsonObject());
    }

    public static EquipmentData fromJson(JsonObject json) throws CorruptDataException, IncompatibleDataException {
        if (json == null) {
            throw new CorruptDataException("No equipment data");
        }
        int version = requireInt(json, "v");
        if (version > VERSION) {
            throw new IncompatibleDataException("Equipment data version " + version + " is newer than this build ("
                    + VERSION + "). Update the mod.");
        }
        if (version < 1) {
            throw new CorruptDataException("Invalid equipment data version " + version);
        }
        String slotId = requireString(json, "slot");
        EquipmentSlot slot = EquipmentSlot.byId(slotId)
                .orElseThrow(() -> new CorruptDataException("Unknown slot '" + slotId + "'"));
        WeaponType weaponType = null;
        if (slot == EquipmentSlot.WEAPON) {
            String typeId = requireString(json, "weaponType");
            weaponType = WeaponType.byId(typeId)
                    .orElseThrow(() -> new CorruptDataException("Unknown weapon type '" + typeId + "'"));
        } else if (isPresent(json, "weaponType")) {
            throw new CorruptDataException("Armor (" + slotId + ") must not have a weapon type");
        }
        String tierId = requireString(json, "tier");
        ItemTier tier = ItemTier.byId(tierId)
                .orElseThrow(() -> new CorruptDataException("Unknown tier '" + tierId + "'"));
        String rarityId = requireString(json, "rarity");
        ItemRarity rarity = ItemRarity.byId(rarityId)
                .orElseThrow(() -> new CorruptDataException("Unknown rarity '" + rarityId + "'"));
        int enhancement = requireInt(json, "enhancement");
        List<Affix> affixes = affixes(json);
        UUID uniqueId = optionalUuid(json, "uniqueId");
        UUID boundTo = optionalUuid(json, "boundTo");
        try {
            return new EquipmentData(slot, weaponType, tier, rarity, enhancement, affixes, uniqueId, boundTo);
        } catch (IllegalArgumentException e) {
            throw new CorruptDataException("Invalid equipment data: " + e.getMessage(), e);
        }
    }

    private static List<Affix> affixes(JsonObject json) throws CorruptDataException {
        JsonElement element = require(json, "affixes");
        if (!element.isJsonArray()) {
            throw new CorruptDataException("Field 'affixes' is not an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > ItemRarity.MAX_AFFIXES) {
            throw new CorruptDataException("Too many affixes: " + array.size());
        }
        List<Affix> affixes = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            if (entry == null || !entry.isJsonObject()) {
                throw new CorruptDataException("Affix " + i + " is not an object");
            }
            JsonObject object = entry.getAsJsonObject();
            String kindId = requireString(object, "kind");
            AffixKind kind = AffixKind.byId(kindId)
                    .orElseThrow(() -> new CorruptDataException("Unknown affix kind '" + kindId + "'"));
            double value = requireNumber(object, "value");
            try {
                affixes.add(new Affix(kind, value));
            } catch (IllegalArgumentException e) {
                throw new CorruptDataException("Invalid affix " + i + ": " + e.getMessage(), e);
            }
        }
        return affixes;
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private static boolean isPresent(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull();
    }

    private static JsonElement require(JsonObject json, String key) throws CorruptDataException {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            throw new CorruptDataException("Missing field '" + key + "'");
        }
        return element;
    }

    private static String requireString(JsonObject json, String key) throws CorruptDataException {
        JsonElement element = require(json, key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new CorruptDataException("Field '" + key + "' is not a string");
        }
        return element.getAsString();
    }

    private static double requireNumber(JsonObject json, String key) throws CorruptDataException {
        JsonElement element = require(json, key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new CorruptDataException("Field '" + key + "' is not a number");
        }
        double value;
        try {
            value = element.getAsDouble();
        } catch (NumberFormatException e) {
            throw new CorruptDataException("Field '" + key + "' is not a valid number: " + element, e);
        }
        if (!Double.isFinite(value)) {
            throw new CorruptDataException("Field '" + key + "' is not finite: " + element);
        }
        return value;
    }

    private static int requireInt(JsonObject json, String key) throws CorruptDataException {
        JsonElement element = require(json, key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new CorruptDataException("Field '" + key + "' is not a number");
        }
        try {
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new CorruptDataException("Field '" + key + "' is not an integer: " + element, e);
        }
    }

    /** A canonical UUID string ({@code 8-4-4-4-12} hex digits), or {@code null} if the field is absent. */
    private static UUID optionalUuid(JsonObject json, String key) throws CorruptDataException {
        if (!isPresent(json, key)) {
            return null;
        }
        String text = requireString(json, key);
        try {
            UUID uuid = UUID.fromString(text);
            if (text.length() != 36 || !uuid.toString().equals(text.toLowerCase(Locale.ROOT))) {
                throw new CorruptDataException("Field '" + key + "' is not a canonical UUID: " + text);
            }
            return uuid;
        } catch (IllegalArgumentException e) {
            throw new CorruptDataException("Field '" + key + "' is not a UUID: " + text, e);
        }
    }
}
