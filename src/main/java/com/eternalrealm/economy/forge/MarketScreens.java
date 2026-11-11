package com.eternalrealm.economy.forge;

import com.eternalrealm.economy.AuctionHall;
import com.eternalrealm.economy.AuctionRules;
import com.eternalrealm.economy.Bid;
import com.eternalrealm.economy.HallRules;
import com.eternalrealm.economy.ItemInfo;
import com.eternalrealm.economy.Listing;
import com.eternalrealm.economy.Lot;
import com.eternalrealm.economy.LotState;
import com.eternalrealm.economy.MailItem;
import com.eternalrealm.economy.Mailbox;
import com.eternalrealm.economy.MarketQuery;
import com.eternalrealm.economy.Page;
import com.eternalrealm.economy.PriceHistory;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiDataPacket;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The market's screens (docs/modules/market.md): the trading board (掲示板, opened at the hall's clerk, the mail tab
 * also at the post) and the auction hall (key O anywhere; registering and starting lots only at the hall). The client
 * only shows what these documents say and asks with UI actions; the {@link MarketService} decides.
 */
public final class MarketScreens {
    /** The board: a search result page, the player's listings, mail and the rules. */
    public static final String BOARD_CHANNEL = "market.board";
    /** Recent prices of one item group (相場). */
    public static final String PRICES_CHANNEL = "market.prices";
    /** The auction hall: running, waiting, the player's and finished lots. */
    public static final String HALL_CHANNEL = "market.auction";
    private static final int PAGE_SIZE = 8;
    private static final int QUEUED_SHOWN = 10;
    private static final int RECENT_SHOWN = 15;
    private static final int HISTORY_SHOWN = 6;

    private MarketScreens() {
    }

    public static void register() {
        // the board
        UiActions.register("board.query", (player, context, args) ->
                sendBoard(player, market(context), query(args), "browse", false));
        UiActions.register("board.buy", (player, context, args) -> {
            MarketService market = market(context);
            market.buy(player, UiArgs.uuid(args, "id"));
            sendBoard(player, market, query(args), "browse", false);
        });
        UiActions.register("board.list", (player, context, args) -> {
            MarketService market = market(context);
            market.listFromBag(player, slot(args), (int) UiArgs.number(args, "count", 1, ItemInfo.MAX_COUNT),
                    UiArgs.number(args, "price", 1, Listing.MAX_PRICE));
            sendBoard(player, market, MarketQuery.all(MarketQuery.Sort.NEWEST, PAGE_SIZE), "mine", false);
        });
        UiActions.register("board.cancel", (player, context, args) -> {
            MarketService market = market(context);
            market.cancel(player, UiArgs.uuid(args, "id"));
            sendBoard(player, market, MarketQuery.all(MarketQuery.Sort.NEWEST, PAGE_SIZE), "mine", false);
        });
        UiActions.register("board.prices", (player, context, args) -> sendPrices(player, market(context),
                slot(args)));
        UiActions.register("mail.claim", (player, context, args) -> {
            MarketService market = market(context);
            market.claim(player, args.has("id") ? UiArgs.uuid(args, "id") : null);
            sendBoard(player, market, MarketQuery.all(MarketQuery.Sort.NEWEST, PAGE_SIZE), "mail", false);
        });
        UiActions.register("mail.claim_all", (player, context, args) -> {
            MarketService market = market(context);
            Mailbox box = market.mailbox(player);
            if (box.gold() > 0) {
                market.claim(player, null);
            }
            for (MailItem item : box.items()) {
                market.claim(player, item.id());
            }
            sendBoard(player, market, MarketQuery.all(MarketQuery.Sort.NEWEST, PAGE_SIZE), "mail", false);
        });
        UiActions.register("board.open", (player, context, args) -> {
            // from the hall's screen: the board is the clerk's, so only at the hall
            if (market(context).atHall(player)) {
                openBoard(player, false);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.eternalrealm.hall.not_at_hall").withStyle(net.minecraft.ChatFormatting.RED), true);
            }
        });
        // the auction hall
        UiActions.register("auction.open", (player, context, args) -> market(context).watch(player));
        UiActions.register("auction.close", (player, context, args) -> market(context).unwatch(player));
        UiActions.register("auction.bid", (player, context, args) -> market(context).bid(player,
                UiArgs.uuid(args, "id"), UiArgs.number(args, "amount", 1, Listing.MAX_PRICE)));
        UiActions.register("auction.buyout", (player, context, args) -> market(context).buyout(player,
                UiArgs.uuid(args, "id")));
        UiActions.register("auction.register", (player, context, args) -> market(context).register(player,
                slot(args), (int) UiArgs.number(args, "count", 1, ItemInfo.MAX_COUNT),
                UiArgs.number(args, "start", 1, Listing.MAX_PRICE),
                UiArgs.number(args, "buyout", 0, Listing.MAX_PRICE),
                (int) UiArgs.number(args, "minutes", 1, HallRules.MAX_DURATION_MINUTES)));
        UiActions.register("auction.start", (player, context, args) -> market(context).start(player,
                UiArgs.uuid(args, "id")));
        UiActions.register("auction.withdraw", (player, context, args) -> market(context).withdraw(player,
                UiArgs.uuid(args, "id")));
    }

