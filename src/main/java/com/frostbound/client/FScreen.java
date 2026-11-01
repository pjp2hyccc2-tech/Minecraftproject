package com.frostbound.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for FROSTBOUND screens: immediate-mode drawing. Each frame the screen draws its panels and registers
 * clickable areas; clicks go to the topmost area under the mouse. Content changes every second (server
 * snapshots), so nothing is cached in widgets.
 */
public abstract class FScreen extends Screen {
    private record Hot(int x0, int y0, int x1, int y1, Runnable action) {
    }

    private final List<Hot> hots = new ArrayList<>();
    protected int mx;
    protected int my;
    private List<Component> tooltip;
    private int clipX0 = Integer.MIN_VALUE;
    private int clipY0 = Integer.MIN_VALUE;
    private int clipX1 = Integer.MAX_VALUE;
    private int clipY1 = Integer.MAX_VALUE;

    protected FScreen(Component title) {
        super(title);
    }

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hots.clear();
        tooltip = null;
        mx = mouseX;
        my = mouseY;
        draw(g, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        if (tooltip != null && !tooltip.isEmpty()) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    protected abstract void draw(GuiGraphics g, float partialTick);

    protected boolean hover(int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1 && mx >= clipX0 && mx < clipX1 && my >= clipY0 && my < clipY1;
    }

    protected void hot(int x0, int y0, int x1, int y1, Runnable action) {
        int a0 = Math.max(x0, clipX0);
        int b0 = Math.max(y0, clipY0);
        int a1 = Math.min(x1, clipX1);
        int b1 = Math.min(y1, clipY1);
        if (a0 < a1 && b0 < b1) {
            hots.add(new Hot(a0, b0, a1, b1, action));
        }
    }

    /** Themed button; returns true when hovered. */
    protected boolean button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, Runnable action) {
        return button(g, x, y, w, h, label, enabled, false, action);
    }

    protected boolean button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, boolean selected,
            Runnable action) {
        boolean hv = hover(x, y, x + w, y + h);
        Theme.button(g, x, y, w, h, label, hv, enabled, selected);
        if (enabled && action != null) {
            hot(x, y, x + w, y + h, action);
        }
        return hv;
    }

    protected void tip(int x0, int y0, int x1, int y1, Component... lines) {
        if (hover(x0, y0, x1, y1)) {
            tooltip = new ArrayList<>(Arrays.asList(lines));
        }
    }

    protected void tip(int x0, int y0, int x1, int y1, List<Component> lines) {
        if (hover(x0, y0, x1, y1)) {
            tooltip = new ArrayList<>(lines);
        }
    }

    protected void clip(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
        clipX0 = x0;
        clipY0 = y0;
        clipX1 = x1;
        clipY1 = y1;
    }

    protected void unclip(GuiGraphics g) {
        g.disableScissor();
        clipX0 = Integer.MIN_VALUE;
        clipY0 = Integer.MIN_VALUE;
        clipX1 = Integer.MAX_VALUE;
        clipY1 = Integer.MAX_VALUE;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            for (int i = hots.size() - 1; i >= 0; i--) {
                Hot h = hots.get(i);
                if (x >= h.x0() && x < h.x1() && y >= h.y0() && y < h.y1()) {
                    Sounds.click();
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }

    /** True when (x, y) is on a registered clickable area. */
    protected boolean onHot(double x, double y) {
        for (Hot h : hots) {
            if (x >= h.x0() && x < h.x1() && y >= h.y0() && y < h.y1()) {
                return true;
            }
        }
        return false;
    }
}
