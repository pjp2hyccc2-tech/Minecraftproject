package com.huntcraft.village.client.screen;

import com.huntcraft.village.menu.ItemBoxContainer;
import com.huntcraft.village.menu.ItemBoxMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Item box (アイテムボックス): 2 pages of 54 slots + the player inventory, with page / deposit-all / sort buttons. */
@OnlyIn(Dist.CLIENT)
public class ItemBoxScreen extends AbstractContainerScreen<ItemBoxMenu> {
    private static final int BTN = 20;

    public ItemBoxScreen(ItemBoxMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        nine(g, x, y, imageWidth, imageHeight, 0, 0, 32, 32, 8);
        // box slots (on a darker inset) + player inventory
        nine(g, x + 6, y + 16, 164, 112, 64, 0, 16, 16, 4);
        for (int r = 0; r < ItemBoxMenu.ROWS; r++) {
            for (int c = 0; c < 9; c++) g.blit(VillageScreen.WIDGETS, x + 7 + c * 18, y + 17 + r * 18, 80, 0, 18, 18, VillageScreen.ATLAS, VillageScreen.ATLAS);
        }
        int y0 = 18 + ItemBoxMenu.ROWS * 18 + 13;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) g.blit(VillageScreen.WIDGETS, x + 7 + c * 18, y + y0 - 1 + r * 18, 80, 0, 18, 18, VillageScreen.ATLAS, VillageScreen.ATLAS);
        }
        for (int c = 0; c < 9; c++) g.blit(VillageScreen.WIDGETS, x + 7 + c * 18, y + y0 + 57, 80, 0, 18, 18, VillageScreen.ATLAS, VillageScreen.ATLAS);
        // side buttons
        int bx = x + imageWidth + 2;
        sideButton(g, bx, y + 8, VillageScreen.I_LEFT, mx, my);
        sideButton(g, bx, y + 30, VillageScreen.I_RIGHT, mx, my);
        sideButton(g, bx, y + 60, VillageScreen.I_DEPOSIT, mx, my);
        sideButton(g, bx, y + 82, VillageScreen.I_SORT, mx, my);
    }

    private void nine(GuiGraphics g, int x, int y, int w, int h, int u, int v, int tw, int th, int b) {
        int cw = tw - 2 * b, ch = th - 2 * b, iw = w - 2 * b, ih = h - 2 * b;
        int a = VillageScreen.ATLAS;
        g.blit(VillageScreen.WIDGETS, x, y, u, v, b, b, a, a);
        g.blit(VillageScreen.WIDGETS, x + w - b, y, u + tw - b, v, b, b, a, a);
        g.blit(VillageScreen.WIDGETS, x, y + h - b, u, v + th - b, b, b, a, a);
        g.blit(VillageScreen.WIDGETS, x + w - b, y + h - b, u + tw - b, v + th - b, b, b, a, a);
        g.blit(VillageScreen.WIDGETS, x + b, y, iw, b, u + b, v, cw, b, a, a);
        g.blit(VillageScreen.WIDGETS, x + b, y + h - b, iw, b, u + b, v + th - b, cw, b, a, a);
        g.blit(VillageScreen.WIDGETS, x, y + b, b, ih, u, v + b, b, ch, a, a);
        g.blit(VillageScreen.WIDGETS, x + w - b, y + b, b, ih, u + tw - b, v + b, b, ch, a, a);
        g.blit(VillageScreen.WIDGETS, x + b, y + b, iw, ih, u + b, v + b, cw, ch, a, a);
    }

    private void sideButton(GuiGraphics g, int x, int y, int icon, int mx, int my) {
        boolean hover = mx >= x && mx < x + BTN && my >= y && my < y + BTN;
        nine(g, x, y, BTN, BTN, 100, hover ? 20 : 0, 48, 20, 4);
        g.blit(VillageScreen.WIDGETS, x + 2, y + 2, (icon % 32) * 16, VillageScreen.ICON_V + (icon / 32) * 16, 16, 16, VillageScreen.ATLAS, VillageScreen.ATLAS);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF4E9D0, true);
        String page = (menu.getPage() + 1) + "/" + Math.max(1, com.huntcraft.data.HunterData.ITEM_BOX_SIZE / ItemBoxContainer.PAGE_SIZE);
        g.drawString(this.font, page, this.imageWidth - 8 - this.font.width(page), this.titleLabelY, 0xFFFFD46A, true);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFF4E9D0, true);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
        int bx = leftPos + imageWidth + 2;
        String[] tips = {"screen.huntcraft.box.prev", "screen.huntcraft.box.next", "screen.huntcraft.box.deposit", "screen.huntcraft.box.sort"};
        int[] ys = {8, 30, 60, 82};
        for (int i = 0; i < 4; i++) {
            if (mx >= bx && mx < bx + BTN && my >= topPos + ys[i] && my < topPos + ys[i] + BTN) {
                g.renderTooltip(this.font, Component.translatable(tips[i]), mx, my);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int bx = leftPos + imageWidth + 2;
        int[] ys = {8, 30, 60, 82};
        for (int i = 0; i < 4; i++) {
            if (button == 0 && mx >= bx && mx < bx + BTN && my >= topPos + ys[i] && my < topPos + ys[i] + BTN) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(this.menu.containerId, i);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        int bx = leftPos + imageWidth + 2;
        if (mx >= bx && mx < bx + BTN && my >= top && my < top + 104) return false;
        return super.hasClickedOutside(mx, my, left, top, button);
    }
}
