package com.eternalrealm.ui.phone;

import com.eternalrealm.client.phone.PhotoCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/** Draws a photo's texture into a box, filling it (the middle of the photo when the shapes differ). */
final class PhotoArtBlit {
    private PhotoArtBlit() {
    }

    static void draw(GuiGraphics graphics, PhotoCache.Photo photo, int x, int y, int w, int h) {
        if (photo == null || !photo.ready() || w <= 0 || h <= 0) {
            return;
        }
        int tw = photo.width();
        int th = photo.height();
        float box = w / (float) h;
        float tex = tw / (float) th;
        float u = 0.0F;
        float v = 0.0F;
        int uw = tw;
        int vh = th;
        if (tex > box) {
            uw = Math.max(1, Math.round(th * box));
            u = (tw - uw) / 2.0F;
        } else {
            vh = Math.max(1, Math.round(tw / box));
            v = (th - vh) / 2.0F;
        }
        RenderSystem.enableBlend();
        graphics.blit(photo.texture(), x, y, w, h, u, v, uw, vh, tw, th);
    }
}
