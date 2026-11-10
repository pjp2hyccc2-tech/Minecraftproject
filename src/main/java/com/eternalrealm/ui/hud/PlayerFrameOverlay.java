package com.eternalrealm.ui.hud;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.hud.HudLayout;
import com.eternalrealm.hud.Rect;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The player frame in the top left corner: level, name (in the job's colour) and experience, the health bar with exact
 * numbers, the breath bar under water, unspent points with the key of the character screen, and the status effects
 * underneath. It replaces vanilla's hearts, armor, air, experience bar and potion icons.
 */
public final class PlayerFrameOverlay implements IGuiOverlay {
    private static final int PANEL = 0xA0101018;
    private static final int FRAME = 0x80C9A55A;
    private static final int HP_BACK = 0xFF2A0C0C;
    private static final int HP_HIGH = 0xFF4CC65A;
    private static final int HP_MID = 0xFFE0B83A;
    private static final int HP_LOW = 0xFFE0453A;
    private static final int ABSORB = 0xFFF0D060;
    private static final int EXP_BACK = 0xFF1E2430;
    private static final int EXP_FILL = 0xFF4FC3F7;
    private static final int EXP_MAX = 0xFFFFD54F;
    private static final int AIR_FILL = 0xFF8FD8FF;
    private static final int LEVEL = 0xFFE8C66A;
    private static final int POINTS = 0xFFFFFF55;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.hideGui || minecraft.options.renderDebug || player == null || !HudFrame.mmo(player)) {
            return;
        }
        HudLayout.Layout layout = HudFrame.layout();
        Rect frame = layout.player();
        Font font = minecraft.font;
        graphics.fill(frame.x(), frame.y(), frame.right(), frame.bottom(), PANEL);
        graphics.renderOutline(frame.x(), frame.y(), frame.width(), frame.height(), FRAME);
        int left = frame.x() + 3;
        int inner = frame.width() - 6;
        int y = frame.y() + 3;

        Optional<CharacterSnapshot> state = ClientCharacterState.snapshot();
        // ---- level, name, experience
        String level = state.map(s -> "Lv" + s.level()).orElse("Lv-");
        graphics.drawString(font, level, left, y, LEVEL);
        int nameLeft = left + font.width(level) + 4;
        String exp = state.map(s -> s.atCap() ? "MAX" : String.format(Locale.ROOT, "%.1f%%", s.progress() * 100.0))
                .orElse("");
        int expWidth = font.width(exp);
        int nameColor = state.map(s -> PartyHudOverlay.jobColor(s.jobId())).orElse(0xFFFFFF);
        String name = font.plainSubstrByWidth(player.getName().getString(), Math.max(0, inner - (nameLeft - left) - expWidth - 4));
        graphics.drawString(font, name, nameLeft, y, nameColor);
        graphics.drawString(font, exp, left + inner - expWidth, y, 0xFF9FD8F0);
        y += font.lineHeight + 2;

        // ---- health
        float max = Math.max(1.0F, player.getMaxHealth());
        float health = Mth.clamp(player.getHealth(), 0.0F, max);
        float ratio = health / max;
        graphics.fill(left, y, left + inner, y + 10, HP_BACK);
        int color = ratio > 0.5F ? HP_HIGH : ratio > 0.25F ? HP_MID : HP_LOW;
        int filled = Math.round(inner * ratio);
        if (filled > 0) {
            graphics.fill(left, y, left + filled, y + 10, color);
            graphics.fill(left, y, left + filled, y + 2, lighten(color));
        }
        float absorption = player.getAbsorptionAmount();
        if (absorption > 0.0F) {
            int shield = Math.min(inner, Math.round(inner * absorption / max));
            graphics.fill(left, y + 8, left + shield, y + 10, ABSORB);
        }
        String hp = String.format(Locale.ROOT, "%d / %d", Mth.ceil(health + absorption), Mth.ceil(max));
        graphics.drawString(font, hp, left + (inner - font.width(hp)) / 2, y + 1, 0xFFFFFFFF, true);
        y += 12;

        // ---- experience
        graphics.fill(left, y, left + inner, y + 3, EXP_BACK);
        double progress = state.map(s -> s.atCap() ? 1.0 : s.progress()).orElse(0.0);
        int expFilled = (int) Math.round(inner * Mth.clamp(progress, 0.0, 1.0));
        if (expFilled > 0) {
            graphics.fill(left, y, left + expFilled, y + 3, state.map(CharacterSnapshot::atCap).orElse(false) ? EXP_MAX : EXP_FILL);
        }
        y += 3;

        // ---- breath under water
        if (frame.height() >= HudLayout.PLAYER_BASE + HudLayout.AIR_ROW
                && (player.getAirSupply() < player.getMaxAirSupply() || player.isUnderWater())) {
            y += 2;
            double air = Mth.clamp(player.getAirSupply() / (double) player.getMaxAirSupply(), 0.0, 1.0);
            graphics.fill(left, y, left + inner, y + 3, EXP_BACK);
            graphics.fill(left, y, left + (int) Math.round(inner * air), y + 3, AIR_FILL);
            y += 3;
        }

        // ---- unspent points
        if (state.isPresent() && (state.get().unspentStatPoints() > 0 || state.get().unspentSkillPoints() > 0)) {
            y += 2;
            Component points = Component.translatable("hud.eternalrealm.frame.points", state.get().unspentStatPoints(),
                    state.get().unspentSkillPoints(), ClientKeyMappings.OPEN_CHARACTER.getTranslatedKeyMessage());
            graphics.drawString(font, font.plainSubstrByWidth(points.getString(), inner), left, y, POINTS);
        }

        renderEffects(minecraft, graphics, player, layout);
    }

    /** Status effect icons under the frame (beneficial first), blinking in their last 10 seconds. */
    private static void renderEffects(Minecraft minecraft, GuiGraphics graphics, LocalPlayer player,
                                      HudLayout.Layout layout) {
        Rect area = layout.effects();
        if (area.isEmpty()) {
            return;
        }
        List<MobEffectInstance> shown = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.showIcon()) {
                shown.add(effect);
            }
        }
        shown.sort((a, b) -> Boolean.compare(!a.getEffect().isBeneficial(), !b.getEffect().isBeneficial()));
        int perRow = layout.effectsPerRow();
        int capacity = perRow * (area.height() / HudLayout.EFFECT);
        RenderSystem.enableBlend();
        for (int i = 0; i < shown.size() && i < capacity; i++) {
            MobEffectInstance effect = shown.get(i);
            int x = area.x() + (i % perRow) * HudLayout.EFFECT;
            int y = area.y() + (i / perRow) * HudLayout.EFFECT;
            graphics.fill(x, y, x + 12, y + 12, effect.getEffect().isBeneficial() ? 0x90203A20 : 0x903A2020);
            float alpha = 1.0F;
            if (!effect.isAmbient() && effect.endsWithin(200)) {
                int left = effect.getDuration() / 20;
                alpha = Mth.clamp(left / 10.0F, 0.0F, 0.5F)
                        + Mth.cos(effect.getDuration() * (float) Math.PI / 5.0F) * Mth.clamp((10 - left) / 40.0F, 0.0F, 0.25F);
            }
            TextureAtlasSprite sprite = minecraft.getMobEffectTextures().get(effect.getEffect());
            graphics.setColor(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.15F, 1.0F));
            graphics.blit(x + 1, y + 1, 0, 10, 10, sprite);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        RenderSystem.disableBlend();
    }

    private static int lighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 50);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 50);
        int b = Math.min(255, (color & 0xFF) + 50);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
