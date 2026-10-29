package com.huntcraft.quest.client;

import com.huntcraft.data.HunterData;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.quest.QuestDef;
import com.huntcraft.quest.QuestMap;
import com.huntcraft.quest.Quests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * クエストボード: quests by rank and star level, details, accept (受注) / cancel (破棄) / depart (出発) and joining a
 * nearby hunter's quest. Opened through HCScreens.QUEST_BOARD with or without data (data is requested if missing).
 */
@OnlyIn(Dist.CLIENT)
public class QuestBoardScreen extends Screen {
    private static final int ROW = 13;
    @Nullable
    private CompoundTag data;
    private boolean requested;
    private boolean highRank;
    private int starFilter;
    private final List<QuestDef> rows = new ArrayList<>();
    private int scroll;
    @Nullable
    private QuestDef selected;
    private int left, top, w, h;
    private int listX, listY, listW, listH;
    private int lobX, lobY, lobW, lobH;
    private int detX, detY, detW, detH;
    private Button acceptBtn, cancelBtn, departBtn;

    public QuestBoardScreen(@Nullable CompoundTag tag) {
        super(Component.translatable("screen.huntcraft.quest_board"));
        if (tag != null && tag.getBoolean("data")) {
            data = tag;
            highRank = Quests.highRankUnlocked(done());
        }
    }

    /** "quest/board_data" handler. */
    public static void onData(CompoundTag tag) {
        if (Minecraft.getInstance().screen instanceof QuestBoardScreen s) s.setData(tag);
    }

