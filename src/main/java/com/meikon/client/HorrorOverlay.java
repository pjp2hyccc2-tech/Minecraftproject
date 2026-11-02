package com.meikon.client;

import com.meikon.Meikon;
import com.meikon.MeikonConfig;
import com.meikon.block.AndonBlock;
import com.meikon.block.CandleStandBlock;
import com.meikon.block.SuzuDaiBlock;
import com.meikon.block.TansuBlock;
import com.meikon.block.HeadlessDollBlock;
import com.meikon.entity.Hanayome;
import com.meikon.item.BellItem;
import com.meikon.registry.ModBlocks;
import com.meikon.registry.ModDimensions;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Full-screen horror effects, the HUD and the cinematic overlays (intro, title cards, jump scares, death). */
public final class HorrorOverlay {
    private static final ResourceLocation VIGNETTE = Meikon.id("textures/gui/vignette.png");
    private static final ResourceLocation GRAIN = Meikon.id("textures/gui/grain.png");
    private static final ResourceLocation HANDS = Meikon.id("textures/gui/hands.png");
    private static final ResourceLocation SCARE_BRIDE = Meikon.id("textures/gui/jumpscare_bride.png");
    private static final ResourceLocation SCARE_DOLL = Meikon.id("textures/gui/jumpscare_doll.png");
    private static final ResourceLocation LOGO = Meikon.id("textures/gui/title_logo.png");
    private static final ResourceLocation BELL = Meikon.id("textures/gui/hud_bell.png");
    private static final ResourceLocation BELL_EMPTY = Meikon.id("textures/gui/hud_bell_empty.png");
    private static final RandomSource RAND = RandomSource.create();
    private static final String[] NUMERALS = {"序", "壱", "弐", "参", "肆", "終"};

    private HorrorOverlay() {}

    private static boolean active(Minecraft mc) {
        return mc.level != null && mc.player != null && ModDimensions.isKakuriyo(mc.level);
    }

    private static void image(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, float r, float gr, float b, float a) {
        if (a <= 0.003f) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(r, gr, b, Mth.clamp(a, 0.0f, 1.0f));
        g.blit(tex, x, y, w, h, 0, 0, 256, 256, 256, 256);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    private static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    // ================================================================== below the HUD: the world gets darker
    public static void renderWorld(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc) || mc.options.hideGui) return;
        float strength = (float) MeikonConfig.screenEffects();
        boolean calm = MeikonConfig.flashReduction();
        float shaderMul = ShaderCompat.active() ? 0.3f : 1.0f;
        float fear = Mth.lerp(partial, ClientState.fearPrev, ClientState.fearShown) / 100.0f;
        float danger = Mth.lerp(partial, ClientState.dangerPrev, ClientState.dangerShown);
        float t = ClientState.clientTicks + partial;

        // heartbeat pulse
        float bpm = Mth.lerp(fear, 60.0f, 160.0f) + danger * 30.0f;
        float beat = (float) Math.pow(Math.max(0.0, Math.sin(t / 20.0f * bpm / 60.0f * Math.PI)), 10.0);
        float vignette = (0.28f + 0.58f * fear + 0.2f * danger + (fear > 0.5f && !calm ? beat * 0.12f * fear : 0.0f)) * strength;
        if (ClientState.hidden) vignette += 0.25f;
        image(g, VIGNETTE, 0, 0, w, h, 0.0f, 0.0f, 0.0f, vignette * shaderMul);
        if (ClientState.hidden) image(g, VIGNETTE, -w / 6, -h / 6, w + w / 3, h + h / 3, 0.0f, 0.0f, 0.0f, 0.5f);

