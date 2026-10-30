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
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/** Ticket vending machine (kippu uriba): route strip on top, a button per destination with its fare. */
public class TicketScreen extends Screen {
    private record Dest(int idx, String name, String kana, int fare) {}

    private final CompoundTag data;
    private final List<Dest> dests = new ArrayList<>();
    private final int fromIdx;
    private final int yen;

    public TicketScreen(CompoundTag data) {
        super(Component.literal("きっぷうりば"));
        this.data = data;
        this.fromIdx = data.getInt("from");
        this.yen = data.getInt("yen");
        ListTag l = data.getList("dests", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag d = l.getCompound(i);
            dests.add(new Dest(d.getInt("idx"), d.getString("name"), d.getString("kana"), d.getInt("fare")));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int pw() { return Math.min(300, width - 20); }
    private int ph() { return Math.min(70 + Math.max(dests.size(), 1) * 24 + 26, height - 20); }
    private int px() { return (width - pw()) / 2; }
    private int py() { return (height - ph()) / 2; }
    private int rowY(int i) { return py() + 66 + i * 24; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = px(), y = py(), w = pw(), h = ph();
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF15181C);
        g.fill(x, y, x + w, y + h, 0xFF2C333B);
        g.fill(x, y, x + w, y + 18, 0xFF1C7A3E);
        g.drawCenteredString(font, "きっぷうりば  " + data.getString("routeName"), x + w / 2, y + 5, 0xFFFFFFFF);
        // route strip
        int sx0 = x + 20, sx1 = x + w - 20, sy = y + 36;
        g.fill(sx0, sy - 1, sx1, sy + 2, 0xFF2FA05A);
        List<Dest> all = new ArrayList<>(dests);
        all.add(new Dest(fromIdx, data.getString("fromName"), "", 0));
        all.sort((a, b) -> Integer.compare(a.idx, b.idx));
        int n = all.size();
        for (int i = 0; i < n; i++) {
            Dest d = all.get(i);
            int cx = n == 1 ? (sx0 + sx1) / 2 : sx0 + (sx1 - sx0) * i / (n - 1);
            boolean here = d.idx == fromIdx;
            g.fill(cx - 3, sy - 3, cx + 4, sy + 4, here ? 0xFFFFC233 : 0xFFFFFFFF);
            String nm = d.name;
            int tw = font.width(nm);
            g.drawString(font, nm, cx - tw / 2, sy + (i % 2 == 0 ? 7 : -13), here ? 0xFFFFC233 : 0xFFCFD8DC, false);
        }
        g.drawString(font, "現在地", x + 8, y + 52, 0xFFFFC233, false);
        String money = "所持金 " + String.format("%,d", yen) + "円";
        g.drawString(font, money, x + w - 8 - font.width(money), y + 52, 0xFFFFFFFF, false);
        // destination buttons
        for (int i = 0; i < dests.size(); i++) {
            Dest d = dests.get(i);
            int ry = rowY(i);
            boolean hover = mx >= x + 8 && mx < x + w - 8 && my >= ry && my < ry + 20;
            boolean afford = d.fare <= yen;
            int bg = !afford ? 0xFF4A4F55 : hover ? 0xFFFFE08A : 0xFFF2F2EE;
            g.fill(x + 8, ry, x + w - 8, ry + 20, 0xFF0E1013);
            g.fill(x + 9, ry + 1, x + w - 9, ry + 19, bg);
            int tc = afford ? 0xFF1A1A1A : 0xFF9AA0A6;
            g.drawString(font, d.name, x + 16, ry + 6, tc, false);
            if (!d.kana.isEmpty()) g.drawString(font, d.kana, x + 20 + font.width(d.name), ry + 6, afford ? 0xFF667080 : 0xFF80868C, false);
            String fare = String.format("%,d", d.fare) + "円";
            g.drawString(font, fare, x + w - 16 - font.width(fare), ry + 6, afford ? 0xFFC0392B : 0xFF80868C, false);
        }
        if (dests.isEmpty()) g.drawCenteredString(font, "この駅から買える切符はありません", x + w / 2, y + 72, 0xFFFFFFFF);
        g.drawCenteredString(font, "行き先を選ぶと切符が出てきます", x + w / 2, y + h - 14, 0xFF9FB0BF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = px(), w = pw();
        for (int i = 0; i < dests.size(); i++) {
            int ry = rowY(i);
            if (mx >= x + 8 && mx < x + w - 8 && my >= ry && my < ry + 20) {
                Dest d = dests.get(i);
                if (d.fare > yen) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0f));
                    return true;
                }
                CompoundTag t = new CompoundTag();
                t.putString("route", data.getString("route"));
                t.putInt("from", fromIdx);
                t.putInt("to", d.idx);
                TabiNet.toServer("ticket", t);
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
