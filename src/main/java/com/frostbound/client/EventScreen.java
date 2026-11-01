package com.frostbound.client;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** A choice event: title, text and the available choices. Closing it leaves the event pending. */
public final class EventScreen extends FScreen {
    private final Screen parent;
    private String eventId;

    public EventScreen(Screen parent) {
        super(Component.translatable("frostbound.ui.event"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CompoundTag ev = ClientCity.event();
        eventId = ev == null ? null : ev.getString("id");
        if (ev != null && ev.getBoolean("major")) {
            Sounds.play("event_major", 0.6f, 1.0f);
        } else {
            Sounds.play("ui_open", 0.5f, 0.9f);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        CompoundTag ev = ClientCity.event();
        if (ev == null || !ev.getString("id").equals(eventId)) {
            onClose();
        }
    }

    @Override
    protected void draw(GuiGraphics g, float pt) {
        g.fill(0, 0, width, height, 0x90000000);
        CompoundTag ev = ClientCity.event();
        if (ev == null) {
            return;
        }
        String id = ev.getString("id");
        List<String> args = ClientCity.strings(ev, "args");
        int w = Math.min(320, width - 40);
        int x0 = (width - w) / 2;
        Component text = ClientCity.tr("frostbound.event." + id + ".text", args);
        ListTag choices = ev.getList("choices", Tag.TAG_COMPOUND);
        int textH = Theme.wrappedHeight(text, w - 20);
        int choiceH = 0;
        for (int i = 0; i < choices.size(); i++) {
            Component c = ClientCity.tr(choices.getCompound(i).getString("key"), args);
            choiceH += Math.max(18, Theme.wrappedHeight(c, w - 28) + 8) + 3;
        }
        int h = 18 + textH + 10 + choiceH + 18;
        int y0 = Math.max(6, (height - h) / 2);
        Theme.titled(g, x0, y0, x0 + w, y0 + h, ClientCity.tr("frostbound.event." + id + ".title", args));
        if (ev.getBoolean("major")) {
            Theme.iconAt(g, "warning", x0 + w - 18, y0 + 1, 11);
        }
        int y = y0 + 18;
        y += Theme.wrapped(g, text, x0 + 10, y, w - 20, Theme.TEXT) + 10;
        for (int i = 0; i < choices.size(); i++) {
            CompoundTag c = choices.getCompound(i);
            boolean ok = c.getBoolean("ok");
            Component label = ClientCity.tr(c.getString("key"), args);
            int ch = Math.max(18, Theme.wrappedHeight(label, w - 28) + 8);
            final int choice = i;
            boolean hv = hover(x0 + 8, y, x0 + w - 8, y + ch);
            Theme.button(g, x0 + 8, y, w - 16, ch, null, hv, ok, false);
            Theme.wrapped(g, label, x0 + 14, y + 4, w - 28, ok ? (hv ? Theme.EMBER : Theme.TEXT) : Theme.TEXT_FAINT);
            if (ok) {
                hot(x0 + 8, y, x0 + w - 8, y + ch, () -> {
                    ManagementScreen.act("event", "choice", choice);
                    onClose();
                });
            } else {
                tip(x0 + 8, y, x0 + w - 8, y + ch, Component.translatable("frostbound.ui.choice_unavailable"));
            }
            y += ch + 3;
        }
        String left = String.format(Locale.ROOT, "%.0f", ev.getDouble("hoursLeft"));
        g.drawString(font, Component.translatable("frostbound.ui.event_timeout", left), x0 + 10, y + 3, Theme.TEXT_FAINT, false);
        button(g, x0 + w - 70, y, 62, 14, Component.translatable("frostbound.ui.later"), true, this::onClose);
    }
}
