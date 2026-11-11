package com.eternalrealm.ui.hud;

import com.eternalrealm.client.ClientCombatState;
import com.eternalrealm.hud.Rect;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.bag.PickupFeed;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.network.forge.PickupFeedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * The pickup feed at the right edge, just above the bag and equipment icons ({@code HudLayout.pickupFeed}): one row
 * per acquisition with the item's icon, its name in the rarity's colour and ×count; the same item again within a few
 * seconds adds to its row. Rare and better items are framed and chime, a relic (金枠) gets the gold frame and its own
 * chime; a full bag shows a warning row.
 */
public final class PickupFeedOverlay implements IGuiOverlay {
    private static final PickupFeed<ItemStack> FEED = new PickupFeed<>();
    private static final int ROW = 12;
    private static final int BACK = 0x141018;
    private static final int RIM = 0xC9A55A;
    private static final int WARNING = 0xFF6A50;

    /** Client thread: a row from the server. */
    public static void onPacket(PickupFeedPacket packet) {
        ItemStack stack = packet.stack();
        boolean full = (packet.flags() & PickupFeedPacket.BAG_FULL) != 0;
        ItemRarity rarity = EquipmentStacks.read(stack).map(data -> data.rarity()).orElse(ItemRarity.COMMON);
        String key = ForgeRegistries.ITEMS.getKey(stack.getItem()) + "|" + stack.getHoverName().getString();
        PickupFeed.Row<ItemStack> row = FEED.add(full ? "full|" + key : key,
                full ? PickupFeed.Kind.WARNING : PickupFeed.Kind.ITEM, stack, rarity, packet.count(),
                ClientCombatState.tick());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !full && EquipmentStacks.isGoldFrame(stack)) {
            // a relic (金枠): its own chime
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.4F);
            minecraft.player.playSound(com.eternalrealm.sound.ERSounds.LOOT_RARE_DROP.get(), 0.9F, 0.8F);
        } else if (minecraft.player != null && row.highlighted() && row.count() == packet.count()) {
            minecraft.player.playSound(full ? com.eternalrealm.sound.ERSounds.UI_CLICK.get()
                    : com.eternalrealm.sound.ERSounds.LOOT_RARE_DROP.get(), full ? 0.8F : 0.6F, full ? 0.6F : 1.2F);
        }
    }

    public static void clear() {
        FEED.clear();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.options.hideGui || minecraft.player == null || !HudFrame.mmo(minecraft.player)) {
            return;
        }
        Rect area = HudFrame.layout().pickupFeed();
        if (area.isEmpty()) {
            return;
        }
        long now = ClientCombatState.tick();
        List<PickupFeed.Row<ItemStack>> rows = FEED.visible(now);
        int fit = Math.min(rows.size(), Math.max(0, area.height() / ROW));
        Font font = minecraft.font;
        for (int i = 0; i < fit; i++) {
            // newest at the bottom, next to the icons
            PickupFeed.Row<ItemStack> row = rows.get(i);
            int y = area.bottom() - (i + 1) * ROW;
            drawRow(graphics, font, area, y, row, row.alpha(now));
        }
    }

    private static void drawRow(GuiGraphics graphics, Font font, Rect area, int y, PickupFeed.Row<ItemStack> row,
                                float alpha) {
        if (alpha <= 0.02F) {
            return;
        }
        boolean warning = row.kind() == PickupFeed.Kind.WARNING;
        Component name = warning
                ? Component.translatable("hud.eternalrealm.pickup.bag_full", row.payload().getHoverName())
                : row.payload().getHoverName();
        String count = row.count() > 1 ? " ×" + row.count() : "";
        int textWidth = font.width(name) + font.width(count);
        int width = Math.min(area.width(), textWidth + 18);
        int x = area.right() - width;
        int a = Math.round(alpha * 255.0F);
        graphics.fill(x, y, area.right(), y + ROW - 1, argb((int) (a * 0.7F), BACK));
        boolean gold = !warning && EquipmentStacks.isGoldFrame(row.payload());
        if (gold && alpha > 0.35F) {
            // a relic (金枠): the gold frame around the whole row, its name in gold
            com.eternalrealm.ui.GoldFrame.draw(graphics, x, y, area.right() - x, ROW - 1, ClientCombatState.tick());
        }
        int accent = warning ? WARNING : gold ? 0xFFD84A : row.highlighted() ? row.rarity().color() : RIM;
        graphics.fill(x, y, x + 1, y + ROW - 1, argb(a, accent));
        if (row.highlighted()) {
            graphics.fill(x, y, area.right(), y + 1, argb((int) (a * 0.8F), accent));
            graphics.fill(x, y + ROW - 2, area.right(), y + ROW - 1, argb((int) (a * 0.8F), accent));
        }
        if (alpha > 0.35F) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + 2, y + 0.5F, 0.0F);
            graphics.pose().scale(0.6875F, 0.6875F, 1.0F);
            graphics.renderItem(row.payload(), 0, 0);
            graphics.pose().popPose();
        }
        int color = warning ? WARNING : gold ? 0xFFD84A
                : row.rarity() == ItemRarity.COMMON ? 0xFFFFFF : row.rarity().color();
        int textX = x + 15;
        graphics.drawString(font, name, textX, y + 2, argb(Math.max(8, a), color), true);
        if (!count.isEmpty()) {
            graphics.drawString(font, count, textX + font.width(name), y + 2, argb(Math.max(8, a), 0xE8E0C8), true);
        }
    }

    private static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
