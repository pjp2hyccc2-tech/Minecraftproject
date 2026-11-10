package com.eternalrealm.ui.map;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.client.map.ThreatClient;
import com.eternalrealm.core.ModInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Under the minimap: the 瘴気 gauge of the field the player stands in (violet, pulsing red during an omen, the risen
 * calamity's name while it is out, light blue with the time left while the field is purified), and for a few seconds
 * after a lord hunt a small panel with the player's merit place and what they got.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ThreatOverlay implements IGuiOverlay {
    private static final long RESULT_NANOS = 9_000_000_000L;
    private static JsonObject result;
    private static long resultAt;
    private static final List<ItemStack> RESULT_ITEMS = new ArrayList<>();

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.POTION_ICONS.id(), "field_threat", new ThreatOverlay());
    }

    public static void showResult(JsonObject data) {
        result = data;
        resultAt = System.nanoTime();
        RESULT_ITEMS.clear();
        if (data.has("rewards") && data.get("rewards").isJsonArray()) {
            for (JsonElement element : data.getAsJsonArray("rewards")) {
                try {
                    ItemStack stack = ItemStack.of(TagParser.parseTag(element.getAsString()));
                    if (!stack.isEmpty()) {
                        RESULT_ITEMS.add(stack);
                    }
                } catch (Exception ignored) {
                    // an unreadable item is simply not shown
                }
            }
        }
    }

    /** Whether the result card of a defeated lord is still up (the HUD layout makes room for it). */
    public static boolean resultShowing() {
        return result != null && System.nanoTime() - resultAt < RESULT_NANOS;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui || player == null) {
            return;
        }
        Font font = minecraft.font;
        com.eternalrealm.hud.HudLayout.Layout layout = com.eternalrealm.ui.hud.HudFrame.layout();
        int field = ThreatClient.currentField();
        JsonObject entry = field == 0 ? null : ThreatClient.field(field).orElse(null);
        com.eternalrealm.hud.Rect gauge = layout.threat();
        if (entry != null && !gauge.isEmpty()) {
            drawGauge(graphics, font, entry, gauge.x(), gauge.y(), gauge.width());
        }
        com.eternalrealm.hud.Rect card = layout.threatResult();
        if (resultShowing() && !card.isEmpty()) {
            drawResult(graphics, font, card.x(), card.y(), card.width());
        }
    }

    private static void drawGauge(GuiGraphics graphics, Font font, JsonObject entry, int left, int top, int width) {
        String phase = ClientUiState.stringOr(entry, "phase", "calm");
        int gauge = Mth.clamp(ClientUiState.intOr(entry, "gauge", 0), 0, 1000);
        double remaining = Math.max(0.0, number(entry, "remaining") - ThreatClient.boardAge());
        long nanos = System.nanoTime();
        graphics.fill(left - 1, top - 1, left + width + 1, top + 21, 0xC0000000);
        Component label;
        int fillColor;
        double ratio;
        switch (phase) {
            case "omen" -> {
                float pulse = 0.5F + 0.5F * (float) Math.sin(nanos / 1.2E8);
                label = Component.translatable("threat.eternalrealm.hud.omen", clock(remaining))
                        .withStyle(ChatFormatting.RED);
                fillColor = 0xFF000000 | (int) (150 + 100 * pulse) << 16 | 0x2050;
                ratio = 1.0;
            }
            case "manifest" -> {
                String name = entry.has("calamity") ? ClientUiState.stringOr(entry.getAsJsonObject("calamity"), "name", "")
                        : "";
                label = Component.translatable("threat.eternalrealm.hud.manifest", Component.translatable(name))
                        .withStyle(ChatFormatting.DARK_RED);
                fillColor = 0xFFB01830;
                ratio = 1.0;
            }
            case "purified" -> {
                label = Component.translatable("threat.eternalrealm.hud.purified", clock(remaining))
                        .withStyle(ChatFormatting.AQUA);
                fillColor = 0xFF60D8F0;
                ratio = remaining / (com.eternalrealm.world.field.Miasma.PURIFIED_TICKS / 20.0);
            }
            default -> {
                label = Component.translatable("threat.eternalrealm.hud.miasma", gauge / 10)
                        .withStyle(ChatFormatting.LIGHT_PURPLE);
                fillColor = gauge >= 800 ? 0xFFC040E0 : 0xFF8A40C0;
                ratio = gauge / 1000.0;
            }
        }
        String text = font.plainSubstrByWidth(label.getString(), width - 4);
        graphics.drawString(font, Component.literal(text).withStyle(label.getStyle()), left + 2, top + 1, 0xFFFFFFFF);
        graphics.fill(left + 2, top + 12, left + width - 2, top + 18, 0xFF1A1020);
        graphics.fill(left + 2, top + 12, left + 2 + (int) ((width - 4) * Mth.clamp(ratio, 0.0, 1.0)), top + 18,
                fillColor);
        for (int i = 1; i < 10; i++) {
            int x = left + 2 + (width - 4) * i / 10;
            graphics.fill(x, top + 16, x + 1, top + 18, 0x50000000);
        }
    }

    private static void drawResult(GuiGraphics graphics, Font font, int left, int top, int width) {
        JsonObject data = result;
        float age = (System.nanoTime() - resultAt) / 1.0E9F;
        float alpha = age < 0.25F ? age / 0.25F : age > 8.0F ? Math.max(0.0F, 9.0F - age) : 1.0F;
        if (alpha <= 0.02F) {
            return;
        }
        int height = 34 + (RESULT_ITEMS.isEmpty() ? 0 : 18);
        int a = (int) (alpha * 220);
        graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, (a << 24) | 0x7A5A26);
        graphics.fill(left, top, left + width, top + height, ((int) (alpha * 230) << 24) | 0x141218);
        Component name;
        try {
            Component parsed = Component.Serializer.fromJson(ClientUiState.stringOr(data, "name", "\"\""));
            name = parsed == null ? Component.empty() : parsed;
        } catch (RuntimeException e) {
            name = Component.empty();
        }
        Component title = Component.translatable("threat.eternalrealm.hud.lord_down", name)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), width - 6), left + 4, top + 3,
                0xFFE8C66A);
        boolean hero = data.has("hero") && data.get("hero").getAsBoolean();
        Component merit = Component.translatable("threat.eternalrealm.hud.merit", ClientUiState.intOr(data, "rank", 0),
                String.format(Locale.ROOT, "%.0f", number(data, "share") * 100.0));
        if (hero) {
            merit = Component.literal("♛ ").append(Component.translatable("threat.eternalrealm.hud.hero")).append("  ")
                    .append(merit);
        }
        graphics.drawString(font, merit, left + 4, top + 13, hero ? 0xFFFFD34A : 0xFFE0D8D0);
        String gains = String.format(Locale.ROOT, "EXP +%,d   +%,d G", ClientUiState.longOr(data, "exp", 0L),
                ClientUiState.longOr(data, "gold", 0L));
        graphics.drawString(font, gains, left + 4, top + 23, 0xFF9AE08A);
        int x = left + 4;
        for (ItemStack stack : RESULT_ITEMS) {
            if (x + 16 > left + width) {
                break;
            }
            graphics.renderItem(stack, x, top + 33);
            graphics.renderItemDecorations(font, stack, x, top + 33);
            x += 18;
        }
    }

    private static double number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : 0.0;
    }

    static String clock(double seconds) {
        int total = (int) Math.max(0, Math.ceil(seconds));
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }
}
