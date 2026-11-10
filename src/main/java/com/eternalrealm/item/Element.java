package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.SkillEffect.AfflictionKind;
import com.eternalrealm.job.SkillTheme;

import java.util.Locale;
import java.util.Optional;

/**
 * The element of a named weapon: what its hits may inflict (the weapon's proc chance, {@link WeaponDesign#proc()}) and
 * the colour of its effects. {@link #id()} is a stable key.
 *
 * @param affliction what a proc inflicts, {@code null} for {@link #NONE}
 * @param ticks      duration of the affliction
 * @param power      strength of the affliction (see {@code SkillEffect.Afflict})
 */
public enum Element {
    NONE(null, SkillTheme.STEEL, 0, 0.0),
    FIRE(AfflictionKind.BURN, SkillTheme.FIRE, 60, 0.0),
    FROST(AfflictionKind.SLOW, SkillTheme.FROST, 60, 1.0),
    THUNDER(AfflictionKind.STUN, SkillTheme.THUNDER, 12, 0.0),
    VENOM(AfflictionKind.POISON, SkillTheme.VENOM, 80, 0.2),
    HOLY(AfflictionKind.BLIND, SkillTheme.HOLY, 40, 0.0),
    SHADOW(AfflictionKind.WEAKEN, SkillTheme.SHADOW, 80, 0.15),
    BLOOD(AfflictionKind.BLEED, SkillTheme.BLOOD, 80, 0.2),
    EARTH(AfflictionKind.VULNERABLE, SkillTheme.EARTH, 80, 0.12);

    private final AfflictionKind affliction;
    private final SkillTheme theme;
    private final int ticks;
    private final double power;

    Element(AfflictionKind affliction, SkillTheme theme, int ticks, double power) {
        this.affliction = affliction;
        this.theme = theme;
        this.ticks = ticks;
        this.power = power;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "element." + ModInfo.MOD_ID + "." + id();
    }

    public Optional<AfflictionKind> affliction() {
        return Optional.ofNullable(affliction);
    }

    /** Colours of the element's effects. */
    public SkillTheme theme() {
        return theme;
    }

    public int ticks() {
        return ticks;
    }

    public double power() {
        return power;
    }
}
