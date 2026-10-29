package com.huntcraft.gear.weapon.client;

import com.huntcraft.client.hud.HunterHud;
import com.huntcraft.core.Sharpness;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.gear.weapon.DualBladesItem;
import com.huntcraft.gear.weapon.GreatSwordItem;
import com.huntcraft.gear.weapon.HornSongs;
import com.huntcraft.gear.weapon.HunterBowItem;
import com.huntcraft.gear.weapon.HunterWeaponItem;
import com.huntcraft.gear.weapon.LanceItem;
import com.huntcraft.gear.weapon.LongSwordItem;
import com.huntcraft.gear.weapon.SharpnessProfile;
import com.huntcraft.gear.weapon.WeaponFx;
import com.huntcraft.gear.weapon.WeaponState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/**
 * Weapon HUD below the health / stamina bars: weapon icon + level, sharpness gauge, class gauges (charge pips,
 * spirit gauge + level, demon gauge, horn note queue, bow shot type + coating, guard), song buffs and the
 * sharpening progress bar.
 */
@OnlyIn(Dist.CLIENT)
public final class WeaponHud {
    private static final int PANEL_W = 136;
    private static final int GAUGE_W = 100;

    private WeaponHud() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || p.isSpectator()) return;
        WeaponState s = WeaponState.get(p);
        int x = HunterHud.X;
        int y = HunterHud.NEXT_Y + 2;
        ItemStack st = p.getMainHandItem();
        if (st.getItem() instanceof HunterWeaponItem w) {
            y = drawWeapon(g, mc, p, st, w, s, x, y) + 4;
        }
        drawBuffs(g, mc.font, s, x, y);
        if (s.sharpenLeft > 0 && s.sharpenTotal > 0) drawSharpening(g, mc.font, s, width, height);
    }

    private static int dim(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 255) * f);
        int gg = (int) (((rgb >> 8) & 255) * f);
        int b = (int) ((rgb & 255) * f);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int bg) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, bg);
    }

    private static int drawWeapon(GuiGraphics g, Minecraft mc, LocalPlayer p, ItemStack st, HunterWeaponItem w, WeaponState s, int x, int y) {
        Font font = mc.font;
        int h = 30;
        g.fill(x - 3, y - 2, x + PANEL_W, y + h, 0x88000000);
        // weapon icon in a frame with the level
        frame(g, x, y, 18, 18, 0xFF2B2418);
        g.fill(x, y, x + 18, y + 1, 0xFF6B5A3A);
        g.renderItem(st, x + 1, y + 1);
        g.drawString(font, "Lv" + w.getLevel(st), x, y + 20, 0xFFE8C878, true);
        int gx = x + 24;
        if (w.usesSharpness()) {
            drawSharpness(g, p, st, w, gx, y + 1);
        } else if (w instanceof HunterBowItem) {
            drawBowInfo(g, font, p, st, w, gx, y);
        }
        int ry = y + 12;
        WeaponClass c = w.weaponClass();
        switch (c) {
            case GREAT_SWORD, HAMMER, BOW -> drawCharge(g, font, p, w, s, gx, ry);
            case LONG_SWORD -> drawSpirit(g, font, s, gx, ry);
            case DUAL_BLADES -> drawDemon(g, font, p, s, gx, ry);
            case HUNTING_HORN -> drawNotes(g, font, w, s, gx, ry);
            case LANCE, SWORD_AND_SHIELD -> drawGuard(g, font, p, w, s, gx, ry);
        }
        return y + h;
    }

    // ------------------------------------------------------------------ sharpness
    private static void drawSharpness(GuiGraphics g, LocalPlayer p, ItemStack st, HunterWeaponItem w, int x, int y) {
        int[] seg = w.sharpnessSegments(st, p);
        int[] max = w.line.sharpMax;
        int pts = w.sharpnessPoints(st, p);
        int total = Math.max(1, SharpnessProfile.total(max));
        int hgt = 6;
        frame(g, x, y, GAUGE_W, hgt, 0xFF151515);
        int acc = 0;
        int px = x;
        int curColor = SharpnessProfile.colorIndex(seg, pts);
        for (int i = 0; i < SharpnessProfile.COLORS; i++) {
            int col = Sharpness.byIndex(i).color;
            int len = seg[i];
            if (len > 0) {
                int x0 = x + Math.round(acc * GAUGE_W / (float) total);
                int x1 = x + Math.round((acc + len) * GAUGE_W / (float) total);
                g.fill(x0, y, x1, y + hgt, dim(col, 0.28F));
                int filled = Math.max(0, Math.min(len, pts - acc));
                if (filled > 0) {
                    int xf = x + Math.round((acc + filled) * GAUGE_W / (float) total);
                    g.fillGradient(x0, y, xf, y + hgt, 0xFF000000 | col, dim(col, 0.75F));
                    if (i == curColor) px = xf;
                }
                acc += len;
            }
        }
        // potential unlocked by Handicraft / upgrades: hatched
        int potentialEnd = x + GAUGE_W;
        int accEnd = x + Math.round(acc * GAUGE_W / (float) total);
        for (int xx = accEnd; xx < potentialEnd; xx += 2) g.fill(xx, y + 2, xx + 1, y + hgt - 2, 0xFF3A3A3A);
        // marker on the current edge
        g.fill(px - 1, y - 2, px, y + hgt + 2, 0xFFFFFFFF);
        g.fill(x, y, x + GAUGE_W, y + 1, 0x33FFFFFF);
    }

    private static void drawBowInfo(GuiGraphics g, Font font, LocalPlayer p, ItemStack st, HunterWeaponItem w, int x, int y) {
        Component shot = Component.translatable("shot.huntcraft." + HunterBowItem.shotKey(w.line.shot));
        g.drawString(font, shot, x, y + 1, 0xFFB8E0FF, true);
        int coat = HunterBowItem.getCoating(st);
        Item it = HunterBowItem.coatingItem(coat);
        int sx = x + font.width(shot) + 6;
        if (it != null) {
            int color = coat == HunterBowItem.COAT_POWER ? 0xFFFF6A4A : coat == HunterBowItem.COAT_POISON ? 0xFFB070F0 : 0xFFF2D21B;
            g.fill(sx, y + 1, sx + 6, y + 8, 0xFF000000);
            g.fill(sx + 1, y + 2, sx + 5, y + 7, color);
            String txt = p.getAbilities().instabuild ? "∞" : String.valueOf(HunterBowItem.countCoating(p, coat));
            g.drawString(font, txt, sx + 9, y + 1, 0xFFFFFFFF, true);
        } else {
            g.drawString(font, Component.translatable("tooltip.huntcraft.weapon.coat_none"), sx, y + 1, 0xFF9A9A9A, true);
        }
    }

    // ------------------------------------------------------------------ class gauges
    private static void drawCharge(GuiGraphics g, Font font, LocalPlayer p, HunterWeaponItem w, WeaponState s, int x, int y) {
        boolean charging = p.isUsingItem() && p.getUseItem().getItem() == w && s.useMode == WeaponState.MODE_CHARGE;
        int used = charging ? p.getTicksUsingItem() : 0;
        int lv = charging ? w.chargeLevel(p, used) : 0;
        boolean over = charging && w instanceof GreatSwordItem gs && gs.isOvercharged(p, used);
        for (int i = 1; i <= 3; i++) {
            int bx = x + (i - 1) * 16;
            frame(g, bx, y, 13, 6, 0xFF1E1E1E);
            float prog;
            if (lv >= i) prog = 1.0F;
            else if (charging && lv == i - 1) {
                int from = i == 1 ? 0 : w.chargeTicksFor(p, i - 1);
                int to = w.chargeTicksFor(p, i);
                prog = Math.max(0, Math.min(1, (used - from) / (float) Math.max(1, to - from)));
            } else prog = 0;
            int col = 0xFF000000 | WeaponFx.chargeColor(i);
            if (over) col = (p.tickCount / 3) % 2 == 0 ? 0xFF802020 : 0xFFFF3020;
            if (prog > 0) g.fill(bx, y, bx + Math.round(13 * prog), y + 6, prog >= 1 ? col : dim(col, 0.55F));
        }
        if (charging && lv >= 3 && !over && (p.tickCount / 4) % 2 == 0) {
            g.drawString(font, "MAX", x + 50, y - 1, 0xFFFF5030, true);
        } else if (over) {
            g.drawString(font, Component.translatable("hud.huntcraft.weapon.overcharge"), x + 50, y - 1, 0xFFB0B0B0, true);
        } else if (w.guardPower() > 0 && w.isGuarding(p)) {
            g.drawString(font, Component.translatable("hud.huntcraft.weapon.guard"), x + 50, y - 1, 0xFF9FD0FF, true);
        }
    }

    private static void drawSpirit(GuiGraphics g, Font font, WeaponState s, int x, int y) {
        int bw = 74;
        frame(g, x, y, bw, 6, 0xFF1E1010);
        int fill = Math.round(bw * Math.max(0, Math.min(100, s.spirit)) / 100.0F);
        boolean full = s.spirit >= 100.0F;
        g.fillGradient(x, y, x + fill, y + 6, full ? 0xFFFF6A50 : 0xFFE0402A, 0xFF8A1A10);
        // level box
        int lx = x + bw + 6;
        int lc = LongSwordItem.LEVEL_COLORS[Math.max(0, Math.min(3, s.spiritLevel))];
        frame(g, lx, y - 1, 8, 8, 0xFF000000 | dim(lc, s.spiritLevel > 0 ? 1.0F : 0.4F));
        if (s.spiritLevel > 0) {
            int tw = Math.round(14 * Math.min(1.0F, s.spiritLevelTicks / (float) LongSwordItem.LEVEL_DURATION));
            g.fill(lx + 11, y + 2, lx + 11 + tw, y + 4, 0xFF000000 | lc);
        }
        if (s.foresightTicks > 0) g.drawString(font, "!", lx + 28, y - 1, 0xFFFF5040, true);
    }

    private static void drawDemon(GuiGraphics g, Font font, LocalPlayer p, WeaponState s, int x, int y) {
        int bw = 74;
        frame(g, x, y, bw, 6, 0xFF1E1010);
        int fill = Math.round(bw * Math.max(0, Math.min(100, s.demonGauge)) / 100.0F);
        int c1 = s.archdemon ? ((p.tickCount / 4) % 2 == 0 ? 0xFFFFC040 : 0xFFFF9020) : 0xFFFF7A2A;
        g.fillGradient(x, y, x + fill, y + 6, c1, 0xFF9A2A10);
        Component label = s.demon ? Component.translatable("hud.huntcraft.weapon.demon")
                : s.archdemon ? Component.translatable("hud.huntcraft.weapon.archdemon") : null;
        if (label != null) g.drawString(font, label, x + bw + 5, y - 1, s.demon ? 0xFFFF4030 : 0xFFFFB040, true);
        if (s.demon) {
            int pulse = (p.tickCount / 5) % 2 == 0 ? 0x66FF2010 : 0x33FF2010;
            g.fill(x - 1, y - 1, x + bw + 1, y + 7, pulse);
        }
    }

    private static void drawNotes(GuiGraphics g, Font font, HunterWeaponItem w, WeaponState s, int x, int y) {
        for (int i = 0; i < 4; i++) {
            int nx = x + i * 11;
            g.fill(nx, y - 1, nx + 9, y + 8, 0xFF000000);
            if (i < s.noteCount) {
                int col = 0xFF000000 | HornSongs.noteColor(w.line, s.notes[i]);
                g.fill(nx + 1, y, nx + 8, y + 7, col);
                g.fill(nx + 2, y + 1, nx + 4, y + 3, 0x88FFFFFF);
                g.drawString(font, String.valueOf(s.notes[i]), nx + 3, y, 0xFF101010, false);
            } else {
                g.fill(nx + 1, y, nx + 8, y + 7, 0xFF2A2A2A);
            }
        }
        List<HornSongs.Song> songs = HornSongs.match(w.line, s.notes, s.noteCount);
        if (!songs.isEmpty()) {
            HornSongs.Song song = songs.get(0);
            g.drawString(font, Component.translatable("song.huntcraft." + HornSongs.EFFECT_KEYS[song.effect()]), x + 47, y, 0xFF000000 | HornSongs.EFFECT_COLORS[song.effect()], true);
        }
    }

    private static void drawGuard(GuiGraphics g, Font font, LocalPlayer p, HunterWeaponItem w, WeaponState s, int x, int y) {
        boolean guarding = w.isGuarding(p);
        int col = guarding ? 0xFF9FD0FF : 0xFF5A5A5A;
        // small shield icon
        g.fill(x, y - 1, x + 7, y + 6, 0xFF000000);
        g.fill(x + 1, y, x + 6, y + 5, col);
        g.fill(x + 2, y + 5, x + 5, y + 7, col);
        if (guarding) g.drawString(font, Component.translatable("hud.huntcraft.weapon.guard"), x + 11, y - 1, col, true);
        if (w instanceof LanceItem && s.dashTicks > 0) {
            int bw = 50;
            frame(g, x + 60, y, bw, 5, 0xFF1E1E1E);
            g.fill(x + 60, y, x + 60 + Math.round(bw * s.dashTicks / (float) LanceItem.DASH_TICKS), y + 5, 0xFFFFB040);
        }
    }

    // ------------------------------------------------------------------ buffs & sharpening
    private static void drawBuffs(GuiGraphics g, Font font, WeaponState s, int x, int y) {
        int yy = y;
        for (int i = 0; i < HornSongs.EFFECT_COUNT; i++) {
            if (s.buffTicks[i] <= 0) continue;
            int col = 0xFF000000 | HornSongs.EFFECT_COLORS[i];
            g.fill(x - 3, yy - 1, x + PANEL_W, yy + 10, 0x66000000);
            g.fill(x, yy + 1, x + 6, yy + 7, col);
            Component name = Component.translatable("song.huntcraft." + HornSongs.EFFECT_KEYS[i]);
            if (s.buffLevel[i] >= 2) name = name.copy().append(" +");
            g.drawString(font, name, x + 10, yy, col, true);
            String secs = (s.buffTicks[i] + 19) / 20 + "s";
            g.drawString(font, secs, x + PANEL_W - 4 - font.width(secs), yy, 0xFFDDDDDD, true);
            yy += 11;
        }
    }

    private static void drawSharpening(GuiGraphics g, Font font, WeaponState s, int width, int height) {
        int bw = 90;
        int bx = width / 2 - bw / 2;
        int by = height - 66;
        Component label = Component.translatable("hud.huntcraft.weapon.sharpening");
        g.drawCenteredString(font, label, width / 2, by - 11, 0xFFFFFFFF);
        frame(g, bx, by, bw, 5, 0xFF1A1A1A);
        float prog = 1.0F - s.sharpenLeft / (float) Math.max(1, s.sharpenTotal);
        g.fillGradient(bx, by, bx + Math.round(bw * prog), by + 5, 0xFFFFFFFF, 0xFF8AB8FF);
    }

    /** Used by the HUD and tooltips: true while dual blades demon mode is on. */
    public static boolean demon(LocalPlayer p) {
        return p.getMainHandItem().getItem() instanceof DualBladesItem && WeaponState.get(p).demon;
    }
}
