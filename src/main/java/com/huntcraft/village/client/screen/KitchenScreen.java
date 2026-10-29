package com.huntcraft.village.client.screen;

import com.huntcraft.data.HunterData;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.village.meal.Ingredients;
import com.huntcraft.village.meal.MealSystem;
import com.huntcraft.village.npc.NpcRole;
import com.huntcraft.village.npc.VillageNpc;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mofu Diner (モフ亭): pick two ingredients of different categories; the pair decides the dish and its food skill,
 * premium ingredients add bonus skills, and the daily special is cheaper and stronger.
 */
@OnlyIn(Dist.CLIENT)
public class KitchenScreen extends VillageScreen {
    private static final int ROW_H = 16;
    @Nullable
    private Ingredients.Ingredient pickA, pickB;
    private int scroll;
    private final List<Object> rows = new ArrayList<>();
    @Nullable
    private Component hoverText;
    @Nullable
    private final VillageNpc portrait;

    public KitchenScreen(@Nullable CompoundTag tag) {
        super(Component.translatable("screen.huntcraft.kitchen.title"), 356, 226, tag);
        this.portrait = NpcPortrait.npc(NpcRole.CHEF);
        for (Ingredients.Category c : Ingredients.Category.values()) {
            List<Ingredients.Ingredient> list = new ArrayList<>();
            for (Ingredients.Ingredient i : Ingredients.ALL) if (i.category() == c) list.add(i);
            if (list.isEmpty()) continue;
            rows.add(c);
            rows.addAll(list);
        }
    }

    private long day() {
        Player p = player();
        return p == null ? 0 : MealSystem.day(p.level());
    }

    @Override
    public void tick() {
        super.tick();
        NpcPortrait.tick(portrait);
    }

    private void pick(Ingredients.Ingredient i) {
        if (pickA == i) {
            pickA = pickB;
            pickB = null;
        } else if (pickB == i) {
            pickB = null;
        } else if (pickA == null) {
            pickA = i;
        } else if (pickA.category() == i.category()) {
            pickA = i;
        } else {
            pickB = i;
        }
        click();
    }

