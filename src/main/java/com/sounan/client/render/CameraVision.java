package com.sounan.client.render;

import com.sounan.item.GameItem;
import net.minecraft.client.Minecraft;

/** Is the local player looking through the phone camera (写るもの are visible only then)? */
public final class CameraVision {
    private CameraVision() {}

    public static boolean active(Minecraft mc) {
        if (mc.player == null) return false;
        return GameItem.kindOf(mc.player.getMainHandItem()) == GameItem.Kind.PHONE_CAMERA
                || GameItem.kindOf(mc.player.getOffhandItem()) == GameItem.Kind.PHONE_CAMERA;
    }
}
