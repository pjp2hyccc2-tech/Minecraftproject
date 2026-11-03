package com.reisatsu.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reisatsu.Reisatsu;
import com.reisatsu.client.ClientPacketHandler;
import com.reisatsu.client.ClientState;
import com.reisatsu.client.PhotoView;
import com.reisatsu.client.Tex;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.GameData;
import com.reisatsu.registry.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 霊撮帳 (docs/DESIGN.md §3.2, key J): six silhouette slots that fill with each spirit's photo and red name, every
 * shot of the night (empty and failed ones too), the notes found, the medium's rules. Click a photo to see it large.
 */
public class AlbumScreen extends Screen {
    private static final ResourceLocation ALBUM = Reisatsu.id("textures/gui/album.png");
    private static final int TAB_SHOTS = 0, TAB_NOTES = 1, TAB_RULES = 2;
    private int tab;
    private int page;
    private int bx, by, bw, bh;

    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();

    public AlbumScreen() {
        super(Component.translatable("gui.reisatsu.album"));
    }

    public AlbumScreen tab(int t) {
        tab = t;
        return this;
    }

    @Override
    protected void init() {
        bw = Math.min(width - 20, (int) ((height - 20) * 1.6f));
        bh = (int) (bw / 1.6f);
        bx = (width - bw) / 2;
        by = (height - bh) / 2;
        ClientPacketHandler.ui(ModSounds.ITEM_PAPER.get(), 0.6f, 1.0f);
    }

