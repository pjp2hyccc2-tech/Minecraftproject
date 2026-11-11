package com.eternalrealm.ui.market;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.economy.forge.MarketScreens;
import com.eternalrealm.item.bag.Bag;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The auction hall (オークション会場, D-AUCTION): the lots running now (at most five on the server) with their
 * countdowns, bid history and the bid box; the waiting queue; the player's own lots with the registration counter
 * (only usable at the hall in the capital); and the latest results. Opened with the O key anywhere. Every action is a
 * request; the server decides and pushes the new state to everyone watching.
 */
public final class AuctionScreen extends Screen {
    private static final int WIDTH = 384;
    private static final int HEIGHT = 232;
    private static final int ROW = 30;
    /** The counter's bag grid and form (MINE tab, right column), relative to the top. */
    private static final int GRID_Y = 49;
    private static final int FORM_Y = 148;

    private enum Tab {
        LIVE, QUEUE, MINE, RESULTS
    }

    private Tab tab = Tab.LIVE;
    private int left;
    private int top;
    private long seenRevision = -1;
    private long receivedAt;
    private JsonObject data;
    private String selected = "";
    private String mineSelected = "";
    private int pickSlot = -1;
    private int minutes = -1;
    private int resultScroll;
    private String confirming = "";

    private EditBox bidBox;
    private Button bidButton;
    private Button minButton;
    private Button raiseButton;
    private Button buyoutButton;
    private Button withdrawLiveButton;
    private EditBox countBox;
    private EditBox startBox;
    private EditBox buyoutBox;
    private Button registerButton;
    private final List<Button> durationButtons = new ArrayList<>();
    private Button startLotButton;
    private Button withdrawLotButton;
    private Button boardButton;

    public AuctionScreen() {
        super(Component.translatable("screen.eternalrealm.auction_hall.title"));
    }

