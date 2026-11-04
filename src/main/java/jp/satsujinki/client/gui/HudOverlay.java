package jp.satsujinki.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.satsujinki.Satsujinki;
import jp.satsujinki.client.ClientState;
import jp.satsujinki.game.KillStyle;
import jp.satsujinki.network.RoundPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.registries.ForgeRegistries;

/** 殺人喜 HUD: joy points, 殺戮ランク emblem + gauge, combo, kill stamps, round banners, blood on the screen. */
public final class HudOverlay {
    private static final ResourceLocation[] RANKS = new ResourceLocation[7];
    private static final ResourceLocation[] STAMPS = new ResourceLocation[KillStyle.values().length];
    private static final ResourceLocation[] SCREEN_BLOOD = new ResourceLocation[4];
    private static final ResourceLocation VIGNETTE = Satsujinki.id("textures/gui/vignette.png");
    private static final int[] RANK_COLORS = {0xFF9A9A9A, 0xFF7FD0FF, 0xFF7CFF7C, 0xFFFFE04A, 0xFFFF8A2A, 0xFFFF3030, 0xFFFF1060};

    static {
        for (int i = 0; i < RANKS.length; i++) RANKS[i] = Satsujinki.id("textures/gui/rank_" + i + ".png");
        for (KillStyle k : KillStyle.values()) STAMPS[k.ordinal()] = Satsujinki.id("textures/gui/stamp_" + k.id() + ".png");
        for (int i = 0; i < 4; i++) SCREEN_BLOOD[i] = Satsujinki.id("textures/gui/blood_screen_" + i + ".png");
    }

