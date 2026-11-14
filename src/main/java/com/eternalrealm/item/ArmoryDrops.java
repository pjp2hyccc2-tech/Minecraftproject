package com.eternalrealm.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * How often the named designs drop, and which one. Pure functions of their arguments and the injected generator; the
 * platform layer rolls the rarity and the affixes of the piece as for every drop ({@link EquipmentGenerator#roll}).
 *
 * <pre>
 * field monsters   an equipment drop becomes a field design of the field: 12 % (elites and mid-bosses 25 %)
 * wielders         a monster carrying a named weapon drops it: 2 % (elites 6 %)
 * lords (ヌシ)     signature weapon: hero (殊勲) 25 %, other contributors 8 %
 * calamities       weapon: hero 30 %, others 10 %; a piece of the calamity set: hero 50 %, others 20 %
 * dungeon bosses   a design of the dungeon: NORMAL 15 %, HARD 25 %, EX 40 %
 * boss weapon      NORMAL 3 %, HARD 8 %, EX 15 % (final raid: as its difficulty)
 * </pre>
 *
 * <p>Weapons that drop in the world — from monsters (also their equipment rolls,
 * {@code monster.DropTables}), wielders, lords and calamities — come {@value #WEAPON_DROP_RATE} times as often as these
 * numbers (owner, 2026-11-14: 「武器のドロップ率を今の0.9倍して。少し落ちにくくする」). Clear rewards of the dungeons
 * keep theirs.</p>
 */
public final class ArmoryDrops {
    /** Multiplier of every weapon (and monster equipment) drop chance in the world. */
    public static final double WEAPON_DROP_RATE = 0.9;
    public static final double FIELD_DESIGN_SHARE = 0.12;
    public static final double FIELD_DESIGN_SHARE_ELITE = 0.25;
    public static final double WIELDED_DROP = 0.02;
    public static final double WIELDED_DROP_ELITE = 0.06;
    public static final double LORD_WEAPON_HERO = 0.25;
    public static final double LORD_WEAPON = 0.08;
    public static final double CALAMITY_WEAPON_HERO = 0.30;
    public static final double CALAMITY_WEAPON = 0.10;
    public static final double CALAMITY_ARMOR_HERO = 0.50;
    public static final double CALAMITY_ARMOR = 0.20;
    /** A field design may be at most this many levels above the monster that drops it. */
    public static final int FIELD_LEVEL_MARGIN = 5;

    /** Dungeon difficulty as far as the drops care (mirrors the boss module's difficulties). */
    public enum Difficulty {
        NORMAL(0.15, 0.03),
        HARD(0.25, 0.08),
        EX(0.40, 0.15);

        private final double design;
        private final double bossWeapon;

        Difficulty(double design, double bossWeapon) {
            this.design = design;
            this.bossWeapon = bossWeapon;
        }

        /** Chance that a member's clear reward includes a design of the dungeon. */
        public double designChance() {
            return design;
        }

        /** Chance that a member's clear reward includes the boss weapon. */
        public double bossWeaponChance() {
            return bossWeapon;
        }

        public static Difficulty of(String name) {
            for (Difficulty difficulty : values()) {
                if (difficulty.name().equalsIgnoreCase(name)) {
                    return difficulty;
                }
            }
            return NORMAL;
        }
    }

    private ArmoryDrops() {
    }

    /** Share of a field monster's equipment drops that become a field design. */
    public static double fieldDesignShare(boolean elite) {
        return elite ? FIELD_DESIGN_SHARE_ELITE : FIELD_DESIGN_SHARE;
    }

    public static double wieldedDropChance(boolean elite) {
        return (elite ? WIELDED_DROP_ELITE : WIELDED_DROP) * WEAPON_DROP_RATE;
    }

    public static double lordWeaponChance(boolean hero) {
        return (hero ? LORD_WEAPON_HERO : LORD_WEAPON) * WEAPON_DROP_RATE;
    }

    public static double calamityWeaponChance(boolean hero) {
        return (hero ? CALAMITY_WEAPON_HERO : CALAMITY_WEAPON) * WEAPON_DROP_RATE;
    }

    public static double calamityArmorChance(boolean hero) {
        return hero ? CALAMITY_ARMOR_HERO : CALAMITY_ARMOR;
    }

    /**
     * A field design of {@code field} for a monster of {@code monsterLevel}: one of the designs the field's monsters
     * drop ({@link DesignSource#FIELD}, weapons and armor) at most {@link #FIELD_LEVEL_MARGIN} levels above the monster,
     * or the lowest of them if none is that low. Draws one {@code nextInt}.
     */
    public static Optional<Design> fieldDesign(int field, int monsterLevel, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        if (field < 1 || field > 10) {
            return Optional.empty();
        }
        List<Design> all = Armory.fromSource(field, DesignSource.FIELD);
        if (all.isEmpty()) {
            return Optional.empty();
        }
        List<Design> fitting = new ArrayList<>();
        int lowest = Integer.MAX_VALUE;
        for (Design design : all) {
            lowest = Math.min(lowest, design.level());
            if (design.level() <= monsterLevel + FIELD_LEVEL_MARGIN) {
                fitting.add(design);
            }
        }
        if (fitting.isEmpty()) {
            for (Design design : all) {
                if (design.level() == lowest) {
                    fitting.add(design);
                }
            }
        }
        return Optional.of(fitting.get(random.nextInt(fitting.size())));
    }

    /** One of the designs of a dungeon; draws one {@code nextInt}. */
    public static Optional<Design> dungeonDesign(String dungeonId, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        List<Design> designs = Signatures.dungeonDesigns(dungeonId);
        return designs.isEmpty() ? Optional.empty() : Optional.of(designs.get(random.nextInt(designs.size())));
    }

    /** One piece of a calamity's armor set; draws one {@code nextInt}. */
    public static Optional<ArmorPiece> calamityPiece(String calamityId, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        return Signatures.calamityArmor(calamityId).map(set -> set.pieces().get(random.nextInt(ArmorSet.PIECES)));
    }
}
