package com.eternalrealm.item;

import com.eternalrealm.character.StatPercents;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * 装身具の工房: the smithy's catalogue of named trinkets (owner's request of 2026-11-13: more accessories and charms,
 * forged at the smithy, raising the primary stats by percentages). 13 grades × 6 focuses × an accessory and a charm =
 * 156 trinkets. A trinket raises its focus stat by a percentage of everything else the character has
 * ({@link StatPercents}) and adds a small bonus of its own; rarity, enhancement and the percent affixes push it further
 * ({@link EquipmentStats}).
 *
 * <pre>
 * grade levels      1, 8, 15, 22, 30, 37, 44 (the tiers), 50, 60, 70, 80, 90, 100 (made on the abyss base)
 * accessory         focus stat +a(L),        a(L) = 3 % + 0.3 % × level   (Lv100: +33 %)
 *                   plus the focus's bonus (STR physical, INT magic, DEX ranged damage, AGI attack speed,
 *                   VIT max health, LUK critical chance)
 * charm             focus stat +0.6 × a(L),  plus max health 0.4 × level and defense 0.3 × level
 * sources           forged at the smithy ({@link Recipes#trinketRecipes()}); grades 1-8 also drop in the fields
 *                   ({@link #forDrop})
 * </pre>
 *
 * <p>The same named trinket counts once ({@link Gear#usable}), so a build that wants four STR accessories wears four
 * different ones: the best grades of the catalogue, the relics, or both — that choice is where the "工夫" lies. A
 * perfect level-100 STR build (legendary, +10, perfect percent affixes on every trinket and armor piece) reaches about
 * STR 3 900; {@code StatGrowthTest} pins the range.</p>
 *
 * <p><b>Ids and models are stable</b>: {@code trinket_<grade>_<focus>_<kind>}, model
 * {@code 5200 + grade index × 12 + focus index × 2 + (charm ? 1 : 0)}.</p>
 */
public final class Trinkets {
    /** First custom model data number of the catalogue trinkets (the relic trinkets use 5000..5199). */
    public static final int MODELS = 5200;
    /** Every catalogue id starts with this. */
    public static final String PREFIX = "trinket_";
    /** Highest grade that drops in the fields (the fields end at level 50). */
    public static final Grade LAST_FIELD_GRADE = Grade.CHAMPION;

    /** How far along a trinket is: its level, its look (metal) and the first word of its name. */
    public enum Grade {
        NOVICE(1), WAYFARER(8), WARRIOR(15), VETERAN(22), VALIANT(30), ELITE(37), HEROIC(44),
        CHAMPION(50), STARLIT(60), CELESTIAL(70), DIVINE(80), AEON(90), ETERNAL(100);

        private final int level;

        Grade(int level) {
            this.level = level;
        }

        public int level() {
            return level;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 1..13. */
        public int number() {
            return ordinal() + 1;
        }

        /** The tier of its base item (grades past the seventh are made on the abyss base). */
        public ItemTier tier() {
            return ItemTier.highestFor(level);
        }

        /** The grade of a tier (1..7 → NOVICE..HEROIC). */
        public static Grade ofTier(ItemTier tier) {
            return values()[Objects.requireNonNull(tier, "tier").number() - 1];
        }

        public static Optional<Grade> byId(String id) {
            for (Grade grade : values()) {
                if (grade.id().equals(id)) {
                    return Optional.of(grade);
                }
            }
            return Optional.empty();
        }
    }

    /** Which stat a trinket raises, and the kinds of accessory and charm that carry it. */
    public enum Focus {
        STR(StatType.STR, TrinketKind.BRACELET, TrinketKind.STONE, "veined_steel"),
        VIT(StatType.VIT, TrinketKind.AMULET, TrinketKind.TALISMAN, "guardian_plate"),
        AGI(StatType.AGI, TrinketKind.EARRING, TrinketKind.BELL, "storm_heart"),
        INT(StatType.INT, TrinketKind.RING, TrinketKind.TALISMAN, "falls_spirit_tear"),
        DEX(StatType.DEX, TrinketKind.EARRING, TrinketKind.BELL, "harvest_gem"),
        LUK(StatType.LUK, TrinketKind.RING, TrinketKind.POUCH, "water_spirit_pearl");

        private final StatType stat;
        private final TrinketKind accessory;
        private final TrinketKind charm;
        private final String elite;

        Focus(StatType stat, TrinketKind accessory, TrinketKind charm, String elite) {
            this.stat = stat;
            this.accessory = accessory;
            this.charm = charm;
            this.elite = "eternalrealm:" + elite;
        }

        public StatType stat() {
            return stat;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public TrinketKind kind(boolean charm) {
            return charm ? this.charm : accessory;
        }

        /** The elite material of the field the high grades (8+) of this focus are forged from. */
        public String eliteMaterial() {
            return elite;
        }

        public static Focus of(StatType stat) {
            for (Focus focus : values()) {
                if (focus.stat == stat) {
                    return focus;
                }
            }
            throw new IllegalArgumentException("No focus for " + stat);
        }
    }

    private static final List<TrinketDesign> ALL;
    private static final Map<String, TrinketDesign> BY_ID;
    private static final Map<Integer, TrinketDesign> BY_MODEL;

    static {
        List<TrinketDesign> all = new ArrayList<>(Grade.values().length * Focus.values().length * 2);
        Map<String, TrinketDesign> byId = new LinkedHashMap<>();
        Map<Integer, TrinketDesign> byModel = new LinkedHashMap<>();
        for (Grade grade : Grade.values()) {
            for (Focus focus : Focus.values()) {
                for (boolean charm : new boolean[]{false, true}) {
                    TrinketDesign design = build(grade, focus, charm);
                    if (byId.put(design.id(), design) != null || byModel.put(design.model(), design) != null) {
                        throw new ExceptionInInitializerError("Duplicate trinket " + design.id());
                    }
                    all.add(design);
                }
            }
        }
        ALL = Collections.unmodifiableList(all);
        BY_ID = Collections.unmodifiableMap(byId);
        BY_MODEL = Collections.unmodifiableMap(byModel);
    }

    private Trinkets() {
    }

    /** Every catalogue trinket: grade by grade, focus by focus, the accessory before the charm. */
    public static List<TrinketDesign> all() {
        return ALL;
    }

    public static Optional<TrinketDesign> design(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id));
    }

    public static Optional<TrinketDesign> byModel(int model) {
        return Optional.ofNullable(BY_MODEL.get(model));
    }

    public static TrinketDesign of(Grade grade, Focus focus, boolean charm) {
        return ALL.get((grade.ordinal() * Focus.values().length + focus.ordinal()) * 2 + (charm ? 1 : 0));
    }

    /** The trinkets of a grade (6 accessories and 6 charms, interleaved by focus). */
    public static List<TrinketDesign> ofGrade(Grade grade) {
        int from = grade.ordinal() * Focus.values().length * 2;
        return ALL.subList(from, from + Focus.values().length * 2);
    }

    /** The grade of a catalogue trinket. */
    public static Optional<Grade> gradeOf(Design design) {
        if (!(design instanceof TrinketDesign trinket) || !BY_ID.containsKey(trinket.id())) {
            return Optional.empty();
        }
        return Optional.of(Grade.values()[(trinket.model() - MODELS) / (Focus.values().length * 2)]);
    }

    /** The focus of a catalogue trinket. */
    public static Optional<Focus> focusOf(Design design) {
        if (!(design instanceof TrinketDesign trinket) || !BY_ID.containsKey(trinket.id())) {
            return Optional.empty();
        }
        return Optional.of(Focus.values()[(trinket.model() - MODELS) / 2 % Focus.values().length]);
    }

    /** Whether {@code design} is a catalogue trinket (not a relic). */
    public static boolean isCatalogue(Design design) {
        return design instanceof TrinketDesign trinket && BY_ID.get(trinket.id()) == trinket;
    }

    // ------------------------------------------------------------------ curves

    /** An accessory's focus percentage at {@code level}: 3 % + 0.3 % per level (on the 0.1 % grid). */
    public static double accessoryPercent(int level) {
        return EquipmentStats.snapPercent(0.03 + 0.003 * Checks.range("level", level, 1, Design.MAX_LEVEL));
    }

    /** A charm's focus percentage at {@code level}: 60 % of an accessory's. */
    public static double charmPercent(int level) {
        return EquipmentStats.snapPercent(0.6 * accessoryPercent(level));
    }

    // ------------------------------------------------------------------ drops

    /** Share of a field monster's equipment drops that become a catalogue trinket. */
    public static final double DROP_SHARE = 0.10;
    /** The same for elites and mid-bosses. */
    public static final double DROP_SHARE_ELITE = 0.18;
    /** An abyss-tier drop of a level-50 monster is a CHAMPION trinket this often (else HEROIC). */
    public static final double CHAMPION_SHARE = 0.5;
    /** The killer's job stats are this many times as likely as the focus of a dropped trinket. */
    public static final int JOB_FOCUS_WEIGHT = 2;

    public static double dropShare(boolean elite) {
        return elite ? DROP_SHARE_ELITE : DROP_SHARE;
    }

    /**
     * The trinket an equipment drop of {@code tier} becomes: the tier's grade (an abyss drop of a monster of level 50 or
     * more is a CHAMPION trinket half the time), a focus drawn by weight (the stats in {@code favoured}, e.g. the
     * killer's job growth, count {@link #JOB_FOCUS_WEIGHT} times), an accessory or a charm alike. Draws one
     * {@code nextInt} for the focus and one for the slot, plus one {@code nextDouble} for an abyss drop of a level-50
     * monster.
     */
    public static TrinketDesign forDrop(ItemTier tier, int monsterLevel, Set<StatType> favoured,
                                        RandomGenerator random) {
        Objects.requireNonNull(favoured, "favoured");
        Objects.requireNonNull(random, "random");
        Grade grade = Grade.ofTier(tier);
        if (tier == ItemTier.ABYSS && monsterLevel >= LAST_FIELD_GRADE.level()
                && random.nextDouble() < CHAMPION_SHARE) {
            grade = LAST_FIELD_GRADE;
        }
        int total = 0;
        for (Focus focus : Focus.values()) {
            total += favoured.contains(focus.stat()) ? JOB_FOCUS_WEIGHT : 1;
        }
        int target = random.nextInt(total);
        Focus picked = Focus.LUK;
        for (Focus focus : Focus.values()) {
            target -= favoured.contains(focus.stat()) ? JOB_FOCUS_WEIGHT : 1;
            if (target < 0) {
                picked = focus;
                break;
            }
        }
        return of(grade, picked, random.nextInt(2) == 1);
    }

    // ------------------------------------------------------------------ building

    private static TrinketDesign build(Grade grade, Focus focus, boolean charm) {
        int level = grade.level();
        TrinketKind kind = focus.kind(charm);
        String id = PREFIX + grade.id() + "_" + focus.id() + "_" + kind.id();
        int model = MODELS + grade.ordinal() * Focus.values().length * 2 + focus.ordinal() * 2 + (charm ? 1 : 0);
        int field = Math.min(grade.tier().number(), 7);
        Set<DesignSource> sources = grade.ordinal() <= LAST_FIELD_GRADE.ordinal()
                ? EnumSet.of(DesignSource.CRAFT, DesignSource.FIELD) : EnumSet.of(DesignSource.CRAFT);
        StatPercents percents = StatPercents.of(focus.stat(), charm ? charmPercent(level) : accessoryPercent(level));
        CombatModifiers.Builder b = CombatModifiers.builder();
        if (charm) {
            b.healthFlat(r1(0.4 * level)).defense(r1(0.3 * level));
        } else {
            switch (focus) {
                case STR -> b.physical(round(0.02 + 0.001 * level));
                case INT -> b.magic(round(0.02 + 0.001 * level));
                case DEX -> b.ranged(round(0.02 + 0.001 * level));
                case AGI -> b.attackSpeed(round(0.01 + 0.0005 * level));
                case VIT -> b.healthFlat(r1(0.8 * level));
                case LUK -> b.crit(round(0.01 + 0.0006 * level));
            }
        }
        return new TrinketDesign(id, model, kind, field, level, sources, "", percents, b.build(), List.of(),
                TrinketDesign.statBias(percents, kind.slot()));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double r1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
