package com.eternalrealm.ui.estate;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.estate.forge.LedgerService;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

/**
 * The sorting desk's ledger: what the room's chests, barrels and shelves hold, one line per kind of thing with where
 * it lies; tabs by kind, a search box (names in the player's language), the storage pieces with their labels and how
 * full they are (click one to see only what is in it); taking a stack or everything of a line into the bag, and
 * putting the bag away by the chests' labels. The server ({@link LedgerService}) does every move.
 */
public final class LedgerScreen extends Screen {
    private static final int WIDTH = 444;
    private static final int HEIGHT = 262;
    private static final int LIST_W = 318;
    private static final int ROW_H = 21;
    private static final int LIST_TOP = 66;
    private static final int LIST_ROWS = 7;
    private static final int SIDE_ROW = 12;
    private static final String[] TABS = {"all", "gear", "potion", "material", "food", "book", "treasure", "misc"};
    private static final ItemLike[] TAB_ICONS = {Items.CHEST, Items.IRON_SWORD, Items.POTION, Items.IRON_INGOT,
            Items.BREAD, Items.BOOK, Items.DIAMOND, Items.BUNDLE};

    private int left;
    private int top;
    private long seenRevision = -1;
    private JsonObject data = new JsonObject();
    private final List<JsonObject> shown = new ArrayList<>();
    private String kind = "all";
    private int scroll;
    private int sideScroll;
    private int box = -1;
    /** Where the pages before this one started (the server sizes pages by what fits in one document). */
    private final java.util.ArrayDeque<Long> pages = new java.util.ArrayDeque<>();
    private EditBox search;
    private Button depositAll;
    private Button depositKind;
    private Button previous;
    private Button next;
    private Component toast = Component.empty();
    private int toastColor = EstateUi.TEXT;
    private int toastTicks;

    private LedgerScreen() {
        super(Component.translatable("screen.eternalrealm.ledger.title"));
    }

