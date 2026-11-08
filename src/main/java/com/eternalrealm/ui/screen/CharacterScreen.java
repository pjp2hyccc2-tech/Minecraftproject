package com.eternalrealm.ui.screen;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.StatType;
import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.network.forge.AllocateStatPacket;
import com.eternalrealm.network.forge.ERNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Character screen: level, EXP, the six stats and "+" buttons to spend points.
 *
 * <p>Every "+" sends an allocation <em>request</em>; the numbers on screen only change when the server answers with a
 * new snapshot. Buttons are disabled while no points are available, so there is no button that does nothing.</p>
 */
public final class CharacterScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int HEADER_HEIGHT = 58;
    private static final int ROW_HEIGHT = 20;
    /** Shift-click spends up to this many points (the server default limit per request is 10). */
    private static final int SHIFT_AMOUNT = 10;
    /**
     * Client-side pacing (max 4 requests/s) stays below the server's default rate limit (5/s, burst 10), so fast
     * clicking or key repeat on a focused button never produces dropped requests.
     */
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 250L;
    private static final int PANEL_BACKGROUND = 0xD0101018;
    private static final int PANEL_BORDER = 0xFF5A4A2A;

    private final Map<StatType, Button> plusButtons = new EnumMap<>(StatType.class);
    private int left;
    private int top;
    private long lastRequestMillis;

    public CharacterScreen() {
        super(Component.translatable("screen.eternalrealm.character.title"));
    }

    @Override
    protected void init() {
        plusButtons.clear();
        int panelHeight = HEADER_HEIGHT + ROW_HEIGHT * StatType.COUNT + 40;
        left = (width - PANEL_WIDTH) / 2;
        top = Math.max(8, (height - panelHeight) / 2);
        int rowY = top + HEADER_HEIGHT;
        for (StatType stat : StatType.values()) {
            Button plus = Button.builder(Component.literal("+"), button -> requestAllocation(stat))
                    .bounds(left + PANEL_WIDTH - 28, rowY, 20, 16)
                    .tooltip(Tooltip.create(Component.translatable("screen.eternalrealm.character.allocate_tooltip")))
                    .build();
            plusButtons.put(stat, addRenderableWidget(plus));
            rowY += ROW_HEIGHT;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 50, rowY + 14, 100, 20)
                .build());
        updateButtons();
    }

    private void requestAllocation(StatType stat) {
        Optional<CharacterSnapshot> snapshot = ClientCharacterState.snapshot();
        if (snapshot.isEmpty() || snapshot.get().unspentStatPoints() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRequestMillis < MIN_REQUEST_INTERVAL_MILLIS) {
            return;
        }
        lastRequestMillis = now;
        int amount = hasShiftDown() ? Math.min(snapshot.get().unspentStatPoints(), SHIFT_AMOUNT) : 1;
        ERNetwork.sendToServer(new AllocateStatPacket(stat, amount));
    }

    @Override
    public void tick() {
        super.tick();
        updateButtons();
    }

    private void updateButtons() {
        boolean canAllocate = ClientCharacterState.snapshot().map(s -> s.unspentStatPoints() > 0).orElse(false);
        for (Button button : plusButtons.values()) {
            button.active = canAllocate;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelHeight = HEADER_HEIGHT + ROW_HEIGHT * StatType.COUNT + 40;
        graphics.fill(left - 7, top - 7, left + PANEL_WIDTH + 7, top + panelHeight + 7, PANEL_BORDER);
        graphics.fill(left - 6, top - 6, left + PANEL_WIDTH + 6, top + panelHeight + 6, PANEL_BACKGROUND);
        graphics.drawCenteredString(font, title, width / 2, top, 0xFFE8C66A);

        Optional<CharacterSnapshot> state = ClientCharacterState.snapshot();
        if (state.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("hud.eternalrealm.loading"), width / 2, top + 24, 0xA0A0A0);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        CharacterSnapshot snapshot = state.get();
        graphics.drawString(font, Component.translatable("screen.eternalrealm.character.level",
                snapshot.level(), snapshot.levelCap()), left, top + 14, 0xFFFFFF);
        Component exp = snapshot.atCap()
                ? Component.translatable("screen.eternalrealm.character.exp_max")
                : Component.translatable("screen.eternalrealm.character.exp",
                format(snapshot.experience()), format(snapshot.experienceToNext()));
        graphics.drawString(font, exp, left, top + 26, 0xD0D0D0);
        graphics.drawString(font, Component.translatable("screen.eternalrealm.character.points",
                snapshot.unspentStatPoints()), left, top + 38, snapshot.unspentStatPoints() > 0 ? 0xFFFF55 : 0xA0A0A0);

        int rowY = top + HEADER_HEIGHT;
        for (StatType stat : StatType.values()) {
            int total = snapshot.totalStats().get(stat);
            int allocated = snapshot.allocatedStats().get(stat);
            graphics.drawString(font, Component.translatable(stat.translationKey()), left, rowY + 4, 0xFFFFFF);
            graphics.drawString(font, Component.literal(Integer.toString(total)), left + 40, rowY + 4, 0xFFFFFF);
            if (allocated > 0) {
                graphics.drawString(font, Component.literal("(+" + allocated + ")"), left + 70, rowY + 4, 0x7CFC7C);
            }
            graphics.drawString(font, Component.translatable(stat.descriptionKey()), left + 104, rowY + 4, 0x909090);
            rowY += ROW_HEIGHT;
        }
        graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.character.combat_note"),
                width / 2, rowY + 2, 0x808080);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