    public void setData(CompoundTag tag) {
        boolean first = data == null;
        data = tag;
        if (first) highRank = Quests.highRankUnlocked(done());
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ data helpers
    private Set<String> done() {
        Set<String> s = new HashSet<>();
        if (data != null) {
            ListTag l = data.getList("done", Tag.TAG_STRING);
            for (int i = 0; i < l.size(); i++) s.add(l.getString(i));
        } else if (Minecraft.getInstance().player != null) {
            s.addAll(HunterData.get(Minecraft.getInstance().player).completedQuests);
        }
        return s;
    }

    private int hr() {
        if (data != null) return data.getInt("hr");
        return Minecraft.getInstance().player != null ? HunterData.get(Minecraft.getInstance().player).hunterRank : 1;
    }

    private int zenny() {
        if (data != null) return data.getInt("zenny");
        return Minecraft.getInstance().player != null ? HunterData.get(Minecraft.getInstance().player).zenny : 0;
    }

    @Nullable
    private CompoundTag lobby() {
        return data != null && data.contains("lobby", Tag.TAG_COMPOUND) ? data.getCompound("lobby") : null;
    }

    private boolean inQuest() {
        return data != null && data.getBoolean("inQuest");
    }

    // ------------------------------------------------------------------ layout / widgets
    @Override
    protected void init() {
        w = Math.min(472, width - 12);
        h = Math.min(280, height - 12);
        left = (width - w) / 2;
        top = (height - h) / 2;
        listX = left + 8;
        listY = top + 44;
        listW = Math.min(184, w / 2 - 20);
        lobH = 58;
        listH = h - 44 - lobH - 12;
        lobX = listX;
        lobY = listY + listH + 4;
        lobW = listW;
        detX = listX + listW + 8;
        detY = listY;
        detW = left + w - 8 - detX;
        detH = h - 44 - 30;
        if (data == null && !requested) {
            requested = true;
            HCNetwork.sendToServer("quest/board_request", new CompoundTag());
        }
        boolean hrUnlocked = Quests.highRankUnlocked(done());
        Button low = Button.builder(Component.translatable("rank.huntcraft.low"), b -> setRank(false)).bounds(left + 8, top + 24, 46, 16).build();
        low.active = highRank;
        addRenderableWidget(low);
        Button high = Button.builder(Component.translatable("rank.huntcraft.high"), b -> setRank(true)).bounds(left + 56, top + 24, 46, 16).build();
        high.active = !highRank && hrUnlocked;
        addRenderableWidget(high);
        int[] stars = highRank ? new int[]{0, 4, 5, 6} : new int[]{0, 1, 2, 3};
        int sx = left + 110;
        for (int s : stars) {
            Component label = s == 0 ? Component.translatable("board.huntcraft.all") : Component.literal("★" + s);
            Button b = Button.builder(label, btn -> {
                starFilter = s;
                scroll = 0;
                rebuildWidgets();
            }).bounds(sx, top + 24, 30, 16).build();
            b.active = starFilter != s;
            addRenderableWidget(b);
            sx += 32;
        }
        int by = top + h - 24;
        acceptBtn = addRenderableWidget(Button.builder(Component.translatable("button.huntcraft.quest.accept"), b -> accept()).bounds(detX, by, 64, 18).build());
        cancelBtn = addRenderableWidget(Button.builder(Component.translatable("button.huntcraft.quest.cancel"), b -> cancel()).bounds(detX + 68, by, 64, 18).build());
        departBtn = addRenderableWidget(Button.builder(Component.translatable("button.huntcraft.quest.depart"), b -> depart()).bounds(detX + 136, by, 64, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 8 - 48, by, 48, 18).build());
        if (data != null && lobby() == null && !inQuest()) {
            ListTag j = data.getList("joinable", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(2, j.size()); i++) {
                CompoundTag e = j.getCompound(i);
                if (!e.hasUUID("leader")) continue;
                UUID leader = e.getUUID("leader");
                addRenderableWidget(Button.builder(Component.translatable("button.huntcraft.quest.join"), b -> join(leader))
                        .bounds(lobX + lobW - 40, lobY + 15 + i * 19, 36, 15).build());
            }
        }
        buildRows();
        updateButtons();
    }

    private void setRank(boolean high) {
        highRank = high;
        starFilter = 0;
        scroll = 0;
        selected = null;
        rebuildWidgets();
    }

    private void buildRows() {
        rows.clear();
        for (QuestDef q : Quests.all()) {
            if (q.isHighRank() != highRank) continue;
            if (starFilter != 0 && q.stars != starFilter) continue;
            rows.add(q);
        }
        CompoundTag l = lobby();
        if (l != null) {
            QuestDef lq = Quests.get(l.getString("q"));
            if (lq != null) selected = lq;
        }
        if (selected == null || (!rows.contains(selected) && l == null)) {
            selected = null;
            Set<String> done = done();
            for (QuestDef q : rows) {
                if (Quests.status(q, hr(), done) == Quests.Status.AVAILABLE && !done.contains(q.id)) {
                    selected = q;
                    break;
                }
            }
            if (selected == null && !rows.isEmpty()) selected = rows.get(0);
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
    }

    private int visibleRows() {
        return Math.max(1, (listH - 4) / ROW);
    }

    private void updateButtons() {
        CompoundTag l = lobby();
        boolean loaded = data != null;
        boolean quest = inQuest();
        boolean avail = selected != null && Quests.status(selected, hr(), done()) == Quests.Status.AVAILABLE;
        acceptBtn.active = loaded && !quest && l == null && avail;
        cancelBtn.active = loaded && l != null;
        boolean leader = l != null && l.getBoolean("isLeader");
        cancelBtn.setMessage(Component.translatable(l != null && !leader ? "button.huntcraft.quest.leave" : "button.huntcraft.quest.cancel"));
        departBtn.active = loaded && l != null && leader && !quest;
    }

    // ------------------------------------------------------------------ actions
    private void accept() {
        if (selected == null) return;
        CompoundTag t = new CompoundTag();
        t.putString("q", selected.id);
        HCNetwork.sendToServer("quest/accept", t);
    }

    private void cancel() {
        HCNetwork.sendToServer("quest/cancel", new CompoundTag());
    }

    private void depart() {
        HCNetwork.sendToServer("quest/depart", new CompoundTag());
        onClose();
    }

    private void join(UUID leader) {
        CompoundTag t = new CompoundTag();
        t.putUUID("leader", leader);
        HCNetwork.sendToServer("quest/join", t);
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= listX && mx < listX + listW && my >= listY + 2 && my < listY + listH - 2) {
            int i = scroll + (int) ((my - listY - 2) / ROW);
            if (i >= 0 && i < rows.size()) {
                if (lobby() == null) selected = rows.get(i);
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows()), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // ------------------------------------------------------------------ rendering
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        QuestUi.frame(g, left, top, w, h, QuestUi.BG);
        g.drawString(font, title, left + 10, top + 8, QuestUi.GOLD, false);
        Component info = Component.translatable("board.huntcraft.hr_zenny", hr(), zenny());
        g.drawString(font, info, left + w - 10 - font.width(info), top + 8, QuestUi.TEXT, false);
        Set<String> done = done();
        int hr = hr();
        // list
        QuestUi.panel(g, listX, listY, listW, listH);
        if (data == null) {
            g.drawString(font, Component.translatable("board.huntcraft.loading"), listX + 6, listY + 6, QuestUi.TEXT_DIM, false);
        } else if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("board.huntcraft.empty"), listX + 6, listY + 6, QuestUi.TEXT_DIM, false);
        }
        int vis = visibleRows();
        for (int k = 0; k < vis && scroll + k < rows.size(); k++) {
            QuestDef q = rows.get(scroll + k);
            int ry = listY + 2 + k * ROW;
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + ROW;
            if (q == selected) g.fill(listX + 1, ry, listX + listW - 1, ry + ROW, QuestUi.HIGHLIGHT);
            else if (hover) g.fill(listX + 1, ry, listX + listW - 1, ry + ROW, 0x40FFFFFF);
            Quests.Status st = Quests.status(q, hr, done);
            int tx = listX + 4;
            if (q.urgent) {
                g.fill(tx, ry + 2, tx + 3, ry + ROW - 2, QuestUi.RED);
                tx += 5;
            }
            g.drawString(font, "★" + q.stars, tx, ry + 3, 0xFFFFD84A, false);
            tx += font.width("★" + q.stars) + 3;
            Component name;
            int col;
            if (st == Quests.Status.LOCKED) {
                name = Component.translatable("board.huntcraft.locked");
                col = 0xFF6A6050;
            } else {
                name = q.title();
                col = st == Quests.Status.NEED_HR ? 0xFF9A8C74 : QuestUi.TEXT;
            }
            Component type = Component.translatable(q.type.key());
            int typeW = font.width(type);
            int maxW = listX + listW - 8 - typeW - tx - (done.contains(q.id) ? 8 : 0);
            String s = font.plainSubstrByWidth(name.getString(), Math.max(10, maxW));
            g.drawString(font, s, tx, ry + 3, col, false);
            if (done.contains(q.id)) g.drawString(font, "✔", listX + listW - 12 - typeW - 6, ry + 3, QuestUi.GREEN, false);
            g.drawString(font, type, listX + listW - 4 - typeW, ry + 3, st == Quests.Status.LOCKED ? 0xFF6A6050 : QuestUi.typeColor(q.type), false);
        }
        if (rows.size() > vis) {
            int barH = Math.max(10, listH * vis / rows.size());
            int barY = listY + (listH - barH) * scroll / Math.max(1, rows.size() - vis);
            g.fill(listX + listW - 3, barY, listX + listW - 1, barY + barH, QuestUi.GOLD_DARK);
        }
        renderLobby(g);
        renderDetails(g, mouseX, mouseY, done, hr);
        super.render(g, mouseX, mouseY, partialTick);
        renderRewardTooltips(g, mouseX, mouseY);
    }

