package com.eternalrealm.ui.guildhouse;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.guild.house.forge.GuildHouseUi;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The guild houses' catalogue (docs/modules/guildhouse.md), opened by Célestin the broker and by the gates' plaques: a
 * tab per tier with its four designs as cards (a picture of the house, its name and price), and the chosen design in
 * detail (description, size, storage, what buying or switching costs). The guild master buys, switches or gives the
 * house up here (twice to confirm); every member can pay into the house fund. The server decides everything and
 * sends the document again after each action.
 */
public final class GuildHouseScreen extends Screen {
    private static final int WIDTH = 404;
    private static final int HEIGHT = 262;
    private static final int CARD_W = 92;
    private static final int CARD_H = 78;
    private static final int PANEL = 0xF0121019;
    private static final int PANEL_INNER = 0xFF1C1826;
    private static final int RIM = 0xFFC9A55A;
    private static final int RIM_DARK = 0xFF3A2C18;
    private static final int ROW = 0xFF221D2B;
    private static final int ROW_HOVER = 0xFF2E2838;
    private static final int ROW_SELECTED = 0xFF4A3A20;
    private static final int TEXT = 0xFFE8E0C8;
    private static final int MUTED = 0xFF9A90A8;
    private static final int GOLD = 0xFFFFD866;
    private static final int GOOD = 0xFF8CE07A;
    private static final int BAD = 0xFFFF6A50;

    private JsonObject data;
    private long seenRevision = -1;
    private int tier = 1;
    private String selected = "";
    private String confirming = "";
    private String typedAmount = "";
    private Button mainButton;
    private Button sellButton;
    private Button donateButton;
    private EditBox amountBox;

    private GuildHouseScreen() {
        super(Component.translatable("screen.eternalrealm.guildhouse.title"));
    }

    /** A new catalogue document: opens the screen when the server says so, focused on the asked design. */
    public static void accept(JsonObject json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (bool(json, "open")) {
            GuildHouseScreen screen = minecraft.screen instanceof GuildHouseScreen open ? open : new GuildHouseScreen();
            screen.focus(str(json, "focus"), json);
            if (minecraft.screen != screen) {
                minecraft.setScreen(screen);
            }
        }
    }

    private void focus(String design, JsonObject json) {
        String target = design.isEmpty() ? str(json, "owned") : design;
        for (JsonObject each : objects(json, "designs")) {
            if (str(each, "id").equals(target)) {
                selected = target;
                tier = (int) num(each, "tier");
            }
        }
        if (selected.isEmpty()) {
            List<JsonObject> first = designsOf(json, tier);
            selected = first.isEmpty() ? "" : str(first.get(0), "id");
        }
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        mainButton = addRenderableWidget(Button.builder(Component.empty(), b -> mainAction())
                .bounds(left + 8, top + HEIGHT - 46, 190, 18).build());
        sellButton = addRenderableWidget(Button.builder(Component.empty(), b -> sell())
                .bounds(left + 204, top + HEIGHT - 46, 192, 18).build());
        amountBox = new EditBox(font, left + WIDTH - 8 - 64 - 4 - 96, top + HEIGHT - 24, 96, 16,
                Component.translatable("screen.eternalrealm.guildhouse.amount"));
        amountBox.setMaxLength(10);
        amountBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        amountBox.setHint(Component.translatable("screen.eternalrealm.guildhouse.amount"));
        amountBox.setValue(typedAmount);
        amountBox.setResponder(value -> typedAmount = value);
        addRenderableWidget(amountBox);
        donateButton = addRenderableWidget(Button.builder(Component.translatable(
                "screen.eternalrealm.guildhouse.donate"), b -> donate())
                .bounds(left + WIDTH - 8 - 64, top + HEIGHT - 25, 64, 18).build());
        refresh();
        updateWidgets();
    }

    @Override
    public void tick() {
        amountBox.tick();
        refresh();
        updateWidgets();
    }

