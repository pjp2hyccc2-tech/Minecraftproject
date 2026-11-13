package com.eternalrealm.item;

import com.eternalrealm.character.StatPercents;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.job.PassiveEffect;
import com.eternalrealm.job.SkillEffect.AfflictionKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * 名品の装身具 (owner's request of 2026-11-13: more kinds of accessories and charms, from dungeon rewards, the bonus
 * chest, lord drops and the smithy): one trinket for each of the 30 lords (ヌシ) and an accessory and a charm for each
 * of the 8 dungeons and the final raid — 48 in the eight newer kinds (brooch, circlet, anklet, medal; ward mirror,
 * ward feather, prayer beads, ward doll). Each raises two stats by percentages and has an effect of its own.
 *
 * <pre>
 * main stat    +1.1 × a(L) % (charm: +1.1 × c(L) %)   a, c: Trinkets#accessoryPercent / #charmPercent
 * second stat  +0.4 × a(L) % (charm: +0.4 × c(L) %)
 * effect       one of {@link Effect}, growing with the level (critical, damage, speed, lifesteal, thorns, evasion,
 *              a last stand, an affliction on hit, skill power...)
 * level        the lord's fighting level, or the dungeon's recommended level
 * sources      the lord's rewards (6 %, the hero 15 %) or the dungeon's clear rewards (NORMAL 12 % / HARD 20 % /
 *              EX 30 %) and its bonus chest; and the smithy, from the materials of its field
 * </pre>
 *
 * <p>Ids ({@code trinket_lord_<lord>}, {@code trinket_dungeon_<dungeon>_<accessory|charm>}) and models
 * ({@code 5400 + i}) are stable; the rows are generated ({@link NamedTrinketTable}, tools/art/named_trinkets.py).</p>
 */
public final class NamedTrinkets {
    /** First custom model data number of the named trinkets. */
    public static final int MODELS = 5400;
    /** Ids of a lord's trinket start with this. */
    public static final String LORD_PREFIX = "trinket_lord_";
    /** Ids of a dungeon's trinkets start with this. */
    public static final String DUNGEON_PREFIX = "trinket_dungeon_";
    /** Chance a contributor to a lord's defeat gets its trinket (the hero: {@link #LORD_DROP_HERO}). */
    public static final double LORD_DROP = 0.06;
    public static final double LORD_DROP_HERO = 0.15;

    /** What a named trinket does beyond its two stats. */
    public enum Effect {
        CRIT, CRIT_DAMAGE, DAMAGE, SPEED, MOVE, HEALING, HEALTH, DEFENSE, LIFESTEAL, THORNS, EVASION, HEAL_ON_KILL,
        LAST_STAND, SKILL_POWER, BURN, SLOW, POISON, STUN, BLEED, FREEZE
    }

    /** Dungeon difficulty as far as the trinkets care (mirrors the dungeon module's difficulties). */
    public enum Difficulty {
        NORMAL(0.12), HARD(0.20), EX(0.30);

        private final double chance;

        Difficulty(double chance) {
            this.chance = chance;
        }

        /** Chance that a member's clear reward includes one of the dungeon's trinkets. */
        public double chance() {
            return chance;
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

    private static final List<TrinketDesign> ALL;
    private static final Map<String, TrinketDesign> BY_ID;
    private static final Map<Integer, TrinketDesign> BY_MODEL;
    private static final Map<String, Effect> EFFECTS;

    static {
        List<TrinketDesign> all = new ArrayList<>();
        Map<String, TrinketDesign> byId = new LinkedHashMap<>();
        Map<Integer, TrinketDesign> byModel = new LinkedHashMap<>();
        Map<String, Effect> effects = new LinkedHashMap<>();
        int index = 0;
        for (NamedTrinketTable.Row row : NamedTrinketTable.ROWS) {
            TrinketDesign design = build(row, MODELS + index++);
            if (byId.put(design.id(), design) != null || byModel.put(design.model(), design) != null) {
                throw new ExceptionInInitializerError("Duplicate named trinket " + design.id());
            }
            effects.put(design.id(), Effect.valueOf(row.effect()));
            all.add(design);
        }
        ALL = Collections.unmodifiableList(all);
        BY_ID = Collections.unmodifiableMap(byId);
        BY_MODEL = Collections.unmodifiableMap(byModel);
        EFFECTS = Collections.unmodifiableMap(effects);
    }

    private NamedTrinkets() {
    }

    /** All 48: the lords' (in the lords' order), then the dungeons' (accessory before charm). */
    public static List<TrinketDesign> all() {
        return ALL;
    }

    public static Optional<TrinketDesign> design(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id));
    }

    public static Optional<TrinketDesign> byModel(int model) {
        return Optional.ofNullable(BY_MODEL.get(model));
    }

    /** Whether {@code id} names one of these trinkets (by its prefix). */
    public static boolean isNamedId(String id) {
        return id != null && (id.startsWith(LORD_PREFIX) || id.startsWith(DUNGEON_PREFIX));
    }

    /** The effect of a named trinket. */
    public static Optional<Effect> effectOf(Design design) {
        return Optional.ofNullable(design == null ? null : EFFECTS.get(design.id()));
    }

