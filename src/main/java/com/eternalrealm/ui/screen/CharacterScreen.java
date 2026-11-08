package com.eternalrealm.ui.screen;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.job.JobType;
import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.client.ClientCombatState;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.network.forge.AllocateStatPacket;
import com.eternalrealm.network.forge.ERNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
public final class CharacterScreen extends JournalScreen {
    private static final int PANEL_WIDTH = CONTENT_WIDTH;
    private static final int HEADER_HEIGHT = 58;
    private static final int ROW_HEIGHT = 20;
    /** Combat values (two lines) and the note below the stat rows. */
    private static final int FOOTER_HEIGHT = 64;
    /** Shift-click spends up to this many points (the server default limit per request is 10). */
    private static final int SHIFT_AMOUNT = 10;
    /**
     * Client-side pacing (max 4 requests/s) stays below the server's default rate limit (5/s, burst 10), so fast
     * clicking or key repeat on a focused button never produces dropped requests.
     */
    private static final long MIN_REQUEST_INTERVAL_MILLIS = 250L;

    private final Map<StatType, Button> plusButtons = new EnumMap<>(StatType.class);
    private long lastRequestMillis;

    public CharacterScreen() {
        super(Tab.STATUS);
    }

    @Override
    protected void initPage() {
        plusButtons.clear();
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
                .bounds(left + PANEL_WIDTH / 2 - 50, rowY + FOOTER_HEIGHT - 26, 100, 20)
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
    protected void renderPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Optional<CharacterSnapshot> state = ClientCharacterState.snapshot();
        if (state.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("hud.eternalrealm.loading"), left + PANEL_WIDTH / 2,
                    top + 24, 0xA0A0A0);
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
        JobType job = JobType.fromStored(snapshot.jobId());
        graphics.drawString(font, Component.translatable("screen.eternalrealm.character.job_gold",
                Component.translatable(job.translationKey()), format(snapshot.gold())), left + 150, top + 14, 0xE8C66A);
        StatBlock bonus = ClientCharacterState.bonusStats();

        int rowY = top + HEADER_HEIGHT;
        for (StatType stat : StatType.values()) {
            int extra = bonus.get(stat);
            int total = Math.min(StatBlock.MAX_VALUE, snapshot.totalStats().get(stat) + extra);
            int allocated = snapshot.allocatedStats().get(stat);
            graphics.drawString(font, Component.translatable(stat.translationKey()), left, rowY + 4, 0xFFFFFF);
            graphics.drawString(font, Component.literal(Integer.toString(total)), left + 40, rowY + 4, 0xFFFFFF);
            if (allocated > 0) {
                graphics.drawString(font, Component.literal("(+" + allocated + ")"), left + 70, rowY + 4, 0x7CFC7C);
            }
            if (extra > 0) {
                graphics.drawString(font, Component.literal("[+" + extra + "]"), left + 104, rowY + 4, 0x8AB4FF);
            }
            graphics.drawString(font, Component.translatable(stat.descriptionKey()), left + 140, rowY + 4, 0x909090);
            rowY += ROW_HEIGHT;
        }
        CombatStats combat = ClientCombatState.stats();
        if (combat != null) {
            graphics.drawString(font, Component.translatable("screen.eternalrealm.character.combat1",
                    String.format(Locale.ROOT, "%.2f", combat.attackMultiplier()),
                    String.format(Locale.ROOT, "%.1f", combat.maxHealthBonus()),
                    String.format(Locale.ROOT, "%.1f", combat.damageReduction() * 100.0)), left, rowY + 2, 0xD8D8D8);
            graphics.drawString(font, Component.translatable("screen.eternalrealm.character.combat2",
                    String.format(Locale.ROOT, "%.1f", (1.0 / combat.actionSpeedFactor() - 1.0) * 100.0),
                    String.format(Locale.ROOT, "%.1f", combat.critChance() * 100.0),
                    String.format(Locale.ROOT, "%.0f", combat.maxStamina())), left, rowY + 13, 0xD8D8D8);
        }
        graphics.drawCenteredString(font, Component.translatable("screen.eternalrealm.character.combat_note"),
                left + PANEL_WIDTH / 2, rowY + 26, 0x808080);
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