        // film grain (the shader pack has its own)
        float grain = (0.05f + 0.16f * fear + 0.08f * danger) * strength * (ShaderCompat.active() ? 0.0f : 1.0f);
        if (grain > 0.01f) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1.0f, 1.0f, 1.0f, Mth.clamp(grain, 0.0f, 0.6f));
            g.blit(GRAIN, 0, 0, w, h, RAND.nextInt(128), RAND.nextInt(128), 128, 128, 256, 256);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
        // blood-red pulse at the edges while she is close
        if (danger > 0.3f) {
            float a = (danger - 0.3f) * 0.5f * (calm ? 0.6f : 0.6f + beat * 0.4f) * strength;
            image(g, VIGNETTE, 0, 0, w, h, 0.55f, 0.0f, 0.02f, a);
        }
        if (danger > 0.85f) {
            float a = (danger - 0.85f) * 4.0f;
            int off = (int) ((1.0f - Math.min(1.0f, a)) * 60) + w / 10;
            image(g, HANDS, -off, -off / 2, w + off * 2, h + off, 0.55f, 0.55f, 0.6f, Math.min(0.32f, a * 0.32f));
        }
        // glitches when terror peaks
        if (!calm && fear > 0.86f && RAND.nextFloat() < (fear - 0.86f) * 0.9f) {
            int bars = 1 + RAND.nextInt(3);
            for (int i = 0; i < bars; i++) {
                int y = RAND.nextInt(h);
                int bh = 1 + RAND.nextInt(5);
                g.fill(0, y, w, y + bh, RAND.nextBoolean() ? 0x90000000 : 0x40FFFFFF);
            }
        }
        // event flashes
        if (ClientState.shadowTicks >= 0) {
            float a = 1.0f - (ClientState.shadowTicks + partial) / 24.0f;
            g.fill(0, 0, w, h, argb(Math.max(0.0f, a) * 0.95f, 0x000000));
        }
        if (ClientState.flickerTicks >= 0 && !calm) {
            int f = ClientState.flickerTicks;
            if (f < 20 && (f % 6 < 2 || RAND.nextInt(5) == 0)) g.fill(0, 0, w, h, 0xC0000000);
        }
        if (ClientState.spottedTicks >= 0) {
            float a = 1.0f - (ClientState.spottedTicks + partial) / 12.0f;
            if (!calm && ClientState.spottedTicks < 2) g.fill(0, 0, w, h, argb(0.35f, 0xFFFFFF));
            image(g, VIGNETTE, 0, 0, w, h, 0.6f, 0.0f, 0.0f, Math.max(0.0f, a) * 0.9f);
        }
        if (ClientState.stingerTicks >= 0) {
            float a = 1.0f - (ClientState.stingerTicks + partial) / 14.0f;
            image(g, VIGNETTE, -w / 4, -h / 4, w + w / 2, h + h / 2, 0.0f, 0.0f, 0.0f, Math.max(0.0f, a));
        }
    }

    // ================================================================== the HUD
    public static void renderHud(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc) || mc.options.hideGui || ClientState.cinematic() || mc.player == null) return;
        Font font = mc.font;
        // the seven bells
        float a = ClientState.bellFlash > 0 ? 1.0f : 0.6f;
        RenderSystem.enableBlend();
        for (int i = 0; i < 7; i++) {
            boolean have = i < ClientState.bells;
            float pulse = have && ClientState.bellFlash > 0 && i == ClientState.bells - 1 ? 0.6f + 0.4f * Mth.sin(ClientState.bellFlash * 0.5f) : 1.0f;
            g.setColor(1.0f, 1.0f, 1.0f, a * pulse);
            g.blit(have ? BELL : BELL_EMPTY, 8 + i * 13, 8, 0, 0, 12, 12, 12, 12);
        }
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        String obj = ClientState.prologue ? "hud.meikon.objective.prologue"
                : ClientState.bells >= 7 ? "hud.meikon.objective.final" : "hud.meikon.objective.bells";
        g.drawString(font, Component.translatable(obj, ClientState.bells), 8, 24, argb(a * 0.9f, 0xC8B9A8), true);

        int cx = w / 2;
        // stamina
        if (ClientState.stamina < 99.0f || ClientState.exhausted) {
            int bw = 82;
            int y = h - 40;
            g.fill(cx - bw / 2 - 1, y - 1, cx + bw / 2 + 1, y + 3, 0x90000000);
            int fillW = Math.round(bw * ClientState.stamina / 100.0f);
            int col = ClientState.exhausted ? ((ClientState.clientTicks / 4) % 2 == 0 ? 0xFFAA2020 : 0xFF551010) : 0xFFB0703A;
            g.fill(cx - bw / 2, y, cx - bw / 2 + fillW, y + 2, col);
        }
        // breath in the closet
        if (ClientState.hidden) {
            int bw = 82;
            int y = h - 54;
            g.drawCenteredString(font, Component.translatable(ClientState.holding ? "hud.meikon.holding" : "hud.meikon.hold_hint"),
                    cx, y - 11, ClientState.holding ? 0xD0D8E8 : 0x9A9A9A);
            g.fill(cx - bw / 2 - 1, y - 1, cx + bw / 2 + 1, y + 3, 0x90000000);
            g.fill(cx - bw / 2, y, cx - bw / 2 + Math.round(bw * ClientState.breath / 100.0f), y + 2, 0xFF8AA0C0);
        }
        // the ritual
        if (mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof BellItem) {
            float p = (BellItem.RITUAL_TICKS - mc.player.getUseItemRemainingTicks()) / (float) BellItem.RITUAL_TICKS;
            int bw = 120;
            int y = h / 2 + 24;
            g.fill(cx - bw / 2 - 1, y - 1, cx + bw / 2 + 1, y + 4, 0xA0000000);
            g.fill(cx - bw / 2, y, cx - bw / 2 + Math.round(bw * p), y + 3, 0xFFE8D8A0);
            g.drawCenteredString(font, Component.translatable("hud.meikon.ritual"), cx, y + 8, 0xE8D8A0);
        } else if (ClientState.ritualReady) {
            g.drawCenteredString(font, Component.translatable("hud.meikon.ritual_ready"), cx, h / 2 + 24, 0xE8D8A0);
        }
        // what you are looking at
        Component prompt = prompt(mc);
        if (prompt != null) g.drawCenteredString(font, prompt, cx, h / 2 + 10, 0xBFB6A6);
    }

    private static Component prompt(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK || mc.level == null) return null;
        BlockState s = mc.level.getBlockState(bh.getBlockPos());
        Block b = s.getBlock();
        String key = null;
        if (b == ModBlocks.TANSU.get() && !s.getValue(TansuBlock.OPENED)) key = "search";
        else if (b == ModBlocks.OSHIIRE_DOOR.get()) key = ClientState.hidden ? "leave_closet" : "hide";
        else if (b == ModBlocks.FUSUMA_DOOR.get() || b == ModBlocks.SHOJI_DOOR.get() || b == ModBlocks.SEALED_DOOR.get())
            key = s.getValue(DoorBlock.OPEN) ? "close" : "open";
        else if (b == ModBlocks.KURA_DOOR.get()) key = "kura";
        else if (b == ModBlocks.LETTER.get()) key = "read";
        else if (b == ModBlocks.SUZU_DAI.get() && s.getValue(SuzuDaiBlock.HAS_BELL)) key = "bell";
        else if (b == ModBlocks.CANDLE_STAND.get() && !s.getValue(CandleStandBlock.LIT)) key = "light";
        else if (b == ModBlocks.ANDON.get() && !s.getValue(AndonBlock.LIT)) key = "light";
        else if (b == ModBlocks.WELL.get()) key = "well";
        else if (b == ModBlocks.HEADLESS_DOLL.get() && !s.getValue(HeadlessDollBlock.HEAD)) key = "doll";
        else if (b == ModBlocks.SAKAZUKI_TABLE.get()) key = "sakazuki";
        return key == null ? null : Component.translatable("prompt.meikon." + key);
    }

    // ================================================================== above everything: cinematics
    public static void renderCinematic(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Font font = mc.font;
        boolean scares = MeikonConfig.jumpscares();
        boolean calm = MeikonConfig.flashReduction();

        // phase title card
        if (ClientState.titleTicks >= 0 && ClientState.titleTicks < 140 && ClientState.jumpscareTicks < 0 && ClientState.deathTicks < 0) {
            float t = ClientState.titleTicks + partial;
            float a = t < 20 ? t / 20.0f : t > 110 ? (140 - t) / 30.0f : 1.0f;
            int p = Mth.clamp(ClientState.titlePhase, 0, 5);
            g.fill(0, h / 2 - 34, w, h / 2 + 30, argb(a * 0.55f, 0x000000));
            g.pose().pushPose();
            g.pose().translate(w / 2.0f, h / 2.0f - 26, 0);
            g.pose().scale(3.0f, 3.0f, 1.0f);
            g.drawCenteredString(font, NUMERALS[p], 0, 0, argb(a, 0xB01E1E));
            g.pose().popPose();
            g.drawCenteredString(font, Component.translatable("title.meikon.phase." + p), w / 2, h / 2 + 6, argb(a, 0xD8D0C4));
            g.drawCenteredString(font, Component.translatable("title.meikon.phase." + p + ".sub"), w / 2, h / 2 + 17, argb(a * 0.8f, 0x9A9088));
        }

        // intro
        if (ClientState.introTicks >= 0 && ClientState.introTicks < ClientState.IntroTiming.END) {
            float t = ClientState.introTicks + partial;
            float bg = t > ClientState.IntroTiming.FADE ? 1.0f - (t - ClientState.IntroTiming.FADE) / (ClientState.IntroTiming.END - ClientState.IntroTiming.FADE) : 1.0f;
            g.fill(0, 0, w, h, argb(bg, 0x000000));
            int[] starts = {ClientState.IntroTiming.LINE1, ClientState.IntroTiming.LINE2, ClientState.IntroTiming.LINE3};
            if (t < ClientState.IntroTiming.LOGO) {
                for (int i = 0; i < 3; i++) {
                    if (t < starts[i]) continue;
                    String line = Component.translatable("intro.meikon." + (i + 1), ClientState.introName).getString();
                    int chars = Math.min(line.length(), (int) ((t - starts[i]) / 2.2f));
                    float la = t > ClientState.IntroTiming.LOGO - 25 ? (ClientState.IntroTiming.LOGO - t) / 25.0f : 1.0f;
                    g.drawCenteredString(font, line.substring(0, chars), w / 2, h / 2 - 22 + i * 16, argb(la, 0xCFC6BA));
                }
            } else {
                float lt = t - ClientState.IntroTiming.LOGO;
                float la = Math.min(1.0f, lt / 30.0f) * bg;
                float flick = calm ? 1.0f : (RAND.nextInt(14) == 0 ? 0.55f : 1.0f);
                int lw = Math.min(w * 3 / 5, 420);
                int lh = lw / 2;
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                g.setColor(1.0f, 1.0f, 1.0f, la * flick);
                g.blit(LOGO, w / 2 - lw / 2, h / 2 - lh / 2 - 10, lw, lh, 0, 0, 512, 256, 512, 256);
                g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                RenderSystem.disableBlend();
            }
        }

        // jump scare
        if (ClientState.jumpscareTicks >= 0) {
            float t = ClientState.jumpscareTicks + partial;
            int kind = ClientState.jumpscareKind;
            int len = kind == 1 ? 22 : 32;
            if (t < len) {
                if (kind == 2) {
                    float a = Math.min(1.0f, t / 4.0f) * (t > len - 8 ? (len - t) / 8.0f : 1.0f);
                    image(g, VIGNETTE, 0, 0, w, h, 0.0f, 0.0f, 0.0f, a);
                    int off = (int) (Math.max(0.0f, 1.0f - t / 6.0f) * 60);
                    image(g, HANDS, -off, -off, w + off * 2, h + off * 2, 1.0f, 1.0f, 1.0f, a);
                } else if (scares) {
                    g.fill(0, 0, w, h, 0xFF000000);
                    float zoom = 0.92f + Math.min(1.0f, t / 5.0f) * 0.32f + (t > 12 ? (t - 12) * 0.01f : 0.0f);
                    int size = (int) (Math.max(w, h) * zoom);
                    int jx = calm ? 0 : RAND.nextInt(13) - 6;
                    int jy = calm ? 0 : RAND.nextInt(13) - 6;
                    float red = calm ? 1.0f : (RAND.nextInt(4) == 0 ? 1.0f : 0.8f);
                    image(g, kind == 1 ? SCARE_DOLL : SCARE_BRIDE, w / 2 - size / 2 + jx, h / 2 - size / 2 + jy, size, size,
                            1.0f, red, red, 1.0f);
                    if (!calm && ClientState.jumpscareTicks % 5 < 1) g.fill(0, 0, w, h, 0x50FFFFFF);
                } else {
                    g.fill(0, 0, w, h, argb(Math.min(1.0f, t / 3.0f), 0x000000));
                }
            } else if (kind == 0) {
                g.fill(0, 0, w, h, 0xFF000000);
            }
        }

        // death
        if (ClientState.deathTicks >= 0 && ClientState.deathTicks < 150) {
            float t = ClientState.deathTicks + partial;
            float a = t < 100 ? 1.0f : 1.0f - (t - 100) / 50.0f;
            g.fill(0, 0, w, h, argb(a, 0x000000));
            float ta = t < 20 ? 0.0f : Math.min(1.0f, (t - 20) / 30.0f) * a;
            g.pose().pushPose();
            g.pose().translate(w / 2.0f, h / 2.0f - 14, 0);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            g.drawCenteredString(font, Component.translatable("death.meikon.taken"), 0, 0, argb(ta, 0x9E1818));
            g.pose().popPose();
            g.drawCenteredString(font, Component.translatable("death.meikon.count", ClientState.deathCount), w / 2, h / 2 + 14, argb(ta * 0.8f, 0x8A8078));
            g.drawCenteredString(font, Component.translatable("death.meikon.hint." + (ClientState.deathCount % 5)), w / 2, h / 2 + 30, argb(ta * 0.7f, 0x8A8078));
        }

        // the ofuda burns
        if (ClientState.ofudaTicks >= 0 && ClientState.ofudaTicks < 40) {
            float t = ClientState.ofudaTicks + partial;
            float a = 1.0f - t / 40.0f;
            g.fill(0, 0, w, h, argb(a * (calm ? 0.35f : 0.75f), 0xFFE6B0));
            g.drawCenteredString(font, Component.translatable("message.meikon.ofuda"), w / 2, h / 2 + 30, argb(a, 0x3A1A00));
        }
    }

    /** True while the bride in front of the camera is chasing (for the renderer's eyes). */
    public static boolean chasing(Hanayome h) {
        return h.clientState() == Hanayome.State.CHASE || h.clientState() == Hanayome.State.CAPTURE;
    }
}
