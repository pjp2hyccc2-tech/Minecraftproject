package com.eternalrealm.ui.screen;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.client.ClientTrinkets;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentSlot;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.item.bag.EquipmentComparison;
import com.eternalrealm.item.forge.BagService;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.GearArmorItem;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The equipment (装備): the eleven slots around the character (head, chest, legs, feet on the left; the weapon on top
 * of two columns on the right, four accessories in two pairs and the two charms below them), the pieces in the bag
 * that fit the selected slot with a comparison against what is worn, equip and take off, and the totals of everything
 * worn (flat stats and stat percentages). The job's weapons and the level requirements are shown; the server checks
 * them again ({@link BagService}). Relics (金枠) wear the gold frame.
 */
public final class EquipmentScreen extends Screen {
    /** The boxes of the screen: what fits in each, and the UI action target. */
    private enum Box {
        WEAPON(EquipmentSlot.WEAPON, "weapon", -1),
        HEAD(EquipmentSlot.HEAD, "head", -1),
        CHEST(EquipmentSlot.CHEST, "chest", -1),
        LEGS(EquipmentSlot.LEGS, "legs", -1),
        FEET(EquipmentSlot.FEET, "feet", -1),
        ACCESSORY_1(EquipmentSlot.ACCESSORY, "accessory_1", 0),
        ACCESSORY_2(EquipmentSlot.ACCESSORY, "accessory_2", 1),
        ACCESSORY_3(EquipmentSlot.ACCESSORY, "accessory_3", 3),
        ACCESSORY_4(EquipmentSlot.ACCESSORY, "accessory_4", 4),
        CHARM(EquipmentSlot.CHARM, "charm", 2),
        CHARM_2(EquipmentSlot.CHARM, "charm_2", 5);

        final EquipmentSlot kind;
        final String target;
        /** Index in {@link ClientTrinkets} (the server's storage order), -1 for the weapon and the armor. */
        final int trinket;

        Box(EquipmentSlot kind, String target, int trinket) {
            this.kind = kind;
            this.target = target;
            this.trinket = trinket;
        }
    }

    private static final int WIDTH = 312;
    private static final int HEIGHT = 240;
    private static final int BOX = 20;
    private static final int ROW = 18;
    private static final int LIST_ROWS = 4;
    private static final int PANEL = 0xF0141018;
    private static final int PANEL_INNER = 0xFF1E1A26;
    private static final int RIM = 0xFFC9A55A;
    private static final int RIM_DARK = 0xFF3A2C18;
    private static final int TEXT = 0xFFE8E0C8;
    private static final int MUTED = 0xFF9A90A8;
    private static final int UP = 0xFF7CDB3A;
    private static final int DOWN = 0xFFFF6A50;

    private Box slot = Box.WEAPON;
    private int candidate = -1;
    private int scroll;
    private int left;
    private int top;
    private Button equip;
    private Button unequip;

