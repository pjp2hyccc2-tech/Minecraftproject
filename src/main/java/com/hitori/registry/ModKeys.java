package com.hitori.registry;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Key mappings (client only; loaded from client code alone). docs/DESIGN.md §3.9. */
public final class ModKeys {
    public static final String CATEGORY = "key.categories.hitori";
    /** Hold to hold your breath. */
    public static final KeyMapping BREATH = new KeyMapping("key.hitori.breath", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    /** Spit / speak the ritual line (context). */
    public static final KeyMapping ACTION = new KeyMapping("key.hitori.action", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    /** The camera app (laptop or phone in hand). */
    public static final KeyMapping CAMERA = new KeyMapping("key.hitori.camera", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    /** The printout: the procedure, the rules, the checklist. */
    public static final KeyMapping PRINTOUT = new KeyMapping("key.hitori.printout", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    private ModKeys() {}
}
