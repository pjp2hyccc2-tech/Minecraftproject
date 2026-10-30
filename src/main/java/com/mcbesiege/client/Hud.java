package com.mcbesiege.client;

import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.item.ConnectorItem;
import com.mcbesiege.item.WrenchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Mission objectives panel and build-mode key hints. */
public final class Hud {
    private Hud() {}

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        CompoundTag m = ClientState.mission;
        if (m.contains("id")) {
            String id = m.getString("id");
            String state = m.getString("state");
            ListTag objs = m.getList("objs", Tag.TAG_COMPOUND);
            int x = 6, y = OrbitCamera.isActive() ? height - 40 - 12 * (objs.size() + 2) : 6;
            int w = 170;
            g.fill(x - 2, y - 2, x + w, y + 14 + objs.size() * 11 + 2, 0xA0201A14);
            g.drawString(mc.font, Component.translatable("mission.mcbesiege." + id), x + 2, y + 1, 0xFFFFD27A, true);
            y += 14;
            for (int i = 0; i < objs.size(); i++) {
                CompoundTag o = objs.getCompound(i);
                int c = o.getInt("c"), t = o.getInt("t");
                boolean done = c >= t;
                String prefix = done ? "✔ " : "□ ";
                g.drawString(mc.font, Component.literal(prefix).append(Component.translatable("objective.mcbesiege." + o.getString("k"), c, t)),
                        x + 2, y, done ? 0xFF8CFF8C : 0xFFE8E8E8, false);
                y += 11;
            }
            if (state.equals("gen")) g.drawString(mc.font, Component.translatable("hud.mcbesiege.generating"), x + 2, y + 2, 0xFFFFE070, true);
            else if (state.equals("done")) g.drawString(mc.font, Component.translatable("title.mcbesiege.victory"), x + 2, y + 2, 0xFFFFD700, true);
        }
        if (mc.screen == null && !OrbitCamera.isActive()) {
            ItemStack held = mc.player.getMainHandItem();
            boolean building = held.getItem() instanceof WrenchItem || held.getItem() instanceof ConnectorItem
                    || (held.getItem() instanceof BlockItem bi && bi.getBlock() instanceof MachinePartBlock);
            if (building) {
                Component hint = Component.translatable("hud.mcbesiege.build_keys", ClientSetup.KEY_SIMULATE.getTranslatedKeyMessage(),
                        ClientSetup.KEY_BUILDER.getTranslatedKeyMessage(), ClientSetup.KEY_MIRROR.getTranslatedKeyMessage(),
                        ClientSetup.KEY_CONTROL.getTranslatedKeyMessage());
                int tw = mc.font.width(hint);
                g.fill((width - tw) / 2 - 4, height - 74, (width + tw) / 2 + 4, height - 62, 0x80000000);
                g.drawString(mc.font, hint, (width - tw) / 2, height - 72, 0xFFFFE08A, false);
            }
        }
    }
}
