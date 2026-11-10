package com.eternalrealm.ui.screen;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.client.revival.ClientRevival;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 戦闘不能 — the revival screen (Ragnarok X style): who brought the player down, what it cost, the two ways back
 * (revive on the spot with a 世界樹の葉, or return to the save point, which happens by itself when the countdown ends),
 * a reminder that allies can revive the player, and "強くなるには" advice. The world stays visible (and running)
 * behind a dark red veil; Esc opens the pause menu and T the chat, after which the screen comes back.
 */
public final class RevivalScreen extends Screen {
    private static final int GOLD = 0xFFE8C66A;
    private static final int BORDER = 0xFF7A5A26;
    private static final int PANEL = 0xD8141218;
    private static final int BUTTON_W = 156;
    private static final int BUTTON_H = 44;
    private static final int GAP = 14;

    private Component killer = Component.empty();
    private Component destination = Component.empty();
    private int leaves;
    private long expLost;
    private double unlock;
    private double auto;
    private String place = "field";
    private final List<Tip> tips = new ArrayList<>();
    private long pendingUntil;
    private final long openedAt = System.nanoTime();

    private record Tip(ItemStack icon, Component text) {
    }

    public RevivalScreen() {
        super(Component.translatable("revival.eternalrealm.title"));
        refresh();
    }

    /** Re-reads the latest screen document (a new one arrives every few seconds and after changes). */
    public void refresh() {
        JsonObject data = ClientRevival.screenData().orElse(null);
        if (data == null) {
            return;
        }
        killer = component(ClientUiState.stringOr(data, "killer", ""));
        destination = component(ClientUiState.stringOr(data, "destination", ""));
        leaves = ClientUiState.intOr(data, "leaves", 0);
        expLost = ClientUiState.longOr(data, "exp", 0L);
        unlock = number(data, "unlock");
        auto = number(data, "auto");
        place = ClientUiState.stringOr(data, "place", "field");
        tips.clear();
        if (data.has("tips") && data.get("tips").isJsonArray()) {
            for (JsonElement element : data.getAsJsonArray("tips")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject tip = element.getAsJsonObject();
                List<Object> args = new ArrayList<>();
                if (tip.has("args") && tip.get("args").isJsonArray()) {
                    JsonArray array = tip.getAsJsonArray("args");
                    array.forEach(arg -> args.add(arg.getAsString()));
                }
                ResourceLocation icon = ResourceLocation.tryParse(ClientUiState.stringOr(tip, "icon", "minecraft:book"));
                ItemStack stack = new ItemStack(icon == null ? Items.BOOK : BuiltInRegistries.ITEM.get(icon));
                tips.add(new Tip(stack, Component.translatable(ClientUiState.stringOr(tip, "key", ""), args.toArray())));
            }
        }
    }

    private static Component component(String json) {
        if (json.isEmpty()) {
            return Component.empty();
        }
        try {
            Component parsed = Component.Serializer.fromJson(json);
            return parsed == null ? Component.empty() : parsed;
        } catch (RuntimeException e) {
            return Component.literal(json);
        }
    }