    private static MarketService market(ServerContext context) {
        return context.game().market();
    }

    private static int slot(com.google.gson.JsonObject args) {
        return (int) UiArgs.number(args, "slot", Bag.FIRST_SLOT, Bag.LAST_SLOT);
    }

    private static MarketQuery query(JsonObject args) {
        String text = args.has("text") ? UiArgs.string(args, "text", MarketQuery.MAX_FILTER_LENGTH) : "";
        String category = args.has("category") ? UiArgs.string(args, "category", 16) : "";
        if (!category.isEmpty() && !ItemInfo.CATEGORIES.contains(category)) {
            throw new UiArgs.InvalidArgumentException("unknown category");
        }
        int minRarity = args.has("rarity") ? (int) UiArgs.number(args, "rarity", -1, 4) : -1;
        int minLevel = args.has("minLevel") ? (int) UiArgs.number(args, "minLevel", 0, ItemInfo.MAX_LEVEL) : 0;
        int maxLevel = args.has("maxLevel") ? (int) UiArgs.number(args, "maxLevel", 0, ItemInfo.MAX_LEVEL)
                : ItemInfo.MAX_LEVEL;
        boolean gold = args.has("gold") && args.get("gold").isJsonPrimitive() && args.get("gold").getAsBoolean();
        MarketQuery.Sort sort = MarketQuery.Sort.NEWEST;
        if (args.has("sort")) {
            String name = UiArgs.string(args, "sort", 32);
            try {
                sort = MarketQuery.Sort.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new UiArgs.InvalidArgumentException("unknown sort");
            }
        }
        int page = args.has("page") ? (int) UiArgs.number(args, "page", 0, 10_000) : 0;
        return new MarketQuery(text, category, minRarity, minLevel, Math.max(minLevel, maxLevel), gold, sort, page,
                PAGE_SIZE);
    }

    // ------------------------------------------------------------------ the board

    /** Opens the board (at the hall's clerk) or its mail tab (at the post: {@code mailOnly}). */
    public static void openBoard(ServerPlayer player, boolean mailOnly) {
        ServerContext.current().ifPresent(context -> {
            MarketService market = market(context);
            market.remind(player);
            sendBoard(player, market, MarketQuery.all(MarketQuery.Sort.NEWEST, PAGE_SIZE),
                    mailOnly ? "mail" : "browse", true);
        });
    }

    static void sendBoard(ServerPlayer player, MarketService market, MarketQuery query, String tab, boolean open) {
        long now = market.now();
        JsonObject data = new JsonObject();
        data.addProperty("open", open);
        data.addProperty("tab", tab);
        data.addProperty("atHall", market.atHall(player));
        data.addProperty("gold", market.gold(player));
        data.addProperty("ready", market.isOpen());
        JsonObject echo = new JsonObject();
        echo.addProperty("text", query.text());
        echo.addProperty("category", query.category());
        echo.addProperty("rarity", query.minRarity());
        echo.addProperty("minLevel", query.minLevel());
        echo.addProperty("maxLevel", query.maxLevel());
        echo.addProperty("gold", query.goldOnly());
        echo.addProperty("sort", query.sort().name());
        data.add("query", echo);
        Page<Listing> page = market.house().search(query, now);
        data.addProperty("page", page.page());
        data.addProperty("pages", Math.max(1, page.totalPages()));
        data.addProperty("total", page.totalItems());
        JsonArray listings = new JsonArray();
        for (Listing listing : page.items()) {
            listings.add(listing(listing, player, market, now));
        }
        data.add("listings", listings);
        JsonArray mine = new JsonArray();
        for (Listing listing : market.house().listingsOf(player.getUUID())) {
            mine.add(listing(listing, player, market, now));
        }
        data.add("mine", mine);
        Mailbox box = market.mailbox(player);
        JsonObject mail = new JsonObject();
        mail.addProperty("gold", box.gold());
        JsonArray items = new JsonArray();
        for (MailItem item : box.items()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", item.id().toString());
            json.addProperty("item", item.itemData());
            json.addProperty("reason", item.reason().id());
            json.addProperty("ago", Math.max(0L, now - item.receivedAtMillis()));
            items.add(json);
        }
        mail.add("items", items);
        data.add("mail", mail);
        AuctionRules rules = market.house().rules();
        JsonObject ruleJson = new JsonObject();
        ruleJson.addProperty("feeBp", rules.listingFeeBasisPoints());
        ruleJson.addProperty("minFee", rules.minimumListingFee());
        ruleJson.addProperty("maxFee", AuctionRules.MAX_LISTING_FEE);
        ruleJson.addProperty("taxBp", rules.salesTaxBasisPoints());
        ruleJson.addProperty("hours", rules.listingDuration().toHours());
        ruleJson.addProperty("maxListings", rules.maxListingsPerPlayer());
        data.add("rules", ruleJson);
        ERNetwork.sendToPlayer(player, new UiDataPacket(BOARD_CHANNEL, fit(data, "listings", "mine")));
    }

