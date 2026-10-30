package com.mcbesiege.client;

import com.mcbesiege.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** The mission table: siege campaign map with two islands of missions. */
public class MissionScreen extends Screen {
    private static final ResourceLocation LOGO = new ResourceLocation("mcbesiege", "textures/gui/logo.png");

    private record Entry(String id, int island, int stars, boolean done) {}

    private final List<Entry> entries = new ArrayList<>();
    private final String active;
    private int selected = 0;
    private int left, top, w, h;
    private Button startBtn;

    public MissionScreen(CompoundTag data) {
        super(Component.translatable("screen.mcbesiege.missions"));
        ListTag l = data.getList("missions", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            entries.add(new Entry(c.getString("id"), c.getInt("island"), c.getInt("stars"), c.getBoolean("done")));
        }
        active = data.getString("active");
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(active)) selected = i;
    }

    @Override
    protected void init() {
        w = Math.min(width - 20, 400);
        h = Math.min(height - 20, 250);
        left = (width - w) / 2;
        top = (height - h) / 2 + 10;
        startBtn = addRenderableWidget(Button.builder(Component.translatable("button.mcbesiege.mission_start"), b -> {
            if (selected < 0 || selected >= entries.size()) return;
            CompoundTag t = new CompoundTag();
            t.putString("id", entries.get(selected).id());
            Net.toServer("mission_start", t);
            onClose();
        }).bounds(left + w - 128, top + h - 26, 116, 20).build());
        if (!active.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("button.mcbesiege.mission_leave"), b -> {
                Net.toServer("mission_leave", new CompoundTag());
                onClose();
            }).bounds(left + w - 248, top + h - 26, 116, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 10, top + h - 26, 60, 20).build());
    }

    private int rowY(int i) {
        Entry e = entries.get(i);
        int idxInIsland = 0;
        for (int k = 0; k < i; k++) if (entries.get(k).island() == e.island()) idxInIsland++;
        return top + 38 + idxInIsland * 22 + (e.island() == 1 ? 0 : 0);
    }

    private int rowX(int i) {
        return entries.get(i).island() == 1 ? left + 10 : left + 10 + 150;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < entries.size(); i++) {
            int x = rowX(i), y = rowY(i);
            if (mx >= x && mx < x + 144 && my >= y && my < y + 20) {
                selected = i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        g.blit(LOGO, (width - 160) / 2, Math.max(2, top - 44), 160, 40, 0, 0, 256, 64, 256, 64);
        g.fill(left - 2, top - 2, left + w + 2, top + h + 2, 0xFF6B4A2E);
        g.fill(left, top, left + w, top + h, 0xF0E8D8B0);
        g.fill(left, top, left + w, top + 22, 0xFF3A2C1E);
        g.drawString(font, title, left + 10, top + 7, 0xFFFFD27A, true);
        g.drawString(font, Component.translatable("island.mcbesiege.1"), left + 12, top + 26, 0xFF3A2C1E, false);
        g.drawString(font, Component.translatable("island.mcbesiege.2"), left + 162, top + 26, 0xFF3A2C1E, false);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int x = rowX(i), y = rowY(i);
            boolean hover = mouseX >= x && mouseX < x + 144 && mouseY >= y && mouseY < y + 20;
            int bg = i == selected ? 0xFFC8962C : (hover ? 0xFFD8C090 : 0xFFCDBB92);
            g.fill(x, y, x + 144, y + 20, 0xFF6B4A2E);
            g.fill(x + 1, y + 1, x + 143, y + 19, bg);
            g.drawString(font, Component.translatable("mission.mcbesiege." + e.id()), x + 5, y + 6, 0xFF2A1E12, false);
            g.drawString(font, (e.done() ? "✔ " : "") + "★".repeat(e.stars()), x + 144 - 6 - font.width((e.done() ? "✔ " : "") + "★".repeat(e.stars())),
                    y + 6, e.done() ? 0xFF2E7A2E : 0xFF8A5A10, false);
            if (e.id().equals(active)) g.fill(x - 3, y + 2, x - 1, y + 18, 0xFFE03030);
        }
        int dx = left + 310, dy = top + 30;
        if (selected >= 0 && selected < entries.size()) {
            Entry e = entries.get(selected);
            int tx = left + 10, ty = top + h - 82;
            g.fill(tx, ty, left + w - 10, top + h - 32, 0x30000000);
            g.drawString(font, Component.translatable("mission.mcbesiege." + e.id()), tx + 6, ty + 5, 0xFF7A1E10, false);
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable("mission.mcbesiege." + e.id() + ".desc"), w - 36);
            for (int i = 0; i < Math.min(3, lines.size()); i++) g.drawString(font, lines.get(i), tx + 6, ty + 18 + i * 10, 0xFF2A1E12, false);
        }
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
