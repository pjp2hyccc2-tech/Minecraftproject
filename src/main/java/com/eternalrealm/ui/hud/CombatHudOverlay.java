package com.eternalrealm.ui.hud;

import com.eternalrealm.client.ClientCombatState;
import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.DefenseEvent;
import com.eternalrealm.combat.HeavyAttack;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.forge.WeaponItem;
import com.eternalrealm.config.ERClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;

/**
 * Combat HUD: stamina under the crosshair, charge meter above it, defense popups ("パリィ！"), and the weapon skill
 * with its cooldown plus the combo counter in the bottom-left corner. Shows the client's predicted state, which the
 * server corrects with every status update.
 */
public final class CombatHudOverlay implements IGuiOverlay {
    private static final int BAR_WIDTH = 40;
    private static final int BAR_BACKGROUND = 0xA0000000;
    private static final int STAMINA_HIGH = 0xFF6BD66B;
    private static final int STAMINA_MID = 0xFFE8D24A;
    private static final int STAMINA_LOW = 0xFFE05A4A;
    private static final int CHARGE = 0xFFFFB347;
    private static final int CHARGE_FULL = 0xFFFFFFFF;
    private static final int PANEL = 0x90000000;
    private static final int COOLDOWN_SHADE = 0xA0303030;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.screen != null
                || !ERClientConfig.COMBAT_HUD.get()) {
            return; // hidden behind screens (the skill panel would overlap the chat input)
        }
        Combatant local = ClientCombatState.combatant();
        if (local == null) {
            return;
        }
        WeaponProfile weapon = WeaponItem.profileOf(minecraft.player.getMainHandItem());
        long now = ClientCombatState.tick();
        Font font = minecraft.font;
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        renderPopup(graphics, font, centerX, centerY);
        if (weapon == null) {
            return;
        }
        renderStamina(graphics, local, now, centerX, centerY);
        renderCharge(graphics, font, local, weapon, now, centerX, centerY);
        renderSkill(graphics, font, local, weapon, now, screenHeight);
    }

    private static void renderStamina(GuiGraphics graphics, Combatant local, long now, int centerX, int centerY) {
        double max = local.stats().maxStamina();
        double ratio = Math.max(0.0, Math.min(1.0, local.stamina() / max));
        boolean show = ratio < 0.999 || local.isGuarding() || local.phase(now) == Combatant.Phase.CHARGING;
        if (!show) {
            return;
        }
        int left = centerX - BAR_WIDTH / 2;
        int top = centerY + 9;
        graphics.fill(left - 1, top - 1, left + BAR_WIDTH + 1, top + 3, BAR_BACKGROUND);
        int color = ratio > 0.5 ? STAMINA_HIGH : ratio > 0.25 ? STAMINA_MID : STAMINA_LOW;
        int filled = (int) Math.round(BAR_WIDTH * ratio);
        if (filled > 0) {
            graphics.fill(left, top, left + filled, top + 2, color);
        }
    }

    private static void renderCharge(GuiGraphics graphics, Font font, Combatant local, WeaponProfile weapon, long now,
                                     int centerX, int centerY) {
        if (local.phase(now) != Combatant.Phase.CHARGING) {
            return;
        }
        HeavyAttack heavy = weapon.heavy();
        double ratio = Math.min(1.0, (double) local.chargeTicks(now) / heavy.maxChargeTicks());
        int left = centerX - BAR_WIDTH / 2;
        int top = centerY - 13;
        graphics.fill(left - 1, top - 1, left + BAR_WIDTH + 1, top + 3, BAR_BACKGROUND);
        boolean full = ratio >= 1.0;
        graphics.fill(left, top, left + (int) Math.round(BAR_WIDTH * ratio), top + 2, full ? CHARGE_FULL : CHARGE);
        int minimum = left + (int) Math.round(BAR_WIDTH * (double) heavy.minChargeTicks() / heavy.maxChargeTicks());
        graphics.fill(minimum, top - 1, minimum + 1, top + 3, 0xFFFFFFFF);
        if (full) {
            graphics.drawCenteredString(font, Component.translatable("hud.eternalrealm.combat.charge_max"), centerX,
                    top - 11, 0xFFFFE08A);
        }
    }

    private static void renderPopup(GuiGraphics graphics, Font font, int centerX, int centerY) {
        DefenseEvent popup = ClientCombatState.popup();
        if (popup == DefenseEvent.NONE) {
            return;
        }
        String key;
        int color;
        switch (popup) {
            case DODGED -> {
                key = "hud.eternalrealm.combat.dodged";
                color = 0xBFE6FF;
            }
            case PARRIED -> {
                key = "hud.eternalrealm.combat.parried";
                color = 0xFFE066;
            }
            case GUARDED -> {
                key = "hud.eternalrealm.combat.guarded";
                color = 0xC8C8C8;
            }
            case GUARD_BROKEN -> {
                key = "hud.eternalrealm.combat.guard_broken";
                color = 0xFF6655;
            }
            default -> {
                return; // NONE, GUARD_REFUSED: nothing to show
            }
        }
        long age = ClientCombatState.popupAge();
        int total = ClientCombatState.popupTicks();
        int alpha = (int) Math.round(255.0 * Math.max(0.0, Math.min(1.0, (total - age) / (total * 0.5))));
        alpha = Math.max(8, Math.min(255, alpha));
        graphics.drawCenteredString(font, Component.translatable(key), centerX, centerY - 30, (alpha << 24) | color);
    }

    private static void renderSkill(GuiGraphics graphics, Font font, Combatant local, WeaponProfile weapon, long now,
                                    int screenHeight) {
        Component name = Component.translatable("hud.eternalrealm.combat.skill",
                ClientKeyMappings.WEAPON_SKILL.getTranslatedKeyMessage(),
                Component.translatable(weapon.skill().translationKey()));
        int remaining = local.skillCooldownRemaining(now);
        Component state = remaining > 0
                ? Component.translatable("hud.eternalrealm.combat.cooldown",
                String.format(Locale.ROOT, "%.1f", remaining / 20.0))
                : Component.translatable("hud.eternalrealm.combat.ready");
        int width = Math.max(font.width(name), font.width(state)) + 8;
        int height = font.lineHeight * 2 + 6;
        int left = 4;
        int top = screenHeight - height - 4;
        graphics.fill(left, top, left + width, top + height, PANEL);
        if (remaining > 0 && local.skillCooldownTotal() > 0) {
            double ratio = Math.min(1.0, (double) remaining / local.skillCooldownTotal());
            int shadeTop = top + height - (int) Math.round(height * ratio);
            graphics.fill(left, shadeTop, left + width, top + height, COOLDOWN_SHADE);
        }
        graphics.drawString(font, name, left + 4, top + 3, 0xFFFFFF);
        graphics.drawString(font, state, left + 4, top + 4 + font.lineHeight, remaining > 0 ? 0xB0B0B0 : 0x7CFC7C);

        int step = local.currentComboStep();
        if (step >= 0) {
            Component combo = Component.translatable("hud.eternalrealm.combat.combo", step + 1, weapon.combo().size());
            graphics.drawString(font, combo, left + 2, top - font.lineHeight - 2, 0xFFE8C66A);
        }
    }
}
