package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;

import java.util.EnumMap;
import java.util.Map;

/**
 * The four colours a skill's light is drawn in: a near-white {@code core}, the theme's {@code glow}, a second hue for
 * accents and the deeper outer {@code deep} glow. Light is added to the scene, so even the deep colour stays bright
 * enough to show; darkness (shadow, venom, blood) is drawn as its rim of coloured light.
 */
public record Palette(int core, int glow, int accent, int deep) {
    private static final Map<SkillTheme, Palette> THEMES = new EnumMap<>(SkillTheme.class);

    static {
        THEMES.put(SkillTheme.STEEL, new Palette(0xFFFFFF, 0xC4DAFF, 0x8CC0FF, 0x4C6CB8));
        THEMES.put(SkillTheme.FIRE, new Palette(0xFFF6D8, 0xFF8A1E, 0xFFD040, 0xE03400));
        THEMES.put(SkillTheme.FROST, new Palette(0xF2FFFF, 0x78E6FF, 0xD2F6FF, 0x2C78FF));
        THEMES.put(SkillTheme.THUNDER, new Palette(0xFFFFF2, 0xFFE94C, 0xA6DCFF, 0x7C5CFF));
        THEMES.put(SkillTheme.HOLY, new Palette(0xFFFFF6, 0xFFD868, 0xFFF4B8, 0xFF9C28));
        THEMES.put(SkillTheme.SHADOW, new Palette(0xF8ECFF, 0xB866FF, 0xFF5CDC, 0x6A2CC8));
        THEMES.put(SkillTheme.ARCANE, new Palette(0xFAF2FF, 0xB87EFF, 0x5CDCFF, 0x6A2CFF));
        THEMES.put(SkillTheme.VENOM, new Palette(0xF4FFE4, 0x9AFF48, 0xDAFF6C, 0x3CB04C));
        THEMES.put(SkillTheme.BLOOD, new Palette(0xFFF0F0, 0xFF3E4E, 0xFF8C6C, 0xC8102C));
        THEMES.put(SkillTheme.WIND, new Palette(0xFFFFFF, 0xAAFFE2, 0xE2FFF8, 0x3CCAA2));
        THEMES.put(SkillTheme.EARTH, new Palette(0xFFF2D2, 0xFFAC4C, 0xFFE28C, 0xAC5C1C));
        THEMES.put(SkillTheme.LIFE, new Palette(0xF6FFF2, 0x7CFF8C, 0xFFF27C, 0x2CBA5C));
    }

    public static Palette of(SkillTheme theme) {
        return THEMES.get(theme == null ? SkillTheme.STEEL : theme);
    }

    /** ARGB with {@code alpha} (0..1, the light's strength) on {@code rgb}. */
    public static int argb(int rgb, double alpha) {
        int a = (int) Math.round(255.0 * Math.max(0.0, Math.min(1.0, alpha)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** {@code a} blended towards {@code b} by {@code t}. */
    public static int mix(int a, int b, double t) {
        double k = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** Between the glow and the accent (a second hue for half of a pattern). */
    public int between(double t) {
        return mix(glow, accent, t);
    }
}
