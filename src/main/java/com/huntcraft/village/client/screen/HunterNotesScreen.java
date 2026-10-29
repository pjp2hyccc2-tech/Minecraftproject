package com.huntcraft.village.client.screen;

import com.huntcraft.core.Element;
import com.huntcraft.core.Rank;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.village.companion.MofuData;
import com.huntcraft.village.meal.Ingredients;
import com.huntcraft.village.story.ElderStory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hunter's notes (ハンターノート): monster field guide (description, weaknesses, hunt counts, size records with crowns,
 * breakable parts and carve materials - details unlock as you hunt) and the guild card.
 */
@OnlyIn(Dist.CLIENT)
public class HunterNotesScreen extends VillageScreen {
    private static final int ROW_H = 18;
    private static final Element[] ELEMENTS = {Element.FIRE, Element.WATER, Element.THUNDER, Element.ICE, Element.DRAGON};
    private int tab; // 0 monsters, 1 guild card
    private int scroll;
    private int detailScroll;
    @Nullable
    private String selected;
    private final List<MonsterRegistry.Entry> entries = new ArrayList<>();
    private final Map<String, HuntMonster> previews = new HashMap<>();
    @Nullable
    private ItemStack hoverStack;
    @Nullable
    private Component hoverText;
    private float spin;

    public HunterNotesScreen(@Nullable CompoundTag tag) {
        super(Component.translatable("screen.huntcraft.notes.title"), 356, 226, tag);
        entries.addAll(MonsterRegistry.large());
        entries.addAll(MonsterRegistry.small());
        if (!entries.isEmpty()) selected = entries.get(0).id();
    }

    @Override
    public void tick() {
        super.tick();
        spin += 1.2F;
        HuntMonster m = selected == null ? null : previews.get(selected);
        if (m != null) {
            m.tickCount++;
            m.idleAnim.startIfStopped(m.tickCount);
        }
    }

    @Nullable
    private HuntMonster preview(String id) {
        if (previews.containsKey(id)) return previews.get(id);
        HuntMonster m = null;
        MonsterRegistry.Entry e = MonsterRegistry.get(id);
        if (e != null && Minecraft.getInstance().level != null) {
            try {
                m = e.type().get().create(Minecraft.getInstance().level);
            } catch (RuntimeException ex) {
                m = null;
            }
        }
        previews.put(id, m);
        return m;
    }

