package com.huntcraft.quest.client;

import com.huntcraft.network.HCNetwork;
import com.huntcraft.quest.QuestDef;
import com.huntcraft.quest.QuestHudState;
import com.huntcraft.quest.QuestMap;
import com.huntcraft.quest.Quests;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/** Full map of the current quest region: numbered areas, base camp, party, painted / slain targets. */
@OnlyIn(Dist.CLIENT)
public class QuestMapScreen extends Screen {
    private boolean confirmRetire;

    public QuestMapScreen() {
        super(Component.translatable("screen.huntcraft.quest_map"));
    }

    @Override
    protected void init() {
        int s = Math.max(96, Math.min(width - 40, height - 56));
        int x0 = (width - s) / 2;
        if (QuestHudState.active && QuestHudState.state == 0) {
            addRenderableWidget(Button.builder(Component.translatable("button.huntcraft.quest.abandon"), b -> {
                if (!confirmRetire) {
                    confirmRetire = true;
                    b.setMessage(Component.translatable("button.huntcraft.quest.abandon_confirm"));
                } else {
                    HCNetwork.sendToServer("quest/abandon", new CompoundTag());
                    onClose();
                }
            }).bounds(x0 + s - 96, 6, 96, 16).build());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_E || QuestClient.QUEST_MAP.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Minecraft mc = Minecraft.getInstance();
        int s = Math.max(96, Math.min(width - 40, height - 56));
        int x0 = (width - s) / 2, y0 = 28;
        QuestUi.frame(g, x0 - 6, y0 - 22, s + 12, s + 44, QuestUi.BG);
        QuestDef q = Quests.get(QuestHudState.questId);
        QuestMap map = QuestMap.byId(QuestHudState.mapId);
        Component head = map != null ? map.displayName() : title;
        g.drawCenteredString(font, head, width / 2, y0 - 16, QuestUi.GOLD);
        g.blit(QuestUi.mapTexture(QuestHudState.mapId), x0, y0, s, s, 0.0F, 0.0F, 256, 256, 256, 256);
        g.renderOutline(x0 - 1, y0 - 1, s + 2, s + 2, 0xFF000000);
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        // area labels
        for (QuestHudState.AreaInfo a : QuestHudState.AREAS) {
            int[] p = toScreen(a.x() + 0.5, a.z() + 0.5, x0, y0, s);
            boolean here = a.n() == QuestHudState.area;
            String num = String.valueOf(a.n());
            int r = 7;
            g.fill(p[0] - r, p[1] - r, p[0] + r, p[1] + r, 0xC0000000);
            g.renderOutline(p[0] - r, p[1] - r, r * 2, r * 2, here ? 0xFFFFE070 : QuestUi.GOLD_DARK);
            g.drawCenteredString(font, num, p[0], p[1] - 4, here ? 0xFFFFE070 : QuestUi.TEXT);
            Component name = Component.translatable("area.huntcraft." + a.key());
            int tw = font.width(name);
            g.fill(p[0] - tw / 2 - 2, p[1] + r + 1, p[0] + tw / 2 + 2, p[1] + r + 11, 0x90000000);
            g.drawCenteredString(font, name, p[0], p[1] + r + 2, QuestUi.climateColor(a.climate()));
        }
        // camp
        int[] c = toScreen(QuestHudState.campX + 0.5, QuestHudState.campZ + 0.5, x0, y0, s);
        g.fill(c[0] - 4, c[1] - 4, c[0] + 4, c[1] + 4, 0xFF000000);
        g.fill(c[0] - 3, c[1] - 3, c[0] + 3, c[1] + 3, 0xFF5CD65C);
        // targets
        for (QuestHudState.Marker m : QuestHudState.MONSTERS) {
            int[] p = toScreen(m.x(), m.z(), x0, y0, s);
            int col = m.dead() ? 0xFF909090 : m.enraged() && now / 5 % 2 == 0 ? 0xFFFF2020 : 0xFFE060E0;
            g.fill(p[0] - 4, p[1] - 4, p[0] + 4, p[1] + 4, 0xFF000000);
            g.fill(p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3, col);
            if (mouseX >= p[0] - 4 && mouseX <= p[0] + 4 && mouseY >= p[1] - 4 && mouseY <= p[1] + 4) {
                g.renderTooltip(font, Component.translatable("entity.huntcraft." + m.id()), mouseX, mouseY);
            }
        }
        // party
        for (QuestHudState.Mate m : QuestHudState.PARTY) {
            int[] p = toScreen(m.x(), m.z(), x0, y0, s);
            g.fill(p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3, 0xFF000000);
            g.fill(p[0] - 2, p[1] - 2, p[0] + 2, p[1] + 2, 0xFF60E0FF);
            g.drawString(font, m.name(), p[0] + 5, p[1] - 4, 0xFF60E0FF, true);
        }
        // me
        if (mc.player != null) {
            int[] p = toScreen(mc.player.getX(), mc.player.getZ(), x0, y0, s);
            g.pose().pushPose();
            g.pose().translate(p[0], p[1], 0);
            g.pose().scale(1.4F, 1.4F, 1.0F);
            g.pose().mulPose(Axis.ZP.rotationDegrees(mc.player.getYRot() + 180.0F));
            QuestHudOverlay.arrow(g, 0xFF000000, 1);
            QuestHudOverlay.arrow(g, 0xFFFFFFFF, 0);
            g.pose().popPose();
        }
        // legend
        int ly = y0 + s + 5;
        Component legend = Component.translatable("screen.huntcraft.quest_map.legend");
        g.drawCenteredString(font, legend, width / 2, ly, QuestUi.TEXT_DIM);
        if (q != null) g.drawString(font, q.title(), x0, y0 - 16, QuestUi.TEXT, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static int[] toScreen(double x, double z, int x0, int y0, int s) {
        float u = QuestHudOverlay.texU(x, QuestHudState.centerX) / 256.0F;
        float v = QuestHudOverlay.texU(z, QuestHudState.centerZ) / 256.0F;
        return new int[]{Math.round(x0 + u * s), Math.round(y0 + v * s)};
    }
}
