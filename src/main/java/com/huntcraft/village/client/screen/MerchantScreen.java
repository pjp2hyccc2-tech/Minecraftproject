package com.huntcraft.village.client.screen;

import com.huntcraft.core.ShopRegistry;
import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.data.HunterData;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.village.client.ClientVillage;
import com.huntcraft.village.npc.NpcRole;
import com.huntcraft.village.npc.VillageNpc;
import com.huntcraft.village.shop.VillageShop;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
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

/**
 * General store (よろず屋): buy from {@link ShopRegistry} (stock grows with hunter rank, three daily deals), in bulk; sell
 * from the inventory or straight from the item box.
 */
@OnlyIn(Dist.CLIENT)
public class MerchantScreen extends VillageScreen {
    private static final int ROW_H = 18;
    private int tab; // 0 buy, 1 sell
    private int scroll;
    private int qty = 1;
    private final List<Row> rows = new ArrayList<>();
    @Nullable
    private Row selected;
    @Nullable
    private ItemStack hoverStack;
    private int lastBox = -1;
    @Nullable
    private final VillageNpc portrait;

    /** header (category), shop entry, or a sellable stack (inventory slot / item box index). */
    private record Row(@Nullable String header, @Nullable ShopRegistry.Entry entry, boolean deal, int invSlot, int boxIndex) {
        boolean sameAs(@Nullable Row o) {
            if (o == null) return false;
            if (entry != null) return o.entry == entry && o.deal == deal;
            return o.entry == null && o.header == null && o.invSlot == invSlot && o.boxIndex == boxIndex;
        }
    }

    public MerchantScreen(@Nullable CompoundTag tag) {
        super(Component.translatable("screen.huntcraft.shop.title"), 356, 226, tag);
        this.portrait = NpcPortrait.npc(NpcRole.MERCHANT);
    }

    @Override
    protected void init() {
        super.init();
        HCNetwork.sendToServer("village/box_request", new CompoundTag());
        rebuild();
    }

    private long day() {
        Player p = player();
        return p == null ? 0 : VillageShop.day(p.level());
    }

