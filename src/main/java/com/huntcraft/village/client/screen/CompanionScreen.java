package com.huntcraft.village.client.screen;

import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.data.HunterData;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.village.client.ClientVillage;
import com.huntcraft.village.companion.CompanionManager;
import com.huntcraft.village.companion.Expeditions;
import com.huntcraft.village.companion.MofuData;
import com.huntcraft.village.companion.MofuEntity;
import com.huntcraft.village.companion.MofuGear;
import com.huntcraft.village.companion.MofuGearItem;
import com.huntcraft.village.companion.MofuSkill;
import com.huntcraft.village.companion.MofuType;
import com.huntcraft.village.npc.NpcRole;
import com.huntcraft.village.npc.VillageNpc;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Mofu Lodge (モフの寄合所): choose the first companion (type, fur, name), manage the roster (follow / rest / train /
 * retrain / rename / dismiss), equip forged gear, hire new companions and send idle ones on expeditions.
 */
@OnlyIn(Dist.CLIENT)
public class CompanionScreen extends VillageScreen {
    private static final int ROW_H = 26;
    private int tab; // 0 roster, 1 gear, 2 hire, 3 expedition
    private int selUid;
    private int gearSlot;
    private int dest;
    private int length;
    private int retrainType = -1;
    private boolean confirmDismiss;
    private boolean renaming;
    // first companion
    private int firstType;
    private int firstPattern;
    private EditBox nameBox;
    @Nullable
    private MofuEntity preview;
    private int previewKey = Integer.MIN_VALUE;
    @Nullable
    private final VillageNpc elder;
    @Nullable
    private ItemStack hoverStack;
    @Nullable
    private Component hoverText;

    public CompanionScreen(@Nullable CompoundTag tag) {
        super(Component.translatable("screen.huntcraft.mofu.title"), 356, 226, tag);
        this.elder = NpcPortrait.npc(NpcRole.MOFU_ELDER);
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(this.font, left + 10, top + panelH - 50, 120, 16, Component.translatable("screen.huntcraft.mofu.name"));
        nameBox.setMaxLength(16);
        nameBox.setVisible(false);
        this.addRenderableWidget(nameBox);
        CompoundTag c = new CompoundTag();
        c.putString("op", "cands");
        HCNetwork.sendToServer("village/mofu", c);
    }

    private void send(String op, CompoundTag extra) {
        CompoundTag t = extra.copy();
        t.putString("op", op);
        HCNetwork.sendToServer("village/mofu", t);
    }

    private void send(String op, int uid) {
        CompoundTag t = new CompoundTag();
        t.putInt("uid", uid);
        send(op, t);
    }

    private boolean firstMode() {
        CompoundTag v = village();
        return MofuData.list(v).isEmpty() && !v.getBoolean("firstMofu");
    }

    @Nullable
    private CompoundTag selected() {
        CompoundTag v = village();
        CompoundTag r = MofuData.find(v, selUid);
        if (r == null) {
            List<CompoundTag> all = MofuData.records(v);
            if (!all.isEmpty()) {
                r = all.get(0);
                selUid = r.getInt("uid");
            }
        }
        return r;
    }

    @Override
    public void tick() {
        super.tick();
        NpcPortrait.tick(elder);
        NpcPortrait.tick(preview);
    }

    /** Keeps the 3D preview in sync with the shown companion. */
    private MofuEntity previewFor(int pattern, MofuType type, int lv, int w, int h, int a) {
        int key = ((((pattern * 7 + type.ordinal()) * 64 + lv) * 16 + w + 1) * 16 + h + 1) * 16 + a + 1;
        if (preview == null || key != previewKey) {
            MofuEntity old = preview;
            preview = NpcPortrait.mofu(pattern, type, lv, w, h, a);
            if (preview != null && old != null) preview.tickCount = old.tickCount;
            previewKey = key;
        }
        return preview;
    }

    private MofuEntity previewFor(CompoundTag rec) {
        return previewFor(rec.getInt("pat"), MofuData.type(rec), MofuData.level(rec), MofuGear.equipped(rec, MofuGear.Slot.WEAPON),
                MofuGear.equipped(rec, MofuGear.Slot.HELM), MofuGear.equipped(rec, MofuGear.Slot.ARMOR));
    }

