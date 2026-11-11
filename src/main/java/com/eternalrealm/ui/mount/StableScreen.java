package com.eternalrealm.ui.mount;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.mount.MountKind;
import com.eternalrealm.mount.MountLook;
import com.eternalrealm.mount.MountLooks;
import com.eternalrealm.mount.MountRules;
import com.eternalrealm.mount.MountTraits;
import com.eternalrealm.mount.forge.MountEntities;
import com.eternalrealm.mount.forge.MountEntity;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The stable (docs/modules/mounts.md). At the stablemaster: a tab per kind with the {@value MountRules#OFFERS} horses or
 * griffins he shows (their looks turning on a stand, their values hidden as "???"), buying one (twice to confirm; its
 * values are then revealed, counting up to its grade), being shown others for a fee; the kept mounts with their values,
 * to pick, call or make into a deed; and the deeds in the bag, to take in. With the choose key (anywhere) only the kept
 * mounts. The server decides everything and sends the screen again after each action.
 */
public final class StableScreen extends Screen {
    private static final int WIDTH = 384;
    private static final int HEIGHT = 236;
    private static final int CARD_W = 88;
    private static final int CARD_H = 150;
    private static final int ROW = 15;
    private static final int REVEAL_TICKS = 30;
    private static final int PANEL = 0xF0141018;
    private static final int PANEL_INNER = 0xFF1E1A26;
    private static final int RIM = 0xFFC9A55A;
    private static final int RIM_DARK = 0xFF3A2C18;
    private static final int ROW_COLOR = 0xFF221D2B;
    private static final int ROW_HOVER = 0xFF2E2838;
    private static final int ROW_SELECTED = 0xFF4A3A20;
    private static final int TEXT = 0xFFE8E0C8;
    private static final int MUTED = 0xFF9A90A8;
    private static final int GOLD = 0xFFFFD866;
    private static final int BAD = 0xFFFF6A50;
    private static final int RARE = 0xFFE0A0FF;

    private enum Tab {
        HORSE, GRIFFIN, BIKE, STABLE, DEEDS
    }

    private final boolean stableMode;
    private Tab tab;
    private int left;
    private int top;
    private long seenRevision = -1;
    private JsonObject data;
    private String selected = "";
    private String confirming = "";
    private JsonObject reveal;
    private String revealed = "";
    private int revealTicks;
    private int ticks;
    private int listScroll;
    private final Map<String, MountEntity> previews = new HashMap<>();

    private final Button[] buyButtons = new Button[MountRules.OFFERS];
    private Button rerollButton;
    private Button bikeButton;
    private Button callButton;
    private Button pickButton;
    private Button deedButton;
    private Button revealButton;

    private StableScreen(boolean stableMode) {
        super(Component.translatable(stableMode ? MountRules.STABLE_TITLE : MountRules.CHOOSE_TITLE));
        this.stableMode = stableMode;
        this.tab = stableMode ? Tab.HORSE : Tab.STABLE;
    }

    /** New stable data: opens the screen when the server says so; an open screen refreshes itself. */
    public static void accept(JsonObject json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (bool(json, "open")) {
            boolean stable = "stable".equals(str(json, "mode"));
            if (!(minecraft.screen instanceof StableScreen screen) || screen.stableMode != stable) {
                minecraft.setScreen(new StableScreen(stable));
            }
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        for (int i = 0; i < buyButtons.length; i++) {
            int index = i;
            buyButtons[i] = addRenderableWidget(Button.builder(Component.empty(), button -> buy(index))
                    .bounds(cardX(i) + 4, top + 50 + CARD_H - 20, CARD_W - 8, 16).build());
        }
        rerollButton = addRenderableWidget(Button.builder(Component.empty(), button -> reroll())
                .bounds(left + WIDTH / 2 - 90, top + HEIGHT - 22, 180, 16).build());
        bikeButton = addRenderableWidget(Button.builder(Component.empty(), button -> buy(0))
                .bounds(left + WIDTH / 2 - 70, top + HEIGHT - 26, 140, 18).build());
        int panelX = left + 178;
        callButton = addRenderableWidget(Button.builder(Component.translatable(MountRules.SCREEN_CALL),
                button -> select(true)).bounds(panelX, top + HEIGHT - 24, 62, 16).build());
        pickButton = addRenderableWidget(Button.builder(Component.translatable(MountRules.SCREEN_PICK),
                button -> select(false)).bounds(panelX + 64, top + HEIGHT - 24, 58, 16).build());
        deedButton = addRenderableWidget(Button.builder(Component.empty(), button -> deed())
                .bounds(panelX + 124, top + HEIGHT - 24, 74, 16).build());
        revealButton = addRenderableWidget(Button.builder(Component.translatable(MountRules.SCREEN_REVEAL_OK),
                button -> closeReveal()).bounds(width / 2 - 50, height / 2 + 62, 100, 16).build());
        refresh();
        updateWidgets();
    }

    @Override
    public void tick() {
        ticks++;
        for (MountEntity preview : previews.values()) {
            preview.tickCount++;
        }
        if (reveal != null && revealTicks < REVEAL_TICKS + 20) {
            revealTicks++;
            if (revealTicks == REVEAL_TICKS && minecraft != null && minecraft.player != null) {
                MountTraits.Grade grade = grade(reveal);
                minecraft.player.playSound(grade.ordinal() >= MountTraits.Grade.S.ordinal()
                        ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F,
                        grade.ordinal() >= MountTraits.Grade.S.ordinal() ? 1.0F : 1.3F);
            }
        }
        refresh();
        updateWidgets();
    }

    private void refresh() {
        if (ClientUiState.revision() == seenRevision) {
            return;
        }
        seenRevision = ClientUiState.revision();
        JsonObject next = ClientUiState.get(MountRules.STABLE_CHANNEL).orElse(null);
        if (next == null || next == data) {
            return;
        }
        data = next;
        if (data.has("reveal") && data.get("reveal").isJsonObject()) {
            JsonObject bought = data.getAsJsonObject("reveal");
            if (!str(bought, "id").equals(revealed)) {
                revealed = str(bought, "id");
                reveal = bought;
                revealTicks = 0;
                selected = revealed;
            }
        }
        confirming = "";
        if (mount(selected).isEmpty()) {
            List<JsonObject> mounts = objects(data, "mounts");
            selected = mounts.stream().filter(each -> bool(each, "selected")).findFirst()
                    .or(() -> mounts.stream().findFirst()).map(each -> str(each, "id")).orElse("");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ widgets

    private void updateWidgets() {
        boolean modal = reveal != null;
        MountKind kind = tabKind();
        JsonObject kindData = kind == null ? null : kindJson(kind);
        boolean offersTab = !modal && kind != null && kind != MountKind.BIKE;
        boolean atStable = bool(data, "atStable");
        String status = str(kindData, "status");
        List<JsonObject> offers = objects(kindData, "offers");
        for (int i = 0; i < buyButtons.length; i++) {
            Button button = buyButtons[i];
            button.visible = offersTab && i < offers.size();
            button.active = atStable && "ok".equals(status);
            String key = "buy:" + (kind == null ? "" : kind.id()) + ":" + i;
            button.setMessage(confirming.equals(key) ? Component.translatable(MountRules.SCREEN_CONFIRM)
                    : Component.translatable(MountRules.SCREEN_BUY, gold(num(kindData, "price"))));
        }
        rerollButton.visible = offersTab;
        rerollButton.active = atStable && num(data, "gold") >= num(kindData, "reroll");
        String rerollKey = "reroll:" + (kind == null ? "" : kind.id());
        rerollButton.setMessage(confirming.equals(rerollKey) ? Component.translatable(MountRules.SCREEN_CONFIRM)
                : Component.translatable(MountRules.SCREEN_REROLL, gold(num(kindData, "reroll"))));
        bikeButton.visible = !modal && kind == MountKind.BIKE;
        bikeButton.active = atStable && "ok".equals(status);
        bikeButton.setMessage(confirming.equals("buy:bike:0") ? Component.translatable(MountRules.SCREEN_CONFIRM)
                : "already_owned".equals(status) ? Component.translatable(MountRules.STABLE_OWNED)
                : Component.translatable(MountRules.SCREEN_BUY, gold(num(kindData, "price"))));

        Optional<JsonObject> mount = mount(selected);
        boolean stableTab = !modal && tab == Tab.STABLE && mount.isPresent();
        callButton.visible = stableTab;
        callButton.active = mount.map(each -> !bool(each, "riding")).orElse(false);
        pickButton.visible = stableTab;
        pickButton.active = mount.map(each -> !bool(each, "selected")).orElse(false);
        deedButton.visible = stableTab && stableMode && mount.map(each -> bool(each, "tradeable")).orElse(false);
        deedButton.active = atStable;
        deedButton.setMessage(confirming.equals("deed:" + selected) ? Component.translatable(MountRules.SCREEN_CONFIRM)
                : Component.translatable(MountRules.SCREEN_DEED));
        revealButton.visible = modal && revealTicks >= REVEAL_TICKS;
    }

    private MountKind tabKind() {
        return switch (tab) {
            case HORSE -> MountKind.HORSE;
            case GRIFFIN -> MountKind.GRIFFIN;
            case BIKE -> MountKind.BIKE;
            default -> null;
        };
    }

    private JsonObject kindJson(MountKind kind) {
        for (JsonObject each : objects(data, "kinds")) {
            if (str(each, "kind").equals(kind.id())) {
                return each;
            }
        }
        return null;
    }

    private Optional<JsonObject> mount(String id) {
        for (JsonObject each : objects(data, "mounts")) {
            if (str(each, "id").equals(id)) {
                return Optional.of(each);
            }
        }
        return Optional.empty();
    }

    private int cardX(int index) {
        return left + 7 + index * (CARD_W + 6);
    }

    // ------------------------------------------------------------------ actions

    private void send(String action, JsonObject args) {
        ERNetwork.sendToServer(new UiActionPacket(action, args));
    }

    private void buy(int index) {
        MountKind kind = tabKind();
        if (kind == null) {
            return;
        }
        String key = "buy:" + kind.id() + ":" + index;
        if (!confirming.equals(key)) {
            confirming = key;
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("kind", kind.id());
        args.addProperty("index", index);
        send(MountRules.BUY_ACTION, args);
    }

    private void reroll() {
        MountKind kind = tabKind();
        if (kind == null) {
            return;
        }
        String key = "reroll:" + kind.id();
        if (!confirming.equals(key)) {
            confirming = key;
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("kind", kind.id());
        send(MountRules.REROLL_ACTION, args);
    }

    private void select(boolean call) {
        if (selected.isEmpty()) {
            return;
        }
        JsonObject args = new JsonObject();
        args.addProperty("id", selected);
        if (call) {
            args.addProperty("call", true);
            onClose();
        }
        send(MountRules.SELECT_ACTION, args);
    }

    private void deed() {
        String key = "deed:" + selected;
        if (!confirming.equals(key)) {
            confirming = key;
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("id", selected);
        send(MountRules.DEED_ACTION, args);
    }

    private void redeem(int slot) {
        String key = "redeem:" + slot;
        if (!confirming.equals(key)) {
            confirming = key;
            return;
        }
        confirming = "";
        JsonObject args = new JsonObject();
        args.addProperty("slot", slot);
        send(MountRules.REDEEM_ACTION, args);
    }

    private void closeReveal() {
        reveal = null;
        tab = Tab.STABLE;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (reveal != null) {
            if (revealTicks < REVEAL_TICKS - 1) {
                revealTicks = REVEAL_TICKS - 1; // a click skips the count-up
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (stableMode) {
            int x = left + 8;
            for (Tab each : Tab.values()) {
                int w = tabWidth(each);
                if (inside(mouseX, mouseY, x, top + 20, w, 13)) {
                    if (tab != each) {
                        tab = each;
                        confirming = "";
                        listScroll = 0;
                    }
                    return true;
                }
                x += w + 2;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (tab == Tab.STABLE) {
            List<JsonObject> mounts = objects(data, "mounts");
            for (int i = listScroll; i < mounts.size() && i < listScroll + rows(); i++) {
                if (inside(mouseX, mouseY, left + 8, listTop() + (i - listScroll) * ROW, 164, ROW - 1)) {
                    selected = str(mounts.get(i), "id");
                    confirming = "";
                    return true;
                }
            }
        } else if (tab == Tab.DEEDS) {
            List<JsonObject> deeds = objects(data, "deeds");
            for (int i = 0; i < deeds.size() && i < 8; i++) {
                int rowY = top + 52 + i * 22;
                if (inside(mouseX, mouseY, left + WIDTH - 8 - 84, rowY + 3, 80, 14) && bool(data, "atStable")
                        && "ok".equals(str(deeds.get(i), "redeem"))) {
                    redeem((int) num(deeds.get(i), "slot"));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.STABLE) {
            int max = Math.max(0, objects(data, "mounts").size() - rows());
            listScroll = Math.max(0, Math.min(max, listScroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int listTop() {
        return top + (stableMode ? 40 : 26);
    }

    private int rows() {
        return (top + HEIGHT - 10 - listTop()) / ROW;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, RIM_DARK);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, RIM);
        graphics.drawString(font, title, left + 8, top + 7, RIM, true);
        Component goldLine = Component.translatable(MountRules.STABLE_GOLD, gold(num(data, "gold")));
        graphics.drawString(font, goldLine, left + WIDTH - 8 - font.width(goldLine), top + 7, GOLD, true);
        if (data == null) {
            graphics.drawCenteredString(font, Component.translatable(MountRules.SCREEN_LOADING), left + WIDTH / 2,
                    top + HEIGHT / 2, MUTED);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        if (stableMode) {
            drawTabs(graphics, mouseX, mouseY);
        }
        switch (tab) {
            case HORSE, GRIFFIN -> drawOffers(graphics, tabKind(), mouseX, mouseY);
            case BIKE -> drawBike(graphics);
            case STABLE -> drawStable(graphics, mouseX, mouseY);
            case DEEDS -> drawDeeds(graphics, mouseX, mouseY);
        }
        if (reveal != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 300.0F);
            drawReveal(graphics);
            graphics.pose().popPose();
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, reveal != null ? 400.0F : 0.0F);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }

    private int tabWidth(Tab each) {
        return Math.max(46, font.width(tabName(each)) + 10);
    }

    private Component tabName(Tab each) {
        return switch (each) {
            case HORSE -> Component.translatable(MountKind.HORSE.translationKey());
            case GRIFFIN -> Component.translatable(MountKind.GRIFFIN.translationKey());
            case BIKE -> Component.translatable(MountKind.BIKE.translationKey());
            case STABLE -> Component.translatable(MountRules.SCREEN_TAB_STABLE, objects(data, "mounts").size(),
                    num(data, "capacity"));
            case DEEDS -> Component.translatable(MountRules.SCREEN_TAB_DEEDS, objects(data, "deeds").size());
        };
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + 8;
        for (Tab each : Tab.values()) {
            int w = tabWidth(each);
            boolean on = each == tab;
            boolean hover = inside(mouseX, mouseY, x, top + 20, w, 13);
            graphics.fill(x, top + 20, x + w, top + 33, on ? ROW_SELECTED : hover ? ROW_HOVER : PANEL_INNER);
            graphics.renderOutline(x, top + 20, w, 13, on ? RIM : RIM_DARK);
            Component name = tabName(each);
            graphics.drawString(font, name, x + (w - font.width(name)) / 2, top + 23, on ? 0xFFFFE8A8 : MUTED, false);
            x += w + 2;
        }
    }

    private void drawOffers(GuiGraphics graphics, MountKind kind, int mouseX, int mouseY) {
        JsonObject kindData = kindJson(kind);
        Component line = Component.translatable(MountRules.SCREEN_KIND_LINE, num(kindData, "level"),
                gold(num(kindData, "price")), str(kindData, "speed"));
        graphics.drawString(font, line, left + 8, top + 38, TEXT, false);
        Component status = statusText(kindData);
        if (status != null) {
            graphics.drawString(font, status, left + WIDTH - 8 - font.width(status), top + 38, BAD, false);
        }
        List<JsonObject> offers = objects(kindData, "offers");
        for (int i = 0; i < offers.size() && i < MountRules.OFFERS; i++) {
            JsonObject offer = offers.get(i);
            int x = cardX(i);
            int y = top + 50;
            boolean hover = inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
            graphics.fill(x, y, x + CARD_W, y + CARD_H, hover ? ROW_HOVER : ROW_COLOR);
            boolean rare = bool(offer, "rare");
            graphics.renderOutline(x, y, CARD_W, CARD_H, rare ? RARE : RIM_DARK);
            MountLook look = new MountLook(str(offer, "coat"), str(offer, "shape"));
            float yaw = 205.0F + 28.0F * Mth.sin((ticks + i * 17) * 0.035F);
            drawMount(graphics, kind, look, x + CARD_W / 2, y + 74, kind == MountKind.GRIFFIN ? 21 : 27, yaw,
                    x + 1, y + 1, x + CARD_W - 1, y + 84);
            int ty = y + 88;
            Component coat = Component.translatable(MountLooks.coatKey(kind, look.coat()));
            graphics.drawCenteredString(font, coat, x + CARD_W / 2, ty, rare ? RARE : TEXT);
            Component shape = Component.translatable(MountLooks.shapeKey(kind, look.shape()));
            graphics.drawCenteredString(font, shape, x + CARD_W / 2, ty + 11, MUTED);
            if (rare) {
                graphics.drawCenteredString(font, Component.translatable(MountRules.SCREEN_RARE), x + CARD_W / 2,
                        ty + 22, RARE);
            }
            graphics.drawCenteredString(font, Component.translatable(MountRules.SCREEN_HIDDEN), x + CARD_W / 2,
                    ty + (rare ? 33 : 24), 0xFF8A82A0);
        }
        if (!bool(data, "atStable")) {
            graphics.drawCenteredString(font, Component.translatable(MountRules.SCREEN_AT_STABLE), left + WIDTH / 2,
                    top + HEIGHT - 34, 0xFF8A82A0);
        } else {
            graphics.drawCenteredString(font, Component.translatable(MountRules.SCREEN_VALUES_LATER),
                    left + WIDTH / 2, top + HEIGHT - 34, 0xFF8A82A0);
        }
    }

    private Component statusText(JsonObject kindData) {
        return switch (str(kindData, "status")) {
            case "level_too_low" -> Component.translatable(MountRules.STABLE_LOCKED, num(kindData, "level"));
            case "not_enough_gold" -> Component.translatable(MountRules.SCREEN_NO_GOLD);
            case "stable_full" -> Component.translatable(MountRules.STABLE_FULL, num(data, "capacity"));
            case "already_owned" -> Component.translatable(MountRules.STABLE_OWNED);
            default -> null;
        };
    }

    private void drawBike(GuiGraphics graphics) {
        JsonObject kindData = kindJson(MountKind.BIKE);
        Component line = Component.translatable(MountRules.SCREEN_KIND_LINE, num(kindData, "level"),
                gold(num(kindData, "price")), str(kindData, "speed"));
        graphics.drawString(font, line, left + 8, top + 38, TEXT, false);
        Component status = statusText(kindData);
        if (status != null) {
            graphics.drawString(font, status, left + WIDTH - 8 - font.width(status), top + 38, BAD, false);
        }
        float yaw = 205.0F + 28.0F * Mth.sin(ticks * 0.035F);
        drawMount(graphics, MountKind.BIKE, MountLooks.classic(MountKind.BIKE), left + WIDTH / 2, top + 150, 50, yaw,
                left + 8, top + 50, left + WIDTH - 8, top + 160);
        graphics.drawCenteredString(font, Component.translatable(MountKind.BIKE.descriptionKey()), left + WIDTH / 2,
                top + 166, MUTED);
        graphics.drawCenteredString(font, Component.translatable(MountRules.SCREEN_MACHINE), left + WIDTH / 2,
                top + 180, 0xFF8A82A0);
    }

    private void drawStable(GuiGraphics graphics, int mouseX, int mouseY) {
        List<JsonObject> mounts = objects(data, "mounts");
        int y0 = listTop();
        if (mounts.isEmpty()) {
            graphics.drawString(font, Component.translatable(MountRules.STABLE_NONE), left + 12, y0 + 4, MUTED, false);
            return;
        }
        for (int i = listScroll; i < mounts.size() && i < listScroll + rows(); i++) {
            JsonObject mount = mounts.get(i);
            int rowY = y0 + (i - listScroll) * ROW;
            boolean on = str(mount, "id").equals(selected);
            boolean hover = inside(mouseX, mouseY, left + 8, rowY, 164, ROW - 1);
            graphics.fill(left + 8, rowY, left + 172, rowY + ROW - 1, on ? ROW_SELECTED : hover ? ROW_HOVER : ROW_COLOR);
            MountKind kind = MountKind.byId(str(mount, "kind")).orElse(MountKind.HORSE);
            badge(graphics, mount, left + 10, rowY + 2);
            String name = Component.translatable(kind.translationKey()).getString();
            if (bool(mount, "varies")) {
                name += "・" + Component.translatable(MountLooks.coatKey(kind, str(mount, "coat"))).getString();
            }
            graphics.drawString(font, fit(name, 120), left + 30, rowY + 3, bool(mount, "rare") ? RARE : TEXT, false);
            String marks = (bool(mount, "riding") ? "▶" : "") + (bool(mount, "selected") ? "★" : "");
            graphics.drawString(font, marks, left + 170 - font.width(marks), rowY + 3, GOLD, false);
        }
        mount(selected).ifPresent(mount -> drawDetail(graphics, mount, left + 178, top + (stableMode ? 38 : 24)));
    }

    /** The detail of a kept mount: its look turning, its name, look and values with bars, its grade. */
    private void drawDetail(GuiGraphics graphics, JsonObject mount, int x, int y) {
        MountKind kind = MountKind.byId(str(mount, "kind")).orElse(MountKind.HORSE);
        MountLook look = new MountLook(str(mount, "coat"), str(mount, "shape"));
        int w = WIDTH - (x - left) - 8;
        graphics.fill(x, y, x + w, y + 88, PANEL_INNER);
        graphics.renderOutline(x, y, w, 88, RIM_DARK);
        float yaw = 205.0F + 28.0F * Mth.sin(ticks * 0.035F);
        drawMount(graphics, kind, look, x + w / 2, y + 78, kind == MountKind.GRIFFIN ? 22 : 30, yaw, x + 1, y + 1,
                x + w - 1, y + 87);
        int ty = y + 92;
        graphics.drawString(font, Component.translatable(kind.translationKey()), x, ty, GOLD, false);
        if (bool(mount, "varies")) {
            Component lookLine = Component.translatable(MountRules.DEED_LOOK,
                    Component.translatable(MountLooks.coatKey(kind, look.coat())),
                    Component.translatable(MountLooks.shapeKey(kind, look.shape())));
            graphics.drawString(font, fit(lookLine.getString(), w), x, ty + 10, bool(mount, "rare") ? RARE : MUTED,
                    false);
            bar(graphics, x, ty + 22, w, Component.translatable(MountRules.SCREEN_SPEED), (int) num(mount, "speed"));
            bar(graphics, x, ty + 33, w, Component.translatable(MountRules.SCREEN_ACCEL), (int) num(mount, "accel"));
            bar(graphics, x, ty + 44, w, Component.translatable(kind.flies() ? MountRules.SCREEN_FLIGHT
                    : MountRules.SCREEN_JUMP), (int) num(mount, "lift"));
            MountTraits.Grade grade = grade(mount);
            badge(graphics, mount, x, ty + 57);
            graphics.drawString(font, Component.translatable(MountRules.SCREEN_TOTAL, num(mount, "total"),
                    Component.translatable(grade.translationKey())), x + 22, ty + 59, 0xFF000000 | grade.color(), false);
        } else {
            graphics.drawString(font, Component.translatable(MountRules.SCREEN_MACHINE), x, ty + 12, MUTED, false);
        }
    }

    private void bar(GuiGraphics graphics, int x, int y, int w, Component label, int value) {
        graphics.drawString(font, label, x, y, TEXT, false);
        int bx = x + 54;
        int bw = w - 54 - 24;
        graphics.fill(bx, y + 1, bx + bw, y + 7, 0xFF2A2433);
        float t = (value - MountTraits.MIN) / (float) (MountTraits.MAX - MountTraits.MIN);
        int color = value >= 90 ? 0xFFFFB020 : value >= 75 ? 0xFF4FA3FF : value >= 55 ? 0xFF6FD36F : 0xFF9A9AA6;
        graphics.fill(bx, y + 1, bx + Math.max(1, (int) (bw * Mth.clamp(t, 0.0F, 1.0F))), y + 7, color);
        String number = String.valueOf(value);
        graphics.drawString(font, number, x + w - font.width(number), y, TEXT, false);
    }

    private void badge(GuiGraphics graphics, JsonObject mount, int x, int y) {
        if (!bool(mount, "varies")) {
            return;
        }
        MountTraits.Grade grade = grade(mount);
        int color = 0xFF000000 | grade.color();
        graphics.fill(x, y, x + 18, y + 10, 0xFF101014);
        graphics.renderOutline(x, y, 18, 10, color);
        String letter = grade.name();
        graphics.drawString(font, letter, x + 9 - font.width(letter) / 2, y + 1, color, false);
    }

    private void drawDeeds(GuiGraphics graphics, int mouseX, int mouseY) {
        List<JsonObject> deeds = objects(data, "deeds");
        graphics.drawString(font, Component.translatable(MountRules.SCREEN_DEEDS_NOTE), left + 8, top + 38, MUTED,
                false);
        if (deeds.isEmpty()) {
            graphics.drawString(font, Component.translatable(MountRules.SCREEN_NO_DEEDS), left + 12, top + 56, MUTED,
                    false);
        }
        boolean here = bool(data, "atStable");
        for (int i = 0; i < deeds.size() && i < 8; i++) {
            JsonObject deed = deeds.get(i);
            int rowY = top + 52 + i * 22;
            graphics.fill(left + 8, rowY, left + WIDTH - 8, rowY + 20, ROW_COLOR);
            MountKind kind = MountKind.byId(str(deed, "kind")).orElse(MountKind.HORSE);
            badge(graphics, deed, left + 11, rowY + 5);
            String name = Component.translatable(kind.translationKey()).getString() + "・"
                    + Component.translatable(MountLooks.coatKey(kind, str(deed, "coat"))).getString() + "・"
                    + Component.translatable(MountLooks.shapeKey(kind, str(deed, "shape"))).getString();
            graphics.drawString(font, fit(name, 190), left + 33, rowY + 2, bool(deed, "rare") ? RARE : TEXT, false);
            graphics.drawString(font, Component.translatable(MountRules.DEED_VALUES, num(deed, "speed"),
                    num(deed, "accel"), Component.translatable(kind.flies() ? MountRules.SCREEN_FLIGHT
                            : MountRules.SCREEN_JUMP), num(deed, "lift")), left + 33, rowY + 11, MUTED, false);
            String redeem = str(deed, "redeem");
            int bx = left + WIDTH - 8 - 84;
            if ("ok".equals(redeem) && here) {
                boolean armed = confirming.equals("redeem:" + num(deed, "slot"));
                boolean hover = inside(mouseX, mouseY, bx, rowY + 3, 80, 14);
                graphics.fill(bx, rowY + 3, bx + 80, rowY + 17, armed ? 0xFF6A4A20 : hover ? 0xFF4A3A20 : PANEL_INNER);
                graphics.renderOutline(bx, rowY + 3, 80, 14, RIM);
                graphics.drawCenteredString(font, Component.translatable(armed ? MountRules.SCREEN_CONFIRM_SHORT
                        : MountRules.SCREEN_REDEEM), bx + 40, rowY + 6, TEXT);
            } else {
                Component why = switch (redeem) {
                    case "level_too_low" -> Component.translatable(MountRules.STABLE_LOCKED, kind.requiredLevel());
                    case "stable_full" -> Component.translatable(MountRules.SCREEN_FULL_SHORT);
                    case "already_kept" -> Component.translatable(MountRules.SCREEN_KEPT_SHORT);
                    default -> here ? Component.empty() : Component.translatable(MountRules.SCREEN_AT_STABLE_SHORT);
                };
                graphics.drawString(font, why, left + WIDTH - 12 - font.width(why), rowY + 6, BAD, false);
            }
        }
    }

    /** The purchase reveal: the new mount, its values counting up, then its grade. */
    private void drawReveal(GuiGraphics graphics) {
        int w = 240;
        int h = 168;
        int x = (width - w) / 2;
        int y = (height - h) / 2 - 6;
        graphics.fill(0, 0, width, height, 0x88000000);
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, RIM_DARK);
        graphics.fill(x, y, x + w, y + h, PANEL);
        MountTraits.Grade grade = grade(reveal);
        boolean done = revealTicks >= REVEAL_TICKS;
        int rim = done ? 0xFF000000 | grade.color() : RIM;
        graphics.renderOutline(x, y, w, h, rim);
        graphics.drawCenteredString(font, Component.translatable(MountRules.SCREEN_REVEAL_TITLE), x + w / 2, y + 6, GOLD);
        MountKind kind = MountKind.byId(str(reveal, "kind")).orElse(MountKind.HORSE);
        MountLook look = new MountLook(str(reveal, "coat"), str(reveal, "shape"));
        float yaw = 205.0F + 28.0F * Mth.sin(ticks * 0.035F);
        drawMount(graphics, kind, look, x + w / 2, y + 86, kind == MountKind.GRIFFIN ? 24 : 32, yaw, x + 1, y + 16,
                x + w - 1, y + 92);
        Component lookLine = Component.translatable(MountRules.DEED_LOOK,
                Component.translatable(MountLooks.coatKey(kind, look.coat())),
                Component.translatable(MountLooks.shapeKey(kind, look.shape())));
        graphics.drawCenteredString(font, lookLine, x + w / 2, y + 94, bool(reveal, "rare") ? RARE : TEXT);
        float t = Math.min(1.0F, revealTicks / (float) REVEAL_TICKS);
        bar(graphics, x + 12, y + 106, w - 24, Component.translatable(MountRules.SCREEN_SPEED),
                counted((int) num(reveal, "speed"), t));
        bar(graphics, x + 12, y + 117, w - 24, Component.translatable(MountRules.SCREEN_ACCEL),
                counted((int) num(reveal, "accel"), t));
        bar(graphics, x + 12, y + 128, w - 24, Component.translatable(kind.flies() ? MountRules.SCREEN_FLIGHT
                : MountRules.SCREEN_JUMP), counted((int) num(reveal, "lift"), t));
        if (done) {
            Component gradeLine = Component.translatable(MountRules.SCREEN_TOTAL, num(reveal, "total"),
                    Component.translatable(grade.translationKey()));
            float pulse = grade.ordinal() >= MountTraits.Grade.S.ordinal()
                    ? 0.75F + 0.25F * Mth.sin(ticks * 0.3F) : 1.0F;
            int color = 0xFF000000 | scale(grade.color(), pulse);
            graphics.drawCenteredString(font, gradeLine, x + w / 2, y + 142, color);
        }
    }

    private static int counted(int value, float t) {
        return MountTraits.MIN + Math.round((value - MountTraits.MIN) * t);
    }

    private static int scale(int rgb, float factor) {
        int r = Math.min(255, (int) ((rgb >> 16 & 0xFF) * factor));
        int g = Math.min(255, (int) ((rgb >> 8 & 0xFF) * factor));
        int b = Math.min(255, (int) ((rgb & 0xFF) * factor));
        return r << 16 | g << 8 | b;
    }

    /** A mount of {@code look} standing at ({@code x}, {@code y}) (its feet), turned by {@code yaw}, clipped to a box. */
    private void drawMount(GuiGraphics graphics, MountKind kind, MountLook look, int x, int y, int scale, float yaw,
                           int x0, int y0, int x1, int y1) {
        MountEntity entity = preview(kind, look);
        if (entity == null) {
            return;
        }
        entity.yBodyRot = yaw;
        entity.yBodyRotO = yaw;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setXRot(0.0F);
        entity.yHeadRot = yaw;
        entity.yHeadRotO = yaw;
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(-12.0F * Mth.DEG_TO_RAD);
        pose.mul(camera);
        graphics.enableScissor(x0, y0, x1, y1);
        InventoryScreen.renderEntityInInventory(graphics, x, y, scale, pose, camera, entity);
        graphics.disableScissor();
    }

    private MountEntity preview(MountKind kind, MountLook look) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        String key = kind.id() + ":" + look.encode();
        MountEntity entity = previews.get(key);
        if (entity == null) {
            entity = MountEntities.type(kind).create(minecraft.level);
            if (entity == null) {
                return null;
            }
            entity.preview(MountLooks.orClassic(kind, look));
            if (kind.flies()) {
                entity.previewSpread();
            }
            previews.put(key, entity);
        }
        return entity;
    }

    // ------------------------------------------------------------------ data helpers

    private static MountTraits.Grade grade(JsonObject mount) {
        return MountTraits.Grade.byId(str(mount, "grade")).orElse(MountTraits.Grade.C);
    }

    private String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private static String gold(long amount) {
        return String.format("%,d", amount);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static List<JsonObject> objects(JsonObject data, String key) {
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

    private static String str(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : "";
    }

    private static long num(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isNumber() ? object.get(key).getAsLong() : 0L;
    }

    private static boolean bool(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsBoolean();
    }
}
