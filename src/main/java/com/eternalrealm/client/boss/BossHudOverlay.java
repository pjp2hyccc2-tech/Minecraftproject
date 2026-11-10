package com.eternalrealm.client.boss;

import com.eternalrealm.client.ClientChannels;
import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.core.ModInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * The boss HUD, replacing the vanilla boss bar: name, level and difficulty over a layered health bar
 * ("×N" bars of changing colour, the lost part fading out in white), phase pips, the enrage timer, a shield or
 * invulnerability veil, the cast bar of the attack being prepared (its name over a filling bar) and, on the right,
 * the damage ranking of the party with the player's own line highlighted. Fed by the {@code boss_hud} channel every
 * few ticks; hides itself when the fight ends or the updates stop.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BossHudOverlay implements IGuiOverlay {
    private static final Logger LOG = LogUtils.getLogger();
    public static final String CHANNEL = "boss_hud";
    private static final long STALE_NANOS = 2_000_000_000L;
    private static final int[] LAYER_COLORS = {0xFFD8402E, 0xFFE0801E, 0xFFD8B42A, 0xFF5CBF4A, 0xFF2EAFA4,
            0xFF3C7CDA, 0xFF8C4CD4, 0xFFD04C9C};
    private static final int GOLD = 0xFFE8C66A;
    private static final int BORDER = 0xFF7A5A26;

    private static JsonObject data;
    private static long receivedAt;
    private static String fightId = "";
    private static Component name = Component.empty();
    private static double shownHp = -1.0;
    private static double ghostHp = -1.0;
    private static long lastFrame;
    private static boolean broken;

    public static void init() {
        ClientChannels.register(CHANNEL, BossHudOverlay::accept);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), "boss_hud", new BossHudOverlay());
    }

    private static void accept(JsonObject update) {
        String id = ClientUiState.stringOr(update, "id", "");
        if (update.has("end")) {
            if (id.equals(fightId)) {
                data = null;
            }
            return;
        }
        if (!id.equals(fightId)) {
            fightId = id;
            shownHp = -1.0;
            ghostHp = -1.0;
            try {
                Component parsed = Component.Serializer.fromJson(ClientUiState.stringOr(update, "name", "\"\""));
                name = parsed == null ? Component.empty() : parsed;
            } catch (RuntimeException e) {
                name = Component.literal(ClientUiState.stringOr(update, "boss", "?"));
            }
        }
        data = update;
        receivedAt = System.nanoTime();
    }

    /** Whether the HUD is up (other overlays may move out of its way). */
    public static boolean active() {
        return data != null && System.nanoTime() - receivedAt < STALE_NANOS;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (broken || minecraft.options.hideGui || !active()) {
            return;
        }
        try {
            draw(minecraft, graphics, screenWidth, screenHeight);
        } catch (RuntimeException e) {
            broken = true;
            LOG.error("Boss HUD disabled after a rendering error", e);
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : fallback;
    }

    private static boolean flag(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static void draw(Minecraft minecraft, GuiGraphics graphics, int screenWidth, int screenHeight) {
        JsonObject d = data;
        Font font = minecraft.font;
        double max = Math.max(1.0, number(d, "max", 1.0));
        double hp = Mth.clamp(number(d, "hp", 0.0), 0.0, max);
        int layers = Mth.clamp((int) number(d, "layers", 10), 1, 50);
        double perLayer = max / layers;
        // smooth the bar: the live part slides quickly, the white "lost" part follows slowly
        long nowNanos = System.nanoTime();
        double dt = lastFrame == 0L ? 0.016 : Math.min(0.1, (nowNanos - lastFrame) / 1.0E9);
        lastFrame = nowNanos;
        if (shownHp < 0.0) {
            shownHp = hp;
            ghostHp = hp;
        }
        shownHp += (hp - shownHp) * Math.min(1.0, dt * 14.0);
        if (ghostHp < shownHp) {
            ghostHp = shownHp;
        } else {
            ghostHp += (shownHp - ghostHp) * Math.min(1.0, dt * 2.2);
        }

        int width = Mth.clamp(screenWidth - 240, 180, 320);
        int left = (screenWidth - width) / 2;
        int top = 6;
        double elapsed = (nowNanos - receivedAt) / 1.0E9;
        boolean dying = flag(d, "dying");

        // ---- name row: difficulty tag, Lv and name, ×N
        int layerIndex = hp <= 0.0 ? 0 : (int) Math.ceil(shownHp / perLayer - 1.0E-6) - 1;
        layerIndex = Mth.clamp(layerIndex, 0, layers - 1);
        int remaining = hp <= 0.0 ? 0 : layerIndex + 1;
        Component level = Component.literal("Lv." + (int) number(d, "level", 1) + " ").withStyle(ChatFormatting.GRAY);
        Component title = level.copy().append(name.copy().withStyle(ChatFormatting.BOLD));
        graphics.drawCenteredString(font, title, screenWidth / 2, top, 0xFFF4ECD8);
        String difficulty = ClientUiState.stringOr(d, "difficulty", "normal");
        Component tag = Component.translatable(ClientUiState.stringOr(d, "difficultyKey", "difficulty.eternalrealm.normal"));
        int tagColor = switch (difficulty) {
            case "hard" -> 0xFFE0703A;
            case "ex" -> 0xFFD050E0;
            case "lord" -> 0xFFC890FF;
            default -> 0xFF8FC8FF;
        };
        graphics.drawString(font, tag, left, top, tagColor);
        Component count = Component.literal("×" + remaining).withStyle(ChatFormatting.BOLD);
        graphics.drawString(font, count, left + width - font.width(count), top, 0xFFFFFFFF);

        // ---- the layered bar
        int barTop = top + 11;
        int barHeight = 9;
        boolean shield = flag(d, "shield");
        boolean invulnerable = flag(d, "invulnerable") || dying;
        int frame = shield ? 0xFF5FE0FF : BORDER;
        graphics.fill(left - 2, barTop - 2, left + width + 2, barTop + barHeight + 2, 0xC0000000);
        graphics.fill(left - 1, barTop - 1, left + width + 1, barTop + barHeight + 1, frame);
        graphics.fill(left, barTop, left + width, barTop + barHeight, 0xFF140C0C);
        double within = Mth.clamp(shownHp - layerIndex * perLayer, 0.0, perLayer) / perLayer;
        double ghostWithin = Mth.clamp(ghostHp - layerIndex * perLayer, 0.0, perLayer) / perLayer;
        if (layerIndex > 0) {
            graphics.fill(left, barTop, left + width, barTop + barHeight, dim(LAYER_COLORS[(layerIndex - 1) % LAYER_COLORS.length]));
        }
        if (ghostWithin > within) {
            graphics.fill(left + (int) (width * within), barTop, left + (int) (width * Math.min(1.0, ghostWithin)),
                    barTop + barHeight, 0xFFF4F0E8);
        }
        int color = LAYER_COLORS[layerIndex % LAYER_COLORS.length];
        int fillRight = left + (int) Math.round(width * within);
        if (hp > 0.0) {
            graphics.fill(left, barTop, fillRight, barTop + barHeight, color);
            graphics.fill(left, barTop, fillRight, barTop + 2, lighten(color));
            graphics.fill(left, barTop + barHeight - 2, fillRight, barTop + barHeight, dim(color));
        }
        for (int i = 1; i < 10; i++) {
            int x = left + width * i / 10;
            graphics.fill(x, barTop + barHeight - 3, x + 1, barTop + barHeight, 0x70000000);
        }
        if (invulnerable || shield) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(nowNanos / 2.0E8);
            int veil = shield ? 0x405FE0FF : 0x60808080;
            graphics.fill(left, barTop, left + width, barTop + barHeight, (int) (((veil >>> 24) * (0.6 + 0.4 * pulse))) << 24
                    | (veil & 0xFFFFFF));
            Component label = Component.translatable(shield ? "hud.eternalrealm.boss.shield"
                    : dying ? "hud.eternalrealm.boss.defeated" : "hud.eternalrealm.boss.invulnerable");
            graphics.drawCenteredString(font, label, screenWidth / 2, barTop + 1, 0xFFFFFFFF);
        }

        // ---- under the bar: HP, phases, enrage
        int infoTop = barTop + barHeight + 3;
        String hpText = String.format(Locale.ROOT, "%,d / %,d", Math.round(hp), Math.round(max));
        graphics.drawString(font, hpText, left, infoTop, 0xFFB8B0A8);
        int phases = Mth.clamp((int) number(d, "phases", 1), 1, 8);
        int phase = Mth.clamp((int) number(d, "phase", 0), 0, phases - 1);
        if (phases > 1) {
            int pipLeft = screenWidth / 2 - (phases * 8) / 2;
            for (int i = 0; i < phases; i++) {
                int x = pipLeft + i * 8;
                int pip = i < phase ? 0xFF5A5050 : i == phase ? 0xFFE84A3A : 0xFFE8C66A;
                graphics.fill(x + 1, infoTop + 1, x + 6, infoTop + 6, 0xFF000000);
                graphics.fill(x + 2, infoTop + 2, x + 5, infoTop + 5, pip);
            }
        }
        double enrage = number(d, "enrage", -1.0) - elapsed;
        Component rage;
        if (flag(d, "enraged")) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(nowNanos / 1.5E8);
            rage = Component.translatable("hud.eternalrealm.boss.enraged");
            int red = 0xFF000000 | ((int) (180 + 75 * pulse) << 16) | 0x2A20;
            graphics.drawString(font, rage, left + width - font.width(rage), infoTop, red);
        } else if (number(d, "enrage", -1.0) >= 0.0) {
            int seconds = (int) Math.max(0, Math.ceil(enrage));
            rage = Component.translatable("hud.eternalrealm.boss.enrage_in",
                    String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
            graphics.drawString(font, rage, left + width - font.width(rage), infoTop, seconds < 30 ? 0xFFFFB050
                    : 0xFF9A9088);
        }

        // ---- the cast bar
        if (d.has("cast") && d.get("cast").isJsonObject()) {
            JsonObject cast = d.getAsJsonObject("cast");
            double total = Math.max(0.05, number(cast, "total", 1.0));
            double left0 = number(cast, "left", 0.0) - elapsed;
            if (left0 > -0.1) {
                double progress = Mth.clamp(1.0 - left0 / total, 0.0, 1.0);
                int castWidth = Math.min(200, width - 40);
                int castLeft = (screenWidth - castWidth) / 2;
                int castTop = infoTop + 22;
                Component ability = Component.translatable(ClientUiState.stringOr(cast, "key", ""));
                Component label = Component.literal("◆ ").append(ability).append(" ◆");
                graphics.drawCenteredString(font, label, screenWidth / 2, castTop - 10, 0xFFFFC46A);
                graphics.fill(castLeft - 1, castTop - 1, castLeft + castWidth + 1, castTop + 5, GOLD);
                graphics.fill(castLeft, castTop, castLeft + castWidth, castTop + 4, 0xFF201410);
                boolean flash = left0 < 0.35;
                int castColor = flash && (nowNanos / 60_000_000L) % 2 == 0 ? 0xFFFFFFFF : 0xFFF08A2A;
                graphics.fill(castLeft, castTop, castLeft + (int) (castWidth * progress), castTop + 4, castColor);
            }
        }

        // ---- the damage ranking (right, below the minimap)
        if (d.has("rank") && d.get("rank").isJsonArray() && !d.getAsJsonArray("rank").isEmpty()) {
            int panelWidth = 118;
            int panelLeft = screenWidth - 4 - panelWidth;
            int rows = d.getAsJsonArray("rank").size();
            int panelTop = Mth.clamp(screenHeight / 2 - 30, 150, Math.max(150, screenHeight - 24 - rows * 12));
            graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + panelWidth + 1, panelTop + 13 + rows * 12, BORDER);
            graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 12 + rows * 12, 0xC0101014);
            graphics.drawString(font, Component.translatable("hud.eternalrealm.boss.ranking"), panelLeft + 4,
                    panelTop + 2, GOLD);
            int y = panelTop + 13;
            int lastPos = 0;
            for (JsonElement element : d.getAsJsonArray("rank")) {
                JsonObject row = element.getAsJsonObject();
                int pos = (int) number(row, "pos", 0);
                boolean me = flag(row, "me");
                if (pos > lastPos + 1 && lastPos > 0) {
                    graphics.fill(panelLeft + 4, y - 1, panelLeft + panelWidth - 4, y, 0x50FFFFFF);
                }
                lastPos = pos;
                double share = Mth.clamp(number(row, "share", 0.0), 0.0, 1.0);
                if (me) {
                    graphics.fill(panelLeft + 1, y - 1, panelLeft + panelWidth - 1, y + 11, 0x50806020);
                }
                graphics.fill(panelLeft + 3, y + 9, panelLeft + 3 + (int) ((panelWidth - 6) * share), y + 10,
                        me ? 0xFFE8C66A : 0xA0D8D0C0);
                int posColor = pos == 1 ? 0xFFFFD34A : pos == 2 ? 0xFFD8D8E0 : pos == 3 ? 0xFFD8945A : 0xFF9A9090;
                graphics.drawString(font, Integer.toString(pos), panelLeft + 4, y, posColor);
                String who = ClientUiState.stringOr(row, "name", "?");
                String percent = String.format(Locale.ROOT, "%.1f%%", share * 100.0);
                int nameWidth = panelWidth - 18 - font.width(percent) - 4;
                graphics.drawString(font, font.plainSubstrByWidth(who, nameWidth), panelLeft + 14, y,
                        jobColor(ClientUiState.stringOr(row, "job", "none")));
                graphics.drawString(font, percent, panelLeft + panelWidth - 3 - font.width(percent), y,
                        me ? 0xFFFFE8A0 : 0xFFE0D8D0);
                y += 12;
            }
        }
    }

    private static int dim(int color) {
        int r = (color >> 16 & 0xFF) * 55 / 100;
        int g = (color >> 8 & 0xFF) * 55 / 100;
        int b = (color & 0xFF) * 55 / 100;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int lighten(int color) {
        int r = Math.min(255, (color >> 16 & 0xFF) + 60);
        int g = Math.min(255, (color >> 8 & 0xFF) + 60);
        int b = Math.min(255, (color & 0xFF) + 60);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static int jobColor(String job) {
        return switch (job) {
            case "knight" -> 0xFF7FA8FF;
            case "assassin" -> 0xFFE05050;
            case "mage" -> 0xFFD08CFF;
            case "ranger" -> 0xFF7CE07C;
            case "priest" -> 0xFFFFD966;
            case "spellblade" -> 0xFF66E6E6;
            default -> 0xFFFFFFFF;
        };
    }
}
