package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings (rebindable in Options → Controls → ETERNAL REALM). Left click (attack) and right click (guard) are the
 * vanilla attack/use keys.
 */
public final class ClientKeyMappings {
    public static final String CATEGORY = "key.categories." + ModInfo.MOD_ID;

    public static final KeyMapping OPEN_CHARACTER = key("open_character", GLFW.GLFW_KEY_K);
    /** Hold to charge, release to strike. */
    public static final KeyMapping HEAVY_ATTACK = key("heavy_attack", GLFW.GLFW_KEY_R);
    public static final KeyMapping DODGE = key("dodge", GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping WEAPON_SKILL = key("weapon_skill", GLFW.GLFW_KEY_Z);

    private ClientKeyMappings() {
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key." + ModInfo.MOD_ID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }
}
