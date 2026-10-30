package com.mcbesiege.client;

import com.mcbesiege.block.MachinePartBlock;
import com.mcbesiege.net.Net;
import com.mcbesiege.part.PartType;
import com.mcbesiege.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Besiege-style block bar: categories, part palette and the ▶ / ⚙ / 🗑 / mirror toolbar. */
public class BuilderScreen extends Screen {
    private static final ResourceLocation LOGO = new ResourceLocation("mcbesiege", "textures/gui/logo.png");
    private static final PartType.Cat[] CATS = PartType.Cat.values();
    private static int selected = 0;
    private int barTop, barLeft, barW;
    private boolean confirmClear;

    public BuilderScreen() {
        super(Component.translatable("screen.mcbesiege.builder"));
    }

    private static ResourceLocation icon(String name) {
        return new ResourceLocation("mcbesiege", "textures/gui/" + name + ".png");
    }

    @Override
    protected void init() {
        barW = Math.min(width - 16, 420);
        barLeft = (width - barW) / 2;
        barTop = height - 118;
    }

    private List<Item> items() {
        List<Item> l = new ArrayList<>();
        PartType.Cat c = CATS[selected];
        for (PartType t : PartType.values()) if (t.cat == c) l.add(ModItems.part(t));
        if (c == PartType.Cat.MECHANICAL) {
            l.add(ModItems.BRACE_TOOL.get());
            l.add(ModItems.SPRING_TOOL.get());
            l.add(ModItems.ROPE_TOOL.get());
        }
        if (c == PartType.Cat.BASIC) {
            l.add(ModItems.WRENCH.get());
            l.add(ModItems.BLUEPRINT.get());
        }
        return l;
    }

    private int tabX(int i) { return barLeft + 6 + i * 26; }

    private int toolX(int i) { return barLeft + barW - 6 - (4 - i) * 26; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < CATS.length; i++) {
            int x = tabX(i), y = barTop + 6;
            if (mx >= x && mx < x + 22 && my >= y && my < y + 22) {
                selected = i;
                confirmClear = false;
                return true;
            }
        }
        for (int i = 0; i < 4; i++) {
            int x = toolX(i), y = barTop + 6;
            if (mx >= x && mx < x + 22 && my >= y && my < y + 22) {
                tool(i);
                return true;
            }
        }
        List<Item> items = items();
        for (int i = 0; i < items.size(); i++) {
            int x = slotX(i), y = slotY(i);
            if (mx >= x && mx < x + 20 && my >= y && my < y + 20) {
                CompoundTag t = new CompoundTag();
                t.putString("item", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(items.get(i)).getPath());
                Net.toServer("palette", t);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private int slotX(int i) { return barLeft + 8 + (i % 16) * 25; }

    private int slotY(int i) { return barTop + 40 + (i / 16) * 25; }

    private void tool(int i) {
        switch (i) {
            case 0 -> {
                CompoundTag t = new CompoundTag();
                BlockPos p = lookedPart();
                if (p != null) t.putLong("pos", p.asLong());
                Net.toServer("toggle", t);
                onClose();
            }
            case 1 -> {
                BlockPos p = lookedPart();
                if (p != null) ClientHooks.openPartConfig(p);
            }
            case 2 -> {
                BlockPos p = lookedPart();
                if (p == null) return;
                if (!confirmClear) {
                    confirmClear = true;
                    return;
                }
                CompoundTag t = new CompoundTag();
                t.putLong("pos", p.asLong());
                Net.toServer("clear", t);
                confirmClear = false;
            }
            case 3 -> Net.toServer("mirror", new CompoundTag());
            default -> {}
        }
    }

    private BlockPos lookedPart() {
        if (minecraft == null || minecraft.level == null || minecraft.hitResult == null) return null;
        if (minecraft.hitResult.getType() != HitResult.Type.BLOCK) return null;
        BlockPos p = ((BlockHitResult) minecraft.hitResult).getBlockPos();
        return minecraft.level.getBlockState(p).getBlock() instanceof MachinePartBlock ? p : null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int lw = 192, lh = 48;
        g.blit(LOGO, (width - lw) / 2, 6, lw, lh, 0, 0, 256, 64, 256, 64);
        List<Item> items = items();
        int rows = Math.max(1, (items.size() + 15) / 16);
        int barH = 40 + rows * 25 + 6;
        barTop = height - barH - 6;
        g.fill(barLeft - 2, barTop - 2, barLeft + barW + 2, barTop + barH + 2, 0xFF6B4A2E);
        g.fill(barLeft, barTop, barLeft + barW, barTop + barH, 0xE81E1A16);
        g.fill(barLeft, barTop + 32, barLeft + barW, barTop + 33, 0xFF6B4A2E);
        String[] catIcons = {"icon_cat_basic", "icon_cat_armor", "icon_cat_locomotion", "icon_cat_mechanical", "icon_cat_weapons", "icon_cat_flight", "icon_cat_automation"};
        for (int i = 0; i < CATS.length; i++) {
            int x = tabX(i), y = barTop + 6;
            boolean hover = mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + 22;
            g.fill(x, y, x + 22, y + 22, i == selected ? 0xFFC8962C : (hover ? 0xFF5A4A36 : 0xFF3A3028));
            g.blit(icon(catIcons[i]), x + 3, y + 3, 0, 0, 16, 16, 16, 16);
            if (hover) g.renderTooltip(font, Component.translatable("cat.mcbesiege." + CATS[i].name().toLowerCase(Locale.ROOT)), mouseX, mouseY);
        }
        g.drawString(font, Component.translatable("cat.mcbesiege." + CATS[selected].name().toLowerCase(Locale.ROOT)), tabX(CATS.length) + 4, barTop + 13, 0xFFFFD27A, true);
        String[] tools = {"icon_play", "icon_settings", "icon_trash", "icon_mirror"};
        String[] tips = {"tool.mcbesiege.play", "tool.mcbesiege.settings", confirmClear ? "tool.mcbesiege.trash_confirm" : "tool.mcbesiege.trash", "tool.mcbesiege.mirror"};
        for (int i = 0; i < 4; i++) {
            int x = toolX(i), y = barTop + 6;
            boolean hover = mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + 22;
            int bg = i == 0 ? 0xFF2E6B2E : (i == 2 && confirmClear ? 0xFF8B2020 : 0xFF3A3028);
            g.fill(x, y, x + 22, y + 22, hover ? 0xFF6B6B4A : bg);
            g.blit(icon(tools[i]), x + 3, y + 3, 0, 0, 16, 16, 16, 16);
        }
        for (int i = 0; i < items.size(); i++) {
            int x = slotX(i), y = slotY(i);
            boolean hover = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            g.fill(x, y, x + 20, y + 20, hover ? 0xFF6B5A40 : 0xFF2C2620);
            g.renderItem(new ItemStack(items.get(i)), x + 2, y + 2);
        }
        for (int i = 0; i < 4; i++) {
            int x = toolX(i), y = barTop + 6;
            if (mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + 22) g.renderTooltip(font, Component.translatable(tips[i]), mouseX, mouseY);
        }
        for (int i = 0; i < items.size(); i++) {
            int x = slotX(i), y = slotY(i);
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) {
                Item it = items.get(i);
                String path = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it).getPath();
                List<Component> lines = new ArrayList<>();
                lines.add(new ItemStack(it).getHoverName());
                lines.add(Component.translatable("desc.mcbesiege." + path).withStyle(net.minecraft.ChatFormatting.GRAY));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
        g.drawCenteredString(font, Component.translatable("screen.mcbesiege.builder_hint"), width / 2, barTop - 12, 0xFFDDDDDD);
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
