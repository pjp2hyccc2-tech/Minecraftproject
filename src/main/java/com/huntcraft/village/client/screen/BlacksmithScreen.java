package com.huntcraft.village.client.screen;

import com.huntcraft.core.IHunterWeapon;
import com.huntcraft.core.IUpgradeable;
import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.data.HunterData;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.village.client.ClientVillage;
import com.huntcraft.village.companion.MofuGear;
import com.huntcraft.village.companion.MofuGearItem;
import com.huntcraft.village.npc.NpcRole;
import com.huntcraft.village.npc.VillageNpc;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Blacksmith (鍛冶屋): forge / upgrade every recipe registered in {@link SmithingRegistry} (weapons, armor, companion
 * gear - shown as upgrade trees per group), and level up {@link IUpgradeable} gear. Shows required materials with the
 * owned counts (inventory + item box), zenny, HR and a stat preview against the gear currently used.
 */
@OnlyIn(Dist.CLIENT)
public class BlacksmithScreen extends VillageScreen {
    private static final int ROW_H = 18;
    private static final SmithingRegistry.Category[] CATS = {SmithingRegistry.Category.WEAPON, SmithingRegistry.Category.ARMOR,
            SmithingRegistry.Category.COMPANION};
    private int tab; // 0 weapon, 1 armor, 2 companion, 3 level-up
    private int scroll;
    private boolean onlyCraftable;
    @Nullable
    private SmithingRegistry.Recipe selected;
    private int selectedSlot = -1;
    private final List<Row> rows = new ArrayList<>();
    /** Expanded group keys per tab ("tab:group"); groups start collapsed except the first of each tab. */
    private final java.util.Set<String> expanded = new java.util.HashSet<>();
    private final java.util.Set<Integer> seededTabs = new java.util.HashSet<>();
    @Nullable
    private ItemStack hoverStack;
    private int lastBox = -1;
    @Nullable
    private final VillageNpc portrait;

    private record Row(@Nullable String header, @Nullable SmithingRegistry.Recipe recipe, int slot, int depth, int count, int craftable) {
        Row(@Nullable String header, @Nullable SmithingRegistry.Recipe recipe, int slot, int depth) {
            this(header, recipe, slot, depth, 0, 0);
        }
    }

    private String groupKey(String group) {
        return tab + ":" + group;
    }

    public BlacksmithScreen(@Nullable CompoundTag tag) {
        super(Component.translatable("screen.huntcraft.smith.title"), 356, 226, tag);
        this.portrait = NpcPortrait.npc(NpcRole.BLACKSMITH);
    }

