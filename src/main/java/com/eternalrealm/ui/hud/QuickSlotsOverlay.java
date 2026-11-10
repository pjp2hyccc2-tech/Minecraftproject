package com.eternalrealm.ui.hud;

import com.eternalrealm.client.QuickSlots;
import com.eternalrealm.hud.HudLayout;
import com.eternalrealm.hud.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The quick-use items at the right end of the skill bar: the item each number key would use now (count = how many
 * the player carries), its cooldown, and the key. A slot without the item shows it dimmed with 0.
 */
public final class QuickSlotsOverlay implements IGuiOverlay {
    private static final int PANEL = 0x90000000;
    private static final int EMPTY_SHADE = 0xA0101014;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.screen != null || !HudFrame.mmo(player)) {
            return;
        }
        HudLayout.Layout layout = HudFrame.layout();
        Font font = minecraft.font;
        for (int i = 0; i < QuickSlots.COUNT && i < layout.quickSlots().size(); i++) {
            Rect box = layout.quickSlots().get(i);
            QuickSlots.Pick pick = QuickSlots.pick(player, i);
            graphics.fill(box.x(), box.y(), box.right(), box.bottom(), PANEL);
            graphics.renderOutline(box.x(), box.y(), box.width(), box.height(), 0x60C9A55A);
            int itemX = box.x() + 3;
            int itemY = box.y() + 3;
            graphics.renderItem(pick.shown(), itemX, itemY);
            if (pick.inventorySlot() < 0) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                graphics.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, EMPTY_SHADE);
                graphics.drawString(font, "0", box.right() - font.width("0") - 1, box.bottom() - font.lineHeight,
                        0xFF8A8A8A, true);
                graphics.pose().popPose();
            } else {
                graphics.renderItemDecorations(font, pick.shown(), itemX, itemY);
            }
            Component key = minecraft.options.keyHotbarSlots[i].getTranslatedKeyMessage();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 300.0F);
            graphics.drawString(font, key, box.x() + 1, box.y() + 1, 0xFFE8C66A, true);
            graphics.pose().popPose();
        }
    }
}
