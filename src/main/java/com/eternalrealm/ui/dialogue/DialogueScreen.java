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

    private static final int CHOICE_WIDTH = 170;
    private static final int CHOICE_HEIGHT = 16;

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // a soft shade behind the box only, so the speaker stays visible
        graphics.fillGradient(0, height - 110, width, height, 0x00000000, 0xA0000000);
        DialogueBox.render(graphics, font, conversation, width / 2, height - 10, width - 24, true,
                Component.translatable(conversation.choices().isEmpty() ? "dialogue.eternalrealm.hint.modal"
                        : "dialogue.eternalrealm.hint.choice"));
        if (conversation.awaitingChoice()) {
            int hovered = choiceAt(mouseX, mouseY);
            for (int i = 0; i < conversation.choices().size(); i++) {
                int[] box = choiceBox(i);
                boolean over = i == hovered;
                graphics.fill(box[0], box[1], box[0] + CHOICE_WIDTH, box[1] + CHOICE_HEIGHT, over ? 0xF0303A58
                        : 0xE60E1220);
                graphics.renderOutline(box[0], box[1], CHOICE_WIDTH, CHOICE_HEIGHT, over ? 0xFFF2D27A : 0xFFC9A55A);
                Component label = Component.literal((i + 1) + ". ").append(conversation.choices().get(i));
                graphics.drawString(font, font.plainSubstrByWidth(label.getString(), CHOICE_WIDTH - 10), box[0] + 6,
                        box[1] + 4, over ? 0xFFF2D27A : 0xFFF4F4F8, false);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** The answers stand right above the dialogue box, the first on top. */
    private int[] choiceBox(int index) {
        int boxHeight = DialogueBox.height(font, conversation, width - 24, true);
        int count = conversation.choices().size();
        int top = height - 10 - boxHeight - DialogueBox.TAB - 6 - count * (CHOICE_HEIGHT + 3);
        int left = width / 2 + Math.min(width - 24, 420) / 2 - CHOICE_WIDTH;
        return new int[]{left, top + index * (CHOICE_HEIGHT + 3)};
    }

    private int choiceAt(double mouseX, double mouseY) {
        for (int i = 0; i < conversation.choices().size(); i++) {
            int[] box = choiceBox(i);
            if (mouseX >= box[0] && mouseX < box[0] + CHOICE_WIDTH && mouseY >= box[1]
                    && mouseY < box[1] + CHOICE_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (conversation.awaitingChoice()) {
            int choice = choiceAt(mouseX, mouseY);
            if (choice >= 0) {
                answer(choice);
            }
            return true;
        }
        next();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (conversation.awaitingChoice() && key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1
                + conversation.choices().size()) {
            answer(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            next();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers); // Esc -> onClose()
    }

    private void answer(int choice) {
        ClientDialogue.choose(conversation, choice);
        if (minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(null);
        }
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
