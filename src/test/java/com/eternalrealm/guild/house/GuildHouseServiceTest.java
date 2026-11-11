package com.eternalrealm.guild.house;

import com.eternalrealm.guild.GuildRank;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GuildHouseServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final UUID GUILD = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID BOB = UUID.fromString("00000000-0000-4000-8000-0000000000b0");

    private GuildHouseService service;

    static GuildHouseCatalog catalog() {
        return new GuildHouseCatalog(List.of(
                new GuildHouseDesign("hut", 1, 10_000, 0x112233, 0x445566, 21, 21, 12),
                new GuildHouseDesign("hall", 2, 40_000, 0x223344, 0x556677, 31, 31, 18),
                new GuildHouseDesign("palace", 5, 400_000, 0xFFFFFF, 0xFFCC00, 61, 61, 40)));
    }

    @Before
    public void setUp() {
        service = new GuildHouseService(catalog(), List.of());
    }

    @Test
    public void onlyTheMasterBuysAndOnlyOnce() {
        assertEquals(GuildHouseOutcome.NO_PERMISSION, service.buy(GUILD, GuildRank.OFFICER, "hut", 1_000_000, NOW).outcome());
        assertEquals(GuildHouseOutcome.NO_PERMISSION, service.buy(GUILD, GuildRank.MEMBER, "hut", 1_000_000, NOW).outcome());
        assertEquals(GuildHouseOutcome.NOT_IN_GUILD, service.buy(GUILD, null, "hut", 1_000_000, NOW).outcome());
        assertEquals(GuildHouseOutcome.UNKNOWN_DESIGN, service.buy(GUILD, GuildRank.MASTER, "castle", 1_000_000, NOW).outcome());
        GuildHouseResult bought = service.buy(GUILD, GuildRank.MASTER, "hut", 1_000_000, NOW);
        assertTrue(bought.isSuccess());
        assertEquals(10_000, bought.fromWallet());
        assertEquals("hut", service.house(GUILD).design());
        assertEquals(NOW, service.house(GUILD).since());
        assertEquals(GuildHouseOutcome.ALREADY_OWNED, service.buy(GUILD, GuildRank.MASTER, "hall", 1_000_000, NOW).outcome());
        assertFalse(service.house(OTHER).hasHouse());
    }

    @Test
    public void theFundPaysFirstAndTheWalletTheRest() {
        assertEquals(GuildHouseOutcome.INVALID_AMOUNT, service.donate(GUILD, GuildRank.MEMBER, 0, 100).outcome());
        assertEquals(GuildHouseOutcome.INVALID_AMOUNT, service.donate(GUILD, GuildRank.MEMBER, 500, 100).outcome());
        assertEquals(GuildHouseOutcome.NOT_IN_GUILD, service.donate(GUILD, null, 5, 100).outcome());
        GuildHouseResult donated = service.donate(GUILD, GuildRank.MEMBER, 6_000, 6_000);
        assertTrue(donated.isSuccess());
        assertEquals(6_000, donated.fromWallet());
        assertEquals(6_000, service.house(GUILD).fund());
        assertEquals(GuildHouseOutcome.NOT_ENOUGH_GOLD, service.buy(GUILD, GuildRank.MASTER, "hut", 3_999, NOW).outcome());
        assertEquals(6_000, service.house(GUILD).fund());
        GuildHouseResult bought = service.buy(GUILD, GuildRank.MASTER, "hut", 4_000, NOW);
        assertTrue(bought.isSuccess());
        assertEquals(6_000, bought.fromFund());
        assertEquals(4_000, bought.fromWallet());
        assertEquals(0, service.house(GUILD).fund());
    }

    @Test
    public void switchingTradesInHalfThePrice() {
        service.buy(GUILD, GuildRank.MASTER, "hut", 1_000_000, NOW);
        assertEquals(GuildHouseOutcome.SAME_DESIGN, service.switchTo(GUILD, GuildRank.MASTER, "hut", 1_000_000, NOW).outcome());
        assertEquals(GuildHouseOutcome.NO_PERMISSION, service.switchTo(GUILD, GuildRank.OFFICER, "hall", 1_000_000, NOW).outcome());
        // up: 40 000 - 5 000 trade-in
        assertEquals(GuildHouseOutcome.NOT_ENOUGH_GOLD, service.switchTo(GUILD, GuildRank.MASTER, "hall", 34_999, NOW).outcome());
        GuildHouseResult up = service.switchTo(GUILD, GuildRank.MASTER, "hall", 35_000, NOW.plusSeconds(60));
        assertTrue(up.isSuccess());
        assertEquals(35_000, up.cost());
        assertEquals(35_000, up.fromWallet());
        assertEquals("hall", service.house(GUILD).design());
        assertEquals(NOW.plusSeconds(60), service.house(GUILD).since());
        // down: the 20 000 trade-in pays the 10 000 hut, 10 000 go into the fund
        GuildHouseResult down = service.switchTo(GUILD, GuildRank.MASTER, "hut", 0, NOW);
        assertTrue(down.isSuccess());
        assertEquals(0, down.fromWallet());
        assertEquals(10_000, down.toFund());
        assertEquals(10_000, service.house(GUILD).fund());
        assertEquals(GuildHouseOutcome.NO_HOUSE, service.switchTo(OTHER, GuildRank.MASTER, "hut", 1_000_000, NOW).outcome());
    }

    @Test
    public void givingUpPaysHalfIntoTheFund() {
        assertEquals(GuildHouseOutcome.NO_HOUSE, service.sell(GUILD, GuildRank.MASTER).outcome());
        service.buy(GUILD, GuildRank.MASTER, "hall", 1_000_000, NOW);
        assertEquals(GuildHouseOutcome.NO_PERMISSION, service.sell(GUILD, GuildRank.OFFICER).outcome());
        GuildHouseResult sold = service.sell(GUILD, GuildRank.MASTER);
        assertTrue(sold.isSuccess());
        assertEquals(20_000, sold.toFund());
        assertFalse(service.house(GUILD).hasHouse());
        assertEquals(20_000, service.house(GUILD).fund());
        // the fund buys the next house
        GuildHouseResult again = service.buy(GUILD, GuildRank.MASTER, "hut", 0, NOW);
        assertTrue(again.isSuccess());
        assertEquals(10_000, again.fromFund());
        assertEquals(10_000, service.house(GUILD).fund());
    }

    @Test
    public void disbandingReleasesTheHouseAndReturnsTheGold() {
        service.donate(GUILD, GuildRank.MEMBER, 1_000, 1_000);
        service.buy(GUILD, GuildRank.MASTER, "hall", 1_000_000, NOW);
        GuildHouseService.Release release = service.disband(GUILD);
        assertEquals("hall", release.design());
        assertEquals(20_000, release.gold());
        assertFalse(service.house(GUILD).hasHouse());
        assertTrue(service.houses().isEmpty());
        assertEquals(new GuildHouseService.Release(null, 0), service.disband(GUILD));
    }

    @Test
    public void theGateLetsInOnlyTheOwnersOfItsDesign() {
        assertEquals(GateAccess.NOT_IN_GUILD, service.gate("hut", null));
        assertEquals(GateAccess.NO_HOUSE, service.gate("hut", GUILD));
        service.buy(GUILD, GuildRank.MASTER, "hut", 1_000_000, NOW);
        service.buy(OTHER, GuildRank.MASTER, "hut", 1_000_000, NOW);
        assertEquals(GateAccess.ENTER, service.gate("hut", GUILD));
        assertEquals(GateAccess.ENTER, service.gate("hut", OTHER));
        assertEquals(GateAccess.OTHER_DESIGN, service.gate("hall", GUILD));
        assertEquals(2, service.owners().size());
    }

    @Test
    public void theBoardHasModeratedMotdAndLimitedNotes() {
        assertEquals(GuildHouseOutcome.NO_PERMISSION, service.setMotd(GUILD, GuildRank.MEMBER, "hi").outcome());
        assertTrue(service.setMotd(GUILD, GuildRank.OFFICER, "  Raid at 21:00 §c!  ").isSuccess());
        assertEquals("Raid at 21:00 c!", service.house(GUILD).motd());
        assertTrue(service.setMotd(GUILD, GuildRank.MASTER, "x".repeat(500)).isSuccess());
        assertEquals(GuildHouseRules.MOTD_MAX, service.house(GUILD).motd().length());
        assertEquals(GuildHouseOutcome.EMPTY_TEXT, service.addNote(GUILD, GuildRank.MEMBER, ALICE, "Alice", "  ", NOW).outcome());
        assertTrue(service.addNote(GUILD, GuildRank.MEMBER, ALICE, "Alice", "selling iron", NOW).isSuccess());
        assertTrue(service.addNote(GUILD, GuildRank.MEMBER, ALICE, "Alice", "need a healer", NOW).isSuccess());
        assertEquals(GuildHouseOutcome.TOO_MANY_NOTES,
                service.addNote(GUILD, GuildRank.MEMBER, ALICE, "Alice", "third", NOW).outcome());
        long first = service.house(GUILD).notes().get(0).id();
        assertEquals(GuildHouseOutcome.NO_PERMISSION, service.removeNote(GUILD, BOB, GuildRank.MEMBER, first).outcome());
        assertTrue(service.removeNote(GUILD, ALICE, GuildRank.MEMBER, first).isSuccess());
        long second = service.house(GUILD).notes().get(0).id();
        assertTrue(service.removeNote(GUILD, BOB, GuildRank.OFFICER, second).isSuccess());
        assertEquals(GuildHouseOutcome.NOTE_NOT_FOUND, service.removeNote(GUILD, BOB, GuildRank.OFFICER, second).outcome());
        for (int i = 0; i < GuildHouseRules.NOTES_MAX + 3; i++) {
            UUID author = new UUID(7, i);
            assertTrue(service.addNote(GUILD, GuildRank.MEMBER, author, "m" + i, "note " + i, NOW).isSuccess());
        }
        List<BoardNote> notes = service.house(GUILD).notes();
        assertEquals(GuildHouseRules.NOTES_MAX, notes.size());
        assertEquals("note 3", notes.get(0).text());
    }

    @Test
    public void theStorageLogKeepsTheNewestLines() {
        for (int i = 0; i < GuildHouseRules.LOG_SIZE + 25; i++) {
            service.log(GUILD, new StorageLogEntry(NOW.plusSeconds(i), ALICE, "Alice", "minecraft:iron_ingot", i + 1,
                    i % 2 == 0, "chest.1"));
        }
        List<StorageLogEntry> log = service.house(GUILD).log();
        assertEquals(GuildHouseRules.LOG_SIZE, log.size());
        assertEquals(26, log.get(0).count());
        assertEquals(GuildHouseRules.LOG_SIZE + 25, log.get(log.size() - 1).count());
    }

    @Test
    public void everyChangeIsSavedAndBlankDataIsDropped() {
        long before = service.revision();
        service.donate(GUILD, GuildRank.MEMBER, 10, 10);
        assertTrue(service.revision() > before);
        assertTrue(service.isDirty());
        service.markSaved(service.revision());
        assertFalse(service.isDirty());
        service.setMotd(OTHER, GuildRank.MASTER, "");
        assertEquals(1, service.houses().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void storedHousesMustNameKnownDesigns() {
        new GuildHouseService(catalog(), List.of(GuildHouse.empty(GUILD).withDesign("castle", NOW)));
    }

    @Test
    public void paymentsAndRefundsFollowTheRules() {
        GuildHouseDesign hut = catalog().design("hut").orElseThrow();
        GuildHouseDesign palace = catalog().design("palace").orElseThrow();
        assertEquals(5_000, GuildHouseRules.refund(hut));
        assertEquals(395_000, GuildHouseRules.switchBalance(hut, palace));
        assertEquals(-190_000, GuildHouseRules.switchBalance(palace, hut));
        assertEquals(new GuildHouseRules.Payment(100, 900, true), GuildHouseRules.pay(1_000, 100, 900));
        assertFalse(GuildHouseRules.pay(1_000, 100, 899).affordable());
        assertTrue(GuildHouseRules.mayOpenVault(GuildRank.OFFICER));
        assertFalse(GuildHouseRules.mayOpenVault(GuildRank.MEMBER));
        assertTrue(GuildHouseRules.isVault("vault.1"));
        assertFalse(GuildHouseRules.isVault("chest.1"));
        assertEquals(54, hut.storageSlots());
        assertEquals(162, palace.storageSlots());
        assertEquals(2, GuildHouseRules.vaultChests(5));
    }
}