    @Override
    protected void init() {
        super.init();
        HCNetwork.sendToServer("village/box_request", new CompoundTag());
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        Player p = player();
        if (tab < 3) {
            List<SmithingRegistry.Recipe> recipes = SmithingRegistry.byCategory(CATS[tab]);
            Map<Item, SmithingRegistry.Recipe> byResult = new HashMap<>();
            for (SmithingRegistry.Recipe r : recipes) byResult.put(r.result.get(), r);
            Map<String, List<SmithingRegistry.Recipe>> groups = new LinkedHashMap<>();
            for (SmithingRegistry.Recipe r : recipes) groups.computeIfAbsent(r.group, k -> new ArrayList<>()).add(r);
            if (!seededTabs.contains(tab) && !groups.isEmpty()) {
                seededTabs.add(tab);
                expanded.add(groupKey(groups.keySet().iterator().next()));
            }
            for (Map.Entry<String, List<SmithingRegistry.Recipe>> e : groups.entrySet()) {
                List<Row> groupRows = new ArrayList<>();
                // upgrade trees: roots first, then children depth-first
                for (SmithingRegistry.Recipe r : e.getValue()) {
                    if (r.upgradeFrom == null || !byResult.containsKey(r.upgradeFrom.get())) addTree(groupRows, r, e.getValue(), 0, p);
                }
                for (SmithingRegistry.Recipe r : e.getValue()) {
                    boolean added = groupRows.stream().anyMatch(x -> x.recipe == r);
                    if (!added && (!onlyCraftable || (p != null && canForge(p, r)))) groupRows.add(new Row(null, r, -1, 0));
                }
                if (groupRows.isEmpty()) continue;
                int craftable = 0;
                if (p != null) for (Row r : groupRows) if (r.recipe != null && canForge(p, r.recipe)) craftable++;
                rows.add(new Row(e.getKey(), null, -1, 0, groupRows.size(), craftable));
                boolean open = onlyCraftable || expanded.contains(groupKey(e.getKey()))
                        || (selected != null && groupRows.stream().anyMatch(x -> x.recipe == selected) && expanded.contains("sel:" + groupKey(e.getKey())));
                if (open) rows.addAll(groupRows);
            }
        } else if (p != null) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (st.getItem() instanceof IUpgradeable) rows.add(new Row(null, null, i, 0));
            }
        }
        if (selected != null && rows.stream().noneMatch(r -> r.recipe == selected)) selected = null;
        if (selectedSlot >= 0 && rows.stream().noneMatch(r -> r.slot == selectedSlot)) selectedSlot = -1;
    }

    private void addTree(List<Row> out, SmithingRegistry.Recipe r, List<SmithingRegistry.Recipe> group, int depth, @Nullable Player p) {
        if (depth > 6 || out.stream().anyMatch(x -> x.recipe == r)) return;
        if (!onlyCraftable || (p != null && canForge(p, r))) out.add(new Row(null, r, -1, depth));
        Item result = r.result.get();
        for (SmithingRegistry.Recipe c : group) {
            if (c.upgradeFrom != null && c.upgradeFrom.get() == result) addTree(out, c, group, depth + 1, p);
        }
    }

    public static Component groupName(String group) {
        for (WeaponClass wc : WeaponClass.values()) if (wc.name().equalsIgnoreCase(group)) return Component.translatable(wc.key());
        String g = group.toLowerCase(Locale.ROOT);
        if (I18n.exists("smith_group.huntcraft." + g)) return Component.translatable("smith_group.huntcraft." + g);
        if (I18n.exists("armor_set.huntcraft." + g)) return Component.translatable("armor_set.huntcraft." + g);
        if (I18n.exists("entity.huntcraft." + g)) return Component.translatable("screen.huntcraft.smith.armor_set", Component.translatable("entity.huntcraft." + g));
        String pretty = g.replace('_', ' ');
        return Component.literal(pretty.isEmpty() ? "-" : Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1));
    }

    @Override
    public void tick() {
        super.tick();
        NpcPortrait.tick(portrait);
        Player p = player();
        if (p != null && (lastBox != ClientVillage.boxVersion || (tab == 3 && p.tickCount % 10 == 0) || (onlyCraftable && p.tickCount % 20 == 0))) {
            lastBox = ClientVillage.boxVersion;
            rebuildRows();
        }
    }

    @Override
    protected void drawContent(GuiGraphics g, int mx, int my, float pt) {
        hoverStack = null;
        titlePlate(g, this.title, I_HAMMER);
        walletBar(g);
        int tx = left + 10, ty = top + 24;
        String[] tabs = {"screen.huntcraft.smith.tab_weapon", "screen.huntcraft.smith.tab_armor", "screen.huntcraft.smith.tab_companion",
                "screen.huntcraft.smith.tab_levelup"};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tab(g, tx + i * 41, ty, 40, Component.translatable(tabs[i]), tab == i, () -> {
                tab = idx;
                scroll = 0;
                selected = null;
                selectedSlot = -1;
                rebuildRows();
                pageSound();
            });
        }
        // list
        int lx = left + 10, ly = top + 46, lw = 164, lh = panelH - 74;
        inset(g, lx, ly, lw, lh);
        int visible = (lh - 6) / ROW_H;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
        if (rows.isEmpty()) {
            wrap(g, Component.translatable(tab == 3 ? "screen.huntcraft.smith.no_gear" : onlyCraftable ? "screen.huntcraft.smith.none_craftable"
                    : "screen.huntcraft.smith.no_recipes"), lx + 6, ly + 8, lw - 12, GREY, 8);
        }
        Player p = player();
        g.enableScissor(lx + 2, ly + 2, lx + lw - 2, ly + lh - 2);
        for (int i = 0; i < visible + 1 && i + scroll < rows.size(); i++) {
            Row r = rows.get(i + scroll);
            int ry = ly + 3 + i * ROW_H;
            if (r.header != null) {
                boolean open = onlyCraftable || expanded.contains(groupKey(r.header));
                boolean hov = in(mx, my, lx + 3, ry + 1, lw - 10, ROW_H - 2);
                g.fill(lx + 3, ry + 1, lx + lw - 8, ry + ROW_H - 1, hov ? 0x70C9A66B : 0x50C9A66B);
                icon(g, open ? I_MINUS : I_PLUS, lx + 4, ry + 1);
                String cnt = (r.craftable > 0 ? "✔" + r.craftable + " " : "") + r.count;
                int cw = this.font.width(cnt);
                textClip(g, groupName(r.header), lx + 22, ry + 5, lw - 34 - cw, GOLD, true);
                g.drawString(this.font, cnt, lx + lw - 10 - cw, ry + 5, r.craftable > 0 ? GOOD : 0xFFD8C8A8, true);
                final String key = groupKey(r.header);
                if (!onlyCraftable) region(lx + 3, ry, lw - 10, ROW_H, () -> {
                    if (!expanded.remove(key)) expanded.add(key);
                    rebuildRows();
                    pageSound();
                });
                continue;
            }
            ItemStack st = r.recipe != null ? new ItemStack(r.recipe.result.get()) : (p != null ? p.getInventory().getItem(r.slot) : ItemStack.EMPTY);
            boolean sel = (r.recipe != null && r.recipe == selected) || (r.slot >= 0 && r.slot == selectedSlot);
            boolean hover = in(mx, my, lx + 3, ry, lw - 10, ROW_H);
            rowHighlight(g, lx + 3, ry, lw - 10, ROW_H, sel, hover);
            int indent = Math.min(r.depth, 4) * 6;
            if (r.depth > 0) g.fill(lx + 2 + indent, ry + 8, lx + 5 + indent, ry + 9, 0xFFC9A66B);
            item(g, st, lx + 5 + indent, ry + 1);
            int nameCol = LIGHT;
            if (r.recipe != null && p != null && !canForge(p, r.recipe)) nameCol = 0xFFB8A890;
            boolean owned = r.recipe != null && p != null && SmithingRegistry.count(p, r.recipe.result.get()) > 0;
            int right = owned ? 12 : 0;
            textClip(g, st.getHoverName(), lx + 24 + indent, ry + 5, lw - 36 - indent - right, nameCol, true);
            if (owned) icon(g, I_CHECK, lx + lw - 22, ry + 1);
            if (r.slot >= 0 && st.getItem() instanceof IUpgradeable up) {
                String lv = "Lv" + up.getLevel(st);
                textRight(g, lv, lx + lw - 10, ry + 5, GOLD, true);
            }
            final Row row = r;
            region(lx + 3, ry, lw - 10, ROW_H, () -> {
                selected = row.recipe;
                selectedSlot = row.slot;
                click();
            });
        }
        g.disableScissor();
        scrollBar(g, lx + lw - 6, ly + 3, lh - 6, rows.size() <= visible ? 0 : scroll / (float) (rows.size() - visible), visible / (float) Math.max(1, rows.size()));
        if (tab < 3) {
            button(g, lx, ly + lh + 4, lw, 18, Component.translatable(onlyCraftable ? "screen.huntcraft.smith.filter_on" : "screen.huntcraft.smith.filter_off"), true, () -> {
                onlyCraftable = !onlyCraftable;
                scroll = 0;
                rebuildRows();
            });
        }
        // detail
        int dx = left + 180, dy = top + 46, dw = panelW - 190, dh = panelH - 56;
        paper(g, dx, dy, dw, dh);
        if (p == null) return;
        if (selected != null) drawRecipe(g, p, selected, dx, dy, dw, dh, mx, my);
        else if (selectedSlot >= 0) drawLevelUp(g, p, selectedSlot, dx, dy, dw, dh, mx, my);
        else drawIdle(g, dx, dy, dw, dh, mx, my);
    }

    /** Nothing selected: the smith himself and a hint. */
    private void drawIdle(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        preview(g, x + w / 2, y + h - 34, 42, portrait, mx, my);
        wrap(g, Component.translatable(tab == 3 ? "screen.huntcraft.smith.pick_gear" : "screen.huntcraft.smith.pick_recipe"), x + 8, y + 8, w - 16, INK_SOFT, 4);
        textClip(g, Component.translatable("npc.huntcraft.blacksmith.line"), x + 8, y + h - 14, w - 16, INK, false);
    }

    private static boolean canForge(Player p, SmithingRegistry.Recipe r) {
        HunterData d = HunterData.get(p);
        if (d.hunterRank < r.requiredHR || d.zenny < r.zenny || !SmithingRegistry.hasAll(p, r.costs)) return false;
        return r.upgradeFrom == null || hasItem(p, r.upgradeFrom.get());
    }

    private static boolean hasItem(Player p, Item item) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) return true;
        return false;
    }

    private void header(GuiGraphics g, ItemStack st, int x, int y, int w) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.renderItem(st, 0, 0);
        g.pose().popPose();
        if (in(mouseX, mouseY, x, y, 32, 32)) hoverStack = st;
        textClip(g, st.getHoverName(), x + 38, y + 4, w - 46, INK, false);
    }

    /** Stat lines: computed comparison for hunter weapons / armor / mofu gear, else the item's own tooltip. */
    private int statLines(GuiGraphics g, Player p, ItemStack st, int x, int y, int w, int max) {
        List<Component> lines = new ArrayList<>();
        if (st.getItem() instanceof IHunterWeapon wpn) {
            ItemStack held = p.getMainHandItem();
            IHunterWeapon hw = held.getItem() instanceof IHunterWeapon h2 && h2.weaponClass() == wpn.weaponClass() ? h2 : null;
            float atk = wpn.getAttack(st);
            lines.add(compare("screen.huntcraft.smith.stat_attack", atk, hw == null ? Float.NaN : hw.getAttack(held)));
            if (wpn.getElement(st) != com.huntcraft.core.Element.NONE) {
                lines.add(Component.translatable("screen.huntcraft.smith.stat_element", wpn.getElement(st).displayName(), fmt(wpn.getElementValue(st))));
            }
            lines.add(compare("screen.huntcraft.smith.stat_affinity", wpn.getAffinity(st), hw == null ? Float.NaN : hw.getAffinity(held)));
        } else if (st.getItem() instanceof ArmorItem armor) {
            ItemStack worn = p.getItemBySlot(armor.getEquipmentSlot());
            float cur = worn.getItem() instanceof ArmorItem wa ? wa.getDefense() : Float.NaN;
            lines.add(compare("screen.huntcraft.smith.stat_defense", armor.getDefense(), cur));
        } else if (st.getItem() instanceof MofuGearItem gear) {
            lines.addAll(MofuGearItem.statLines(gear.slot, gear.tier));
            lines.add(Component.translatable("screen.huntcraft.smith.for_mofu", gear.slot.displayName()));
        }
        if (lines.isEmpty() || !(st.getItem() instanceof MofuGearItem)) {
            List<Component> tip = st.getTooltipLines(p, TooltipFlag.NORMAL);
            for (int i = 1; i < tip.size(); i++) {
                Component c = tip.get(i);
                if (!c.getString().isBlank()) lines.add(c);
            }
        }
        int n = 0;
        for (Component c : lines) {
            if (n >= max) break;
            textClip(g, c, x, y + n * 10, w, INK_SOFT, false);
            n++;
        }
        return n * 10;
    }

    private static Component compare(String key, float value, float current) {
        Component base = Component.translatable(key, fmt(value));
        if (Float.isNaN(current) || Math.abs(value - current) < 0.05F) return base;
        float d = value - current;
        return base.copy().append(Component.literal(String.format(Locale.ROOT, " (%s%s)", d > 0 ? "+" : "", fmt(d)))
                .withStyle(d > 0 ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED));
    }

    private int costRow(GuiGraphics g, Player p, Item item, int need, int x, int y, int w) {
        ItemStack st = new ItemStack(item);
        g.renderItem(st, x, y);
        if (in(mouseX, mouseY, x, y, w, 16)) hoverStack = st;
        int have = SmithingRegistry.count(p, item);
        String cnt = have + "/" + need;
        textClip(g, st.getHoverName(), x + 20, y + 4, w - 26 - this.font.width(cnt), INK, false);
        g.drawString(this.font, cnt, x + w - this.font.width(cnt) - 2, y + 4, have >= need ? OK_DARK : BAD_DARK, false);
        return 17;
    }

    private void drawRecipe(GuiGraphics g, Player p, SmithingRegistry.Recipe r, int x, int y, int w, int h, int mx, int my) {
        HunterData d = HunterData.get(p);
        ItemStack out = new ItemStack(r.result.get());
        header(g, out, x + 8, y + 8, w - 8);
        text(g, Component.translatable(r.upgradeFrom != null ? "screen.huntcraft.smith.kind_upgrade" : "screen.huntcraft.smith.kind_forge"),
                x + 46, y + 20, 0xFF8A6A3A);
        int cy = y + 44;
        cy += statLines(g, p, out, x + 10, cy, w - 20, 3) + 2;
        rule(g, x + 8, cy, w - 16);
        cy += 4;
        if (r.upgradeFrom != null) {
            Item from = r.upgradeFrom.get();
            boolean has = hasItem(p, from);
            Component lbl = Component.translatable("screen.huntcraft.smith.from");
            text(g, lbl, x + 10, cy + 4, INK_SOFT);
            ItemStack fs = new ItemStack(from);
            int fx = x + 14 + this.font.width(lbl);
            g.renderItem(fs, fx, cy);
            if (in(mx, my, fx, cy, 16, 16)) hoverStack = fs;
            textClip(g, fs.getHoverName(), fx + 20, cy + 4, w - (fx - x) - 30, has ? OK_DARK : BAD_DARK, false);
            cy += 18;
        }
        text(g, Component.translatable("screen.huntcraft.smith.materials"), x + 10, cy, INK_SOFT);
        cy += 11;
        for (SmithingRegistry.Cost c : r.costs) {
            if (cy > y + h - 44) break;
            cy += costRow(g, p, c.item().get(), c.count(), x + 10, cy, w - 20);
        }
        int by = y + h - 42;
        icon(g, I_ZENNY, x + 10, by);
        text(g, Component.literal(String.format("%,d z", r.zenny)), x + 28, by + 4, d.zenny >= r.zenny ? OK_DARK : BAD_DARK);
        icon(g, I_HR, x + w / 2 + 10, by);
        text(g, Component.literal("HR " + r.requiredHR), x + w / 2 + 28, by + 4, d.hunterRank >= r.requiredHR ? OK_DARK : BAD_DARK);
        boolean ok = canForge(p, r);
        button(g, x + 10, y + h - 24, w - 20, 18, Component.translatable(r.upgradeFrom != null ? "screen.huntcraft.smith.do_upgrade"
                : "screen.huntcraft.smith.do_forge"), ok, () -> {
            CompoundTag t = new CompoundTag();
            t.putString("id", r.id);
            HCNetwork.sendToServer("village/smith", t);
        });
    }

    private void drawLevelUp(GuiGraphics g, Player p, int slot, int x, int y, int w, int h, int mx, int my) {
        ItemStack st = p.getInventory().getItem(slot);
        if (!(st.getItem() instanceof IUpgradeable up)) return;
        HunterData d = HunterData.get(p);
        header(g, st, x + 8, y + 8, w - 8);
        int lv = up.getLevel(st), max = up.getMaxLevel(st);
        text(g, Component.translatable("screen.huntcraft.smith.level", lv, max), x + 46, y + 20, 0xFF8A6A3A);
        int cy = y + 44;
        for (int i = 0; i < max; i++) {
            int px = x + 10 + i * 9;
            if (px > x + w - 14) break;
            g.fill(px, cy, px + 7, cy + 5, i < lv ? 0xFFE0A030 : 0x60402A10);
        }
        cy += 10;
        cy += statLines(g, p, st, x + 10, cy, w - 20, 3) + 2;
        if (lv >= max) {
            text(g, Component.translatable("screen.huntcraft.smith.max_level").withStyle(ChatFormatting.BOLD), x + 10, cy + 6, 0xFF8A6A3A);
            return;
        }
        int next = lv + 1;
        rule(g, x + 8, cy, w - 16);
        cy += 4;
        text(g, Component.translatable("screen.huntcraft.smith.next_level", next), x + 10, cy, INK_SOFT);
        cy += 11;
        List<SmithingRegistry.Cost> costs = up.getUpgradeCost(st, next);
        for (SmithingRegistry.Cost c : costs) {
            if (cy > y + h - 44) break;
            cy += costRow(g, p, c.item().get(), c.count(), x + 10, cy, w - 20);
        }
        int zenny = up.getUpgradeZenny(st, next), hr = up.getRequiredHR(st, next);
        int by = y + h - 42;
        icon(g, I_ZENNY, x + 10, by);
        text(g, Component.literal(String.format("%,d z", zenny)), x + 28, by + 4, d.zenny >= zenny ? OK_DARK : BAD_DARK);
        icon(g, I_HR, x + w / 2 + 10, by);
        text(g, Component.literal("HR " + hr), x + w / 2 + 28, by + 4, d.hunterRank >= hr ? OK_DARK : BAD_DARK);
        boolean ok = d.zenny >= zenny && d.hunterRank >= hr && SmithingRegistry.hasAll(p, costs);
        button(g, x + 10, y + h - 24, w - 20, 18, Component.translatable("screen.huntcraft.smith.do_levelup"), ok, () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("slot", slot);
            HCNetwork.sendToServer("village/smith", t);
        });
    }

    @Override
    protected void drawTooltips(GuiGraphics g, int mx, int my) {
        if (hoverStack != null && !hoverStack.isEmpty()) g.renderTooltip(this.font, hoverStack, mx, my);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }

    /** Group label of companion recipes ("mofu_weapon" ...). */
    static String mofuGroup(MofuGear.Slot s) {
        return "mofu_" + s.id;
    }
}
