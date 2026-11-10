package com.eternalrealm.monster;

/**
 * How menacing a field monster looks: the higher its level, the more it shows it — from nothing (Lv1–14) through
 * drifting motes of its field's element (Lv15+), a steady aura (Lv25+) and an energy shroud over its whole body
 * (Lv35+) to a bright, fast shroud with ground pulses (Lv45+). The element follows the field the monster is in, so
 * monsters of the falls steam with frost and those of the end forest bleed void. Pure; the client draws it.
 */
public final class MonsterMenace {
    /** Levels at which tiers 1..4 begin. */
    public static final int[] TIER_LEVELS = {15, 25, 35, 45};

    /** The look of a field's monsters. */
    public enum Element {
        /** Meadow, farm: plain sparkles. */
        PLAIN(0xF2E6B0),
        /** Moonshadow forest: spores. */
        NATURE(0x7CE06A),
        /** Ironvein mine: embers and smoke. */
        EMBER(0xFF8A3A),
        /** Sacred falls: frost. */
        FROST(0x9FE8FF),
        /** Ancient ruins (the old volcano): flame. */
        FLAME(0xFF4A1E),
        /** Soaring peaks: lightning. */
        STORM(0xC8B6FF),
        /** Forest of the end: void. */
        VOID(0xB040FF),
        /** Lakeside: water. */
        TIDE(0x3FA8FF),
        /** Port of winds: wind. */
        GALE(0xD8F0F0);

        private final int color;

        Element(int color) {
            this.color = color;
        }

        /** RGB of the element's aura and shroud. */
        public int color() {
            return color;
        }
    }

    private MonsterMenace() {
    }

    /** 0 (Lv1–14) .. 4 (Lv45+). */
    public static int tier(int level) {
        int tier = 0;
        for (int start : TIER_LEVELS) {
            if (level >= start) {
                tier++;
            }
        }
        return tier;
    }

    /** The element of the monsters of a field (area id 1..10; anything else is plain). */
    public static Element elementOf(int field) {
        return switch (field) {
            case 2 -> Element.NATURE;
            case 3 -> Element.EMBER;
            case 4 -> Element.FROST;
            case 5 -> Element.FLAME;
            case 6 -> Element.STORM;
            case 7 -> Element.VOID;
            case 9 -> Element.TIDE;
            case 10 -> Element.GALE;
            default -> Element.PLAIN;
        };
    }

    /** Chance per tick that a monster of this tier gives off one aura particle. */
    public static double particleChance(int tier) {
        return switch (tier) {
            case 0 -> 0.0;
            case 1 -> 0.08;
            case 2 -> 0.22;
            case 3 -> 0.35;
            default -> 0.55;
        };
    }

    /** Whether the whole body wears the energy shroud. */
    public static boolean shrouded(int tier) {
        return tier >= 3;
    }

    /**
     * The level written in a monster's name plate ("Lv12 森グモ" is the translation of key
     * {@code hud.eternalrealm.monster.nameplate.*} with the level as its first argument), or -1 if {@code text} is not
     * a level.
     */
    public static int parseLevel(String text) {
        if (text == null) {
            return -1;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > 4) {
            return -1;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i)) || trimmed.charAt(i) > '9') {
                return -1;
            }
        }
        return Integer.parseInt(trimmed);
    }
}
