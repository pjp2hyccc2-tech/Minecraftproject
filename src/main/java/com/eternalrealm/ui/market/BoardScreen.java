package com.eternalrealm.ui.market;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.economy.ItemInfo;
import com.eternalrealm.economy.MarketQuery;
import com.eternalrealm.economy.forge.MarketScreens;
import com.eternalrealm.item.bag.Bag;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The trading board (取引掲示板, D-AUCTION): fixed-price listings anyone can browse with filters (category, rarity, level,
 * gold frame, words) and sorts; selling from the bag with the item's recent prices beside the form; the player's own
 * listings; and the mailbox. Buying and listing happen at the auction hall in the capital; cancelling and mail work
 * wherever the board is open. Every action is a request: the server answers with the new page.
 */
public final class BoardScreen extends Screen {
    private static final int WIDTH = 384;
    private static final int HEIGHT = 232;
    private static final int ROW = 22;
    private static final int ROWS = 8;
    private static final int LIST_X = 118;
    private static final int LIST_W = 258;
    private static final int GRID_Y = 49;
    private static final String[] CATEGORIES = {"", "weapon", "armor", "accessory", "material", "consumable", "mount",
            "other"};
    private static final MarketQuery.Sort[] SORTS = {MarketQuery.Sort.NEWEST, MarketQuery.Sort.PRICE_ASCENDING,
            MarketQuery.Sort.PRICE_DESCENDING, MarketQuery.Sort.UNIT_PRICE_ASCENDING, MarketQuery.Sort.ENDING_SOON,
            MarketQuery.Sort.LEVEL_DESCENDING};
    private static final String[] RARITIES = {"common", "uncommon", "rare", "epic", "legendary"};

    private enum Tab {
        BROWSE, SELL, MINE, MAIL
    }

    private Tab tab;
    private int left;
    private int top;
    private long seenRevision = -1;
    private long receivedAt;
    private JsonObject data;
    private JsonObject prices;
    private int category;
    private int rarity = -1;
    private int sort;
    private boolean goldOnly;
    private int page;
    private String selected = "";
    private String confirming = "";
    private int pickSlot = -1;
    private int mineScroll;
    private int mailScroll;

    private EditBox searchBox;
    private EditBox minLevelBox;
    private EditBox maxLevelBox;
    private final Button[] categoryButtons = new Button[CATEGORIES.length];
    private Button rarityButton;
    private Button goldButton;
    private Button sortButton;
    private Button searchButton;
    private Button resetButton;
    private Button previousButton;
    private Button nextButton;
    private Button buyButton;
    private Button hallButton;
    private EditBox countBox;
    private EditBox priceBox;
    private Button listButton;
    private Button claimAllButton;

    private BoardScreen(Tab tab) {
        super(Component.translatable("screen.eternalrealm.board.title"));
        this.tab = tab;
    }

