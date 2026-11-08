package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings (rebindable in Options → Controls).
 */
public final class ClientKeyMappings {
    public static final String CATEGORY = "key.categories." + ModInfo.MOD_ID;

    public static final KeyMapping OPEN_CHARACTER = new KeyMapping("key." + ModInfo.MOD_ID + ".open_character",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    private ClientKeyMappings() {
    }
}
