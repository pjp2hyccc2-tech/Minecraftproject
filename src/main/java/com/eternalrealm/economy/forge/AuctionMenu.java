package com.eternalrealm.economy.forge;

import com.eternalrealm.economy.Listing;
import com.eternalrealm.economy.MailItem;
import com.eternalrealm.economy.Mailbox;
import com.eternalrealm.economy.MarketQuery;
import com.eternalrealm.economy.Page;
import com.eternalrealm.menu.MenuItems;
import com.eternalrealm.menu.ServerMenu;
import com.eternalrealm.server.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The auction house (browse with paging and sorting; shift-click buys, shift-click on an own listing cancels it;
 * selling is {@code /auction sell <price>} with the item in hand) and the mailbox (click a letter to take it).
 */
public final class AuctionMenu {
    private static final int PAGE_SIZE = 45;

    private static final class State {
        int page;
        MarketQuery.Sort sort = MarketQuery.Sort.NEWEST;
        boolean mine;
    }

    private AuctionMenu() {
    }

    public static void open(ServerPlayer player) {
        MarketService market = market();
        if (market == null) {
            return;
        }
        market.remind(player);
        State state = new State();
        ServerMenu.open(player, Component.translatable("screen.eternalrealm.auction.title"), 6, menu -> fill(menu, state));
    }

    public static void openMailbox(ServerPlayer player) {
        if (market() == null) {
            return;
        }
        ServerMenu.open(player, Component.translatable("screen.eternalrealm.mail.title"), 6, AuctionMenu::fillMail);
    }

    private static MarketService market() {
        return ServerContext.current().map(context -> context.game().market()).orElse(null);
    }

    private static void fill(ServerMenu menu, State state) {
        MarketService market = market();
        ServerPlayer player = menu.viewer();
        if (market == null) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Listing> listings;
        int totalPages;
        if (state.mine) {
            listings = market.house().listingsOf(player.getUUID());
            totalPages = 1;
        } else {
            Page<Listing> page = market.house().search(new MarketQuery("", state.sort, state.page, PAGE_SIZE), now);
            listings = page.items();
            totalPages = Math.max(1, page.totalPages());
        }
        for (int i = 0; i < Math.min(PAGE_SIZE, listings.size()); i++) {
            Listing listing = listings.get(i);
            boolean own = listing.sellerId().equals(player.getUUID());
            ItemStack stack = MarketService.restore(listing.itemData());
            if (stack.isEmpty()) {
                stack = new ItemStack(Items.BARRIER);
            }
            List<Component> lore = new ArrayList<>();
            lore.add(Component.translatable("screen.eternalrealm.auction.price", listing.price()).withStyle(ChatFormatting.GOLD));
            lore.add(Component.translatable("screen.eternalrealm.auction.seller", listing.sellerName()));
            long minutes = Math.max(0, (listing.expiresAtMillis() - now) / 60_000);
            lore.add(Component.translatable("screen.eternalrealm.auction.time_left",
                    String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60)).withStyle(ChatFormatting.DARK_GRAY));
            lore.add(Component.translatable(own ? "screen.eternalrealm.auction.cancel_hint" : "screen.eternalrealm.auction.buy_hint")
                    .withStyle(ChatFormatting.YELLOW));
            ItemStack icon = MenuItems.icon(stack, stack.getHoverName(), lore, false);
            menu.set(i, icon, (p, button, shift) -> {
                if (!shift) {
                    return;
                }
                if (own) {
                    market.cancel(p, listing.id());
                } else {
                    market.buy(p, listing.id());
                }
                menu.refresh();
            });
        }
        for (int slot = 45; slot < 54; slot++) {
            menu.set(slot, MenuItems.filler(), null);
        }
        if (!state.mine && state.page > 0) {
            menu.set(45, MenuItems.icon(Items.ARROW, Component.translatable("screen.eternalrealm.auction.previous"),
                    List.of()), (p, button, shift) -> {
                state.page--;
                menu.refresh();
            });
        }
        menu.set(47, MenuItems.icon(Items.COMPARATOR, Component.translatable("screen.eternalrealm.auction.sort."
                + state.sort.name().toLowerCase(Locale.ROOT)), List.of(Component.translatable(
                "screen.eternalrealm.auction.sort_hint"))), (p, button, shift) -> {
            MarketQuery.Sort[] sorts = MarketQuery.Sort.values();
            state.sort = sorts[(state.sort.ordinal() + 1) % sorts.length];
            state.page = 0;
            menu.refresh();
        });
        menu.set(49, MenuItems.icon(Items.PAPER, Component.translatable("screen.eternalrealm.auction.page",
                state.page + 1, totalPages), List.of(Component.translatable("screen.eternalrealm.auction.sell_hint"))),
                null);
        menu.set(51, MenuItems.icon(state.mine ? Items.CHEST : Items.ENDER_CHEST, Component.translatable(state.mine
                ? "screen.eternalrealm.auction.all" : "screen.eternalrealm.auction.mine"), List.of()), (p, button, shift) -> {
            state.mine = !state.mine;
            state.page = 0;
            menu.refresh();
        });
        menu.set(52, MenuItems.icon(Items.WRITABLE_BOOK, Component.translatable("screen.eternalrealm.mail.title"),
                List.of()), (p, button, shift) -> openMailbox(p));
        if (!state.mine && state.page + 1 < totalPages) {
            menu.set(53, MenuItems.icon(Items.ARROW, Component.translatable("screen.eternalrealm.auction.next"),
                    List.of()), (p, button, shift) -> {
                state.page++;
                menu.refresh();
            });
        }
    }

    private static void fillMail(ServerMenu menu) {
        MarketService market = market();
        if (market == null) {
            return;
        }
        Mailbox box = market.mailbox(menu.viewer());
        int slot = 0;
        if (box.gold() > 0) {
            menu.set(slot++, MenuItems.icon(Items.GOLD_INGOT, Component.translatable("screen.eternalrealm.mail.gold",
                    box.gold()).withStyle(ChatFormatting.GOLD), List.of(Component.translatable(
                    "screen.eternalrealm.mail.claim_hint"))), (p, button, shift) -> {
                market.claim(p, null);
                menu.refresh();
            });
        }
        for (MailItem item : box.items()) {
            if (slot >= 54) {
                break;
            }
            ItemStack stack = MarketService.restore(item.itemData());
            if (stack.isEmpty()) {
                stack = new ItemStack(Items.BARRIER);
            }
            menu.set(slot++, MenuItems.icon(stack, stack.getHoverName(), List.of(Component.translatable(
                    item.reason().translationKey()).withStyle(ChatFormatting.GRAY), Component.translatable(
                    "screen.eternalrealm.mail.claim_hint")), false), (p, button, shift) -> {
                market.claim(p, item.id());
                menu.refresh();
            });
        }
        if (slot == 0) {
            menu.set(22, MenuItems.icon(Items.PAPER, Component.translatable("screen.eternalrealm.mail.empty"), List.of()),
                    null);
        }
    }
}