    @Override
    protected void drawContent(GuiGraphics g, int mx, int my, float pt) {
        hoverText = null;
        titlePlate(g, this.title, I_POT);
        walletBar(g);
        HunterData d = data();
        // ---- ingredient list
        int lx = left + 10, ly = top + 26, lw = 150, lh = panelH - 36;
        inset(g, lx, ly, lw, lh);
        int visible = (lh - 6) / ROW_H;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
        g.enableScissor(lx + 2, ly + 2, lx + lw - 2, ly + lh - 2);
        for (int i = 0; i < visible + 1 && i + scroll < rows.size(); i++) {
            Object o = rows.get(i + scroll);
            int ry = ly + 3 + i * ROW_H;
            if (o instanceof Ingredients.Category c) {
                g.fill(lx + 3, ry + 1, lx + lw - 8, ry + ROW_H - 1, 0x50C9A66B);
                textClip(g, c.displayName(), lx + 7, ry + 4, lw - 18, GOLD, true);
                continue;
            }
            Ingredients.Ingredient ing = (Ingredients.Ingredient) o;
            boolean locked = ing.requiredHR() > d.hunterRank;
            boolean sel = ing == pickA || ing == pickB;
            boolean hover = in(mx, my, lx + 3, ry, lw - 10, ROW_H);
            rowHighlight(g, lx + 3, ry, lw - 10, ROW_H, sel, hover);
            icon(g, I_ING0 + Ingredients.index(ing), lx + 5, ry);
            String price = locked ? "HR" + ing.requiredHR() : ing.price() + "z";
            int pw = this.font.width(price);
            textClip(g, ing.displayName(), lx + 24, ry + 4, lw - 38 - pw, locked ? GREY : LIGHT, true);
            if (ing.premium()) icon(g, I_SPARKLE, lx + lw - 26 - pw, ry);
            g.drawString(this.font, price, lx + lw - 10 - pw, ry + 4, locked ? GREY : GOLD, true);
            if (hover) hoverText = ingredientTip(ing);
            if (!locked) region(lx + 3, ry, lw - 10, ROW_H, () -> pick(ing));
        }
        g.disableScissor();
        scrollBar(g, lx + lw - 6, ly + 3, lh - 6, rows.size() <= visible ? 0 : scroll / (float) (rows.size() - visible), visible / (float) Math.max(1, rows.size()));

        // ---- dish preview
        int dx = left + 166, dy = top + 26, dw = panelW - 176, dh = panelH - 36;
        paper(g, dx, dy, dw, dh);
        Ingredients.Ingredient[] special = Ingredients.dailySpecial(day());
        // daily special strip
        int sy = dy + 6;
        g.fill(dx + 6, sy, dx + dw - 6, sy + 20, 0x30E05A20);
        icon(g, I_TAG, dx + 8, sy + 2);
        icon(g, I_ING0 + Ingredients.index(special[0]), dx + 26, sy + 2);
        icon(g, I_ING0 + Ingredients.index(special[1]), dx + 42, sy + 2);
        textClip(g, Component.translatable("screen.huntcraft.kitchen.special"), dx + 62, sy + 6, dw - 116, 0xFF9A3A10, false);
        if (in(mx, my, dx + 6, sy, dw - 60, 20)) hoverText = Component.translatable("screen.huntcraft.kitchen.special_tip");
        button(g, dx + dw - 50, sy + 1, 42, 18, Component.translatable("screen.huntcraft.kitchen.pick_special"),
                special[0].requiredHR() <= d.hunterRank && special[1].requiredHR() <= d.hunterRank, () -> {
                    pickA = special[0];
                    pickB = special[1];
                });
        // selection slots: [A] + [B] = [dish]
        int cy = dy + 32;
        int sx = dx + (dw - 150) / 2;
        slotBox(g, sx, cy, pickA, "A");
        icon(g, I_PLUS, sx + 32, cy + 4);
        slotBox(g, sx + 54, cy, pickB, "B");
        text(g, "=", sx + 88, cy + 8, INK);
        Ingredients.Meal meal = pickA != null && pickB != null ? Ingredients.cook(pickA, pickB, Ingredients.isSpecial(pickA, pickB, day())) : null;
        if (meal == null) {
            inset(g, sx + 100, cy - 4, 34, 34);
            wrap(g, Component.translatable(pickA == null ? "screen.huntcraft.kitchen.pick_first" : "screen.huntcraft.kitchen.pick_second"),
                    dx + 10, cy + 34, dw - 20, INK_SOFT, 2);
            drawStatus(g, d, dx, dy, dw, dh);
            if (pickA == null) {
                preview(g, dx + dw - 30, dy + dh - 8, 30, portrait, mx, my);
                textClip(g, Component.translatable("npc.huntcraft.chef.line"), dx + 8, dy + dh - 14, dw - 60, INK, false);
            }
            return;
        }
        int icon = Ingredients.DISH_IDS.indexOf(meal.dish().id());
        inset(g, sx + 100, cy - 4, 34, 34);
        dishIcon(g, icon, sx + 101, cy - 3, 32);
        cy += 34;
        textClip(g, meal.displayName().copy().withStyle(ChatFormatting.BOLD), dx + 10, cy, dw - 20, INK, false);
        if (meal.special()) text(g, Component.translatable("screen.huntcraft.kitchen.special_badge"), dx + 10, cy + 10, 0xFF9A3A10);
        cy += meal.special() ? 22 : 13;
        rule(g, dx + 8, cy, dw - 16);
        cy += 5;
        int colW = (dw - 20) / 2;
        statLine(g, I_HEART, Component.translatable("screen.huntcraft.kitchen.hp", meal.hp()), dx + 10, cy, meal.hp() > 0);
        statLine(g, I_STAMINA, Component.translatable("screen.huntcraft.kitchen.stamina", meal.stamina()), dx + 10 + colW, cy, meal.stamina() > 0);
        cy += 17;
        statLine(g, I_SWORD, Component.translatable("screen.huntcraft.kitchen.attack", meal.atkPct()), dx + 10, cy, meal.atkPct() > 0);
        statLine(g, I_SHIELD, Component.translatable("screen.huntcraft.kitchen.defense", meal.def()), dx + 10 + colW, cy, meal.def() > 0);
        cy += 19;
        text(g, Component.translatable("screen.huntcraft.kitchen.skills"), dx + 10, cy, INK_SOFT);
        cy += 11;
        for (Ingredients.Skill s : meal.skills()) {
            if (cy > dy + dh - 40) break;
            icon(g, I_SPARKLE, dx + 10, cy - 4);
            text(g, s.displayName().copy().withStyle(ChatFormatting.BOLD), dx + 28, cy, 0xFF8A4A10);
            int used = wrap(g, s.description(), dx + 28 + this.font.width(s.displayName()) + 8, cy, dw - 48 - this.font.width(s.displayName()), INK_SOFT, 2);
            cy += Math.max(12, used + 2);
        }
        int by = dy + dh - 24;
        boolean afford = d.zenny >= meal.price();
        boolean hrOk = d.hunterRank >= meal.requiredHR();
        icon(g, I_ZENNY, dx + 10, by + 1);
        text(g, Component.literal(String.format("%,d z", meal.price())), dx + 28, by + 5, afford ? OK_DARK : BAD_DARK);
        final Ingredients.Ingredient a = pickA, b = pickB;
        button(g, dx + dw - 100, by, 90, 18, Component.translatable("screen.huntcraft.kitchen.eat"), afford && hrOk, () -> {
            CompoundTag t = new CompoundTag();
            t.putString("a", a.id());
            t.putString("b", b.id());
            HCNetwork.sendToServer("village/meal", t);
            pickA = null;
            pickB = null;
        });
    }