    /** New hall data: opens the screen when the server says so, otherwise the open screen refreshes itself. */
    public static void accept(JsonObject json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (MarketUi.bool(json, "open") && !(minecraft.screen instanceof AuctionScreen)) {
            minecraft.setScreen(new AuctionScreen());
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        // a resize rebuilds the widgets: keep what was typed
        String oldBid = bidBox == null ? "" : bidBox.getValue();
        String oldCount = countBox == null ? "" : countBox.getValue();
        String oldStart = startBox == null ? "" : startBox.getValue();
        String oldBuyout = buyoutBox == null ? "" : buyoutBox.getValue();
        int detailX = left + 196;
        int bottom = top + HEIGHT - 26;
        bidBox = addRenderableWidget(new EditBox(font, detailX, bottom - 20, 96, 16,
                Component.translatable("screen.eternalrealm.auction_hall.amount")));
        bidBox.setMaxLength(10);
        bidBox.setFilter(text -> text.chars().allMatch(c -> Character.isDigit(c) || c == ','));
        bidButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.auction_hall.bid"),
                button -> bid()).bounds(detailX + 100, bottom - 20, 80, 16).build());
        minButton = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.auction_hall.min"),
                button -> setBid(MarketUi.num(selectedLot(), "next"))).bounds(detailX, bottom, 46, 16).build());
        raiseButton = addRenderableWidget(Button.builder(Component.translatable(
                "screen.eternalrealm.auction_hall.raise"), button -> raise()).bounds(detailX + 50, bottom, 46, 16)
                .build());
        buyoutButton = addRenderableWidget(Button.builder(Component.empty(), button -> buyout())
                .bounds(detailX + 100, bottom, 80, 16).build());
        withdrawLiveButton = addRenderableWidget(Button.builder(Component.translatable(
                "screen.eternalrealm.auction_hall.withdraw"), button -> withdraw(selected))
                .bounds(detailX, bottom, 180, 16).build());
        // the counter (MINE tab, right column)
        int formY = top + FORM_Y;
        countBox = addRenderableWidget(new EditBox(font, detailX + 30, formY, 34, 14,
                Component.translatable("screen.eternalrealm.auction_hall.count")));
        countBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        countBox.setMaxLength(4);
        startBox = addRenderableWidget(new EditBox(font, detailX + 30, formY + 16, 70, 14,
                Component.translatable("screen.eternalrealm.auction_hall.start_price")));
        startBox.setFilter(text -> text.chars().allMatch(c -> Character.isDigit(c) || c == ','));
        startBox.setMaxLength(13);
        buyoutBox = addRenderableWidget(new EditBox(font, detailX + 30, formY + 32, 70, 14,
                Component.translatable("screen.eternalrealm.auction_hall.buyout_price")));
        buyoutBox.setFilter(text -> text.chars().allMatch(c -> Character.isDigit(c) || c == ','));
        buyoutBox.setMaxLength(13);
        durationButtons.clear();
        for (int i = 0; i < 4; i++) {
            int index = i;
            durationButtons.add(addRenderableWidget(Button.builder(Component.empty(), button -> chooseDuration(index))
                    .bounds(detailX + i * 45, formY + 48, 43, 14).build()));
        }
        registerButton = addRenderableWidget(Button.builder(Component.translatable(
                "screen.eternalrealm.auction_hall.register"), button -> register())
                .bounds(detailX + 104, formY, 76, 46).build());
        startLotButton = addRenderableWidget(Button.builder(Component.empty(), button -> startLot())
                .bounds(left + 8, top + HEIGHT - 26, 110, 16).build());
        withdrawLotButton = addRenderableWidget(Button.builder(Component.translatable(
                "screen.eternalrealm.auction_hall.withdraw"), button -> withdraw(mineSelected))
                .bounds(left + 122, top + HEIGHT - 26, 64, 16).build());
        boardButton = addRenderableWidget(Button.builder(Component.translatable(
                "screen.eternalrealm.auction_hall.to_board"), button -> MarketUi.send("board.open", new JsonObject()))
                .bounds(left + WIDTH - 8 - 74, top + 19, 74, 14).build());
        bidBox.setValue(oldBid);
        countBox.setValue(oldCount);
        startBox.setValue(oldStart);
        buyoutBox.setValue(oldBuyout);
        refresh();
        updateWidgets();
    }

    @Override
    public void tick() {
        bidBox.tick();
        countBox.tick();
        startBox.tick();
        buyoutBox.tick();
        refresh();
        updateWidgets();
    }

    private void refresh() {
        if (ClientUiState.revision() == seenRevision) {
            return;
        }
        seenRevision = ClientUiState.revision();
        JsonObject next = ClientUiState.get(MarketScreens.HALL_CHANNEL).orElse(null);
        if (next != null && next != data) {
            data = next;
            receivedAt = System.currentTimeMillis();
            JsonObject lot = selectedLot();
            if (selected.isEmpty() || lot == null) {
                List<JsonObject> running = MarketUi.objects(data, "running");
                selected = running.isEmpty() ? "" : MarketUi.str(running.get(0), "id");
                bidBox.setValue("");
            }
            long next2 = MarketUi.num(selectedLot(), "next");
            if (MarketUi.parseGold(bidBox.getValue()) < next2) {
                setBid(next2);
            }
        }
    }

    @Override
    public void removed() {
        MarketUi.send("auction.close", new JsonObject());
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ widgets

    private void updateWidgets() {
        JsonObject lot = selectedLot();
        boolean live = tab == Tab.LIVE && lot != null;
        boolean mine = live && MarketUi.bool(lot, "mine");
        bidBox.visible = live && !mine;
        bidButton.visible = live && !mine;
        bidButton.active = !MarketUi.bool(lot, "leading");
        bidButton.setMessage(confirming.equals("bid") ? Component.translatable("screen.eternalrealm.market.confirm")
                : Component.translatable("screen.eternalrealm.auction_hall.bid"));
        minButton.visible = live && !mine;
        raiseButton.visible = live && !mine;
        long buyout = MarketUi.num(lot, "buyout");
        buyoutButton.visible = live && !mine && buyout > 0;
        buyoutButton.setMessage(confirming.equals("buyout") ? Component.translatable(
                "screen.eternalrealm.market.confirm") : Component.translatable(
                "screen.eternalrealm.auction_hall.buyout", MarketUi.gold(buyout)));
        withdrawLiveButton.visible = mine && MarketUi.num(lot, "bids") == 0;
        withdrawLiveButton.setMessage(confirming.equals("withdraw") ? Component.translatable(
                "screen.eternalrealm.market.confirm") : Component.translatable(
                "screen.eternalrealm.auction_hall.withdraw"));

        boolean counter = tab == Tab.MINE && MarketUi.bool(data, "atHall");
        countBox.visible = counter && pickSlot >= 0;
        startBox.visible = counter && pickSlot >= 0;
        buyoutBox.visible = counter && pickSlot >= 0;
        registerButton.visible = counter && pickSlot >= 0;
        List<Long> durations = durations();
        for (int i = 0; i < durationButtons.size(); i++) {
            Button button = durationButtons.get(i);
            button.visible = counter && pickSlot >= 0 && i < durations.size();
            if (i < durations.size()) {
                long value = durations.get(i);
                button.setMessage(Component.translatable("screen.eternalrealm.auction_hall.minutes", value)
                        .withStyle(value == minutes ? net.minecraft.ChatFormatting.GOLD
                                : net.minecraft.ChatFormatting.WHITE));
            }
        }
        JsonObject own = mineLot();
        String state = MarketUi.str(own, "state");
        startLotButton.visible = tab == Tab.MINE && "draft".equals(state);
        startLotButton.active = MarketUi.bool(data, "atHall");
        startLotButton.setMessage(confirming.equals("start") ? Component.translatable(
                "screen.eternalrealm.market.confirm") : Component.translatable(
                "screen.eternalrealm.auction_hall.start_lot", MarketUi.gold(fee(MarketUi.num(own, "start")))));
        boardButton.visible = MarketUi.bool(data, "atHall");
        withdrawLotButton.visible = tab == Tab.MINE && ("draft".equals(state) || "queued".equals(state)
                || ("running".equals(state) && MarketUi.num(own, "bids") == 0));
        withdrawLotButton.setMessage(confirming.equals("withdraw_mine") ? Component.translatable(
                "screen.eternalrealm.market.confirm") : Component.translatable(
                "screen.eternalrealm.auction_hall.withdraw"));
    }

    private List<Long> durations() {
        List<Long> list = new ArrayList<>();
        JsonObject rules = data == null || !data.has("rules") ? null : data.getAsJsonObject("rules");
        if (rules != null && rules.has("durations")) {
            rules.getAsJsonArray("durations").forEach(element -> list.add(element.getAsLong()));
        }
        return list;
    }

    private long fee(long start) {
        JsonObject rules = data == null || !data.has("rules") ? null : data.getAsJsonObject("rules");
        long bp = MarketUi.num(rules, "feeBp");
        return Math.max(MarketUi.num(rules, "minFee"), start / 10_000 * bp + start % 10_000 * bp / 10_000);
    }

    private JsonObject selectedLot() {
        for (JsonObject lot : MarketUi.objects(data, "running")) {
            if (MarketUi.str(lot, "id").equals(selected)) {
                return lot;
            }
        }
        return null;
    }

    private JsonObject mineLot() {
        for (JsonObject lot : MarketUi.objects(data, "mine")) {
            if (MarketUi.str(lot, "id").equals(mineSelected)) {
                return lot;
            }
        }
        return null;
    }

    private long remaining(JsonObject lot) {
        return Math.max(0L, MarketUi.num(lot, "remaining") - (System.currentTimeMillis() - receivedAt));
    }

    // ------------------------------------------------------------------ actions

    private void setBid(long amount) {
        if (amount > 0) {
            bidBox.setValue(String.valueOf(amount));
        }
        confirming = "";
    }

    private void raise() {
        JsonObject lot = selectedLot();
        long current = MarketUi.num(lot, "price");
        setBid(Math.max(MarketUi.num(lot, "next"), current + current / 10));
    }

    private void bid() {
        JsonObject lot = selectedLot();
        long amount = MarketUi.parseGold(bidBox.getValue());
        long next = MarketUi.num(lot, "next");
        if (lot == null || amount < next) {
            setBid(next);
            return;
        }
        // a bid far above the next lowest is probably a typo: ask once more
        if (amount > next * 2 && !confirming.equals("bid")) {
            confirming = "bid";
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("id", selected);
        args.addProperty("amount", amount);
        MarketUi.send("auction.bid", args);
    }

    private void buyout() {
        if (!confirming.equals("buyout")) {
            confirming = "buyout";
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("id", selected);
        MarketUi.send("auction.buyout", args);
    }

    private void withdraw(String id) {
        String key = id.equals(mineSelected) && tab == Tab.MINE ? "withdraw_mine" : "withdraw";
        if (!confirming.equals(key)) {
            confirming = key;
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("id", id);
        MarketUi.send("auction.withdraw", args);
    }

    private void startLot() {
        if (!confirming.equals("start")) {
            confirming = "start";
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("id", mineSelected);
        MarketUi.send("auction.start", args);
    }

    private void chooseDuration(int index) {
        List<Long> durations = durations();
        if (index < durations.size()) {
            minutes = durations.get(index).intValue();
        }
    }

    private void register() {
        Player player = Minecraft.getInstance().player;
        if (player == null || pickSlot < 0) {
            return;
        }
        ItemStack stack = player.getInventory().items.get(pickSlot);
        long count = MarketUi.parseGold(countBox.getValue());
        long start = MarketUi.parseGold(startBox.getValue());
        long buyout = buyoutBox.getValue().isBlank() ? 0L : MarketUi.parseGold(buyoutBox.getValue());
        if (stack.isEmpty() || count < 1 || count > stack.getCount() || start < 1 || buyout < 0
                || (buyout != 0 && buyout <= start) || minutes <= 0) {
            player.displayClientMessage(Component.translatable("screen.eternalrealm.auction_hall.form_invalid")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("slot", pickSlot);
        args.addProperty("count", count);
        args.addProperty("start", start);
        args.addProperty("buyout", buyout);
        args.addProperty("minutes", minutes);
        MarketUi.send("auction.register", args);
        pickSlot = -1;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left + 8;
        int y = top + 20;
        for (Tab each : Tab.values()) {
            int w = tabWidth(each);
            if (MarketUi.inside(mouseX, mouseY, x, y, w, 13)) {
                tab = each;
                confirming = "";
                return true;
            }
            x += w + 2;
        }
        int listY = top + 38;
        if (tab == Tab.LIVE) {
            List<JsonObject> running = MarketUi.objects(data, "running");
            for (int i = 0; i < running.size(); i++) {
                if (MarketUi.inside(mouseX, mouseY, left + 8, listY + i * ROW, 180, ROW - 2)) {
                    String id = MarketUi.str(running.get(i), "id");
                    if (!id.equals(selected)) {
                        selected = id;
                        setBid(MarketUi.num(running.get(i), "next"));
                    }
                    return true;
                }
            }
        } else if (tab == Tab.MINE) {
            List<JsonObject> mine = MarketUi.objects(data, "mine");
            for (int i = 0; i < mine.size() && i < 6; i++) {
                if (MarketUi.inside(mouseX, mouseY, left + 8, listY + i * 24, 180, 22)) {
                    mineSelected = MarketUi.str(mine.get(i), "id");
                    confirming = "";
                    return true;
                }
            }
            if (MarketUi.bool(data, "atHall")) {
                int slot = bagSlotAt(mouseX, mouseY);
                if (slot >= 0) {
                    pickSlot = slot;
                    Player player = Minecraft.getInstance().player;
                    if (player != null) {
                        countBox.setValue(String.valueOf(player.getInventory().items.get(slot).getCount()));
                    }
                    if (minutes < 0 && !durations().isEmpty()) {
                        minutes = durations().get(Math.min(1, durations().size() - 1)).intValue();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.RESULTS) {
            int max = Math.max(0, MarketUi.objects(data, "recent").size() - 8);
            resultScroll = Math.max(0, Math.min(max, resultScroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** The bag slot under the mouse in the counter's grid (7 × 5, slots 1-35), or -1. */
    private int bagSlotAt(double mouseX, double mouseY) {
        int gridX = left + 196;
        int gridY = top + GRID_Y;
        for (int i = 0; i < Bag.CAPACITY; i++) {
            int cx = gridX + (i % 7) * 19;
            int cy = gridY + (i / 7) * 19;
            if (MarketUi.inside(mouseX, mouseY, cx, cy, 18, 18)) {
                Player player = Minecraft.getInstance().player;
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
        Component gold = Component.translatable("screen.eternalrealm.market.gold", MarketUi.gold(MarketUi.num(data,
                "gold")));
        graphics.drawString(font, gold, left + WIDTH - 8 - font.width(gold), top + 7, MarketUi.GOLD, true);
        drawTabs(graphics, mouseX, mouseY);
        ItemStack hovered = ItemStack.EMPTY;
        if (data == null || !MarketUi.bool(data, "ready")) {
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.market.loading"),
                    left + WIDTH / 2, top + HEIGHT / 2, MarketUi.MUTED);
        } else {
            hovered = switch (tab) {
                case LIVE -> drawLive(graphics, mouseX, mouseY);
                case QUEUE -> drawQueue(graphics, mouseX, mouseY);
                case MINE -> drawMine(graphics, mouseX, mouseY);
                case RESULTS -> drawResults(graphics, mouseX, mouseY);
            };
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private int tabWidth(Tab each) {
        return Math.max(40, font.width(tabName(each)) + 10);
    }

    private Component tabName(Tab each) {
        return switch (each) {
            case LIVE -> Component.translatable("screen.eternalrealm.auction_hall.tab_live",
                    MarketUi.objects(data, "running").size(), MarketUi.num(rules(), "maxRunning"));
            case QUEUE -> Component.translatable("screen.eternalrealm.auction_hall.tab_queue",
                    MarketUi.num(data, "queuedTotal"));
            case MINE -> Component.translatable("screen.eternalrealm.auction_hall.tab_mine");
            case RESULTS -> Component.translatable("screen.eternalrealm.auction_hall.tab_results");
        };
    }

    private JsonObject rules() {
        return data == null || !data.has("rules") ? null : data.getAsJsonObject("rules");
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

    private ItemStack drawLive(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int x = left + 8;
        int y = top + 38;
        List<JsonObject> running = MarketUi.objects(data, "running");
        int max = (int) Math.max(5, MarketUi.num(rules(), "maxRunning"));
        for (int i = 0; i < max && i < 6; i++) {
            int rowY = y + i * ROW;
            if (i >= running.size()) {
                graphics.fill(x, rowY, x + 180, rowY + ROW - 2, MarketUi.PANEL_INNER);
                graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.empty_place"),
                        x + 8, rowY + 10, 0xFF5A5268, false);
                continue;
            }
            JsonObject lot = running.get(i);
            boolean on = MarketUi.str(lot, "id").equals(selected);
            boolean hover = MarketUi.inside(mouseX, mouseY, x, rowY, 180, ROW - 2);
            graphics.fill(x, rowY, x + 180, rowY + ROW - 2, on ? MarketUi.ROW_SELECTED : hover ? MarketUi.ROW_HOVER
                    : MarketUi.ROW);
            ItemStack stack = MarketUi.stack(MarketUi.str(lot, "item"));
            MarketUi.cell(graphics, font, stack, x + 4, rowY + 5, MarketUi.bool(lot, "gold"));
            if (MarketUi.inside(mouseX, mouseY, x + 4, rowY + 5, 18, 18)) {
                hovered = stack;
            }
            graphics.drawString(font, MarketUi.fit(font, stack.getHoverName().getString(), 150), x + 26, rowY + 4,
                    MarketUi.rarityColor(stack, MarketUi.TEXT), false);
            graphics.drawString(font, MarketUi.gold(MarketUi.num(lot, "price")), x + 26, rowY + 15, MarketUi.GOLD,
                    false);
            long left2 = remaining(lot);
            Component time = MarketUi.remaining(left2);
            graphics.drawString(font, time, x + 176 - font.width(time), rowY + 15, left2 < 60_000 ? MarketUi.BAD
                    : MarketUi.TEXT, false);
            if (MarketUi.bool(lot, "leading")) {
                graphics.drawString(font, "★", x + 168, rowY + 4, MarketUi.GOOD, false);
            }
        }
        JsonObject lot = selectedLot();
        if (lot != null) {
            hovered = drawLotDetail(graphics, lot, mouseX, mouseY, hovered);
        } else {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.none_running"),
                    left + 196, top + 44, MarketUi.MUTED, false);
        }
        if (!MarketUi.bool(data, "atHall")) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.anywhere"), x,
                    top + HEIGHT - 12, 0xFF6A6078, false);
        }
        return hovered;
    }

    private ItemStack drawLotDetail(GuiGraphics graphics, JsonObject lot, int mouseX, int mouseY, ItemStack hovered) {
        int x = left + 196;
        int y = top + 38;
        ItemStack stack = MarketUi.stack(MarketUi.str(lot, "item"));
        MarketUi.cell(graphics, font, stack, x, y, MarketUi.bool(lot, "gold"));
        if (MarketUi.inside(mouseX, mouseY, x, y, 18, 18)) {
            hovered = stack;
        }
        graphics.drawString(font, MarketUi.fit(font, stack.getHoverName().getString(), 158), x + 22, y + 1,
                MarketUi.rarityColor(stack, MarketUi.TEXT), false);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.seller",
                MarketUi.str(lot, "seller")), x + 22, y + 10, MarketUi.MUTED, false);
        int line = y + 22;
        long buyout = MarketUi.num(lot, "buyout");
        graphics.drawString(font, Component.translatable(buyout > 0 ? "screen.eternalrealm.auction_hall.start_buyout"
                        : "screen.eternalrealm.auction_hall.start_only", MarketUi.gold(MarketUi.num(lot, "start")),
                MarketUi.gold(buyout)), x, line, MarketUi.MUTED, false);
        line += 11;
        String highest = MarketUi.str(lot, "highest");
        graphics.drawString(font, Component.translatable(highest.isEmpty()
                        ? "screen.eternalrealm.auction_hall.no_bid" : "screen.eternalrealm.auction_hall.current",
                MarketUi.gold(MarketUi.num(lot, "price")), highest), x, line, MarketUi.GOLD, false);
        line += 11;
        graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.next",
                MarketUi.gold(MarketUi.num(lot, "next")), MarketUi.num(lot, "bids")), x, line, MarketUi.TEXT, false);
        line += 11;
        long left2 = remaining(lot);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.remaining",
                MarketUi.remaining(left2)), x, line, left2 < 60_000 ? MarketUi.BAD : MarketUi.TEXT, false);
        line += 13;
        if (MarketUi.bool(lot, "leading")) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.you_lead"), x, line,
                    MarketUi.GOOD, false);
            line += 11;
        }
        for (JsonObject bid : MarketUi.objects(lot, "history")) {
            if (line > top + HEIGHT - 60) {
                break;
            }
            Component text = Component.translatable("screen.eternalrealm.auction_hall.history_line",
                    MarketUi.fit(font, MarketUi.str(bid, "name"), 70), MarketUi.gold(MarketUi.num(bid, "amount")),
                    MarketUi.ago(MarketUi.num(bid, "ago")));
            graphics.drawString(font, text, x, line, MarketUi.bool(bid, "me") ? MarketUi.GOOD : 0xFFB8AEC8, false);
            line += 10;
        }
        if (MarketUi.bool(lot, "mine")) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.your_lot"), x,
                    top + HEIGHT - 40, MarketUi.MUTED, false);
        }
        return hovered;
    }

    private ItemStack drawQueue(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int x = left + 8;
        int y = top + 38;
        List<JsonObject> queued = MarketUi.objects(data, "queued");
        if (queued.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.queue_empty"), x, y + 4,
                    MarketUi.MUTED, false);
        }
        for (int i = 0; i < queued.size() && i < 9; i++) {
            JsonObject lot = queued.get(i);
            int rowY = y + i * 20;
            graphics.fill(x, rowY, x + WIDTH - 16, rowY + 19, MarketUi.ROW);
            graphics.drawString(font, "#" + MarketUi.num(lot, "position"), x + 4, rowY + 6, MarketUi.RIM, false);
            ItemStack stack = MarketUi.stack(MarketUi.str(lot, "item"));
            MarketUi.cell(graphics, font, stack, x + 26, rowY, MarketUi.bool(lot, "gold"));
            if (MarketUi.inside(mouseX, mouseY, x + 26, rowY, 18, 18)) {
                hovered = stack;
            }
            graphics.drawString(font, MarketUi.fit(font, stack.getHoverName().getString(), 170), x + 50, rowY + 6,
                    MarketUi.rarityColor(stack, MarketUi.TEXT), false);
            Component price = Component.translatable("screen.eternalrealm.auction_hall.queue_line",
                    MarketUi.gold(MarketUi.num(lot, "start")), MarketUi.num(lot, "minutes"),
                    MarketUi.fit(font, MarketUi.str(lot, "seller"), 60));
            graphics.drawString(font, price, x + WIDTH - 20 - font.width(price), rowY + 6, MarketUi.MUTED, false);
        }
        long total = MarketUi.num(data, "queuedTotal");
        if (total > queued.size()) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.queue_more",
                    total - queued.size()), x, top + HEIGHT - 14, MarketUi.MUTED, false);
        }
        return hovered;
    }

    private ItemStack drawMine(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int x = left + 8;
        int y = top + 38;
        List<JsonObject> mine = MarketUi.objects(data, "mine");
        if (mine.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.mine_empty"), x, y + 4,
                    MarketUi.MUTED, false);
        }
        for (int i = 0; i < mine.size() && i < 6; i++) {
            JsonObject lot = mine.get(i);
            int rowY = y + i * 24;
            boolean on = MarketUi.str(lot, "id").equals(mineSelected);
            graphics.fill(x, rowY, x + 180, rowY + 22, on ? MarketUi.ROW_SELECTED
                    : MarketUi.inside(mouseX, mouseY, x, rowY, 180, 22) ? MarketUi.ROW_HOVER : MarketUi.ROW);
            ItemStack stack = MarketUi.stack(MarketUi.str(lot, "item"));
            MarketUi.cell(graphics, font, stack, x + 2, rowY + 2, MarketUi.bool(lot, "gold"));
            if (MarketUi.inside(mouseX, mouseY, x + 2, rowY + 2, 18, 18)) {
                hovered = stack;
            }
            graphics.drawString(font, MarketUi.fit(font, stack.getHoverName().getString(), 150), x + 24, rowY + 2,
                    MarketUi.rarityColor(stack, MarketUi.TEXT), false);
            graphics.drawString(font, stateText(lot), x + 24, rowY + 12, MarketUi.MUTED, false);
        }
        int formX = left + 196;
        if (!MarketUi.bool(data, "atHall")) {
            List<FormattedLine> lines = wrap(Component.translatable("screen.eternalrealm.auction_hall.counter_only"),
                    176);
            int ly = y + 4;
            for (FormattedLine line : lines) {
                graphics.drawString(font, line.text(), formX, ly, MarketUi.MUTED, false);
                ly += 10;
            }
            return hovered;
        }
        graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.pick_item"), formX,
                y, MarketUi.RIM, false);
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            for (int i = 0; i < Bag.CAPACITY; i++) {
                int slot = Bag.FIRST_SLOT + i;
                int cx = formX + (i % 7) * 19;
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
        }
        if (pickSlot >= 0) {
            int formY = top + FORM_Y;
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.count_label"), formX,
                    formY + 3, MarketUi.TEXT, false);
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.start_label"), formX,
                    formY + 19, MarketUi.TEXT, false);
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.buyout_label"), formX,
                    formY + 35, MarketUi.TEXT, false);
            long start = Math.max(0L, MarketUi.parseGold(startBox.getValue()));
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.fee_note",
                    MarketUi.gold(fee(Math.max(1L, start))), String.format("%.1f",
                            MarketUi.num(rules(), "taxBp") / 100.0)), formX, formY + 65, MarketUi.MUTED, false);
        }
        return hovered;
    }

    private Component stateText(JsonObject lot) {
        String state = MarketUi.str(lot, "state");
        return switch (state) {
            case "draft" -> Component.translatable("screen.eternalrealm.auction_hall.state_draft",
                    MarketUi.gold(MarketUi.num(lot, "start")));
            case "queued" -> Component.translatable("screen.eternalrealm.auction_hall.state_queued",
                    MarketUi.num(lot, "position"));
            case "running" -> Component.translatable("screen.eternalrealm.auction_hall.state_running",
                    MarketUi.gold(MarketUi.num(lot, "price")), MarketUi.remaining(remaining(lot)));
            case "sold" -> Component.translatable("screen.eternalrealm.auction_hall.state_sold",
                    MarketUi.gold(MarketUi.num(lot, "price")), MarketUi.str(lot, "highest"));
            case "unsold" -> Component.translatable("screen.eternalrealm.auction_hall.state_unsold");
            default -> Component.translatable("screen.eternalrealm.auction_hall.state_cancelled");
        };
    }

    private ItemStack drawResults(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int x = left + 8;
        int y = top + 38;
        List<JsonObject> recent = MarketUi.objects(data, "recent");
        if (recent.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.auction_hall.results_empty"), x,
                    y + 4, MarketUi.MUTED, false);
        }
        for (int i = resultScroll; i < recent.size() && i < resultScroll + 8; i++) {
            JsonObject lot = recent.get(i);
            int rowY = y + (i - resultScroll) * 22;
            graphics.fill(x, rowY, x + WIDTH - 16, rowY + 20, MarketUi.ROW);
            ItemStack stack = MarketUi.stack(MarketUi.str(lot, "item"));
            MarketUi.cell(graphics, font, stack, x + 2, rowY + 1, MarketUi.bool(lot, "gold"));
            if (MarketUi.inside(mouseX, mouseY, x + 2, rowY + 1, 18, 18)) {
                hovered = stack;
            }
            graphics.drawString(font, MarketUi.fit(font, stack.getHoverName().getString(), 150), x + 24, rowY + 6,
                    MarketUi.rarityColor(stack, MarketUi.TEXT), false);
            Component result = stateText(lot).copy().append(" ・ ").append(MarketUi.ago(MarketUi.num(lot, "ago")));
            graphics.drawString(font, result, x + WIDTH - 20 - font.width(result), rowY + 6,
                    "sold".equals(MarketUi.str(lot, "state")) ? MarketUi.GOLD : MarketUi.MUTED, false);
        }
        return hovered;
    }

    private record FormattedLine(net.minecraft.util.FormattedCharSequence text) {
    }

    private List<FormattedLine> wrap(Component text, int width) {
        List<FormattedLine> lines = new ArrayList<>();
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, width)) {
            lines.add(new FormattedLine(line));
        }
        return lines;
    }
}
