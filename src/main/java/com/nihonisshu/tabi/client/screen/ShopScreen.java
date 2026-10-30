package com.nihonisshu.tabi.client.screen;

import com.nihonisshu.tabi.network.TabiNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Shop counter: cafes, stalls, souvenir shops (buy) and the farmers' market (sell). */
public class ShopScreen extends Screen {
    private record Entry(int index, ItemStack stack, int price, String note) {}

    private String shopId = "";
    private String mode = "buy";
    private String greet = "";
    private int yen;
    private final List<Entry> items = new ArrayList<>();
    private int scroll;
    private int flash;

    public ShopScreen(CompoundTag data) {
        super(Component.literal(data.getString("name")));
        parse(data);
    }

    public String shopId() {
        return shopId;
    }

    public void refresh(CompoundTag data) {
        parse(data);
        flash = 12;
    }

    private void parse(CompoundTag d) {
        shopId = d.getString("id");
        mode = d.getString("mode");
        greet = d.getString("greet");
        yen = d.getInt("yen");
        items.clear();
        ListTag l = d.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag e = l.getCompound(i);
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(e.getString("item")));
            if (it == null) it = Items.BARRIER;
            items.add(new Entry(i, new ItemStack(it), e.getInt("price"), e.getString("note")));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (flash > 0) flash--;
    }

    private boolean selling() {
        return "sell".equals(mode);
    }

    private int panelW() { return Math.min(320, width - 20); }
    private int panelH() { return Math.min(250, height - 20); }
    private int panelX() { return (width - panelW()) / 2; }
    private int panelY() { return (height - panelH()) / 2; }
    private int listTop() { return panelY() + 50; }
    private int listBottom() { return panelY() + panelH() - 22; }
    private static final int ROW = 26;

    private int maxScroll() {
        return Math.max(0, items.size() * ROW - (listBottom() - listTop()));
    }

    private int held(ItemStack s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (st.is(s.getItem())) n += st.getCount();
        }
        return n;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = panelX(), y = panelY(), w = panelW(), h = panelH();
        int wood = selling() ? 0xFF5B6B3A : 0xFF6B4226;
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF2A1A10);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, wood);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFFF7F1E1);
        // noren style header
        g.fill(x + 4, y + 4, x + w - 4, y + 24, selling() ? 0xFF3E5A2A : 0xFF27406B);
        for (int i = 1; i < 6; i++) g.fill(x + 4 + i * (w - 8) / 6, y + 4, x + 5 + i * (w - 8) / 6, y + 24, 0x55FFFFFF);
        g.drawCenteredString(font, title.getString(), x + w / 2, y + 10, 0xFFFFFFFF);
        // greeting
        int gy = y + 28;
        for (FormattedCharSequence s : font.split(Component.literal(greet), w - 20).stream().limit(2).toList()) {
            g.drawString(font, s, x + 10, gy, 0xFF5A4A3A, false);
            gy += 10;
        }
        // money
        String money = "所持金 " + String.format("%,d", yen) + "円";
        int mcol = flash > 0 ? 0xFFE0A000 : 0xFF2A2219;
        g.drawString(font, money, x + w - 10 - font.width(money), y + h - 16, mcol, false);
        g.drawString(font, selling() ? "クリックで全部売る" : "クリックで1つ / Shift+クリックで5つ", x + 10, y + h - 16, 0xFF8A7A5A, false);

        // list
        int top = listTop(), bottom = listBottom();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        g.enableScissor(x + 4, top, x + w - 4, bottom);
        Entry hovered = null;
        for (Entry e : items) {
            int ry = top + e.index * ROW - scroll;
            if (ry + ROW < top || ry > bottom) continue;
            boolean hover = mx >= x + 6 && mx < x + w - 6 && my >= ry && my < ry + ROW - 2 && my >= top && my < bottom;
            if (hover) hovered = e;
            g.fill(x + 6, ry, x + w - 6, ry + ROW - 2, hover ? 0xFFFFF0C8 : (e.index % 2 == 0 ? 0xFFEFE6D0 : 0xFFF3ECDA));
            g.renderItem(e.stack, x + 10, ry + 4);
            g.drawString(font, e.stack.getHoverName().getString(), x + 32, ry + 3, 0xFF2A2219, false);
            String note = e.note;
            if (selling()) note = "持っている：" + held(e.stack) + "こ";
            if (!note.isEmpty()) {
                int maxw = w - 120;
                String n = note;
                if (font.width(n) > maxw) n = font.plainSubstrByWidth(n, maxw - 8) + "…";
                g.drawString(font, n, x + 32, ry + 14, 0xFF7A6A55, false);
            }
            String price = (selling() ? "1こ " : "") + String.format("%,d", e.price) + "円";
            int pc = !selling() && e.price > yen ? 0xFFB0381E : 0xFF1F2F4F;
            g.drawString(font, price, x + w - 12 - font.width(price), ry + 8, pc, false);
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int track = bottom - top;
            int knob = Math.max(16, track * track / (track + maxScroll()));
            int ky = top + (track - knob) * scroll / maxScroll();
            g.fill(x + w - 5, ky, x + w - 3, ky + knob, 0xFF9A8A6A);
        }
        super.render(g, mx, my, pt);
        if (hovered != null) {
            List<FormattedCharSequence> tip = new ArrayList<>();
            for (Component c : Screen.getTooltipFromItem(Minecraft.getInstance(), hovered.stack)) tip.addAll(font.split(c, 220));
            if (!hovered.note.isEmpty()) tip.addAll(font.split(Component.literal("§7" + hovered.note), 220));
            g.renderTooltip(font, tip, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = panelX(), w = panelW(), top = listTop(), bottom = listBottom();
        if (mx >= x + 6 && mx < x + w - 6 && my >= top && my < bottom) {
            int idx = (int) ((my - top + scroll) / ROW);
            if (idx >= 0 && idx < items.size()) {
                Entry e = items.get(idx);
                CompoundTag t = new CompoundTag();
                t.putString("shop", shopId);
                t.putInt("index", e.index);
                if (selling()) {
                    TabiNet.toServer("sell", t);
                } else {
                    t.putInt("count", hasShiftDown() ? 5 : 1);
                    TabiNet.toServer("buy", t);
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (delta * 14)));
        return true;
    }
}