    public EquipmentScreen() {
        super(Component.translatable("screen.eternalrealm.equipment.title"));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int rightX = left + 150;
        int buttonsY = top + HEIGHT - 24;
        equip = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.equipment.equip"),
                button -> equipCandidate()).bounds(rightX, buttonsY, 76, 16).build());
        unequip = addRenderableWidget(Button.builder(Component.translatable("screen.eternalrealm.equipment.unequip"),
                button -> unequipSlot()).bounds(rightX + 80, buttonsY, 76, 16).build());
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
        if (com.eternalrealm.client.ClientKeyMappings.OPEN_EQUIPMENT.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        Player player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            return;
        }
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, RIM);
        graphics.renderOutline(left + 2, top + 2, WIDTH - 4, HEIGHT - 4, RIM_DARK);
        graphics.drawString(font, title, left + 8, top + 7, RIM, true);
        // the character between the armor and the weapon and trinkets
        int cx = left + 60;
        graphics.fill(left + 32, top + 20, left + 88, top + 120, PANEL_INNER);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, cx, top + 112, 36, cx - mouseX,
                top + 60 - mouseY, player);
        ItemStack hovered = ItemStack.EMPTY;
        for (Box each : Box.values()) {
            int[] at = boxOf(each);
            ItemStack worn = worn(player, each);
            boolean on = each == slot;
            graphics.fill(at[0], at[1], at[0] + BOX, at[1] + BOX, 0xFF262130);
            if (!on && EquipmentStacks.isGoldFrame(worn)) {
                com.eternalrealm.ui.GoldFrame.draw(graphics, at[0], at[1], BOX, BOX,
                        com.eternalrealm.client.ClientCombatState.tick());
            } else {
                graphics.renderOutline(at[0] - 1, at[1] - 1, BOX + 2, BOX + 2, on ? RIM : rimOf(worn));
            }
            if (worn.isEmpty()) {
                graphics.drawString(font, Component.translatable(slotKey(each)), at[0] + 2, at[1] + 6, 0x60FFFFFF,
                        false);
            } else {
                graphics.renderItem(worn, at[0] + 2, at[1] + 2);
            }
            if (inside(mouseX, mouseY, at[0], at[1], BOX, BOX) && !worn.isEmpty()) {
                hovered = worn;
            }
        }
        drawTotals(graphics, player);
        ItemStack hoveredCandidate = drawCandidates(graphics, player, mouseX, mouseY);
        if (!hoveredCandidate.isEmpty()) {
            hovered = hoveredCandidate;
        }
        drawComparison(graphics, player);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private void drawTotals(GuiGraphics graphics, Player player) {
        List<EquipmentData> wornData = new ArrayList<>();
        for (Box each : Box.values()) {
            EquipmentStacks.read(worn(player, each)).ifPresent(wornData::add);
        }
        double[] totals = EquipmentComparison.total(wornData);
        int x = left + 8;
        int y = top + 126;
        graphics.drawString(font, Component.translatable("screen.eternalrealm.equipment.total"), x, y, RIM, false);
        y += 11;
        for (EquipmentComparison.Stat stat : EquipmentComparison.Stat.values()) {
            double value = totals[stat.ordinal()];
            if (Math.abs(value) < 1.0E-9) {
                continue;
            }
            if (y > top + HEIGHT - 12) {
                break;
            }
            graphics.drawString(font, Component.translatable(stat.translationKey()), x, y, MUTED, false);
            String text = format(stat, value);
            graphics.drawString(font, text, x + 136 - font.width(text), y, TEXT, false);
            y += 10;
        }
    }

    /** @return the candidate under the mouse */
    private ItemStack drawCandidates(GuiGraphics graphics, Player player, int mouseX, int mouseY) {
        int x = left + 150;
        int y = top + 20;
        int w = WIDTH - 150 - 8;
        ItemStack worn = worn(player, slot);
        Component header = Component.translatable("screen.eternalrealm.equipment.slot_header",
                Component.translatable(slotKey(slot)), worn.isEmpty()
                        ? Component.translatable("screen.eternalrealm.equipment.empty") : worn.getHoverName());
        graphics.drawString(font, font.plainSubstrByWidth(header.getString(), w), x, y, TEXT, false);
        y += 12;
        graphics.fill(x, y, x + w, y + LIST_ROWS * ROW + 2, PANEL_INNER);
        graphics.renderOutline(x, y, w, LIST_ROWS * ROW + 2, RIM_DARK);
        List<Integer> candidates = candidates(player);
        if (candidates.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.equipment.none"), x + 5, y + 6,
                    MUTED, false);
            return ItemStack.EMPTY;
        }
        scroll = Math.max(0, Math.min(scroll, candidates.size() - LIST_ROWS));
        ItemStack hovered = ItemStack.EMPTY;
        int level = ClientCharacterState.snapshot().map(CharacterSnapshot::level).orElse(1);
        for (int row = 0; row < LIST_ROWS && scroll + row < candidates.size(); row++) {
            int inventorySlot = candidates.get(scroll + row);
            ItemStack stack = player.getInventory().items.get(inventorySlot);
            int ry = y + 1 + row * ROW;
            if (inventorySlot == candidate) {
                graphics.fill(x + 1, ry, x + w - 1, ry + ROW, 0xFF3A3020);
            } else if (inside(mouseX, mouseY, x, ry, w, ROW)) {
                graphics.fill(x + 1, ry, x + w - 1, ry + ROW, 0xFF2A2434);
            }
            graphics.renderItem(stack, x + 2, ry + 1);
            EquipmentData data = EquipmentStacks.read(stack).orElse(null);
            boolean usable = usable(player, data, level);
            ItemRarity rarity = data == null ? ItemRarity.COMMON : data.rarity();
            int color = !usable ? 0xFF6A6474 : data != null && data.isRelic() ? com.eternalrealm.ui.GoldFrame.GOLD
                    : rarity == ItemRarity.COMMON ? TEXT : 0xFF000000 | rarity.color();
            if (data != null && data.isRelic()) {
                com.eternalrealm.ui.GoldFrame.draw(graphics, x + 2, ry + 1, 16, 16,
                        com.eternalrealm.client.ClientCombatState.tick());
            }
            String name = font.plainSubstrByWidth(stack.getHoverName().getString(), w - 52);
            graphics.drawString(font, name, x + 21, ry + 5, color, false);
            if (data != null) {
                String lv = "Lv" + data.requiredLevel();
                graphics.drawString(font, lv, x + w - 4 - font.width(lv), ry + 5,
                        data.requiredLevel() > level ? DOWN : MUTED, false);
            }
            if (inside(mouseX, mouseY, x, ry, w, ROW)) {
                hovered = stack;
            }
        }
        if (candidates.size() > LIST_ROWS) {
            String more = (scroll + 1) + "-" + Math.min(candidates.size(), scroll + LIST_ROWS) + "/" + candidates.size();
            graphics.drawString(font, more, x + w - font.width(more), y + LIST_ROWS * ROW + 4, MUTED, false);
        }
        return hovered;
    }

    private void drawComparison(GuiGraphics graphics, Player player) {
        int x = left + 150;
        int y = top + 32 + LIST_ROWS * ROW + 6;
        int w = WIDTH - 150 - 8;
        if (!Bag.isBagSlot(candidate)) {
            return;
        }
        EquipmentData next = EquipmentStacks.read(player.getInventory().items.get(candidate)).orElse(null);
        if (next == null) {
            return;
        }
        EquipmentData now = EquipmentStacks.read(worn(player, slot)).orElse(null);
        int level = ClientCharacterState.snapshot().map(CharacterSnapshot::level).orElse(1);
        String reason = reason(player, next, level, slot);
        if (reason != null) {
            graphics.drawString(font, Component.translatable(reason, next.requiredLevel()), x, y, DOWN, false);
            y += 10;
        }
        graphics.drawString(font, Component.translatable("screen.eternalrealm.equipment.compare"), x, y, RIM, false);
        y += 10;
        for (EquipmentComparison.Delta row : EquipmentComparison.compare(now, next)) {
            if (y > top + HEIGHT - 34) {
                break;
            }
            graphics.drawString(font, Component.translatable(row.stat().translationKey()), x, y, MUTED, false);
            String value = format(row.stat(), row.after());
            graphics.drawString(font, value, x + 92 - font.width(value), y, TEXT, false);
            if (row.direction() != 0) {
                String delta = (row.direction() > 0 ? "▲" : "▼") + format(row.stat(), Math.abs(row.change()));
                graphics.drawString(font, delta, x + w - font.width(delta), y, row.direction() > 0 ? UP : DOWN, false);
            }
            y += 9;
        }
    }

    // ------------------------------------------------------------------ input and actions

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Player player = minecraft == null ? null : minecraft.player;
        if (player != null) {
            for (Box each : Box.values()) {
                int[] at = boxOf(each);
                if (inside(mouseX, mouseY, at[0], at[1], BOX, BOX)) {
                    slot = each;
                    candidate = -1;
                    scroll = 0;
                    click();
                    updateButtons();
                    return true;
                }
            }
            int x = left + 150;
            int y = top + 33;
            List<Integer> candidates = candidates(player);
            for (int row = 0; row < LIST_ROWS && scroll + row < candidates.size(); row++) {
                if (inside(mouseX, mouseY, x, y + row * ROW, WIDTH - 158, ROW)) {
                    candidate = candidates.get(scroll + row);
                    click();
                    if (button == 1) {
                        equipCandidate(); // right click: equip at once
                    }
                    updateButtons();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    private void equipCandidate() {
        if (Bag.isBagSlot(candidate)) {
            JsonObject args = new JsonObject();
            args.addProperty("slot", candidate);
            if (slot.trinket >= 0) {
                args.addProperty("target", slot.target);
            }
            ERNetwork.sendToServer(new UiActionPacket(BagService.EQUIP, args));
            candidate = -1;
            click();
        }
    }

    private void unequipSlot() {
        JsonObject args = new JsonObject();
        args.addProperty("target", slot.target);
        ERNetwork.sendToServer(new UiActionPacket(BagService.UNEQUIP, args));
        click();
    }

    private void updateButtons() {
        Player player = minecraft == null ? null : minecraft.player;
        if (player == null || equip == null) {
            return;
        }
        if (Bag.isBagSlot(candidate) && EquipmentStacks.read(player.getInventory().items.get(candidate))
                .map(data -> data.slot() != slot.kind).orElse(true)) {
            candidate = -1; // moved or equipped meanwhile
        }
        int level = ClientCharacterState.snapshot().map(CharacterSnapshot::level).orElse(1);
        EquipmentData next = Bag.isBagSlot(candidate)
                ? EquipmentStacks.read(player.getInventory().items.get(candidate)).orElse(null) : null;
        equip.active = next != null && reason(player, next, level, slot) == null;
        unequip.active = !worn(player, slot).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    /** The bag slots holding pieces for the selected slot: usable ones first, then by level. */
    private List<Integer> candidates(Player player) {
        int level = ClientCharacterState.snapshot().map(CharacterSnapshot::level).orElse(1);
        List<Integer> out = new ArrayList<>();
        for (int i = Bag.FIRST_SLOT; i <= Bag.LAST_SLOT; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            EquipmentData data = EquipmentStacks.read(stack).orElse(null);
            if (data != null && data.slot() == slot.kind) {
                out.add(i);
            }
        }
        out.sort((a, b) -> {
            EquipmentData da = EquipmentStacks.read(player.getInventory().items.get(a)).orElseThrow();
            EquipmentData db = EquipmentStacks.read(player.getInventory().items.get(b)).orElseThrow();
            int usable = Boolean.compare(usable(player, db, level), usable(player, da, level));
            return usable != 0 ? usable : Integer.compare(db.requiredLevel(), da.requiredLevel());
        });
        return out;
    }

    private static boolean usable(Player player, EquipmentData data, int level) {
        return data != null && reason(player, data, level, null) == null;
    }

    /** Why a piece cannot be worn in {@code box} (a translation key), or {@code null}. */
    private static String reason(Player player, EquipmentData data, int level, Box box) {
        if (!data.canBeUsedBy(level, player.getUUID())) {
            return "screen.eternalrealm.equipment.need_level";
        }
        if (box != null && box.trinket >= 0 && data.design() != null) {
            for (Box other : Box.values()) {
                if (other != box && other.trinket >= 0 && EquipmentStacks.designOf(worn(player, other))
                        .map(design -> design.id().equals(data.design().id())).orElse(false)) {
                    return "screen.eternalrealm.equipment.duplicate";
                }
            }
        }
        if (data.isWeapon()) {
            JobType job = ClientCharacterState.snapshot().map(CharacterSnapshot::jobId).map(JobType::fromStored)
                    .orElse(JobType.NONE);
            if (job.isChosen() && !Jobs.canUse(job, data.weaponType())) {
                return "screen.eternalrealm.equipment.wrong_job";
            }
        }
        return null;
    }

    private static ItemStack worn(Player player, Box box) {
        if (box.trinket >= 0) {
            return ClientTrinkets.get(box.trinket);
        }
        return box == Box.WEAPON ? player.getInventory().items.get(0)
                : player.getItemBySlot(GearArmorItem.vanillaSlot(box.kind));
    }

    private int[] boxOf(Box box) {
        return switch (box) {
            case HEAD -> new int[] {left + 8, top + 22};
            case CHEST -> new int[] {left + 8, top + 46};
            case LEGS -> new int[] {left + 8, top + 70};
            case FEET -> new int[] {left + 8, top + 94};
            case WEAPON -> new int[] {left + 106, top + 22};
            case ACCESSORY_1 -> new int[] {left + 94, top + 46};
            case ACCESSORY_2 -> new int[] {left + 118, top + 46};
            case ACCESSORY_3 -> new int[] {left + 94, top + 70};
            case ACCESSORY_4 -> new int[] {left + 118, top + 70};
            case CHARM -> new int[] {left + 94, top + 94};
            case CHARM_2 -> new int[] {left + 118, top + 94};
        };
    }

    private static String slotKey(Box box) {
        return "screen.eternalrealm.equipment.slot." + box.target;
    }

    private static int rimOf(ItemStack stack) {
        ItemRarity rarity = EquipmentStacks.read(stack).map(data -> data.rarity()).orElse(ItemRarity.COMMON);
        return stack.isEmpty() ? RIM_DARK : 0xFF000000 | (rarity == ItemRarity.COMMON ? 0x8A8494 : rarity.color());
    }

    private static String format(EquipmentComparison.Stat stat, double value) {
        if (stat.percent()) {
            return String.format("%.1f%%", value * 100.0);
        }
        return switch (stat) {
            case ATTACK, DEFENSE, MAX_HEALTH -> String.format("%.1f", value);
            default -> String.valueOf(Math.round(value));
        };
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void click() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(com.eternalrealm.sound.ERSounds.UI_CLICK.get(), 0.5F, 1.0F);
        }
    }
}