    /** New board data: opens the board when the server says so (at the clerk or the post). */
    public static void accept(JsonObject json) {
        if (!MarketUi.bool(json, "open")) {
            return;
        }
        Tab tab = "mail".equals(MarketUi.str(json, "tab")) ? Tab.MAIL : Tab.BROWSE;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof BoardScreen board) {
            board.switchTo(tab);
        } else {
            minecraft.setScreen(new BoardScreen(tab));
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        String oldSearch = searchBox == null ? "" : searchBox.getValue();
        String oldMin = minLevelBox == null ? "" : minLevelBox.getValue();
        String oldMax = maxLevelBox == null ? "" : maxLevelBox.getValue();
        String oldCount = countBox == null ? "" : countBox.getValue();
        String oldPrice = priceBox == null ? "" : priceBox.getValue();
        int x = left + 8;
        searchBox = addRenderableWidget(new EditBox(font, x, top + 38, 104, 14,
                Component.translatable("screen.eternalrealm.board.search")));
        searchBox.setMaxLength(32);
        searchBox.setHint(Component.translatable("screen.eternalrealm.board.search_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        for (int i = 0; i < CATEGORIES.length; i++) {
            int index = i;
            categoryButtons[i] = addRenderableWidget(Button.builder(categoryName(i), button -> {
                category = index;
                page = 0;
                query();
            }).bounds(x + (i % 2) * 53, top + 56 + (i / 2) * 14, 51, 13).build());
        }
        rarityButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            rarity = rarity >= RARITIES.length - 1 ? -1 : rarity + 1;
            page = 0;
            query();
        }).bounds(x, top + 116, 104, 13).build());
        minLevelBox = addRenderableWidget(new EditBox(font, x + 18, top + 132, 32, 13,
                Component.translatable("screen.eternalrealm.board.min_level")));
        maxLevelBox = addRenderableWidget(new EditBox(font, x + 66, top + 132, 32, 13,
                Component.translatable("screen.eternalrealm.board.max_level")));
        for (EditBox box : new EditBox[] {minLevelBox, maxLevelBox}) {
            box.setMaxLength(3);
            box.setFilter(text -> text.chars().allMatch(Character::isDigit));
        }
        goldButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            goldOnly = !goldOnly;
            page = 0;
            query();
        }).bounds(x, top + 148, 104, 13).build());
        sortButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            sort = (sort + 1) % SORTS.length;
            page = 0;
            query();
        }).bounds(x, top + 164, 104, 13).build());
        searchButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.board.do_search"),
                button -> {
                    page = 0;
                    query();
                }).bounds(x, top + 181, 51, 14).build());
        resetButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.board.reset"),
                button -> reset()).bounds(x + 53, top + 181, 51, 14).build());
        previousButton = addRenderableWidget(Button.builder(Component.literal("◀"), button -> turn(-1))
                .bounds(left + LIST_X, top + HEIGHT - 16, 18, 13).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal("▶"), button -> turn(1))
                .bounds(left + LIST_X + 112, top + HEIGHT - 16, 18, 13).build());
        buyButton = addRenderableWidget(Button.builder(Component.empty(), button -> buy())
                .bounds(left + WIDTH - 8 - 120, top + HEIGHT - 17, 120, 14).build());
        hallButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.board.to_hall"),
                button -> MarketUi.send("auction.open", new JsonObject()))
                .bounds(x, top + HEIGHT - 26, 104, 14).build());
        // the sell form
        int formX = left + 196;
        countBox = addRenderableWidget(new EditBox(font, formX + 44, top + 70, 40, 14,
                Component.translatable("screen.eternalrealm.board.count")));
        countBox.setMaxLength(4);
        countBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        priceBox = addRenderableWidget(new EditBox(font, formX + 44, top + 88, 90, 14,
                Component.translatable("screen.eternalrealm.board.price")));
        priceBox.setMaxLength(13);
        priceBox.setFilter(text -> text.chars().allMatch(c -> Character.isDigit(c) || c == ','));
        listButton = addRenderableWidget(Button.builder(Component.empty(), button -> list())
                .bounds(formX, top + HEIGHT - 30, 180, 18).build());
        claimAllButton = addRenderableWidget(Button.builder(Component.translatable(
                "screen.eternalrealm.board.claim_all"), button -> {
                    confirming = "";
                    MarketUi.send("mail.claim_all", new JsonObject());
                }).bounds(left + WIDTH - 8 - 110, top + HEIGHT - 20, 110, 14).build());
        searchBox.setValue(oldSearch);
        minLevelBox.setValue(oldMin);
        maxLevelBox.setValue(oldMax);
        countBox.setValue(oldCount);
        priceBox.setValue(oldPrice);
        refresh();
        updateWidgets();
    }

    private void switchTo(Tab next) {
        tab = next;
        confirming = "";
        if (next == Tab.BROWSE) {
            query();
        }
    }

    @Override
    public void tick() {
        searchBox.tick();
        minLevelBox.tick();
        maxLevelBox.tick();
        countBox.tick();
        priceBox.tick();
        refresh();
        updateWidgets();
    }

    private void refresh() {
        if (ClientUiState.revision() == seenRevision) {
            return;
        }
        seenRevision = ClientUiState.revision();
        JsonObject next = ClientUiState.get(MarketScreens.BOARD_CHANNEL).orElse(null);
        if (next != null && next != data) {
            data = next;
            receivedAt = System.currentTimeMillis();
            page = (int) MarketUi.num(data, "page");
            long pages = Math.max(1L, MarketUi.num(data, "pages"));
            if (page >= pages && page > 0) {
                // the last page emptied (bought out by others): show the new last one
                page = (int) pages - 1;
                query();
            }
        }
        prices = ClientUiState.get(MarketScreens.PRICES_CHANNEL).orElse(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ widgets

    private void updateWidgets() {
        boolean browse = tab == Tab.BROWSE;
        searchBox.visible = browse;
        minLevelBox.visible = browse;
        maxLevelBox.visible = browse;
        for (int i = 0; i < categoryButtons.length; i++) {
            categoryButtons[i].visible = browse;
            categoryButtons[i].setMessage(categoryName(i).copy().withStyle(i == category ? ChatFormatting.GOLD
                    : ChatFormatting.WHITE));
        }
        rarityButton.visible = browse;
        rarityButton.setMessage(rarity < 0 ? Component.translatable("screen.eternalrealm.board.rarity_any")
                : Component.translatable("screen.eternalrealm.board.rarity_from",
                        Component.translatable("rarity.eternalrealm." + RARITIES[rarity])));
        goldButton.visible = browse;
        goldButton.setMessage(Component.translatable(goldOnly ? "screen.eternalrealm.board.gold_on"
                : "screen.eternalrealm.board.gold_off").withStyle(goldOnly ? ChatFormatting.GOLD
                : ChatFormatting.WHITE));
        sortButton.visible = browse;
        sortButton.setMessage(Component.translatable("screen.eternalrealm.board.sort."
                + SORTS[sort].name().toLowerCase(java.util.Locale.ROOT)));
        searchButton.visible = browse;
        resetButton.visible = browse;
        long pages = Math.max(1L, MarketUi.num(data, "pages"));
        previousButton.visible = browse;
        previousButton.active = page > 0;
        nextButton.visible = browse;
        nextButton.active = page + 1 < pages;
        JsonObject listing = selectedListing();
        buyButton.visible = browse && listing != null;
        boolean atHall = MarketUi.bool(data, "atHall");
        buyButton.active = listing != null && atHall && !MarketUi.bool(listing, "mine")
                && MarketUi.num(data, "gold") >= MarketUi.num(listing, "price");
        if (listing != null) {
            buyButton.setMessage(MarketUi.bool(listing, "mine")
                    ? Component.translatable("screen.eternalrealm.board.own_listing")
                    : !atHall ? Component.translatable("screen.eternalrealm.board.buy_at_hall")
                    : MarketUi.num(data, "gold") < MarketUi.num(listing, "price")
                    ? Component.translatable("screen.eternalrealm.board.not_enough_gold")
                    : confirming.equals("buy") ? Component.translatable("screen.eternalrealm.market.confirm")
                    : Component.translatable("screen.eternalrealm.board.buy",
                            MarketUi.gold(MarketUi.num(listing, "price"))));
        }
        hallButton.visible = browse;

        boolean sell = tab == Tab.SELL && atHall && pickSlot >= 0;
        countBox.visible = sell;
        priceBox.visible = sell;
        listButton.visible = sell;
        long price = MarketUi.parseGold(priceBox.getValue());
        long count = MarketUi.parseGold(countBox.getValue());
        listButton.active = price >= 1 && count >= 1 && pickedCount() >= count && MarketUi.num(data, "gold")
                >= boardFee(price) && MarketUi.objects(data, "mine").size() < MarketUi.num(rules(), "maxListings");
        listButton.setMessage(confirming.equals("list") ? Component.translatable("screen.eternalrealm.market.confirm")
                : Component.translatable("screen.eternalrealm.board.list", MarketUi.gold(Math.max(0L, price))));

        JsonObject mail = data == null || !data.has("mail") ? null : data.getAsJsonObject("mail");
        claimAllButton.visible = tab == Tab.MAIL && (MarketUi.num(mail, "gold") > 0
                || !MarketUi.objects(mail, "items").isEmpty());
    }

    private Component categoryName(int index) {
        return Component.translatable(index == 0 ? "screen.eternalrealm.board.category_all"
                : "screen.eternalrealm.board.category." + CATEGORIES[index]);
    }

    private JsonObject rules() {
        return data == null || !data.has("rules") ? null : data.getAsJsonObject("rules");
    }

    private long boardFee(long price) {
        JsonObject rules = rules();
        long bp = MarketUi.num(rules, "feeBp");
        long raw = price / 10_000 * bp + price % 10_000 * bp / 10_000;
        long minimum = MarketUi.num(rules, "minFee");
        long maximum = Math.max(minimum, MarketUi.num(rules, "maxFee"));
        return Math.min(maximum, Math.max(minimum, raw));
    }

    private long boardTax(long price) {
        long bp = MarketUi.num(rules(), "taxBp");
        return price / 10_000 * bp + price % 10_000 * bp / 10_000;
    }

    private int pickedCount() {
        Player player = Minecraft.getInstance().player;
        return player == null || pickSlot < 0 ? 0 : player.getInventory().items.get(pickSlot).getCount();
    }

    private JsonObject selectedListing() {
        for (JsonObject listing : MarketUi.objects(data, "listings")) {
            if (MarketUi.str(listing, "id").equals(selected)) {
                return listing;
            }
        }
        return null;
    }

    private long remaining(JsonObject entry) {
        return Math.max(0L, MarketUi.num(entry, "remaining") - (System.currentTimeMillis() - receivedAt));
    }

    // ------------------------------------------------------------------ actions

    private JsonObject queryArgs() {
        JsonObject args = new JsonObject();
        String text = searchBox == null ? "" : searchBox.getValue().trim();
        if (!text.isEmpty()) {
            args.addProperty("text", text.length() > MarketQuery.MAX_FILTER_LENGTH
                    ? text.substring(0, MarketQuery.MAX_FILTER_LENGTH) : text);
        }
        if (category > 0) {
            args.addProperty("category", CATEGORIES[category]);
        }
        if (rarity >= 0) {
            args.addProperty("rarity", rarity);
        }
        int min = level(minLevelBox, 0);
        int max = level(maxLevelBox, ItemInfo.MAX_LEVEL);
        if (min > 0) {
            args.addProperty("minLevel", min);
        }
        if (max < ItemInfo.MAX_LEVEL) {
            args.addProperty("maxLevel", Math.max(min, max));
        }
        if (goldOnly) {
            args.addProperty("gold", true);
        }
        args.addProperty("sort", SORTS[sort].name());
        args.addProperty("page", Math.max(0, Math.min(10_000, page)));
        return args;
    }

    private static int level(EditBox box, int fallback) {
        if (box == null || box.getValue().isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Math.min(ItemInfo.MAX_LEVEL, Integer.parseInt(box.getValue().trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void query() {
        confirming = "";
        MarketUi.send("board.query", queryArgs());
    }

    private void reset() {
        searchBox.setValue("");
        minLevelBox.setValue("");
        maxLevelBox.setValue("");
        category = 0;
        rarity = -1;
        sort = 0;
        goldOnly = false;
        page = 0;
        query();
    }

    private void turn(int delta) {
        long pages = Math.max(1L, MarketUi.num(data, "pages"));
        int next = (int) Math.max(0, Math.min(pages - 1, page + delta));
        if (next != page) {
            page = next;
            query();
        }
    }

    private void buy() {
        JsonObject listing = selectedListing();
        if (listing == null) {
            return;
        }
        if (!confirming.equals("buy")) {
            confirming = "buy";
            return;
        }
        confirming = "";
        JsonObject args = queryArgs();
        args.addProperty("id", selected);
        MarketUi.send("board.buy", args);
    }

    private void list() {
        long price = MarketUi.parseGold(priceBox.getValue());
        long count = MarketUi.parseGold(countBox.getValue());
        if (pickSlot < 0 || price < 1 || count < 1 || count > pickedCount()) {
            return;
        }
        if (!confirming.equals("list")) {
            confirming = "list";
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("slot", pickSlot);
        args.addProperty("count", count);
        args.addProperty("price", price);
        MarketUi.send("board.list", args);
        pickSlot = -1;
    }

    private void pick(int slot) {
        pickSlot = slot;
        confirming = "";
        countBox.setValue(String.valueOf(pickedCount()));
        priceBox.setValue("");
        JsonObject args = new JsonObject();
        args.addProperty("slot", slot);
        MarketUi.send("board.prices", args);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && tab == Tab.BROWSE && (searchBox.isFocused() || minLevelBox.isFocused()
                || maxLevelBox.isFocused())) {
            page = 0;
            query();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left + 8;
        for (Tab each : Tab.values()) {
            int w = tabWidth(each);
            if (MarketUi.inside(mouseX, mouseY, x, top + 20, w, 13)) {
                if (tab != each) {
                    switchTo(each);
                }
                return true;
            }
            x += w + 2;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        switch (tab) {
            case BROWSE -> {
                List<JsonObject> listings = MarketUi.objects(data, "listings");
                for (int i = 0; i < listings.size() && i < ROWS; i++) {
                    if (MarketUi.inside(mouseX, mouseY, left + LIST_X, top + 38 + i * ROW, LIST_W, ROW - 2)) {
                        String id = MarketUi.str(listings.get(i), "id");
                        if (!id.equals(selected)) {
                            selected = id;
                            confirming = "";
                        }
                        return true;
                    }
                }
            }
            case SELL -> {
                if (MarketUi.bool(data, "atHall")) {
                    int slot = bagSlotAt(mouseX, mouseY);
                    if (slot >= 0) {
                        pick(slot);
                        return true;
                    }
                }
            }
            case MINE -> {
                List<JsonObject> mine = MarketUi.objects(data, "mine");
                for (int i = mineScroll; i < mine.size() && i < mineScroll + ROWS; i++) {
                    int rowY = top + 38 + (i - mineScroll) * ROW;
                    if (MarketUi.inside(mouseX, mouseY, left + WIDTH - 8 - 64, rowY + 3, 60, 14)) {
                        String id = "cancel:" + MarketUi.str(mine.get(i), "id");
                        if (!confirming.equals(id)) {
                            confirming = id;
                            return true;
                        }
                        confirming = "";
                        JsonObject args = new JsonObject();
                        args.addProperty("id", MarketUi.str(mine.get(i), "id"));
                        MarketUi.send("board.cancel", args);
                        return true;
                    }
                }
            }
            case MAIL -> {
                JsonObject mail = data == null || !data.has("mail") ? null : data.getAsJsonObject("mail");
                if (MarketUi.num(mail, "gold") > 0 && MarketUi.inside(mouseX, mouseY, left + WIDTH - 8 - 64,
                        top + 40, 60, 14)) {
                    MarketUi.send("mail.claim", new JsonObject());
                    return true;
                }
                List<JsonObject> items = MarketUi.objects(mail, "items");
                for (int i = mailScroll; i < items.size() && i < mailScroll + 7; i++) {
                    int rowY = top + 60 + (i - mailScroll) * ROW;
                    if (MarketUi.inside(mouseX, mouseY, left + WIDTH - 8 - 64, rowY + 3, 60, 14)) {
                        JsonObject args = new JsonObject();
                        args.addProperty("id", MarketUi.str(items.get(i), "id"));
                        MarketUi.send("mail.claim", args);
                        return true;
                    }
                }
            }
            default -> {
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int step = (int) -Math.signum(delta);
        if (tab == Tab.BROWSE && MarketUi.inside(mouseX, mouseY, left + LIST_X, top + 38, LIST_W, ROWS * ROW)) {
            turn(step);
            return true;
        }
        if (tab == Tab.MINE) {
            int max = Math.max(0, MarketUi.objects(data, "mine").size() - ROWS);
            mineScroll = Math.max(0, Math.min(max, mineScroll + step));
            return true;
        }
        if (tab == Tab.MAIL) {
            JsonObject mail = data == null || !data.has("mail") ? null : data.getAsJsonObject("mail");
            int max = Math.max(0, MarketUi.objects(mail, "items").size() - 7);
            mailScroll = Math.max(0, Math.min(max, mailScroll + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int bagSlotAt(double mouseX, double mouseY) {
        Player player = Minecraft.getInstance().player;
        for (int i = 0; i < Bag.CAPACITY; i++) {
            int cx = left + 8 + (i % 7) * 19;
            int cy = top + GRID_Y + (i / 7) * 19;
            if (MarketUi.inside(mouseX, mouseY, cx, cy, 18, 18)) {
                int slot = Bag.FIRST_SLOT + i;
                return player != null && !player.getInventory().items.get(slot).isEmpty() ? slot : -1;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        MarketUi.frame(graphics, left, top, WIDTH, HEIGHT);
        graphics.drawString(font, title, left + 8, top + 7, MarketUi.RIM, true);
        Component gold = Component.translatable("screen.eternalrealm.market.gold",
                MarketUi.gold(MarketUi.num(data, "gold")));
        graphics.drawString(font, gold, left + WIDTH - 8 - font.width(gold), top + 7, MarketUi.GOLD, true);
        drawTabs(graphics, mouseX, mouseY);
        ItemStack hovered = ItemStack.EMPTY;
        if (data == null || !MarketUi.bool(data, "ready")) {
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.market.loading"),
                    left + WIDTH / 2, top + HEIGHT / 2, MarketUi.MUTED);
        } else {
            hovered = switch (tab) {
                case BROWSE -> drawBrowse(graphics, mouseX, mouseY);
                case SELL -> drawSell(graphics, mouseX, mouseY);
                case MINE -> drawMine(graphics, mouseX, mouseY);
                case MAIL -> drawMail(graphics, mouseX, mouseY);
            };
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private int tabWidth(Tab each) {
        return Math.max(44, font.width(tabName(each)) + 10);
    }

    private Component tabName(Tab each) {
        JsonObject mail = data == null || !data.has("mail") ? null : data.getAsJsonObject("mail");
        return switch (each) {
            case BROWSE -> Component.translatable("screen.eternalrealm.board.tab_browse");
            case SELL -> Component.translatable("screen.eternalrealm.board.tab_sell");
            case MINE -> Component.translatable("screen.eternalrealm.board.tab_mine",
                    MarketUi.objects(data, "mine").size(), MarketUi.num(rules(), "maxListings"));
            case MAIL -> {
                int waiting = MarketUi.objects(mail, "items").size() + (MarketUi.num(mail, "gold") > 0 ? 1 : 0);
                yield waiting > 0 ? Component.translatable("screen.eternalrealm.board.tab_mail_count", waiting)
                        : Component.translatable("screen.eternalrealm.board.tab_mail");
            }
        };
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + 8;
        int y = top + 20;
        for (Tab each : Tab.values()) {
            int w = tabWidth(each);
            boolean on = each == tab;
            boolean hover = MarketUi.inside(mouseX, mouseY, x, y, w, 13);
            graphics.fill(x, y, x + w, y + 13, on ? MarketUi.ROW_SELECTED : hover ? MarketUi.ROW_HOVER
                    : MarketUi.PANEL_INNER);
            graphics.renderOutline(x, y, w, 13, on ? MarketUi.RIM : MarketUi.RIM_DARK);
            Component name = tabName(each);
            graphics.drawString(font, name, x + (w - font.width(name)) / 2, y + 3, on ? 0xFFFFE8A8 : MarketUi.MUTED,
                    false);
            x += w + 2;
        }
    }

    private ItemStack drawBrowse(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        graphics.drawString(font, "Lv", left + 8, top + 135, MarketUi.TEXT, false);
        graphics.drawString(font, "~", left + 8 + 55, top + 135, MarketUi.TEXT, false);
        int x = left + LIST_X;
        int y = top + 38;
        List<JsonObject> listings = MarketUi.objects(data, "listings");
        if (listings.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.none"), x + 6, y + 6,
                    MarketUi.MUTED, false);
        }
        for (int i = 0; i < listings.size() && i < ROWS; i++) {
            JsonObject listing = listings.get(i);
            int rowY = y + i * ROW;
            boolean on = MarketUi.str(listing, "id").equals(selected);
            boolean hover = MarketUi.inside(mouseX, mouseY, x, rowY, LIST_W, ROW - 2);
            graphics.fill(x, rowY, x + LIST_W, rowY + ROW - 2, on ? MarketUi.ROW_SELECTED : hover
                    ? MarketUi.ROW_HOVER : MarketUi.ROW);
            ItemStack stack = MarketUi.stack(MarketUi.str(listing, "item"));
            MarketUi.cell(graphics, font, stack, x + 1, rowY + 1, MarketUi.bool(listing, "gold"));
            if (MarketUi.inside(mouseX, mouseY, x + 1, rowY + 1, 18, 18)) {
                hovered = stack;
            }
            long price = MarketUi.num(listing, "price");
            long count = Math.max(1L, MarketUi.num(listing, "count"));
            String priceText = MarketUi.gold(price);
            int priceWidth = font.width(priceText);
            String name = stack.getHoverName().getString() + (count > 1 ? " ×" + count : "");
            graphics.drawString(font, MarketUi.fit(font, name, LIST_W - 30 - priceWidth - 6), x + 22, rowY + 2,
                    MarketUi.rarityColor(stack, MarketUi.TEXT), false);
            graphics.drawString(font, priceText, x + LIST_W - 3 - priceWidth, rowY + 2, MarketUi.GOLD, false);
            Component detail = detailLine(listing, count, price);
            graphics.drawString(font, detail, x + 22, rowY + 11, MarketUi.MUTED, false);
            long median = MarketUi.num(listing, "median");
            if (median > 0) {
                long unit = price / count;
                Component deal = unit * 10 <= median * 9 ? Component.translatable("screen.eternalrealm.board.cheap")
                        .withStyle(ChatFormatting.GREEN) : unit * 10 >= median * 13
                        ? Component.translatable("screen.eternalrealm.board.pricey").withStyle(ChatFormatting.RED)
                        : Component.empty();
                graphics.drawString(font, deal, x + LIST_W - 3 - font.width(deal), rowY + 11, MarketUi.MUTED, false);
            }
            if (MarketUi.bool(listing, "mine")) {
                graphics.fill(x, rowY, x + 2, rowY + ROW - 2, MarketUi.RIM);
            }
        }
        long pages = Math.max(1L, MarketUi.num(data, "pages"));
        Component pageText = Component.translatable("screen.eternalrealm.board.page", page + 1, pages,
                MarketUi.num(data, "total"));
        graphics.drawCenteredString(font, pageText, left + LIST_X + 65, top + HEIGHT - 13, MarketUi.TEXT);
        JsonObject listing = selectedListing();
        if (listing != null && !MarketUi.bool(data, "atHall")) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.hall_only"), left + 8,
                    top + HEIGHT - 9, 0xFF6A6078, false);
        }
        return hovered;
    }

    private Component detailLine(JsonObject listing, long count, long price) {
        Component time = MarketUi.remaining(remaining(listing));
        String seller = MarketUi.fit(font, MarketUi.str(listing, "seller"), 64);
        long level = MarketUi.num(listing, "level");
        if (count > 1) {
            return Component.translatable("screen.eternalrealm.board.detail_unit", MarketUi.gold(price / count),
                    seller, time);
        }
        return level > 0 ? Component.translatable("screen.eternalrealm.board.detail_level", level, seller, time)
                : Component.translatable("screen.eternalrealm.board.detail", seller, time);
    }

    private ItemStack drawSell(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int x = left + 8;
        int y = top + 38;
        if (!MarketUi.bool(data, "atHall")) {
            int line = y + 4;
            for (var text : font.split(Component.translatable("screen.eternalrealm.board.sell_hall_only"),
                    WIDTH - 16)) {
                graphics.drawString(font, text, x, line, MarketUi.MUTED, false);
                line += 10;
            }
            return hovered;
        }
        graphics.drawString(font, Component.translatable("screen.eternalrealm.board.pick_item"), x, y, MarketUi.RIM,
                false);
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return hovered;
        }
        for (int i = 0; i < Bag.CAPACITY; i++) {
            int slot = Bag.FIRST_SLOT + i;
            int cx = x + (i % 7) * 19;
            int cy = top + GRID_Y + (i / 7) * 19;
            ItemStack stack = player.getInventory().items.get(slot);
            MarketUi.cell(graphics, font, stack, cx, cy, false);
            if (slot == pickSlot) {
                graphics.renderOutline(cx - 1, cy - 1, 20, 20, 0xFFFFFFFF);
            }
            if (!stack.isEmpty() && MarketUi.inside(mouseX, mouseY, cx, cy, 18, 18)) {
                hovered = stack;
            }
        }
        drawPriceReference(graphics, x, top + GRID_Y + 99);
        int formX = left + 196;
        if (pickSlot < 0) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.pick_first"), formX, y + 4,
                    MarketUi.MUTED, false);
            return hovered;
        }
        ItemStack picked = player.getInventory().items.get(pickSlot);
        if (picked.isEmpty()) {
            pickSlot = -1;
            return hovered;
        }
        MarketUi.cell(graphics, font, picked, formX, y, false);
        if (MarketUi.inside(mouseX, mouseY, formX, y, 18, 18)) {
            hovered = picked;
        }
        graphics.drawString(font, MarketUi.fit(font, picked.getHoverName().getString(), 156), formX + 22, y + 5,
                MarketUi.rarityColor(picked, MarketUi.TEXT), false);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.board.count_label"), formX, top + 73,
                MarketUi.TEXT, false);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.board.price_label"), formX, top + 91,
                MarketUi.TEXT, false);
        long price = MarketUi.parseGold(priceBox.getValue());
        long count = MarketUi.parseGold(countBox.getValue());
        int line = top + 108;
        if (price >= 1 && count >= 1) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.unit_price",
                    MarketUi.gold(price / count)), formX, line, MarketUi.MUTED, false);
            line += 11;
            long fee = boardFee(price);
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.fee", MarketUi.gold(fee)),
                    formX, line, fee > MarketUi.num(data, "gold") ? MarketUi.BAD : MarketUi.TEXT, false);
            line += 11;
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.proceeds",
                    MarketUi.gold(price - boardTax(price))), formX, line, MarketUi.GOOD, false);
            line += 11;
        }
        JsonObject rules = rules();
        graphics.drawString(font, Component.translatable("screen.eternalrealm.board.terms",
                MarketUi.num(rules, "hours"), String.format("%.1f", MarketUi.num(rules, "taxBp") / 100.0)), formX,
                line, MarketUi.MUTED, false);
        line += 11;
        if (MarketUi.objects(data, "mine").size() >= MarketUi.num(rules, "maxListings")) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.full",
                    MarketUi.num(rules, "maxListings")), formX, line, MarketUi.BAD, false);
        }
        return hovered;
    }

    /** The picked item's recent prices (相場) under the bag grid. */
    private void drawPriceReference(GuiGraphics graphics, int x, int y) {
        if (pickSlot < 0) {
            return;
        }
        graphics.drawString(font, Component.translatable("screen.eternalrealm.board.prices_title"), x, y,
                MarketUi.RIM, false);
        y += 11;
        if (prices == null || MarketUi.num(prices, "slot") != pickSlot) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.market.loading"), x, y,
                    MarketUi.MUTED, false);
            return;
        }
        long sales = MarketUi.num(prices, "sales");
        if (sales == 0) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.no_sales"), x, y,
                    MarketUi.MUTED, false);
            y += 10;
        } else {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.median_line",
                    MarketUi.gold(MarketUi.num(prices, "median")), sales), x, y, MarketUi.GOLD, false);
            y += 10;
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.range_line",
                    MarketUi.gold(MarketUi.num(prices, "lowest")), MarketUi.gold(MarketUi.num(prices, "highest"))),
                    x, y, MarketUi.MUTED, false);
            y += 10;
        }
        long lowest = MarketUi.num(prices, "boardLowest");
        if (lowest > 0) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.board_lowest",
                    MarketUi.gold(lowest)), x, y, MarketUi.TEXT, false);
            y += 10;
        }
        List<JsonObject> recent = MarketUi.objects(prices, "recent");
        for (int i = 0; i < recent.size() && i < 2 && y < top + HEIGHT - 10; i++) {
            JsonObject sale = recent.get(i);
            Component text = Component.translatable(MarketUi.bool(sale, "auction")
                            ? "screen.eternalrealm.board.sale_auction" : "screen.eternalrealm.board.sale_board",
                    MarketUi.gold(MarketUi.num(sale, "unit")), MarketUi.num(sale, "count"),
                    MarketUi.ago(MarketUi.num(sale, "ago")));
            graphics.drawString(font, text, x, y, 0xFFB8AEC8, false);
            y += 10;
        }
    }

    private ItemStack drawMine(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int x = left + 8;
        int y = top + 38;
        List<JsonObject> mine = MarketUi.objects(data, "mine");
        if (mine.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.mine_empty"), x + 4, y + 6,
                    MarketUi.MUTED, false);
        }
        for (int i = mineScroll; i < mine.size() && i < mineScroll + ROWS; i++) {
            JsonObject listing = mine.get(i);
            int rowY = y + (i - mineScroll) * ROW;
            graphics.fill(x, rowY, x + WIDTH - 16, rowY + ROW - 2, MarketUi.ROW);
            ItemStack stack = MarketUi.stack(MarketUi.str(listing, "item"));
            MarketUi.cell(graphics, font, stack, x + 1, rowY + 1, MarketUi.bool(listing, "gold"));
            if (MarketUi.inside(mouseX, mouseY, x + 1, rowY + 1, 18, 18)) {
                hovered = stack;
            }
            long count = Math.max(1L, MarketUi.num(listing, "count"));
            graphics.drawString(font, MarketUi.fit(font, stack.getHoverName().getString()
                    + (count > 1 ? " ×" + count : ""), 200), x + 22, rowY + 2, MarketUi.rarityColor(stack,
                    MarketUi.TEXT), false);
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.mine_line",
                    MarketUi.gold(MarketUi.num(listing, "price")), MarketUi.remaining(remaining(listing))), x + 22,
                    rowY + 11, MarketUi.MUTED, false);
            int bx = left + WIDTH - 8 - 64;
            boolean armed = confirming.equals("cancel:" + MarketUi.str(listing, "id"));
            drawRowButton(graphics, bx, rowY + 3, Component.translatable(armed ? "screen.eternalrealm.market.confirm"
                    : "screen.eternalrealm.board.cancel"), mouseX, mouseY, armed);
        }
        graphics.drawString(font, Component.translatable("screen.eternalrealm.board.cancel_note"), x,
                top + HEIGHT - 12, 0xFF6A6078, false);
        return hovered;
    }

    private ItemStack drawMail(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int x = left + 8;
        JsonObject mail = data.has("mail") ? data.getAsJsonObject("mail") : null;
        long gold = MarketUi.num(mail, "gold");
        graphics.fill(x, top + 38, x + WIDTH - 16, top + 56, MarketUi.ROW);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.board.mail_gold",
                MarketUi.gold(gold)), x + 6, top + 43, gold > 0 ? MarketUi.GOLD : MarketUi.MUTED, false);
        if (gold > 0) {
            drawRowButton(graphics, left + WIDTH - 8 - 64, top + 40, Component.translatable(
                    "screen.eternalrealm.board.claim"), mouseX, mouseY, false);
        }
        List<JsonObject> items = MarketUi.objects(mail, "items");
        if (items.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.mail_empty"), x + 6,
                    top + 66, MarketUi.MUTED, false);
        }
        for (int i = mailScroll; i < items.size() && i < mailScroll + 7; i++) {
            JsonObject item = items.get(i);
            int rowY = top + 60 + (i - mailScroll) * ROW;
            graphics.fill(x, rowY, x + WIDTH - 16, rowY + ROW - 2, MarketUi.ROW);
            ItemStack stack = MarketUi.stack(MarketUi.str(item, "item"));
            MarketUi.cell(graphics, font, stack, x + 1, rowY + 1, false);
            if (MarketUi.inside(mouseX, mouseY, x + 1, rowY + 1, 18, 18)) {
                hovered = stack;
            }
            graphics.drawString(font, MarketUi.fit(font, stack.getHoverName().getString(), 220), x + 22, rowY + 2,
                    MarketUi.rarityColor(stack, MarketUi.TEXT), false);
            graphics.drawString(font, Component.translatable("screen.eternalrealm.board.mail_line",
                    Component.translatable("mail_reason.eternalrealm." + MarketUi.str(item, "reason")),
                    MarketUi.ago(MarketUi.num(item, "ago"))), x + 22, rowY + 11, MarketUi.MUTED, false);
            drawRowButton(graphics, left + WIDTH - 8 - 64, rowY + 3, Component.translatable(
                    "screen.eternalrealm.board.claim"), mouseX, mouseY, false);
        }
        return hovered;
    }

    private void drawRowButton(GuiGraphics graphics, int x, int y, Component text, int mouseX, int mouseY,
                               boolean armed) {
        boolean hover = MarketUi.inside(mouseX, mouseY, x, y, 60, 14);
        graphics.fill(x, y, x + 60, y + 14, armed ? 0xFF6A2A20 : hover ? 0xFF4A3A20 : MarketUi.PANEL_INNER);
        graphics.renderOutline(x, y, 60, 14, armed ? MarketUi.BAD : MarketUi.RIM);
        graphics.drawCenteredString(font, text, x + 30, y + 3, armed ? 0xFFFFD0C0 : MarketUi.TEXT);
    }
}
