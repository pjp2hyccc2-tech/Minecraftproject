package com.eternalrealm.ui.screen;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
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
                .bounds(left + PANEL_WIDTH / 2 - 50, rowY + 43, 100, 18)
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
        // the class's name once the job has grown into one (守護士), else the job's (騎士)
        com.eternalrealm.job.JobPath job = com.eternalrealm.job.JobPath.of(snapshot.jobId());
        graphics.drawString(font, Component.translatable("screen.eternalrealm.character.job_gold",
                Component.translatable(job.translationKey()), format(snapshot.gold())), left + 150, top + 14, 0xE8C66A);
        StatBlock bonus = ClientCharacterState.bonusStats();
        com.eternalrealm.character.StatPercents percents = ClientCharacterState.statPercents();

        int rowY = top + HEADER_HEIGHT;
        for (StatType stat : StatType.values()) {
            int extra = bonus.get(stat);
            int total = Math.min(StatBlock.MAX_VALUE, snapshot.totalStats().get(stat) + extra);
            int allocated = snapshot.allocatedStats().get(stat);
            graphics.drawString(font, Component.translatable(stat.translationKey()), left, rowY + 4, 0xFFFFFF);
            graphics.drawString(font, Component.literal(Integer.toString(total)), left + 30, rowY + 4, 0xFFFFFF);
            if (allocated > 0) {
                graphics.drawString(font, Component.literal("(+" + allocated + ")"), left + 58, rowY + 4, 0x7CFC7C);
            }
            if (extra > 0) {
                graphics.drawString(font, Component.literal("[+" + extra + "]"), left + 96, rowY + 4, 0x8AB4FF);
            }
            double percent = percents.get(stat);
            if (percent > 0.0) {
                // the equipment's percentage of this stat (what multiplies everything flat)
                graphics.drawString(font, Component.literal("+" + percentText(percent) + "%"), left + 140, rowY + 4,
                        0xFFD27A);
            }
            int descriptionX = left + 182;
            String description = Component.translatable(stat.descriptionKey()).getString();
            int room = left + PANEL_WIDTH - 32 - descriptionX;
            if (font.width(description) > room) {
                description = font.plainSubstrByWidth(description, room - font.width("…")) + "…";
            }
            graphics.drawString(font, description, descriptionX, rowY + 4, 0x909090);
            rowY += ROW_HEIGHT;
        }
        CombatStats combat = ClientCombatState.stats();
        if (combat != null) {
            line(graphics, rowY, 0, "screen.eternalrealm.character.combat1",
                    pct(combat.attackMultiplier() - 1.0), pct(combat.magicMultiplier() - 1.0),
                    pct(combat.rangedMultiplier() - 1.0), f1(combat.maxHealthBonus()), pct(combat.damageReduction()));
            line(graphics, rowY, 1, "screen.eternalrealm.character.combat2",
                    pct(1.0 / combat.actionSpeedFactor() - 1.0), pct(combat.critChance()),
                    pct(combat.critMultiplier()), String.format(Locale.ROOT, "%.0f", combat.maxStamina()),
                    pct(combat.moveSpeedBonus()), pct(combat.evasion()));
            line(graphics, rowY, 2, "screen.eternalrealm.character.combat3",
                    pct(1.0 - combat.cooldownFactor()), pct(1.0 - combat.chargeTimeFactor()),
                    pct(combat.dodgeDistanceFactor() - 1.0), pct(combat.knockbackResistance()),
                    pct(combat.potionBonus()));
            line(graphics, rowY, 3, "screen.eternalrealm.character.combat4",
                    pct(combat.healingMultiplier() - 1.0), pct(combat.projectileSpeedFactor() - 1.0),
                    pct(combat.lootBonus()), pct(combat.goldBonus()));
        }
    }

    private void line(GuiGraphics graphics, int rowY, int index, String key, Object... args) {
        graphics.drawString(font, Component.translatable(key, args), left, rowY + 1 + index * 10, 0xD8D8D8);
    }

    /** {@code 707} or {@code 12.5}: a stat percentage without the sign. */
    private static String percentText(double fraction) {
        double value = Math.round(fraction * 1000.0) / 10.0;
        return value >= 100.0 || value == Math.rint(value) ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String pct(double fraction) {
        return String.format(Locale.ROOT, "%.0f", fraction * 100.0);
    }

    private static String f1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
