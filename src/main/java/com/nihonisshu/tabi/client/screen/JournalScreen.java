package com.nihonisshu.tabi.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nihonisshu.tabi.client.ClientState;
import com.nihonisshu.tabi.data.TabiData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Travel journal: left page = list of records by category (locked ones shown as ？？？), right page = the selected
 * record with its explanation ("why is this a local speciality?").
 */
public class JournalScreen extends Screen {
    private record Rec(String key, String title, String category, String place, String text, String why, boolean open) {}

    private static final String[] CATEGORY_ORDER = {"spot", "food", "souvenir", "ride", "station", "people", "life"};
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "spot", "名所", "food", "味", "souvenir", "おみやげ", "ride", "乗りもの", "station", "駅", "people", "人", "life", "くらし");

    private final Map<String, List<Rec>> byCat = new LinkedHashMap<>();
    private String tab;
    private Rec selected;
    private int scroll, textScroll;
    private int total, opened;

    public JournalScreen() {
        super(Component.literal("旅の手帳"));
        JsonObject entries = TabiData.journal().getAsJsonObject("entries");
        for (String c : CATEGORY_ORDER) byCat.put(c, new ArrayList<>());
        if (entries != null) {
            for (Map.Entry<String, JsonElement> e : entries.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                String cat = TabiData.str(o, "category", "life");
                boolean open = ClientState.unlocked(e.getKey());
                Rec r = new Rec(e.getKey(), TabiData.str(o, "title", e.getKey()), cat, TabiData.str(o, "place", ""),
                        TabiData.str(o, "text", ""), TabiData.str(o, "why", ""), open);
                byCat.computeIfAbsent(cat, k -> new ArrayList<>()).add(r);
                total++;
                if (open) opened++;
            }
        }
        tab = "spot";
        for (String c : CATEGORY_ORDER) {
            if (byCat.get(c).stream().anyMatch(Rec::open)) {
                tab = c;
                break;
            }
        }
        selected = byCat.get(tab).stream().filter(Rec::open).findFirst().orElse(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int bw() { return Math.min(400, width - 16); }
    private int bh() { return Math.min(240, height - 16); }
    private int bx() { return (width - bw()) / 2; }
    private int by() { return (height - bh()) / 2; }
    private int pageW() { return bw() / 2 - 12; }
    private int listTop() { return by() + 40; }
    private int listBottom() { return by() + bh() - 22; }

    private List<Rec> list() {
        return byCat.getOrDefault(tab, List.of());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = bx(), y = by(), w = bw(), h = bh();
        // cover + pages
        g.fill(x - 6, y - 6, x + w + 6, y + h + 6, 0xFF7A2E22);
        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xFF97392A);
        g.fill(x, y, x + w / 2 - 1, y + h, 0xFFFBF6E8);
        g.fill(x + w / 2 + 1, y, x + w, y + h, 0xFFFBF6E8);
        g.fill(x + w / 2 - 1, y, x + w / 2 + 1, y + h, 0xFFD8CCAE);
        for (int i = 0; i * 12 + 40 < h - 10; i++) {
            g.fill(x + 6, y + 49 + i * 12, x + w / 2 - 6, y + 50 + i * 12, 0x18405080);
            g.fill(x + w / 2 + 6, y + 49 + i * 12, x + w - 6, y + 50 + i * 12, 0x18405080);
        }
        // tabs
        int tx = x + 6;
        for (String c : CATEGORY_ORDER) {
            if (byCat.get(c).isEmpty()) continue;
            String nm = CATEGORY_NAMES.getOrDefault(c, c);
            int tw = font.width(nm) + 8;
            boolean on = c.equals(tab);
            g.fill(tx, y + 6, tx + tw, y + 20, on ? 0xFF27406B : 0xFFE6DCC4);
            g.drawString(font, nm, tx + 4, y + 9, on ? 0xFFFFFFFF : 0xFF5A4A3A, false);
            tx += tw + 3;
            if (tx > x + w / 2 - 20) break;
        }
        String prog = "記録 " + opened + " / " + total;
        g.drawString(font, prog, x + w / 2 - 8 - font.width(prog), y + 26, 0xFF8A7A5A, false);
        String money = "所持金 " + String.format("%,d", ClientState.yen()) + "円";
        g.drawString(font, money, x + 8, y + 26, 0xFF5A4A3A, false);

        // list (left page)
        int top = listTop(), bottom = listBottom();
        List<Rec> recs = list();
        int maxScroll = Math.max(0, recs.size() * 12 - (bottom - top));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        g.enableScissor(x, top, x + w / 2 - 1, bottom);
        for (int i = 0; i < recs.size(); i++) {
            Rec r = recs.get(i);
            int ry = top + i * 12 - scroll;
            boolean hover = mx >= x + 6 && mx < x + w / 2 - 6 && my >= ry && my < ry + 12 && my >= top && my < bottom;
            if (r == selected) g.fill(x + 6, ry, x + w / 2 - 6, ry + 12, 0x3327406B);
            else if (hover && r.open) g.fill(x + 6, ry, x + w / 2 - 6, ry + 12, 0x22000000);
            String label = r.open ? "・" + r.title : "・？？？";
            if (font.width(label) > pageW() - 4) label = font.plainSubstrByWidth(label, pageW() - 12) + "…";
            g.drawString(font, label, x + 8, ry + 2, r.open ? 0xFF2A2219 : 0xFFB0A68E, false);
        }
        g.disableScissor();

        // right page
        int rx = x + w / 2 + 10, rw = w / 2 - 20;
        if (selected == null) {
            g.drawString(font, "まだ記録がありません。", rx, y + 14, 0xFF8A7A5A, false);
            int yy = y + 30;
            for (FormattedCharSequence s : font.split(Component.literal("人と話したり、名所を訪ねたり、名物を味わったりすると、ここに書きこまれていきます。"), rw)) {
                g.drawString(font, s, rx, yy, 0xFF5A4A3A, false);
                yy += 11;
            }
        } else {
            g.drawString(font, selected.title, rx, y + 10, 0xFF7A2E22, false);
            if (!selected.place.isEmpty()) g.drawString(font, selected.place, rx, y + 22, 0xFF8A7A5A, false);
            List<FormattedCharSequence> body = new ArrayList<>(font.split(Component.literal(selected.text), rw));
            if (!selected.why.isEmpty()) {
                body.add(FormattedCharSequence.EMPTY);
                body.addAll(font.split(Component.literal("§3【なるほどメモ】§r" + selected.why), rw));
            }
            int areaTop = y + 36, areaBottom = y + h - 10;
            int maxText = Math.max(0, body.size() * 11 - (areaBottom - areaTop));
            textScroll = Math.max(0, Math.min(textScroll, maxText));
            g.enableScissor(x + w / 2 + 1, areaTop, x + w, areaBottom);
            int yy = areaTop - textScroll;
            for (FormattedCharSequence s : body) {
                g.drawString(font, s, rx, yy, 0xFF2A2219, false);
                yy += 11;
            }
            g.disableScissor();
            if (maxText > 0) g.drawString(font, "↕", x + w - 12, y + h - 12, 0xFF8A7A5A, false);
        }
        g.drawCenteredString(font, "旅の手帳", x + w / 4, y + h - 14, 0xFF97392A);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = bx(), y = by(), w = bw();
        int tx = x + 6;
        for (String c : CATEGORY_ORDER) {
            if (byCat.get(c).isEmpty()) continue;
            int tw = font.width(CATEGORY_NAMES.getOrDefault(c, c)) + 8;
            if (mx >= tx && mx < tx + tw && my >= y + 6 && my < y + 20) {
                tab = c;
                scroll = 0;
                selected = byCat.get(c).stream().filter(Rec::open).findFirst().orElse(null);
                textScroll = 0;
                click();
                return true;
            }
            tx += tw + 3;
            if (tx > x + w / 2 - 20) break;
        }
        int top = listTop(), bottom = listBottom();
        if (mx >= x + 6 && mx < x + w / 2 - 6 && my >= top && my < bottom) {
            int i = (int) ((my - top + scroll) / 12);
            List<Rec> recs = list();
            if (i >= 0 && i < recs.size() && recs.get(i).open) {
                selected = recs.get(i);
                textScroll = 0;
                click();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < bx() + bw() / 2) scroll -= (int) (delta * 12);
        else textScroll -= (int) (delta * 11);
        return true;
    }
}
