package com.eternalrealm.gametest;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.economy.Listing;
import com.eternalrealm.economy.Lot;
import com.eternalrealm.economy.LotState;
import com.eternalrealm.economy.MailItem;
import com.eternalrealm.economy.MailReason;
import com.eternalrealm.economy.PriceHistory;
import com.eternalrealm.economy.forge.MarketItems;
import com.eternalrealm.economy.forge.MarketService;
import com.eternalrealm.npc.forge.NpcRole;
import com.eternalrealm.npc.forge.Npcs;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-game tests of the market (docs/modules/market.md): the trading board at the hall's clerk (fee, purchase, the
 * seller's pay and the bought item through the mailbox, prices), and the auction hall (registering and starting at the
 * hall, bidding from anywhere with escrow and refunds, buying outright, a lot running out with and without bids). The
 * fake players are never online, so everything they get goes to their mailboxes, as for players who logged out.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketGameTests {
    private static final String ARENA = "arena";
    private static final Vec3 COUNTER = new Vec3(4.5, 2.0, 4.5);
    /**
     * Far beyond the clerk's reach (bidding still works from here): high above the test, because the GameTests are
     * packed side by side and a spot to the side can fall within reach of another test's clerk.
     */
    private static final Vec3 FAR = new Vec3(4.5, 2.0 + 80.0, 4.5);

    private MarketGameTests() {
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void theBoardTradesAtTheHallAndPaysThroughTheMailbox(GameTestHelper helper) {
        ServerContext context = context();
        PlayerDataService data = context.playerData();
        MarketService market = context.game().market();
        String run = UUID.randomUUID().toString().substring(0, 6);
        FakePlayer seller = player(helper, "er_gt_board_s_" + run, COUNTER);
        FakePlayer buyer = player(helper, "er_gt_board_b_" + run, COUNTER.add(1.0, 0.0, 0.0));
        Mob clerk = clerk(helper);
        joined(helper, data, List.of(seller, buyer), market, clerk, () -> {
            UUID sellerId = seller.getUUID();
            UUID buyerId = buyer.getUUID();
            seller.getInventory().items.set(1, new ItemStack(Items.IRON_INGOT, 16));
            data.grantGold(sellerId, 100);
            long sellerGold = gold(data, sellerId);

            market.listFromBag(seller, 1, 10, 500);
            List<Listing> listed = market.house().listingsOf(sellerId);
            check(listed.size() == 1 && listed.get(0).price() == 500 && listed.get(0).item().count() == 10,
                    "one listing of 10 for 500 G, got " + listed);
            check(seller.getInventory().items.get(1).getCount() == 6, "10 of the 16 ingots left the bag");
            check(gold(data, sellerId) == sellerGold - 10, "the fee is 2 % of 500 G");
            Listing listing = listed.get(0);

            data.grantGold(buyerId, 600);
            long buyerGold = gold(data, buyerId);
            move(helper, buyer, FAR);
            market.buy(buyer, listing.id());
            check(market.house().listing(listing.id()).isPresent() && gold(data, buyerId) == buyerGold,
                    "buying is only possible at the hall");
            move(helper, buyer, COUNTER.add(1.0, 0.0, 0.0));
            market.buy(buyer, listing.id());
            check(market.house().listing(listing.id()).isEmpty(), "the listing is gone after the purchase");
            check(gold(data, buyerId) == buyerGold - 500, "the buyer paid 500 G");
            List<MailItem> bought = market.house().mailbox(buyerId).items();
            check(bought.size() == 1 && bought.get(0).reason() == MailReason.AUCTION_PURCHASE
                    && bought.get(0).item().count() == 10, "the purchase waits in the mailbox: " + bought);
            check(market.house().mailbox(sellerId).gold() == 475, "the seller gets 500 G less 5 % tax by mail");
            PriceHistory.Stats stats = market.prices().stats(MarketItems.describe(new ItemStack(Items.IRON_INGOT,
                    10), 10).group()).orElseThrow(() -> new GameTestAssertException("no price was recorded"));
            check(stats.sales() >= 1 && stats.last() == 50, "the sale is recorded at 50 G each, got " + stats);

            market.claim(buyer, bought.get(0).id());
            check(count(buyer, Items.IRON_INGOT) == 10, "the buyer took the 10 ingots from the mailbox");
            long before = gold(data, sellerId);
            market.claim(seller, null);
            check(gold(data, sellerId) == before + 475 && market.house().mailbox(sellerId).gold() == 0,
                    "the seller took the 475 G");
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void theHallTakesBidsFromAnywhereAndSellsOutright(GameTestHelper helper) {
        ServerContext context = context();
        PlayerDataService data = context.playerData();
        MarketService market = context.game().market();
        String run = UUID.randomUUID().toString().substring(0, 6);
        FakePlayer seller = player(helper, "er_gt_hall_s_" + run, COUNTER);
        FakePlayer first = player(helper, "er_gt_hall_a_" + run, FAR);
        FakePlayer second = player(helper, "er_gt_hall_b_" + run, FAR.add(2.0, 0.0, 0.0));
        Mob clerk = clerk(helper);
        joined(helper, data, List.of(seller, first, second), market, clerk, () -> {
            UUID sellerId = seller.getUUID();
            check(market.atHall(seller) && !market.atHall(first), "the counter is at the clerk only");
            seller.getInventory().items.set(1, new ItemStack(Items.DIAMOND, 1));
            data.grantGold(sellerId, 100);
            data.grantGold(first.getUUID(), 5_000);
            data.grantGold(second.getUUID(), 5_000);

            market.register(first, 1, 1, 200, 1_000, 10);
            check(market.hall().lotsOf(first.getUUID()).isEmpty(), "registering away from the hall is refused");
            market.register(seller, 1, 1, 200, 1_000, 10);
            List<Lot> mine = market.hall().lotsOf(sellerId);
            check(mine.size() == 1 && mine.get(0).state() == LotState.DRAFT, "a draft is registered: " + mine);
            check(seller.getInventory().items.get(1).isEmpty(), "the diamond is held by the hall");
            UUID lotId = mine.get(0).id();
            long sellerGold = gold(data, sellerId);
            market.start(seller, lotId);
            Lot lot = market.hall().lot(lotId).orElseThrow();
            check(lot.state() == LotState.RUNNING || lot.state() == LotState.QUEUED, "started: " + lot.state());
            check(gold(data, sellerId) == sellerGold - 10, "the start fee is 1 % of 200 G, at least 10 G");
            check(lot.state() == LotState.RUNNING, "the hall had a free place");

            long firstGold = gold(data, first.getUUID());
            market.bid(first, lotId, 200);
            check(gold(data, first.getUUID()) == firstGold - 200, "the bid is held from the bidder's gold");
            long secondGold = gold(data, second.getUUID());
            market.bid(second, lotId, 205);
            check(gold(data, second.getUUID()) == secondGold, "a bid under the next minimum (210 G) is refused");
            market.bid(second, lotId, 210);
            check(gold(data, second.getUUID()) == secondGold - 210, "210 G is held");
            check(market.house().mailbox(first.getUUID()).gold() == 200, "the outbid 200 G go back (by mail)");

            market.buyout(first, lotId);
            Lot sold = market.hall().lot(lotId).orElseThrow();
            check(sold.state() == LotState.SOLD && sold.currentPrice() == 1_000, "bought outright: " + sold);
            check(market.house().mailbox(second.getUUID()).gold() == 210, "the second bidder gets the 210 G back");
            List<MailItem> won = market.house().mailbox(first.getUUID()).items();
            check(won.size() == 1 && won.get(0).reason() == MailReason.AUCTION_WON, "the diamond is mailed: " + won);
            check(market.house().mailbox(sellerId).gold() == 950, "the seller gets 1,000 G less 5 % tax");
            check(market.hall().recent().stream().anyMatch(each -> each.id().equals(lotId)),
                    "the result is listed");
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void lotsRunOutToTheHighestBidderOrBackToTheSeller(GameTestHelper helper) {
        ServerContext context = context();
        PlayerDataService data = context.playerData();
        MarketService market = context.game().market();
        String run = UUID.randomUUID().toString().substring(0, 6);
        FakePlayer seller = player(helper, "er_gt_lot_s_" + run, COUNTER);
        FakePlayer bidder = player(helper, "er_gt_lot_b_" + run, FAR);
        Mob clerk = clerk(helper);
        joined(helper, data, List.of(seller, bidder), market, clerk, () -> {
            UUID sellerId = seller.getUUID();
            seller.getInventory().items.set(1, new ItemStack(Items.EMERALD, 3));
            seller.getInventory().items.set(2, new ItemStack(Items.GOLD_INGOT, 2));
            data.grantGold(sellerId, 100);
            data.grantGold(bidder.getUUID(), 1_000);
            market.register(seller, 1, 3, 50, 0, 10);
            market.register(seller, 2, 2, 80, 0, 10);
            List<Lot> lots = market.hall().lotsOf(sellerId);
            check(lots.size() == 2, "two drafts: " + lots);
            UUID emeralds = lots.stream().filter(lot -> lot.startPrice() == 50).findFirst().orElseThrow().id();
            UUID ingots = lots.stream().filter(lot -> lot.startPrice() == 80).findFirst().orElseThrow().id();
            market.start(seller, emeralds);
            market.start(seller, ingots);
            check(market.hall().lot(emeralds).orElseThrow().state() == LotState.RUNNING
                    && market.hall().lot(ingots).orElseThrow().state() == LotState.RUNNING, "both are live");
            market.bid(bidder, emeralds, 50);

            market.advanceClockForTests(10 * 60_000L + 2_000L);
            market.tickSecond();
            Lot sold = market.hall().lot(emeralds).orElseThrow();
            Lot unsold = market.hall().lot(ingots).orElseThrow();
            check(sold.state() == LotState.SOLD && unsold.state() == LotState.UNSOLD,
                    "after their time: " + sold.state() + ", " + unsold.state());
            List<MailItem> won = market.house().mailbox(bidder.getUUID()).items();
            check(won.size() == 1 && won.get(0).reason() == MailReason.AUCTION_WON && won.get(0).item().count() == 3,
                    "the emeralds go to the winner: " + won);
            check(market.house().mailbox(sellerId).gold() == 48, "50 G less 5 % tax (2 G) to the seller");
            List<MailItem> back = market.house().mailbox(sellerId).items();
            check(back.size() == 1 && back.get(0).reason() == MailReason.AUCTION_RETURNED
                    && back.get(0).item().count() == 2, "the unsold ingots go back: " + back);
        });
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Waits for the characters and the market, then runs {@code steps} once; the clerk and the characters go away
     * afterwards whatever happened.
     */
    private static void joined(GameTestHelper helper, PlayerDataService data, List<FakePlayer> players,
                               MarketService market, Mob clerk, Runnable steps) {
        for (FakePlayer player : players) {
            data.onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        }
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            if (done.get()) {
                return;
            }
            for (FakePlayer player : players) {
                check(data.isActive(player.getUUID()), "waiting for the characters to load");
            }
            check(market.isOpen(), "waiting for the market to load");
            done.set(true);
            try {
                steps.run();
            } catch (GameTestAssertException e) {
                failure.set(e);
                throw e;
            } finally {
                clerk.discard();
                for (FakePlayer player : players) {
                    data.onPlayerLeave(player.getUUID());
                }
            }
        });
    }

    private static Mob clerk(GameTestHelper helper) {
        // this test's own clerk: ensure() would otherwise take over the clerk of a test placed within 24 blocks
        return Npcs.ensure(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), NpcRole.AUCTION, 0.0F,
                        "eternalrealm_gametest", UUID.randomUUID().toString())
                .orElseThrow(() -> new GameTestAssertException("no auction clerk"));
    }

    private static long gold(PlayerDataService data, UUID player) {
        return data.snapshot(player).map(CharacterSnapshot::gold).orElse(-1L);
    }

    private static int count(FakePlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static ServerContext context() {
        return ServerContext.current().orElseThrow(() -> new GameTestAssertException("ServerContext is not installed"));
    }

    private static FakePlayer player(GameTestHelper helper, String name, Vec3 at) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        move(helper, player, at);
        return player;
    }

    private static void move(GameTestHelper helper, FakePlayer player, Vec3 at) {
        Vec3 position = helper.absoluteVec(at);
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