    private Component ingredientTip(Ingredients.Ingredient ing) {
        Component c = ing.displayName().copy().append(Component.literal("  "));
        List<String> parts = new ArrayList<>();
        if (ing.hp() > 0) parts.add("HP+" + ing.hp());
        if (ing.stamina() > 0) parts.add("ST+" + ing.stamina());
        if (ing.atk() > 0) parts.add("ATK+" + ing.atk() + "%");
        if (ing.def() > 0) parts.add("DEF+" + ing.def());
        Component out = c.copy().append(Component.literal(String.join(" ", parts)).withStyle(ChatFormatting.GRAY));
        if (ing.bonus() != null) out = out.copy().append(Component.literal(" / ")).append(ing.bonus().displayName().copy().withStyle(ChatFormatting.GOLD));
        return out;
    }

    private void slotBox(GuiGraphics g, int x, int y, @Nullable Ingredients.Ingredient ing, String label) {
        inset(g, x, y - 2, 26, 26);
        if (ing != null) {
            g.pose().pushPose();
            g.pose().translate(x + 5, y + 3, 0);
            icon(g, I_ING0 + Ingredients.index(ing), 0, 0);
            g.pose().popPose();
            if (in(mouseX, mouseY, x, y - 2, 26, 26)) {
                hoverText = ingredientTip(ing);
                region(x, y - 2, 26, 26, () -> pick(ing));
            }
        } else {
            g.drawString(this.font, label, x + 10, y + 7, 0xFF8A7A60, false);
        }
    }

    private void statLine(GuiGraphics g, int iconIdx, Component c, int x, int y, boolean active) {
        icon(g, iconIdx, x, y - 4);
        text(g, c, x + 18, y, active ? INK : 0xFFAA9A80);
    }

    /** Current meal (if any) and the recipe book progress. */
    private void drawStatus(GuiGraphics g, HunterData d, int dx, int dy, int dw, int dh) {
        Player p = player();
        if (p == null) return;
        int y = dy + 92;
        rule(g, dx + 8, y, dw - 16);
        y += 6;
        Ingredients.Meal cur = MealSystem.active(p);
        if (cur != null) {
            long secs = MealSystem.remainingTicks(p) / 20;
            dishIcon(g, Ingredients.DISH_IDS.indexOf(cur.dish().id()), dx + 10, y, 16);
            textClip(g, Component.translatable("screen.huntcraft.kitchen.current", cur.displayName()), dx + 30, y + 4, dw - 40, INK, false);
            text(g, Component.translatable("screen.huntcraft.kitchen.remaining", secs / 60, String.format("%02d", secs % 60)), dx + 30, y + 14, INK_SOFT);
        } else {
            text(g, Component.translatable("screen.huntcraft.kitchen.no_meal"), dx + 10, y + 4, INK_SOFT);
        }
        y += 28;
        CompoundTag book = village().getCompound("dishes");
        int tried = 0;
        for (String id : Ingredients.DISH_IDS) if (book.getInt(id) > 0) tried++;
        icon(g, I_BOOK, dx + 10, y - 4);
        text(g, Component.translatable("screen.huntcraft.kitchen.book", tried, Ingredients.DISH_IDS.size()), dx + 28, y, INK_SOFT);
    }

    @Override
    protected void drawTooltips(GuiGraphics g, int mx, int my) {
        if (hoverText != null) g.renderTooltip(this.font, hoverText, mx, my);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }
}
