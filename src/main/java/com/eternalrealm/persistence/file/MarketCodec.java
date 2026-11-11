package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.economy.Bid;
import com.eternalrealm.economy.ItemInfo;
import com.eternalrealm.economy.Lot;
import com.eternalrealm.economy.LotState;
import com.eternalrealm.economy.PriceHistory;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON form of a season's market ({@code seasons/<seasonId>/market.json}, schema 2; docs/modules/party.md and
 * docs/modules/market.md). Schema 2 added the items' board data ({@code category, level, gold, group, keywords}), the
 * auction hall's {@code lots}, the price history {@code prices} and {@code savedAtMillis}; schema 1 files load with
 * defaults for them.
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
    public static final int SCHEMA_VERSION = 2;

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
        JsonArray lots = new JsonArray();
        for (Lot lot : data.lots()) {
            lots.add(encodeLot(lot));
        }
        JsonObject prices = new JsonObject();
        data.prices().forEach((group, sales) -> {
            JsonArray array = new JsonArray();
            for (PriceHistory.Sale sale : sales) {
                JsonObject json = new JsonObject();
                json.addProperty("unitPrice", sale.unitPrice());
                json.addProperty("count", sale.count());
                json.addProperty("atMillis", sale.atMillis());
                json.addProperty("auction", sale.auction());
                array.add(json);
            }
            prices.add(group, array);
        });
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("seasonId", data.season().value());
        root.add("listings", listings);
        root.add("mailboxes", mailboxes);
        root.add("lots", lots);
        root.add("prices", prices);
        root.addProperty("savedAtMillis", data.savedAtMillis());
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
            List<Lot> lots = new ArrayList<>();
            if (root.has("lots")) {
                for (JsonElement element : JsonSupport.requireArray(root, "lots")) {
                    lots.add(decodeLot(JsonSupport.asObject(element, "lot")));
                }
            }
            Map<String, List<PriceHistory.Sale>> prices = new LinkedHashMap<>();
            if (root.has("prices")) {
                for (Map.Entry<String, JsonElement> entry : JsonSupport.requireObject(root, "prices").entrySet()) {
                    List<PriceHistory.Sale> sales = new ArrayList<>();
                    for (JsonElement element : entry.getValue().getAsJsonArray()) {
                        JsonObject sale = JsonSupport.asObject(element, "sale");
                        sales.add(new PriceHistory.Sale(JsonSupport.requireLong(sale, "unitPrice"),
                                JsonSupport.requireInt(sale, "count"), JsonSupport.requireLong(sale, "atMillis"),
                                sale.has("auction") && sale.get("auction").getAsBoolean()));
                    }
                    prices.put(entry.getKey(), sales);
                }
            }
            long savedAt = root.has("savedAtMillis") ? JsonSupport.requireLong(root, "savedAtMillis") : 0L;
            return new MarketData(season, listings, mailboxes, lots, prices, savedAt);
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            throw new CorruptDataException("Invalid market data: " + e.getMessage(), e);
        }
    }

    private static JsonObject encodeItem(ItemInfo item) {
        JsonObject json = new JsonObject();
        json.addProperty("itemId", item.itemId());
        json.addProperty("count", item.count());
        json.addProperty("rarity", item.rarity());
        json.addProperty("category", item.category());
        json.addProperty("level", item.level());
        json.addProperty("gold", item.gold());
        json.addProperty("group", item.group());
        json.addProperty("keywords", item.keywords());
        return json;
    }

    private static ItemInfo decodeItem(JsonObject json) throws CorruptDataException {
        ItemInfo basic = new ItemInfo(JsonSupport.requireString(json, "itemId"), JsonSupport.requireInt(json, "count"),
                JsonSupport.requireString(json, "rarity"));
        if (!json.has("category")) {
            return basic; // schema 1
        }
        return new ItemInfo(basic.itemId(), basic.count(), basic.rarity(), JsonSupport.requireString(json, "category"),
                JsonSupport.requireInt(json, "level"), json.has("gold") && json.get("gold").getAsBoolean(),
                JsonSupport.requireString(json, "group"), JsonSupport.requireString(json, "keywords"));
    }

    private static JsonObject encodeLot(Lot lot) {
        JsonObject json = new JsonObject();
        json.addProperty("id", lot.id().toString());
        json.addProperty("seller", lot.seller().toString());
        json.addProperty("sellerName", lot.sellerName());
        json.addProperty("itemData", lot.itemData());
        json.add("item", encodeItem(lot.item()));
        json.addProperty("startPrice", lot.startPrice());
        json.addProperty("buyoutPrice", lot.buyoutPrice());
        json.addProperty("durationMinutes", lot.durationMinutes());
        json.addProperty("state", lot.state().id());
        json.addProperty("createdAtMillis", lot.createdAtMillis());
        json.addProperty("queuedAtMillis", lot.queuedAtMillis());
        json.addProperty("startedAtMillis", lot.startedAtMillis());
        json.addProperty("endsAtMillis", lot.endsAtMillis());
        json.addProperty("latestEndMillis", lot.latestEndMillis());
        JsonArray bids = new JsonArray();
        for (Bid bid : lot.bids()) {
            JsonObject b = new JsonObject();
            b.addProperty("bidder", bid.bidder().toString());
            b.addProperty("bidderName", bid.bidderName());
            b.addProperty("amount", bid.amount());
            b.addProperty("atMillis", bid.atMillis());
            bids.add(b);
        }
        json.add("bids", bids);
        json.addProperty("bidCount", lot.bidCount());
        json.addProperty("feePaid", lot.feePaid());
        json.addProperty("finishedAtMillis", lot.finishedAtMillis());
        return json;
    }

    private static Lot decodeLot(JsonObject json) throws CorruptDataException {
        String stateId = JsonSupport.requireString(json, "state");
        LotState state = LotState.byId(stateId)
                .orElseThrow(() -> new CorruptDataException("Unknown lot state '" + stateId + "'"));
        List<Bid> bids = new ArrayList<>();
        for (JsonElement element : JsonSupport.requireArray(json, "bids")) {
            JsonObject bid = JsonSupport.asObject(element, "bid");
            bids.add(new Bid(JsonSupport.requireUuid(bid, "bidder"), JsonSupport.requireString(bid, "bidderName"),
                    JsonSupport.requireLong(bid, "amount"), JsonSupport.requireLong(bid, "atMillis")));
        }
        return new Lot(JsonSupport.requireUuid(json, "id"), JsonSupport.requireUuid(json, "seller"),
                JsonSupport.requireString(json, "sellerName"), JsonSupport.requireString(json, "itemData"),
                decodeItem(JsonSupport.requireObject(json, "item")), JsonSupport.requireLong(json, "startPrice"),
                JsonSupport.requireLong(json, "buyoutPrice"), JsonSupport.requireInt(json, "durationMinutes"), state,
                JsonSupport.requireLong(json, "createdAtMillis"), JsonSupport.requireLong(json, "queuedAtMillis"),
                JsonSupport.requireLong(json, "startedAtMillis"), JsonSupport.requireLong(json, "endsAtMillis"),
                JsonSupport.requireLong(json, "latestEndMillis"), bids, JsonSupport.requireInt(json, "bidCount"),
                JsonSupport.requireLong(json, "feePaid"), JsonSupport.requireLong(json, "finishedAtMillis"));
    }
}
