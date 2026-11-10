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
    /** Job skill slots 1..6 (4 = the ultimate); the skill tree screen assigns the skills. */
    public static final KeyMapping JOB_SKILL_1 = key("job_skill_1", GLFW.GLFW_KEY_V);
    public static final KeyMapping JOB_SKILL_2 = key("job_skill_2", GLFW.GLFW_KEY_G);
    public static final KeyMapping JOB_SKILL_3 = key("job_skill_3", GLFW.GLFW_KEY_B);
    public static final KeyMapping JOB_SKILL_4 = key("job_skill_4", GLFW.GLFW_KEY_H);
    public static final KeyMapping JOB_SKILL_5 = key("job_skill_5", GLFW.GLFW_KEY_N);
    public static final KeyMapping JOB_SKILL_6 = key("job_skill_6", GLFW.GLFW_KEY_J);
    /** Next line of the dialogue box above the hotbar (the dialogue window itself takes clicks, Space and Enter). */
    public static final KeyMapping DIALOGUE_NEXT = key("dialogue_next", GLFW.GLFW_KEY_ENTER);

    /** The key of job skill slot {@code 1..6}. */
    public static KeyMapping jobSkill(int slot) {
        return switch (slot) {
            case 1 -> JOB_SKILL_1;
            case 2 -> JOB_SKILL_2;
            case 3 -> JOB_SKILL_3;
            case 4 -> JOB_SKILL_4;
            case 5 -> JOB_SKILL_5;
            case 6 -> JOB_SKILL_6;
            default -> throw new IllegalArgumentException("slot " + slot);
        };
    }

    private ClientKeyMappings() {
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key." + ModInfo.MOD_ID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }
}
