package com.eternalrealm.job;

/**
 * The look and sound of a skill: the platform layer themes the particles of every hit frame, projectile and visual
 * with it (embers for fire, frost for ice, light for holy...). It never changes what the skill does.
 */
public enum SkillTheme {
    /** Plain weapon arts: sparks, blade arcs. */
    STEEL,
    FIRE,
    FROST,
    THUNDER,
    /** Light and judgment (priest, paladin skills). */
    HOLY,
    SHADOW,
    /** Raw mana: runes, violet sparks. */
    ARCANE,
    /** Poison: green mist and drops. */
    VENOM,
    BLOOD,
    WIND,
    /** Rock and dust: slams, quakes. */
    EARTH,
    /** Healing and growth: green crosses, leaves. */
    LIFE
}