    private static JsonObject listing(Listing listing, ServerPlayer player, MarketService market, long now) {
        JsonObject json = new JsonObject();
        json.addProperty("id", listing.id().toString());
        json.addProperty("item", listing.itemData());
        json.addProperty("price", listing.price());
        json.addProperty("count", listing.item().count());
        json.addProperty("seller", listing.sellerName());
        json.addProperty("mine", listing.sellerId().equals(player.getUUID()));
        json.addProperty("remaining", Math.max(0L, listing.expiresAtMillis() - now));
        json.addProperty("gold", listing.item().gold());
        json.addProperty("level", listing.item().level());
        market.prices().stats(listing.item().group()).ifPresent(stats -> json.addProperty("median", stats.median()));
        return json;
    }

    /** Recent prices of the item in bag slot {@code slot} (for pricing it). */
    static void sendPrices(ServerPlayer player, MarketService market, int slot) {
        ItemStack stack = player.getInventory().items.get(slot);
        JsonObject data = new JsonObject();
        data.addProperty("slot", slot);
        if (!stack.isEmpty()) {
            ItemInfo info = MarketItems.describe(stack, stack.getCount());
            data.addProperty("group", info.group());
            long now = market.now();
            Optional<PriceHistory.Stats> stats = market.prices().stats(info.group());
            stats.ifPresent(s -> {
                data.addProperty("sales", s.sales());
                data.addProperty("lowest", s.lowest());
                data.addProperty("median", s.median());
                data.addProperty("highest", s.highest());
                data.addProperty("last", s.last());
                data.addProperty("lastAgo", Math.max(0L, now - s.lastAtMillis()));
            });
            JsonArray recent = new JsonArray();
            for (PriceHistory.Sale sale : market.prices().sales(info.group())) {
                JsonObject json = new JsonObject();
                json.addProperty("unit", sale.unitPrice());
                json.addProperty("count", sale.count());
                json.addProperty("ago", Math.max(0L, now - sale.atMillis()));
                json.addProperty("auction", sale.auction());
                recent.add(json);
            }
            data.add("recent", recent);
            // the cheapest piece of the same group on the board right now
            long cheapest = 0;
            for (Listing listing : market.house().search(new MarketQuery("", "", -1, 0, ItemInfo.MAX_LEVEL, false,
                    MarketQuery.Sort.UNIT_PRICE_ASCENDING, 0, MarketQuery.MAX_PAGE_SIZE), now).items()) {
                if (listing.item().group().equals(info.group())) {
                    cheapest = listing.price() / listing.item().count();
                    break;
                }
            }
            data.addProperty("boardLowest", cheapest);
        }
        ERNetwork.sendToPlayer(player, new UiDataPacket(PRICES_CHANNEL, data.toString()));
    }

    // ------------------------------------------------------------------ the auction hall

