package com.reisatsu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.reisatsu.Reisatsu;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.GameData;
import com.reisatsu.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * The instant photo: sliding out of the camera, developing over ~3 s from murky teal to the picture, and — when a
 * spirit is caught in it — its true name writing itself in red on the white margin. Also draws photos in the album.
 */
public final class PhotoView {
    private static final ResourceLocation FRAME = Reisatsu.id("textures/gui/photo_frame.png");
    public static final int EJECT_TICKS = 190;

    private record Eject(String id, PhotoCapture.Result r) {}

    @Nullable private static Eject current;
    private static int ticks;

    private PhotoView() {}

    public static void eject(String id, PhotoCapture.Result r) {
        current = new Eject(id, r);
        ticks = 0;
        ClientPacketHandler.ui(ModSounds.CAMERA_EJECT.get(), 0.8f, 1.0f);
    }

    public static boolean active() {
        return current != null;
    }

    public static void dismiss() {
        if (current != null && ticks < EJECT_TICKS - 18) ticks = EJECT_TICKS - 18;
    }

    public static void tick() {
        if (current == null) return;
        ticks++;
        PhotoCapture.Result r = current.r();
        if (ticks == 16) ClientPacketHandler.ui(ModSounds.PHOTO_DEVELOP.get(), 0.7f, 1.0f);
        if (r.result() == GameData.RESULT_CAPTURED && ticks == 76) {
            ClientPacketHandler.ui(ModSounds.PHOTO_NAME.get(), 0.9f, 1.0f);
        }
        if (r.result() == GameData.RESULT_CAPTURED && ticks == 86) ClientPacketHandler.ui(ModSounds.GAME_CAPTURED.get(), 0.9f, 1.0f);
        if (r.result() == GameData.RESULT_FAILED && ticks == 46) ClientPacketHandler.ui(ModSounds.PHOTO_FAIL.get(), 0.9f, 1.0f);
        if (ticks > EJECT_TICKS) current = null;
    }

    public static void render(GuiGraphics g, Font font, int w, int h, float partial) {
        if (current == null) return;
        float k = ticks + partial;
        int ph = (int) (h * 0.46f), pw = ph * 128 / 160;
        float rise = k < 16 ? 1.0f - (float) Math.pow(1.0f - k / 16.0f, 3) : 1.0f;
        float drop = k > EJECT_TICKS - 18 ? (k - (EJECT_TICKS - 18)) / 18.0f : 0.0f;
        int y = (int) (h + 4 - (ph + 18) * rise + (ph + 30) * drop * drop);
        int x = w / 2 - pw / 2 + (int) (w * 0.16f);
        float develop = Mth.clamp((k - 16) / 60.0f, 0.0f, 1.0f);
        float name = current.r().result() == GameData.RESULT_CAPTURED ? Mth.clamp((k - 76) / 26.0f, 0.0f, 1.0f) : 0.0f;
        g.pose().pushPose();
        g.pose().translate(x + pw / 2.0f, y + ph / 2.0f, 200.0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-4.0f + 3.0f * rise));
        g.pose().translate(-pw / 2.0f, -ph / 2.0f, 0.0f);
        // a soft shadow
        g.fill(4, 6, pw + 4, ph + 6, 0x50000000);
        drawPhoto(g, font, 0, 0, ph, current.id(), current.r().result(), current.r().spirit(), current.r().stars(), develop, name);
        g.pose().popPose();
    }

    /**
     * An instant photo at (x, y), {@code height} pixels tall: the frame, the picture (developing 0..1), the red name
     * on the margin (revealed 0..1) and the ★.
     */
    public static void drawPhoto(GuiGraphics g, Font font, int x, int y, int height, String id, int result, @Nullable SpiritKind spirit,
                                 int stars, float develop, float nameReveal) {
        float s = height / 160.0f;
        int width = Math.round(128 * s);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (Tex.has(FRAME)) {
            g.blit(FRAME, x, y, width, height, 0, 0, 128, 160, 128, 160);
        } else {
            g.fill(x, y, x + width, y + height, 0xFFEFEBE0);
            g.fill(x, y + height - 1, x + width, y + height, 0xFFC8C2B4);
        }
        int wx = x + Math.round(8 * s), wy = y + Math.round(8 * s), ww = Math.round(112 * s), wh = Math.round(84 * s);
        ResourceLocation tex = Photos.texture(id);
        if (tex != null) {
            float c = Mth.lerp(develop, 0.12f, 1.0f);
            g.setColor(Mth.lerp(develop, 0.10f, 1.0f), Mth.lerp(develop, 0.15f, 1.0f), Mth.lerp(develop, 0.17f, 1.0f), 1.0f);
            g.blit(tex, wx, wy, ww, wh, 0, 0, PhotoCapture.W, PhotoCapture.H, PhotoCapture.W, PhotoCapture.H);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            if (develop < 1.0f) g.fill(wx, wy, wx + ww, wy + wh, HorrorOverlay.argb((1.0f - develop) * 0.75f * c + (1.0f - develop) * 0.2f, 0x1A2A2E));
        } else {
            g.fill(wx, wy, wx + ww, wy + wh, 0xFF15191B);
        }
        // the true name, in red ink, writing itself
        if (spirit != null && nameReveal > 0.0f && result == GameData.RESULT_CAPTURED) {
            ResourceLocation nameTex = Reisatsu.id("textures/gui/photo_name_" + spirit.id + ".png");
            int nx = x, ny = y + Math.round(104 * s), nw = width, nh = Math.round(32 * s);
            if (Tex.has(nameTex)) {
                int shownW = Math.max(1, Math.round(nw * nameReveal));
                g.blit(nameTex, nx, ny, shownW, nh, 0, 0, Math.max(1, Math.round(128 * nameReveal)), 32, 128, 32);
            } else {
                String n = I18n.get(spirit.nameKey());
                int chars = Math.max(0, Math.min(n.length(), Math.round(n.length() * nameReveal + 0.49f)));
                String part = n.substring(0, chars);
                float scale = Math.max(0.6f, s * 0.9f);
                g.pose().pushPose();
                g.pose().translate(x + width / 2.0f - font.width(n) * scale / 2.0f, ny + nh / 2.0f - 4 * scale, 0);
                g.pose().scale(scale, scale, 1.0f);
                g.drawString(font, part, 0, 0, 0xFFB01818, false);
                g.pose().popPose();
            }
        }
        if (stars > 0 && result == GameData.RESULT_CAPTURED && nameReveal >= 1.0f) {
            String st = "★".repeat(Math.min(3, stars));
            g.drawString(font, st, x + width - font.width(st) - Math.round(6 * s), y + height - Math.round(14 * s), 0xA0705048, false);
        }
        RenderSystem.disableBlend();
    }
}
