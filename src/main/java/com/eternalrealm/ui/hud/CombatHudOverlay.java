package com.eternalrealm.ui.hud;

import com.eternalrealm.client.ClientCombatState;
import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.SkillBinding;
import com.eternalrealm.combat.DefenseEvent;
import com.eternalrealm.combat.HeavyAttack;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.forge.WeaponItem;
import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.hud.HudLayout;
import com.eternalrealm.hud.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;
import java.util.Locale;

/**
 * Combat HUD: stamina under the crosshair, charge meter above it, the combo counter beside it, defense popups
 * ("パリィ！"), and the skill bar at the bottom centre (weapon skill with its name, then the six job skills; the
 * quick-use items follow, see {@link QuickSlotsOverlay}). Positions come from the HUD layout ({@link HudFrame}). Shows
 * the client's predicted state, which the server corrects with every status update.
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
        renderCombo(graphics, font, local, weapon, centerX, centerY);
        if (HudFrame.mmo(minecraft.player)) {
            renderSkills(graphics, font, local, weapon, now, HudFrame.layout());
        }
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
                return;
            }
        }
        long age = ClientCombatState.popupAge();
        int total = ClientCombatState.popupTicks();
        int alpha = (int) Math.round(255.0 * Math.max(0.0, Math.min(1.0, (total - age) / (total * 0.5))));
        alpha = Math.max(8, Math.min(255, alpha));
        graphics.drawCenteredString(font, Component.translatable(key), centerX, centerY - 30, (alpha << 24) | color);
    }

    /** Order of the job skill boxes on the HUD: the five active slots, then the ultimate. */
    private static final int[] HUD_ORDER = {1, 2, 3, 5, 6, 4};

    /**
     * Six job skill boxes: icon, key and cooldown; empty slots show just the key. The ultimate (slot 4) comes last,
     * a little apart and framed in gold.
     */
    private static void renderJobSkills(GuiGraphics graphics, Font font, Combatant local, long now,
                                        List<Rect> boxes) {
        for (int i = 0; i < HUD_ORDER.length && i < boxes.size(); i++) {
            int slot = HUD_ORDER[i];
            Rect box = boxes.get(i);
            boolean ultimate = slot == Combatant.ULTIMATE_SLOT;
            SkillBinding binding = local.jobSkill(slot);
            Component key = ClientKeyMappings.jobSkill(slot).getTranslatedKeyMessage();
            if (binding == null) {
                graphics.fill(box.x(), box.y(), box.right(), box.bottom(), 0x50000000);
                graphics.drawString(font, key, box.x() + 2, box.y() + 2, 0x606060);
                if (ultimate) {
                    graphics.renderOutline(box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, 0x80E8C66A);
                }
                continue;
            }
            int remaining = local.skillCooldownRemaining(slot, now);
            int total = local.skillCooldownTotal(slot);
            skillBox(graphics, font, box, skillIcon(binding.action().id()), key, remaining, total);
            if (ultimate) {
                graphics.renderOutline(box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, 0xFFE8C66A);
            }
        }
    }

    /** One skill box: icon, key in the corner, the cooldown as a shade with seconds, a green rim when ready. */
    static void skillBox(GuiGraphics graphics, Font font, Rect box, net.minecraft.resources.ResourceLocation icon,
                         Component key, int remaining, int total) {
        graphics.fill(box.x(), box.y(), box.right(), box.bottom(), PANEL);
        graphics.blit(icon, box.x() + 3, box.y() + 3, 0, 0, 16, 16, 16, 16);
        if (remaining > 0 && total > 0) {
            double ratio = Math.min(1.0, (double) remaining / total);
            int shadeTop = box.bottom() - (int) Math.round(box.height() * ratio);
            graphics.fill(box.x(), shadeTop, box.right(), box.bottom(), COOLDOWN_SHADE);
            String seconds = remaining >= 200 ? Integer.toString(remaining / 20)
                    : String.format(Locale.ROOT, "%.1f", remaining / 20.0);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            graphics.drawString(font, seconds, box.right() - font.width(seconds) - 1, box.bottom() - font.lineHeight,
                    0xFFFFFF);
            graphics.pose().popPose();
        } else {
            graphics.renderOutline(box.x(), box.y(), box.width(), box.height(), 0xFF7CFC7C);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.drawString(font, key, box.x() + 1, box.y() + 1, 0xE8C66A);
        graphics.pose().popPose();
    }

    /** The skill's icon (tools/art, 16x16, frame coloured by job). */
    public static net.minecraft.resources.ResourceLocation skillIcon(String skillId) {
        return new net.minecraft.resources.ResourceLocation("eternalrealm", "textures/gui/skill/" + skillId + ".png");
    }

    /** The weapon skill box with its name above it, then the job skills. */
    private static void renderSkills(GuiGraphics graphics, Font font, Combatant local, WeaponProfile weapon, long now,
                                     HudLayout.Layout layout) {
        int remaining = local.skillCooldownRemaining(now);
        Rect box = layout.weaponSkill();
        skillBox(graphics, font, box, skillIcon(weapon.skill().id()), ClientKeyMappings.WEAPON_SKILL.getTranslatedKeyMessage(),
                remaining, local.skillCooldownTotal());
        Rect label = layout.weaponLabel();
        String name = font.plainSubstrByWidth(Component.translatable(weapon.skill().translationKey()).getString(),
                label.width());
        graphics.drawString(font, name, label.x(), label.y() + 1, remaining > 0 ? 0xFF9A9A9A : 0xFFFFE8B0, true);
        renderJobSkills(graphics, font, local, now, layout.jobSlots());
    }

    /** The combo step ("2/4") to the right of the crosshair while a combo is running. */
    private static void renderCombo(GuiGraphics graphics, Font font, Combatant local, WeaponProfile weapon, int centerX,
                                    int centerY) {
        int step = local.currentComboStep();
        if (step >= 0) {
            Component combo = Component.translatable("hud.eternalrealm.combat.combo", step + 1, weapon.combo().size());
            graphics.drawString(font, combo, centerX + 14, centerY - 4, 0xFFE8C66A, true);
        }
    }
}
