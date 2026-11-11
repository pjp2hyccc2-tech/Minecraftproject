package com.eternalrealm.ui.screen;

import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.item.bag.BagCategory;
import com.eternalrealm.item.bag.BagEntry;
import com.eternalrealm.item.bag.BagTab;
import com.eternalrealm.item.forge.BagService;
import com.eternalrealm.item.forge.Bags;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.eternalrealm.network.forge.UseItemPacket;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The bag (カバン): the 35 bag slots in five tabs (全て / 装備 / 消耗品 / 素材 / その他) with rarity frames (relics: the
 * gold frame) and counts,
 * the selected item's details, and its actions: use a consumable, equip a piece, discard (after a confirmation), sort the
 * bag. Every action is a request; the server checks and moves the items ({@link BagService}, {@code UseItemPacket}).
 */
public final class BagScreen extends Screen {
    private static final int WIDTH = 296;
    private static final int HEIGHT = 182;
    private static final int COLUMNS = 7;
    private static final int ROWS = 5;
    private static final int CELL = 20;
    private static final int PANEL = 0xF0141018;
    private static final int PANEL_INNER = 0xFF1E1A26;
    private static final int RIM = 0xFFC9A55A;
    private static final int RIM_DARK = 0xFF3A2C18;
    private static final int CELL_BACK = 0xFF262130;
    private static final int TEXT = 0xFFE8E0C8;
    private static final int MUTED = 0xFF9A90A8;

    private BagTab tab = BagTab.ALL;
    private int selected = -1;
    private boolean confirmDiscard;
    private int left;
    private int top;
    private Button primary;
    private Button discard;
    private Button confirm;
    private Button cancel;

