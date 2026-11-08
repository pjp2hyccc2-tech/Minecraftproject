package com.eternalrealm.economy.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.economy.AuctionHouse;
import com.eternalrealm.economy.AuctionOutcome;
import com.eternalrealm.economy.AuctionRules;
import com.eternalrealm.economy.CancelResult;
import com.eternalrealm.economy.EconomyMessages;
import com.eternalrealm.economy.ItemInfo;
import com.eternalrealm.economy.Listing;
import com.eternalrealm.economy.ListingResult;
import com.eternalrealm.economy.MailClaim;
import com.eternalrealm.economy.MailItem;
import com.eternalrealm.economy.MailReason;
import com.eternalrealm.economy.Mailbox;
import com.eternalrealm.economy.MarketData;
import com.eternalrealm.economy.MarketRepository;
import com.eternalrealm.economy.SaleResult;
import com.eternalrealm.economy.Wallet;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.Inventories;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.season.SeasonId;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The auction house and mailboxes on the server: items are stored as their NBT text, gold moves between the character
 * and the economy module's wallets, expired listings go back to the sellers' mailboxes, and the market is season data
 * saved through the I/O executor. Bound equipment cannot be listed.
 */
public final class MarketService {
    private static final Logger LOG = LoggerFactory.getLogger(MarketService.class);
    private static final UUID IO_KEY = new UUID(0L, 0x6d61726b6574L);

    private final MinecraftServer server;
    private final PlayerDataService playerData;
    private final KeyedSerialExecutor<UUID> io;
    private final MarketRepository repository;
    private final Clock clock;
    private final SeasonId season;
    private AuctionHouse house;
    private boolean loaded;
    private boolean saveInFlight;

    public MarketService(MinecraftServer server, PlayerDataService playerData, KeyedSerialExecutor<UUID> io,
                         MarketRepository repository, Clock clock, SeasonId season) {
        this.server = server;
        this.playerData = playerData;
        this.io = io;
        this.repository = repository;
        this.clock = clock;
        this.season = season;
        this.house = new AuctionHouse(AuctionRules.defaults(), new SplittableRandom(), MarketData.empty(season));
        io.submit(IO_KEY, () -> {
            Optional<MarketData> data;
            try {
                data = repository.load(season);
            } catch (DataAccessException | RuntimeException e) {
                LOG.error("Could not load the market of {}; the auction house stays closed", season.value(), e);
                return null;
            }
            server.execute(() -> {
                data.ifPresent(stored -> house = new AuctionHouse(AuctionRules.defaults(), new SplittableRandom(), stored));
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

    private long now() {
        return clock.millis();
    }

    // ------------------------------------------------------------------ wallet bridge

    private long gold(ServerPlayer player) {
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

    // ------------------------------------------------------------------ actions

    /** Lists the stack in the main hand at {@code price} gold. */
    public void sellHeld(ServerPlayer player, long price) {
        if (!loaded) {
            closed(player);
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.auction.hold_item")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        Optional<EquipmentData> equipment = EquipmentStacks.read(held);
        if (equipment.isPresent() && !equipment.get().isTradeable()) {
            player.displayClientMessage(Component.translatable("tooltip.eternalrealm.equipment.bound")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        String data = held.save(new CompoundTag()).toString();
        if (data.length() > Listing.MAX_ITEM_DATA_LENGTH) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.auction.too_complex")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemInfo info = new ItemInfo(Inventories.idOf(held), held.getCount(),
                equipment.map(e -> e.rarity().id()).orElse("common"));
        long before = gold(player);
        AuctionOutcome check = house.checkListing(player.getUUID(), price, Wallet.of(before));
        if (check != AuctionOutcome.OK) {
            error(player, check);
            return;
        }
        Component name = held.getDisplayName();
        ItemStack removed = held.copy();
        player.getMainHandItem().setCount(0);
        ListingResult result = house.list(player.getUUID(), player.getGameProfile().getName(), data, info, price,
                Wallet.of(before), now());
        if (!result.isSuccess()) {
            Inventories.give(player, removed);
            error(player, result.outcome());
            return;
        }
        settle(player, before, result.wallet());
        player.sendSystemMessage(Component.translatable(EconomyMessages.AUCTION_LISTED, name, price, result.fee())
                .withStyle(ChatFormatting.GREEN));
        saveIfDirty();
    }

    public void buy(ServerPlayer player, UUID listingId) {
        if (!loaded) {
            closed(player);
            return;
        }
        long before = gold(player);
        SaleResult sale = house.buy(player.getUUID(), listingId, Wallet.of(before), now());
        if (!sale.isSuccess() || sale.listing().isEmpty()) {
            error(player, sale.outcome());
            return;
        }
        settle(player, before, sale.wallet());
        Listing listing = sale.listing().get();
        ItemStack stack = restore(listing.itemData());
        Component name = stack.isEmpty() ? Component.literal(listing.item().itemId()) : stack.getDisplayName();
        if (!stack.isEmpty() && player.getInventory().add(stack)) {
            player.sendSystemMessage(Component.translatable(EconomyMessages.AUCTION_BOUGHT, name, sale.price())
                    .withStyle(ChatFormatting.GREEN));
        } else {
            house.sendItem(player.getUUID(), listing.itemData(), listing.item(), MailReason.AUCTION_PURCHASE, now());
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
        if (!loaded) {
            closed(player);
            return;
        }
        CancelResult result = house.cancel(player.getUUID(), listingId, now());
        if (!result.isSuccess()) {
            error(player, result.outcome());
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
        if (!loaded) {
            closed(player);
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
    }

    /** Once per second: expired listings go back to their sellers' mailboxes. */
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
        saveIfDirty();
    }

    static ItemStack restore(String data) {
        try {
            return ItemStack.of(TagParser.parseTag(data));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static void error(ServerPlayer player, AuctionOutcome outcome) {
        outcome.messageKey().ifPresent(key -> player.displayClientMessage(Component.translatable(key)
                .withStyle(ChatFormatting.RED), true));
    }

    private static void closed(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.eternalrealm.auction.closed")
                .withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ persistence

    private void saveIfDirty() {
        if (!loaded || saveInFlight || !house.isDirty()) {
            return;
        }
        MarketData snapshot = house.snapshot();
        long revision = house.revision();
        AuctionHouse saving = house;
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
                    saving.markSaved(revision);
                }
            });
            return null;
        });
    }

    public void flush() {
        if (!loaded || !house.isDirty()) {
            return;
        }
        try {
            repository.save(house.snapshot());
            house.markSaved(house.revision());
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Could not save the market at shutdown", e);
        }
    }
}
