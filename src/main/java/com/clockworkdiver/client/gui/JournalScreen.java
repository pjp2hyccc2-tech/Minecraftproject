package com.clockworkdiver.client.gui;

import com.clockworkdiver.client.ClientState;
import com.clockworkdiver.item.KeepsakeItem;
import com.clockworkdiver.story.StoryQuest;
import com.clockworkdiver.story.StoryQuests;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 修理日誌 — the journal (J key): chapters and quests of the story, what happened so far, and the keepsakes the
 * clients left behind.
 */
public class JournalScreen extends Screen {
    private int chapter;
    private int selected;
    private int bx, by, bw, bh;
    private BrassButton endingButton;

    public JournalScreen() {
        super(Component.translatable("gui.clockworkdiver.journal"));
        int current = Math.min(ClientState.storyIndex, StoryQuests.count() - 1);
        selected = current;
        chapter = StoryQuests.get(current).chapter;
    }

    private static boolean finished() {
        return ClientState.storyIndex >= StoryQuests.count();
    }

    private static int state(StoryQuest q) {
        int i = q.number - 1;
        return i < ClientState.storyIndex ? 2 : i == ClientState.storyIndex ? 1 : 0;
    }

    private static int reachedChapter() {
        return StoryQuests.get(Math.min(ClientState.storyIndex, StoryQuests.count() - 1)).chapter;
    }

    @Override
    protected void init() {
        bw = Math.min(width - 30, 540);
        bh = Math.min(height - 24, 310);
        bx = (width - bw) / 2;
        by = (height - bh) / 2;
        endingButton = addRenderableWidget(new BrassButton(bx + 18, by + bh - 30, bw / 2 - 36, 18,
                Component.translatable("gui.clockworkdiver.journal.ending"), b -> {
                    if (minecraft != null) minecraft.setScreen(new EndingScreen(false));
                }));
        endingButton.visible = finished();
        addRenderableWidget(new BrassButton(bx + bw - 88, by + bh - 30, 70, 18, Component.translatable("gui.clockworkdiver.close"), b -> onClose()));
    }

    /** CI tour helper. */
    public void select(int questIndex) {
        selected = Math.max(0, Math.min(StoryQuests.count() - 1, questIndex));
        chapter = StoryQuests.get(selected).chapter;
    }

    private int tabX(int c) {
        return bx + 18 + (c - 1) * ((bw / 2 - 36) / 5);
    }

    private int rowY(int i) {
        return by + 66 + i * 30;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int tw = (bw / 2 - 36) / 5;
        for (int c = 1; c <= StoryQuests.CHAPTERS; c++) {
            int x = tabX(c);
            if (mouseX >= x && mouseX < x + tw - 3 && mouseY >= by + 34 && mouseY < by + 52 && c <= reachedChapter()) {
                chapter = c;
                List<StoryQuest> qs = StoryQuests.chapter(c);
                selected = qs.get(0).number - 1;
                for (StoryQuest q : qs) if (state(q) == 1) selected = q.number - 1;
                click();
                return true;
            }
        }
        List<StoryQuest> qs = StoryQuests.chapter(chapter);
        for (int i = 0; i < qs.size(); i++) {
            int y = rowY(i);
            if (mouseX >= bx + 14 && mouseX < bx + bw / 2 - 12 && mouseY >= y - 4 && mouseY < y + 24 && state(qs.get(i)) > 0) {
                selected = qs.get(i).number - 1;
                click();
                return true;
            }
        }
        return false;
    }

