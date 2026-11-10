package com.eternalrealm.ui.map;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.client.map.ThreatClient;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 討伐手配書 — the wanted board of the fields: for every field its recommended levels, its 瘴気 gauge and phase, its
 * calamity (sleeping, omen, risen, or the field purified) and its two lords (out now, or back in mm:ss). Opened from
 * the map; scrolls with the mouse wheel.
 */
public final class ThreatBoardScreen extends Screen {
    private static final int GOLD = 0xFFE8C66A;
    private static final int BORDER = 0xFF7A5A26;
    private static final int ROW = 50;
    private final Screen parent;
    private double scroll;

    public ThreatBoardScreen(Screen parent) {
        super(Component.translatable("threat.eternalrealm.board.title"));
        this.parent = parent;
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ThreatBoardScreen(minecraft.screen));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("×"), b -> onClose()).bounds(width - 22, 4, 18, 16).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - delta * 24.0, 0.0, Math.max(0.0, 10 * ROW - (height - 40)));
        return true;
    }

    private int panelLeft() {
        return Math.max(8, width / 2 - 200);
    }

    private int panelRight() {
        return Math.min(width - 8, width / 2 + 200);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = panelLeft();
        int right = panelRight();
        graphics.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, 8, GOLD);
        graphics.drawCenteredString(font, Component.translatable("threat.eternalrealm.board.hint")
                .withStyle(ChatFormatting.GRAY), width / 2, 19, 0xFFFFFFFF);
        int top = 32;
        int bottom = height - 6;
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, BORDER);
        graphics.fill(left, top, right, bottom, 0xE0141218);
        graphics.enableScissor(left, top, right, bottom);
        int y = top + 4 - (int) scroll;
        if (ThreatClient.board().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("threat.eternalrealm.board.empty")
                    .withStyle(ChatFormatting.GRAY), width / 2, top + 20, 0xFFFFFFFF);
        }
        double age = ThreatClient.boardAge();
        for (AreaDefinition area : Areas.all()) {
            JsonObject entry = ThreatClient.field(area.id()).orElse(null);
            if (entry == null) {
                continue;
            }
            drawField(graphics, area, entry, left + 6, y, right - left - 12, age);
            y += ROW;
        }
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawField(GuiGraphics graphics, AreaDefinition area, JsonObject entry, int x, int y, int width,
                           double age) {
        graphics.fill(x - 2, y - 2, x + width + 2, y + ROW - 6, 0x30FFFFFF);
        Component name = Component.translatable(area.translationKey()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        graphics.drawString(font, name, x, y, 0xFFFFFFFF);
        Component levels = WorldMapScreen.levelRange(area);
        graphics.drawString(font, levels.copy().withStyle(ChatFormatting.GRAY), x + font.width(name) + 6, y, 0xFFFFFFFF);
        // miasma gauge and phase on the right of the name row
        String phase = ClientUiState.stringOr(entry, "phase", "calm");
        int gauge = Mth.clamp(ClientUiState.intOr(entry, "gauge", 0), 0, 1000);
        double remaining = Math.max(0.0, number(entry, "remaining") - age);
        int barWidth = 90;
        int barLeft = x + width - barWidth;
        int color = switch (phase) {
            case "omen" -> 0xFFE04060;
            case "manifest" -> 0xFFB01830;
            case "purified" -> 0xFF60D8F0;
            default -> 0xFF9A48D0;
        };
        double ratio = switch (phase) {
            case "calm" -> gauge / 1000.0;
            case "purified" -> remaining / (com.eternalrealm.world.field.Miasma.PURIFIED_TICKS / 20.0);
            default -> 1.0;
        };
        graphics.fill(barLeft, y + 1, barLeft + barWidth, y + 7, 0xFF1A1020);
        graphics.fill(barLeft, y + 1, barLeft + (int) (barWidth * Mth.clamp(ratio, 0.0, 1.0)), y + 7, color);
        Component phaseText = switch (phase) {
            case "omen" -> Component.translatable("threat.eternalrealm.board.omen", ThreatOverlay.clock(remaining));
            case "manifest" -> Component.translatable("threat.eternalrealm.board.manifest");
            case "purified" -> Component.translatable("threat.eternalrealm.board.purified",
                    ThreatOverlay.clock(remaining));
            default -> Component.translatable("threat.eternalrealm.board.miasma", gauge / 10);
        };
        graphics.drawString(font, phaseText, barLeft - 4 - font.width(phaseText), y, color);
        // the calamity
        int line = y + 12;
        if (entry.has("calamity") && entry.get("calamity").isJsonObject()) {
            JsonObject calamity = entry.getAsJsonObject("calamity");
            boolean risen = calamity.has("risen") && calamity.get("risen").getAsBoolean();
            Component status = risen ? Component.translatable("threat.eternalrealm.board.risen")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    : "manifest".equals(phase) ? Component.translatable("threat.eternalrealm.board.waiting")
                    .withStyle(ChatFormatting.RED)
                    : Component.translatable("threat.eternalrealm.board.sleeping").withStyle(ChatFormatting.DARK_GRAY);
            Component text = Component.translatable("threat.eternalrealm.board.calamity",
                    Component.translatable(ClientUiState.stringOr(calamity, "name", "")).withStyle(ChatFormatting.DARK_RED),
                    ClientUiState.intOr(calamity, "level", 0)).append("  ").append(status);
            graphics.drawString(font, text, x + 4, line, 0xFFE0D0D0);
        }
        // the lords
        line += 11;
        int lordX = x + 4;
        if (entry.has("lords") && entry.get("lords").isJsonArray()) {
            List<Component> parts = new ArrayList<>();
            for (JsonElement element : entry.getAsJsonArray("lords")) {
                JsonObject lord = element.getAsJsonObject();
                boolean present = lord.has("present") && lord.get("present").getAsBoolean();
                double due = Math.max(0.0, number(lord, "due") - age);
                Component status = present ? Component.translatable("threat.eternalrealm.board.lord_out")
                        .withStyle(ChatFormatting.GOLD)
                        : due > 0.5 ? Component.translatable("threat.eternalrealm.board.lord_back",
                        ThreatOverlay.clock(due)).withStyle(ChatFormatting.DARK_GRAY)
                        : Component.translatable("threat.eternalrealm.board.lord_near").withStyle(ChatFormatting.GRAY);
                parts.add(Component.translatable("threat.eternalrealm.board.lord",
                        Component.translatable(ClientUiState.stringOr(lord, "name", "")).withStyle(ChatFormatting.LIGHT_PURPLE),
                        ClientUiState.intOr(lord, "level", 0)).append(" ").append(status));
            }
            for (Component part : parts) {
                graphics.drawString(font, part, lordX, line, 0xFFD8D0C8);
                line += 11;
            }
        }
    }

    private static double number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : 0.0;
    }
}
