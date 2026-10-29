package com.huntcraft.quest.client;

import com.huntcraft.quest.QuestDef;
import com.huntcraft.quest.QuestHudState;
import com.huntcraft.quest.Quests;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;

/** Quest HUD (top-right): quest name, objectives, timer, faints, current area / climate and a minimap. */
@OnlyIn(Dist.CLIENT)
public final class QuestHudOverlay {
    private static final int W = 176;
    private static final int MINIMAP = 84;

    private QuestHudOverlay() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !QuestHudState.active || mc.screen instanceof QuestMapScreen) return;
        QuestDef q = Quests.get(QuestHudState.questId);
        if (q == null) return;
        Font font = mc.font;
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        int x = width - W - 6;
        int y = 6;
        List<Line> lines = new ArrayList<>();
        // title
        MutableComponent title = Component.literal(QuestUi.stars(q.stars) + " ").withStyle(s -> s.withColor(0xFFD84A))
                .append(q.title().copy().withStyle(s -> s.withColor(0xF2E8D2)));
        for (FormattedCharSequence l : font.split(title, W - 10)) lines.add(new Line(l, 0xFFFFFFFF));
        // objectives
        for (int i = 0; i < q.targets.size(); i++) {
            QuestDef.Target t = q.targets.get(i);
            int have = i < QuestHudState.progress.length ? QuestHudState.progress[i] : 0;
            boolean done = have >= t.count();
            MutableComponent c = Component.literal(done ? "✔ " : "◆ ").append(t.name()).append(" " + Math.min(have, t.count()) + "/" + t.count());
            int area = i < QuestHudState.targetAreas.length ? QuestHudState.targetAreas[i] : 0;
            if (!done && area > 0 && !t.isItem()) c.append(Component.literal("  ")).append(Component.translatable("hud.huntcraft.quest.target_area", area));
            lines.add(new Line(c.getVisualOrderText(), done ? QuestUi.GREEN : QuestUi.TEXT));
        }
        int panelH = 6 + lines.size() * 10 + 24;
        QuestUi.frame(g, x, y, W, panelH, 0xC8181410);
        int ty = y + 5;
        for (Line l : lines) {
            g.drawString(font, l.text, x + 6, ty, l.color, true);
            ty += 10;
        }
        // timer / state + faints
        int rem = QuestHudState.remainingNow(now);
        String timeStr = QuestUi.time(rem);
        Component state;
        int stateColor;
        if (QuestHudState.state == 1) {
            state = Component.translatable("hud.huntcraft.quest.clear", timeStr);
            stateColor = 0xFFFFD84A;
        } else if (QuestHudState.state == 2) {
            state = Component.translatable("hud.huntcraft.quest.failed", timeStr);
            stateColor = QuestUi.RED;
        } else {
            state = Component.translatable("hud.huntcraft.quest.time", timeStr);
            stateColor = rem < 20 * 60 * 5 ? (now / 10 % 2 == 0 ? QuestUi.RED : 0xFFFFB0A0) : QuestUi.TEXT;
        }
        g.drawString(font, state, x + 6, ty + 1, stateColor, true);
        // faint icons (filled = used)
        int fx = x + W - 8 - QuestHudState.maxFaints * 9;
        for (int i = 0; i < QuestHudState.maxFaints; i++) {
            int ix = fx + i * 9, iy = ty + 1;
            boolean used = i < QuestHudState.faints;
            g.fill(ix, iy, ix + 7, iy + 7, 0xFF000000);
            g.fill(ix + 1, iy + 1, ix + 6, iy + 6, used ? 0xFFB02A20 : 0xFFE8DCC0);
            if (used) {
                g.fill(ix + 2, iy + 2, ix + 5, iy + 3, 0xFF000000);
                g.fill(ix + 3, iy + 3, ix + 4, iy + 5, 0xFF000000);
            }
        }
        // area + climate
        int area = QuestHudState.area;
        Component areaText;
        if (QuestHudState.climate == 3 && area == 1) areaText = Component.translatable("hud.huntcraft.quest.camp");
        else if (area > 0) areaText = Component.translatable("hud.huntcraft.quest.area", area).append(" ")
                .append(Component.translatable("area.huntcraft." + QuestHudState.areaKey(area)));
        else areaText = Component.literal("-");
        g.drawString(font, areaText, x + 6, ty + 12, QuestUi.TEXT_DIM, true);
        if (QuestHudState.climate == 1 || QuestHudState.climate == 2) {
            Component cl = Component.translatable(QuestHudState.climate == 1 ? "hud.huntcraft.quest.cold" : "hud.huntcraft.quest.hot");
            g.drawString(font, cl, x + W - 8 - font.width(cl), ty + 12, QuestUi.climateColor(QuestHudState.climate), true);
        }
        renderMinimap(g, mc, p, width - MINIMAP - 8, y + panelH + 5, now);
    }

    private record Line(FormattedCharSequence text, int color) {
    }

    private static void renderMinimap(GuiGraphics g, Minecraft mc, LocalPlayer p, int mx, int my, long now) {
        int ms = MINIMAP;
        QuestUi.frame(g, mx - 2, my - 2, ms + 4, ms + 4, 0xFF000000);
        float scale = 1.7F;
        float tu = texU(p.getX(), QuestHudState.centerX);
        float tv = texU(p.getZ(), QuestHudState.centerZ);
        int dx = Math.round(mx + ms / 2.0F - tu * scale);
        int dy = Math.round(my + ms / 2.0F - tv * scale);
        int size = Math.round(256 * scale);
        g.enableScissor(mx, my, mx + ms, my + ms);
        g.blit(QuestUi.mapTexture(QuestHudState.mapId), dx, dy, size, size, 0.0F, 0.0F, 256, 256, 256, 256);
        // camp
        int[] c = toMini(QuestHudState.campX + 0.5, QuestHudState.campZ + 0.5, mx, my, ms, tu, tv, scale);
        g.fill(c[0] - 3, c[1] - 3, c[0] + 3, c[1] + 3, 0xFF000000);
        g.fill(c[0] - 2, c[1] - 2, c[0] + 2, c[1] + 2, 0xFF5CD65C);
        for (QuestHudState.Mate m : QuestHudState.PARTY) {
            int[] s = toMini(m.x(), m.z(), mx, my, ms, tu, tv, scale);
            g.fill(s[0] - 2, s[1] - 2, s[0] + 2, s[1] + 2, 0xFF000000);
            g.fill(s[0] - 1, s[1] - 1, s[0] + 1, s[1] + 1, 0xFF60E0FF);
        }
        for (QuestHudState.Marker m : QuestHudState.MONSTERS) {
            int[] s = toMini(m.x(), m.z(), mx, my, ms, tu, tv, scale);
            int col = m.dead() ? 0xFF909090 : m.enraged() && now / 5 % 2 == 0 ? 0xFFFF2020 : 0xFFE060E0;
            g.fill(s[0] - 3, s[1] - 3, s[0] + 3, s[1] + 3, 0xFF000000);
            g.fill(s[0] - 2, s[1] - 2, s[0] + 2, s[1] + 2, col);
        }
        g.disableScissor();
        // player arrow (north = up)
        g.pose().pushPose();
        g.pose().translate(mx + ms / 2.0F, my + ms / 2.0F, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(p.getYRot() + 180.0F));
        arrow(g, 0xFF000000, 1);
        arrow(g, 0xFFFFFFFF, 0);
        g.pose().popPose();
        Component n = Component.literal("N");
        g.drawString(mc.font, n, mx + ms / 2 - 2, my + 2, 0xFFFFE080, true);
    }

    /** Small arrow pointing up (drawn in the current pose). */
    static void arrow(GuiGraphics g, int color, int grow) {
        g.fill(-1 - grow, -5 - grow, 1 + grow, 3 + grow, color);
        g.fill(-2 - grow, -3 - grow, 2 + grow, -1 + grow, color);
        g.fill(-3 - grow, -1 - grow, 3 + grow, 1 + grow, color);
    }

    /** Texture pixel (0..256) of a world coordinate along one axis of the region. */
    static float texU(double world, int center) {
        double u = world - (center - 256);
        return (float) ((u - QuestUi.MAP_U0) * 256.0 / QuestUi.MAP_SPAN);
    }

    private static int[] toMini(double x, double z, int mx, int my, int ms, float tu, float tv, float scale) {
        float px = texU(x, QuestHudState.centerX), pz = texU(z, QuestHudState.centerZ);
        return new int[]{Math.round(mx + ms / 2.0F + (px - tu) * scale), Math.round(my + ms / 2.0F + (pz - tv) * scale)};
    }
}