    private void renderLobby(GuiGraphics g) {
        QuestUi.panel(g, lobX, lobY, lobW, lobH);
        CompoundTag l = lobby();
        int y = lobY + 4;
        if (inQuest()) {
            g.drawString(font, Component.translatable("board.huntcraft.in_quest"), lobX + 5, y, QuestUi.GOLD, false);
            return;
        }
        if (l != null) {
            QuestDef q = Quests.get(l.getString("q"));
            g.drawString(font, Component.translatable("board.huntcraft.lobby_title"), lobX + 5, y, QuestUi.GOLD, false);
            y += 11;
            if (q != null) {
                String t = font.plainSubstrByWidth(q.title().getString(), lobW - 10);
                g.drawString(font, t, lobX + 5, y, QuestUi.TEXT, false);
                y += 11;
            }
            ListTag mem = l.getList("members", Tag.TAG_STRING);
            List<String> names = new ArrayList<>();
            for (int i = 0; i < mem.size(); i++) names.add(mem.getString(i));
            Component m = Component.translatable("board.huntcraft.members", String.join(", ", names));
            QuestUi.wrap(g, font, m, lobX + 5, y, lobW - 10, QuestUi.TEXT_DIM, 2);
            return;
        }
        g.drawString(font, Component.translatable("board.huntcraft.joinable"), lobX + 5, y, QuestUi.GOLD, false);
        ListTag j = data == null ? new ListTag() : data.getList("joinable", Tag.TAG_COMPOUND);
        if (j.isEmpty()) {
            QuestUi.wrap(g, font, Component.translatable("board.huntcraft.no_joinable"), lobX + 5, y + 13, lobW - 10, QuestUi.TEXT_DIM, 3);
            return;
        }
        for (int i = 0; i < Math.min(2, j.size()); i++) {
            CompoundTag e = j.getCompound(i);
            QuestDef q = Quests.get(e.getString("q"));
            String line = e.getString("name") + " (" + e.getInt("n") + "/4) " + (q != null ? q.title().getString() : "?");
            g.drawString(font, font.plainSubstrByWidth(line, lobW - 50), lobX + 5, lobY + 19 + i * 19, QuestUi.TEXT, false);
        }
    }

