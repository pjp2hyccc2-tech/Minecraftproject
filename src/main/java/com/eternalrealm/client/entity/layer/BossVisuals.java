package com.eternalrealm.client.entity.layer;

import com.eternalrealm.entity.ERBoss;

/** Theme colors of the bosses and the state-dependent tint of their emissive layer. */
public final class BossVisuals {
    private BossVisuals() {
    }

    /** Theme color {r, g, b} of a boss id. */
    public static float[] theme(String bossId) {
        return switch (bossId) {
            case "b1" -> new float[]{1.0F, 0.78F, 0.32F};
            case "b2" -> new float[]{0.75F, 1.0F, 0.35F};
            case "b3" -> new float[]{1.0F, 0.72F, 0.3F};
            case "b4" -> new float[]{0.5F, 0.85F, 1.0F};
            case "b5" -> new float[]{0.6F, 0.95F, 1.0F};
            case "b6" -> new float[]{1.0F, 0.5F, 0.12F};
            case "b7" -> new float[]{0.35F, 0.9F, 1.0F};
            case "b8" -> new float[]{0.3F, 0.95F, 0.9F};
            default -> new float[]{0.8F, 0.4F, 1.0F};
        };
    }

    /** Colour the boss's power turns towards in its last forms {r, g, b}. */
    public static float[] powerColor(String bossId) {
        return switch (bossId) {
            case "b1" -> new float[]{1.0F, 0.35F, 0.15F};
            case "b2" -> new float[]{0.7F, 0.3F, 1.0F};
            case "b3" -> new float[]{1.0F, 0.45F, 0.15F};
            case "b4" -> new float[]{0.35F, 0.5F, 1.0F};
            case "b5" -> new float[]{0.85F, 0.95F, 1.0F};
            case "b6" -> new float[]{1.0F, 0.9F, 0.45F};
            case "b7" -> new float[]{1.0F, 0.25F, 0.6F};
            case "b8" -> new float[]{0.6F, 0.3F, 1.0F};
            default -> new float[]{1.0F, 0.15F, 0.3F};
        };
    }

    /** Colour of a form (0 = first): the theme colour shifting towards the power colour with every form. */
    public static float[] formColor(String bossId, int form) {
        float[] theme = theme(bossId);
        float[] power = powerColor(bossId);
        float t = Math.max(0, Math.min(3, form)) / 3.0F;
        return new float[]{theme[0] + (power[0] - theme[0]) * t, theme[1] + (power[1] - theme[1]) * t,
                theme[2] + (power[2] - theme[2]) * t};
    }

    /**
     * Emissive tint: white while transforming or dissolving, red and flickering when enraged, a bright pulse while
     * telegraphing, otherwise the colour of the current form, brighter in later forms.
     */
    public static float[] glowColor(ERBoss boss, float ageInTicks) {
        float partial = ageInTicks - boss.tickCount;
        float white = Math.max(boss.transformFlash(partial), boss.isDefeatPlaying()
                ? 0.4F + 0.6F * boss.defeatProgress(partial) : 0.0F);
        float[] base;
        if (boss.isEnraged() && !boss.isDefeatPlaying()) {
            float flicker = 0.85F + 0.15F * (float) Math.sin(ageInTicks * 1.3F);
            base = new float[]{1.0F * flicker, 0.12F * flicker, 0.08F * flicker};
        } else {
            int form = Math.min(3, boss.getPhase());
            float[] color = formColor(boss.bossId(), form);
            float level = boss.isTelegraphing() ? 0.6F + 0.4F * (float) Math.sin(ageInTicks * 0.7F)
                    : 0.75F + 0.08F * form;
            base = new float[]{Math.min(1.0F, color[0] * level), Math.min(1.0F, color[1] * level),
                    Math.min(1.0F, color[2] * level)};
        }
        if (white <= 0.0F) {
            return base;
        }
        float w = Math.min(1.0F, white);
        return new float[]{base[0] + (1.0F - base[0]) * w, base[1] + (1.0F - base[1]) * w,
                base[2] + (1.0F - base[2]) * w};
    }
}
