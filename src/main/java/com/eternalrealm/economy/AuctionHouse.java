package com.eternalrealm.economy;

import com.eternalrealm.season.SeasonId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * The auction house (取引所) of one season, together with every player's {@link Mailbox}. Server thread only.
 *
 * <p>Listings and mailboxes live in one object and are saved as one {@link MarketData} document because every market
 * transaction moves value between them: a sale removes a listing and credits the seller's mailbox, a cancellation or
 * expiry moves the item from the listing into the mailbox. Saving them together makes each of those moves atomic on
 * disk. Gold is never created here: buyers pay with their {@link Wallet}, fees and taxes are destroyed (gold sinks).</p>
 *
 * <p><b>Integration flows</b> (all on the server thread, each in one task):</p>
 * <ul>
 *     <li>List: {@link #checkListing} → remove the item from the inventory → {@link #list} → store the returned wallet.</li>
 *     <li>Buy: {@link #buy} → store the returned wallet → give {@link Listing#itemData()} to the buyer (inventory, or
 *         {@link #sendItem} with {@link MailReason#AUCTION_PURCHASE} when it is full).</li>
 *     <li>Claim: {@link #claimAll}/{@link #claimGold}/{@link #claimItem} → store the wallet → give the items.</li>
 * </ul>
 * After each transaction save the market and the affected players promptly (see docs/modules/party.md: the wallet and
 * inventory are stored with the character, so the two stores are only consistent once both are written).
 *
 * <p><b>Persistence:</b> every change increments {@link #revision()}; save {@link #snapshot()} on the I/O thread and
 * report success with {@link #markSaved(long)}.</p>
 */
public final class AuctionHouse {
    private static final Logger LOG = LoggerFactory.getLogger(AuctionHouse.class);

    private final SeasonId season;
    private final RandomGenerator random;
    private AuctionRules rules;
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, Integer> listingCounts = new HashMap<>();
    private final Map<UUID, Mailbox> mailboxes = new LinkedHashMap<>();
    private final Set<UUID> mailItemIds = new HashSet<>();
    private long revision;
    private long savedRevision;

    /** @param stored the season's market from the {@link MarketRepository}, or {@link MarketData#empty} */
    public AuctionHouse(AuctionRules rules, RandomGenerator random, MarketData stored) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.random = Objects.requireNonNull(random, "random");
        this.season = stored.season();
        for (Listing listing : stored.listings()) {
            listings.put(listing.id(), listing);
            listingCounts.merge(listing.sellerId(), 1, Integer::sum);
        }
        for (Mailbox mailbox : stored.mailboxes()) {
            store(mailbox);
            for (MailItem item : mailbox.items()) {
                mailItemIds.add(item.id());
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ listing

    /**
     * Whether {@link #list} would succeed, without changing anything. Order: {@code INVALID_PRICE},
     * {@code TOO_MANY_LISTINGS}, {@code INSUFFICIENT_FUNDS} (for the listing fee).
     */
    public AuctionOutcome checkListing(UUID seller, long price, Wallet sellerWallet) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(sellerWallet, "sellerWallet");
        if (price < 1 || price > Listing.MAX_PRICE) {
            return AuctionOutcome.INVALID_PRICE;
        }
        if (activeListings(seller) >= rules.maxListingsPerPlayer()) {
            return AuctionOutcome.TOO_MANY_LISTINGS;
        }
        if (!sellerWallet.canAfford(rules.listingFee(price))) {
            return AuctionOutcome.INSUFFICIENT_FUNDS;
        }
        return AuctionOutcome.OK;
    }

    /** Fee for listing at {@code price} (valid price required), for confirmation dialogs. */
    public long listingFee(long price) {
        return rules.listingFee(price);
    }

    /**
     * Lists an item for {@link AuctionRules#listingDuration()} and charges the listing fee from {@code sellerWallet}.
     * The caller must already have removed the item from the seller's inventory (see the class comment).
     */
    public ListingResult list(UUID seller, String sellerName, String itemData, ItemInfo item, long price,
                              Wallet sellerWallet, long nowMillis) {
        Objects.requireNonNull(item, "item");
        AuctionOutcome check = checkListing(seller, price, sellerWallet);
        if (!check.isSuccess()) {
            return ListingResult.failure(check, sellerWallet);
        }
        long fee = rules.listingFee(price);
        Listing listing = new Listing(newListingId(), seller, sellerName, itemData, item, price, nowMillis,
                saturatedAdd(nowMillis, rules.listingDuration().toMillis()));
        Wallet charged = sellerWallet.debit(fee).wallet();
        listings.put(listing.id(), listing);
        listingCounts.merge(seller, 1, Integer::sum);
        revision++;
        LOG.info("Market [{}] listed {} by {}: {} x{} for {} G (fee {} G)", season, listing.id(), seller,
                item.itemId(), item.count(), price, fee);
        return new ListingResult(AuctionOutcome.OK, Optional.of(listing), fee, charged);
    }

    /**
     * Buys a listing: the buyer pays the price, the seller's mailbox receives price minus sales tax. Order:
     * {@code NOT_FOUND}, {@code OWN_LISTING}, {@code EXPIRED}, {@code INSUFFICIENT_FUNDS}.
     */
    public SaleResult buy(UUID buyer, UUID listingId, Wallet buyerWallet, long nowMillis) {
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(buyerWallet, "buyerWallet");
        Listing listing = listings.get(listingId);
        if (listing == null) {
            return SaleResult.failure(AuctionOutcome.NOT_FOUND, buyerWallet);
        }
        if (listing.sellerId().equals(buyer)) {
            return SaleResult.failure(AuctionOutcome.OWN_LISTING, buyerWallet);
        }
        if (listing.isExpired(nowMillis)) {
            return SaleResult.failure(AuctionOutcome.EXPIRED, buyerWallet);
        }
        WalletResult payment = buyerWallet.debit(listing.price());
        if (!payment.isSuccess()) {
            return SaleResult.failure(AuctionOutcome.INSUFFICIENT_FUNDS, buyerWallet);
        }
        long tax = rules.salesTax(listing.price());
        removeListing(listing);
        deposit(listing.sellerId(), listing.price() - tax);
        revision++;
        LOG.info("Market [{}] sold {} from {} to {}: {} x{} for {} G (tax {} G)", season, listing.id(),
                listing.sellerId(), buyer, listing.item().itemId(), listing.item().count(), listing.price(), tax);
        return new SaleResult(AuctionOutcome.OK, Optional.of(listing), listing.price(), tax, payment.wallet());
    }

    /** The seller withdraws a listing; the item goes to their mailbox. The listing fee is not refunded. */
    public CancelResult cancel(UUID seller, UUID listingId, long nowMillis) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(listingId, "listingId");
        Listing listing = listings.get(listingId);
        if (listing == null) {
            return new CancelResult(AuctionOutcome.NOT_FOUND, Optional.empty());
        }
        if (!listing.sellerId().equals(seller)) {
            return new CancelResult(AuctionOutcome.NOT_OWNER, Optional.empty());
        }
        removeListing(listing);
        deliver(seller, listing.itemData(), listing.item(), MailReason.AUCTION_CANCELLED, nowMillis);
        revision++;
        LOG.info("Market [{}] cancelled {} by {}", season, listing.id(), seller);
        return new CancelResult(AuctionOutcome.OK, Optional.of(listing));
    }

    /** Returns every listing expired at {@code nowMillis} to its seller's mailbox. Call periodically. */
    public List<Listing> expireListings(long nowMillis) {
        List<Listing> expired = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.isExpired(nowMillis)) {
                expired.add(listing);
            }
        }
        for (Listing listing : expired) {
            removeListing(listing);
            deliver(listing.sellerId(), listing.itemData(), listing.item(), MailReason.AUCTION_EXPIRED, nowMillis);
            LOG.info("Market [{}] expired {} of {}", season, listing.id(), listing.sellerId());
        }
        if (!expired.isEmpty()) {
            revision++;
        }
        return List.copyOf(expired);
    }

    // ------------------------------------------------------------------------------------------------ browsing

    /** Listings matching {@code query} that have not expired at {@code nowMillis}. */
    public Page<Listing> search(MarketQuery query, long nowMillis) {
        Objects.requireNonNull(query, "query");
        String needle = query.itemIdContains().toLowerCase(Locale.ROOT);
        List<Listing> matches = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (!listing.isExpired(nowMillis) && listing.item().itemId().contains(needle)) {
                matches.add(listing);
            }
        }
        matches.sort(query.sort().comparator());
        long from = (long) query.page() * query.pageSize();
        List<Listing> items = from >= matches.size()
                ? List.of()
                : matches.subList((int) from, (int) Math.min(matches.size(), from + query.pageSize()));
        return new Page<>(items, query.page(), query.pageSize(), matches.size());
    }

    /** The seller's listings (including expired ones not yet returned), oldest first. */
    public List<Listing> listingsOf(UUID seller) {
        List<Listing> result = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.sellerId().equals(seller)) {
                result.add(listing);
            }
        }
        return List.copyOf(result);
    }

    public Optional<Listing> listing(UUID listingId) {
        return Optional.ofNullable(listings.get(listingId));
    }

    public int activeListings(UUID seller) {
        return listingCounts.getOrDefault(seller, 0);
    }

    public int listingCount() {
        return listings.size();
    }

    // ------------------------------------------------------------------------------------------------ mail

    /** The player's mailbox (an empty one if nothing is waiting). */
    public Mailbox mailbox(UUID player) {
        Objects.requireNonNull(player, "player");
        Mailbox mailbox = mailboxes.get(player);
        return mailbox == null ? Mailbox.empty(player) : mailbox;
    }

    /** Mails gold to a player (rewards, refunds). The gold must come from a legitimate source. */
    public void sendGold(UUID player, long amount, String reason) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reason, "reason");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        deposit(player, amount);
        revision++;
        LOG.info("Market [{}] mailed {} G to {} ({})", season, amount, player, reason);
    }

    /** Mails an item to a player (purchases without inventory room, rewards). */
    public MailItem sendItem(UUID player, String itemData, ItemInfo item, MailReason reason, long nowMillis) {
        Objects.requireNonNull(player, "player");
        MailItem mail = deliver(player, itemData, item, reason, nowMillis);
        revision++;
        LOG.info("Market [{}] mailed {} x{} to {} ({})", season, item.itemId(), item.count(), player, reason.id());
        return mail;
    }

    /** Moves as much pending gold into the wallet as it can take; the rest stays in the mailbox. */
    public MailClaim claimGold(UUID player, Wallet wallet) {
        return claimAll(player, wallet, 0);
    }

    /** Removes one item from the mailbox; the integration must give it to the player (check room first). */
    public Optional<MailItem> claimItem(UUID player, UUID mailItemId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(mailItemId, "mailItemId");
        Mailbox mailbox = mailboxes.get(player);
        Optional<MailItem> item = mailbox == null ? Optional.empty() : mailbox.item(mailItemId);
        if (item.isPresent()) {
            store(mailbox.withoutItems(Set.of(mailItemId)));
            mailItemIds.remove(mailItemId);
            revision++;
            LOG.info("Market [{}] {} claimed mail item {}", season, player, mailItemId);
        }
        return item;
    }

    /**
     * Claims the pending gold (as much as the wallet can take) and up to {@code maxItems} items, oldest first.
     *
     * @param maxItems how many items the player can receive now (e.g. free inventory slots); 0 claims only gold
     */
    public MailClaim claimAll(UUID player, Wallet wallet, int maxItems) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(wallet, "wallet");
        if (maxItems < 0) {
            throw new IllegalArgumentException("maxItems must be >= 0: " + maxItems);
        }
        Mailbox mailbox = mailboxes.get(player);
        if (mailbox == null) {
            return new MailClaim(0L, List.of(), wallet);
        }
        long gold = Math.min(mailbox.gold(), wallet.capacity());
        List<MailItem> items = mailbox.items().subList(0, Math.min(maxItems, mailbox.items().size()));
        if (gold == 0 && items.isEmpty()) {
            return new MailClaim(0L, List.of(), wallet);
        }
        Set<UUID> claimedIds = new HashSet<>();
        for (MailItem item : items) {
            claimedIds.add(item.id());
        }
        store(mailbox.withGold(mailbox.gold() - gold).withoutItems(claimedIds));
        mailItemIds.removeAll(claimedIds);
        revision++;
        LOG.info("Market [{}] {} claimed {} G and {} mail items", season, player, gold, items.size());
        return new MailClaim(gold, items, wallet.credit(gold).wallet());
    }

    // ------------------------------------------------------------------------------------------------ persistence

    public SeasonId season() {
        return season;
    }

    /** Immutable copy of the whole market, the form to persist. */
    public MarketData snapshot() {
        return new MarketData(season, List.copyOf(listings.values()), List.copyOf(mailboxes.values()));
    }

    /** Incremented by every change of listings or mailboxes. */
    public long revision() {
        return revision;
    }

    public boolean isDirty() {
        return revision > savedRevision;
    }

    /** Records that the snapshot taken at {@code persistedRevision} has been stored. Never moves backwards. */
    public void markSaved(long persistedRevision) {
        if (persistedRevision > savedRevision) {
            savedRevision = persistedRevision;
        }
    }

    public AuctionRules rules() {
        return rules;
    }

    /** Applies reloaded rules to future operations; existing listings keep their expiry time. */
    public void setRules(AuctionRules newRules) {
        this.rules = Objects.requireNonNull(newRules, "newRules");
    }

    // ------------------------------------------------------------------------------------------------ internals

    private void removeListing(Listing listing) {
        listings.remove(listing.id());
        listingCounts.computeIfPresent(listing.sellerId(), (seller, count) -> count > 1 ? count - 1 : null);
    }

    private void deposit(UUID player, long amount) {
        store(mailbox(player).plusGold(amount));
    }

    private MailItem deliver(UUID player, String itemData, ItemInfo item, MailReason reason, long nowMillis) {
        MailItem mail = new MailItem(newMailItemId(), itemData, item, reason, nowMillis);
        store(mailbox(player).plusItem(mail));
        mailItemIds.add(mail.id());
        return mail;
    }

    /** Keeps only non-empty mailboxes, so the stored document does not grow with every player who ever traded. */
    private void store(Mailbox mailbox) {
        if (mailbox.isEmpty()) {
            mailboxes.remove(mailbox.owner());
        } else {
            mailboxes.put(mailbox.owner(), mailbox);
        }
    }

    private UUID newListingId() {
        UUID id;
        do {
            id = randomUuid();
        } while (listings.containsKey(id));
        return id;
    }

    private UUID newMailItemId() {
        UUID id;
        do {
            id = randomUuid();
        } while (mailItemIds.contains(id));
        return id;
    }

    /** Version-4 UUID from the injected generator (deterministic in tests). */
    private UUID randomUuid() {
        long most = (random.nextLong() & ~0xF000L) | 0x4000L;
        long least = (random.nextLong() & ~(0x3L << 62)) | (0x2L << 62);
        return new UUID(most, least);
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }
}