    @Override
    protected void drawContent(GuiGraphics g, int mx, int my, float pt) {
        hoverStack = null;
        hoverText = null;
        titlePlate(g, this.title, I_PAW);
        walletBar(g);
        if (firstMode()) {
            drawFirst(g, mx, my);
            return;
        }
        if (!renaming) nameBox.setVisible(false);
        int tx = left + 10, ty = top + 24;
        String[] tabs = {"screen.huntcraft.mofu.tab_roster", "screen.huntcraft.mofu.tab_gear", "screen.huntcraft.mofu.tab_hire",
                "screen.huntcraft.mofu.tab_expedition"};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tab(g, tx + i * 58, ty, 56, Component.translatable(tabs[i]), tab == i, () -> {
                tab = idx;
                confirmDismiss = false;
                retrainType = -1;
                stopRename();
                pageSound();
            });
        }
        if (tab == 2) {
            drawHire(g, mx, my);
            return;
        }
        drawRoster(g, mx, my);
        CompoundTag rec = selected();
        int dx = left + 118, dy = top + 46, dw = panelW - 128, dh = panelH - 56;
        paper(g, dx, dy, dw, dh);
        if (rec == null) {
            preview(g, dx + dw / 2, dy + dh - 30, 54, elder, mx, my);
            wrap(g, Component.translatable("screen.huntcraft.mofu.no_companions"), dx + 8, dy + 8, dw - 16, INK_SOFT, 4);
            return;
        }
        switch (tab) {
            case 0 -> drawDetail(g, rec, dx, dy, dw, dh, mx, my);
            case 1 -> drawGear(g, rec, dx, dy, dw, dh, mx, my);
            default -> drawExpedition(g, rec, dx, dy, dw, dh, mx, my);
        }
    }

    // ------------------------------------------------------------------ first companion
    private void drawFirst(GuiGraphics g, int mx, int my) {
        int x = left + 10, y = top + 24, w = panelW - 20;
        speech(g, Component.translatable("screen.huntcraft.mofu.first_intro"), x, y, w);
        // type cards
        int cy = y + 26;
        for (MofuType t : MofuType.values()) {
            int cx = x + t.ordinal() * 76;
            boolean sel = firstType == t.ordinal();
            boolean hover = in(mx, my, cx, cy, 74, 74);
            paper(g, cx, cy, 74, 74);
            if (sel) nine(g, cx, cy, 74, 74, 200, 0, 64, 16, 3);
            else if (hover) g.fill(cx + 2, cy + 2, cx + 72, cy + 72, 0x20FFFFFF);
            icon(g, typeIcon(t), cx + 4, cy + 4);
            textClip(g, t.displayName().copy().withStyle(ChatFormatting.BOLD), cx + 22, cy + 8, 50, INK, false);
            wrap(g, t.description(), cx + 5, cy + 24, 64, INK_SOFT, 5);
            region(cx, cy, 74, 74, () -> {
                firstType = t.ordinal();
                click();
            });
        }
        // fur patterns
        int py = cy + 80;
        text(g, Component.translatable("screen.huntcraft.mofu.fur"), x, py + 6, LIGHT);
        for (int i = 0; i < MofuData.PATTERNS; i++) {
            int fx = x + 40 + i * 30;
            final int idx = i;
            inset(g, fx, py, 28, 28);
            if (firstPattern == i) nine(g, fx - 1, py - 1, 30, 30, 200, 0, 64, 16, 3);
            mofuFace(g, i, fx + 2, py + 2, 24);
            region(fx, py, 28, 28, () -> {
                firstPattern = idx;
                click();
            });
        }
        // name + confirm
        nameBox.setVisible(true);
        nameBox.setX(x + 40);
        nameBox.setY(top + panelH - 26);
        text(g, Component.translatable("screen.huntcraft.mofu.name"), x, top + panelH - 22, LIGHT);
        button(g, x + 166, top + panelH - 28, 62, 20, Component.translatable("screen.huntcraft.mofu.random_name"), true,
                () -> nameBox.setValue(Component.translatable("mofu.huntcraft.name." + (int) (Math.random() * MofuData.NAMES)).getString()));
        // preview
        int px = left + panelW - 44, ppy = top + 160;
        inset(g, px - 36, top + 50, 72, 128);
        preview(g, px, ppy, 64, previewFor(firstPattern, MofuType.byId(firstType), 1, MofuGear.Tier.BAMBOO.ordinal(), -1, MofuGear.Tier.BAMBOO.ordinal()), mx, my);
        button(g, left + panelW - 112, top + panelH - 28, 102, 20, Component.translatable("screen.huntcraft.mofu.decide"), true, () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("type", firstType);
            t.putInt("pat", firstPattern);
            t.putString("name", nameBox.getValue());
            send("first", t);
            nameBox.setValue("");
            nameBox.setVisible(false);
        });
    }

    private static int typeIcon(MofuType t) {
        return switch (t) {
            case FIGHTER -> I_FIGHTER;
            case HEALER -> I_HEALER;
            case BOMBER -> I_BOMBER;
        };
    }

    // ------------------------------------------------------------------ roster list (left)
    private void drawRoster(GuiGraphics g, int mx, int my) {
        int lx = left + 10, ly = top + 46, lw = 104, lh = panelH - 56;
        inset(g, lx, ly, lw, lh);
        CompoundTag v = village();
        int active = v.getInt("active");
        Player p = player();
        long now = p == null ? 0 : p.level().getGameTime();
        List<CompoundTag> recs = MofuData.records(v);
        for (int i = 0; i < recs.size(); i++) {
            CompoundTag r = recs.get(i);
            int ry = ly + 3 + i * ROW_H;
            int uid = r.getInt("uid");
            boolean sel = uid == selUid;
            rowHighlight(g, lx + 3, ry, lw - 6, ROW_H - 1, sel, in(mx, my, lx + 3, ry, lw - 6, ROW_H - 1));
            mofuFace(g, r.getInt("pat"), lx + 5, ry + 1, 24);
            textClip(g, MofuData.name(r), lx + 31, ry + 3, lw - 36, uid == active ? GOLD : LIGHT, true);
            icon(g, typeIcon(MofuData.type(r)), lx + 29, ry + 11);
            g.drawString(this.font, "Lv" + MofuData.level(r), lx + 46, ry + 15, 0xFFD8C8A8, true);
            int status = MofuData.onExpedition(r) ? I_MAP : MofuData.fainted(r, now) ? I_CROSS : uid == active ? I_FLAG : -1;
            if (status >= 0) icon(g, status, lx + lw - 21, ry + 9);
            region(lx + 3, ry, lw - 6, ROW_H - 1, () -> {
                selUid = uid;
                confirmDismiss = false;
                retrainType = -1;
                stopRename();
            });
        }
        int free = MofuData.MAX_COMPANIONS - recs.size();
        for (int i = 0; i < free; i++) {
            int ry = ly + 3 + (recs.size() + i) * ROW_H;
            g.fill(lx + 5, ry + 2, lx + lw - 5, ry + ROW_H - 3, 0x20000000);
            text(g, Component.translatable("screen.huntcraft.mofu.empty_slot"), lx + 12, ry + 9, 0xFF6A5A48);
        }
        boolean stay = v.getBoolean("stay");
        button(g, lx, ly + lh - 20, lw, 18, Component.translatable(stay ? "screen.huntcraft.mofu.stay_on" : "screen.huntcraft.mofu.stay_off"), active > 0, () -> {
            CompoundTag t = new CompoundTag();
            t.putBoolean("value", !stay);
            send("stay", t);
        });
    }

    // ------------------------------------------------------------------ roster detail
    private void drawDetail(GuiGraphics g, CompoundTag rec, int x, int y, int w, int h, int mx, int my) {
        int uid = rec.getInt("uid");
        CompoundTag v = village();
        HunterData d = data();
        boolean active = v.getInt("active") == uid;
        boolean away = MofuData.onExpedition(rec);
        MofuType type = MofuData.type(rec);
        int lv = MofuData.level(rec);
        // preview column
        inset(g, x + 6, y + 6, 70, 100);
        preview(g, x + 41, y + 96, 54, previewFor(rec), mx, my);
        // name / type / level
        int cx = x + 82, cw = w - 88;
        if (!renaming) {
            textClip(g, MofuData.name(rec).copy().withStyle(ChatFormatting.BOLD), cx, y + 8, cw - 22, INK, false);
            iconButton(g, x + w - 26, y + 4, I_TAG, true, () -> startRename(rec, x, y));
        }
        icon(g, typeIcon(type), cx, y + 18);
        text(g, type.displayName(), cx + 18, y + 22, INK_SOFT);
        text(g, Component.literal("Lv " + lv + (lv >= MofuData.MAX_LEVEL ? " MAX" : "")), cx + 70, y + 22, 0xFF8A6A3A);
        int need = MofuData.xpForNext(lv);
        bar(g, cx, y + 34, cw - 6, 4, lv >= MofuData.MAX_LEVEL ? 1 : rec.getInt("xp") / (float) need, 0xFF7FD0FF, 0xFF3F90D0);
        // stats
        int sy = y + 44;
        statLine(g, I_HEART, Component.translatable("screen.huntcraft.mofu.stat_hp", fmt(MofuData.maxHealth(rec))), cx, sy);
        statLine(g, I_SHIELD, Component.translatable("screen.huntcraft.mofu.stat_def", fmt(MofuData.armor(rec))), cx + cw / 2, sy);
        sy += 16;
        statLine(g, type == MofuType.BOMBER ? I_BOMBER : I_SWORD, Component.translatable("screen.huntcraft.mofu.stat_atk", fmt(MofuData.attack(rec))), cx, sy);
        MofuGear.Tier wt = MofuGear.equippedTier(rec, MofuGear.Slot.WEAPON);
        if (wt != null && wt.element != com.huntcraft.core.Element.NONE) {
            text(g, wt.element.displayName(), cx + cw / 2 + 2, sy + 4, INK);
        }
        sy += 16;
        text(g, Component.translatable("screen.huntcraft.mofu.hunts", rec.getInt("hunts")), cx, sy + 2, INK_SOFT);
        // skills
        int ky = y + 108;
        rule(g, x + 6, ky - 3, w - 12);
        text(g, Component.translatable("screen.huntcraft.mofu.skills"), x + 8, ky, INK_SOFT);
        ky += 10;
        int col = 0;
        for (MofuSkill s : MofuSkill.forType(type)) {
            boolean has = s.has(type, lv);
            int sx = x + 8 + col * ((w - 16) / 2);
            int syy = ky;
            icon(g, has ? I_SPARKLE : I_LOCK, sx, syy - 3);
            Component name = has ? s.displayName() : Component.translatable("screen.huntcraft.mofu.skill_locked", s.displayName(), s.level);
            textClip(g, name, sx + 16, syy + 1, (w - 16) / 2 - 18, has ? 0xFF8A4A10 : 0xFFAA9A80, false);
            if (in(mx, my, sx, syy - 3, (w - 16) / 2, 12)) hoverText = s.description();
            col++;
            if (col == 2) {
                col = 0;
                ky += 12;
            }
        }
        // actions
        int by = y + h - 22;
        int bw = (w - 16) / 3;
        if (confirmDismiss) {
            textClip(g, Component.translatable("screen.huntcraft.mofu.dismiss_confirm", MofuData.name(rec)), x + 8, by - 12, w - 16, BAD_DARK, false);
            button(g, x + 8, by, bw, 18, Component.translatable("screen.huntcraft.mofu.yes"), true, () -> {
                send("dismiss", uid);
                confirmDismiss = false;
                selUid = 0;
            });
            button(g, x + 8 + bw + 2, by, bw, 18, Component.translatable("screen.huntcraft.mofu.no"), true, () -> confirmDismiss = false);
            return;
        }
        if (retrainType >= 0) {
            for (MofuType t : MofuType.values()) {
                if (t == type) continue;
                int bx = x + 8 + (t.ordinal() > type.ordinal() ? t.ordinal() - 1 : t.ordinal()) * (bw + 2);
                button(g, bx, by, bw, 18, t.displayName(), d.zenny >= MofuData.retrainCost(rec), () -> {
                    CompoundTag tt = new CompoundTag();
                    tt.putInt("uid", uid);
                    tt.putInt("type", t.ordinal());
                    send("retrain", tt);
                    retrainType = -1;
                });
            }
            button(g, x + 8 + 2 * (bw + 2), by, bw, 18, Component.translatable("screen.huntcraft.mofu.cancel"), true, () -> retrainType = -1);
            textClip(g, Component.translatable("screen.huntcraft.mofu.retrain_cost", MofuData.retrainCost(rec)), x + 8, by - 12, w - 16, INK_SOFT, false);
            return;
        }
        if (active) button(g, x + 8, by - 20, bw, 18, Component.translatable("screen.huntcraft.mofu.rest"), true, () -> send("rest", uid));
        else button(g, x + 8, by - 20, bw, 18, Component.translatable("screen.huntcraft.mofu.set_active"), !away, () -> send("active", uid));
        boolean maxed = lv >= MofuData.MAX_LEVEL;
        if (button(g, x + 8 + bw + 2, by - 20, bw, 18, Component.translatable("screen.huntcraft.mofu.train"), !maxed && d.zenny >= MofuData.trainCost(rec),
                () -> send("train", uid))) {
            hoverText = Component.translatable("screen.huntcraft.mofu.train_tip", MofuData.trainCost(rec), Math.round(MofuData.xpForNext(lv) * 0.4F));
        }
        button(g, x + 8 + 2 * (bw + 2), by - 20, bw, 18, Component.translatable("screen.huntcraft.mofu.retrain"), true, () -> retrainType = type.ordinal());
        button(g, x + 8 + 2 * (bw + 2), by, bw, 18, Component.translatable("screen.huntcraft.mofu.dismiss"), !away, () -> confirmDismiss = true);
        if (away) textClip(g, Component.translatable("screen.huntcraft.mofu.away"), x + 8, by + 5, 2 * bw, 0xFF3A6A9A, false);
        else if (active) textClip(g, Component.translatable("screen.huntcraft.mofu.following"), x + 8, by + 5, 2 * bw, OK_DARK, false);
    }

    private void statLine(GuiGraphics g, int iconIdx, Component c, int x, int y) {
        icon(g, iconIdx, x, y - 4);
        text(g, c, x + 17, y, INK);
    }

    private void startRename(CompoundTag rec, int x, int y) {
        renaming = true;
        nameBox.setVisible(true);
        nameBox.setX(x + 82);
        nameBox.setY(y + 4);
        nameBox.setWidth(110);
        nameBox.setValue(rec.contains("cname", Tag.TAG_STRING) ? rec.getString("cname") : MofuData.name(rec).getString());
        this.setFocused(nameBox);
        nameBox.setFocused(true);
    }

    private void stopRename() {
        renaming = false;
        if (nameBox != null) {
            nameBox.setVisible(false);
            nameBox.setFocused(false);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (renaming && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            CompoundTag t = new CompoundTag();
            t.putInt("uid", selUid);
            t.putString("name", nameBox.getValue());
            send("rename", t);
            stopRename();
            return true;
        }
        if (renaming && key == GLFW.GLFW_KEY_ESCAPE) {
            stopRename();
            return true;
        }
        if (nameBox != null && nameBox.isVisible() && nameBox.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            return nameBox.keyPressed(key, scan, mods) || true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ gear
    private void drawGear(GuiGraphics g, CompoundTag rec, int x, int y, int w, int h, int mx, int my) {
        Player p = player();
        if (p == null) return;
        int uid = rec.getInt("uid");
        inset(g, x + 6, y + 6, 70, 100);
        preview(g, x + 41, y + 96, 54, previewFor(rec), mx, my);
        textClip(g, MofuData.name(rec).copy().withStyle(ChatFormatting.BOLD), x + 82, y + 8, w - 88, INK, false);
        // equipped slots
        int sy = y + 22;
        for (MofuGear.Slot s : MofuGear.Slot.values()) {
            int ry = sy + s.ordinal() * 26;
            boolean sel = gearSlot == s.ordinal();
            rowHighlight(g, x + 80, ry, w - 86, 24, sel, in(mx, my, x + 80, ry, w - 86, 24));
            slot(g, x + 82, ry + 3);
            MofuGear.Tier t = MofuGear.equippedTier(rec, s);
            if (t != null) {
                ItemStack st = new ItemStack(MofuGear.item(s, t).get());
                item(g, st, x + 83, ry + 4);
                if (in(mx, my, x + 82, ry + 3, 18, 18)) hoverStack = st;
                textClip(g, st.getHoverName(), x + 104, ry + 4, w - 140, INK, false);
            } else {
                text(g, Component.translatable("screen.huntcraft.mofu.none"), x + 104, ry + 4, 0xFFAA9A80);
            }
            text(g, s.displayName(), x + 104, ry + 14, INK_SOFT);
            final int si = s.ordinal();
            region(x + 80, ry, w - 86, 24, () -> gearSlot = si);
            if (t != null) iconButton(g, x + w - 28, ry + 2, I_CROSS, true, () -> {
                CompoundTag tt = new CompoundTag();
                tt.putInt("uid", uid);
                tt.putString("slot", s.id);
                send("unequip", tt);
            });
        }
        // owned pieces for the selected slot
        MofuGear.Slot slot = MofuGear.Slot.values()[Math.max(0, Math.min(2, gearSlot))];
        int oy = y + 110;
        rule(g, x + 6, oy - 3, w - 12);
        text(g, Component.translatable("screen.huntcraft.mofu.owned_gear", slot.displayName()), x + 8, oy, INK_SOFT);
        oy += 11;
        int n = 0;
        for (MofuGear.Tier t : MofuGear.Tier.values()) {
            Item item = MofuGear.item(slot, t).get();
            int have = SmithingRegistry.count(p, item);
            if (have <= 0) continue;
            int gx = x + 8 + (n % 9) * 22, gy = oy + (n / 9) * 22;
            if (gy > y + h - 22) break;
            ItemStack st = new ItemStack(item, have);
            slot(g, gx, gy);
            itemWithCount(g, st, gx + 1, gy + 1);
            if (in(mx, my, gx, gy, 18, 18)) hoverStack = st;
            region(gx, gy, 18, 18, () -> {
                CompoundTag tt = new CompoundTag();
                tt.putInt("uid", uid);
                tt.putString("item", MofuGear.key(item));
                send("equip", tt);
            });
            n++;
        }
        if (n == 0) wrap(g, Component.translatable("screen.huntcraft.mofu.no_gear"), x + 8, oy + 2, w - 16, 0xFFAA9A80, 3);
    }

    // ------------------------------------------------------------------ hire
    private void drawHire(GuiGraphics g, int mx, int my) {
        CompoundTag v = village();
        HunterData d = data();
        int x = left + 10, y = top + 46, w = panelW - 20;
        boolean full = MofuData.list(v).size() >= MofuData.MAX_COMPANIONS;
        speech(g, Component.translatable(full ? "screen.huntcraft.mofu.hire_full" : "screen.huntcraft.mofu.hire_intro"), x, y, w);
        ListTag cands = MofuData.candidates(v);
        int cw = (w - 8) / 3;
        for (int i = 0; i < cands.size() && i < 3; i++) {
            CompoundTag c = cands.getCompound(i);
            int cx = x + i * (cw + 4), cy = y + 26;
            paper(g, cx, cy, cw, 128);
            mofuFace(g, c.getInt("pat"), cx + 6, cy + 6, 24);
            textClip(g, MofuData.name(c).copy().withStyle(ChatFormatting.BOLD), cx + 34, cy + 8, cw - 40, INK, false);
            icon(g, typeIcon(MofuData.type(c)), cx + 32, cy + 17);
            text(g, Component.literal("Lv" + MofuData.level(c)), cx + 50, cy + 21, 0xFF8A6A3A);
            int sy = cy + 36;
            text(g, MofuData.type(c).displayName(), cx + 6, sy, INK_SOFT);
            sy += 12;
            text(g, Component.translatable("screen.huntcraft.mofu.stat_hp", fmt(MofuData.maxHealth(c))), cx + 6, sy, INK);
            sy += 10;
            text(g, Component.translatable("screen.huntcraft.mofu.stat_atk", fmt(MofuData.attack(c))), cx + 6, sy, INK);
            sy += 12;
            for (MofuSkill s : MofuSkill.forType(MofuData.type(c))) {
                if (!s.has(MofuData.type(c), MofuData.level(c))) continue;
                textClip(g, s.displayName(), cx + 6, sy, cw - 12, 0xFF8A4A10, false);
                sy += 10;
                if (sy > cy + 96) break;
            }
            int cost = MofuData.hireCost(c);
            final int idx = i;
            button(g, cx + 4, cy + 104, cw - 8, 18, Component.translatable("screen.huntcraft.mofu.hire", String.format("%,d", cost)),
                    !full && d.zenny >= cost, () -> {
                        CompoundTag t = new CompoundTag();
                        t.putInt("idx", idx);
                        send("hire", t);
                    });
        }
        if (cands.isEmpty()) wrap(g, Component.translatable("screen.huntcraft.mofu.no_candidates"), x + 8, y + 40, w - 16, GREY, 3);
        button(g, x + w - 130, top + panelH - 26, 130, 18, Component.translatable("screen.huntcraft.mofu.refresh", CompanionManager.REFRESH_COST),
                d.zenny >= CompanionManager.REFRESH_COST, () -> send("refresh", new CompoundTag()));
    }

    // ------------------------------------------------------------------ expedition
    private void drawExpedition(GuiGraphics g, CompoundTag rec, int x, int y, int w, int h, int mx, int my) {
        int uid = rec.getInt("uid");
        HunterData d = data();
        textClip(g, MofuData.name(rec).copy().withStyle(ChatFormatting.BOLD), x + 8, y + 8, w - 60, INK, false);
        text(g, Component.literal("Lv" + MofuData.level(rec)), x + w - 40, y + 8, 0xFF8A6A3A);
        if (MofuData.onExpedition(rec)) {
            CompoundTag exp = rec.getCompound("exp");
            Expeditions.Destination de = Expeditions.get(exp.getInt("dest"));
            long dur = exp.contains("dur") ? exp.getLong("dur") : Expeditions.Length.SHORT.millis;
            long elapsed = System.currentTimeMillis() - exp.getLong("start");
            long remain = Math.max(0, dur - elapsed) / 1000L;
            int y2 = y + 26;
            icon(g, I_MAP, x + 8, y2);
            textClip(g, Component.translatable("screen.huntcraft.mofu.exp_at", de.displayName()), x + 28, y2 + 4, w - 36, INK, false);
            y2 += 22;
            text(g, Component.translatable("screen.huntcraft.mofu.exp_time", remain / 60, String.format("%02d", remain % 60)), x + 8, y2, INK_SOFT);
            bar(g, x + 8, y2 + 12, w - 16, 6, elapsed / (float) Math.max(1, dur), 0xFF8FD06A, 0xFF4F9A3A);
            y2 += 26;
            text(g, Component.translatable("screen.huntcraft.mofu.exp_hunts", exp.getInt("prog"), exp.getInt("need")), x + 8, y2, INK_SOFT);
            bar(g, x + 8, y2 + 12, w - 16, 6, exp.getInt("prog") / (float) Math.max(1, exp.getInt("need")), 0xFFF2C94A, 0xFFC08A20);
            wrap(g, Component.translatable("screen.huntcraft.mofu.exp_note"), x + 8, y2 + 26, w - 16, INK_SOFT, 3);
            button(g, x + 8, y + h - 24, w - 16, 18, Component.translatable("screen.huntcraft.mofu.recall"), true, () -> send("recall", uid));
            return;
        }
        // destinations
        int y2 = y + 22;
        for (int i = 0; i < Expeditions.ALL.size(); i++) {
            Expeditions.Destination de = Expeditions.ALL.get(i);
            boolean ok = MofuData.level(rec) >= de.minLevel() && d.hunterRank >= de.requiredHR();
            boolean sel = dest == i;
            int ry = y2 + i * 24;
            rowHighlight(g, x + 6, ry, w - 12, 22, sel, in(mx, my, x + 6, ry, w - 12, 22));
            g.fill(x + 9, ry + 3, x + 13, ry + 19, 0xFF000000 | de.color());
            textClip(g, de.displayName(), x + 18, ry + 3, w - 90, ok ? INK : 0xFFAA9A80, false);
            Component req = Component.translatable("screen.huntcraft.mofu.exp_req", de.minLevel(), de.requiredHR());
            text(g, req, x + 18, ry + 12, ok ? INK_SOFT : BAD_DARK);
            if (!ok) icon(g, I_LOCK, x + w - 26, ry + 3);
            if (in(mx, my, x + 6, ry, w - 12, 22)) hoverText = de.description();
            final int di = i;
            region(x + 6, ry, w - 12, 22, () -> dest = di);
        }
        Expeditions.Destination de = Expeditions.get(dest);
        int ly = y2 + Expeditions.ALL.size() * 24 + 4;
        text(g, Component.translatable("screen.huntcraft.mofu.exp_loot"), x + 8, ly, INK_SOFT);
        ly += 11;
        int n = 0;
        for (Expeditions.Loot l : de.loot()) {
            if (n >= 10) break;
            ItemStack st = new ItemStack(l.item().get());
            int lx = x + 8 + n * 18;
            item(g, st, lx, ly);
            if (in(mx, my, lx, ly, 16, 16)) hoverStack = st;
            n++;
        }
        // length toggle + send
        int by = y + h - 24;
        for (Expeditions.Length len : Expeditions.Length.values()) {
            final int li = len.ordinal();
            int bx = x + 8 + li * 52;
            if (length == li) nine(g, bx - 1, by - 23, 52, 20, 200, 0, 64, 16, 3);
            button(g, bx, by - 22, 50, 18, len.displayName(), length != li, () -> length = li);
        }
        boolean ok = MofuData.level(rec) >= de.minLevel() && d.hunterRank >= de.requiredHR();
        button(g, x + 8, by, w - 16, 18, Component.translatable("screen.huntcraft.mofu.send", de.displayName()), ok, () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("uid", uid);
            t.putInt("dest", dest);
            t.putInt("len", length);
            send("expedition", t);
        });
        // last loot
        if (rec.contains("lastLoot", Tag.TAG_LIST)) {
            ListTag ll = rec.getList("lastLoot", Tag.TAG_COMPOUND);
            int ly2 = ly + 20;
            text(g, Component.translatable("screen.huntcraft.mofu.last_loot"), x + 8, ly2, INK_SOFT);
            for (int i = 0; i < ll.size() && i < 10; i++) {
                ItemStack st = ItemStack.of(ll.getCompound(i));
                int lx = x + 8 + i * 18;
                itemWithCount(g, st, lx, ly2 + 10);
                if (in(mx, my, lx, ly2 + 10, 16, 16)) hoverStack = st;
            }
        }
    }

    @Override
    protected void drawTooltips(GuiGraphics g, int mx, int my) {
        if (hoverStack != null && !hoverStack.isEmpty()) g.renderTooltip(this.font, hoverStack, mx, my);
        else if (hoverText != null) g.renderTooltip(this.font, this.font.split(hoverText, 200), mx, my);
    }

    /** Used by tests / debugging: gear item of a slot+tier is a MofuGearItem. */
    static boolean isGear(ItemStack st) {
        return st.getItem() instanceof MofuGearItem;
    }

    /** Current client item box version (forces list refresh after equip). */
    static int boxVersion() {
        return ClientVillage.boxVersion;
    }
}
