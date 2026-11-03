package com.reisatsu.registry;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Key mappings (client only; loaded from client code alone). docs/DESIGN.md §3.14. */
public final class ModKeys {
    public static final String CATEGORY = "key.categories.reisatsu";
    /** Hold to hold your breath. */
    public static final KeyMapping BREATH = new KeyMapping("key.reisatsu.breath", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    /** 霊撮帳 (album, notes). */
    public static final KeyMapping ALBUM = new KeyMapping("key.reisatsu.album", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    private ModKeys() {}
}
