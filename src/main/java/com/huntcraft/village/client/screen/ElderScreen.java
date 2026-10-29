package com.huntcraft.village.client.screen;

import com.huntcraft.core.Element;
import com.huntcraft.core.ShopRegistry;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.village.companion.Expeditions;
import com.huntcraft.village.companion.MofuData;
import com.huntcraft.village.companion.MofuGear;
import com.huntcraft.village.meal.Ingredients;
import com.huntcraft.village.npc.NpcRole;
import com.huntcraft.village.npc.VillageNpc;
import com.huntcraft.village.story.ElderStory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Village chief (村長): the village chronicle (story chapters unlocked by progress), hunter rank guidance (what to do
 * next and what the next rank unlocks) and one-time milestone rewards.
 */
@OnlyIn(Dist.CLIENT)
public class ElderScreen extends VillageScreen {
    private int tab; // 0 story, 1 guidance, 2 rewards
    private int chapter;
    private int textScroll;
    @Nullable
    private final VillageNpc portrait;
    @Nullable
    private ItemStack hoverStack;

    public ElderScreen(@Nullable CompoundTag tag) {
        super(Component.translatable("screen.huntcraft.elder.title"), 356, 226, tag);
        this.portrait = NpcPortrait.npc(NpcRole.ELDER);
        Player p = player();
        if (p != null && ElderStory.unclaimed(p) > 0) tab = 2;
    }

    @Override
    protected void init() {
        super.init();
        com.huntcraft.network.HCNetwork.sendToServer("village/box_request", new CompoundTag());
    }

    @Override
    public void tick() {
        super.tick();
        NpcPortrait.tick(portrait);
    }

