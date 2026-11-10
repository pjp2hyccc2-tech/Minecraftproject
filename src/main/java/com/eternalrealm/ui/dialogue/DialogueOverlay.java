package com.eternalrealm.ui.dialogue;

import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.hud.HudLayout;
import com.eternalrealm.hud.Rect;
import com.eternalrealm.ui.hud.HudFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Ambient dialogue (entering an area or dungeon, boss lines, server news) in a compact box between the HUD's columns,
 * under the boss HUD and above the vanilla title (the HUD layout's dialogue area; on narrow screens just above the
 * action bar). It stays until the player presses the dialogue key (Enter by default), so a line said in the middle of
 * a fight can be read afterwards; see {@link ClientDialogue#tick()} for the optional auto-advance.
 */
public final class DialogueOverlay implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        ClientDialogue.Conversation conversation = ClientDialogue.ambient();
        Minecraft minecraft = Minecraft.getInstance();
        if (conversation == null || minecraft.options.hideGui || minecraft.screen instanceof DialogueScreen) {
            return;
        }
        HudLayout.Layout layout = HudFrame.layout();
        Rect area = layout.dialogue();
        if (area.isEmpty()) {
            return; // no room this frame (tiny screens); the line waits
        }
        Component hint = Component.translatable("dialogue.eternalrealm.hint.ambient",
                ClientKeyMappings.DIALOGUE_NEXT.getTranslatedKeyMessage());
        int bottom = layout.dialogueFromBottom() ? area.bottom()
                : area.y() + DialogueBox.TAB + DialogueBox.height(minecraft.font, conversation, area.width(), false);
        DialogueBox.render(graphics, minecraft.font, conversation, area.centerX(), bottom, area.width(), false, hint);
    }
}
