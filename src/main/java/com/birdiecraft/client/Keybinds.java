package com.birdiecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {
    public static final String CATEGORY = "key.categories.birdiecraft";

    public static final KeyMapping MENU = key("menu", GLFW.GLFW_KEY_G);
    public static final KeyMapping SCORECARD = key("scorecard", GLFW.GLFW_KEY_H);
    public static final KeyMapping SHOT = key("shot", GLFW.GLFW_KEY_B);
    public static final KeyMapping CLUB_PREV = key("club_prev", GLFW.GLFW_KEY_Z);
    public static final KeyMapping CLUB_NEXT = key("club_next", GLFW.GLFW_KEY_V);
    public static final KeyMapping MODE = key("mode", GLFW.GLFW_KEY_P);
    public static final KeyMapping SPIN_UP = key("spin_up", GLFW.GLFW_KEY_UP);
    public static final KeyMapping SPIN_DOWN = key("spin_down", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping SPIN_LEFT = key("spin_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping SPIN_RIGHT = key("spin_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping BALL_CAM = key("ball_cam", GLFW.GLFW_KEY_N);
    public static final KeyMapping AIM_PIN = key("aim_pin", GLFW.GLFW_KEY_Y);
    public static final KeyMapping CANCEL = key("cancel", GLFW.GLFW_KEY_BACKSPACE);

    public static final KeyMapping[] ALL = {MENU, SCORECARD, SHOT, CLUB_PREV, CLUB_NEXT, MODE, SPIN_UP, SPIN_DOWN,
            SPIN_LEFT, SPIN_RIGHT, BALL_CAM, AIM_PIN, CANCEL};

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.birdiecraft." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    private Keybinds() {
    }
}