    private static double number(JsonObject data, String key) {
        JsonElement element = data.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : 0.0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    private int buttonsTop() {
        return Math.max(96, height * 46 / 100);
    }

    private int leafLeft() {
        return width / 2 - GAP / 2 - BUTTON_W;
    }

    private int returnLeft() {
        return width / 2 + GAP / 2;
    }

    private double elapsed() {
        return ClientRevival.secondsSinceUpdate();
    }

    private boolean leafEnabled() {
        return leaves > 0 && System.currentTimeMillis() >= pendingUntil;
    }

    private boolean returnEnabled() {
        return unlock - elapsed() <= 0.0 && System.currentTimeMillis() >= pendingUntil;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int top = buttonsTop();
            if (inside(mouseX, mouseY, leafLeft(), top, BUTTON_W, BUTTON_H) && leafEnabled()) {
                send(com.eternalrealm.revival.forge.RevivalService.LEAF_ACTION);
                return true;
            }
            if (inside(mouseX, mouseY, returnLeft(), top, BUTTON_W, BUTTON_H) && returnEnabled()) {
                send(com.eternalrealm.revival.forge.RevivalService.RETURN_ACTION);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(String action) {
        pendingUntil = System.currentTimeMillis() + 1000L;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        ERNetwork.sendToServer(new UiActionPacket(action, new JsonObject()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.pauseGame(false);
            return true;
        }
        if (minecraft.options.keyChat.matches(keyCode, scanCode)) {
            minecraft.setScreen(new ChatScreen(""));
            return true;
        }
        if (minecraft.options.keyCommand.matches(keyCode, scanCode)) {
            minecraft.setScreen(new ChatScreen("/"));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // a dark red veil over the world, heavier towards the edges and the bottom
        graphics.fillGradient(0, 0, width, height, 0x50180404, 0xC8100202);
        int edge = Math.max(24, width / 8);
        for (int i = 0; i < edge; i += 4) {
            int alpha = (int) (110 * (1.0 - (double) i / edge));
            graphics.fill(i, 0, i + 4, height, alpha << 24);
            graphics.fill(width - i - 4, 0, width - i, height, alpha << 24);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        double opened = (System.nanoTime() - openedAt) / 1.0E9;
        float fadeIn = (float) Mth.clamp(opened / 0.6, 0.0, 1.0);

        // title: 戦闘不能, pulsing, sliding down into place
        int titleY = Math.max(24, height * 17 / 100) - (int) ((1.0F - fadeIn) * 14);
        float pulse = 0.85F + 0.15F * (float) Math.sin(opened * 3.0);
        int red = (int) (255 * pulse);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, titleY, 0.0F);
        graphics.pose().scale(3.0F, 3.0F, 1.0F);
        Component title = this.title.copy().withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, title, -font.width(title) / 2, 0, (0xFF << 24) | (red << 16) | 0x3A30, true);
        graphics.pose().popPose();
        int y = titleY + 34;
        Component by = Component.translatable("revival.eternalrealm.killed_by", killer.copy()
                .withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(font, by, width / 2, y, 0xFFFFFF);
        y += 12;
        if (expLost > 0) {
            graphics.drawCenteredString(font, Component.translatable("revival.eternalrealm.exp_lost",
                    String.format(Locale.ROOT, "%,d", expLost)).withStyle(ChatFormatting.DARK_GRAY), width / 2, y,
                    0xFFFFFF);
        }

        // the two ways back
        int top = buttonsTop();
        ItemStack leafIcon = new ItemStack(ERGearItems.consumable(Consumable.YGGDRASIL_LEAF));
        Component leafSub = leaves > 0
                ? Component.translatable("revival.eternalrealm.leaf_count", leaves).withStyle(ChatFormatting.GREEN)
                : Component.translatable("revival.eternalrealm.no_leaf").withStyle(ChatFormatting.DARK_GRAY);
        button(graphics, leafLeft(), top, leafIcon, Component.translatable("revival.eternalrealm.revive_here"), leafSub,
                Component.translatable("revival.eternalrealm.revive_here.health").withStyle(ChatFormatting.GRAY),
                leafEnabled(), inside(mouseX, mouseY, leafLeft(), top, BUTTON_W, BUTTON_H), 0xFF4FB85A);
        double unlockLeft = unlock - elapsed();
        double autoLeft = Math.max(0.0, auto - elapsed());
        Component countdown = unlockLeft > 0.0
                ? Component.translatable("revival.eternalrealm.return_locked",
                String.format(Locale.ROOT, "%.1f", unlockLeft)).withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("revival.eternalrealm.auto_return", (int) Math.ceil(autoLeft))
                .withStyle(autoLeft < 6.0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
        button(graphics, returnLeft(), top, new ItemStack(Items.LODESTONE),
                Component.translatable("revival.eternalrealm.return"), destination.copy().withStyle(ChatFormatting.AQUA),
                countdown, returnEnabled(), inside(mouseX, mouseY, returnLeft(), top, BUTTON_W, BUTTON_H), 0xFF5AA8E0);
        // the automatic return as a draining bar under the buttons
        int barLeft = leafLeft();
        int barRight = returnLeft() + BUTTON_W;
        double total = com.eternalrealm.revival.RevivalRules.autoReturnTicks(com.eternalrealm.revival.RevivalRules.Place
                .byId(place)) / 20.0;
        double ratio = Mth.clamp(autoLeft / Math.max(1.0, total), 0.0, 1.0);
        graphics.fill(barLeft, top + BUTTON_H + 6, barRight, top + BUTTON_H + 8, 0x60000000);
        graphics.fill(barLeft, top + BUTTON_H + 6, barLeft + (int) ((barRight - barLeft) * ratio), top + BUTTON_H + 8,
                0xC0D04A3A);
        graphics.drawCenteredString(font, Component.translatable("revival.eternalrealm.wait_for_allies")
                .withStyle(ChatFormatting.GRAY), width / 2, top + BUTTON_H + 14, 0xFFFFFF);

        // 強くなるには
        if (!tips.isEmpty()) {
            int panelWidth = Math.min(width - 40, 340);
            int panelLeft = width / 2 - panelWidth / 2;
            int panelTop = top + BUTTON_H + 32;
            int panelBottom = panelTop + 16 + tips.size() * 20;
            if (panelBottom < height - 16) {
                graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + panelWidth + 1, panelBottom + 1, BORDER);
                graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, PANEL);
                graphics.drawString(font, Component.translatable("revival.eternalrealm.tips")
                        .withStyle(ChatFormatting.BOLD), panelLeft + 6, panelTop + 4, GOLD);
                int row = panelTop + 16;
                for (Tip tip : tips) {
                    graphics.renderItem(tip.icon(), panelLeft + 5, row);
                    List<net.minecraft.util.FormattedCharSequence> lines = font.split(tip.text(), panelWidth - 30);
                    int textY = row + (lines.size() > 1 ? 0 : 4);
                    for (int i = 0; i < Math.min(2, lines.size()); i++) {
                        graphics.drawString(font, lines.get(i), panelLeft + 25, textY + i * 9, 0xFFE6E0D6);
                    }
                    row += 20;
                }
            }
        }
        graphics.drawString(font, Component.translatable("revival.eternalrealm.keys").withStyle(ChatFormatting.DARK_GRAY),
                6, height - 12, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void button(GuiGraphics graphics, int x, int y, ItemStack icon, Component label, Component line1,
                        Component line2, boolean enabled, boolean hovered, int accent) {
        int border = !enabled ? 0xFF3A3238 : hovered ? accent : BORDER;
        graphics.fill(x - 1, y - 1, x + BUTTON_W + 1, y + BUTTON_H + 1, border);
        graphics.fill(x, y, x + BUTTON_W, y + BUTTON_H, enabled ? (hovered ? 0xF0262030 : PANEL) : 0xC0101014);
        if (enabled) {
            graphics.fill(x, y, x + 3, y + BUTTON_H, accent);
        }
        graphics.renderItem(icon, x + 8, y + (BUTTON_H - 16) / 2);
        int textLeft = x + 30;
        int width = BUTTON_W - 34;
        fit(graphics, enabled ? label : label.copy().withStyle(ChatFormatting.DARK_GRAY), textLeft, y + 6, width,
                0xFFFFFFFF);
        fit(graphics, line1, textLeft, y + 18, width, enabled ? 0xFFB8E0B0 : 0xFF5A5460);
        fit(graphics, line2, textLeft, y + 30, width, 0xFF9A94A0);
    }

    /** Draws {@code text} in its own colours, or shortened with an ellipsis when it does not fit. */
    private void fit(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, text, x, y, color);
        } else {
            graphics.drawString(font, font.plainSubstrByWidth(text.getString(), maxWidth - font.width("…")) + "…", x,
                    y, color);
        }
    }
}
