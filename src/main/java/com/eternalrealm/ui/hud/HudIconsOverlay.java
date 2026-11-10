package com.eternalrealm.ui.hud;

import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.hud.HudLayout;
import com.eternalrealm.hud.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The equipment and bag icons in the bottom right corner, each with its key in the icon's bottom right corner
 * (equipment: {@link ClientKeyMappings#OPEN_EQUIPMENT}, bag: vanilla's inventory key).
 */
public final class HudIconsOverlay implements IGuiOverlay {
    public static final ResourceLocation BAG = ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "textures/gui/hud/bag.png");
    public static final ResourceLocation EQUIPMENT = ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID,
            "textures/gui/hud/equipment.png");
    private static final int PANEL = 0xB0141018;
    private static final int RIM = 0xFFC9A55A;
    private static final int RIM_DARK = 0xFF3A2C18;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.options.hideGui || minecraft.player == null || !HudFrame.mmo(minecraft.player)) {
            return;
        }
        HudLayout.Layout layout = HudFrame.layout();
        Font font = minecraft.font;
        icon(graphics, font, layout.equipment(), EQUIPMENT, ClientKeyMappings.OPEN_EQUIPMENT.getTranslatedKeyMessage());
        icon(graphics, font, layout.bag(), BAG, minecraft.options.keyInventory.getTranslatedKeyMessage());
    }

    private static void icon(GuiGraphics graphics, Font font, Rect box, ResourceLocation texture, Component key) {
        if (box.isEmpty()) {
            return;
        }
        graphics.fill(box.x() - 1, box.y() - 1, box.right() + 1, box.bottom() + 1, RIM_DARK);
        graphics.fill(box.x(), box.y(), box.right(), box.bottom(), PANEL);
        graphics.renderOutline(box.x(), box.y(), box.width(), box.height(), RIM);
        graphics.blit(texture, box.x() + (box.width() - 16) / 2, box.y() + (box.height() - 16) / 2 - 1, 0, 0, 16, 16, 16,
                16);
        // the key, small, in the icon's bottom right corner
        String text = key.getString();
        float scale = 0.75F;
        int width = font.width(text);
        int maxWidth = box.width() - 2;
        if (width * scale > maxWidth) {
            scale = Math.max(0.5F, maxWidth / (float) width);
        }
        float textWidth = width * scale;
        float textHeight = font.lineHeight * scale;
        float x = box.right() - 1 - textWidth;
        float y = box.bottom() - 1 - textHeight;
        graphics.fill((int) Math.floor(x) - 1, (int) Math.floor(y) - 1, box.right() - 1, box.bottom() - 1, 0xC0000000);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 200.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, 0xFFFFE08A, false);
        graphics.pose().popPose();
    }
}
