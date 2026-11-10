package com.eternalrealm.combat;

import com.eternalrealm.job.SkillTheme;

import java.util.EnumMap;
import java.util.Map;

/**
 * How the weapon skill of each weapon type (the Z key) is staged: its colour (the element of a named weapon wins over
 * the weapon type's own colour), how long it winds up before its first hit, and which shape its hits draw. Pure data
 * for the platform's effects; it never changes what the skill does.
 */
public final class WeaponSkillLooks {
    /** What the hits of a weapon skill draw. */
    public enum Stage {
        /** 片手剣: a ring of wind around the wielder. */
        WHIRL,
        /** 両手剣: a shockwave and cracks through the ground. */
        QUAKE,
        /** 双剣: crossing slashes, a big X at the end. */
        DANCE,
        /** 槍: a drilling thrust line with a spiral and a shock cone. */
        DRILL,
        /** 杖: a magic circle, a star bolt and a burst where it lands. */
        SIGIL,
        /** 弓: wind gathering on the bow, a shock ring and a streak. */
        STREAK
    }

    /**
     * @param stage       what the hits draw
     * @param rgb         the weapon type's own colour (0xRRGGBB)
     * @param accentRgb   a lighter accent (sparks, the core of rings)
     */
    public record Look(Stage stage, int rgb, int accentRgb) {
    }

    private static final Map<WeaponType, Look> LOOKS = new EnumMap<>(WeaponType.class);
    private static final Map<SkillTheme, Integer> THEMES = new EnumMap<>(SkillTheme.class);

    static {
        LOOKS.put(WeaponType.ONE_HANDED_SWORD, new Look(Stage.WHIRL, 0x7FD4FF, 0xE8F8FF));
        LOOKS.put(WeaponType.TWO_HANDED_SWORD, new Look(Stage.QUAKE, 0xFF8A2A, 0xFFE0A0));
        LOOKS.put(WeaponType.DUAL_BLADES, new Look(Stage.DANCE, 0xFF3A6A, 0xFFC0D0));
        LOOKS.put(WeaponType.SPEAR, new Look(Stage.DRILL, 0xFFD040, 0xFFF6C0));
        LOOKS.put(WeaponType.STAFF, new Look(Stage.SIGIL, 0xB070FF, 0xF0D8FF));
        LOOKS.put(WeaponType.BOW, new Look(Stage.STREAK, 0x6CF0B0, 0xE0FFF0));
        THEMES.put(SkillTheme.FIRE, 0xFF6A1A);
        THEMES.put(SkillTheme.FROST, 0x9EE8FF);
        THEMES.put(SkillTheme.THUNDER, 0xF6F28A);
        THEMES.put(SkillTheme.HOLY, 0xFFF0A8);
        THEMES.put(SkillTheme.SHADOW, 0x8A3CD8);
        THEMES.put(SkillTheme.ARCANE, 0xB070FF);
        THEMES.put(SkillTheme.VENOM, 0x7CDB3A);
        THEMES.put(SkillTheme.BLOOD, 0xD01E3A);
        THEMES.put(SkillTheme.WIND, 0xC8F8E8);
        THEMES.put(SkillTheme.EARTH, 0xC89A50);
        THEMES.put(SkillTheme.LIFE, 0x6CE070);
    }

    private WeaponSkillLooks() {
    }

    public static Look of(WeaponType type) {
        return LOOKS.get(type);
    }

    /**
     * The colour of a weapon skill: the element of the held named weapon ({@code theme}, {@code null} or
     * {@link SkillTheme#STEEL} for none) or else the weapon type's own colour.
     */
    public static int rgb(WeaponType type, SkillTheme theme) {
        Integer element = theme == null ? null : THEMES.get(theme);
        return element != null ? element : of(type).rgb();
    }

    /** Ticks from the start of the weapon skill to its first hit or launch: the wind-up. */
    public static int windUpTicks(WeaponProfile profile) {
        int first = Integer.MAX_VALUE;
        for (HitFrame frame : profile.skill().frames()) {
            first = Math.min(first, frame.offsetTicks());
        }
        return first == Integer.MAX_VALUE ? 0 : first;
    }

    /** Ticks of the weapon skill's hit or launch frames, in order. */
    public static int[] hitTicks(WeaponProfile profile) {
        return profile.skill().frames().stream().mapToInt(HitFrame::offsetTicks).sorted().toArray();
    }
}