    private void renderDetails(GuiGraphics g, int mouseX, int mouseY, Set<String> done, int hr) {
        QuestUi.panel(g, detX, detY, detW, detH);
        QuestDef q = selected;
        if (q == null) return;
        int x = detX + 6, y = detY + 5, tw = detW - 12;
        Quests.Status st = Quests.status(q, hr, done);
        if (st == Quests.Status.LOCKED) {
            g.drawString(font, "★" + q.stars + " ", x, y, 0xFFFFD84A, false);
            g.drawString(font, Component.translatable("board.huntcraft.locked"), x + font.width("★" + q.stars + " "), y, 0xFF9A8C74, false);
            y += 14;
            Component hint = q.isHighRank() && !Quests.highRankUnlocked(done) ? Component.translatable("board.huntcraft.locked_hr")
                    : Component.translatable("board.huntcraft.locked_hint", q.urgent ? q.stars : Math.max(1, q.stars - 1));
            QuestUi.wrap(g, font, hint, x, y, tw, QuestUi.TEXT_DIM, 4);
            return;
        }
        // title line
        MutableComponent head = Component.literal("");
        if (q.urgent) head.append(Component.translatable("board.huntcraft.urgent").withStyle(s -> s.withColor(0xFF5A4A)));
        head.append(Component.literal(QuestUi.stars(q.stars) + " ").withStyle(s -> s.withColor(0xFFD84A)));
        head.append(q.title().copy().withStyle(s -> s.withColor(0xF2E8D2)));
        y = QuestUi.wrap(g, font, head, x, y, tw, QuestUi.TEXT, 2) + 1;
        g.drawString(font, Component.translatable("board.huntcraft.client", Component.translatable(q.clientKey())), x, y, QuestUi.TEXT_DIM, false);
        y += 11;
        g.fill(x, y, x + tw, y + 1, QuestUi.GOLD_DARK);
        y += 4;
        // objective
        g.drawString(font, Component.translatable("board.huntcraft.objective"), x, y, QuestUi.GOLD, false);
        y += 10;
        for (QuestDef.Target t : q.targets) {
            y = QuestUi.wrap(g, font, Component.literal(" ").append(q.objective(t)), x, y, tw, QuestUi.TEXT, 2);
        }
        y += 2;
        g.drawString(font, Component.translatable("board.huntcraft.map", q.map.displayName()), x, y, QuestUi.TEXT, false);
        Component time = Component.translatable("board.huntcraft.time", q.timeLimitMinutes);
        g.drawString(font, time, x + tw - font.width(time), y, QuestUi.TEXT, false);
        y += 10;
        g.drawString(font, Component.translatable("board.huntcraft.reward", q.zenny, q.hrp), x, y, 0xFFFFE08A, false);
        Component fee = Component.translatable("board.huntcraft.fee", q.contractFee());
        g.drawString(font, fee, x + tw - font.width(fee), y, QuestUi.TEXT_DIM, false);
        y += 10;
        Component req = st == Quests.Status.NEED_HR ? Component.translatable("board.huntcraft.need_hr", q.hrReq)
                : Component.translatable("board.huntcraft.hrreq", q.hrReq);
        g.drawString(font, req, x, y, st == Quests.Status.NEED_HR ? QuestUi.RED : QuestUi.TEXT_DIM, false);
        if (done.contains(q.id)) {
            Component c = Component.translatable("board.huntcraft.cleared");
            g.drawString(font, c, x + tw - font.width(c), y, QuestUi.GREEN, false);
        }
        y += 11;
        if (q.map.climate != QuestMap.Climate.TEMPERATE) {
            Component cl = Component.translatable(q.map.climate == QuestMap.Climate.COLD ? "board.huntcraft.climate.cold" : "board.huntcraft.climate.hot");
            g.drawString(font, cl, x, y, q.map.climate == QuestMap.Climate.COLD ? 0xFF9FD8FF : 0xFFFF9A5A, false);
            y += 10;
        }
        g.fill(x, y, x + tw, y + 1, QuestUi.GOLD_DARK);
        y += 4;
        int rewardsY = detY + detH - 24;
        int lines = Math.max(1, (rewardsY - 12 - y) / (font.lineHeight + 1));
        QuestUi.wrap(g, font, Component.translatable(q.descKey()), x, y, tw, 0xFFD8CCB0, lines);
        // sub rewards
        g.drawString(font, Component.translatable("board.huntcraft.subrewards"), x, rewardsY + 4, QuestUi.GOLD, false);
        int ix = x + font.width(Component.translatable("board.huntcraft.subrewards")) + 6;
        for (QuestDef.Reward r : q.rewards) {
            ItemStack st2 = new ItemStack(r.item().get(), r.count());
            g.renderItem(st2, ix, rewardsY);
            g.renderItemDecorations(font, st2, ix, rewardsY);
            ix += 19;
        }
    }

    private void renderRewardTooltips(GuiGraphics g, int mouseX, int mouseY) {
        QuestDef q = selected;
        if (q == null || Quests.status(q, hr(), done()) == Quests.Status.LOCKED) return;
        int rewardsY = detY + detH - 24;
        int ix = detX + 6 + font.width(Component.translatable("board.huntcraft.subrewards")) + 6;
        for (QuestDef.Reward r : q.rewards) {
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= rewardsY && mouseY < rewardsY + 16) {
                g.renderTooltip(font, new ItemStack(r.item().get(), r.count()), mouseX, mouseY);
            }
            ix += 19;
        }
    }
}
