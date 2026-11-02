package com.meikon.client.gui;

import com.meikon.client.ClientState;
import com.meikon.item.NoteItem;
import com.meikon.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.TreeSet;

/** J: the notes you carry and what you must do. */
public class JournalScreen extends Screen {
    public JournalScreen() {
        super(Component.translatable("gui.meikon.journal"));
    }

    @Override
    protected void init() {
        TreeSet<Integer> ids = new TreeSet<>();
        if (minecraft != null && minecraft.player != null) {
            for (ItemStack s : minecraft.player.getInventory().items) {
                if (s.is(ModItems.NOTE.get())) ids.add(NoteItem.noteId(s));
            }
        }
        int y = height / 2 - 40;
        int col = 0;
        for (int id : ids) {
            int x = width / 2 - 155 + col * 160;
            int noteId = id;
            addRenderableWidget(Button.builder(Component.translatable("note.meikon." + id + ".title"), b -> {
                String extra = ClientState.NOTE_EXTRA.getOrDefault(noteId, Minecraft.getInstance().getUser().getName());
                Minecraft.getInstance().setScreen(new NoteScreen(noteId, extra));
            }).bounds(x, y, 150, 18).build());
            col++;
            if (col == 2) {
                col = 0;
                y += 21;
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, height / 2 - 92, 0xB01E1E);
        String obj = ClientState.bells >= 7 ? "hud.meikon.objective.final" : "hud.meikon.objective.bells";
        g.drawCenteredString(font, Component.translatable(obj, ClientState.bells), width / 2, height / 2 - 74, 0xD8D0C4);
        g.drawCenteredString(font, Component.translatable("gui.meikon.journal.hint." + Math.min(5, ClientState.phase)), width / 2, height / 2 - 60, 0x9A9088);
        if (children().isEmpty()) g.drawCenteredString(font, Component.translatable("gui.meikon.journal.empty"), width / 2, height / 2 - 30, 0x6A6058);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
