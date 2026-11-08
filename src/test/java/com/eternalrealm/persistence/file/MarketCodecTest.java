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
        return new MarketData(S1, List.of(sword, diamonds),
                List.of(new Mailbox(SELLER, 950, List.of(returned)), new Mailbox(BUYER, 7, List.of())));
    }

    private static String json() {
        return MarketCodec.encode(sample());
    }

    @Test
    public void roundTripKeepsEverything() throws Exception {
        String json = json();
        assertTrue(json.contains("\"schema\": 1"));
        assertTrue(json.contains("\"seasonId\": \"s1\""));
        assertEquals(sample(), MarketCodec.decode(json, S1));
        assertEquals(MarketData.empty(S1), MarketCodec.decode(MarketCodec.encode(MarketData.empty(S1)), S1));
    }

    @Test
    public void aFileOfAnotherSeasonIsCorrupt() {
        assertThrows(CorruptDataException.class, () -> MarketCodec.decode(json(), SeasonId.of("s2")));
    }

    @Test
    public void newerSchemaIsIncompatibleNotCorrupt() {
        assertThrows(IncompatibleDataException.class,
                () -> MarketCodec.decode(json().replace("\"schema\": 1", "\"schema\": 3"), S1));
        assertCorrupt(json().replace("\"schema\": 1", "\"schema\": -1"));
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
