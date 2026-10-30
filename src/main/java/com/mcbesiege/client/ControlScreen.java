package com.mcbesiege.client;

import com.mcbesiege.entity.MachineEntity;
import com.mcbesiege.entity.MachineView;
import com.mcbesiege.net.Net;
import com.mcbesiege.part.Keys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Set;

/**
 * Machine control mode: every key goes to the machine (Besiege style), the camera orbits the machine.
 * Esc leaves control mode (the machine keeps running); the simulate key resets it.
 */
public class ControlScreen extends Screen {
    private final int entityId;
    private final Set<Integer> down = new HashSet<>();
    private boolean dragging;

    public ControlScreen(int entityId) {
        super(Component.translatable("screen.mcbesiege.control"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        OrbitCamera.begin(entityId);
        int y = height - 24;
        addRenderableWidget(Button.builder(Component.translatable("button.mcbesiege.reset"), b -> {
            Net.toServer("reset", new CompoundTag());
            onClose();
        }).bounds(width - 214, y, 64, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("button.mcbesiege.place_here"), b -> {
            Net.toServer("place_here", new CompoundTag());
            onClose();
        }).bounds(width - 146, y, 76, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("button.mcbesiege.exit"), b -> onClose())
                .bounds(width - 66, y, 60, 18).build());
    }

    private MachineEntity machine() {
        if (minecraft == null || minecraft.level == null) return null;
        Entity e = minecraft.level.getEntity(entityId);
        return e instanceof MachineEntity me && !me.isRemoved() ? me : null;
    }

    private void sendKeys() {
        int[] k = new int[down.size()];
        int i = 0;
        for (int v : down) k[i++] = v;
        Net.sendKeys(entityId, k);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == Keys.ESCAPE) {
            onClose();
            return true;
        }
        if (ClientSetup.KEY_SIMULATE.getKey().getValue() == key) {
            Net.toServer("reset", new CompoundTag());
            onClose();
            return true;
        }
        if (key == 292 || key == 291 || key == 290) return super.keyPressed(key, scan, mods); // F1-F3 debug keys
        if (down.add(key)) sendKeys();
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (down.remove(key)) sendKeys();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        dragging = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            OrbitCamera.yaw += (float) dx * 0.5f;
            OrbitCamera.pitch = Math.max(-20f, Math.min(85f, OrbitCamera.pitch + (float) dy * 0.5f));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        OrbitCamera.dist = Math.max(3f, Math.min(64f, OrbitCamera.dist - (float) delta * 1.5f));
        return true;
    }

    @Override
    public void tick() {
        if (machine() == null) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        MachineEntity me = machine();
        int x = 8, y = 8;
        g.fill(4, 4, 214, 20, 0xA0201A14);
        g.drawString(font, Component.translatable("screen.mcbesiege.control_title"), x, y, 0xFFFFD27A, true);
        y += 18;
        if (me != null) {
            MachineView v = me.view;
            int rows = Math.min(v.help.size(), 16);
            g.fill(4, y - 3, 214, y + rows * 11 + 2, 0x80000000);
            for (int i = 0; i < rows; i++) {
                MachineView.HelpEntry h = v.help.get(i);
                String[] parts = h.action.split("\\.");
                Component type = parts[0].equals("spring") || parts[0].equals("rope")
                        ? Component.translatable("item.mcbesiege." + parts[0] + "_tool")
                        : Component.translatable("block.mcbesiege." + parts[0]);
                Component act = Component.translatable("action.mcbesiege." + parts[1]);
                g.drawString(font, "[" + Keys.names(h.keys) + "]", x, y, 0xFFFFE08A, false);
                g.drawString(font, Component.empty().append(type).append(": ").append(act).append(h.count > 1 ? " ×" + h.count : ""),
                        x + 46, y, 0xFFE8E8E8, false);
                y += 11;
            }
            if (v.help.isEmpty()) {
                g.drawString(font, Component.translatable("screen.mcbesiege.no_keys"), x, y, 0xFFB0B0B0, false);
                y += 11;
            }
            if (v.totalParts > 0) {
                g.drawString(font, Component.translatable("screen.mcbesiege.integrity", v.aliveParts, v.totalParts), x, y + 4, 0xFFB8D8FF, true);
            }
        }
        if (!down.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int k : down) sb.append('[').append(Keys.name(k)).append("] ");
            g.drawString(font, sb.toString(), 8, height - 40, 0xFF9CFF9C, true);
        }
        g.fill(0, height - 28, width, height, 0x90000000);
        g.drawString(font, Component.translatable("screen.mcbesiege.control_hint", ClientSetup.KEY_SIMULATE.getTranslatedKeyMessage()), 8, height - 20, 0xFFDDDDDD, false);
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        down.clear();
        Net.sendKeys(entityId, new int[0]);
        OrbitCamera.end();
        super.removed();
    }
}