    public BagScreen() {
        super(Component.translatable("screen.eternalrealm.bag.title"));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int detailX = left + 158;
        int buttonsY = top + HEIGHT - 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.bag.sort"), button -> send(
                BagService.SORT, new JsonObject())).bounds(left + 8, buttonsY, 44, 16).build());
        primary = addRenderableWidget(Button.builder(Component.empty(), button -> primaryAction())
                .bounds(detailX, buttonsY, 62, 16).build());
        discard = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.bag.discard"),
                button -> confirmDiscard = true).bounds(detailX + 66, buttonsY, 62, 16).build());
        confirm = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.bag.discard_yes"),
                button -> discardSelected()).bounds(detailX, buttonsY, 62, 16).build());
        cancel = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.bag.discard_no"),
                button -> confirmDiscard = false).bounds(detailX + 66, buttonsY, 62, 16).build());
        updateButtons();
    }

    @Override
    public void tick() {
        updateButtons();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        Player player = player();
        if (player == null) {
            return;
        }
        frame(graphics, left, top, WIDTH, HEIGHT);
        graphics.drawString(font, title, left + 8, top + 7, RIM, true);
        CharacterSnapshot snapshot = ClientCharacterState.snapshot().orElse(null);
        if (snapshot != null) {
            Component gold = Component.translatable("screen.eternalrealm.bag.gold", String.format("%,d", snapshot.gold()));
            graphics.drawString(font, gold, left + WIDTH - 8 - font.width(gold), top + 7, 0xFFFFD866, true);
        }
        drawTabs(graphics, mouseX, mouseY);
        List<BagEntry> entries = Bags.entries(player);
        drawGrid(graphics, player, entries, mouseX, mouseY);
        Component capacity = Component.translatable("screen.eternalrealm.bag.capacity", Bag.used(entries), Bag.CAPACITY);
        int capacityColor = Bag.used(entries) >= Bag.CAPACITY ? 0xFFFF6A50 : MUTED;
        graphics.drawString(font, capacity, left + 8 + COLUMNS * CELL - font.width(capacity), top + HEIGHT - 20,
                capacityColor, false);
        drawDetail(graphics, player);
        super.render(graphics, mouseX, mouseY, partialTick);
        int hovered = slotAt(player, entries, mouseX, mouseY);
        if (hovered >= 0 && !player.getInventory().items.get(hovered).isEmpty()) {
            graphics.renderTooltip(font, player.getInventory().items.get(hovered), mouseX, mouseY);
        }
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + 8;
        int y = top + 20;
        for (BagTab each : BagTab.values()) {
            Component name = Component.translatable(each.translationKey());
            int w = Math.max(24, font.width(name) + 8);
            boolean on = each == tab;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 13;
            graphics.fill(x, y, x + w, y + 13, on ? 0xFF4A3A20 : hover ? 0xFF2E2838 : PANEL_INNER);
            graphics.renderOutline(x, y, w, 13, on ? RIM : RIM_DARK);
            graphics.drawString(font, name, x + (w - font.width(name)) / 2, y + 3, on ? 0xFFFFE8A8 : MUTED, false);
            x += w + 2;
        }
    }

    private void drawGrid(GuiGraphics graphics, Player player, List<BagEntry> entries, int mouseX, int mouseY) {
        List<Integer> slots = visibleSlots(entries);
        int gx = left + 8;
        int gy = top + 38;
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            int x = gx + (i % COLUMNS) * CELL;
            int y = gy + (i / COLUMNS) * CELL;
            graphics.fill(x, y, x + CELL - 2, y + CELL - 2, CELL_BACK);
            int slot = i < slots.size() ? slots.get(i) : -1;
            ItemStack stack = slot < 0 ? ItemStack.EMPTY : player.getInventory().items.get(slot);
            int rim = RIM_DARK;
            if (!stack.isEmpty()) {
                ItemRarity rarity = Bags.rarity(stack);
                if (Bags.category(stack) == BagCategory.EQUIPMENT) {
                    rim = 0xFF000000 | (rarity == ItemRarity.COMMON ? 0x8A8494 : rarity.color());
                }
                graphics.renderItem(stack, x + 1, y + 1);
                graphics.renderItemDecorations(font, stack, x + 1, y + 1);
            }
            boolean hover = mouseX >= x && mouseX < x + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2;
            if (!stack.isEmpty() && EquipmentStacks.isGoldFrame(stack) && slot != selected) {
                // a relic (金枠): the gold frame
                com.eternalrealm.ui.GoldFrame.draw(graphics, x, y, CELL - 2, CELL - 2,
                        com.eternalrealm.client.ClientCombatState.tick());
            } else {
                graphics.renderOutline(x - 1, y - 1, CELL, CELL, slot >= 0 && slot == selected ? RIM
                        : hover ? 0xFF8A7A50 : rim);
            }
        }
    }

    private void drawDetail(GuiGraphics graphics, Player player) {
        int x = left + 158;
        int y = top + 38;
        int w = WIDTH - 158 - 8;
        int h = HEIGHT - 38 - 28;
        graphics.fill(x, y, x + w, y + h, PANEL_INNER);
        graphics.renderOutline(x, y, w, h, RIM_DARK);
        ItemStack stack = selectedStack(player);
        if (stack.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.bag.nothing"), x + 6, y + 6, MUTED,
                    false);
            return;
        }
        if (confirmDiscard) {
            int line = y + 6;
            for (FormattedCharSequence part : font.split(Component.translatable("screen.eternalrealm.bag.discard_confirm",
                    stack.getHoverName()), w - 12)) {
                graphics.drawString(font, part, x + 6, line, 0xFFFF9A80, false);
                line += 10;
            }
            return;
        }
        List<Component> lines = Screen.getTooltipFromItem(minecraft, stack);
        int line = y + 5;
        for (Component component : lines) {
            for (FormattedCharSequence part : font.split(component, w - 10)) {
                if (line > y + h - 10) {
                    graphics.drawString(font, "…", x + 5, line - 2, MUTED, false);
                    return;
                }
                graphics.drawString(font, part, x + 5, line, TEXT, false);
                line += 9;
            }
        }
    }

    private void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.renderOutline(x, y, w, h, RIM);
        graphics.renderOutline(x + 2, y + 2, w - 4, h - 4, RIM_DARK);
    }

    // ------------------------------------------------------------------ input and actions

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Player player = player();
        if (player != null) {
            int x = left + 8;
            int y = top + 20;
            for (BagTab each : BagTab.values()) {
                int w = Math.max(24, font.width(Component.translatable(each.translationKey())) + 8);
                if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 13) {
                    tab = each;
                    selected = -1;
                    confirmDiscard = false;
                    click();
                    return true;
                }
                x += w + 2;
            }
            int slot = slotAt(player, Bags.entries(player), mouseX, mouseY);
            if (slot >= 0) {
                selected = player.getInventory().items.get(slot).isEmpty() ? -1 : slot;
                confirmDiscard = false;
                if (button == 1 && selected >= 0) {
                    primaryAction(); // right click: use or equip at once
                }
                click();
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void primaryAction() {
        Player player = player();
        ItemStack stack = player == null ? ItemStack.EMPTY : selectedStack(player);
        if (stack.isEmpty()) {
            return;
        }
        BagCategory category = Bags.category(stack);
        if (category == BagCategory.CONSUMABLE) {
            ERNetwork.sendToServer(new UseItemPacket(selected));
        } else if (category == BagCategory.EQUIPMENT) {
            JsonObject args = new JsonObject();
            args.addProperty("slot", selected);
            send(BagService.EQUIP, args);
        }
    }

    private void discardSelected() {
        if (selected >= 0) {
            JsonObject args = new JsonObject();
            args.addProperty("slot", selected);
            send(BagService.DISCARD, args);
        }
        confirmDiscard = false;
        selected = -1;
    }

    private void updateButtons() {
        Player player = player();
        ItemStack stack = player == null ? ItemStack.EMPTY : selectedStack(player);
        if (stack.isEmpty()) {
            selected = -1;
            confirmDiscard = false;
        }
        BagCategory category = stack.isEmpty() ? BagCategory.OTHER : Bags.category(stack);
        boolean actionable = category == BagCategory.CONSUMABLE || category == BagCategory.EQUIPMENT;
        if (primary != null) {
            primary.setMessage(Component.translatable(category == BagCategory.EQUIPMENT ? "screen.eternalrealm.bag.equip"
                    : "screen.eternalrealm.bag.use"));
            primary.visible = !confirmDiscard;
            primary.active = actionable;
            discard.visible = !confirmDiscard;
            discard.active = !stack.isEmpty();
            confirm.visible = confirmDiscard;
            cancel.visible = confirmDiscard;
        }
    }

    private void send(String action, JsonObject args) {
        ERNetwork.sendToServer(new UiActionPacket(action, args));
        click();
    }

    private void click() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(com.eternalrealm.sound.ERSounds.UI_CLICK.get(), 0.5F, 1.0F);
        }
    }

    /** The inventory slot under the mouse in the grid, or -1. */
    private int slotAt(Player player, List<BagEntry> entries, double mouseX, double mouseY) {
        int gx = left + 8;
        int gy = top + 38;
        if (mouseX < gx || mouseY < gy || mouseX >= gx + COLUMNS * CELL || mouseY >= gy + ROWS * CELL) {
            return -1;
        }
        int index = (int) ((mouseY - gy) / CELL) * COLUMNS + (int) ((mouseX - gx) / CELL);
        List<Integer> slots = visibleSlots(entries);
        return index < slots.size() ? slots.get(index) : -1;
    }

    /** Tab 全て: every bag slot in place (empty ones too); the other tabs: their items packed together. */
    private List<Integer> visibleSlots(List<BagEntry> entries) {
        List<Integer> slots = new ArrayList<>();
        if (tab == BagTab.ALL) {
            for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
                slots.add(slot);
            }
            return slots;
        }
        for (BagEntry entry : Bag.view(entries, tab)) {
            slots.add(entry.slot());
        }
        return slots;
    }

    private ItemStack selectedStack(Player player) {
        return Bag.isBagSlot(selected) ? player.getInventory().items.get(selected) : ItemStack.EMPTY;
    }

    private Player player() {
        return minecraft == null ? null : minecraft.player;
    }
}
