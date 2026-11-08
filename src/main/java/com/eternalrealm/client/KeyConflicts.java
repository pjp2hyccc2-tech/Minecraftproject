package com.eternalrealm.client;

import com.eternalrealm.core.ModInfo;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shader mod (Oculus/Iris) binds K (toggle shaders) and R (reload shaders) by default: the same keys as the
 * character journal and the heavy attack, so every heavy attack reloaded the shader pack. Once per game, known key
 * mappings of other mods that sit on one of ETERNAL REALM's keys are moved to a free key (or unbound), the options are
 * saved and the player is told in chat. Bindings the player chose deliberately elsewhere are never touched.
 */
public final class KeyConflicts {
    private static final Logger LOG = LogUtils.getLogger();
    /** Foreign key mapping name -> preferred new key (GLFW key code; unknown = unbind). */
    private static final Map<String, Integer> MOVES = new LinkedHashMap<>();
    private static boolean done;

    static {
        MOVES.put("iris.keybind.toggleShaders", GLFW.GLFW_KEY_F8);
        MOVES.put("iris.keybind.reload", GLFW.GLFW_KEY_UNKNOWN);
    }

    private KeyConflicts() {
    }

    /** Client tick: runs once, as soon as the player is in a world. */
    static void resolveOnce() {
        Minecraft minecraft = Minecraft.getInstance();
        if (done || minecraft.player == null || minecraft.options == null) {
            return;
        }
        done = true;
        List<KeyMapping> ours = new ArrayList<>();
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (ClientKeyMappings.CATEGORY.equals(mapping.getCategory())) {
                ours.add(mapping);
            }
        }
        boolean changed = false;
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            Integer target = MOVES.get(mapping.getName());
            if (target == null || mapping.isUnbound() || !clashes(mapping, ours)) {
                continue;
            }
            InputConstants.Key newKey = target == GLFW.GLFW_KEY_UNKNOWN || isBound(minecraft, target)
                    ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(target);
            Component oldName = mapping.getTranslatedKeyMessage();
            mapping.setKey(newKey);
            changed = true;
            LOG.info("[{}] Moved key mapping {} off {} (clashed with ETERNAL REALM) to {}", ModInfo.MOD_ID,
                    mapping.getName(), oldName.getString(), newKey.getName());
            minecraft.gui.getChat().addMessage(Component.translatable("message.eternalrealm.keys.moved",
                    Component.translatable(mapping.getName()), oldName,
                    newKey == InputConstants.UNKNOWN ? Component.translatable("message.eternalrealm.keys.unbound")
                            : newKey.getDisplayName()).withStyle(ChatFormatting.GRAY));
        }
        if (changed) {
            KeyMapping.resetMapping();
            minecraft.options.save();
        }
    }

    private static boolean clashes(KeyMapping foreign, List<KeyMapping> ours) {
        for (KeyMapping mine : ours) {
            if (!mine.isUnbound() && mine.getKey().equals(foreign.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBound(Minecraft minecraft, int glfwKey) {
        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(glfwKey);
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }
}