    private void click() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.0f));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int mid = bx + bw / 2;
        Gfx.begin(g);
        // The open book: leather cover, two parchment pages, a brass spine.
        Gfx.rect(bx - 6, by - 6, bx + bw + 6, by + bh + 6, 0xFF3A2414);
        Gfx.rect(bx - 4, by - 4, bx + bw + 4, by + bh + 4, 0xFF5A3A20);
        Gfx.rectV(bx, by, mid - 2, by + bh, 0xFFF2E6C8, 0xFFE4D3AE);
        Gfx.rectV(mid + 2, by, bx + bw, by + bh, 0xFFF2E6C8, 0xFFE4D3AE);
        Gfx.rect(mid - 3, by, mid + 3, by + bh, 0xFF8C6424);
        for (int[] c : new int[][]{{bx - 6, by - 6}, {bx + bw - 6, by - 6}, {bx - 6, by + bh - 6}, {bx + bw - 6, by + bh - 6}}) {
            Gfx.rect(c[0], c[1], c[0] + 12, c[1] + 12, 0xFFD9AE55);
        }
        // Chapter tabs.
        int tw = (bw / 2 - 36) / 5;
        for (int c = 1; c <= StoryQuests.CHAPTERS; c++) {
            int x = tabX(c);
            boolean reached = c <= reachedChapter();
            int fill = c == chapter ? 0xFFD9AE55 : reached ? 0xFFB8925A : 0xFFB8A888;
            Gfx.rect(x, by + 34, x + tw - 3, by + 52, fill);
            Gfx.rect(x, by + 51, x + tw - 3, by + 52, 0xFF6A4A24);
        }
        Gfx.end();

        int ink = 0xFF2E1C0C, soft = 0xFF6A5236;
        g.drawString(font, Component.translatable("gui.clockworkdiver.journal").withStyle(ChatFormatting.BOLD), bx + 18, by + 14, ink, false);
        Component progress = Component.translatable("gui.clockworkdiver.journal.progress", Math.min(ClientState.storyIndex, StoryQuests.count()),
                StoryQuests.count());
        g.drawString(font, progress, mid - 14 - font.width(progress), by + 14, 0xFF8A5A10, false);
        for (int c = 1; c <= StoryQuests.CHAPTERS; c++) {
            String label = Component.translatable("gui.clockworkdiver.journal.tab", c).getString();
            g.drawCenteredString(font, label, tabX(c) + (tw - 3) / 2, by + 39, c <= reachedChapter() ? 0xFF2E1C0C : 0xFF8A7A66);
        }

        // Quests of the chapter.
        List<StoryQuest> qs = StoryQuests.chapter(chapter);
        for (int i = 0; i < qs.size(); i++) {
            StoryQuest q = qs.get(i);
            int y = rowY(i);
            int st = state(q);
            if (q.number - 1 == selected) {
                Gfx.begin(g);
                Gfx.rect(bx + 12, y - 5, mid - 12, y + 24, 0x40C08A2E);
                Gfx.end();
            }
            String mark = st == 2 ? "✔" : st == 1 ? "▶" : "？";
            int color = st == 2 ? 0xFF2E6A2E : st == 1 ? 0xFF9A3A10 : 0xFF8A7A66;
            g.drawString(font, mark, bx + 16, y, color, false);
            Component title = st > 0 ? Component.translatable("gui.clockworkdiver.quest_tag", q.number, Component.translatable(q.titleKey()))
                    : Component.translatable("gui.clockworkdiver.journal.locked", q.number);
            g.drawString(font, title, bx + 28, y, st > 0 ? ink : 0xFF8A7A66, false);
            if (st > 0) {
                g.drawString(font, Component.translatable(q.client.nameKey()), bx + 28, y + 11, soft, false);
                if ((ClientState.keepsakes & (1 << (q.number - 1))) != 0) {
                    g.renderItem(KeepsakeItem.create(q), mid - 32, y + 1);
                }
            }
        }
        // Keepsake shelf.
        int kx = bx + 18, ky = by + bh - 70;
        g.drawString(font, Component.translatable("gui.clockworkdiver.journal.keepsakes", Integer.bitCount(ClientState.keepsakes), StoryQuests.count()),
                kx, ky - 12, soft, false);
        Gfx.begin(g);
        for (int i = 0; i < StoryQuests.count(); i++) {
            int x = kx + (i % 10) * 20, y = ky + (i / 10) * 18;
            Gfx.rect(x, y, x + 17, y + 16, 0x30704A20);
        }
        Gfx.end();
        for (int i = 0; i < StoryQuests.count(); i++) {
            if ((ClientState.keepsakes & (1 << i)) == 0) continue;
            int x = kx + (i % 10) * 20, y = ky + (i / 10) * 18;
            g.pose().pushPose();
            g.pose().translate(x + 1, y, 0.0f);
            g.pose().scale(0.95f, 0.95f, 1.0f);
            g.renderItem(KeepsakeItem.create(StoryQuests.get(i)), 0, 0);
            g.pose().popPose();
        }
        if (finished()) {
            g.pose().pushPose();
            g.pose().translate(mid - 50, by + 22, 0.0f);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-12.0f));
            g.pose().scale(1.6f, 1.6f, 1.0f);
            g.drawString(font, Component.translatable("gui.clockworkdiver.journal.complete"), 0, 0, 0xFFB02020, false);
            g.pose().popPose();
        }

        // Right page: the chapter and the selected quest.
        int rx = mid + 16, rw = bw / 2 - 32;
        int y = by + 14;
        StoryQuest q = StoryQuests.get(selected);
        g.drawString(font, Component.translatable(StoryQuests.chapterKey(chapter)).withStyle(ChatFormatting.BOLD), rx, y, ink, false);
        y += 12;
        g.drawString(font, Component.translatable(StoryQuests.chapterKey(chapter) + ".sub"), rx, y, 0xFF8A5A10, false);
        y += 14;
        for (FormattedCharSequence line : font.split(Component.translatable(StoryQuests.chapterKey(chapter) + ".summary"), rw)) {
            g.drawString(font, line, rx, y, soft, false);
            y += 10;
        }
        y += 6;
        Gfx.begin(g);
        Gfx.rect(rx, y, rx + rw, y + 1, 0xFFB8925A);
        Gfx.end();
        y += 8;
        int st = state(q);
        if (st == 0) {
            g.drawString(font, Component.translatable("gui.clockworkdiver.journal.locked", q.number), rx, y, 0xFF8A7A66, false);
        } else {
            g.drawString(font, Component.translatable("gui.clockworkdiver.quest_tag", q.number, Component.translatable(q.titleKey()))
                    .withStyle(ChatFormatting.BOLD), rx, y, ink, false);
            y += 12;
            g.drawString(font, Component.translatable("gui.clockworkdiver.journal.client", Component.translatable(q.client.nameKey())), rx, y, soft, false);
            y += 10;
            g.drawString(font, Component.translatable("gui.clockworkdiver.journal.clock", Component.translatable(q.key("clock"))), rx, y, soft, false);
            y += 14;
            Component body = st == 2 ? Component.translatable(q.key("summary")) : Component.translatable("gui.clockworkdiver.journal.current");
            for (FormattedCharSequence line : font.split(body, rw)) {
                if (y > by + bh - 58) break;
                g.drawString(font, line, rx, y, st == 2 ? 0xFF3A2A18 : 0xFF9A3A10, false);
                y += 10;
            }
            if ((ClientState.keepsakes & (1 << (q.number - 1))) != 0) {
                int ky2 = by + bh - 52;
                ItemStack stack = KeepsakeItem.create(q);
                g.renderItem(stack, rx, ky2);
                g.drawString(font, Component.translatable(q.key("keepsake")), rx + 20, ky2, ink, false);
                List<FormattedCharSequence> desc = font.split(Component.translatable(q.key("keepsake.desc")), rw - 20);
                if (!desc.isEmpty()) g.drawString(font, desc.get(0), rx + 20, ky2 + 10, soft, false);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