    private HudOverlay() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        screenBlood(g, w, h);
        vignette(g, w, h, pt);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        boolean holding = isModItem(mc.player.getMainHandItem()) || isModItem(mc.player.getOffhandItem());
        if (ClientState.synced && (holding || ClientState.hudVisibleTicks > 0)) rankPanel(g, font, w, pt);
        roundTop(g, font, w);
        popups(g, font, w, h, pt);
        banner(g, font, w, h, pt);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static boolean isModItem(ItemStack st) {
        if (st.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(st.getItem());
        return key != null && Satsujinki.MODID.equals(key.getNamespace());
    }

    private static void screenBlood(GuiGraphics g, int w, int h) {
        float a = ClientState.screenBlood;
        if (a <= 0.01F) return;
        g.setColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, a * 1.1F));
        g.blit(SCREEN_BLOOD[ClientState.screenBloodVariant & 3], 0, 0, w, h, 0.0F, 0.0F, 256, 256, 256, 256);
    }

    private static void vignette(GuiGraphics g, int w, int h, float pt) {
        float t = ClientState.clientTicks + pt;
        float a = ClientState.rankFlash / 30.0F * 0.7F;
        if (ClientState.rank >= 4) a += 0.12F + 0.08F * Mth.sin(t * 0.25F) + (ClientState.rank - 4) * 0.06F;
        if (ClientState.roundActive && ClientState.roundTicksLeft < 600) a += 0.15F + 0.15F * Mth.sin(t * 0.5F);
        if (a <= 0.01F) return;
        g.setColor(1.0F, 0.08F, 0.08F, Math.min(0.85F, a));
        g.blit(VIGNETTE, 0, 0, w, h, 0.0F, 0.0F, 256, 256, 256, 256);
    }

    private static void rankPanel(GuiGraphics g, Font font, int w, float pt) {
        float t = ClientState.clientTicks + pt;
        int x = w - 112;
        int y = 6;
        // points
        String pts = String.format("%,d", Math.round(ClientState.shownPoints));
        Component pc = Component.translatable("satsujinki.hud.points", pts);
        int pcol = ClientState.pointsFlash > 0 ? 0xFFFFFFFF : 0xFFFFC84A;
        g.drawString(font, pc, w - 6 - font.width(pc), y, pcol, true);
        Component kc = Component.translatable("satsujinki.hud.kills", ClientState.totalKills);
        g.drawString(font, kc, w - 6 - font.width(kc), y + 10, 0xFFB09090, true);

        int rank = Mth.clamp(ClientState.rank, 0, 6);
        boolean active = ClientState.rank > 0 || ClientState.gauge > 0.0F || ClientState.combo > 0;
        if (!active) return;
        // emblem
        float pop = ClientState.rankFlash > 20 ? 1.0F + (ClientState.rankFlash - 20) * 0.08F : 1.0F;
        float jitter = rank >= 4 ? (rank - 3) * 0.6F : 0.0F;
        g.pose().pushPose();
        g.pose().translate(x + 48 + Mth.sin(t * 2.7F) * jitter, y + 46 + Mth.cos(t * 3.1F) * jitter, 0.0F);
        g.pose().scale(pop, pop, 1.0F);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-6.0F + Mth.sin(t * 0.15F) * 2.0F));
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(RANKS[rank], -48, -24, 96, 48, 0.0F, 0.0F, 128, 64, 128, 64);
        g.pose().popPose();
        // rank name + gauge
        Component rn = Component.translatable("satsujinki.rank." + rank);
        int col = RANK_COLORS[rank];
        g.drawString(font, rn, x + 48 - font.width(rn) / 2, y + 72, col, true);
        int bx = x + 4, by = y + 84, bw = 88;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 5, 0xC0000000);
        int fillW = (int) (bw * Mth.clamp(ClientState.gauge, 0.0F, 1.0F));
        g.fill(bx, by, bx + fillW, by + 4, col);
        if (fillW > 2) g.fill(bx + fillW - 2, by, bx + fillW, by + 4, 0xFFFFFFFF);
        // combo
        if (ClientState.combo >= 2) {
            float cs = 1.6F + (ClientState.comboPop > 0 ? ClientState.comboPop * 0.08F : 0.0F);
            Component cc = Component.translatable("satsujinki.hud.combo", ClientState.combo);
            int ccol = ClientState.combo >= 10 ? 0xFFFF2050 : ClientState.combo >= 5 ? 0xFFFF7A30 : 0xFFFFE060;
            bigText(g, font, cc, x + 48, y + 104, cs, ccol, 255);
        }
    }

    private static void roundTop(GuiGraphics g, Font font, int w) {
        if (!ClientState.roundActive) return;
        int secs = Math.max(0, ClientState.roundTicksLeft / 20);
        String time = String.format("%02d:%02d", secs / 60, secs % 60);
        Component line = Component.translatable("satsujinki.hud.round", ClientState.roundAlive, ClientState.roundTotal, time);
        int tw = font.width(line);
        int x = w / 2 - tw / 2;
        g.fill(x - 8, 2, x + tw + 8, 16, 0xA0200000);
        g.fill(x - 8, 15, x + tw + 8, 16, 0xFFB00000);
        g.drawString(font, line, x, 5, secs < 30 ? 0xFFFF4040 : 0xFFFFFFFF, true);
    }

    private static void popups(GuiGraphics g, Font font, int w, int h, float pt) {
        for (ClientState.Popup p : ClientState.POPUPS) {
            float a = p.age + pt;
            float scale = a < 6.0F ? 2.4F - a * 0.233F : 1.0F;
            float alpha = a > 35.0F ? Math.max(0.0F, (50.0F - a) / 15.0F) : 1.0F;
            float cx = w / 2.0F + p.offX;
            float cy = h / 2.0F - 46.0F - a * 0.6F;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0.0F);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(p.tilt));
            g.pose().scale(scale, scale, 1.0F);
            g.setColor(1.0F, 1.0F, 1.0F, alpha);
            g.blit(STAMPS[p.style.ordinal()], -48, -24, 96, 48, 0.0F, 0.0F, 128, 64, 128, 64);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            int ai = Mth.clamp((int) (alpha * 255.0F), 5, 255);
            Component pts = Component.translatable("satsujinki.hud.plus", String.format("%,d", p.points));
            g.drawString(font, pts, -font.width(pts) / 2, 26, (ai << 24) | 0xFFD040, true);
            if (p.combo >= 2) {
                Component cc = Component.translatable("satsujinki.hud.combo", p.combo);
                g.drawString(font, cc, -font.width(cc) / 2, 37, (ai << 24) | 0xFF6040, true);
            }
            g.pose().popPose();
        }
    }

    private static void banner(GuiGraphics g, Font font, int w, int h, float pt) {
        if (ClientState.bannerTicks <= 0) return;
        float left = ClientState.bannerTicks - pt;
        int cx = w / 2;
        int cy = h / 2 - 30;
        switch (ClientState.bannerType) {
            case RoundPacket.INTRO -> {
                float age = 110.0F - left;
                int alpha = fadeAlpha(age, left, 10.0F);
                g.fill(0, cy - 26, w, cy + 30, (Math.min(170, alpha * 2 / 3) << 24));
                typeText(g, font, Component.translatable("satsujinki.banner.intro"), cx, cy - 6, 3.0F, 0xE01010, alpha, age);
                bigText(g, font, Component.translatable("satsujinki.banner.intro_sub", ClientState.bannerValue), cx, cy + 20, 1.2F, 0xE0D0D0, alpha);
            }
            case RoundPacket.COUNT -> {
                float age = 20.0F - left;
                float s = 6.0F - Math.min(2.5F, age * 0.25F);
                bigText(g, font, Component.literal(String.valueOf(ClientState.bannerValue)), cx, cy, s, 0xFF2020, fadeAlpha(age, left, 4.0F));
            }
            case RoundPacket.GO -> {
                float age = 40.0F - left;
                float s = age < 6.0F ? 7.0F - age * 0.6F : 3.4F;
                bigText(g, font, Component.translatable("satsujinki.banner.go"), cx + Mth.sin(age * 2.0F) * 2.0F, cy, s, 0xFF1010, fadeAlpha(age, left, 8.0F));
            }
            case RoundPacket.END -> {
                RoundPacket r = ClientState.result;
                if (r == null) return;
                float age = 200.0F - left;
                int alpha = fadeAlpha(age, left, 15.0F);
                g.fill(cx - 130, cy - 34, cx + 130, cy + 70, (Math.min(200, alpha * 4 / 5) << 24) | 0x100000);
                g.fill(cx - 130, cy - 34, cx + 130, cy - 32, (alpha << 24) | 0xB00000);
                bigText(g, font, Component.translatable(r.flag ? "satsujinki.banner.perfect" : "satsujinki.banner.end"), cx, cy - 16, 2.4F,
                        r.flag ? 0xFFD040 : 0xE02020, alpha);
                int secs = r.e / 20;
                lineText(g, font, Component.translatable("satsujinki.result.kills", r.a, r.b), cx, cy + 6, alpha, 0xFFFFFF);
                lineText(g, font, Component.translatable("satsujinki.result.time", String.format("%02d:%02d", secs / 60, secs % 60)), cx, cy + 18, alpha, 0xC0C0C0);
                lineText(g, font, Component.translatable("satsujinki.result.points", String.format("%,d", r.c)), cx, cy + 30, alpha, 0xFFC84A);
                if (r.d > 0) lineText(g, font, Component.translatable("satsujinki.result.bonus", String.format("%,d", r.d)), cx, cy + 42, alpha, 0xFF8040);
                lineText(g, font, Component.translatable("satsujinki.rank." + ClientState.rank), cx, cy + 56, alpha, RANK_COLORS[Mth.clamp(ClientState.rank, 0, 6)] & 0xFFFFFF);
            }
            default -> {
            }
        }
    }

    private static int fadeAlpha(float age, float left, float fade) {
        float a = Math.min(1.0F, Math.min(age / fade, left / fade));
        return Mth.clamp((int) (a * 255.0F), 5, 255);
    }

    private static void lineText(GuiGraphics g, Font font, Component text, int cx, int y, int alpha, int rgb) {
        g.drawString(font, text, cx - font.width(text) / 2, y, (alpha << 24) | rgb, true);
    }

    private static void bigText(GuiGraphics g, Font font, Component text, float cx, float cy, float scale, int rgb, int alpha) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        int tw = font.width(text);
        g.drawString(font, text, -tw / 2, -4, (Mth.clamp(alpha, 5, 255) << 24) | (rgb & 0xFFFFFF), true);
        g.pose().popPose();
    }

    /** Typewriter reveal for the intro banner. */
    private static void typeText(GuiGraphics g, Font font, Component text, int cx, int cy, float scale, int rgb, int alpha, float age) {
        String s = text.getString();
        int n = Mth.clamp((int) (age / 2.5F), 0, s.length());
        String shown = s.substring(0, n);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        int tw = font.width(s);
        g.drawString(font, shown, -tw / 2, -4, (Mth.clamp(alpha, 5, 255) << 24) | rgb, true);
        g.pose().popPose();
    }
}