    private void rebuild() {
        rows.clear();
        Player p = player();
        HunterData d = data();
        if (tab == 0) {
            List<String> deals = VillageShop.deals(day(), d.hunterRank);
            if (!deals.isEmpty()) {
                rows.add(new Row("deals", null, false, -1, -1));
                for (String id : deals) {
                    ShopRegistry.Entry e = ShopRegistry.get(id);
                    if (e != null) rows.add(new Row(null, e, true, -1, -1));
                }
            }
            Map<String, List<ShopRegistry.Entry>> cats = new LinkedHashMap<>();
            for (ShopRegistry.Entry e : ShopRegistry.stock()) cats.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
            for (Map.Entry<String, List<ShopRegistry.Entry>> c : cats.entrySet()) {
                rows.add(new Row(c.getKey(), null, false, -1, -1));
                List<ShopRegistry.Entry> l = new ArrayList<>(c.getValue());
                l.sort((a, b) -> Integer.compare(a.requiredHR(), b.requiredHR()));
                for (ShopRegistry.Entry e : l) rows.add(new Row(null, e, false, -1, -1));
            }
        } else if (p != null) {
            boolean any = false;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (!st.isEmpty() && ShopRegistry.sellPrice(st) > 0) {
                    if (!any) rows.add(new Row("inventory", null, false, -1, -1));
                    any = true;
                    rows.add(new Row(null, null, false, i, -1));
                }
            }
            any = false;
            for (int i = 0; i < d.itemBox.size(); i++) {
                ItemStack st = d.itemBox.get(i);
                if (!st.isEmpty() && ShopRegistry.sellPrice(st) > 0) {
                    if (!any) rows.add(new Row("item_box", null, false, -1, -1));
                    any = true;
                    rows.add(new Row(null, null, false, -1, i));
                }
            }
        }
        if (selected != null) {
            Row keep = null;
            for (Row r : rows) if (r.sameAs(selected)) keep = r;
            selected = keep;
        }
    }

    private ItemStack stackOf(Row r) {
        Player p = player();
        if (r.entry != null) return new ItemStack(r.entry.item().get());
        if (r.invSlot >= 0 && p != null) return p.getInventory().getItem(r.invSlot);
        if (r.boxIndex >= 0) {
            HunterData d = data();
            return r.boxIndex < d.itemBox.size() ? d.itemBox.get(r.boxIndex) : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    private Component headerName(String h) {
        String key = "screen.huntcraft.shop.cat." + h.toLowerCase(Locale.ROOT);
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(h);
    }

    @Override
    public void tick() {
        super.tick();
        NpcPortrait.tick(portrait);
        Player p = player();
        if (p != null && (lastBox != ClientVillage.boxVersion || (tab == 1 && p.tickCount % 10 == 0))) {
            lastBox = ClientVillage.boxVersion;
            rebuild();
        }
    }

    @Override
    protected void drawContent(GuiGraphics g, int mx, int my, float pt) {
        hoverStack = null;
        titlePlate(g, this.title, I_BAG);
        walletBar(g);
        HunterData d = data();
        int tx = left + 10, ty = top + 24;
        String[] tabs = {"screen.huntcraft.shop.tab_buy", "screen.huntcraft.shop.tab_sell"};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tab(g, tx + i * 62, ty, 60, Component.translatable(tabs[i]), tab == i, () -> {
                tab = idx;
                scroll = 0;
                selected = null;
                qty = 1;
                rebuild();
                pageSound();
            });
        }
        int lx = left + 10, ly = top + 46, lw = 170, lh = panelH - 56;
        inset(g, lx, ly, lw, lh);
        int visible = (lh - 6) / ROW_H;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
        if (rows.isEmpty()) wrap(g, Component.translatable(tab == 0 ? "screen.huntcraft.shop.empty" : "screen.huntcraft.shop.nothing_to_sell"),
                lx + 6, ly + 8, lw - 12, GREY, 6);
        g.enableScissor(lx + 2, ly + 2, lx + lw - 2, ly + lh - 2);
        for (int i = 0; i < visible + 1 && i + scroll < rows.size(); i++) {
            Row r = rows.get(i + scroll);
            int ry = ly + 3 + i * ROW_H;
            if (r.header != null) {
                g.fill(lx + 3, ry + 2, lx + lw - 8, ry + ROW_H - 2, r.header.equals("deals") ? 0x60E05A20 : 0x50C9A66B);
                if (r.header.equals("deals")) icon(g, I_TAG, lx + 4, ry + 1);
                textClip(g, headerName(r.header), lx + (r.header.equals("deals") ? 22 : 7), ry + 5, lw - 30, GOLD, true);
                continue;
            }
            ItemStack st = stackOf(r);
            boolean locked = r.entry != null && r.entry.requiredHR() > d.hunterRank;
            boolean sel = r.sameAs(selected);
            boolean hover = in(mx, my, lx + 3, ry, lw - 10, ROW_H);
            rowHighlight(g, lx + 3, ry, lw - 10, ROW_H, sel, hover);
            if (tab == 1) itemWithCount(g, st, lx + 5, ry + 1);
            else item(g, st, lx + 5, ry + 1);
            String price;
            int pcol;
            if (r.entry != null) {
                price = locked ? "HR" + r.entry.requiredHR() : String.format("%,dz", VillageShop.unitPrice(r.entry, r.deal));
                pcol = locked ? GREY : r.deal ? GOOD : GOLD;
            } else {
                price = String.format("%,dz", ShopRegistry.sellPrice(st));
                pcol = GOLD;
            }
            int pw = this.font.width(price);
            textClip(g, st.getHoverName(), lx + 24, ry + 5, lw - 40 - pw, locked ? GREY : LIGHT, true);
            if (locked) icon(g, I_LOCK, lx + lw - 28 - pw, ry + 1);
            g.drawString(this.font, price, lx + lw - 10 - pw, ry + 5, pcol, true);
            final Row row = r;
            region(lx + 3, ry, lw - 10, ROW_H, () -> {
                if (!row.sameAs(selected)) qty = 1;
                selected = row;
            });
        }
        g.disableScissor();
        scrollBar(g, lx + lw - 6, ly + 3, lh - 6, rows.size() <= visible ? 0 : scroll / (float) (rows.size() - visible), visible / (float) Math.max(1, rows.size()));
        int dx = left + 186, dy = top + 46, dw = panelW - 196, dh = panelH - 56;
        paper(g, dx, dy, dw, dh);
        Player p = player();
        if (p == null) return;
        if (selected == null) {
            preview(g, dx + dw / 2, dy + dh - 30, 40, portrait, mx, my);
            wrap(g, Component.translatable(tab == 0 ? "screen.huntcraft.shop.hint_buy" : "screen.huntcraft.shop.hint_sell"), dx + 8, dy + 8, dw - 16, INK_SOFT, 4);
            textClip(g, Component.translatable("npc.huntcraft.merchant.line"), dx + 8, dy + dh - 14, dw - 16, INK, false);
            return;
        }
        if (selected.entry != null) drawBuy(g, p, d, selected, dx, dy, dw, dh);
        else drawSell(g, p, selected, dx, dy, dw, dh);
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

    private int description(GuiGraphics g, Player p, ItemStack st, int x, int y, int w, int maxLines) {
        List<Component> tip = st.getTooltipLines(p, TooltipFlag.NORMAL);
        int n = 0;
        for (int i = 1; i < tip.size() && n < maxLines; i++) {
            Component c = tip.get(i);
            if (c.getString().isBlank()) continue;
            int used = wrap(g, c, x, y + n * 10, w, INK_SOFT, maxLines - n) / 10;
            n += Math.max(1, used);
        }
        return n * 10;
    }

    private void qtySelector(GuiGraphics g, int x, int y, int w, int max) {
        qty = Math.max(1, Math.min(qty, Math.max(1, max)));
        iconButton(g, x, y, I_MINUS, qty > 1, () -> qty = Math.max(1, qty - 1));
        String s = "×" + qty;
        text(g, s, x + 40 - this.font.width(s) / 2, y + 6, INK);
        iconButton(g, x + 60, y, I_PLUS, qty < max, () -> qty = Math.min(max, qty + 1));
        button(g, x + 84, y, 26, 20, Component.literal("10"), max >= 2, () -> qty = Math.min(max, qty == 1 ? 10 : qty + 10));
        button(g, x + 112, y, Math.max(26, w - 112), 20, Component.translatable("screen.huntcraft.shop.max"), max > 1, () -> qty = max);
    }

    private void drawBuy(GuiGraphics g, Player p, HunterData d, Row r, int x, int y, int w, int h) {
        ShopRegistry.Entry e = r.entry;
        if (e == null) return;
        ItemStack st = new ItemStack(e.item().get());
        header(g, st, x + 8, y + 8, w - 8);
        int unit = VillageShop.unitPrice(e, r.deal);
        if (r.deal) {
            text(g, Component.translatable("screen.huntcraft.shop.deal", String.format("%,d", e.price()), String.format("%,d", unit))
                    .withStyle(ChatFormatting.DARK_GREEN), x + 46, y + 20, OK_DARK);
        } else {
            text(g, Component.literal(String.format("%,d z", unit)), x + 46, y + 20, 0xFF8A6A3A);
        }
        int cy = y + 44;
        cy += description(g, p, st, x + 10, cy, w - 20, 4) + 4;
        int owned = SmithingRegistry.count(p, e.item().get());
        text(g, Component.translatable("screen.huntcraft.shop.owned", owned), x + 10, cy, INK_SOFT);
        boolean locked = e.requiredHR() > d.hunterRank;
        int by = y + h - 66;
        if (locked) {
            icon(g, I_LOCK, x + 10, by + 22);
            wrap(g, Component.translatable("screen.huntcraft.shop.locked", e.requiredHR()), x + 30, by + 26, w - 40, BAD_DARK, 2);
            return;
        }
        int afford = unit <= 0 ? 99 : Math.min(99, d.zenny / unit);
        qtySelector(g, x + 10, by, w - 20, Math.max(1, afford));
        int total = unit * qty;
        icon(g, I_ZENNY, x + 10, by + 24);
        text(g, Component.translatable("screen.huntcraft.shop.total", String.format("%,d", total)), x + 28, by + 28, d.zenny >= total ? OK_DARK : BAD_DARK);
        boolean ok = d.zenny >= total && afford >= 1;
        button(g, x + 10, y + h - 24, w - 20, 18, Component.translatable("screen.huntcraft.shop.do_buy"), ok, () -> {
            CompoundTag t = new CompoundTag();
            t.putString("id", e.id());
            t.putInt("count", qty);
            HCNetwork.sendToServer("village/buy", t);
        });
    }

    private void drawSell(GuiGraphics g, Player p, Row r, int x, int y, int w, int h) {
        ItemStack st = stackOf(r);
        if (st.isEmpty()) {
            selected = null;
            return;
        }
        header(g, st, x + 8, y + 8, w - 8);
        int price = ShopRegistry.sellPrice(st);
        text(g, Component.translatable("screen.huntcraft.shop.sell_price", String.format("%,d", price)), x + 46, y + 20, 0xFF8A6A3A);
        int cy = y + 44;
        cy += description(g, p, st, x + 10, cy, w - 20, 4) + 4;
        text(g, Component.translatable(r.boxIndex >= 0 ? "screen.huntcraft.shop.from_box" : "screen.huntcraft.shop.from_inventory"), x + 10, cy, INK_SOFT);
        int by = y + h - 66;
        qtySelector(g, x + 10, by, w - 20, st.getCount());
        icon(g, I_ZENNY, x + 10, by + 24);
        text(g, Component.translatable("screen.huntcraft.shop.total", String.format("%,d", price * qty)), x + 28, by + 28, OK_DARK);
        final int count = qty;
        button(g, x + 10, y + h - 24, w - 20, 18, Component.translatable("screen.huntcraft.shop.do_sell"), true, () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("count", count);
            if (r.boxIndex >= 0) {
                t.putInt("index", r.boxIndex);
                HCNetwork.sendToServer("village/sell_box", t);
            } else {
                t.putInt("slot", r.invSlot);
                HCNetwork.sendToServer("shop/sell", t);
            }
            qty = 1;
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