    @Nullable
    private static GameData.Photo bestOf(SpiritKind k) {
        GameData.Photo best = null;
        for (GameData.Photo p : ClientState.PHOTOS) {
            if (p.result() == GameData.RESULT_CAPTURED && p.spirit() == k.ordinal() && (best == null || p.stars() > best.stars())) best = p;
        }
        return best;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        hits.clear();
        if (Tex.has(ALBUM)) {
            RenderSystem.enableBlend();
            g.blit(ALBUM, bx, by, bw, bh, 0, 0, 512, 320, 512, 320);
            RenderSystem.disableBlend();
        } else {
            g.fill(bx - 6, by - 6, bx + bw + 6, by + bh + 6, 0xFF2A1A12);
            g.fill(bx, by, bx + bw / 2 - 2, by + bh, 0xFFE6DCC8);
            g.fill(bx + bw / 2 + 2, by, bx + bw, by + bh, 0xFFE6DCC8);
            g.fill(bx + bw / 2 - 2, by, bx + bw / 2 + 2, by + bh, 0xFFB8AA90);
        }
        int ink = 0xFF2A2018;
        // the notebook texture has its own pages (left 14..252, right 262..498 of 512) and the 霊撮帳 heading
        boolean tex = Tex.has(ALBUM);
        int pad = bw / 22;
        int lx = tex ? bx + bw * 26 / 512 : bx + pad;
        int rx = tex ? bx + bw * 274 / 512 : bx + bw / 2 + pad;
        int top = tex ? by + bh * 20 / 320 : by + pad;
        int pageW = tex ? bw * 216 / 512 : bw / 2 - pad * 2;
        if (!tex) g.drawString(font, I18n.get("gui.reisatsu.album"), lx, top, ink, false);
        // ---- the six
        int cols = 3, rows = 2;
        int gridTop = tex ? by + bh * 62 / 320 : top + 14;
        int gridBottom = by + bh - (tex ? bh * 16 / 320 : pad);
        int cellW = pageW / cols, cellH = (gridBottom - gridTop) / rows;
        int photoH = Math.min(cellH - 14, cellW * 160 / 128 - 4);
        for (int i = 0; i < SpiritKind.ALL.length; i++) {
            SpiritKind k = SpiritKind.ALL[i];
            int cx = lx + (i % cols) * cellW, cy = gridTop + (i / cols) * cellH;
            int pw = photoH * 128 / 160;
            int px = cx + (cellW - pw) / 2;
            GameData.Photo p = bestOf(k);
            boolean got = ClientState.isCaptured(k);
            if (got && p != null) {
                PhotoView.drawPhoto(g, font, px, cy, photoH, p.id(), GameData.RESULT_CAPTURED, k, p.stars(), 1.0f, 1.0f);
                final GameData.Photo fp = p;
                hits.add(new Hit(px, cy, px + pw, cy + photoH, () -> minecraft.setScreen(new PhotoScreen(fp.id(), fp.result(), k, fp.stars(), this))));
            } else {
                g.fill(px, cy, px + pw, cy + photoH, 0x30201810);
                g.fill(px + pw / 2 - pw / 6, cy + photoH / 5, px + pw / 2 + pw / 6, cy + photoH / 5 + pw / 3, 0x50201810);
                g.fill(px + pw / 2 - pw / 4, cy + photoH / 5 + pw / 3, px + pw / 2 + pw / 4, cy + photoH * 4 / 5, 0x50201810);
                g.drawCenteredString(font, "？", px + pw / 2, cy + photoH / 2 - 4, 0x80FFFFFF);
            }
            String label = got ? I18n.get(k.nameKey()) : I18n.get(k.roleKey());
            int lc = got ? 0xFF9A1414 : 0xFF6A5A48;
            String fitted = font.plainSubstrByWidth(label, cellW - 2);
            g.drawString(font, fitted, cx + (cellW - font.width(fitted)) / 2, cy + photoH + 3, lc, false);
            if ((ClientState.submitted & k.bit()) != 0) g.drawString(font, "✓", px + pw - 6, cy + 2, 0xFF2E6A2E, false);
        }
        // ---- the right page: tabs
        String[] tabs = {I18n.get("gui.reisatsu.tab_shots"), I18n.get("gui.reisatsu.tab_notes"), I18n.get("gui.reisatsu.tab_rules")};
        int tx = rx;
        for (int i = 0; i < tabs.length; i++) {
            int tw = font.width(tabs[i]) + 10;
            final int ti = i;
            g.fill(tx, top - 2, tx + tw, top + 10, i == tab ? 0x40201810 : 0x15201810);
            g.drawString(font, tabs[i], tx + 5, top, i == tab ? ink : 0xFF7A6A58, false);
            hits.add(new Hit(tx, top - 2, tx + tw, top + 10, () -> {
                tab = ti;
                page = 0;
                ClientPacketHandler.ui(ModSounds.ITEM_PAPER.get(), 0.4f, 1.2f);
            }));
            tx += tw + 4;
        }
        int y0 = top + 16;
        switch (tab) {
            case TAB_SHOTS -> shots(g, rx, y0, pageW, by + bh - pad - y0, ink);
            case TAB_NOTES -> notes(g, rx, y0, pageW, ink);
            default -> rules(g, rx, y0, pageW, ink);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private void shots(GuiGraphics g, int x, int y, int w, int h, int ink) {
        List<GameData.Photo> all = ClientState.PHOTOS;
        if (all.isEmpty()) {
            g.drawString(font, I18n.get("gui.reisatsu.no_shots"), x, y, 0xFF7A6A58, false);
            return;
        }
        int cols = 3, rows = 2, per = cols * rows;
        int pages = (all.size() + per - 1) / per;
        page = Math.max(0, Math.min(page, pages - 1));
        int cellW = w / cols, cellH = (h - 14) / rows;
        int ph = Math.min(cellH - 6, cellW * 160 / 128 - 4);
        for (int i = 0; i < per; i++) {
            int idx = all.size() - 1 - (page * per + i);
            if (idx < 0) break;
            GameData.Photo p = all.get(idx);
            int cx = x + (i % cols) * cellW, cy = y + (i / cols) * cellH;
            int pw = ph * 128 / 160;
            int px = cx + (cellW - pw) / 2;
            SpiritKind k = SpiritKind.byIndex(p.spirit());
            PhotoView.drawPhoto(g, font, px, cy, ph, p.id(), p.result(), k, p.stars(), 1.0f, 1.0f);
            hits.add(new Hit(px, cy, px + pw, cy + ph, () -> minecraft.setScreen(new PhotoScreen(p.id(), p.result(), k, p.stars(), this))));
        }
        String pg = (page + 1) + " / " + pages;
        int py = y + h - 10;
        g.drawCenteredString(font, pg, x + w / 2, py, 0xFF6A5A48);
        if (page > 0) {
            g.drawString(font, "‹", x + w / 2 - 30, py, ink, false);
            hits.add(new Hit(x + w / 2 - 34, py - 2, x + w / 2 - 22, py + 10, () -> page--));
        }
        if (page < pages - 1) {
            g.drawString(font, "›", x + w / 2 + 26, py, ink, false);
            hits.add(new Hit(x + w / 2 + 22, py - 2, x + w / 2 + 34, py + 10, () -> page++));
        }
    }

    private void notes(GuiGraphics g, int x, int y, int w, int ink) {
        if (ClientState.NOTES.isEmpty()) {
            g.drawString(font, I18n.get("gui.reisatsu.no_notes"), x, y, 0xFF7A6A58, false);
            return;
        }
        for (String id : ClientState.NOTES) {
            String t = "・" + I18n.get("note.reisatsu." + id + ".title");
            g.drawString(font, font.plainSubstrByWidth(t, w), x, y, ink, false);
            final String nid = id;
            hits.add(new Hit(x, y - 1, x + w, y + 10, () -> minecraft.setScreen(new NoteScreen(nid, "", this))));
            y += 13;
        }
    }

    private void rules(GuiGraphics g, int x, int y, int w, int ink) {
        g.drawString(font, I18n.get("gui.reisatsu.rules_title"), x, y, 0xFF8A1A14, false);
        y += 14;
        for (int i = 1; i <= 5; i++) {
            for (FormattedCharSequence l : font.split(Component.translatable("rule.reisatsu." + i), w)) {
                g.drawString(font, l, x, y, ink, false);
                y += 10;
            }
            y += 4;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Hit h : new ArrayList<>(hits)) {
            if (mouseX >= h.x0() && mouseX < h.x1() && mouseY >= h.y0() && mouseY < h.y1()) {
                h.action().run();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == TAB_SHOTS) page = Math.max(0, page - (int) Math.signum(delta));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (com.reisatsu.registry.ModKeys.ALBUM.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
