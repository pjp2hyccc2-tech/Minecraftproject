package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.economy.ItemInfo;
import com.eternalrealm.economy.Listing;
import com.eternalrealm.economy.MailItem;
import com.eternalrealm.economy.MailReason;
import com.eternalrealm.economy.Mailbox;
import com.eternalrealm.economy.MarketData;
import com.eternalrealm.season.SeasonId;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON form of a season's market ({@code seasons/<seasonId>/market.json}, schema 1; docs/modules/party.md):
 * <pre>
 * { "schema": 1, "seasonId": "s1",
 *   "listings":  [ { "id", "sellerId", "sellerName", "item": { "itemId", "count", "rarity" }, "itemData",
 *                    "price", "createdAtMillis", "expiresAtMillis" } ],
 *   "mailboxes": [ { "owner", "gold",
 *                    "items": [ { "id", "item": { ... }, "itemData", "reason", "receivedAtMillis" } ] } ] }
 * </pre>
 * Times are epoch milliseconds. Decoding is strict; a season id that does not match the requested file, invalid
 * values and duplicate ids are corruption.
 */
public final class MarketCodec {
    public static final int SCHEMA_VERSION = 1;

    private MarketCodec() {
    }

    public static String encode(MarketData data) {
        JsonArray listings = new JsonArray();
        for (Listing listing : data.listings()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", listing.id().toString());
            json.addProperty("sellerId", listing.sellerId().toString());
            json.addProperty("sellerName", listing.sellerName());
            json.add("item", encodeItem(listing.item()));
            json.addProperty("itemData", listing.itemData());
            json.addProperty("price", listing.price());
            json.addProperty("createdAtMillis", listing.createdAtMillis());
            json.addProperty("expiresAtMillis", listing.expiresAtMillis());
            listings.add(json);
        }
        JsonArray mailboxes = new JsonArray();
        for (Mailbox mailbox : data.mailboxes()) {
            JsonArray items = new JsonArray();
            for (MailItem item : mailbox.items()) {
                JsonObject json = new JsonObject();
                json.addProperty("id", item.id().toString());
                json.add("item", encodeItem(item.item()));
                json.addProperty("itemData", item.itemData());
                json.addProperty("reason", item.reason().id());
                json.addProperty("receivedAtMillis", item.receivedAtMillis());
                items.add(json);
            }
            JsonObject json = new JsonObject();
            json.addProperty("owner", mailbox.owner().toString());
            json.addProperty("gold", mailbox.gold());
            json.add("items", items);
            mailboxes.add(json);
        }
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("seasonId", data.season().value());
        root.add("listings", listings);
        root.add("mailboxes", mailboxes);
        return JsonSupport.toJson(root);
    }

    public static MarketData decode(String json, SeasonId expectedSeason)
            throws CorruptDataException, IncompatibleDataException {
        JsonObject root = JsonSupport.parseObject(json);
        JsonSupport.checkSchema(root, SCHEMA_VERSION, "market");
        try {
            SeasonId season = new SeasonId(JsonSupport.requireString(root, "seasonId"));
            if (!season.equals(expectedSeason)) {
                throw new CorruptDataException("Market file belongs to season " + season + ", expected " + expectedSeason);
            }
            List<Listing> listings = new ArrayList<>();
            for (JsonElement element : JsonSupport.requireArray(root, "listings")) {
                JsonObject listing = JsonSupport.asObject(element, "listing");
                listings.add(new Listing(JsonSupport.requireUuid(listing, "id"),
                        JsonSupport.requireUuid(listing, "sellerId"),
                        JsonSupport.requireString(listing, "sellerName"),
                        JsonSupport.requireString(listing, "itemData"),
                        decodeItem(JsonSupport.requireObject(listing, "item")),
                        JsonSupport.requireLong(listing, "price"),
                        JsonSupport.requireLong(listing, "createdAtMillis"),
                        JsonSupport.requireLong(listing, "expiresAtMillis")));
            }
            List<Mailbox> mailboxes = new ArrayList<>();
            for (JsonElement element : JsonSupport.requireArray(root, "mailboxes")) {
                JsonObject mailbox = JsonSupport.asObject(element, "mailbox");
                List<MailItem> items = new ArrayList<>();
                for (JsonElement itemElement : JsonSupport.requireArray(mailbox, "items")) {
                    JsonObject item = JsonSupport.asObject(itemElement, "mail item");
                    String reasonId = JsonSupport.requireString(item, "reason");
                    MailReason reason = MailReason.byId(reasonId)
                            .orElseThrow(() -> new CorruptDataException("Unknown mail reason '" + reasonId + "'"));
                    items.add(new MailItem(JsonSupport.requireUuid(item, "id"),
                            JsonSupport.requireString(item, "itemData"),
                            decodeItem(JsonSupport.requireObject(item, "item")),
                            reason,
                            JsonSupport.requireLong(item, "receivedAtMillis")));
                }
                mailboxes.add(new Mailbox(JsonSupport.requireUuid(mailbox, "owner"),
                        JsonSupport.requireLong(mailbox, "gold"), items));
            }
            return new MarketData(season, listings, mailboxes);
        } catch (IllegalArgumentException e) {
            throw new CorruptDataException("Invalid market data: " + e.getMessage(), e);
        }
    }

    private static JsonObject encodeItem(ItemInfo item) {
        JsonObject json = new JsonObject();
        json.addProperty("itemId", item.itemId());
        json.addProperty("count", item.count());
        json.addProperty("rarity", item.rarity());
        return json;
    }

    private static ItemInfo decodeItem(JsonObject json) throws CorruptDataException {
        return new ItemInfo(JsonSupport.requireString(json, "itemId"), JsonSupport.requireInt(json, "count"),
                JsonSupport.requireString(json, "rarity"));
    }
}
