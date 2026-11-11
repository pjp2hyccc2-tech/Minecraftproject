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
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MarketCodecTest {
    static final SeasonId S1 = SeasonId.of("s1");
    static final UUID LISTING_1 = UUID.fromString("11111111-0000-4000-8000-000000000001");
    static final UUID LISTING_2 = UUID.fromString("22222222-0000-4000-8000-000000000002");
    static final UUID MAIL_1 = UUID.fromString("33333333-0000-4000-8000-000000000003");
    static final UUID SELLER = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");
    static final UUID BUYER = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b");
    /** Serialized items contain quotes, backslashes, newlines and Japanese text; all must survive. */
    static final String TRICKY_ITEM = "{id:\"eternalrealm:iron_longsword\",tag:{display:{Name:'{\"text\":\"名剣\\\\n\"}'}}}\n";

    static MarketData sample() {
        Listing sword = new Listing(LISTING_1, SELLER, "Alex", TRICKY_ITEM,
                new ItemInfo("eternalrealm:iron_longsword", 1, "rare"), 12_345, 1_794_096_000_000L, 1_794_268_800_000L);
        Listing diamonds = new Listing(LISTING_2, BUYER, "Steve", "{id:\"minecraft:diamond\",Count:16b}",
                new ItemInfo("minecraft:diamond", 16, "common"), 900, 1_794_096_000_001L, 1_794_268_800_001L);
        MailItem returned = new MailItem(MAIL_1, "{id:\"minecraft:bread\"}", new ItemInfo("minecraft:bread", 3, "common"),
                MailReason.AUCTION_EXPIRED, 1_794_000_000_000L);
        ItemInfo relic = new ItemInfo("eternalrealm:abyss_greatsword", 1, "legendary", "weapon", 55, true,
                "design:king_of_end_greatsword:legendary", "終焉の大剣 king of end greatsword");
        Lot running = new Lot(UUID.fromString("44444444-0000-4000-8000-000000000004"), SELLER, "Alex", TRICKY_ITEM,
                relic, 100_000, 500_000, 60, LotState.RUNNING, 1_794_096_000_000L, 1_794_096_100_000L,
                1_794_096_100_000L, 1_794_099_700_000L, 1_794_100_600_000L,
                List.of(new Bid(BUYER, "Steve", 120_000, 1_794_096_200_000L)), 3, 1_000, 0L);
        return new MarketData(S1, List.of(sword, diamonds),
                List.of(new Mailbox(SELLER, 950, List.of(returned)), new Mailbox(BUYER, 7, List.of())),
                List.of(running), java.util.Map.of("item:minecraft:diamond",
                        List.of(new PriceHistory.Sale(55, 16, 1_794_000_000_000L, false))), 1_794_096_300_000L);
    }

    private static String json() {
        return MarketCodec.encode(sample());
    }

    @Test
    public void roundTripKeepsEverything() throws Exception {
        String json = json();
        assertTrue(json.contains("\"schema\": 2"));
        assertTrue(json.contains("\"seasonId\": \"s1\""));
        assertEquals(sample(), MarketCodec.decode(json, S1));
        assertEquals(MarketData.empty(S1), MarketCodec.decode(MarketCodec.encode(MarketData.empty(S1)), S1));
    }

    /** A market saved before the auction hall (schema 1) still loads: items get default board data. */
    @Test
    public void schemaOneStillLoads() throws Exception {
        String v1 = "{\"schema\": 1, \"seasonId\": \"s1\", \"listings\": [{\"id\": \"" + LISTING_1
                + "\", \"sellerId\": \"" + SELLER + "\", \"sellerName\": \"Alex\", \"item\": {\"itemId\": "
                + "\"minecraft:diamond\", \"count\": 2, \"rarity\": \"common\"}, \"itemData\": \"{}\", "
                + "\"price\": 10, \"createdAtMillis\": 1, \"expiresAtMillis\": 2}], \"mailboxes\": []}";
        MarketData data = MarketCodec.decode(v1, S1);
        ItemInfo item = data.listings().get(0).item();
        assertEquals("other", item.category());
        assertEquals("item:minecraft:diamond", item.group());
        assertTrue(data.lots().isEmpty());
        assertTrue(data.prices().isEmpty());
    }

    @Test
    public void aFileOfAnotherSeasonIsCorrupt() {
        assertThrows(CorruptDataException.class, () -> MarketCodec.decode(json(), SeasonId.of("s2")));
    }

    @Test
    public void newerSchemaIsIncompatibleNotCorrupt() {
        assertThrows(IncompatibleDataException.class,
                () -> MarketCodec.decode(json().replace("\"schema\": 2", "\"schema\": 3"), S1));
        assertCorrupt(json().replace("\"schema\": 2", "\"schema\": -1"));
    }

    @Test
    public void malformedDocumentsAreCorrupt() {
        String json = json();
        assertCorrupt(json.substring(0, json.length() - 10));
        assertCorrupt("{\"schema\": 1, \"seasonId\": \"s1\", \"listings\": []}");
        assertCorrupt("{\"schema\": 1, \"seasonId\": \"s1\", \"listings\": {}, \"mailboxes\": []}");
        assertCorrupt("{\"schema\": 1, \"seasonId\": \"S 1\", \"listings\": [], \"mailboxes\": []}");
        assertCorrupt(json.replace("\"listings\": [", "\"listings\": [\"x\", "));
    }

    @Test
    public void invalidValuesAreCorrupt() {
        assertCorrupt(json().replace("\"reason\": \"auction_expired\"", "\"reason\": \"gift\""));
        assertCorrupt(json().replace("\"price\": 900", "\"price\": 0"));
        assertCorrupt(json().replace("\"price\": 900", "\"price\": 9.5"));
        assertCorrupt(json().replace("\"gold\": 950", "\"gold\": -1"));
        assertCorrupt(json().replace("\"count\": 16", "\"count\": 0"));
        assertCorrupt(json().replace("\"rarity\": \"rare\"", "\"rarity\": \"RARE\""));
        assertCorrupt(json().replace("\"itemId\": \"minecraft:diamond\"", "\"itemId\": \"diamond\""));
        assertCorrupt(json().replace("\"sellerName\": \"Steve\"", "\"sellerName\": \"\""));
        assertCorrupt(json().replace("\"expiresAtMillis\": 1794268800001", "\"expiresAtMillis\": 1794096000001"));
        assertCorrupt(json().replace("\"itemData\": \"{id:\\\"minecraft:bread\\\"}\"", "\"itemData\": \"\""));
        assertCorrupt("duplicate listing id", json().replace(LISTING_2.toString(), LISTING_1.toString()));
        assertCorrupt("duplicate mailbox", json().replace("\"owner\": \"" + BUYER, "\"owner\": \"" + SELLER));
    }

    private static void assertCorrupt(String json) {
        assertCorrupt("", json);
    }

    private static void assertCorrupt(String message, String json) {
        assertThrows(message, CorruptDataException.class, () -> MarketCodec.decode(json, S1));
    }
}
