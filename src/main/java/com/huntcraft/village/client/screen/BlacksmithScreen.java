package com.huntcraft.village.client.screen;

import com.huntcraft.core.IUpgradeable;
import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.data.HunterData;
import com.huntcraft.network.HCNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Blacksmith: forge weapons / armor from SmithingRegistry recipes and level up IUpgradeable gear. */
@OnlyIn(Dist.CLIENT)
public class BlacksmithScreen extends VillageScreen {
    private static final int ROW_H = 18;
    private int tab; // 0 weapon, 1 armor, 2 level-up
    private int scroll;
    @Nullable
    private SmithingRegistry.Recipe selected;
    private int selectedSlot = -1;
    private final List<Row> rows = new ArrayList<>();
    @Nullable
    private ItemStack hoverStack;

    private record Row(@Nullable String header, @Nullable SmithingRegistry.Recipe recipe, int slot) {
    }

    public BlacksmithScreen() {
        super(Component.translatable("screen.huntcraft.smith.title"), 330, 214);
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
        if (tab < 2) {
            SmithingRegistry.Category cat = tab == 0 ? SmithingRegistry.Category.WEAPON : SmithingRegistry.Category.ARMOR;
            Map<String, List<SmithingRegistry.Recipe>> groups = new LinkedHashMap<>();
            for (SmithingRegistry.Recipe r : SmithingRegistry.byCategory(cat)) groups.computeIfAbsent(r.group, k -> new ArrayList<>()).add(r);
            for (Map.Entry<String, List<SmithingRegistry.Recipe>> e : groups.entrySet()) {
                rows.add(new Row(e.getKey(), null, -1));
                for (SmithingRegistry.Recipe r : e.getValue()) rows.add(new Row(null, r, -1));
            }
        } else if (p != null) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (st.getItem() instanceof IUpgradeable) rows.add(new Row(null, null, i));
            }
        }
        if (selected != null && rows.stream().noneMatch(r -> r.recipe == selected)) selected = null;
        if (selectedSlot >= 0 && rows.stream().noneMatch(r -> r.slot == selectedSlot)) selectedSlot = -1;
    }

    public static Component groupName(String group) {
        for (WeaponClass wc : WeaponClass.values()) if (wc.name().equalsIgnoreCase(group)) return Component.translatable(wc.key());
        String g = group.toLowerCase(Locale.ROOT);
        if (I18n.exists("armor_set.huntcraft." + g)) return Component.translatable("armor_set.huntcraft." + g);
        if (I18n.exists("entity.huntcraft." + g)) return Component.translatable("screen.huntcraft.smith.armor_set", Component.translatable("entity.huntcraft." + g));
        String pretty = g.replace('_', ' ');
        return Component.literal(pretty.isEmpty() ? "-" : Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1));
    }

    @Override
    public void tick() {
        super.tick();
        if (tab == 2 && player() != null && player().tickCount % 10 == 0) rebuildRows();
    }

    @Override
    protected void drawContent(GuiGraphics g, int mx, int my, float pt) {
        hoverStack = null;
        titlePlate(g, this.title, I_HAMMER);
        walletBar(g);
        int tx = left + 10, ty = top + 26;
        String[] tabs = {"screen.huntcraft.smith.tab_weapon", "screen.huntcraft.smith.tab_armor", "screen.huntcraft.smith.tab_levelup"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            tab(g, tx + i * 46, ty, 44, Component.translatable(tabs[i]), tab == i, () -> {
                tab = idx;
                scroll = 0;
                selected = null;
                selectedSlot = -1;
                rebuildRows();
                pageSound();
            });
        }
        // list
        int lx = left + 10, ly = top + 46, lw = 136, lh = panelH - 56;
        inset(g, lx, ly, lw, lh);
        int visible = (lh - 6) / ROW_H;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
        if (rows.isEmpty()) {
            wrap(g, Component.translatable(tab == 2 ? "screen.huntcraft.smith.no_gear" : "screen.huntcraft.smith.no_recipes"), lx + 6, ly + 8, lw - 12, GREY, 6);
        }
        Player p = player();
        g.enableScissor(lx + 2, ly + 2, lx + lw - 2, ly + lh - 2);
        for (int i = 0; i < visible + 1 && i + scroll < rows.size(); i++) {
            Row r = rows.get(i + scroll);
            int ry = ly + 3 + i * ROW_H;
            if (r.header != null) {
                g.fill(lx + 3, ry + 2, lx + lw - 3, ry + ROW_H - 2, 0x40C9A66B);
                textClip(g, groupName(r.header), lx + 7, ry + 5, lw - 14, GOLD, true);
                continue;
            }
            ItemStack st = r.recipe != null ? new ItemStack(r.recipe.result.get()) : (p != null ? p.getInventory().getItem(r.slot) : ItemStack.EMPTY);
            boolean sel = (r.recipe != null && r.recipe == selected) || (r.slot >= 0 && r.slot == selectedSlot);
            boolean hover = in(mx, my, lx + 3, ry, lw - 10, ROW_H);
            rowHighlight(g, lx + 3, ry, lw - 10, ROW_H, sel, hover);
            item(g, st, lx + 5, ry + 1);
            Component name = st.getHoverName();
            int nameCol = LIGHT;
            if (r.recipe != null && p != null && !canForge(p, r.recipe)) nameCol = 0xFFB8A890;
            textClip(g, name, lx + 24, ry + 5, lw - 34 - (r.recipe != null && r.recipe.upgradeFrom != null ? 10 : 0), nameCol, true);
            if (r.recipe != null && r.recipe.upgradeFrom != null) g.drawString(this.font, "↑", lx + lw - 16, ry + 5, GOOD, true);
            if (r.slot >= 0 && st.getItem() instanceof IUpgradeable up) {
                String lv = "Lv" + up.getLevel(st);
                g.drawString(this.font, lv, lx + lw - 12 - this.font.width(lv), ry + 5, GOLD, true);
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
        // detail
        int dx = left + 152, dy = top + 46, dw = panelW - 162, dh = panelH - 56;
        paper(g, dx, dy, dw, dh);
        if (p == null) return;
        if (selected != null) drawRecipe(g, p, selected, dx, dy, dw, dh, mx, my);
        else if (selectedSlot >= 0) drawLevelUp(g, p, selectedSlot, dx, dy, dw, dh, mx, my);
        else wrap(g, Component.translatable(tab == 2 ? "screen.huntcraft.smith.pick_gear" : "screen.huntcraft.smith.pick_recipe"), dx + 10, dy + 12, dw - 20, INK_SOFT, 6);
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

    private int detailLines(GuiGraphics g, Player p, ItemStack st, int x, int y, int w, int max) {
        List<Component> lines = st.getTooltipLines(p, TooltipFlag.NORMAL);
        int n = 0;
        for (int i = 1; i < lines.size() && n < max; i++) {
            Component c = lines.get(i);
            if (c.getString().isBlank()) continue;
            textClip(g, c, x, y + n * 10, w, INK_SOFT, false);
            n++;
        }
        return n * 10;
    }

    private int costRow(GuiGraphics g, Player p, Item item, int need, int x, int y, int w) {
        ItemStack st = new ItemStack(item);
        g.renderItem(st, x, y);
        if (in(mouseX, mouseY, x, y, 16, 16)) hoverStack = st;
        int have = SmithingRegistry.count(p, item);
        String cnt = have + "/" + need;
        textClip(g, st.getHoverName(), x + 20, y + 4, w - 26 - this.font.width(cnt), INK, false);
        g.drawString(this.font, cnt, x + w - this.font.width(cnt) - 2, y + 4, have >= need ? 0xFF2E8B2E : 0xFFC0392B, false);
        return 18;
    }

    private void drawRecipe(GuiGraphics g, Player p, SmithingRegistry.Recipe r, int x, int y, int w, int h, int mx, int my) {
        HunterData d = HunterData.get(p);
        ItemStack out = new ItemStack(r.result.get());
        header(g, out, x + 8, y + 8, w - 8);
        int cy = y + 22;
        text(g, Component.translatable(r.upgradeFrom != null ? "screen.huntcraft.smith.kind_upgrade" : "screen.huntcraft.smith.kind_forge"), x + 46, cy, 0xFF8A6A3A);
        cy = y + 44;
        cy += detailLines(g, p, out, x + 10, cy, w - 20, 3) + 4;
        g.fill(x + 8, cy, x + w - 8, cy + 1, 0x60402A10);
        cy += 4;
        if (r.upgradeFrom != null) {
            Item from = r.upgradeFrom.get();
            boolean has = hasItem(p, from);
            text(g, Component.translatable("screen.huntcraft.smith.from"), x + 10, cy + 4, INK_SOFT);
            ItemStack fs = new ItemStack(from);
            int fx = x + 14 + this.font.width(Component.translatable("screen.huntcraft.smith.from"));
            g.renderItem(fs, fx, cy);
            if (in(mx, my, fx, cy, 16, 16)) hoverStack = fs;
            textClip(g, fs.getHoverName(), fx + 20, cy + 4, w - (fx - x) - 34, has ? 0xFF2E8B2E : 0xFFC0392B, false);
            cy += 18;
        }
        text(g, Component.translatable("screen.huntcraft.smith.materials"), x + 10, cy, INK_SOFT);
        cy += 11;
        for (SmithingRegistry.Cost c : r.costs) {
            if (cy > y + h - 46) break;
            cy += costRow(g, p, c.item().get(), c.count(), x + 10, cy, w - 20);
        }
        int by = y + h - 42;
        icon(g, I_ZENNY, x + 10, by);
        text(g, Component.literal(String.format("%,d / %,d z", d.zenny, r.zenny)), x + 28, by + 4, d.zenny >= r.zenny ? 0xFF2E8B2E : 0xFFC0392B);
        icon(g, I_HR, x + w / 2 + 14, by);
        text(g, Component.literal("HR " + r.requiredHR), x + w / 2 + 32, by + 4, d.hunterRank >= r.requiredHR ? 0xFF2E8B2E : 0xFFC0392B);
        boolean ok = canForge(p, r);
        button(g, x + 10, y + h - 24, w - 20, 18, Component.translatable(r.upgradeFrom != null ? "screen.huntcraft.smith.do_upgrade" : "screen.huntcraft.smith.do_forge"), ok, () -> {
            CompoundTag t = new CompoundTag();
            t.putString("id", r.id);
            HCNetwork.sendToServer("smith/forge", t);
            HCNetwork.sendToServer("village/box_request", new CompoundTag());
        });
    }

    private void drawLevelUp(GuiGraphics g, Player p, int slot, int x, int y, int w, int h, int mx, int my) {
        ItemStack st = p.getInventory().getItem(slot);
        if (!(st.getItem() instanceof IUpgradeable up)) return;
        HunterData d = HunterData.get(p);
        header(g, st, x + 8, y + 8, w - 8);
        int lv = up.getLevel(st), max = up.getMaxLevel(st);
        text(g, Component.translatable("screen.huntcraft.smith.level", lv, max), x + 46, y + 22, 0xFF8A6A3A);
        int cy = y + 44;
        // level pips
        for (int i = 0; i < max; i++) {
            int px = x + 10 + i * 9;
            if (px > x + w - 14) break;
            g.fill(px, cy, px + 7, cy + 5, i < lv ? 0xFFE0A030 : 0x60402A10);
        }
        cy += 10;
        cy += detailLines(g, p, st, x + 10, cy, w - 20, 3) + 4;
        if (lv >= max) {
            text(g, Component.translatable("screen.huntcraft.smith.max_level").withStyle(ChatFormatting.BOLD), x + 10, cy + 6, 0xFF8A6A3A);
            return;
        }
        int next = lv + 1;
        g.fill(x + 8, cy, x + w - 8, cy + 1, 0x60402A10);
        cy += 4;
        text(g, Component.translatable("screen.huntcraft.smith.next_level", next), x + 10, cy, INK_SOFT);
        cy += 11;
        List<SmithingRegistry.Cost> costs = up.getUpgradeCost(st, next);
        for (SmithingRegistry.Cost c : costs) {
            if (cy > y + h - 46) break;
            cy += costRow(g, p, c.item().get(), c.count(), x + 10, cy, w - 20);
        }
        int zenny = up.getUpgradeZenny(st, next), hr = up.getRequiredHR(st, next);
        int by = y + h - 42;
        icon(g, I_ZENNY, x + 10, by);
        text(g, Component.literal(String.format("%,d / %,d z", d.zenny, zenny)), x + 28, by + 4, d.zenny >= zenny ? 0xFF2E8B2E : 0xFFC0392B);
        icon(g, I_HR, x + w / 2 + 14, by);
        text(g, Component.literal("HR " + hr), x + w / 2 + 32, by + 4, d.hunterRank >= hr ? 0xFF2E8B2E : 0xFFC0392B);
        boolean ok = d.zenny >= zenny && d.hunterRank >= hr && SmithingRegistry.hasAll(p, costs);
        button(g, x + 10, y + h - 24, w - 20, 18, Component.translatable("screen.huntcraft.smith.do_levelup"), ok, () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("slot", slot);
            HCNetwork.sendToServer("smith/levelup", t);
            HCNetwork.sendToServer("village/box_request", new CompoundTag());
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
}