    /** The trinket of a lord. */
    public static Optional<TrinketDesign> forLord(String lordId) {
        return design(LORD_PREFIX + lordId);
    }

    /** The accessory and the charm of a dungeon ("d1".."d8") or the final raid ("final"). */
    public static List<TrinketDesign> forDungeon(String dungeonId) {
        List<TrinketDesign> found = new ArrayList<>(2);
        for (TrinketDesign design : ALL) {
            if (design.sources().contains(DesignSource.DUNGEON) && design.origin().equals(dungeonId)) {
                found.add(design);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** The named trinkets of a field (the smithy lists their recipes with the field's named designs). */
    public static List<TrinketDesign> ofField(int field) {
        List<TrinketDesign> found = new ArrayList<>();
        for (TrinketDesign design : ALL) {
            if (design.field() == field) {
                found.add(design);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** Chance a contributor to a lord's defeat gets the lord's trinket. */
    public static double lordDropChance(boolean hero) {
        return hero ? LORD_DROP_HERO : LORD_DROP;
    }

    /** One of a dungeon's two trinkets (a {@code nextInt}); empty for a dungeon without trinkets. */
    public static Optional<TrinketDesign> dungeonDrop(String dungeonId, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        List<TrinketDesign> designs = forDungeon(dungeonId);
        return designs.isEmpty() ? Optional.empty() : Optional.of(designs.get(random.nextInt(designs.size())));
    }

    // ------------------------------------------------------------------ building

    private static TrinketDesign build(NamedTrinketTable.Row row, int model) {
        TrinketKind kind = TrinketKind.valueOf(row.kind());
        boolean charm = kind.slot() == EquipmentSlot.CHARM;
        int level = row.level();
        StatType main = StatType.valueOf(row.main());
        StatType second = StatType.valueOf(row.second());
        double base = charm ? Trinkets.charmPercent(level) : Trinkets.accessoryPercent(level);
        Map<StatType, Double> percents = new EnumMap<>(StatType.class);
        percents.put(main, round(1.1 * base));
        percents.merge(second, round(0.4 * base), Double::sum);
        CombatModifiers.Builder b = CombatModifiers.builder();
        List<PassiveEffect> perks = new ArrayList<>();
        switch (Effect.valueOf(row.effect())) {
            case CRIT -> b.crit(round(0.012 + 0.0007 * level));
            case CRIT_DAMAGE -> b.critDamage(round(0.05 + 0.0025 * level));
            case DAMAGE -> {
                double percent = round(0.025 + 0.0012 * level);
                switch (main) {
                    case INT -> b.magic(percent);
                    case DEX -> b.ranged(percent);
                    default -> b.physical(percent);
                }
            }
            case SPEED -> b.attackSpeed(round(0.012 + 0.0006 * level));
            case MOVE -> b.moveSpeed(round(0.012 + 0.0005 * level));
            case HEALING -> b.healing(round(0.025 + 0.0012 * level));
            case HEALTH -> b.healthPercent(round(0.03 + 0.0012 * level));
            case DEFENSE -> b.defense(r1(0.6 * level));
            case LIFESTEAL -> perks.add(new PassiveEffect.Lifesteal(round(0.012 + 0.00025 * level)));
            case THORNS -> perks.add(new PassiveEffect.Thorns(Math.min(0.2, round(0.08 + 0.002 * level))));
            case EVASION -> perks.add(new PassiveEffect.Evasion(round(0.02 + 0.0005 * level)));
            case HEAL_ON_KILL -> perks.add(new PassiveEffect.HealOnKill(round(0.02 + 0.0006 * level)));
            case LAST_STAND -> perks.add(new PassiveEffect.LastStand(round(0.06 + 0.001 * level),
                    round(0.03 + 0.0006 * level)));
            case SKILL_POWER -> perks.add(new PassiveEffect.SkillPower(round(0.03 + 0.001 * level)));
            case BURN -> perks.add(proc(AfflictionKind.BURN, 1.0, 60, 0.0, level));
            case SLOW -> perks.add(proc(AfflictionKind.SLOW, 1.0, 40, 1.0, level));
            case POISON -> perks.add(proc(AfflictionKind.POISON, 1.0, 80, 0.15, level));
            case BLEED -> perks.add(proc(AfflictionKind.BLEED, 1.0, 60, 0.15, level));
            case STUN -> perks.add(proc(AfflictionKind.STUN, 0.5, 10, 0.0, level));
            case FREEZE -> perks.add(proc(AfflictionKind.FREEZE, 0.5, 30, 1.0, level));
        }
        Set<DesignSource> sources = EnumSet.of(DesignSource.valueOf(row.source()), DesignSource.CRAFT);
        StatPercents block = StatPercents.of(percents);
        return new TrinketDesign(row.id(), model, kind, row.field(), level, sources, row.origin(), block, b.build(),
                perks, TrinketDesign.statBias(block, kind.slot()));
    }

    /** An affliction on hit: chance {@code share × (5 % + 0.08 % × level)}. */
    private static PassiveEffect.Proc proc(AfflictionKind kind, double share, int ticks, double power, int level) {
        return new PassiveEffect.Proc(kind, round(share * (0.05 + 0.0008 * level)), ticks, power, false);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double r1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