    private void refresh() {
        if (ClientUiState.revision() == seenRevision) {
            return;
        }
        seenRevision = ClientUiState.revision();
        JsonObject next = ClientUiState.get(GuildHouseUi.CATALOGUE).orElse(null);
        if (next != null && next != data) {
            data = next;
            confirming = "";
            if (selected.isEmpty()) {
                focus(str(data, "focus"), data);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ actions

    private boolean master() {
        return "master".equals(str(data, "rank"));
    }

    private boolean ready() {
        return data != null && bool(data, "available") && bool(data, "atTerrace");
    }

    private void updateWidgets() {
        JsonObject design = design(selected);
        String owned = str(data, "owned");
        boolean inGuild = data != null && data.has("guild");
        boolean mine = !owned.isEmpty() && owned.equals(selected);
        mainButton.visible = design != null && !mine;
        mainButton.active = ready() && inGuild && master();
        long cost = num(design, "cost");
        String key = (owned.isEmpty() ? "buy:" : "switch:") + selected;
        if (confirming.equals(key)) {
            mainButton.setMessage(Component.translatable("screen.eternalrealm.guildhouse.confirm"));
        } else if (owned.isEmpty()) {
            mainButton.setMessage(Component.translatable("screen.eternalrealm.guildhouse.buy", gold(cost)));
        } else if (cost >= 0) {
            mainButton.setMessage(Component.translatable("screen.eternalrealm.guildhouse.switch_pay", gold(cost)));
        } else {
            mainButton.setMessage(Component.translatable("screen.eternalrealm.guildhouse.switch_refund", gold(-cost)));
        }
        sellButton.visible = mine;
        sellButton.active = ready() && master();
        sellButton.setMessage(confirming.equals("sell:" + selected)
                ? Component.translatable("screen.eternalrealm.guildhouse.confirm")
                : Component.translatable("screen.eternalrealm.guildhouse.sell", gold(num(data, "refund"))));
        amountBox.visible = inGuild;
        donateButton.visible = inGuild;
        long amount = parse(typedAmount);
        donateButton.active = data != null && bool(data, "available") && amount > 0 && amount <= num(data, "gold");
        donateButton.setMessage(confirming.equals("donate")
                ? Component.translatable("screen.eternalrealm.guildhouse.confirm")
                : Component.translatable("screen.eternalrealm.guildhouse.donate"));
    }

    private void mainAction() {
        String owned = str(data, "owned");
        String key = (owned.isEmpty() ? "buy:" : "switch:") + selected;
        if (!confirming.equals(key)) {
            confirming = key;
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("design", selected);
        send(owned.isEmpty() ? "guildhouse.buy" : "guildhouse.switch", args);
    }

    private void sell() {
        if (!confirming.equals("sell:" + selected)) {
            confirming = "sell:" + selected;
            return;
        }
        confirming = "";
        send("guildhouse.sell", new JsonObject());
    }

    private void donate() {
        long amount = parse(typedAmount);
        if (amount <= 0) {
            return;
        }
        if (!confirming.equals("donate")) {
            confirming = "donate";
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("amount", amount);
        if (!selected.isEmpty()) {
            args.addProperty("design", selected);
        }
        send("guildhouse.donate", args);
        typedAmount = "";
        amountBox.setValue("");
    }

    private static void send(String action, JsonObject args) {
        ERNetwork.sendToServer(new UiActionPacket(action, args));
    }

    private static long parse(String text) {
        try {
            return text.isEmpty() ? 0 : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        for (int t = 1; t <= 5; t++) {
            if (inside(mouseX, mouseY, tabX(left, t), top + 22, tabW(), 14)) {
                if (tier != t) {
                    tier = t;
                    confirming = "";
                    List<JsonObject> designs = designsOf(data, tier);
                    String owned = str(data, "owned");
                    selected = designs.stream().map(d -> str(d, "id")).filter(owned::equals).findFirst()
                            .orElse(designs.isEmpty() ? "" : str(designs.get(0), "id"));
                }
                return true;
            }
        }
        List<JsonObject> cards = designsOf(data, tier);
        for (int i = 0; i < cards.size(); i++) {
            if (inside(mouseX, mouseY, cardX(left, i), top + 40, CARD_W, CARD_H)) {
                if (!selected.equals(str(cards.get(i), "id"))) {
                    selected = str(cards.get(i), "id");
                    confirming = "";
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, RIM_DARK);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, RIM);
        graphics.drawString(font, title, left + 8, top + 8, RIM, true);
        if (data == null) {
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.guildhouse.loading"),
                    left + WIDTH / 2, top + HEIGHT / 2, MUTED);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        Component goldLine = Component.translatable("screen.eternalrealm.guildhouse.gold", gold(num(data, "gold")));
        graphics.drawString(font, goldLine, left + WIDTH - 8 - font.width(goldLine), top + 8, GOLD, true);
        for (int t = 1; t <= 5; t++) {
            int x = tabX(left, t);
            boolean on = t == tier;
            boolean hover = inside(mouseX, mouseY, x, top + 22, tabW(), 14);
            graphics.fill(x, top + 22, x + tabW(), top + 36, on ? ROW_SELECTED : hover ? ROW_HOVER : PANEL_INNER);
            graphics.renderOutline(x, top + 22, tabW(), 14, on ? RIM : RIM_DARK);
            Component name = Component.literal("★".repeat(t) + " ")
                    .append(Component.translatable("guildhouse.eternalrealm.tier." + t));
            graphics.drawString(font, name, x + (tabW() - font.width(name)) / 2, top + 25, on ? 0xFFFFE8A8 : MUTED,
                    false);
        }
        List<JsonObject> cards = designsOf(data, tier);
        String owned = str(data, "owned");
        for (int i = 0; i < cards.size(); i++) {
            JsonObject card = cards.get(i);
            String id = str(card, "id");
            int x = cardX(left, i);
            int y = top + 40;
            boolean on = id.equals(selected);
            boolean hover = inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
            graphics.fill(x, y, x + CARD_W, y + CARD_H, on ? ROW_SELECTED : hover ? ROW_HOVER : ROW);
            graphics.blit(thumbnail(id), x + 2, y + 2, CARD_W - 4, 50, 0.0F, 0.0F, 128, 72, 128, 72);
            int base = 0xFF000000 | (int) num(card, "base");
            int accent = 0xFF000000 | (int) num(card, "accent");
            graphics.fill(x + 2, y + 52, x + CARD_W - 2, y + 54, base);
            graphics.fill(x + CARD_W / 2, y + 52, x + CARD_W - 2, y + 54, accent);
            Component name = Component.translatable("guildhouse.eternalrealm.design." + id);
            graphics.drawCenteredString(font, ellipsize(name, CARD_W - 4), x + CARD_W / 2, y + 56,
                    id.equals(owned) ? GOOD : TEXT);
            graphics.drawCenteredString(font, Component.literal(gold(num(card, "price")) + " G"), x + CARD_W / 2,
                    y + 67, GOLD);
            graphics.renderOutline(x, y, CARD_W, CARD_H, on ? RIM : id.equals(owned) ? GOOD : RIM_DARK);
        }
        drawDetail(graphics, left, top);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawDetail(GuiGraphics graphics, int left, int top) {
        JsonObject design = design(selected);
        int y = top + 40 + CARD_H + 6;
        graphics.fill(left + 6, y - 2, left + WIDTH - 6, y - 1, RIM_DARK);
        if (design == null) {
            return;
        }
        String id = str(design, "id");
        int tierOf = (int) num(design, "tier");
        Component name = Component.translatable("guildhouse.eternalrealm.design." + id);
        graphics.drawString(font, name, left + 8, y + 2, GOLD, true);
        Component stars = Component.literal("★".repeat(tierOf) + "☆".repeat(5 - tierOf));
        graphics.drawString(font, stars, left + 8 + font.width(name) + 8, y + 2, 0xFFFFC840, false);
        Component price = Component.translatable("screen.eternalrealm.guildhouse.price", gold(num(design, "price")));
        graphics.drawString(font, price, left + WIDTH - 8 - font.width(price), y + 2, GOLD, false);
        List<FormattedCharSequence> lines = font.split(FormattedText.composite(
                Component.translatable("guildhouse.eternalrealm.design." + id + ".desc")), WIDTH - 16);
        int ly = y + 14;
        for (int i = 0; i < lines.size() && i < 3; i++) {
            graphics.drawString(font, lines.get(i), left + 8, ly, TEXT, false);
            ly += 10;
        }
        Component facts = Component.translatable("screen.eternalrealm.guildhouse.facts", num(design, "w"),
                num(design, "d"), num(design, "h"), num(design, "storage"), num(design, "vaults"));
        graphics.drawString(font, facts, left + 8, ly + 1, MUTED, false);
        graphics.drawString(font, status(design), left + 8, top + HEIGHT - 60, statusColor(design), false);
        if (data.has("guild")) {
            Component fund = Component.translatable("screen.eternalrealm.guildhouse.fund", str(data, "guild"),
                    gold(num(data, "fund")));
            graphics.drawString(font, fund, left + 8, top + HEIGHT - 20, TEXT, false);
        }
    }

    private Component status(JsonObject design) {
        String owned = str(data, "owned");
        if (!bool(data, "available")) {
            return Component.translatable("screen.eternalrealm.guildhouse.status.closed");
        }
        if (!data.has("guild")) {
            return Component.translatable("screen.eternalrealm.guildhouse.status.no_guild");
        }
        if (str(design, "id").equals(owned)) {
            return bool(data, "ready") ? Component.translatable("screen.eternalrealm.guildhouse.status.yours")
                    : Component.translatable("screen.eternalrealm.guildhouse.status.building", num(data, "percent"));
        }
        if (!master()) {
            return Component.translatable("screen.eternalrealm.guildhouse.status.master_only");
        }
        if (!bool(data, "atTerrace")) {
            return Component.translatable("screen.eternalrealm.guildhouse.status.at_terrace");
        }
        long cost = num(design, "cost");
        long fund = num(data, "fund");
        if (cost > num(data, "gold") + fund) {
            return Component.translatable("screen.eternalrealm.guildhouse.status.short", gold(cost - fund - num(data,
                    "gold")));
        }
        return owned.isEmpty() ? Component.translatable("screen.eternalrealm.guildhouse.status.can_buy")
                : Component.translatable("screen.eternalrealm.guildhouse.status.can_switch",
                Component.translatable("guildhouse.eternalrealm.design." + owned), num(data, "refundPercent"));
    }

    private int statusColor(JsonObject design) {
        String id = str(design, "id");
        if (id.equals(str(data, "owned"))) {
            return GOOD;
        }
        return bool(data, "available") && data.has("guild") && master() && bool(data, "atTerrace")
                && num(design, "cost") <= num(data, "gold") + num(data, "fund") ? TEXT : BAD;
    }

    private Component ellipsize(Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String plain = text.getString();
        while (!plain.isEmpty() && font.width(plain + "…") > width) {
            plain = plain.substring(0, plain.length() - 1);
        }
        return Component.literal(plain + "…");
    }

    private static ResourceLocation thumbnail(String id) {
        return new ResourceLocation(ModInfo.MOD_ID, "textures/gui/guildhouse/" + id + ".png");
    }

    private static int tabW() {
        return (WIDTH - 16 - 4 * 3) / 5;
    }

    private static int tabX(int left, int t) {
        return left + 8 + (t - 1) * (tabW() + 3);
    }

    private static int cardX(int left, int index) {
        return left + 8 + index * (CARD_W + 6);
    }

    private JsonObject design(String id) {
        for (JsonObject each : objects(data, "designs")) {
            if (str(each, "id").equals(id)) {
                return each;
            }
        }
        return null;
    }

    private static List<JsonObject> designsOf(JsonObject json, int tier) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonObject each : objects(json, "designs")) {
            if (num(each, "tier") == tier) {
                out.add(each);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ JSON helpers

    static String gold(long amount) {
        return String.format("%,d", amount);
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    static List<JsonObject> objects(JsonObject data, String key) {
        List<JsonObject> list = new ArrayList<>();
        if (data != null && data.has(key) && data.get(key).isJsonArray()) {
            JsonArray array = data.getAsJsonArray(key);
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    list.add(element.getAsJsonObject());
                }
            }
        }
        return list;
    }

    static String str(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : "";
    }

    static long num(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isNumber() ? object.get(key).getAsLong() : 0L;
    }

    static boolean bool(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isBoolean() && object.get(key).getAsBoolean();
    }
}