    @Override
    protected void drawContent(GuiGraphics g, int mx, int my, float pt) {
        hoverStack = null;
        hoverText = null;
        titlePlate(g, this.title, I_BOOK);
        int tx = left + 10, ty = top + 24;
        String[] tabs = {"screen.huntcraft.notes.tab_monsters", "screen.huntcraft.notes.tab_card"};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tab(g, tx + i * 82, ty, 80, Component.translatable(tabs[i]), tab == i, () -> {
                tab = idx;
                pageSound();
            });
        }
        if (tab == 1) {
            drawCard(g, mx, my);
            return;
        }
        HunterData d = data();
        int lx = left + 10, ly = top + 46, lw = 118, lh = panelH - 56;
        inset(g, lx, ly, lw, lh);
        int visible = (lh - 6) / ROW_H;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - visible)));
        if (entries.isEmpty()) wrap(g, Component.translatable("screen.huntcraft.notes.none"), lx + 6, ly + 8, lw - 12, GREY, 6);
        g.enableScissor(lx + 2, ly + 2, lx + lw - 2, ly + lh - 2);
        for (int i = 0; i < visible + 1 && i + scroll < entries.size(); i++) {
            MonsterRegistry.Entry e = entries.get(i + scroll);
            int ry = ly + 3 + i * ROW_H;
            int hunted = d.totalHunted(e.id());
            boolean sel = e.id().equals(selected);
            rowHighlight(g, lx + 3, ry, lw - 10, ROW_H, sel, in(mx, my, lx + 3, ry, lw - 10, ROW_H));
            icon(g, e.profile().large ? I_SKULL : I_PAW, lx + 4, ry + 1);
            int crown = crownOf(e.profile(), d.sizeRecords.get(e.id()));
            int nameW = lw - 34 - (crown >= 0 ? 12 : 0);
            textClip(g, e.profile().displayName(), lx + 22, ry + 5, nameW, hunted > 0 ? LIGHT : 0xFF8A7A68, true);
            if (crown >= 0) icon(g, crown, lx + lw - 22, ry + 1);
            final String id = e.id();
            region(lx + 3, ry, lw - 10, ROW_H, () -> {
                selected = id;
                detailScroll = 0;
            });
        }
        g.disableScissor();
        scrollBar(g, lx + lw - 6, ly + 3, lh - 6, entries.size() <= visible ? 0 : scroll / (float) (entries.size() - visible), visible / (float) Math.max(1, entries.size()));
        int dx = left + 134, dy = top + 46, dw = panelW - 144, dh = panelH - 56;
        paper(g, dx, dy, dw, dh);
        MonsterRegistry.Entry e = selected == null ? null : MonsterRegistry.get(selected);
        if (e == null) return;
        drawMonster(g, d, e, dx, dy, dw, dh, mx, my, pt);
    }

    /** Gold crown for the smallest / largest possible sizes, silver near them, else -1. */
    static int crownOf(MonsterProfile p, @Nullable float[] rec) {
        if (rec == null || p.maxScale <= p.minScale) return -1;
        float span = p.maxScale - p.minScale;
        if (rec[0] <= p.minScale + span * 0.02F || rec[1] >= p.maxScale - span * 0.02F) return I_CROWN_GOLD;
        if (rec[0] <= p.minScale + span * 0.12F || rec[1] >= p.maxScale - span * 0.12F) return I_CROWN_SILVER;
        return -1;
    }

    private void drawMonster(GuiGraphics g, HunterData d, MonsterRegistry.Entry e, int x, int y, int w, int h, int mx, int my, float pt) {
        MonsterProfile p = e.profile();
        int hunted = d.totalHunted(e.id());
        // model box
        int bx = x + 6, by = y + 6, bw = 84, bh = 78;
        inset(g, bx, by, bw, bh);
        HuntMonster m = preview(e.id());
        if (m != null && hunted > 0) {
            float size = Math.max(m.getBbHeight(), m.getBbWidth() * (p.large ? 1.6F : 1.2F));
            int scale = (int) Math.max(6, Math.min(40, 64 / Math.max(0.5F, size)));
            g.enableScissor(bx + 2, by + 2, bx + bw - 2, by + bh - 2);
            try {
                Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(spin + pt * 1.2F + 200));
                Quaternionf cam = new Quaternionf().rotateX((float) Math.toRadians(-12));
                pose.mul(cam);
                InventoryScreen.renderEntityInInventory(g, bx + bw / 2, by + bh - 12, scale, pose, cam, m);
            } catch (RuntimeException ex) {
                previews.put(e.id(), null);
            }
            g.disableScissor();
        } else {
            g.drawCenteredString(this.font, "?", bx + bw / 2, by + bh / 2 - 4, 0xFF8A7A68);
        }
        // name / category
        int cx = x + 96, cw = w - 102;
        textClip(g, p.displayName().copy().withStyle(ChatFormatting.BOLD), cx, y + 8, cw, INK, false);
        String catKey = "notes.huntcraft.category." + p.category;
        text(g, I18n.exists(catKey) ? Component.translatable(catKey) : Component.literal(p.category), cx, y + 19, INK_SOFT);
        // counts
        text(g, Component.translatable("screen.huntcraft.notes.hunted", d.huntCounts.getOrDefault(e.id(), 0)), cx, y + 32, INK);
        if (p.large) text(g, Component.translatable("screen.huntcraft.notes.captured", d.captureCounts.getOrDefault(e.id(), 0)), cx, y + 42, INK);
        // size records
        float[] rec = d.sizeRecords.get(e.id());
        int sy = y + 54;
        if (rec != null && p.large) {
            text(g, Component.translatable("screen.huntcraft.notes.smallest", Math.round(rec[0] * 1000) / 10.0F), cx, sy, INK_SOFT);
            text(g, Component.translatable("screen.huntcraft.notes.largest", Math.round(rec[1] * 1000) / 10.0F), cx, sy + 10, INK_SOFT);
            float span = p.maxScale - p.minScale;
            if (span > 0 && rec[0] <= p.minScale + span * 0.12F) icon(g, rec[0] <= p.minScale + span * 0.02F ? I_CROWN_GOLD : I_CROWN_SILVER, x + w - 22, sy - 4);
            if (span > 0 && rec[1] >= p.maxScale - span * 0.12F) icon(g, rec[1] >= p.maxScale - span * 0.02F ? I_CROWN_GOLD : I_CROWN_SILVER, x + w - 22, sy + 6);
        }
        // weaknesses (full width under the model box)
        int wy = y + 90;
        text(g, Component.translatable("screen.huntcraft.notes.weakness"), x + 8, wy, INK_SOFT);
        int ex = x + 8 + this.font.width(Component.translatable("screen.huntcraft.notes.weakness")) + 6;
        int step = Math.max(28, (x + w - 6 - ex) / ELEMENTS.length);
        for (Element el : ELEMENTS) {
            icon(g, elementIcon(el), ex, wy - 4);
            if (hunted > 0) {
                int stars = p.weakness.getOrDefault(el, 0);
                g.drawString(this.font, stars == 0 ? "-" : "★".repeat(stars), ex + 15, wy, stars >= 2 ? 0xFFC0392B : INK, false);
            } else {
                g.drawString(this.font, "?", ex + 15, wy, 0xFF8A7A68, false);
            }
            ex += step;
        }
        // description + parts + materials (scrollable text area)
        int ty = y + 106;
        rule(g, x + 6, ty - 3, w - 12);
        int areaH = y + h - 6 - ty;
        g.enableScissor(x + 4, ty, x + w - 4, ty + areaH);
        int cy = ty - detailScroll;
        if (hunted <= 0) {
            cy += wrap(g, Component.translatable("screen.huntcraft.notes.unknown"), x + 8, cy, w - 16, INK_SOFT, 6) + 4;
        } else {
            cy += wrap(g, p.description(), x + 8, cy, w - 16, INK, 12) + 6;
            Set<String> breakable = new LinkedHashSet<>();
            for (PartDef pd : p.parts.values()) if (pd.isBreakable()) breakable.add(pd.name);
            if (!breakable.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (String n : breakable) {
                    String key = "part.huntcraft." + n;
                    names.add(I18n.exists(key) ? I18n.get(key) : n);
                }
                cy += wrap(g, Component.translatable("screen.huntcraft.notes.breakable", String.join(" / ", names)), x + 8, cy, w - 16, INK_SOFT, 3) + 4;
            }
            if (hunted >= 3) {
                text(g, Component.translatable("screen.huntcraft.notes.materials"), x + 8, cy, INK_SOFT);
                cy += 11;
                cy = materials(g, p.carves, x + 8, cy, w - 16, mx, my, ty, areaH);
            } else {
                cy += wrap(g, Component.translatable("screen.huntcraft.notes.more_hunts", 3 - hunted), x + 8, cy, w - 16, 0xFF8A7A68, 2);
            }
        }
        g.disableScissor();
        int total = cy + detailScroll - ty;
        if (total > areaH) {
            detailScroll = Math.max(0, Math.min(detailScroll, total - areaH));
            scrollBar(g, x + w - 8, ty, areaH, detailScroll / (float) Math.max(1, total - areaH), areaH / (float) total);
        } else {
            detailScroll = 0;
        }
    }

    private int materials(GuiGraphics g, RewardTable table, int x, int y, int w, int mx, int my, int clipTop, int clipH) {
        List<RewardTable.Entry> low = table.entries(Rank.LOW);
        Set<Item> seen = new LinkedHashSet<>();
        int col = 0;
        int colW = w / 2;
        for (RewardTable.Entry en : low) {
            Item item = en.item().get();
            if (!seen.add(item)) continue;
            ItemStack st = new ItemStack(item);
            int ix = x + col * colW;
            item(g, st, ix, y);
            int pct = Math.round(table.chanceOf(item, Rank.LOW) * 100);
            textClip(g, st.getHoverName(), ix + 18, y + 4, colW - 44, INK, false);
            g.drawString(this.font, pct + "%", ix + colW - 26, y + 4, INK_SOFT, false);
            if (in(mx, my, ix, y, colW, 16) && my >= clipTop && my < clipTop + clipH) hoverStack = st;
            col++;
            if (col == 2) {
                col = 0;
                y += 18;
            }
        }
        return col == 0 ? y : y + 18;
    }

    static int elementIcon(Element el) {
        return switch (el) {
            case FIRE -> I_FIRE;
            case WATER -> I_WATER;
            case THUNDER -> I_THUNDER;
            case ICE -> I_ICE;
            case DRAGON -> I_DRAGON;
            default -> I_STAR_EMPTY;
        };
    }

    // ------------------------------------------------------------------ guild card
    private void drawCard(GuiGraphics g, int mx, int my) {
        HunterData d = data();
        Player p = player();
        int x = left + 10, y = top + 46, w = panelW - 20, h = panelH - 56;
        paper(g, x, y, w, h);
        inset(g, x + 6, y + 6, 80, h - 12);
        if (p != null) {
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 46, y + h - 18, 36, x + 46 - mx, y + 40 - my, p);
            } catch (RuntimeException ignored) {
            }
        }
        int cx = x + 94, cw = w - 100;
        textClip(g, (p != null ? p.getName() : Component.literal("-")).copy().withStyle(ChatFormatting.BOLD), cx, y + 8, cw, INK, false);
        icon(g, I_HR, cx, y + 18);
        text(g, Component.literal("HR " + d.hunterRank), cx + 18, y + 22, 0xFF8A6A3A);
        int need = HunterData.hrpForNext(d.hunterRank);
        bar(g, cx + 60, y + 24, cw - 66, 5, d.hrPoints / (float) Math.max(1, need), 0xFFF2C94A, 0xFFC08A20);
        text(g, Component.translatable("screen.huntcraft.notes.hrp", d.hrPoints, need), cx + 60, y + 32, INK_SOFT);
        int total = 0, large = 0, captures = 0;
        String fav = null;
        int favN = 0;
        for (MonsterRegistry.Entry e : MonsterRegistry.all()) {
            int n = d.totalHunted(e.id());
            total += n;
            if (e.profile().large) large += n;
            captures += d.captureCounts.getOrDefault(e.id(), 0);
            if (n > favN) {
                favN = n;
                fav = e.id();
            }
        }
        CompoundTag v = village();
        CompoundTag book = v.getCompound("dishes");
        int dishes = 0;
        for (String id : Ingredients.DISH_IDS) if (book.getInt(id) > 0) dishes++;
        int chapters = 0;
        for (ElderStory.Chapter c : ElderStory.CHAPTERS) if (c.unlocked().test(d)) chapters++;
        int ly = y + 46;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.huntcraft.notes.card_zenny", String.format("%,d", d.zenny)));
        lines.add(Component.translatable("screen.huntcraft.notes.card_quests", d.completedQuests.size()));
        lines.add(Component.translatable("screen.huntcraft.notes.card_hunts", total, large));
        lines.add(Component.translatable("screen.huntcraft.notes.card_captures", captures));
        MonsterProfile favP = fav == null ? null : MonsterRegistry.profile(fav);
        lines.add(Component.translatable("screen.huntcraft.notes.card_favorite", favP == null ? Component.literal("-") : favP.displayName(), favN));
        lines.add(Component.translatable("screen.huntcraft.notes.card_mofu", MofuData.list(v).size(), MofuData.MAX_COMPANIONS));
        lines.add(Component.translatable("screen.huntcraft.notes.card_dishes", dishes, Ingredients.DISH_IDS.size()));
        lines.add(Component.translatable("screen.huntcraft.notes.card_story", chapters, ElderStory.CHAPTERS.size()));
        for (Component c : lines) {
            textClip(g, c, cx, ly, cw, INK, false);
            ly += 13;
        }
    }

    @Override
    protected void drawTooltips(GuiGraphics g, int mx, int my) {
        if (hoverStack != null && !hoverStack.isEmpty()) g.renderTooltip(this.font, hoverStack, mx, my);
        else if (hoverText != null) g.renderTooltip(this.font, hoverText, mx, my);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == 0 && mx > left + 134) detailScroll -= (int) Math.signum(delta) * 10;
        else scroll -= (int) Math.signum(delta);
        return true;
    }
}
