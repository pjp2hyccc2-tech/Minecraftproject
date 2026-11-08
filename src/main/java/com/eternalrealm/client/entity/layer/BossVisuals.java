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

    /**
     * Emissive tint: red and flickering when enraged, a bright pulse while telegraphing, otherwise the theme color
     * at a calm level.
     */
    public static float[] glowColor(ERBoss boss, float ageInTicks) {
        if (boss.isEnraged()) {
            float flicker = 0.85F + 0.15F * (float) Math.sin(ageInTicks * 1.3F);
            return new float[]{1.0F * flicker, 0.12F * flicker, 0.08F * flicker};
        }
        float[] theme = theme(boss.bossId());
        float level = boss.isTelegraphing() ? 0.6F + 0.4F * (float) Math.sin(ageInTicks * 0.7F) : 0.75F;
        return new float[]{theme[0] * level, theme[1] * level, theme[2] * level};
    }
}