    static void sendHall(ServerPlayer player, MarketService market, boolean open) {
        AuctionHall hall = market.hall();
        long now = market.now();
        UUID me = player.getUUID();
        JsonObject data = new JsonObject();
        data.addProperty("open", open);
        data.addProperty("atHall", market.atHall(player));
        data.addProperty("gold", market.gold(player));
        data.addProperty("ready", market.isOpen());
        HallRules rules = hall.rules();
        JsonObject ruleJson = new JsonObject();
        ruleJson.addProperty("maxRunning", rules.maxRunning());
        ruleJson.addProperty("maxLots", rules.maxLotsPerPlayer());
        ruleJson.addProperty("feeBp", rules.feeBasisPoints());
        ruleJson.addProperty("minFee", rules.minimumFee());
        ruleJson.addProperty("taxBp", rules.taxBasisPoints());
        ruleJson.addProperty("incBp", rules.minIncrementBasisPoints());
        ruleJson.addProperty("incMin", rules.minIncrementGold());
        ruleJson.addProperty("snipeSeconds", rules.snipeWindowMillis() / 1000);
        JsonArray durations = new JsonArray();
        rules.durationsMinutes().forEach(durations::add);
        ruleJson.add("durations", durations);
        data.add("rules", ruleJson);
        JsonArray running = new JsonArray();
        for (Lot lot : hall.running()) {
            running.add(lot(lot, me, hall, now, true));
        }
        data.add("running", running);
        List<Lot> waiting = hall.queued();
        data.addProperty("queuedTotal", waiting.size());
        JsonArray queued = new JsonArray();
        for (int i = 0; i < waiting.size() && i < QUEUED_SHOWN; i++) {
            JsonObject json = lot(waiting.get(i), me, hall, now, false);
            json.addProperty("position", i + 1);
            queued.add(json);
        }
        data.add("queued", queued);
        JsonArray mine = new JsonArray();
        for (Lot lot : hall.lotsOf(me)) {
            JsonObject json = lot(lot, me, hall, now, lot.state() == LotState.RUNNING);
            if (lot.state() == LotState.QUEUED) {
                json.addProperty("position", hall.queuePosition(lot.id()));
            }
            mine.add(json);
        }
        data.add("mine", mine);
        JsonArray recent = new JsonArray();
        List<Lot> finished = hall.recent();
        for (int i = 0; i < finished.size() && i < RECENT_SHOWN; i++) {
            recent.add(lot(finished.get(i), me, hall, now, false));
        }
        data.add("recent", recent);
        ERNetwork.sendToPlayer(player, new UiDataPacket(HALL_CHANNEL, fit(data, "recent", "queued")));
    }

    private static JsonObject lot(Lot lot, UUID me, AuctionHall hall, long now, boolean withBids) {
        JsonObject json = new JsonObject();
        json.addProperty("id", lot.id().toString());
        json.addProperty("item", lot.itemData());
        json.addProperty("count", lot.item().count());
        json.addProperty("seller", lot.sellerName());
        json.addProperty("mine", lot.seller().equals(me));
        json.addProperty("state", lot.state().id());
        json.addProperty("start", lot.startPrice());
        json.addProperty("buyout", lot.buyoutPrice());
        json.addProperty("price", lot.currentPrice());
        json.addProperty("bids", lot.bidCount());
        json.addProperty("minutes", lot.durationMinutes());
        json.addProperty("gold", lot.item().gold());
        if (lot.state() == LotState.RUNNING) {
            json.addProperty("next", hall.nextMinimum(lot));
            json.addProperty("remaining", lot.remainingMillis(now));
        }
        Optional<Bid> highest = lot.highest();
        json.addProperty("highest", highest.map(Bid::bidderName).orElse(""));
        json.addProperty("leading", highest.map(bid -> bid.bidder().equals(me)).orElse(false));
        if (withBids) {
            JsonArray history = new JsonArray();
            List<Bid> bids = lot.bids();
            for (int i = bids.size() - 1; i >= 0 && i >= bids.size() - HISTORY_SHOWN; i--) {
                JsonObject bid = new JsonObject();
                bid.addProperty("name", bids.get(i).bidderName());
                bid.addProperty("amount", bids.get(i).amount());
                bid.addProperty("ago", Math.max(0L, now - bids.get(i).atMillis()));
                bid.addProperty("me", bids.get(i).bidder().equals(me));
                history.add(bid);
            }
            json.add("history", history);
        }
        if (!lot.isActive()) {
            json.addProperty("ago", Math.max(0L, now - lot.finishedAtMillis()));
        }
        return json;
    }

    /**
     * The document as text, dropping entries from the end of the named lists while it is too large for one packet (a
     * page of very large items).
     */
    private static String fit(JsonObject data, String... trimmable) {
        String text = data.toString();
        int guard = 0;
        while (text.length() > UiDataPacket.MAX_JSON - 1024 && guard++ < 200) {
            boolean trimmed = false;
            for (String key : trimmable) {
                if (data.has(key) && data.get(key).isJsonArray() && data.getAsJsonArray(key).size() > 0) {
                    data.getAsJsonArray(key).remove(data.getAsJsonArray(key).size() - 1);
                    trimmed = true;
                    break;
                }
            }
            if (!trimmed) {
                break;
            }
            data.addProperty("trimmed", true);
            text = data.toString();
        }
        return text;
    }
}
