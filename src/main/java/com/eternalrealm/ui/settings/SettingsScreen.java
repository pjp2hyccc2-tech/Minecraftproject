package com.eternalrealm.ui.settings;

import com.eternalrealm.config.ERClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * The game's own settings in game (the owner, 2026-11-13: 「設定で旗の揺れのアニメーションを消せるようにして」): every
 * on/off option of the client config ({@code config/eternalrealm-client.toml}) as a button, saved at once. Opened
 * from the pause menu's 「ETERNAL REALM の設定」 and from the Mods list's Config button.
 */
public final class SettingsScreen extends Screen {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    /** An option: its translation key (also "...tooltip" for the hint) and the config value it switches. */
    private record Option(String key, ForgeConfigSpec.BooleanValue value) {
    }

    private static final List<Option> OPTIONS = List.of(
            new Option("flag_animation", ERClientConfig.FLAG_ANIMATION),
            new Option("quest_path", ERClientConfig.QUEST_PATH),
            new Option("fantasy_chat", ERClientConfig.FANTASY_CHAT),
            new Option("hud", ERClientConfig.HUD_ENABLED),
            new Option("minimap", ERClientConfig.MINIMAP_ENABLED),
            new Option("minimap_rotate", ERClientConfig.MINIMAP_ROTATE),
            new Option("minimap_coordinates", ERClientConfig.MINIMAP_COORDINATES),
            new Option("damage_numbers", ERClientConfig.DAMAGE_NUMBERS),
            new Option("combat_hud", ERClientConfig.COMBAT_HUD),
            new Option("camera_shake", ERClientConfig.CAMERA_SHAKE),
            new Option("weapon_animations", ERClientConfig.WEAPON_ANIMATIONS),
            new Option("skill_effects", ERClientConfig.SKILL_EFFECTS),
            new Option("dialogue_auto_advance", ERClientConfig.DIALOGUE_AUTO_ADVANCE));

    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("screen.eternalrealm.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int columns = 2;
        int rows = (OPTIONS.size() + columns - 1) / columns;
        int totalWidth = columns * BUTTON_WIDTH + (columns - 1) * GAP;
        int left = (width - totalWidth) / 2;
        int top = Math.max(32, (height - rows * (BUTTON_HEIGHT + GAP) - 40) / 2);
        for (int i = 0; i < OPTIONS.size(); i++) {
            Option option = OPTIONS.get(i);
            int x = left + (i % columns) * (BUTTON_WIDTH + GAP);
            int y = top + (i / columns) * (BUTTON_HEIGHT + GAP);
            addRenderableWidget(CycleButton.onOffBuilder(current(option.value()))
                    .withTooltip(on -> net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("screen.eternalrealm.settings." + option.key() + ".tooltip")))
                    .create(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.translatable("screen.eternalrealm.settings." + option.key()),
                            (button, on) -> set(option.value(), on)));
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, top + rows * (BUTTON_HEIGHT + GAP) + 12, 200, BUTTON_HEIGHT).build());
    }

    private static boolean current(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private static void set(ForgeConfigSpec.BooleanValue value, boolean on) {
        try {
            value.set(on);
            value.save();
        } catch (IllegalStateException e) {
            // the config is not loaded (yet): nothing to change
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