    /** New ledger data: opens the screen when the server says so; an open screen refreshes itself. */
    public static void accept(JsonObject json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (EstateUi.bool(json, "open") && !(minecraft.screen instanceof LedgerScreen)) {
            minecraft.setScreen(new LedgerScreen());
        }
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, left + 9, top + 47, LIST_W - 2, 14,
                Component.translatable("screen.eternalrealm.ledger.search")));
        search.setMaxLength(48);
        search.setHint(Component.translatable("screen.eternalrealm.ledger.search").withStyle(s -> s.withColor(0x8A7F78)));
        search.setValue(query);
        search.setResponder(value -> {
            scroll = 0;
            filter();
        });
        int by = top + HEIGHT - 22;
        depositAll = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.ledger.deposit_all"),
                button -> deposit("all")).bounds(left + 8, by, 112, 16)
                .tooltip(Tooltip.create(Component.translatable("screen.eternalrealm.ledger.deposit_all_tip"))).build());
        depositKind = addRenderableWidget(Button.builder(Component.empty(), button -> deposit(kind))
                .bounds(left + 124, by, 112, 16).build());
        previous = addRenderableWidget(Button.builder(Component.literal("◀"), button -> page(false))
                .bounds(left + LIST_W - 36, by, 18, 16).build());
        next = addRenderableWidget(Button.builder(Component.literal("▶"), button -> page(true))
                .bounds(left + LIST_W - 14, by, 18, 16).build());
        refresh();
        updateWidgets();
    }

    @Override
    public void tick() {
        search.tick();
        if (toastTicks > 0) {
            toastTicks--;
        }
        refresh();
        updateWidgets();
    }

    private void refresh() {
        if (ClientUiState.revision() == seenRevision) {
            return;
        }
        seenRevision = ClientUiState.revision();
        JsonObject next = ClientUiState.get(LedgerService.CHANNEL).orElse(null);
        if (next == null || next == data) {
            return;
        }
        data = next;
        kind = EstateUi.str(data, "kind").isEmpty() ? "all" : EstateUi.str(data, "kind");
        JsonObject result = EstateUi.object(data, "result");
        if (result.has("deposited")) {
            long items = EstateUi.num(result, "deposited");
            long leftOver = EstateUi.num(result, "left");
            toast = items == 0 && leftOver == 0 ? Component.translatable("screen.eternalrealm.ledger.nothing")
                    : leftOver > 0 ? Component.translatable("screen.eternalrealm.ledger.deposited_some", items,
                    EstateUi.num(result, "boxes"), leftOver)
                    : Component.translatable("screen.eternalrealm.ledger.deposited", items, EstateUi.num(result, "boxes"));
            toastColor = leftOver > 0 ? EstateUi.AMBER : EstateUi.GOOD;
            toastTicks = 100;
        } else if (result.has("took")) {
            long took = EstateUi.num(result, "took");
            toast = took > 0 ? Component.translatable("screen.eternalrealm.ledger.took", took)
                    : Component.translatable("screen.eternalrealm.ledger.bag_full");
            toastColor = took > 0 ? EstateUi.GOOD : EstateUi.BAD;
            toastTicks = 80;
        }
        filter();
    }

    private void filter() {
        shown.clear();
        String query = search == null ? "" : search.getValue();
        for (JsonObject line : EstateUi.objects(data, "entries")) {
            if (box >= 0 && !EstateUi.ints(line, "places").contains(box)) {
                continue;
            }
            ItemStack stack = EstateUi.stack(line);
            String words = EstateUi.name(line, stack).getString() + " " + EstateUi.str(line, "item");
            if (com.eternalrealm.estate.Ledger.matches(query, words)) {
                shown.add(line);
            }
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - LIST_ROWS)));
    }

    private void updateWidgets() {
        boolean specific = !"all".equals(kind);
        depositKind.visible = specific;
        depositKind.setMessage(Component.translatable("screen.eternalrealm.ledger.deposit_kind",
                Component.translatable("estate.eternalrealm.label." + kind)));
        long offset = EstateUi.num(data, "offset");
        long nextOffset = data.has("next") ? EstateUi.num(data, "next") : -1L;
        previous.visible = next.visible = offset > 0 || nextOffset > 0;
        previous.active = offset > 0;
        next.active = nextOffset > 0;
    }

    private void view(String wanted, long offset) {
        JsonObject args = new JsonObject();
        args.addProperty("kind", wanted);
        args.addProperty("offset", offset);
        EstateUi.send(LedgerService.VIEW, args);
    }

    private void page(boolean forward) {
        long offset = EstateUi.num(data, "offset");
        if (forward) {
            pages.push(offset);
            view(kind, Math.max(0L, EstateUi.num(data, "next")));
        } else {
            view(kind, pages.isEmpty() ? 0L : pages.pop());
        }
        scroll = 0;
    }

    private void deposit(String what) {
        JsonObject args = new JsonObject();
        args.addProperty("what", what);
        args.addProperty("kind", kind);
        args.addProperty("offset", EstateUi.num(data, "offset"));
        EstateUi.send(LedgerService.DEPOSIT, args);
    }

    private void take(JsonObject line, boolean all) {
        JsonObject args = new JsonObject();
        args.addProperty("key", EstateUi.str(line, "key"));
        args.addProperty("all", all);
        args.addProperty("kind", kind);
        args.addProperty("offset", EstateUi.num(data, "offset"));
        EstateUi.send(LedgerService.TAKE, args);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < TABS.length; i++) {
            if (EstateUi.inside(mouseX, mouseY, tabX(i), top + 23, 38, 20)) {
                if (!TABS[i].equals(kind)) {
                    kind = TABS[i];
                    scroll = 0;
                    box = -1;
                    pages.clear();
                    view(kind, 0);
                    click();
                }
                return true;
            }
        }
        int listX = left + 8;
        for (int row = 0; row < LIST_ROWS; row++) {
            int index = scroll + row;
            if (index >= shown.size()) {
                break;
            }
            int y = top + LIST_TOP + row * ROW_H;
            if (EstateUi.inside(mouseX, mouseY, listX + LIST_W - 70, y + 3, 32, 14)) {
                take(shown.get(index), false);
                click();
                return true;
            }
            if (EstateUi.inside(mouseX, mouseY, listX + LIST_W - 36, y + 3, 32, 14)) {
                take(shown.get(index), true);
                click();
                return true;
            }
        }
        List<JsonObject> boxes = EstateUi.objects(data, "boxes");
        int sx = left + LIST_W + 16;
        for (int row = 0; row < sideRows(); row++) {
            int index = sideScroll + row;
            if (index >= boxes.size()) {
                break;
            }
            if (EstateUi.inside(mouseX, mouseY, sx, top + 38 + row * SIDE_ROW, sideWidth(), SIDE_ROW)) {
                box = box == index ? -1 : index;
                scroll = 0;
                filter();
                click();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left + LIST_W + 12) {
            int max = Math.max(0, EstateUi.objects(data, "boxes").size() - sideRows());
            sideScroll = Math.max(0, Math.min(max, sideScroll - (int) Math.signum(delta)));
        } else {
            int max = Math.max(0, shown.size() - LIST_ROWS);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        }
        return true;
    }

    private void click() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, 1.4F);
        }
    }

    private int tabX(int i) {
        return left + 8 + i * 40;
    }

    private int sideWidth() {
        return WIDTH - LIST_W - 24;
    }

    private int sideRows() {
        return (HEIGHT - 38 - 30) / SIDE_ROW;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        EstateUi.panel(graphics, left, top, WIDTH, HEIGHT);
        String room = EstateUi.str(data, "room");
        Component heading = room.isEmpty() ? title : Component.translatable("screen.eternalrealm.ledger.title_room",
                Component.translatable("estate.eternalrealm.room." + room));
        EstateUi.title(graphics, font, heading, left + WIDTH / 2, top + 6, WIDTH - 40);
        renderTabs(graphics, mouseX, mouseY);
        renderList(graphics, mouseX, mouseY);
        renderSide(graphics, mouseX, mouseY);
        if (toastTicks > 0) {
            int tw = font.width(toast) + 12;
            int tx = left + 8 + (LIST_W - tw) / 2;
            int ty = top + LIST_TOP + LIST_ROWS * ROW_H - 18;
            graphics.fill(tx - 1, ty - 1, tx + tw + 1, ty + 15, toastColor);
            graphics.fill(tx, ty, tx + tw, ty + 14, 0xF0181014);
            graphics.drawCenteredString(font, toast, tx + tw / 2, ty + 3, toastColor);
        }
        if (EstateUi.bool(data, "unavailable")) {
            graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.ledger.unavailable"),
                    left + LIST_W / 2, top + 120, EstateUi.BAD);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        JsonObject kinds = EstateUi.object(data, "kinds");
        for (int i = 0; i < TABS.length; i++) {
            int x = tabX(i);
            int y = top + 23;
            boolean active = TABS[i].equals(kind);
            boolean hover = EstateUi.inside(mouseX, mouseY, x, y, 38, 20);
            graphics.fill(x, y, x + 38, y + 20, active ? EstateUi.ROW_SELECTED : hover ? EstateUi.ROW_HOVER : EstateUi.ROW);
            graphics.fill(x, y + 18, x + 38, y + 20, active ? EstateUi.labelColor(TABS[i]) : EstateUi.WALNUT);
            graphics.renderItem(new ItemStack(TAB_ICONS[i]), x + 3, y + 1);
            String count = Long.toString(EstateUi.num(kinds, TABS[i]));
            graphics.drawString(font, count, x + 36 - font.width(count), y + 6,
                    EstateUi.num(kinds, TABS[i]) > 0 ? EstateUi.TEXT : EstateUi.MUTED);
        }
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + 8;
        int y0 = top + LIST_TOP;
        EstateUi.inset(graphics, x, y0, LIST_W, LIST_ROWS * ROW_H);
        if (shown.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(EstateUi.objects(data, "entries").isEmpty()
                            ? "screen.eternalrealm.ledger.empty" : "screen.eternalrealm.ledger.no_match"),
                    x + LIST_W / 2, y0 + 64, EstateUi.MUTED);
        }
        List<JsonObject> boxes = EstateUi.objects(data, "boxes");
        for (int row = 0; row < LIST_ROWS; row++) {
            int index = scroll + row;
            if (index >= shown.size()) {
                break;
            }
            JsonObject line = shown.get(index);
            int y = y0 + row * ROW_H;
            boolean hover = EstateUi.inside(mouseX, mouseY, x, y, LIST_W, ROW_H);
            graphics.fill(x, y, x + LIST_W, y + ROW_H - 1, hover ? EstateUi.ROW_HOVER : EstateUi.ROW);
            graphics.fill(x, y, x + 2, y + ROW_H - 1, EstateUi.labelColor(EstateUi.str(line, "kind")));
            ItemStack stack = EstateUi.stack(line);
            graphics.renderItem(stack, x + 4, y + 2);
            Component name = EstateUi.name(line, stack);
            graphics.drawString(font, EstateUi.ellipsize(font, name.getString(), LIST_W - 140), x + 24, y + 2,
                    EstateUi.TEXT);
            String count = "×" + EstateUi.gold(EstateUi.num(line, "count"));
            graphics.drawString(font, count, x + LIST_W - 76 - font.width(count), y + 2, EstateUi.GOLD);
            graphics.drawString(font, EstateUi.ellipsize(font, places(line, boxes).getString(), LIST_W - 100),
                    x + 24, y + 11, EstateUi.MUTED);
            button(graphics, x + LIST_W - 70, y + 3, Component.translatable("screen.eternalrealm.ledger.one"), mouseX,
                    mouseY);
            button(graphics, x + LIST_W - 36, y + 3, Component.translatable("screen.eternalrealm.ledger.all"), mouseX,
                    mouseY);
        }
        if (shown.size() > LIST_ROWS) {
            int barH = LIST_ROWS * ROW_H;
            int knob = Math.max(10, barH * LIST_ROWS / shown.size());
            int knobY = y0 + (barH - knob) * scroll / Math.max(1, shown.size() - LIST_ROWS);
            graphics.fill(x + LIST_W + 1, y0, x + LIST_W + 3, y0 + barH, EstateUi.WALNUT_DARK);
            graphics.fill(x + LIST_W + 1, knobY, x + LIST_W + 3, knobY + knob, EstateUi.BRASS);
        }
        String usage = Component.translatable("screen.eternalrealm.ledger.usage", EstateUi.num(data, "used"),
                EstateUi.num(data, "slots")).getString();
        graphics.drawString(font, usage, x + LIST_W - font.width(usage), y0 + LIST_ROWS * ROW_H + 4, EstateUi.MUTED);
        if (box >= 0 && box < boxes.size()) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.ledger.only",
                    EstateUi.pieceName(EstateUi.str(boxes.get(box), "name"))), x, y0 + LIST_ROWS * ROW_H + 4,
                    EstateUi.AMBER);
        }
    }

    private void button(GuiGraphics graphics, int x, int y, Component label, int mouseX, int mouseY) {
        boolean hover = EstateUi.inside(mouseX, mouseY, x, y, 32, 14);
        graphics.fill(x, y, x + 32, y + 14, hover ? EstateUi.BRASS : EstateUi.BRASS_DARK);
        graphics.fill(x + 1, y + 1, x + 31, y + 13, hover ? 0xFF6A4A22 : EstateUi.WALNUT_DARK);
        graphics.drawCenteredString(font, label, x + 16, y + 3, hover ? EstateUi.GOLD : EstateUi.TEXT);
    }

    private static Component places(JsonObject line, List<JsonObject> boxes) {
        MutableComponent text = Component.empty();
        boolean first = true;
        for (int index : EstateUi.ints(line, "places")) {
            if (index < 0 || index >= boxes.size()) {
                continue;
            }
            if (!first) {
                text.append(Component.literal(" · "));
            }
            first = false;
            text.append(EstateUi.pieceName(EstateUi.str(boxes.get(index), "name")));
        }
        return text;
    }

    private void renderSide(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + LIST_W + 16;
        int w = sideWidth();
        graphics.drawString(font, Component.translatable("screen.eternalrealm.ledger.pieces"), x, top + 26,
                EstateUi.GOLD);
        EstateUi.inset(graphics, x, top + 38, w, sideRows() * SIDE_ROW);
        List<JsonObject> boxes = EstateUi.objects(data, "boxes");
        for (int row = 0; row < sideRows(); row++) {
            int index = sideScroll + row;
            if (index >= boxes.size()) {
                break;
            }
            JsonObject piece = boxes.get(index);
            int y = top + 38 + row * SIDE_ROW;
            boolean hover = EstateUi.inside(mouseX, mouseY, x, y, w, SIDE_ROW);
            if (index == box || hover) {
                graphics.fill(x, y, x + w, y + SIDE_ROW, index == box ? EstateUi.ROW_SELECTED : EstateUi.ROW_HOVER);
            }
            String label = EstateUi.str(piece, "label");
            graphics.fill(x + 1, y + 2, x + 4, y + SIDE_ROW - 2, EstateUi.labelColor(label));
            graphics.drawString(font, EstateUi.ellipsize(font, EstateUi.pieceName(EstateUi.str(piece, "name"))
                    .getString(), w - 40), x + 6, y + 2, EstateUi.TEXT);
            long used = EstateUi.num(piece, "used");
            long size = Math.max(1L, EstateUi.num(piece, "size"));
            int barX = x + w - 30;
            graphics.fill(barX, y + 4, barX + 26, y + 8, EstateUi.WALNUT_DARK);
            graphics.fill(barX, y + 4, barX + (int) (26 * used / size), y + 8,
                    used >= size ? EstateUi.BAD : used * 4 >= size * 3 ? EstateUi.AMBER : EstateUi.GOOD);
        }
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < TABS.length; i++) {
            if (EstateUi.inside(mouseX, mouseY, tabX(i), top + 23, 38, 20)) {
                graphics.renderTooltip(font, Component.translatable("estate.eternalrealm.label." + TABS[i]), mouseX,
                        mouseY);
                return;
            }
        }
        List<JsonObject> boxes = EstateUi.objects(data, "boxes");
        int x = left + 8;
        for (int row = 0; row < LIST_ROWS; row++) {
            int index = scroll + row;
            if (index >= shown.size()) {
                break;
            }
            int y = top + LIST_TOP + row * ROW_H;
            if (EstateUi.inside(mouseX, mouseY, x + LIST_W - 70, y + 3, 32, 14)) {
                graphics.renderTooltip(font, Component.translatable("screen.eternalrealm.ledger.one_tip"), mouseX,
                        mouseY);
                return;
            }
            if (EstateUi.inside(mouseX, mouseY, x + LIST_W - 36, y + 3, 32, 14)) {
                graphics.renderTooltip(font, Component.translatable("screen.eternalrealm.ledger.all_tip"), mouseX,
                        mouseY);
                return;
            }
            if (EstateUi.inside(mouseX, mouseY, x + 2, y, 20, ROW_H)) {
                JsonObject line = shown.get(index);
                ItemStack stack = EstateUi.stack(line);
                List<Component> lines = new ArrayList<>();
                if (!stack.isEmpty() && minecraft != null) {
                    lines.addAll(stack.getTooltipLines(minecraft.player, TooltipFlag.NORMAL));
                } else {
                    lines.add(EstateUi.name(line, stack));
                }
                lines.add(Component.translatable("screen.eternalrealm.ledger.where", places(line, boxes))
                        .withStyle(s -> s.withColor(0xA0928A)));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
        int sx = left + LIST_W + 16;
        for (int row = 0; row < sideRows(); row++) {
            int index = sideScroll + row;
            if (index >= boxes.size()) {
                break;
            }
            if (EstateUi.inside(mouseX, mouseY, sx, top + 38 + row * SIDE_ROW, sideWidth(), SIDE_ROW)) {
                JsonObject piece = boxes.get(index);
                String label = EstateUi.str(piece, "label");
                List<Component> lines = new ArrayList<>();
                lines.add(EstateUi.pieceName(EstateUi.str(piece, "name")));
                if (!label.isEmpty() && !"none".equals(label)) {
                    lines.add(Component.translatable("screen.eternalrealm.ledger.label",
                            Component.translatable("estate.eternalrealm.label." + label))
                            .withStyle(s -> s.withColor(EstateUi.labelColor(label) & 0xFFFFFF)));
                }
                lines.add(Component.translatable("screen.eternalrealm.ledger.fill", EstateUi.num(piece, "used"),
                        EstateUi.num(piece, "size")).withStyle(s -> s.withColor(0xA0928A)));
                lines.add(Component.translatable("screen.eternalrealm.ledger.filter_tip")
                        .withStyle(s -> s.withColor(0x7A6F68)));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