    @Override
    protected void drawContent(GuiGraphics g, int mx, int my, float pt) {
        hoverStack = null;
        titlePlate(g, this.title, I_SCROLL);
        walletBar(g);
        Player p = player();
        int tx = left + 100, ty = top + 24;
        String[] tabs = {"screen.huntcraft.elder.tab_story", "screen.huntcraft.elder.tab_guide", "screen.huntcraft.elder.tab_rewards"};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            Component label = Component.translatable(tabs[i]);
            if (i == 2 && p != null && ElderStory.unclaimed(p) > 0) label = label.copy().append(Component.literal(" !").withStyle(ChatFormatting.GOLD));
            tab(g, tx + i * 82, ty, 80, label, tab == i, () -> {
                tab = idx;
                textScroll = 0;
                pageSound();
            });
        }
        // portrait column
        int px = left + 10, py = top + 24, pw = 84, ph = panelH - 34;
        inset(g, px, py, pw, ph);
        preview(g, px + pw / 2, py + ph - 20, 44, portrait, mx, my);
        int x = left + 100, y = top + 46, w = panelW - 110, h = panelH - 56;
        paper(g, x, y, w, h);
        HunterData d = data();
        switch (tab) {
            case 0 -> drawStory(g, d, x, y, w, h, mx, my);
            case 1 -> drawGuide(g, d, x, y, w, h);
            default -> drawRewards(g, d, x, y, w, h, mx, my);
        }
    }

    // ------------------------------------------------------------------ chronicle
    private void drawStory(GuiGraphics g, HunterData d, int x, int y, int w, int h, int mx, int my) {
        List<ElderStory.Chapter> chs = ElderStory.CHAPTERS;
        int lw = 82;
        for (int i = 0; i < chs.size(); i++) {
            ElderStory.Chapter c = chs.get(i);
            boolean open = c.unlocked().test(d);
            int ry = y + 6 + i * 20;
            rowHighlight(g, x + 4, ry, lw, 18, chapter == i, in(mx, my, x + 4, ry, lw, 18));
            icon(g, open ? I_SCROLL : I_LOCK, x + 5, ry + 1);
            textClip(g, open ? c.title() : Component.translatable("screen.huntcraft.elder.locked"), x + 22, ry + 5, lw - 20, open ? INK : 0xFFAA9A80, false);
            final int idx = i;
            region(x + 4, ry, lw, 18, () -> {
                chapter = idx;
                textScroll = 0;
            });
        }
        ElderStory.Chapter c = chs.get(Math.max(0, Math.min(chs.size() - 1, chapter)));
        int tx = x + lw + 10, tw = w - lw - 16;
        g.fill(tx - 4, y + 6, tx - 3, y + h - 6, 0x40402A10);
        if (!c.unlocked().test(d)) {
            text(g, Component.translatable("screen.huntcraft.elder.locked").withStyle(ChatFormatting.BOLD), tx, y + 8, INK);
            wrap(g, c.hint(), tx, y + 22, tw, INK_SOFT, 8);
            return;
        }
        text(g, c.title().copy().withStyle(ChatFormatting.BOLD), tx, y + 8, 0xFF8A4A10);
        int areaY = y + 22, areaH = h - 28;
        int lines = lineCount(c.text(), tw);
        int total = lines * 10;
        textScroll = Math.max(0, Math.min(textScroll, Math.max(0, total - areaH)));
        g.enableScissor(tx - 2, areaY, tx + tw + 2, areaY + areaH);
        wrap(g, c.text(), tx, areaY - textScroll, tw, INK, 200);
        g.disableScissor();
        if (total > areaH) scrollBar(g, x + w - 7, areaY, areaH, textScroll / (float) Math.max(1, total - areaH), areaH / (float) total);
    }

    // ------------------------------------------------------------------ guidance
    private void drawGuide(GuiGraphics g, HunterData d, int x, int y, int w, int h) {
        int cy = y + 8;
        icon(g, I_HR, x + 8, cy - 4);
        text(g, Component.literal("HR " + d.hunterRank).withStyle(ChatFormatting.BOLD), x + 26, cy, 0xFF8A6A3A);
        int need = HunterData.hrpForNext(d.hunterRank);
        bar(g, x + 80, cy + 1, w - 90, 5, d.hrPoints / (float) Math.max(1, need), 0xFFF2C94A, 0xFFC08A20);
        cy += 10;
        text(g, Component.translatable("screen.huntcraft.elder.hrp", need - d.hrPoints, d.hunterRank + 1), x + 80, cy, INK_SOFT);
        cy += 14;
        rule(g, x + 6, cy, w - 12);
        cy += 5;
        text(g, Component.translatable("screen.huntcraft.elder.goals"), x + 8, cy, INK_SOFT);
        cy += 12;
        for (Component goal : goals(d)) {
            if (cy > y + h - 60) break;
            icon(g, I_FLAG, x + 8, cy - 4);
            cy += Math.max(12, wrap(g, goal, x + 26, cy, w - 34, INK, 3) + 3);
        }
        cy = Math.max(cy, y + h - 58);
        rule(g, x + 6, cy, w - 12);
        cy += 5;
        text(g, Component.translatable("screen.huntcraft.elder.next_unlocks", d.hunterRank + 1), x + 8, cy, INK_SOFT);
        cy += 12;
        List<Component> unlocks = unlocks(d.hunterRank + 1);
        if (unlocks.isEmpty()) {
            text(g, Component.translatable("screen.huntcraft.elder.no_unlocks"), x + 12, cy, 0xFFAA9A80);
        }
        int col = 0;
        for (Component u : unlocks) {
            if (cy > y + h - 12) break;
            textClip(g, Component.literal("・").append(u), x + 10 + col * ((w - 20) / 2), cy, (w - 20) / 2 - 4, INK, false);
            col++;
            if (col == 2) {
                col = 0;
                cy += 11;
            }
        }
    }

    /** What the chief suggests next, from the hunter's progress. */
    private List<Component> goals(HunterData d) {
        List<Component> out = new ArrayList<>();
        Player p = player();
        CompoundTag v = village();
        if (MofuData.list(v).isEmpty()) out.add(Component.translatable("screen.huntcraft.elder.goal_mofu"));
        if (v.getCompound("dishes").isEmpty()) out.add(Component.translatable("screen.huntcraft.elder.goal_meal"));
        if (ElderStory.largeHunts(d) == 0) out.add(Component.translatable("screen.huntcraft.elder.goal_quest"));
        for (MonsterRegistry.Entry e : MonsterRegistry.large()) {
            if (d.totalHunted(e.id()) > 0) continue;
            MonsterProfile prof = e.profile();
            Element best = Element.NONE;
            int stars = 0;
            for (Map.Entry<Element, Integer> we : prof.weakness.entrySet()) {
                if (we.getValue() > stars) {
                    stars = we.getValue();
                    best = we.getKey();
                }
            }
            out.add(best == Element.NONE ? Component.translatable("screen.huntcraft.elder.goal_hunt", prof.displayName())
                    : Component.translatable("screen.huntcraft.elder.goal_hunt_weak", prof.displayName(), best.displayName()));
            break;
        }
        if (p != null && ElderStory.unclaimed(p) > 0) out.add(Component.translatable("screen.huntcraft.elder.goal_reward", ElderStory.unclaimed(p)));
        if (MofuData.list(v).size() > 1) out.add(Component.translatable("screen.huntcraft.elder.goal_expedition"));
        if (out.isEmpty()) out.add(Component.translatable("screen.huntcraft.elder.goal_free"));
        return out;
    }

    /** Things that unlock at a hunter rank (shop goods, ingredients, expeditions, companion gear, rewards). */
    private List<Component> unlocks(int hr) {
        List<Component> out = new ArrayList<>();
        for (ShopRegistry.Entry e : ShopRegistry.stock()) {
            if (e.requiredHR() == hr) out.add(Component.translatable("screen.huntcraft.elder.unlock_shop", new ItemStack(e.item().get()).getHoverName()));
        }
        for (Ingredients.Ingredient i : Ingredients.ALL) {
            if (i.requiredHR() == hr) out.add(Component.translatable("screen.huntcraft.elder.unlock_ingredient", i.displayName()));
        }
        for (Expeditions.Destination de : Expeditions.ALL) {
            if (de.requiredHR() == hr) out.add(Component.translatable("screen.huntcraft.elder.unlock_expedition", de.displayName()));
        }
        for (MofuGear.Tier t : MofuGear.Tier.values()) {
            if (t.requiredHR == hr) out.add(Component.translatable("screen.huntcraft.elder.unlock_gear", t.displayName()));
        }
        return out;
    }

    // ------------------------------------------------------------------ rewards
    private void drawRewards(GuiGraphics g, HunterData d, int x, int y, int w, int h, int mx, int my) {
        Player p = player();
        if (p == null) return;
        int cy = y + 6;
        for (ElderStory.Reward r : ElderStory.REWARDS) {
            boolean reached = r.reached().test(d);
            boolean claimed = ElderStory.claimed(p, r);
            rowHighlight(g, x + 4, cy, w - 8, 26, false, in(mx, my, x + 4, cy, w - 8, 26));
            icon(g, claimed ? I_CHECK : reached ? I_GIFT : I_LOCK, x + 6, cy + 5);
            int gx = x + w - 76 - 18 * r.gifts().size();
            textClip(g, r.title(), x + 24, cy + 3, gx - x - 28, reached ? INK : 0xFFAA9A80, false);
            text(g, Component.literal(String.format("%,d z", r.zenny())), x + 24, cy + 14, reached ? 0xFF8A6A3A : 0xFFAA9A80);
            for (ElderStory.Gift gift : r.gifts()) {
                ItemStack st = new ItemStack(gift.item().get(), gift.count());
                itemWithCount(g, st, gx, cy + 5);
                if (in(mx, my, gx, cy + 5, 16, 16)) hoverStack = st;
                gx += 18;
            }
            if (claimed) text(g, Component.translatable("screen.huntcraft.elder.claimed"), x + w - 66, cy + 9, OK_DARK);
            else button(g, x + w - 70, cy + 4, 62, 18, Component.translatable("screen.huntcraft.elder.claim"), reached, () -> {
                CompoundTag t = new CompoundTag();
                t.putString("op", "claim");
                t.putString("id", r.id());
                HCNetwork.sendToServer("village/elder", t);
            });
            cy += 27;
            if (cy > y + h - 26) break;
        }
    }

    @Override
    protected void drawTooltips(GuiGraphics g, int mx, int my) {
        if (hoverStack != null && !hoverStack.isEmpty()) g.renderTooltip(this.font, hoverStack, mx, my);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        textScroll -= (int) Math.signum(delta) * 10;
        return true;
    }
}
