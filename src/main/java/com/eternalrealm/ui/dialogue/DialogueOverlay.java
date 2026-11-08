package com.eternalrealm.ui.dialogue;

import com.eternalrealm.client.ClientKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Ambient dialogue (entering an area or dungeon, boss lines, server news) in a compact box above the hotbar. It stays
 * until the player presses the dialogue key (Enter by default), so a line said in the middle of a fight can be read
 * afterwards; see {@link ClientDialogue#tick()} for the optional auto-advance.
 */
public final class DialogueOverlay implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        ClientDialogue.Conversation conversation = ClientDialogue.ambient();
        Minecraft minecraft = Minecraft.getInstance();
        if (conversation == null || minecraft.options.hideGui || minecraft.screen instanceof DialogueScreen) {
            return;
        }
        Component hint = Component.translatable("dialogue.eternalrealm.hint.ambient",
                ClientKeyMappings.DIALOGUE_NEXT.getTranslatedKeyMessage());
        DialogueBox.render(graphics, minecraft.font, conversation, screenWidth / 2, screenHeight - 72,
                Math.min(screenWidth - 40, 340), false, hint);
    }
}
