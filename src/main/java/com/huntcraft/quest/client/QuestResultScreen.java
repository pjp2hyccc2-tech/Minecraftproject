package com.huntcraft.quest.client;

import com.huntcraft.network.HCNetwork;
import com.huntcraft.quest.QuestDef;
import com.huntcraft.quest.Quests;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/** クエスト結果: clear / failure, time, faints, zenny + HRP, hunter rank up and the reward items (sent to the item box). */
@OnlyIn(Dist.CLIENT)
public class QuestResultScreen extends Screen {
    private record Reward(ItemStack stack, String cat) {
    }

    private final CompoundTag t;
    private final boolean success;
    private final List<Reward> rewards = new ArrayList<>();
    private int left, top, w, h;
    private int age;
    private boolean claimed;

    public QuestResultScreen(CompoundTag tag) {
        super(Component.translatable("screen.huntcraft.quest_result"));
        this.t = tag == null ? new CompoundTag() : tag;
        this.success = t.getBoolean("success");
        ListTag items = t.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag c = items.getCompound(i);
            ItemStack st = ItemStack.of(c);
            if (!st.isEmpty()) rewards.add(new Reward(st, c.getString("cat")));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        w = Math.min(340, width - 16);
        h = Math.min(success ? 236 : 150, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 68, top + h - 26, 60, 18).build());
        if (success && t.getInt("pending") > 0 && !claimed) {
            addRenderableWidget(Button.builder(Component.translatable("button.huntcraft.quest.claim"), b -> {
                CompoundTag c = new CompoundTag();
                c.putBoolean("box", false);
                HCNetwork.sendToServer("quest/claim", c);
                claimed = true;
                b.active = false;
            }).bounds(left + 8, top + h - 26, 110, 18).build());
        }
    }

    @Override
    public void tick() {
        age++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        QuestUi.frame(g, left, top, w, h, QuestUi.BG);
        QuestDef q = Quests.get(t.getString("quest"));
        Component head = Component.translatable(success ? "result.huntcraft.clear" : "result.huntcraft.failed");
        g.pose().pushPose();
        float sc = 1.6F + (age < 8 ? (8 - age) * 0.08F : 0.0F);
        g.pose().translate(left + w / 2.0F, top + 10, 0);
        g.pose().scale(sc, sc, 1.0F);
        g.drawCenteredString(font, head, 0, 0, success ? 0xFFFFD84A : QuestUi.RED);
        g.pose().popPose();
        int y = top + 30;
        if (q != null) {
            g.drawCenteredString(font, Component.literal(QuestUi.stars(q.stars) + " ").append(q.title()), left + w / 2, y, QuestUi.TEXT);
            y += 13;
        }
        g.fill(left + 10, y, left + w - 10, y + 1, QuestUi.GOLD_DARK);
        y += 6;
        if (!success) {
            String reason = t.getString("reason");
            if (!reason.isEmpty()) {
                g.drawCenteredString(font, Component.translatable("message.huntcraft.quest.fail_reason." + reason), left + w / 2, y, QuestUi.TEXT_DIM);
                y += 14;
            }
            g.drawCenteredString(font, Component.translatable("result.huntcraft.no_reward"), left + w / 2, y, QuestUi.TEXT_DIM);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        int col1 = left + 14, col2 = left + w / 2 + 6;
        g.drawString(font, Component.translatable("result.huntcraft.time", QuestUi.time((int) t.getLong("time"))), col1, y, QuestUi.TEXT, false);
        g.drawString(font, Component.translatable("result.huntcraft.faints", t.getInt("faints")), col2, y, t.getInt("faints") > 0 ? QuestUi.RED : QuestUi.TEXT, false);
        y += 12;
        g.drawString(font, Component.translatable("result.huntcraft.zenny", t.getInt("zenny")), col1, y, 0xFFFFE08A, false);
        g.drawString(font, Component.translatable("result.huntcraft.hrp", t.getInt("hrp")), col2, y, 0xFFFFE08A, false);
        y += 12;
        if (t.getInt("newHr") > t.getInt("oldHr")) {
            boolean blink = age / 10 % 2 == 0;
            g.drawCenteredString(font, Component.translatable("result.huntcraft.hr_up", t.getInt("newHr")), left + w / 2, y, blink ? 0xFFFFD84A : 0xFFFFF0B0);
            y += 12;
        }
        if (t.getInt("parts") > 0) {
            g.drawString(font, Component.translatable("result.huntcraft.parts", t.getInt("parts")), col1, y, QuestUi.TEXT_DIM, false);
            y += 12;
        }
        g.fill(left + 10, y, left + w - 10, y + 1, QuestUi.GOLD_DARK);
        y += 5;
        g.drawString(font, Component.translatable("result.huntcraft.rewards"), col1, y, QuestUi.GOLD, false);
        y += 11;
        int perRow = Math.max(1, (w - 28) / 20);
        int shown = Math.min(rewards.size(), Math.min(age / 2, rewards.size()));
        ItemStack hover = ItemStack.EMPTY;
        for (int i = 0; i < shown; i++) {
            Reward r = rewards.get(i);
            int ix = col1 + (i % perRow) * 20, iy = y + (i / perRow) * 20;
            int bg = switch (r.cat()) {
                case "break" -> 0x70A05030;
                case "capture" -> 0x703070A0;
                case "sub" -> 0x50606060;
                case "charm" -> 0x80C8A040;
                default -> 0x40FFFFFF;
            };
            g.fill(ix - 1, iy - 1, ix + 17, iy + 17, bg);
            g.renderItem(r.stack(), ix, iy);
            g.renderItemDecorations(font, r.stack(), ix, iy);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) hover = r.stack();
        }
        int note = top + h - 40;
        Component boxNote = Component.translatable("result.huntcraft.to_box", t.getInt("stored"));
        g.drawString(font, boxNote, col1, note, QuestUi.TEXT_DIM, false);
        super.render(g, mouseX, mouseY, partialTick);
        if (!hover.isEmpty()) g.renderTooltip(font, hover, mouseX, mouseY);
    }
}
