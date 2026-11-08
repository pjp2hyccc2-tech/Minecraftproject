package com.eternalrealm.ui.dialogue;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * A conversation with an NPC (or the prologue): the dialogue box at the bottom of the screen, the world still visible
 * above it. Every click, Space or Enter first completes the line being written, then turns the page; Esc skips to the
 * end (the lines still go to the chat log). Does not pause the game.
 */
public final class DialogueScreen extends Screen {
    private final ClientDialogue.Conversation conversation;

    DialogueScreen(ClientDialogue.Conversation conversation) {
        super(Component.translatable("screen.eternalrealm.dialogue"));
        this.conversation = conversation;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // a soft shade behind the box only, so the speaker stays visible
        graphics.fillGradient(0, height - 110, width, height, 0x00000000, 0xA0000000);
        DialogueBox.render(graphics, font, conversation, width / 2, height - 10, width - 24, true,
                Component.translatable("dialogue.eternalrealm.hint.modal"));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        next();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            next();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers); // Esc -> onClose()
    }

    private void next() {
        if (ClientDialogue.advance(conversation) && minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        ClientDialogue.skip(conversation);
        super.onClose();
    }

    /** Replaced by another screen (a menu, the death screen...): the conversation counts as read. */
    @Override
    public void removed() {
        ClientDialogue.skip(conversation);
        super.removed();
    }
}
