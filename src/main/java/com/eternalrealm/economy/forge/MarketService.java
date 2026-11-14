package com.eternalrealm.economy.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.economy.AuctionHall;
import com.eternalrealm.economy.AuctionHouse;
import com.eternalrealm.economy.AuctionOutcome;
import com.eternalrealm.economy.AuctionRules;
import com.eternalrealm.economy.Bid;
import com.eternalrealm.economy.CancelResult;
import com.eternalrealm.economy.EconomyMessages;
import com.eternalrealm.economy.HallEvent;
import com.eternalrealm.economy.HallOutcome;
import com.eternalrealm.economy.HallResult;
import com.eternalrealm.economy.HallRules;
import com.eternalrealm.economy.ItemInfo;
import com.eternalrealm.economy.Listing;
import com.eternalrealm.economy.ListingResult;
import com.eternalrealm.economy.Lot;
import com.eternalrealm.economy.LotState;
import com.eternalrealm.economy.MailClaim;
import com.eternalrealm.economy.MailItem;
import com.eternalrealm.economy.MailReason;
import com.eternalrealm.economy.Mailbox;
import com.eternalrealm.economy.MarketData;
import com.eternalrealm.economy.MarketRepository;
import com.eternalrealm.economy.PriceHistory;
import com.eternalrealm.economy.SaleResult;
import com.eternalrealm.economy.Wallet;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.Inventories;
import com.eternalrealm.npc.forge.NpcRole;
import com.eternalrealm.npc.forge.Npcs;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.season.SeasonId;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The market on the server (docs/modules/market.md): the trading board (掲示板, fixed prices), the auction hall
 * (オークション会場, live bids; D-AUCTION), the price history and the mailboxes. Items are stored as their NBT text,
 * gold moves between the characters and the economy module's wallets, and everything is season data saved together
 * through the I/O executor. Listing on the board and registering or starting a lot happen at the hall in the capital
 * (an auction clerk within {@value #HALL_REACH} blocks); bidding works from anywhere. Bound equipment is never traded.
 */
public final class MarketService {
    private static final Logger LOG = LoggerFactory.getLogger(MarketService.class);
    private static final UUID IO_KEY = new UUID(0L, 0x6d61726b6574L);
    /** How close to an auction clerk (NpcRole.AUCTION) a player must be to use the hall's counter. */
    public static final double HALL_REACH = 12.0;

    private final MinecraftServer server;
    private final PlayerDataService playerData;
    private final KeyedSerialExecutor<UUID> io;
    private final MarketRepository repository;
    private final Clock clock;
    private final SeasonId season;
    private AuctionHouse house;
    private AuctionHall hall;
    private PriceHistory prices;
    private long savedPricesRevision;
    private boolean loaded;
    private boolean saveInFlight;
    /** How far GameTests moved the market's clock forward ({@link #advanceClockForTests}). */
    private long clockOffsetMillis;
    /** Players with the auction screen open: they get every change. */
    private final Set<UUID> hallViewers = new LinkedHashSet<>();

    public MarketService(MinecraftServer server, PlayerDataService playerData, KeyedSerialExecutor<UUID> io,
                         MarketRepository repository, Clock clock, SeasonId season) {
        this.server = server;
        this.playerData = playerData;
        this.io = io;
        this.repository = repository;
        this.clock = clock;
        this.season = season;
        this.house = new AuctionHouse(AuctionRules.defaults(), new SplittableRandom(), MarketData.empty(season));
        this.hall = new AuctionHall(HallRules.defaults(), new SplittableRandom(), List.of(), 0L);
        this.prices = PriceHistory.empty();
        io.submit(IO_KEY, () -> {
            Optional<MarketData> data;
            try {
                data = repository.load(season);
            } catch (DataAccessException | RuntimeException e) {
                LOG.error("Could not load the market of {}; the board and the hall stay closed", season.value(), e);
                return null;
            }
            server.execute(() -> {
                data.ifPresent(stored -> {
                    house = new AuctionHouse(AuctionRules.defaults(), new SplittableRandom(), stored);
                    long downtime = stored.savedAtMillis() > 0 ? Math.max(0L, now() - stored.savedAtMillis()) : 0L;
                    hall = new AuctionHall(HallRules.defaults(), new SplittableRandom(), stored.lots(), downtime);
                    prices = new PriceHistory(stored.prices());
                    savedPricesRevision = prices.revision();
                });
                loaded = true;
            });
            return null;
        });
    }

    public boolean isOpen() {
        return loaded;
    }

    public AuctionHouse house() {
        return house;
    }

    public AuctionHall hall() {
        return hall;
    }

    public PriceHistory prices() {
        return prices;
    }

    long now() {
        return clock.millis() + clockOffsetMillis;
    }

    /**
     * Moves the market's time forward by {@code millis}, so a GameTest can end a lot without waiting for it. Never
     * used by the game itself.
     */
    public void advanceClockForTests(long millis) {
        if (millis > 0) {
            clockOffsetMillis += millis;
        }
    }

    // ------------------------------------------------------------------ wallet bridge

    long gold(ServerPlayer player) {
        return playerData.snapshot(player.getUUID()).map(CharacterSnapshot::gold).orElse(0L);
    }

    /** Applies the wallet the economy module returned to the character's gold. */
    private void settle(ServerPlayer player, long before, Wallet after) {
        long difference = after.balance() - before;
        if (difference > 0) {
            playerData.grantGold(player.getUUID(), difference);
        } else if (difference < 0) {
            playerData.spendGold(player.getUUID(), -difference);
        }
    }

    /** Gold to a player: straight to an online character, the rest (or all, when away) to the mailbox. */
    private void creditGold(UUID player, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        long added = server.getPlayerList().getPlayer(player) == null ? 0L
                : playerData.grantGold(player, amount).orElse(0L);
        if (added < amount) {
            house.sendGold(player, amount - added, reason);
        }
    }

    /** An item to a player: into the bag of an online player with room, else to the mailbox. */
    private boolean deliver(UUID player, String itemData, ItemInfo item, MailReason reason) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        ItemStack stack = restore(itemData);
        if (online != null && !stack.isEmpty() && com.eternalrealm.item.forge.Bags.fits(online, stack)) {
            com.eternalrealm.item.forge.Bags.give(online, stack);
            return true;
        }
        house.sendItem(player, itemData, item, reason, now());
        return false;
    }

    // ------------------------------------------------------------------ the hall's counter

    /** Whether the player stands at the auction hall (an auction clerk is near). */
    public boolean atHall(ServerPlayer player) {
        return !player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(HALL_REACH),
                mob -> Npcs.roleOf(mob).filter(role -> role == NpcRole.AUCTION).isPresent()).isEmpty();
    }

    /**
     * Takes {@code count} pieces from bag slot {@code slot} for the market, if they may be traded.
     *
     * @return the taken stack, or empty (the player was told why)
     */
    private Optional<ItemStack> takeFromBag(ServerPlayer player, int slot, int count) {
        if (slot < Bag.FIRST_SLOT || slot > Bag.LAST_SLOT) {
            return Optional.empty();
        }
        ItemStack held = player.getInventory().items.get(slot);
        if (held.isEmpty() || count < 1 || count > held.getCount()) {
            notice(player, "message.eternalrealm.market.no_item", ChatFormatting.RED);
            return Optional.empty();
        }
        Optional<EquipmentData> equipment = EquipmentStacks.read(held);
        if (equipment.isPresent() && !equipment.get().isTradeable()) {
            notice(player, "tooltip.eternalrealm.equipment.bound", ChatFormatting.RED);
            return Optional.empty();
        }
        ItemStack taken = held.copy();
        taken.setCount(count);
        if (taken.save(new CompoundTag()).toString().length() > Listing.MAX_ITEM_DATA_LENGTH) {
            notice(player, "message.eternalrealm.auction.too_complex", ChatFormatting.RED);
            return Optional.empty();
        }
        return Optional.of(taken);
    }

    private static void removeFromBag(ServerPlayer player, int slot, int count) {
        ItemStack held = player.getInventory().items.get(slot);
        held.shrink(count);
        if (held.isEmpty()) {
            player.getInventory().items.set(slot, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
    }

    // ------------------------------------------------------------------ the board

    /** Lists {@code count} pieces of bag slot {@code slot} on the board for {@code price} gold (at the hall). */
    public void listFromBag(ServerPlayer player, int slot, int count, long price) {
        if (!ready(player) || !requireHall(player)) {
            return;
        }
        Optional<ItemStack> taken = takeFromBag(player, slot, count);
        if (taken.isEmpty()) {
            return;
        }
        long before = gold(player);
        AuctionOutcome check = house.checkListing(player.getUUID(), price, Wallet.of(before));
        if (check != AuctionOutcome.OK) {
            error(player, check, check == AuctionOutcome.INVALID_PRICE ? 0L : house.listingFee(price));
            return;
        }
        ItemStack stack = taken.get();
        String data = stack.save(new CompoundTag()).toString();
        ItemInfo info = MarketItems.describe(stack, count);
        removeFromBag(player, slot, count);
        ListingResult result = house.list(player.getUUID(), player.getGameProfile().getName(), data, info, price,
                Wallet.of(before), now());
        if (!result.isSuccess()) {
            Inventories.give(player, stack);
            error(player, result.outcome(), result.outcome() == AuctionOutcome.INVALID_PRICE ? 0L
                    : house.listingFee(price));
            return;
        }
        settle(player, before, result.wallet());
        player.sendSystemMessage(Component.translatable(EconomyMessages.AUCTION_LISTED, stack.getDisplayName(), price,
                result.fee()).withStyle(ChatFormatting.GREEN));
        saveIfDirty();
    }

    public void buy(ServerPlayer player, UUID listingId) {
        if (!ready(player) || !requireHall(player)) {
            return;
        }
        long before = gold(player);
        long asked = house.listing(listingId).map(Listing::price).orElse(0L);
        SaleResult sale = house.buy(player.getUUID(), listingId, Wallet.of(before), now());
        if (!sale.isSuccess() || sale.listing().isEmpty()) {
            error(player, sale.outcome(), asked);
            return;
        }
        settle(player, before, sale.wallet());
        Listing listing = sale.listing().get();
        prices.record(listing.item().group(), sale.price(), listing.item().count(), now(), false);
        ItemStack stack = restore(listing.itemData());
        Component name = stack.isEmpty() ? Component.literal(listing.item().itemId()) : stack.getDisplayName();
        if (deliver(player.getUUID(), listing.itemData(), listing.item(), MailReason.AUCTION_PURCHASE)) {
            player.sendSystemMessage(Component.translatable(EconomyMessages.AUCTION_BOUGHT, name, sale.price())
                    .withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.translatable(EconomyMessages.AUCTION_BOUGHT_TO_MAILBOX, name)
                    .withStyle(ChatFormatting.YELLOW));
        }
        ServerPlayer seller = server.getPlayerList().getPlayer(listing.sellerId());
        if (seller != null) {
            seller.sendSystemMessage(Component.translatable(EconomyMessages.AUCTION_SOLD, name, sale.price(),
                    sale.sellerProceeds()).withStyle(ChatFormatting.GOLD));
        }
        saveIfDirty();
    }

    public void cancel(ServerPlayer player, UUID listingId) {
        if (!ready(player)) {
            return;
        }
        CancelResult result = house.cancel(player.getUUID(), listingId, now());
        if (!result.isSuccess()) {
            error(player, result.outcome(), 0L);
            return;
        }
        result.listing().ifPresent(listing -> player.sendSystemMessage(Component.translatable(
                EconomyMessages.AUCTION_CANCELLED, restore(listing.itemData()).getDisplayName())
                .withStyle(ChatFormatting.GRAY)));
        saveIfDirty();
    }

    public Mailbox mailbox(ServerPlayer player) {
        return house.mailbox(player.getUUID());
    }

    /** Takes one mailed item (or the gold when {@code itemId} is null) into the inventory. */
    public void claim(ServerPlayer player, UUID itemId) {
        if (!ready(player)) {
            return;
        }
        if (itemId == null) {
            long before = gold(player);
            MailClaim claim = house.claimGold(player.getUUID(), Wallet.of(before));
            settle(player, before, claim.wallet());
            player.sendSystemMessage(Component.translatable(EconomyMessages.MAIL_CLAIMED, claim.gold(), 0)
                    .withStyle(ChatFormatting.GREEN));
        } else {
            Optional<MailItem> item = house.claimItem(player.getUUID(), itemId);
            item.ifPresent(mail -> {
                Inventories.give(player, restore(mail.itemData()));
                player.sendSystemMessage(Component.translatable(EconomyMessages.MAIL_CLAIMED, 0, 1)
                        .withStyle(ChatFormatting.GREEN));
            });
        }
        saveIfDirty();
    }

    /** At login and when opening the market: tell about waiting mail. */
    public void remind(ServerPlayer player) {
        if (!loaded) {
            return;
        }
        Mailbox box = house.mailbox(player.getUUID());
        if (!box.isEmpty()) {
            player.sendSystemMessage(Component.translatable(EconomyMessages.MAIL_WAITING, box.gold(), box.items().size())
                    .withStyle(ChatFormatting.AQUA));
        }
        List<Lot> leading = hall.leading(player.getUUID());
        if (!leading.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.eternalrealm.hall.leading", leading.size())
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    // ------------------------------------------------------------------ the auction hall

    /** Registers {@code count} pieces of bag slot {@code slot} as a lot (a draft; at the hall). */
    public void register(ServerPlayer player, int slot, int count, long startPrice, long buyoutPrice, int minutes) {
        if (!ready(player) || !requireHall(player)) {
            return;
        }
        HallOutcome check = hall.checkRegistration(player.getUUID(), startPrice, buyoutPrice, minutes);
        if (check != HallOutcome.OK) {
            hallError(player, check, null, 0L);
            return;
        }
        Optional<ItemStack> taken = takeFromBag(player, slot, count);
        if (taken.isEmpty()) {
            return;
        }
        ItemStack stack = taken.get();
        String data = stack.save(new CompoundTag()).toString();
        removeFromBag(player, slot, count);
        HallResult result = hall.register(player.getUUID(), player.getGameProfile().getName(), data,
                MarketItems.describe(stack, count), startPrice, buyoutPrice, minutes, now());
        if (!result.isSuccess()) {
            Inventories.give(player, stack);
            hallError(player, result.outcome(), null, 0L);
            return;
        }
        player.sendSystemMessage(Component.translatable("message.eternalrealm.hall.registered", stack.getDisplayName())
                .withStyle(ChatFormatting.GREEN));
        afterHall(List.of());
    }

    /** Starts a draft (at the hall): live at once, or in the queue when five lots run. */
    public void start(ServerPlayer player, UUID lotId) {
        if (!ready(player) || !requireHall(player)) {
            return;
        }
        long before = gold(player);
        HallResult result = hall.start(player.getUUID(), lotId, Wallet.of(before), now());
        if (!result.isSuccess()) {
            hallError(player, result.outcome(), lotId, hall.lot(lotId).map(lot -> hall.rules().fee(lot.startPrice()))
                    .orElse(0L));
            return;
        }
        settle(player, before, result.wallet());
        afterHall(result.events());
    }

    /** Withdraws a lot (anywhere): a draft or a queued one, or a running one nobody has bid on. */
    public void withdraw(ServerPlayer player, UUID lotId) {
        if (!ready(player)) {
            return;
        }
        HallResult result = hall.withdraw(player.getUUID(), lotId, now());
        if (!result.isSuccess()) {
            hallError(player, result.outcome(), lotId, 0L);
            return;
        }
        afterHall(result.events());
    }

    /** A bid from anywhere. */
    public void bid(ServerPlayer player, UUID lotId, long amount) {
        if (!ready(player)) {
            return;
        }
        long before = gold(player);
        HallResult result = hall.bid(player.getUUID(), player.getGameProfile().getName(), lotId, amount,
                Wallet.of(before), now());
        if (!result.isSuccess()) {
            hallError(player, result.outcome(), lotId, amount);
            return;
        }
        settle(player, before, result.wallet());
        Lot lot = result.lot().orElseThrow();
        player.sendSystemMessage(Component.translatable(lot.state() == LotState.SOLD
                        ? "message.eternalrealm.hall.bought_out" : "message.eternalrealm.hall.bid_placed",
                name(lot), lot.currentPrice()).withStyle(ChatFormatting.GREEN));
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        afterHall(result.events());
    }

    /** Buys a running lot outright at its buyout price (a bid of that amount ends it at once). */
    public void buyout(ServerPlayer player, UUID lotId) {
        if (!ready(player)) {
            return;
        }
        Optional<Lot> lot = hall.lot(lotId).filter(Lot::isActive);
        if (lot.isEmpty()) {
            hallError(player, HallOutcome.NOT_FOUND, lotId, 0L);
            return;
        }
        if (lot.get().buyoutPrice() == 0) {
            hallError(player, HallOutcome.NO_BUYOUT, lotId, 0L);
            return;
        }
        bid(player, lotId, lot.get().buyoutPrice());
    }

    /** The auction screen is open: send it the hall now and after every change. */
    public void watch(ServerPlayer player) {
        hallViewers.add(player.getUUID());
        MarketScreens.sendHall(player, this, true);
    }

    public void unwatch(ServerPlayer player) {
        hallViewers.remove(player.getUUID());
    }

    public void onLogout(ServerPlayer player) {
        hallViewers.remove(player.getUUID());
    }

    /** Carries out what the hall did, tells the players, saves and refreshes open screens. */
    private void afterHall(List<HallEvent> events) {
        for (HallEvent event : events) {
            carryOut(event);
        }
        saveIfDirty();
        for (UUID viewer : new java.util.ArrayList<>(hallViewers)) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewer);
            if (player == null) {
                hallViewers.remove(viewer);
            } else {
                MarketScreens.sendHall(player, this, false);
            }
        }
    }

    private void carryOut(HallEvent event) {
        if (event instanceof HallEvent.Refund refund) {
            creditGold(refund.player(), refund.gold(), "auction refund");
            tell(refund.player(), Component.translatable("message.eternalrealm.hall.refund", refund.gold())
                    .withStyle(ChatFormatting.YELLOW));
        } else if (event instanceof HallEvent.Payout payout) {
            creditGold(payout.player(), payout.gold(), "auction proceeds");
            hall.lot(payout.lotId()).ifPresent(lot -> tell(payout.player(), Component.translatable(
                    "message.eternalrealm.hall.sold", name(lot), lot.currentPrice(), payout.gold(), payout.tax())
                    .withStyle(ChatFormatting.GOLD)));
        } else if (event instanceof HallEvent.Deliver delivery) {
            boolean inBag = deliver(delivery.player(), delivery.itemData(), delivery.item(),
                    delivery.won() ? MailReason.AUCTION_WON : MailReason.AUCTION_RETURNED);
            Component item = restore(delivery.itemData()).getDisplayName();
            String key = delivery.won() ? (inBag ? "message.eternalrealm.hall.won" : "message.eternalrealm.hall.won_mail")
                    : (inBag ? "message.eternalrealm.hall.returned" : "message.eternalrealm.hall.returned_mail");
            tell(delivery.player(), Component.translatable(key, item).withStyle(delivery.won() ? ChatFormatting.GOLD
                    : ChatFormatting.GRAY));
        } else if (event instanceof HallEvent.Started started) {
            Lot lot = started.lot();
            net.minecraft.network.chat.MutableComponent line = lot.buyoutPrice() > 0
                    ? Component.translatable("message.eternalrealm.hall.started_buyout", name(lot), lot.startPrice(),
                    lot.buyoutPrice(), lot.durationMinutes())
                    : Component.translatable("message.eternalrealm.hall.started", name(lot), lot.startPrice(),
                    lot.durationMinutes());
            broadcast(line.withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (event instanceof HallEvent.Queued queued) {
            tell(queued.lot().seller(), Component.translatable("message.eternalrealm.hall.queued", name(queued.lot()),
                    queued.position()).withStyle(ChatFormatting.YELLOW));
        } else if (event instanceof HallEvent.Outbid outbid) {
            ServerPlayer player = server.getPlayerList().getPlayer(outbid.player());
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.eternalrealm.hall.outbid", name(outbid.lot()),
                        outbid.lot().currentPrice()).withStyle(ChatFormatting.RED));
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
            }
        } else if (event instanceof HallEvent.BidPlaced placed) {
            tell(placed.lot().seller(), Component.translatable(placed.extended()
                            ? "message.eternalrealm.hall.new_bid_extended" : "message.eternalrealm.hall.new_bid",
                    name(placed.lot()), placed.lot().currentPrice()).withStyle(ChatFormatting.AQUA));
        } else if (event instanceof HallEvent.EndingSoon soon) {
            Set<UUID> told = new HashSet<>();
            told.add(soon.lot().seller());
            for (Bid bid : soon.lot().bids()) {
                told.add(bid.bidder());
            }
            for (UUID player : told) {
                tell(player, Component.translatable("message.eternalrealm.hall.ending_soon", name(soon.lot()),
                        soon.lot().currentPrice()).withStyle(ChatFormatting.YELLOW));
            }
        } else if (event instanceof HallEvent.Ended ended) {
            Lot lot = ended.lot();
            if (lot.state() == LotState.SOLD) {
                prices.record(lot.item().group(), lot.currentPrice(), lot.item().count(), now(), true);
                broadcast(Component.translatable("message.eternalrealm.hall.result_sold", name(lot),
                        lot.currentPrice(), lot.highest().map(Bid::bidderName).orElse("?"))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            } else if (lot.state() == LotState.UNSOLD) {
                tell(lot.seller(), Component.translatable("message.eternalrealm.hall.unsold", name(lot))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private Component name(Lot lot) {
        ItemStack stack = restore(lot.itemData());
        return stack.isEmpty() ? Component.literal(lot.item().itemId()) : stack.getDisplayName();
    }

    private void tell(UUID player, Component message) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.sendSystemMessage(message);
        }
    }

    private void broadcast(Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    // ------------------------------------------------------------------ time

    /** Once per second: expired board listings go back, lots end and queued lots go live. */
    public void tickSecond() {
        if (!loaded) {
            return;
        }
        List<Listing> expired = house.expireListings(now());
        for (Listing listing : expired) {
            ServerPlayer seller = server.getPlayerList().getPlayer(listing.sellerId());
            if (seller != null) {
                seller.sendSystemMessage(Component.translatable(EconomyMessages.AUCTION_EXPIRED,
                        restore(listing.itemData()).getDisplayName()).withStyle(ChatFormatting.GRAY));
            }
        }
        List<HallEvent> events = hall.tick(now());
        if (!events.isEmpty()) {
            afterHall(events);
        } else {
            saveIfDirty();
        }
    }

    // ------------------------------------------------------------------ helpers

    static ItemStack restore(String data) {
        try {
            return ItemStack.of(TagParser.parseTag(data));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private boolean ready(ServerPlayer player) {
        if (!loaded) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.auction.closed")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    private boolean requireHall(ServerPlayer player) {
        if (atHall(player)) {
            return true;
        }
        notice(player, "message.eternalrealm.hall.not_at_hall", ChatFormatting.RED);
        return false;
    }

    private static void notice(ServerPlayer player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }

    /** A board refusal; {@code needed} is the gold the action needed (shown when the wallet was short). */
    private void error(ServerPlayer player, AuctionOutcome outcome, long needed) {
        Object argument = switch (outcome) {
            case INVALID_PRICE -> String.format("%,d", Listing.MAX_PRICE);
            case TOO_MANY_LISTINGS -> house.rules().maxListingsPerPlayer();
            case INSUFFICIENT_FUNDS -> String.format("%,d", needed);
            default -> "";
        };
        outcome.messageKey().ifPresent(key -> player.displayClientMessage(Component.translatable(key, argument)
                .withStyle(ChatFormatting.RED), true));
    }

    /**
     * A hall refusal, with the number that explains it ({@code needed}: the gold the action needed, for a short wallet),
     * and a fresh hall for an open screen, which may have shown an old price.
     */
    private void hallError(ServerPlayer player, HallOutcome outcome, UUID lotId, long needed) {
        Object argument = switch (outcome) {
            case INVALID_PRICE -> String.format("%,d", Listing.MAX_PRICE);
            case TOO_MANY_LOTS -> hall.rules().maxLotsPerPlayer();
            case QUEUE_FULL -> hall.rules().maxQueued();
            case BID_TOO_LOW -> String.format("%,d", lotId == null ? 0L
                    : hall.lot(lotId).filter(Lot::isActive).map(hall::nextMinimum).orElse(0L));
            case INSUFFICIENT_FUNDS -> String.format("%,d", needed);
            default -> "";
        };
        outcome.messageKey().ifPresent(key -> player.displayClientMessage(Component.translatable(key, argument)
                .withStyle(ChatFormatting.RED), true));
        if (hallViewers.contains(player.getUUID())) {
            MarketScreens.sendHall(player, this, false);
        }
    }

    /** Whether the whole stack fits into the bag's front (slots 1-35; slot 0 is the equipped weapon). */
    static boolean fitsInBag(net.minecraft.world.entity.player.Inventory inventory, ItemStack stack) {
        int room = 0;
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            ItemStack held = inventory.items.get(slot);
            if (held.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (held.isStackable() && ItemStack.isSameItemSameTags(held, stack)) {
                room += held.getMaxStackSize() - held.getCount();
            }
            if (room >= stack.getCount()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ persistence

    private boolean dirty() {
        return house.isDirty() || hall.isDirty() || prices.revision() != savedPricesRevision;
    }

    private MarketData snapshot() {
        return house.snapshot().with(hall.snapshot(), prices.snapshot(), now());
    }

    private void saveIfDirty() {
        if (!loaded || saveInFlight || !dirty()) {
            return;
        }
        MarketData snapshot = snapshot();
        long houseRevision = house.revision();
        long hallRevision = hall.revision();
        long pricesRevision = prices.revision();
        AuctionHouse savingHouse = house;
        AuctionHall savingHall = hall;
        saveInFlight = true;
        io.submit(IO_KEY, () -> {
            boolean ok;
            try {
                repository.save(snapshot);
                ok = true;
            } catch (DataAccessException | RuntimeException e) {
                LOG.error("Saving the market failed; will retry", e);
                ok = false;
            }
            boolean saved = ok;
            server.execute(() -> {
                saveInFlight = false;
                if (saved) {
                    savingHouse.markSaved(houseRevision);
                    savingHall.markSaved(hallRevision);
                    savedPricesRevision = Math.max(savedPricesRevision, pricesRevision);
                }
            });
            return null;
        });
    }

    public void flush() {
        if (!loaded || !dirty()) {
            return;
        }
        try {
            repository.save(snapshot());
            house.markSaved(house.revision());
            hall.markSaved(hall.revision());
            savedPricesRevision = prices.revision();
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Could not save the market at shutdown", e);
        }
    }
}
