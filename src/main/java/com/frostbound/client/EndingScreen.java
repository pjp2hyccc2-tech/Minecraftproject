package com.frostbound.client;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** The ending of the city: title, closing text, final numbers, and the choice to continue (endless mode). */
public final class EndingScreen extends FScreen {
    private final Screen parent;

    public EndingScreen(Screen parent) {
        super(Component.translatable("frostbound.ui.ending"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Sounds.play("event_major", 0.7f, 0.8f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent instanceof ManagementScreen ? parent : null);
    }

    @Override
    protected void draw(GuiGraphics g, float pt) {
        g.fill(0, 0, width, height, 0xB0000000);
        CompoundTag s = ClientCity.snap();
        String ending = s.getString("ending").toLowerCase(Locale.ROOT);
        boolean failed = s.getString("phase").equals("FAILED");
        int w = Math.min(340, width - 40);
        int x0 = (width - w) / 2;
        Component title = ending.isEmpty() ? Component.translatable("frostbound.ui.ending")
                : Component.translatable("frostbound.ending." + ending + ".title");
        Component text = ending.isEmpty() ? Component.empty() : Component.translatable("frostbound.ending." + ending + ".text");
        Component reason = s.contains("endReason") ? Component.translatable(s.getString("endReason")) : null;
        CompoundTag pop = ClientCity.pop();
        Component stats = Component.translatable("frostbound.ui.ending_stats", s.getInt("day"), pop.getInt("alive"), pop.getInt("deaths"),
                pop.getInt("arrivals"));
        int h = 24 + Theme.wrappedHeight(text, w - 24) + (reason == null ? 0 : Theme.wrappedHeight(reason, w - 24) + 6)
                + Theme.wrappedHeight(stats, w - 24) + 52;
        int y0 = Math.max(8, (height - h) / 2);
        Theme.panel(g, x0, y0, x0 + w, y0 + h);
        int tw = font.width(title);
        g.pose().pushPose();
        g.pose().translate(x0 + w / 2.0, y0 + 8, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, title, -tw / 2, 0, failed ? Theme.BAD : Theme.EMBER, false);
        g.pose().popPose();
        int y = y0 + 26;
        g.fill(x0 + 10, y - 3, x0 + w - 10, y - 2, Theme.ORANGE_DIM);
        y += Theme.wrapped(g, text, x0 + 12, y + 2, w - 24, Theme.TEXT) + 6;
        if (reason != null) {
            y += Theme.wrapped(g, reason, x0 + 12, y, w - 24, failed ? Theme.BAD : Theme.TEXT_DIM) + 6;
        }
        y += Theme.wrapped(g, stats, x0 + 12, y, w - 24, Theme.TEXT_DIM) + 8;
        int bw = (w - 30) / 2;
        if (!failed && s.getString("phase").equals("ENDED")) {
            button(g, x0 + 10, y, bw, 16, Component.translatable("frostbound.ui.continue_endless"), true, true, () -> {
                ManagementScreen.act("endless");
                onClose();
            });
        }
        button(g, x0 + w - 10 - bw, y, bw, 16, Component.translatable("frostbound.ui.close"), true, this::onClose);
    }
}
